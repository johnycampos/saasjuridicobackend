package com.jurisflow.modules.tribunal;

import com.jurisflow.modules.tribunal.dto.TribunalRequest;
import com.jurisflow.modules.tribunal.dto.TribunalResponse;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TribunalService {

    private final TribunalRepository tribunalRepository;

    public List<TribunalResponse> list() {
        return tribunalRepository.findAllByOrderBySiglaAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public TribunalResponse create(TribunalRequest request, UserPrincipal principal) {
        if (tribunalRepository.existsBySiglaIgnoreCase(request.sigla())) {
            throw BusinessException.conflict("Ja existe um tribunal com essa sigla");
        }

        Tribunal tribunal = new Tribunal();
        tribunal.setSigla(request.sigla());
        tribunal.setNome(request.nome());
        tribunal.setCreatedBy(principal.getId());

        return toResponse(tribunalRepository.save(tribunal));
    }

    private TribunalResponse toResponse(Tribunal t) {
        return new TribunalResponse(t.getId(), t.getSigla(), t.getNome(), t.getAtivo(),
                t.getCreatedBy(), t.getCreatedAt());
    }
}
