package com.jurisflow.modules.tribunal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TribunalRepository extends JpaRepository<Tribunal, UUID> {
    List<Tribunal> findAllByOrderBySiglaAsc();
    boolean existsBySiglaIgnoreCase(String sigla);
}
