package com.jurisflow.modules.cliente;

import com.jurisflow.modules.cliente.dto.ClienteRequest;
import com.jurisflow.modules.cliente.dto.ClienteResponse;
import com.jurisflow.security.TenantContext;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClienteService {

    private final ClienteRepository clienteRepository;

    @Transactional
    public ClienteResponse create(ClienteRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        Cliente cliente = new Cliente();
        cliente.setTenantId(tenantId);
        apply(cliente, request);

        return toResponse(clienteRepository.save(cliente));
    }

    public Page<ClienteResponse> list(String q, Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Page<Cliente> page = (q == null || q.isBlank())
                ? clienteRepository.findByTenantIdOrderByNomeAsc(tenantId, pageable)
                : clienteRepository.findByTenantIdAndNomeContainingIgnoreCaseOrderByNomeAsc(tenantId, q, pageable);
        return page.map(this::toResponse);
    }

    public ClienteResponse getById(UUID id) {
        return toResponse(findClienteInTenant(id));
    }

    @Transactional
    public ClienteResponse update(UUID id, ClienteRequest request) {
        Cliente cliente = findClienteInTenant(id);
        apply(cliente, request);
        return toResponse(clienteRepository.save(cliente));
    }

    private Cliente findClienteInTenant(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return clienteRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Cliente"));
    }

    private void apply(Cliente cliente, ClienteRequest request) {
        cliente.setNome(request.nome());
        cliente.setCpfCnpj(request.cpfCnpj());
        cliente.setTelefone(request.telefone());
        cliente.setEmail(request.email());
        cliente.setEndereco(request.endereco());
        cliente.setObservacoes(request.observacoes());
        cliente.setDataNascimento(request.dataNascimento());
    }

    private ClienteResponse toResponse(Cliente c) {
        return new ClienteResponse(c.getId(), c.getTenantId(), c.getNome(), c.getCpfCnpj(),
                c.getTelefone(), c.getEmail(), c.getEndereco(), c.getObservacoes(), c.getDataNascimento(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
