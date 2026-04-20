package com.jurisflow.modules.board.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ReorderRequest(@NotEmpty List<UUID> columnIds) {}
