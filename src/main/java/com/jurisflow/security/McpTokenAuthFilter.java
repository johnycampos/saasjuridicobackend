package com.jurisflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jurisflow.modules.mcp.McpTokenService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class McpTokenAuthFilter extends OncePerRequestFilter {
    private final McpTokenService tokens;
    private final ObjectMapper mapper;
    private final String publicBackendUrl;
    private final ConcurrentHashMap<UUID, Window> windows = new ConcurrentHashMap<>();
    private record Window(long minute, int count) {}

    @org.springframework.beans.factory.annotation.Autowired
    public McpTokenAuthFilter(McpTokenService tokens, ObjectMapper mapper,
                              @Value("${app.public-backend-url:http://localhost:8080}") String publicBackendUrl) {
        this.tokens = tokens;
        this.mapper = mapper;
        this.publicBackendUrl = publicBackendUrl != null ? publicBackendUrl.replaceAll("/+$", "") : "http://localhost:8080";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        try {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
            String header = request.getHeader("Authorization");
            var token = tokens.authenticate(header != null && header.startsWith("Bearer ") ? header.substring(7) : null);
            if (token.isEmpty()) {
                response.setStatus(401);
                response.setHeader("WWW-Authenticate", "Bearer realm=\"jurisflow-mcp\", resource_metadata=\""
                        + publicBackendUrl + "/.well-known/oauth-protected-resource/mcp\"");
                response.setContentType("application/json");
                mapper.writeValue(response.getOutputStream(), Map.of("error", "Token MCP inválido ou expirado"));
                return;
            }
            long minute = System.currentTimeMillis() / 60000;
            // Remove expired windows so unused/revoked tokens do not accumulate indefinitely.
            windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
            Window window = windows.compute(token.get().getId(), (id, old) ->
                new Window(minute, old == null || old.minute() != minute ? 1 : old.count() + 1));
            if (window.count() > 120) {
                response.setStatus(429);
                response.setHeader("Retry-After", Long.toString(60 - (System.currentTimeMillis() / 1000) % 60));
                response.setContentType("application/json");
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("jsonrpc", "2.0"); error.put("id", null);
                error.put("error", Map.of("code", -32000, "message", "Limite de 120 requisições por minuto excedido"));
                mapper.writeValue(response.getOutputStream(), error);
                return;
            }
            UserPrincipal principal = tokens.principal(token.get());
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            TenantContext.setCurrentTenantId(token.get().getTenantId());
            tokens.touch(token.get());
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
