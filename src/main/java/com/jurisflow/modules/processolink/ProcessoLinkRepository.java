package com.jurisflow.modules.processolink;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProcessoLinkRepository extends JpaRepository<ProcessoLink, UUID> {
    List<ProcessoLink> findByProcessoIdOrderByCreatedAtDesc(UUID processoId);
}
