package com.jurisflow.modules.cliente;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    Page<Cliente> findByTenantIdOrderByNomeAsc(UUID tenantId, Pageable pageable);

    Page<Cliente> findByTenantIdAndNomeContainingIgnoreCaseOrderByNomeAsc(UUID tenantId, String nome, Pageable pageable);

    Optional<Cliente> findByIdAndTenantId(UUID id, UUID tenantId);
}
