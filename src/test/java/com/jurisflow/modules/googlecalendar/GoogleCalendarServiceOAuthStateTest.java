package com.jurisflow.modules.googlecalendar;

import com.jurisflow.config.GoogleCalendarConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleCalendarServiceOAuthStateTest {

    private GoogleCalendarConfig config;

    @BeforeEach
    void setUp() {
        config = new GoogleCalendarConfig();
        config.setClientId("test-client-id");
        config.setClientSecret("test-client-secret");
        config.setRedirectUri("http://localhost:8080/api/google-calendar/callback");
        // Chave de 32 bytes em base64
        byte[] keyBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(keyBytes);
        config.setTokenEncryptionKey(Base64.getEncoder().encodeToString(keyBytes));
    }

    // Usa reflection para acessar métodos privados
    private String invokeGenerateSignedState(GoogleCalendarService service, UUID tenantId, UUID userId) throws Exception {
        Method method = GoogleCalendarService.class.getDeclaredMethod("generateSignedState", UUID.class, UUID.class);
        method.setAccessible(true);
        return (String) method.invoke(service, tenantId, userId);
    }

    private Object invokeValidateAndExtractState(GoogleCalendarService service, String state) throws Exception {
        Method method = GoogleCalendarService.class.getDeclaredMethod("validateAndExtractState", String.class);
        method.setAccessible(true);
        return method.invoke(service, state);
    }

    @Test
    void stateRoundtrip() throws Exception {
        // Cria o service com mocks mínimos (null nos deps que não usamos nesse teste)
        GoogleCalendarService service = new GoogleCalendarService(null, null, config, null, null);

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String state = invokeGenerateSignedState(service, tenantId, userId);
        assertThat(state).isNotBlank();

        // Valida o state — deve extrair o mesmo tenantId e userId
        Object result = invokeValidateAndExtractState(service, state);
        assertThat(result).isNotNull();
        // O result é um record ValidatedState(tenantId, userId)
        assertThat(result.toString()).contains(tenantId.toString());
        assertThat(result.toString()).contains(userId.toString());
    }

    @Test
    void forgedStateIsRejected() {
        GoogleCalendarService service = new GoogleCalendarService(null, null, config, null, null);

        // Cria um state forjado (sem assinatura HMAC válida)
        String forgedPayload = UUID.randomUUID() + ":" + UUID.randomUUID() + ":" + System.currentTimeMillis() + ":fake-nonce";
        String forgedState = Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> invokeValidateAndExtractState(service, forgedState))
                .hasCauseInstanceOf(com.jurisflow.shared.exception.BusinessException.class);
    }

    @Test
    void expiredStateIsRejected() throws Exception {
        GoogleCalendarService service = new GoogleCalendarService(null, null, config, null, null);

        // Cria um state com timestamp no passado (11 minutos atrás)
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        long oldTimestamp = System.currentTimeMillis() - (11 * 60 * 1000); // 11 min atrás
        String nonce = "test-nonce";
        String payload = tenantId + ":" + userId + ":" + oldTimestamp + ":" + nonce;

        // Calcula HMAC válido para o payload com chave derivada
        String derivedHmacKey = config.getTokenEncryptionKey() + ":oauth-state-hmac";
        byte[] hashedKey = java.security.MessageDigest.getInstance("SHA-256")
                .digest(derivedHmacKey.getBytes(StandardCharsets.UTF_8));
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(hashedKey, "HmacSHA256");
        mac.init(keySpec);
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

        String expiredState = Base64.getUrlEncoder().withoutPadding().encodeToString((payload + "." + signature).getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> invokeValidateAndExtractState(service, expiredState))
                .hasCauseInstanceOf(com.jurisflow.shared.exception.BusinessException.class);
    }
}
