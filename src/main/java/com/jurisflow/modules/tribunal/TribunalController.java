package com.jurisflow.modules.tribunal;

import com.jurisflow.modules.tribunal.dto.TribunalRequest;
import com.jurisflow.modules.tribunal.dto.TribunalResponse;
import com.jurisflow.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tribunais")
@RequiredArgsConstructor
public class TribunalController {

    private final TribunalService tribunalService;

    @GetMapping
    public ResponseEntity<List<TribunalResponse>> list() {
        return ResponseEntity.ok(tribunalService.list());
    }

    @PostMapping
    public ResponseEntity<TribunalResponse> create(
            @Valid @RequestBody TribunalRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tribunalService.create(request, principal));
    }
}
