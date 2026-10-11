package com.jurisflow.modules.mcp;

import com.jurisflow.modules.mcp.dto.*;
import com.jurisflow.modules.tenant.*;
import com.jurisflow.modules.user.*;
import com.jurisflow.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class McpTokenServiceTest {
    @Mock McpTokenRepository repository;
    @Mock TenantMemberRepository members;
    @Mock UserRepository users;
    @Mock TenantAccessGuard guard;
    @InjectMocks McpTokenService service;
    final UUID tenant = UUID.randomUUID(), user = UUID.randomUUID();
    final String raw = "jfmcp_" + "a".repeat(43);

    @BeforeEach void setup() { TenantContext.setCurrentTenantId(tenant); }
    @AfterEach void cleanup() { TenantContext.clear(); }
    private McpToken token() {
        var t = new McpToken(); t.setId(UUID.randomUUID()); t.setTenantId(tenant); t.setUserId(user);
        t.setTokenHash(McpTokenService.hash(raw)); return t;
    }
    @Test void createsRandomTokenAndPersistsOnlyHash() {
        when(users.lockForMcpTokenCreation(user)).thenReturn(Optional.of(new User()));
        when(repository.save(any())).thenAnswer(invocation -> {
            McpToken token = invocation.getArgument(0); token.setId(UUID.randomUUID()); return token;
        });
        var result = service.create(user, new CreateMcpTokenRequest("Claude", 30));
        assertThat(result.token()).matches("jfmcp_[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(result.token().substring(6))).hasSize(32);
        ArgumentCaptor<McpToken> captor = ArgumentCaptor.forClass(McpToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo(McpTokenService.hash(result.token())).hasSize(64);
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(result.token());
        assertThat(captor.getValue().getTokenPrefix()).isEqualTo(result.token().substring(0, 12));
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenant);
        assertThat(captor.getValue().getUserId()).isEqualTo(user);
        assertThat(result.expiresAt()).isAfter(Instant.now().plusSeconds(29 * 86400L));
        verify(guard).requireActiveMember(tenant, user);
    }
    @Test void validTokenRequiresActiveMembershipEachTime() {
        var t = token(); when(repository.findByTokenHash(t.getTokenHash())).thenReturn(Optional.of(t));
        when(members.existsByTenantIdAndUser_IdAndAtivoTrue(tenant, user)).thenReturn(true, false);
        assertThat(service.authenticate(raw)).contains(t);
        assertThat(service.authenticate(raw)).isEmpty();
        verify(members, times(2)).existsByTenantIdAndUser_IdAndAtivoTrue(tenant, user);
    }
    @Test void revokedTokenIsInvalid() {
        var t = token(); t.setRevokedAt(Instant.now());
        when(repository.findByTokenHash(t.getTokenHash())).thenReturn(Optional.of(t));
        assertThat(service.authenticate(raw)).isEmpty(); verifyNoInteractions(members);
    }
    @Test void expiredTokenIsInvalid() {
        var t = token(); t.setExpiresAt(Instant.now().minusSeconds(1));
        when(repository.findByTokenHash(t.getTokenHash())).thenReturn(Optional.of(t));
        assertThat(service.authenticate(raw)).isEmpty(); verifyNoInteractions(members);
    }
    @Test void unknownAndMalformedTokensAreInvalid() {
        assertThat(service.authenticate(raw)).isEmpty();
        assertThat(service.authenticate("jwt")).isEmpty(); assertThat(service.authenticate(null)).isEmpty();
        verify(repository).findByTokenHash(McpTokenService.hash(raw));
    }
    @Test void activeLimitPreventsCreation() {
        when(users.lockForMcpTokenCreation(user)).thenReturn(Optional.of(new User()));
        when(repository.countActive(eq(tenant), eq(user), any())).thenReturn(10L);
        assertThatThrownBy(() -> service.create(user, new CreateMcpTokenRequest("Claude", null))).hasMessageContaining("10 tokens");
        verify(repository, never()).save(any());
    }
    @Test void missingTenantIsRejected() {
        TenantContext.clear();
        assertThatThrownBy(() -> service.list(user)).hasMessageContaining("X-Tenant-ID"); verifyNoInteractions(repository);
    }
    @Test void listIsScopedAndDoesNotExposeHash() {
        var t = token(); t.setNome("Claude"); t.setTokenPrefix(raw.substring(0,12));
        when(repository.findByTenantIdAndUserIdAndKindOrderByCreatedAtDesc(tenant, user, "PAT")).thenReturn(List.of(t));
        assertThat(service.list(user)).hasSize(1).first().extracting(McpTokenResponse::tokenPrefix).isEqualTo(t.getTokenPrefix());
        assertThat(Arrays.stream(McpTokenResponse.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
            .doesNotContain("token", "tokenHash");
    }
    @Test void ownerCanRevokeOwnToken() {
        var t = token(); when(repository.findByIdAndTenantId(t.getId(), tenant)).thenReturn(Optional.of(t));
        service.revoke(t.getId(), user);
        assertThat(t.getRevokedAt()).isNotNull(); verify(guard, never()).requireMinRole(any(), any(), any());
    }
    @Test void revokingOtherUsersTokenRequiresAdmin() {
        var t = token(); t.setUserId(UUID.randomUUID());
        when(repository.findByIdAndTenantId(t.getId(), tenant)).thenReturn(Optional.of(t));
        doThrow(com.jurisflow.shared.exception.BusinessException.forbidden()).when(guard).requireMinRole(tenant, user, TenantRole.ADMIN);
        assertThatThrownBy(() -> service.revoke(t.getId(), user)).hasMessage("Acesso negado");
        verify(repository, never()).save(any());
    }
    @Test void adminCanRevokeWithinCurrentTenant() {
        var t = token(); t.setUserId(UUID.randomUUID());
        when(repository.findByIdAndTenantId(t.getId(), tenant)).thenReturn(Optional.of(t));
        service.revoke(t.getId(), user); verify(guard).requireMinRole(tenant, user, TenantRole.ADMIN);
        assertThat(t.getRevokedAt()).isNotNull();
    }
    @Test void crossTenantRevocationIsNotFound() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.revoke(id, user)).hasMessageContaining("Token");
        verify(repository).findByIdAndTenantId(id, tenant); verify(repository, never()).save(any());
    }
    @Test void recentUseDoesNotWriteAndOldUseIsConditional() {
        var t = token(); t.setLastUsedAt(Instant.now()); service.touch(t); verify(repository, never()).touch(any(), any(), any());
        t.setLastUsedAt(Instant.now().minusSeconds(61)); service.touch(t);
        verify(repository).touch(eq(t.getId()), any(), any());
    }
}
