package com.jurisflow.config;

import com.jurisflow.security.ApiKeyAuthFilter;
import com.jurisflow.security.JwtAuthenticationFilter;
import com.jurisflow.security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final com.jurisflow.security.McpTokenAuthFilter mcpTokenAuthFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    @Value("${app.cors-origins}")
    private String corsOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/login/**", "/oauth2/**", "/api/invites/accept/**", "/actuator/health", "/api/google-calendar/callback").permitAll()
                .requestMatchers("/.well-known/**", "/oauth/authorize", "/oauth/token", "/oauth/register", "/oauth/revoke").permitAll()
                // autenticado pelo ApiKeyAuthFilter (header X-Api-Key), nao por
                // OAuth2/JWT de usuario — ver com.jurisflow.security.ApiKeyAuthFilter
                .requestMatchers("/api/integrations/**").permitAll()
                .requestMatchers("/mcp", "/mcp/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED),
                new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/**")))
            .oauth2Login(oauth2 -> oauth2.successHandler(oAuth2SuccessHandler))
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(mcpTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(corsOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        CorsConfiguration mcpOAuthConfig = new CorsConfiguration();
        mcpOAuthConfig.setAllowedOrigins(List.of("*"));
        mcpOAuthConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        mcpOAuthConfig.setAllowedHeaders(List.of("*"));
        mcpOAuthConfig.setAllowCredentials(false);
        mcpOAuthConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/.well-known/**", mcpOAuthConfig);
        source.registerCorsConfiguration("/oauth/token", mcpOAuthConfig);
        source.registerCorsConfiguration("/oauth/register", mcpOAuthConfig);
        source.registerCorsConfiguration("/oauth/revoke", mcpOAuthConfig);
        source.registerCorsConfiguration("/mcp", mcpOAuthConfig);
        source.registerCorsConfiguration("/mcp/**", mcpOAuthConfig);
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
