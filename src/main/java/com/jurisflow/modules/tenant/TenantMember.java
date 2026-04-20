package com.jurisflow.modules.tenant;

import com.jurisflow.modules.user.User;
import com.jurisflow.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "tenant_members")
public class TenantMember extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantRole role = TenantRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantMemberStatus status = TenantMemberStatus.PENDING;

    private Boolean ativo = true;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;
}
