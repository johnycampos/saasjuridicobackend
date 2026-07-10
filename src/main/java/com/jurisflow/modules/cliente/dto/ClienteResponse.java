package com.jurisflow.modules.cliente.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ClienteResponse(
    UUID id,
    UUID tenantId,
    String nome,
    String cpfCnpj,
    String telefone,
    String email,
    String endereco,
    String observacoes,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
