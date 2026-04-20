package com.jurisflow.modules.group;

import com.jurisflow.shared.entity.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "groups")
public class Group extends TenantEntity {

    @Column(nullable = false)
    private String nome;

    private String descricao;

    private String cor = "#3B82F6";

    @Column(name = "created_by")
    private UUID createdBy;
}
