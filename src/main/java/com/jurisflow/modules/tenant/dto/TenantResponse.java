package com.jurisflow.modules.tenant.dto;

import java.util.UUID;

public record TenantResponse(
    UUID id,
    String nome,
    String slug,
    String cnpj,
    String telefone,
    String endereco,
    String logoUrl,
    String plano,
    Integer maxMembros,
    Boolean ativo
) {}
