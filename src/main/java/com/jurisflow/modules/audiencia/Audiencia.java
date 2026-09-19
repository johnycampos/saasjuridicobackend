package com.jurisflow.modules.audiencia;

import com.jurisflow.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audiencias")
public class Audiencia extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String tipo = "AUDIENCIA";

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "hora")
    private LocalTime hora;

    @Column(length = 255)
    private String vara;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "AGENDADA";

    @Column(name = "created_by")
    private UUID createdBy;
}
