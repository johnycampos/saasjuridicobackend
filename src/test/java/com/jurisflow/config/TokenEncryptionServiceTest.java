package com.jurisflow.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenEncryptionServiceTest {

    private TokenEncryptionService service;
    private String testKey;

    @BeforeEach
    void setUp() {
        // Gera uma chave Base64 de 32 bytes para teste
        byte[] keyBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(keyBytes);
        testKey = Base64.getEncoder().encodeToString(keyBytes);
        service = new TokenEncryptionService(testKey);
    }

    @Test
    void encryptDecryptRoundtrip() {
        String original = "ya29.a0AVvZES1234567890abcdefghijklmnopqrstuvwxyz";
        String encrypted = service.encrypt(original);
        assertThat(encrypted).isNotEqualTo(original);
        String decrypted = service.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void encryptProducesDifferentCiphertexts() {
        String original = "same-secret-token";
        String enc1 = service.encrypt(original);
        String enc2 = service.encrypt(original);
        // Devido ao IV aleatório, encryptagens do mesmo texto devem ser diferentes
        assertThat(enc1).isNotEqualTo(enc2);
        // Mas ambas decriptografam para o mesmo valor
        assertThat(service.decrypt(enc1)).isEqualTo(original);
        assertThat(service.decrypt(enc2)).isEqualTo(original);
    }

    @Test
    void encryptNull() {
        assertThat(service.encrypt(null)).isNull();
        assertThat(service.decrypt(null)).isNull();
    }

    @Test
    void decryptWithWrongKeyFails() {
        String encrypted = service.encrypt("secret-data");

        // Cria service com chave diferente
        byte[] otherKeyBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(otherKeyBytes);
        String otherKey = Base64.getEncoder().encodeToString(otherKeyBytes);
        TokenEncryptionService otherService = new TokenEncryptionService(otherKey);

        assertThatThrownBy(() -> otherService.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shortKeyThrows() {
        byte[] shortKey = new byte[16]; // só 16 bytes
        String shortKeyBase64 = Base64.getEncoder().encodeToString(shortKey);
        TokenEncryptionService shortService = new TokenEncryptionService(shortKeyBase64);

        assertThatThrownBy(() -> shortService.encrypt("test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void emptyKeyThrows() {
        TokenEncryptionService emptyService = new TokenEncryptionService("");
        assertThatThrownBy(() -> emptyService.encrypt("test"))
                .isInstanceOf(IllegalStateException.class);
    }
}
