package com.jurisflow.modules.processo;

import com.jurisflow.shared.entity.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "processos")
public class Processo extends TenantEntity {

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "column_id")
    private UUID columnId;

    @Column(name = "numero_processo")
    private String numeroProcesso;

    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "tipo_acao")
    private String tipoAcao;

    private String vara;
    private String comarca;
    private String tribunal;
    private String autor;
    private String reu;

    @Enumerated(EnumType.STRING)
    private PrioridadeTipo prioridade = PrioridadeTipo.MEDIA;

    @Enumerated(EnumType.STRING)
    private ProcessoStatus status = ProcessoStatus.ATIVO;

    @Column(name = "valor_causa")
    private BigDecimal valorCausa;

    @Column(name = "data_distribuicao")
    private LocalDate dataDistribuicao;

    @Column(name = "prazo_proximo")
    private LocalDateTime prazoProximo;

    @Column(name = "posicao_coluna")
    private Integer posicaoColuna = 0;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "campos_extras", columnDefinition = "TEXT")
    private String camposExtras = "{}";
}
