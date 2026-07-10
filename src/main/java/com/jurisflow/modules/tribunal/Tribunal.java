package com.jurisflow.modules.tribunal;

import com.jurisflow.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "tribunais")
public class Tribunal extends BaseEntity {

    @Column(nullable = false)
    private String sigla;

    private String nome;

    private Boolean ativo = true;

    @Column(name = "created_by")
    private UUID createdBy;
}
