package com.jurisflow.modules.importacao;

import com.jurisflow.modules.importacao.dto.ImportResultResponse;
import com.jurisflow.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        byte[] planilha = importService.gerarPlanilhaModelo(groupId, principal.getId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-processos.xlsx\"")
                .body(planilha);
    }

    @PostMapping("/upload")
    public ResponseEntity<ImportResultResponse> upload(
            @PathVariable UUID groupId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(importService.importar(groupId, file, principal));
    }
}
