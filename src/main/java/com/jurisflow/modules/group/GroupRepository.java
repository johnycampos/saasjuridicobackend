package com.jurisflow.modules.group;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {
    Page<Group> findByTenantId(UUID tenantId, Pageable pageable);
    List<Group> findByTenantId(UUID tenantId);

    @Query("""
        SELECT g FROM Group g
        WHERE g.tenantId = :tenantId
        AND EXISTS (
            SELECT gm FROM GroupMember gm
            WHERE gm.groupId = g.id AND gm.user.id = :userId
        )
    """)
    List<Group> findByTenantIdAndMember(UUID tenantId, UUID userId);
}
