package com.jurisflow.modules.processo;

import com.jurisflow.config.DataJudConfig;
import com.jurisflow.modules.processo.dto.DataJudSyncResponse;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataJudSyncService {

    private final DataJudConfig dataJudConfig;
    @Qualifier("datajudRestTemplate")
    private final RestTemplate datajudRestTemplate;
    private final ProcessoRepository processoRepository;

    // Rate limit: Map<processoId, ultimo timestamp de sync>
    private final Map<UUID, OffsetDateTime> lastSyncTimes = new ConcurrentHashMap<>();

    public DataJudSyncResponse sincronizar(UUID processoId, UUID tenantId) {
        if (!dataJudConfig.isConfigured()) {
            throw new BusinessException("Sincronização DATAJUD não está configurada.");
        }

        // Valida processo existe no tenant
        Processo processo = processoRepository.findByIdAndTenantId(processoId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Processo"));

        // Rate limit check
        OffsetDateTime lastSync = lastSyncTimes.get(processoId);
        if (lastSync != null) {
            OffsetDateTime allowAfter = lastSync.plusSeconds(dataJudConfig.getRateLimitSeconds());
            OffsetDateTime now = OffsetDateTime.now();
            if (allowAfter.isAfter(now)) {
                long secondsRemaining = Duration.between(now, allowAfter).toSeconds();
                if (secondsRemaining <= 0) {
                    secondsRemaining = 1;
                }
                throw new BusinessException("Aguarde " + secondsRemaining + " segundos antes de solicitar nova atualização.");
            }
        }

        // Monta request
        Map<String, String> body = Map.of(
                "tenantId", tenantId.toString(),
                "numeroProcesso", processo.getNumeroProcesso() != null ? processo.getNumeroProcesso() : ""
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Api-Key", dataJudConfig.getApiKey());

        try {
            ResponseEntity<DataJudSyncResponse> response = datajudRestTemplate.exchange(
                    dataJudConfig.getBaseUrl() + "/processos/atualizar",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    DataJudSyncResponse.class
            );

            // Marca o timestamp de sync
            lastSyncTimes.put(processoId, OffsetDateTime.now());

            return response.getBody() != null ? response.getBody()
                    : new DataJudSyncResponse(true, 0, "Processo atualizado com sucesso.");

        } catch (HttpClientErrorException e) {
            log.error("Erro HTTP ao sincronizar processo {} com DATAJUD: {} {}",
                    processoId, e.getStatusCode(), e.getMessage());
            throw new BusinessException("O serviço DATAJUD retornou um erro. Tente novamente mais tarde.");
        } catch (ResourceAccessException e) {
            log.error("Timeout/erro de rede ao sincronizar processo {} com DATAJUD: {}",
                    processoId, e.getMessage());
            throw new BusinessException("Não foi possível conectar ao serviço DATAJUD. Tente novamente em instantes.");
        } catch (Exception e) {
            log.error("Erro inesperado ao sincronizar processo {} com DATAJUD: {}",
                    processoId, e.getMessage(), e);
            throw new BusinessException("Não foi possível atualizar o processo no momento. Tente novamente.");
        }
    }
}
