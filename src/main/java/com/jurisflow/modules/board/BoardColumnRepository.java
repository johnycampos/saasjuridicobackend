package com.jurisflow.modules.board;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BoardColumnRepository extends JpaRepository<BoardColumn, UUID> {
    List<BoardColumn> findByGroupIdOrderByPosicaoAsc(UUID groupId);
    List<BoardColumn> findByTenantIdAndGroupIdIsNullOrderByPosicaoAsc(UUID tenantId);

    @Query("SELECT MAX(bc.posicao) FROM BoardColumn bc WHERE bc.groupId = :groupId")
    Integer findMaxPosicaoByGroupId(UUID groupId);
}
