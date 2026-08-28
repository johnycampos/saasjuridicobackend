package com.jurisflow.modules.movimento;

import com.jurisflow.shared.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "processo_movimentos")
public class Movimento extends TenantEntity {

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    private Integer codigo;

    @Column(nullable = false)
    private String nome;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(name = "dados_extras", columnDefinition = "TEXT")
    private String dadosExtras = "{}";

    @Column(nullable = false)
    private Boolean visualizado = false;

    @Column(name = "visualizado_por")
    private UUID visualizadoPor;

    @Column(name = "visualizado_em")
    private LocalDateTime visualizadoEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovimentoOrigem origem = MovimentoOrigem.MOVIMENTO;

    @Column(name = "hash_origem")
    private String hashOrigem;
}
