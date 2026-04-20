package com.jurisflow.security;

import com.jurisflow.modules.tenant.TenantMember;
import com.jurisflow.modules.tenant.TenantMemberRepository;
import com.jurisflow.modules.tenant.TenantMemberStatus;
import com.jurisflow.modules.user.User;
import com.jurisflow.modules.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final TenantMemberRepository tenantMemberRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();

        String email = oidcUser.getEmail();
        String name = oidcUser.getFullName();
        String picture = oidcUser.getPicture();
        String googleSub = oidcUser.getSubject();

        User user = userRepository.findByEmail(email)
                .map(existing -> {
                    existing.setNome(name);
                    existing.setAvatarUrl(picture);
                    existing.setGoogleSub(googleSub);
                    existing.setUpdatedAt(LocalDateTime.now());
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setNome(name);
                    newUser.setAvatarUrl(picture);
                    newUser.setGoogleSub(googleSub);
                    return userRepository.save(newUser);
                });

        // Ativa memberships pendentes
        List<TenantMember> pending = tenantMemberRepository
                .findByUser_IdAndStatusAndAtivoTrue(user.getId(), TenantMemberStatus.PENDING);
        if (!pending.isEmpty()) {
            pending.forEach(tm -> {
                tm.setStatus(TenantMemberStatus.ACTIVE);
                tm.setJoinedAt(LocalDateTime.now());
            });
            tenantMemberRepository.saveAll(pending);
        }

        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        // Se não tem nenhuma membership ativa, vai para onboarding
        boolean hasActiveMembership = tenantMemberRepository.existsByUser_IdAndAtivoTrue(user.getId());
        String redirectPath = hasActiveMembership ? "/dashboard" : "/onboarding";

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(frontendUrl + redirectPath)
                .queryParam("token", accessToken)
                .queryParam("refresh", refreshToken);

        tenantMemberRepository.findFirstByUser_IdAndAtivoTrue(user.getId())
                .ifPresent(tm -> uriBuilder.queryParam("tenantId", tm.getTenantId()));

        getRedirectStrategy().sendRedirect(request, response, uriBuilder.build().toUriString());
    }
}
