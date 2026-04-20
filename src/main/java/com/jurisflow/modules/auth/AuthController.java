package com.jurisflow.modules.auth;

import com.jurisflow.modules.auth.dto.RefreshTokenRequest;
import com.jurisflow.modules.auth.dto.TokenResponse;
import com.jurisflow.modules.user.UserService;
import com.jurisflow.modules.user.dto.UserResponse;
import com.jurisflow.security.JwtTokenProvider;
import com.jurisflow.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider tokenProvider;
    private final UserService userService;

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        if (!tokenProvider.validateToken(request.refreshToken())) {
            return ResponseEntity.badRequest().build();
        }
        var userId = tokenProvider.getUserIdFromToken(request.refreshToken());
        var email = tokenProvider.getEmailFromToken(request.refreshToken());
        return ResponseEntity.ok(new TokenResponse(
                tokenProvider.generateAccessToken(userId, email),
                tokenProvider.generateRefreshToken(userId, email)
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getById(principal.getId()));
    }
}
