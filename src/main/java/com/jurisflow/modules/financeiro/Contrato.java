package com.jurisflow.modules.financeiro;

import com.jurisflow.shared.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "contratos")
public class Contrato extends TenantEntity {

    @Column(name = "processo_id", nullable = false, unique = true)
    private UUID processoId;

    @Column(name = "valor_total", nullable = false)
    private BigDecimal valorTotal;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "created_by")
    private UUID createdBy;
}
