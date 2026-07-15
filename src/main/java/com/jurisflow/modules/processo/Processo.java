package com.jurisflow.modules.processo;

import com.jurisflow.shared.entity.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Column(name = "cliente_id")
    private UUID clienteId;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "tipo_acao")
    private String tipoAcao;

    private String vara;
    private String comarca;
    private String estado;
    private String tribunal;
    private String reu;

    @Enumerated(EnumType.STRING)
    private ProcessoStatus status = ProcessoStatus.ATIVO;

    @Column(name = "valor_causa")
    private BigDecimal valorCausa;

    @Column(name = "data_distribuicao")
    private LocalDate dataDistribuicao;

    @Column(name = "posicao_coluna")
    private Integer posicaoColuna = 0;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "campos_extras", columnDefinition = "TEXT")
    private String camposExtras = "{}";
}
