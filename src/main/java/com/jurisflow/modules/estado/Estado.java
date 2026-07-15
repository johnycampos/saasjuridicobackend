package com.jurisflow.modules.estado;

import com.jurisflow.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "estados")
public class Estado extends BaseEntity {

    @Column(nullable = false)
    private String sigla;

    @Column(nullable = false)
    private String nome;
}
