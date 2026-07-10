package com.jurisflow.modules.processolink;

import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.processolink.dto.ProcessoLinkRequest;
import com.jurisflow.modules.processolink.dto.ProcessoLinkResponse;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcessoLinkService {

    private final ProcessoLinkRepository processoLinkRepository;
    private final ProcessoRepository processoRepository;

    @Transactional
    public ProcessoLinkResponse create(UUID processoId, ProcessoLinkRequest request, UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        processoRepository.findByIdAndTenantId(processoId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Processo"));

        ProcessoLink link = new ProcessoLink();
        link.setTenantId(tenantId);
        link.setProcessoId(processoId);
        link.setNomeArquivo(request.nomeArquivo());
        link.setUrl(request.url());
        link.setCreatedBy(principal.getId());

        return toResponse(processoLinkRepository.save(link));
    }

    public List<ProcessoLinkResponse> listByProcesso(UUID processoId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        processoRepository.findByIdAndTenantId(processoId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Processo"));
        return processoLinkRepository.findByProcessoIdOrderByCreatedAtDesc(processoId)
                .stream().map(this::toResponse).toList();
    }

    private ProcessoLinkResponse toResponse(ProcessoLink l) {
        return new ProcessoLinkResponse(l.getId(), l.getTenantId(), l.getProcessoId(),
                l.getNomeArquivo(), l.getUrl(), l.getCreatedBy(), l.getCreatedAt());
    }
}
