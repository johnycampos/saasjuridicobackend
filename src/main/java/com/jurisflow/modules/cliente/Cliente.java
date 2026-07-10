package com.jurisflow.modules.cliente;

import com.jurisflow.shared.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "clientes")
public class Cliente extends TenantEntity {

    @Column(nullable = false)
    private String nome;

    @Column(name = "cpf_cnpj")
    private String cpfCnpj;

    private String telefone;
    private String email;

    @Column(columnDefinition = "TEXT")
    private String endereco;

    @Column(columnDefinition = "TEXT")
    private String observacoes;
}
