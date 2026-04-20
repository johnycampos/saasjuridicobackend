package com.jurisflow.modules.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {
    List<GroupMember> findByGroupId(UUID groupId);
    Optional<GroupMember> findByGroupIdAndUser_Id(UUID groupId, UUID userId);
    boolean existsByGroupIdAndUser_Id(UUID groupId, UUID userId);
    void deleteByGroupIdAndUser_Id(UUID groupId, UUID userId);
}
