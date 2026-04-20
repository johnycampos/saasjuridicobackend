package com.jurisflow.modules.tenant;

import com.jurisflow.modules.tenant.dto.TenantRequest;
import com.jurisflow.modules.tenant.dto.TenantResponse;
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
}
