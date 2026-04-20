package com.jurisflow.modules.tenant;

import com.jurisflow.modules.tenant.dto.TenantMemberResponse;
import com.jurisflow.modules.tenant.dto.TenantRequest;
import com.jurisflow.modules.tenant.dto.TenantResponse;
import com.jurisflow.modules.user.UserRepository;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final UserRepository userRepository;

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
        tenantMemberRepository.save(owner);

        return toResponse(tenant);
    }

    @Transactional
    public TenantResponse update(UUID tenantId, TenantRequest request, UserPrincipal principal) {
        Tenant tenant = findTenantById(tenantId);
        requireMinRole(tenantId, principal.getId(), TenantRole.ADMIN);
        tenant.setNome(request.nome());
        tenant.setCnpj(request.cnpj());
        tenant.setTelefone(request.telefone());
        tenant.setEndereco(request.endereco());
        return toResponse(tenantRepository.save(tenant));
    }

    public TenantResponse getById(UUID tenantId) {
        return toResponse(findTenantById(tenantId));
    }

    public Page<TenantMemberResponse> getMembers(UUID tenantId, Pageable pageable) {
        return tenantMemberRepository.findByTenantIdAndAtivoTrue(tenantId, pageable)
                .map(this::toMemberResponse);
    }

    @Transactional
    public void updateMemberRole(UUID tenantId, UUID userId, TenantRole newRole, UserPrincipal principal) {
        requireMinRole(tenantId, principal.getId(), TenantRole.ADMIN);
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
        requireMinRole(tenantId, principal.getId(), TenantRole.ADMIN);
        TenantMember member = tenantMemberRepository.findByTenantIdAndUser_Id(tenantId, userId)
                .orElseThrow(() -> BusinessException.notFound("Membro"));
        if (member.getRole() == TenantRole.OWNER) {
            throw new BusinessException("Nao e possivel remover o OWNER");
        }
        member.setAtivo(false);
        tenantMemberRepository.save(member);
    }

    // OWNER=0, ADMIN=1, MEMBER=2, VIEWER=3 — menor ordinal = mais permissão
    private void requireMinRole(UUID tenantId, UUID userId, TenantRole minimum) {
        TenantMember member = tenantMemberRepository.findActiveMember(tenantId, userId)
                .orElseThrow(BusinessException::forbidden);
        if (member.getRole().ordinal() > minimum.ordinal()) {
            throw BusinessException.forbidden();
        }
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
                user.getNome(), user.getAvatarUrl(), tm.getRole(), tm.getJoinedAt());
    }
}
