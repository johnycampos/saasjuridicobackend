package com.jurisflow.modules.tenant;

import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.tenant.dto.InviteMemberRequest;
import com.jurisflow.modules.tenant.dto.TenantRequest;
import com.jurisflow.modules.tenant.dto.TenantResponse;
import com.jurisflow.modules.user.User;
import com.jurisflow.modules.user.UserRepository;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupService groupService;

    @InjectMocks
    private TenantService tenantService;

    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = new UserPrincipal(UUID.randomUUID(), "owner@test.com", "Owner", null);
    }

    @Test
    void getById_shouldReturnResponse_whenTenantExists() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setNome("Escritorio Silva");
        tenant.setSlug("escritorio-silva");
        tenant.setPlano("FREE");
        tenant.setMaxMembros(5);
        tenant.setAtivo(true);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        TenantResponse response = tenantService.getById(tenantId);

        assertThat(response.nome()).isEqualTo("Escritorio Silva");
        assertThat(response.slug()).isEqualTo("escritorio-silva");
        assertThat(response.plano()).isEqualTo("FREE");
    }

    @Test
    void getById_shouldThrowNotFound_whenTenantDoesNotExist() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getById(tenantId))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_shouldThrowForbidden_whenUserIsNotAdminOrOwner() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setNome("Old Name");
        tenant.setSlug("old-name");
        tenant.setPlano("FREE");
        tenant.setMaxMembros(5);
        tenant.setAtivo(true);

        TenantMember memberRole = new TenantMember();
        memberRole.setRole(TenantRole.MEMBER);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantMemberRepository.findActiveMember(tenantId, principal.getId()))
                .thenReturn(Optional.of(memberRole));

        assertThatThrownBy(() -> tenantService.update(tenantId, new TenantRequest("New Name", null, null, null), principal))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void update_shouldSucceed_whenUserIsOwner() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setNome("Old Name");
        tenant.setSlug("old-name");
        tenant.setPlano("FREE");
        tenant.setMaxMembros(5);
        tenant.setAtivo(true);

        TenantMember ownerMember = new TenantMember();
        ownerMember.setRole(TenantRole.OWNER);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantMemberRepository.findActiveMember(tenantId, principal.getId()))
                .thenReturn(Optional.of(ownerMember));
        when(tenantRepository.save(any())).thenReturn(tenant);

        TenantResponse response = tenantService.update(tenantId, new TenantRequest("New Name", null, null, null), principal);
        assertThat(response).isNotNull();
    }

    @Test
    void removeMember_shouldThrowError_whenRemovingOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        TenantMember callerMember = new TenantMember();
        callerMember.setRole(TenantRole.ADMIN);

        TenantMember targetMember = new TenantMember();
        targetMember.setRole(TenantRole.OWNER);

        when(tenantMemberRepository.findActiveMember(tenantId, principal.getId()))
                .thenReturn(Optional.of(callerMember));
        when(tenantMemberRepository.findByTenantIdAndUser_Id(tenantId, ownerId))
                .thenReturn(Optional.of(targetMember));

        assertThatThrownBy(() -> tenantService.removeMember(tenantId, ownerId, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OWNER");
    }

    @Test
    void removeMember_shouldCleanUpGroupAreas() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TenantMember callerMember = new TenantMember();
        callerMember.setRole(TenantRole.ADMIN);

        TenantMember targetMember = new TenantMember();
        targetMember.setRole(TenantRole.MEMBER);
        targetMember.setAtivo(true);

        when(tenantMemberRepository.findActiveMember(tenantId, principal.getId()))
                .thenReturn(Optional.of(callerMember));
        when(tenantMemberRepository.findByTenantIdAndUser_Id(tenantId, userId))
                .thenReturn(Optional.of(targetMember));

        tenantService.removeMember(tenantId, userId, principal);

        assertThat(targetMember.getAtivo()).isFalse();
        verify(groupService).removeAllAreasForUser(tenantId, userId);
    }

    @Test
    void addMemberByEmail_shouldReactivateExistingRow_whenUserWasPreviouslyRemoved() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String email = "removed@test.com";

        TenantMember callerMember = new TenantMember();
        callerMember.setRole(TenantRole.OWNER);

        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setNome("removed");

        // simula a linha deixada por uma remocao anterior (ativo=false) —
        // tenant_members tem UNIQUE(tenant_id, user_id), entao inserir uma
        // linha nova aqui quebraria a constraint
        TenantMember existingInactive = new TenantMember();
        existingInactive.setTenantId(tenantId);
        existingInactive.setUser(user);
        existingInactive.setRole(TenantRole.VIEWER);
        existingInactive.setAtivo(false);

        when(tenantMemberRepository.findActiveMember(tenantId, principal.getId()))
                .thenReturn(Optional.of(callerMember));
        when(tenantMemberRepository.existsByUser_EmailAndRoleAndAtivoTrue(email, TenantRole.OWNER))
                .thenReturn(false);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(tenantMemberRepository.existsByTenantIdAndUser_IdAndAtivoTrue(tenantId, userId))
                .thenReturn(false);
        when(tenantMemberRepository.findByTenantIdAndUser_Id(tenantId, userId))
                .thenReturn(Optional.of(existingInactive));
        when(tenantMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InviteMemberRequest request = new InviteMemberRequest(email, TenantRole.MEMBER, null);
        tenantService.addMemberByEmail(tenantId, request, principal);

        verify(tenantMemberRepository).save(existingInactive);
        assertThat(existingInactive.getAtivo()).isTrue();
        assertThat(existingInactive.getRole()).isEqualTo(TenantRole.MEMBER);
    }
}
