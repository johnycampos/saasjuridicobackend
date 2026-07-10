package com.jurisflow.modules.financeiro;

import com.jurisflow.shared.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "parcelas")
public class Parcela extends TenantEntity {

    @Column(name = "contrato_id", nullable = false)
    private UUID contratoId;

    private Integer numero = 1;

    @Column(nullable = false)
    private BigDecimal valor;

    private LocalDate vencimento;

    @Enumerated(EnumType.STRING)
    private ParcelaStatus status = ParcelaStatus.PENDENTE;

    @Column(name = "data_pagamento")
    private LocalDateTime dataPagamento;

    @Column(name = "pago_por")
    private UUID pagoPor;

    @Column(name = "created_by")
    private UUID createdBy;
}
