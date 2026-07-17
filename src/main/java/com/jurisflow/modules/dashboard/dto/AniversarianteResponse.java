package com.jurisflow.modules.dashboard.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AniversarianteResponse(
    UUID clienteId,
    String nome,
    String telefone,
    LocalDate dataNascimento
) {}
