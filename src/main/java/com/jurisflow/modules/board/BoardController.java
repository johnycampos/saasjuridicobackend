package com.jurisflow.modules.board;

import com.jurisflow.modules.board.dto.BoardColumnRequest;
import com.jurisflow.modules.board.dto.BoardColumnResponse;
import com.jurisflow.modules.board.dto.ReorderRequest;
import com.jurisflow.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping("/groups/{groupId}/board")
    public ResponseEntity<List<BoardColumnResponse>> getBoardByGroup(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(boardService.getBoardByGroup(groupId, principal.getId()));
    }

    @PostMapping("/board/columns")
    public ResponseEntity<BoardColumnResponse> createColumn(
            @Valid @RequestBody BoardColumnRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(boardService.createColumn(request, principal));
    }

    @PutMapping("/board/columns/{id}")
    public ResponseEntity<BoardColumnResponse> updateColumn(
            @PathVariable UUID id,
            @Valid @RequestBody BoardColumnRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(boardService.updateColumn(id, request, principal));
    }

    @DeleteMapping("/board/columns/{id}")
    public ResponseEntity<Void> deleteColumn(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        boardService.deleteColumn(id, principal);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/board/columns/reorder")
    public ResponseEntity<Void> reorder(
            @Valid @RequestBody ReorderRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        boardService.reorderColumns(request, principal);
        return ResponseEntity.noContent().build();
    }
}
