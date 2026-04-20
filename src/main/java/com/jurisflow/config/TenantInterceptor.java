package com.jurisflow.config;

import com.jurisflow.modules.tenant.TenantMemberRepository;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private final TenantMemberRepository tenantMemberRepository;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String tenantIdHeader = request.getHeader("X-Tenant-ID");

        if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
            try {
                UUID tenantId = UUID.fromString(tenantIdHeader);
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();

                if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
                    boolean isMember = tenantMemberRepository
                            .existsByTenantIdAndUser_IdAndAtivoTrue(tenantId, principal.getId());
                    if (!isMember) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not a member of this tenant");
                        return false;
                    }
                }

                TenantContext.setCurrentTenantId(tenantId);
            } catch (IllegalArgumentException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid tenant ID");
                return false;
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }
}
