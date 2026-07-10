package com.jurisflow.modules.tarefa;

import com.jurisflow.modules.processo.PrioridadeTipo;
import com.jurisflow.shared.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "processo_tarefas")
public class Tarefa extends TenantEntity {

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Enumerated(EnumType.STRING)
    private PrioridadeTipo prioridade = PrioridadeTipo.MEDIA;

    private LocalDate prazo;

    private Boolean concluida = false;

    @Column(name = "concluida_em")
    private LocalDateTime concluidaEm;

    @Column(name = "concluida_por")
    private UUID concluidaPor;

    @Column(name = "created_by")
    private UUID createdBy;
}
