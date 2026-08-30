package com.jurisflow.modules.user;

import com.jurisflow.modules.tenant.TenantMemberRepository;
import com.jurisflow.modules.user.dto.UserResponse;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final TenantMemberRepository tenantMemberRepository;

    /**
     * Ver o proprio perfil sempre e permitido; ver o perfil de outro usuario
     * exige compartilhar algum tenant ativo em comum (evita IDOR — antes
     * qualquer usuario autenticado podia consultar qualquer UUID de usuario).
     */
    public UserResponse getById(UUID id, UUID requesterId) {
        if (!id.equals(requesterId) && !tenantMemberRepository.existsSharedActiveTenant(requesterId, id)) {
            throw BusinessException.forbidden();
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Usuario"));
        return toResponse(user);
    }

    public UserResponse getByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> BusinessException.notFound("Usuario"));
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNome(), user.getAvatarUrl());
    }
}
