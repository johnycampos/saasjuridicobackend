package com.jurisflow.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleCalendarStartupValidator implements ApplicationRunner {

    private final GoogleCalendarConfig config;

    @Override
    public void run(ApplicationArguments args) {
        String tokenKey = config.getTokenEncryptionKey();
        if (tokenKey == null || tokenKey.isBlank()) {
            log.warn("Google Calendar desabilitado: CALENDAR_TOKEN_ENCRYPTION_KEY nao configurada.");
            return;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(tokenKey.trim());
            if (decoded.length < 32) {
                String message = "CALENDAR_TOKEN_ENCRYPTION_KEY configurada, mas possui menos de 32 bytes apos decodificacao Base64 (tamanho: "
                        + decoded.length + " bytes). Abortando inicializacao.";
                log.error(message);
                throw new IllegalStateException(message);
            }
        } catch (IllegalArgumentException e) {
            String message = "CALENDAR_TOKEN_ENCRYPTION_KEY configurada com valor Base64 invalido. Abortando inicializacao.";
            log.error(message, e);
            throw new IllegalStateException(message, e);
        }

        log.info("Google Calendar configurado com sucesso (modo seguro ativado).");
    }
}
