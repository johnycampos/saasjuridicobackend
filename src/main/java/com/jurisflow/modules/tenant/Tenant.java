package com.jurisflow.modules.tenant;

import com.jurisflow.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    @Column(nullable = false)
    private String nome;

    @Column(unique = true, nullable = false)
    private String slug;

    private String cnpj;
    private String telefone;
    private String endereco;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(nullable = false)
    private String plano = "FREE";

    @Column(name = "max_membros")
    private Integer maxMembros = 5;

    private Boolean ativo = true;
}
