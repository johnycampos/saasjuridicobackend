package com.jurisflow.modules.movimento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record PublicacaoSyncItem(
    // vira hash_origem (chave de dedupe, ver indice parcial uq_proc_mov_hash_origem)
    @NotBlank(message = "externalId e obrigatorio")
    String externalId,

    // so guardado em dados_extras — nao participa da dedupe
    String hash,

    @NotNull(message = "dataDisponibilizacao e obrigatoria")
    LocalDate dataDisponibilizacao,

    String siglaTribunal,

    @NotBlank(message = "tipoComunicacao e obrigatorio")
    String tipoComunicacao,

    String tipoDocumento,
    String nomeOrgao,
    String nomeClasse,
    String codigoClasse,
    Long numeroComunicacao,
    String meio,
    String link,
    String texto,
    List<AdvogadoItem> advogados
) {}
