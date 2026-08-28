package com.jurisflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guarda as rotas de integracao maquina-a-maquina (/api/integrations/**),
 * que nao passam pelo login OAuth2/JWT de usuario. Valida o header
 * X-Api-Key contra uma chave de sistema unica (INTEGRATIONS_API_KEY) usando
 * comparacao em tempo constante. Nao interfere em nenhuma outra rota.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Api-Key";
    private static final String PROTECTED_PREFIX = "/api/integrations/";

    @Value("${app.integrations-api-key:}")
    private String expectedApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(PROTECTED_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!isValid(request.getHeader(HEADER))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"API key invalida ou ausente\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isValid(String provided) {
        if (!StringUtils.hasText(expectedApiKey) || !StringUtils.hasText(provided)) {
            return false;
        }
        byte[] expected = expectedApiKey.getBytes(StandardCharsets.UTF_8);
        byte[] actual = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
