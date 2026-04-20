package com.jurisflow.modules.board;

import com.jurisflow.modules.board.dto.BoardColumnRequest;
import com.jurisflow.modules.board.dto.BoardColumnResponse;
import com.jurisflow.modules.board.dto.ReorderRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping("/groups/{groupId}/board")
    public ResponseEntity<List<BoardColumnResponse>> getBoardByGroup(@PathVariable UUID groupId) {
        return ResponseEntity.ok(boardService.getBoardByGroup(groupId));
    }

    @PostMapping("/board/columns")
    public ResponseEntity<BoardColumnResponse> createColumn(@Valid @RequestBody BoardColumnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(boardService.createColumn(request));
    }

    @PutMapping("/board/columns/{id}")
    public ResponseEntity<BoardColumnResponse> updateColumn(
            @PathVariable UUID id,
            @Valid @RequestBody BoardColumnRequest request) {
        return ResponseEntity.ok(boardService.updateColumn(id, request));
    }

    @DeleteMapping("/board/columns/{id}")
    public ResponseEntity<Void> deleteColumn(@PathVariable UUID id) {
        boardService.deleteColumn(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/board/columns/reorder")
    public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderRequest request) {
        boardService.reorderColumns(request);
        return ResponseEntity.noContent().build();
    }
}
