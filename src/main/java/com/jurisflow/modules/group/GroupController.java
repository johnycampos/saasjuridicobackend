package com.jurisflow.modules.group;

import com.jurisflow.modules.group.dto.GroupRequest;
import com.jurisflow.modules.group.dto.GroupResponse;
import com.jurisflow.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    public ResponseEntity<Page<GroupResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(groupService.listByTenant(pageable));
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(
            @Valid @RequestBody GroupRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.create(request, principal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody GroupRequest request) {
        return ResponseEntity.ok(groupService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        groupService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<Void> addMember(
            @PathVariable UUID id,
            @RequestBody Map<String, UUID> body) {
        groupService.addMember(id, body.get("userId"));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        groupService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }
}
