package com.jurisflow.modules.tenant;

import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.tenant.dto.InviteMemberRequest;
import com.jurisflow.modules.tenant.dto.TenantMemberResponse;
import com.jurisflow.modules.tenant.dto.TenantRequest;
import com.jurisflow.modules.tenant.dto.TenantResponse;
import com.jurisflow.modules.user.User;
import com.jurisflow.modules.user.UserRepository;
import com.jurisflow.security.TenantAccessGuard;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final UserRepository userRepository;
    private final GroupService groupService;
    private final TenantAccessGuard tenantAccessGuard;

    @Transactional
    public TenantResponse create(TenantRequest request, UserPrincipal creator) {
        String slug = generateUniqueSlug(request.nome());

        Tenant tenant = new Tenant();
        tenant.setNome(request.nome());
        tenant.setSlug(slug);
        tenant.setCnpj(request.cnpj());
        tenant.setTelefone(request.telefone());
        tenant.setEndereco(request.endereco());
        tenant = tenantRepository.save(tenant);

        var user = userRepository.findById(creator.getId())
                .orElseThrow(() -> BusinessException.notFound("Usuario"));

        TenantMember owner = new TenantMember();
        owner.setTenantId(tenant.getId());
        owner.setUser(user);
        owner.setRole(TenantRole.OWNER);
        owner.setStatus(TenantMemberStatus.ACTIVE);
        tenantMemberRepository.save(owner);

        return toResponse(tenant);
    }

    @Transactional
    public TenantResponse update(UUID tenantId, TenantRequest request, UserPrincipal principal) {
        Tenant tenant = findTenantById(tenantId);
        tenantAccessGuard.requireMinRole(tenantId, principal.getId(), TenantRole.ADMIN);
        tenant.setNome(request.nome());
        tenant.setCnpj(request.cnpj());
        tenant.setTelefone(request.telefone());
        tenant.setEndereco(request.endereco());
        return toResponse(tenantRepository.save(tenant));
    }

    public TenantResponse getById(UUID tenantId, UUID requesterId) {
        tenantAccessGuard.requireActiveMember(tenantId, requesterId);
        return toResponse(findTenantById(tenantId));
    }

    public Page<TenantMemberResponse> getMembers(UUID tenantId, UUID requesterId, Pageable pageable) {
        tenantAccessGuard.requireActiveMember(tenantId, requesterId);
        return tenantMemberRepository.findByTenantIdAndAtivoTrue(tenantId, pageable)
                .map(this::toMemberResponse);
    }

    @Transactional
    public TenantMemberResponse addMemberByEmail(UUID tenantId, InviteMemberRequest request, UserPrincipal principal) {
        tenantAccessGuard.requireMinRole(tenantId, principal.getId(), TenantRole.ADMIN);

        String email = request.email().toLowerCase().trim();
        TenantRole role = request.role() != null ? request.role() : TenantRole.MEMBER;

        // Bloqueia se o usuário já é OWNER de algum tenant
        if (tenantMemberRepository.existsByUser_EmailAndRoleAndAtivoTrue(email, TenantRole.OWNER)) {
            throw new BusinessException("Este usuario ja possui um escritorio cadastrado e nao pode ser adicionado como membro");
        }

        // Busca ou cria o usuário placeholder
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User placeholder = new User();
                    placeholder.setEmail(email);
                    placeholder.setNome(email.split("@")[0]);
                    return userRepository.save(placeholder);
                });

        // Verifica se já é membro ativo
        if (tenantMemberRepository.existsByTenantIdAndUser_IdAndAtivoTrue(tenantId, user.getId())) {
            throw new BusinessException("Este usuario ja e membro deste escritorio");
        }

        // tenant_members tem UNIQUE(tenant_id, user_id) — um usuario removido
        // anteriormente (ativo=false) ja tem uma linha aqui, entao reaproveita
        // (reativa) em vez de inserir uma nova e violar a constraint
        TenantMember member = tenantMemberRepository.findByTenantIdAndUser_Id(tenantId, user.getId())
                .orElseGet(TenantMember::new);
        member.setTenantId(tenantId);
        member.setUser(user);
        member.setRole(role);
        member.setStatus(TenantMemberStatus.PENDING);
        member.setAtivo(true);
        member = tenantMemberRepository.save(member);

        if ((role == TenantRole.MEMBER || role == TenantRole.VIEWER)
                && request.groupIds() != null && !request.groupIds().isEmpty()) {
            groupService.assignAreas(tenantId, user, new LinkedHashSet<>(request.groupIds()));
        }

        return toMemberResponse(member);
    }

    @Transactional
    public void updateMemberRole(UUID tenantId, UUID userId, TenantRole newRole, UserPrincipal principal) {
        tenantAccessGuard.requireMinRole(tenantId, principal.getId(), TenantRole.ADMIN);
        TenantMember member = tenantMemberRepository.findByTenantIdAndUser_Id(tenantId, userId)
                .orElseThrow(() -> BusinessException.notFound("Membro"));
        if (member.getRole() == TenantRole.OWNER) {
            throw new BusinessException("Nao e possivel alterar o papel do OWNER");
        }
        member.setRole(newRole);
        tenantMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(UUID tenantId, UUID userId, UserPrincipal principal) {
        tenantAccessGuard.requireMinRole(tenantId, principal.getId(), TenantRole.ADMIN);
        TenantMember member = tenantMemberRepository.findByTenantIdAndUser_Id(tenantId, userId)
                .orElseThrow(() -> BusinessException.notFound("Membro"));
        if (member.getRole() == TenantRole.OWNER) {
            throw new BusinessException("Nao e possivel remover o OWNER");
        }
        member.setAtivo(false);
        tenantMemberRepository.save(member);

        // sem isso, as areas concedidas ficavam "orfas" em group_members —
        // nao davam acesso de verdade (o TenantInterceptor ja bloqueia quem
        // nao e membro ativo), mas atrapalhavam uma readmissao futura
        groupService.removeAllAreasForUser(tenantId, userId);
    }

    private Tenant findTenantById(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Tenant"));
    }

    private String generateUniqueSlug(String nome) {
        String base = Normalizer.normalize(nome, Normalizer.Form.NFD);
        String slug = Pattern.compile("[^\\p{ASCII}]").matcher(base).replaceAll("")
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (tenantRepository.existsBySlug(slug)) {
            slug = slug + "-" + System.currentTimeMillis();
        }
        return slug;
    }

    private TenantResponse toResponse(Tenant t) {
        return new TenantResponse(t.getId(), t.getNome(), t.getSlug(), t.getCnpj(),
                t.getTelefone(), t.getEndereco(), t.getLogoUrl(), t.getPlano(),
                t.getMaxMembros(), t.getAtivo());
    }

    private TenantMemberResponse toMemberResponse(TenantMember tm) {
        var user = tm.getUser();
        return new TenantMemberResponse(tm.getId(), user.getId(), user.getEmail(),
                user.getNome(), user.getAvatarUrl(), tm.getRole(), tm.getStatus(), tm.getJoinedAt());
    }
}
