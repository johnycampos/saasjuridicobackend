package com.jurisflow.modules.estado;

import com.jurisflow.modules.estado.dto.EstadoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EstadoService {

    private final EstadoRepository estadoRepository;

    public List<EstadoResponse> list() {
        return estadoRepository.findAllByOrderByNomeAsc().stream().map(this::toResponse).toList();
    }

    private EstadoResponse toResponse(Estado e) {
        return new EstadoResponse(e.getId(), e.getSigla(), e.getNome());
    }
}
