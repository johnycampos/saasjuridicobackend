package com.jurisflow.modules.group;

import com.jurisflow.modules.group.dto.GroupRequest;
import com.jurisflow.modules.group.dto.GroupResponse;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.processo.ProcessoStatus;
import com.jurisflow.modules.user.UserRepository;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ProcessoRepository processoRepository;

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

        return toResponse(group);
    }

    public Page<GroupResponse> listByTenant(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return groupRepository.findByTenantId(tenantId, pageable).map(this::toResponse);
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
