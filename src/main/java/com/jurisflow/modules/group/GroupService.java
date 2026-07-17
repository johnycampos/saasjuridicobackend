package com.jurisflow.modules.group;

import com.jurisflow.modules.board.BoardColumn;
import com.jurisflow.modules.board.BoardColumnRepository;
import com.jurisflow.modules.group.dto.GroupRequest;
import com.jurisflow.modules.group.dto.GroupResponse;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.processo.ProcessoStatus;
import com.jurisflow.modules.tenant.TenantMemberRepository;
import com.jurisflow.modules.tenant.TenantRole;
import com.jurisflow.modules.user.User;
import com.jurisflow.modules.user.UserRepository;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ProcessoRepository processoRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final TenantMemberRepository tenantMemberRepository;

    @Transactional
    public GroupResponse create(GroupRequest request, UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        Group group = new Group();
        group.setTenantId(tenantId);
        group.setNome(request.nome());
        group.setDescricao(request.descricao());
        group.setCor(request.cor() != null ? request.cor() : "#3B82F6");
        group.setCreatedBy(principal.getId());
        group = groupRepository.save(group);

        var user = userRepository.findById(principal.getId())
                .orElseThrow(() -> BusinessException.notFound("Usuario"));

        GroupMember leader = new GroupMember();
        leader.setGroupId(group.getId());
        leader.setUser(user);
        leader.setRole(GroupRole.LEADER);
        groupMemberRepository.save(leader);

        BoardColumn defaultColumn = new BoardColumn();
        defaultColumn.setTenantId(tenantId);
        defaultColumn.setGroupId(group.getId());
        defaultColumn.setNome("Geral");
        defaultColumn.setPosicao(0);
        boardColumnRepository.save(defaultColumn);

        return toResponse(group);
    }

    public Page<GroupResponse> listByTenant(Pageable pageable, UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        var restriction = resolveGroupRestriction(tenantId, userId);

        if (restriction.isEmpty()) {
            return groupRepository.findByTenantId(tenantId, pageable).map(this::toResponse);
        }
        if (restriction.get().isEmpty()) {
            return Page.empty(pageable);
        }
        return groupRepository.findByTenantIdAndIdIn(tenantId, restriction.get(), pageable).map(this::toResponse);
    }

    @Transactional
    public GroupResponse update(UUID groupId, GroupRequest request) {
        Group group = findGroupInTenant(groupId);
        group.setNome(request.nome());
        if (request.descricao() != null) group.setDescricao(request.descricao());
        if (request.cor() != null) group.setCor(request.cor());
        return toResponse(groupRepository.save(group));
    }

    @Transactional
    public void delete(UUID groupId) {
        groupRepository.delete(findGroupInTenant(groupId));
    }

    @Transactional
    public void addMember(UUID groupId, UUID userId) {
        findGroupInTenant(groupId);
        if (groupMemberRepository.existsByGroupIdAndUser_Id(groupId, userId)) {
            throw BusinessException.conflict("Usuario ja e membro deste grupo");
        }
        var user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("Usuario"));

        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setUser(user);
        member.setRole(GroupRole.MEMBER);
        groupMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(UUID groupId, UUID userId) {
        findGroupInTenant(groupId);
        groupMemberRepository.deleteByGroupIdAndUser_Id(groupId, userId);
    }

    /**
     * Atribui em lote as areas (grupos) que um usuario pode ver, inserindo
     * um GroupMember por area ainda nao vinculada. Usado ao adicionar um
     * membro ao tenant com visibilidade restrita a areas especificas.
     */
    @Transactional
    public void assignAreas(UUID tenantId, User user, Set<UUID> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) return;

        List<Group> groups = groupRepository.findAllById(groupIds);
        if (groups.size() != groupIds.size()) {
            throw BusinessException.notFound("Uma ou mais areas informadas");
        }
        for (Group g : groups) {
            if (!g.getTenantId().equals(tenantId)) {
                throw BusinessException.forbidden();
            }
        }

        for (UUID groupId : groupIds) {
            if (groupMemberRepository.existsByGroupIdAndUser_Id(groupId, user.getId())) continue;
            GroupMember member = new GroupMember();
            member.setGroupId(groupId);
            member.setUser(user);
            member.setRole(GroupRole.MEMBER);
            groupMemberRepository.save(member);
        }
    }

    /**
     * ADMIN/OWNER veem tudo (retorna Optional vazio = sem restricao).
     * MEMBER/VIEWER so veem os grupos aos quais pertencem (retorna o
     * conjunto de group ids permitidos, possivelmente vazio).
     */
    public Optional<Set<UUID>> resolveGroupRestriction(UUID tenantId, UUID userId) {
        TenantRole role = tenantMemberRepository.findByTenantIdAndUser_Id(tenantId, userId)
                .map(tm -> tm.getRole())
                .orElse(TenantRole.VIEWER);

        if (role == TenantRole.OWNER || role == TenantRole.ADMIN) {
            return Optional.empty();
        }

        Set<UUID> groupIds = groupRepository.findByTenantIdAndMember(tenantId, userId).stream()
                .map(Group::getId)
                .collect(Collectors.toSet());
        return Optional.of(groupIds);
    }

    /**
     * Remove todas as areas (group_members) que um usuario tem dentro de um
     * tenant. Usado ao remover o membro do escritorio, para que o acesso as
     * areas nao fique "orfao" (e nao atrapalhe uma futura readmissao).
     */
    @Transactional
    public void removeAllAreasForUser(UUID tenantId, UUID userId) {
        groupMemberRepository.deleteByUserIdAndTenantId(userId, tenantId);
    }

    private Group findGroupInTenant(UUID groupId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> BusinessException.notFound("Grupo"));
        if (!group.getTenantId().equals(tenantId)) throw BusinessException.forbidden();
        return group;
    }

    private GroupResponse toResponse(Group g) {
        long total = processoRepository.countByGroupIdAndStatus(g.getId(), ProcessoStatus.ATIVO);
        return new GroupResponse(g.getId(), g.getTenantId(), g.getNome(),
                g.getDescricao(), g.getCor(), g.getCreatedBy(), total);
    }
}
