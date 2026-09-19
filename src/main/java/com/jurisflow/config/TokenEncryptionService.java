package com.jurisflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTE = 12;
    private static final int TAG_LENGTH_BIT = 128;
    private static final int MIN_KEY_BYTES = 32;

    private final String rawKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenEncryptionService(@Value("${app.google-calendar.token-encryption-key:}") String rawKey) {
        this.rawKey = rawKey;
    }

    private byte[] getKeyBytes() {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalStateException("Chave de criptografia de token do Google Calendar nao configurada.");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(rawKey.trim());
            if (decoded.length < MIN_KEY_BYTES) {
                throw new IllegalStateException("Chave de criptografia de token deve ter no minimo 32 bytes (256 bits).");
            }
            if (decoded.length == MIN_KEY_BYTES) {
                return decoded;
            }
            byte[] key256 = new byte[MIN_KEY_BYTES];
            System.arraycopy(decoded, 0, key256, 0, MIN_KEY_BYTES);
            return key256;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Chave de criptografia de token invalida (esperado Base64): " + e.getMessage(), e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(getKeyBytes(), "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao criptografar token: " + e.getMessage(), e);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            if (decoded.length < IV_LENGTH_BYTE + (TAG_LENGTH_BIT / 8)) {
                throw new IllegalArgumentException("Payload criptografado invalido ou truncado.");
            }

            byte[] iv = new byte[IV_LENGTH_BYTE];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH_BYTE);

            byte[] cipherText = new byte[decoded.length - IV_LENGTH_BYTE];
            System.arraycopy(decoded, IV_LENGTH_BYTE, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(getKeyBytes(), "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao decriptografar token: " + e.getMessage(), e);
        }
    }
}
