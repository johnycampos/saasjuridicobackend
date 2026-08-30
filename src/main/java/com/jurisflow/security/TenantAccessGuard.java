package com.jurisflow.security;

import com.jurisflow.modules.tenant.TenantMember;
import com.jurisflow.modules.tenant.TenantMemberRepository;
import com.jurisflow.modules.tenant.TenantRole;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Checagens de autorizacao por tenant reaproveitaveis entre modulos
 * (tenant/group/board). Extraido do requireMinRole que existia (privado) em
 * TenantService, pra fechar o RBAC ausente em Group/Board e o tenant leak em
 * TenantController — ver docs/security-audit/relatorio-auditoria-seguranca.md.
 */
@Component
@RequiredArgsConstructor
public class TenantAccessGuard {

    private final TenantMemberRepository tenantMemberRepository;

    /** Exige apenas que o usuario seja membro ativo do tenant (qualquer papel). */
    public void requireActiveMember(UUID tenantId, UUID userId) {
        if (!tenantMemberRepository.existsByTenantIdAndUser_IdAndAtivoTrue(tenantId, userId)) {
            throw BusinessException.forbidden();
        }
    }

    /**
     * Exige que o usuario seja membro ativo do tenant com papel igual ou mais
     * privilegiado que o minimo informado.
     * OWNER=0, ADMIN=1, MEMBER=2, VIEWER=3 — menor ordinal = mais permissao.
     */
    public void requireMinRole(UUID tenantId, UUID userId, TenantRole minimum) {
        TenantMember member = tenantMemberRepository.findActiveMember(tenantId, userId)
                .orElseThrow(BusinessException::forbidden);
        if (member.getRole().ordinal() > minimum.ordinal()) {
            throw BusinessException.forbidden();
        }
    }
}
