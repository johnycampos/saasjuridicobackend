package com.jurisflow.modules.group;

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
@Table(name = "group_members")
public class GroupMember extends BaseEntity {

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private GroupRole role = GroupRole.MEMBER;

    @Column(name = "added_at")
    private LocalDateTime addedAt = LocalDateTime.now();
}
