package com.jurisflow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Falha o boot da aplicacao (em vez de subir silenciosamente insegura) caso
 * segredos criticos estejam ausentes ou usando valores fracos/de exemplo que
 * ja circularam no repositorio (docker-compose.yml, .env, .env.example
 * antigos). Ver docs/security-audit/relatorio-auditoria-seguranca.md.
 */
@Slf4j
@Component
public class SecurityStartupValidator implements ApplicationRunner {

    private static final int MIN_SECRET_BYTES = 32;

    // Valores fracos que ja estiveram hardcoded no repo em algum momento —
    // se alguem reusar um deles por engano, o boot deve falhar mesmo assim.
    private static final Set<String> KNOWN_WEAK_JWT_SECRETS = Set.of(
            "jurisflow-super-secret-key-change-in-production-minimum-32-chars",
            "jurisflow-super-secret-key-change-in-production-min-32-chars",
            "jurisflow-secret-key-change-in-production-minimum-32chars"
    );

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Override
    public void run(ApplicationArguments args) {
        List<String> errors = new ArrayList<>();

        if (jwtSecret == null || jwtSecret.isBlank()) {
            errors.add("JWT_SECRET nao configurado (variavel de ambiente ausente ou em branco).");
        } else if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            errors.add("JWT_SECRET deve ter no minimo " + MIN_SECRET_BYTES + " bytes.");
        } else if (KNOWN_WEAK_JWT_SECRETS.contains(jwtSecret)) {
            errors.add("JWT_SECRET esta usando um valor de exemplo conhecido/inseguro — gere um novo "
                    + "(ex: openssl rand -base64 64) e rotacione.");
        }

        if (!errors.isEmpty()) {
            String message = "Configuracao de seguranca invalida, abortando inicializacao:\n - "
                    + String.join("\n - ", errors);
            log.error(message);
            throw new IllegalStateException(message);
        }
    }
}
