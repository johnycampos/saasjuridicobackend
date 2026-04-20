package com.jurisflow.modules.board;

import com.jurisflow.shared.entity.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "board_columns")
public class BoardColumn extends TenantEntity {

    @Column(name = "group_id")
    private UUID groupId;

    @Column(nullable = false)
    private String nome;

    private Integer posicao = 0;

    private String cor = "#6B7280";
}
