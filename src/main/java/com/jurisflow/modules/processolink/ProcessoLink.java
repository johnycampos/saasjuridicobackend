package com.jurisflow.modules.processolink;

import com.jurisflow.shared.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "processo_links")
public class ProcessoLink extends TenantEntity {

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @Column(nullable = false)
    private String url;

    @Column(name = "created_by")
    private UUID createdBy;
}
