package com.jurisflow.modules.googlecalendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.config.GoogleCalendarConfig;
import com.jurisflow.config.TokenEncryptionService;
import com.jurisflow.modules.googlecalendar.dto.GoogleCalendarEventResponse;
import com.jurisflow.modules.googlecalendar.dto.GoogleCalendarListItem;
import com.jurisflow.modules.googlecalendar.dto.GoogleCalendarStatusResponse;
import com.jurisflow.modules.tenant.TenantRole;
import com.jurisflow.security.TenantAccessGuard;
import com.jurisflow.security.TenantContext;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private static final long STATE_EXPIRATION_MS = 10 * 60 * 1000L; // 10 minutos
    private static final int REFRESH_BATCH_SIZE = 50;
    private static final long CACHE_TTL_MS = 60_000L; // 60 segundos
    private static final int MAX_PAGES = 10;
    private static final int MAX_CACHE_ENTRIES = 200;

    private final Map<String, CachedEvents> eventsCache = new ConcurrentHashMap<>();

    private record CachedEvents(List<GoogleCalendarEventResponse> events, long cachedAt) {}

    private final TenantGoogleCalendarRepository tenantGoogleCalendarRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final GoogleCalendarConfig googleCalendarConfig;
    private final TenantAccessGuard tenantAccessGuard;
    private final RestTemplate restTemplate;

    public void invalidateCache(UUID tenantId) {
        if (tenantId != null) {
            eventsCache.keySet().removeIf(key -> key.startsWith(tenantId + ":"));
        }
    }

    private void ensureConfigured() {
        if (!googleCalendarConfig.isConfigured()) {
            throw new BusinessException("Google Calendar não configurado.");
        }
    }

    @Transactional(readOnly = true)
    public GoogleCalendarStatusResponse getStatus(UUID tenantId) {
        ensureConfigured();
        if (tenantId == null) {
            throw new BusinessException("Tenant não informado.");
        }

        return tenantGoogleCalendarRepository.findByTenantId(tenantId)
                .map(tgc -> new GoogleCalendarStatusResponse(
                        "ACTIVE".equalsIgnoreCase(tgc.getStatus()),
                        tgc.getConnectedEmail(),
                        tgc.getStatus(),
                        tgc.getCreatedAt(),
                        tgc.getCalendarId() != null ? tgc.getCalendarId() : "primary"
                ))
                .orElseGet(() -> new GoogleCalendarStatusResponse(false, null, "DISCONNECTED", null, "primary"));
    }

    public String getAuthorizationUrl(UUID tenantId, UUID userId) {
        ensureConfigured();
        if (tenantId == null) {
            throw new BusinessException("Tenant não informado.");
        }
        if (userId == null) {
            throw new BusinessException("Usuário obrigatório para conexão com o Google Calendar.");
        }

        String state = generateSignedState(tenantId, userId);

        return UriComponentsBuilder.fromHttpUrl("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", googleCalendarConfig.getClientId())
                .queryParam("redirect_uri", googleCalendarConfig.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/userinfo.email openid")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    @Transactional
    public void handleCallback(String code, String state) {
        ensureConfigured();
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new BusinessException("Parâmetros de callback inválidos.");
        }

        ValidatedState vs = validateAndExtractState(state);
        UUID tenantId = vs.tenantId();
        UUID userId = vs.userId();

        // Valida que o usuário que iniciou o fluxo ainda é ADMIN/OWNER ativo do tenant
        tenantAccessGuard.requireMinRole(tenantId, userId, TenantRole.ADMIN);

        TenantContext.setCurrentTenantId(tenantId);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("code", code);
            body.add("client_id", googleCalendarConfig.getClientId());
            body.add("client_secret", googleCalendarConfig.getClientSecret());
            body.add("redirect_uri", googleCalendarConfig.getRedirectUri());
            body.add("grant_type", "authorization_code");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<JsonNode> response;
            try {
                response = restTemplate.postForEntity("https://oauth2.googleapis.com/token", request, JsonNode.class);
            } catch (Exception e) {
                log.error("Erro ao trocar code por token no Google OAuth para tenant {}: {}", tenantId, e.getMessage(), e);
                throw new BusinessException("Erro ao autenticar com o Google: " + e.getMessage());
            }

            JsonNode tokenData = response.getBody();
            if (tokenData == null || !tokenData.has("access_token")) {
                throw new BusinessException("Google não retornou access_token válido.");
            }

            String accessToken = tokenData.get("access_token").asText();
            String refreshToken = tokenData.has("refresh_token") ? tokenData.get("refresh_token").asText() : null;
            int expiresIn = tokenData.has("expires_in") ? tokenData.get("expires_in").asInt() : 3600;
            OffsetDateTime tokenExpiry = OffsetDateTime.now().plusSeconds(expiresIn - 60);

            String connectedEmail = "primary";
            try {
                HttpHeaders infoHeaders = new HttpHeaders();
                infoHeaders.setBearerAuth(accessToken);
                ResponseEntity<JsonNode> infoResponse = restTemplate.exchange(
                        "https://www.googleapis.com/oauth2/v2/userinfo",
                        HttpMethod.GET,
                        new HttpEntity<>(infoHeaders),
                        JsonNode.class
                );
                if (infoResponse.getBody() != null && infoResponse.getBody().has("email")) {
                    connectedEmail = infoResponse.getBody().get("email").asText();
                } else {
                    HttpHeaders calHeaders = new HttpHeaders();
                    calHeaders.setBearerAuth(accessToken);
                    HttpEntity<?> calRequest = new HttpEntity<>(calHeaders);
                    ResponseEntity<JsonNode> calResponse = restTemplate.exchange(
                            "https://www.googleapis.com/calendar/v3/calendars/primary",
                            HttpMethod.GET,
                            calRequest,
                            JsonNode.class
                    );
                    if (calResponse.getBody() != null && calResponse.getBody().has("id")) {
                        connectedEmail = calResponse.getBody().get("id").asText();
                    }
                }
            } catch (Exception e) {
                log.warn("Não foi possível obter email do Google: {}", e.getMessage());
            }

            String encryptedAccessToken = tokenEncryptionService.encrypt(accessToken);

            Optional<TenantGoogleCalendar> existingOpt = tenantGoogleCalendarRepository.findByTenantId(tenantId);
            TenantGoogleCalendar tgc;
            if (existingOpt.isPresent()) {
                tgc = existingOpt.get();
                tgc.setConnectedEmail(connectedEmail);
                tgc.setAccessToken(encryptedAccessToken);
                if (refreshToken != null && !refreshToken.isBlank()) {
                    tgc.setRefreshToken(tokenEncryptionService.encrypt(refreshToken));
                }
                tgc.setTokenExpiry(tokenExpiry);
                tgc.setStatus("ACTIVE");
                tgc.setConnectedBy(userId);
            } else {
                if (refreshToken == null || refreshToken.isBlank()) {
                    throw new BusinessException("Refresh token não fornecido pelo Google. Tente reconectar.");
                }
                tgc = TenantGoogleCalendar.builder()
                        .tenantId(tenantId)
                        .connectedEmail(connectedEmail)
                        .calendarId("primary")
                        .accessToken(encryptedAccessToken)
                        .refreshToken(tokenEncryptionService.encrypt(refreshToken))
                        .tokenExpiry(tokenExpiry)
                        .status("ACTIVE")
                        .connectedBy(userId)
                        .build();
            }

            tenantGoogleCalendarRepository.save(tgc);
            invalidateCache(tenantId);
            log.info("Google Calendar conectado com sucesso para tenant {} pelo usuário {}", tenantId, userId);
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional(readOnly = true)
    public List<GoogleCalendarListItem> listCalendars(UUID tenantId) {
        ensureConfigured();
        if (tenantId == null) {
            throw new BusinessException("Tenant não informado.");
        }

        TenantGoogleCalendar tgc = tenantGoogleCalendarRepository.findByTenantIdAndStatus(tenantId, "ACTIVE")
                .orElseThrow(() -> BusinessException.notFound("Integração Google Calendar"));

        if (tgc.getTokenExpiry().isBefore(OffsetDateTime.now())) {
            refreshToken(tgc);
            tgc = tenantGoogleCalendarRepository.findByTenantIdAndStatus(tenantId, "ACTIVE")
                    .orElseThrow(() -> BusinessException.notFound("Integração Google Calendar"));
        }

        String accessToken = tokenEncryptionService.decrypt(tgc.getAccessToken());
        return listCalendarsInternal(tgc, accessToken);
    }

    private List<GoogleCalendarListItem> listCalendarsInternal(TenantGoogleCalendar tgc, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "https://www.googleapis.com/calendar/v3/users/me/calendarList",
                    HttpMethod.GET,
                    entity,
                    JsonNode.class
            );

            JsonNode root = response.getBody();
            List<GoogleCalendarListItem> result = new ArrayList<>();
            if (root != null && root.has("items")) {
                for (JsonNode item : root.get("items")) {
                    result.add(new GoogleCalendarListItem(
                            item.has("id") ? item.get("id").asText() : "",
                            item.has("summary") ? item.get("summary").asText() : "Sem nome",
                            item.has("backgroundColor") ? item.get("backgroundColor").asText() : "#4285f4",
                            item.has("accessRole") ? item.get("accessRole").asText() : "reader",
                            item.has("primary") && item.get("primary").asBoolean()
                    ));
                }
            }
            return result;
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Token revogado ao listar calendários para tenant {}.", tgc.getTenantId());
            tgc.setStatus("EXPIRED");
            tgc.setAccessToken("");
            tgc.setRefreshToken("");
            tenantGoogleCalendarRepository.save(tgc);
            invalidateCache(tgc.getTenantId());
            throw new BusinessException("Conexão com Google Calendar expirada. Reconecte para continuar.");
        } catch (HttpClientErrorException e) {
            String msg = e.getMessage();
            if (e.getStatusCode().value() == 401
                    || (msg != null && (msg.contains("invalid_token") || msg.contains("invalid_grant")))) {
                log.warn("Token revogado ao listar calendários para tenant {}.", tgc.getTenantId());
                tgc.setStatus("EXPIRED");
                tgc.setAccessToken("");
                tgc.setRefreshToken("");
                tenantGoogleCalendarRepository.save(tgc);
                invalidateCache(tgc.getTenantId());
                throw new BusinessException("Conexão com Google Calendar expirada. Reconecte para continuar.");
            }
            log.error("Erro ao listar calendários para tenant {}: {}", tgc.getTenantId(), e.getMessage(), e);
            throw new BusinessException("Erro ao listar calendários do Google.");
        } catch (Exception e) {
            log.error("Erro ao listar calendários para tenant {}: {}", tgc.getTenantId(), e.getMessage(), e);
            throw new BusinessException("Erro ao listar calendários do Google.");
        }
    }

    @Transactional
    public void updateCalendarId(UUID tenantId, String calendarId) {
        ensureConfigured();
        if (tenantId == null) {
            throw new BusinessException("Tenant não informado.");
        }
        if (calendarId == null || calendarId.isBlank()) {
            throw new BusinessException("calendarId não pode ser vazio.");
        }

        TenantGoogleCalendar tgc = tenantGoogleCalendarRepository.findByTenantIdAndStatus(tenantId, "ACTIVE")
                .orElseThrow(() -> BusinessException.notFound("Integração Google Calendar"));

        // Validação: se não é "all", verificar que o calendarId existe na conta do tenant
        if (!"all".equalsIgnoreCase(calendarId.trim())) {
            // Busca token para chamar a API
            if (tgc.getTokenExpiry().isBefore(OffsetDateTime.now())) {
                refreshToken(tgc);
                tgc = tenantGoogleCalendarRepository.findByTenantIdAndStatus(tenantId, "ACTIVE")
                        .orElseThrow(() -> BusinessException.notFound("Integração Google Calendar"));
            }
            String accessToken = tokenEncryptionService.decrypt(tgc.getAccessToken());
            List<GoogleCalendarListItem> availableCalendars = listCalendarsInternal(tgc, accessToken);
            boolean exists = availableCalendars.stream()
                    .anyMatch(cal -> calendarId.trim().equals(cal.id()));
            if (!exists) {
                throw new BusinessException("Calendário não encontrado na conta Google conectada.");
            }
        }

        tgc.setCalendarId(calendarId.trim());
        tenantGoogleCalendarRepository.save(tgc);
        invalidateCache(tenantId);
        log.info("CalendarId atualizado para '{}' no tenant {}", calendarId, tenantId);
    }

    @Transactional
    public void disconnect(UUID tenantId) {
        ensureConfigured();
        if (tenantId == null) {
            throw new BusinessException("Tenant não informado.");
        }

        TenantGoogleCalendar tgc = tenantGoogleCalendarRepository.findByTenantId(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Integração Google Calendar"));

        tgc.setStatus("DISCONNECTED");
        tgc.setAccessToken("");   // Limpa token criptografado
        tgc.setRefreshToken("");  // Limpa token criptografado
        tgc.setTokenExpiry(OffsetDateTime.now()); // Expira imediatamente
        tenantGoogleCalendarRepository.save(tgc);
        invalidateCache(tenantId);
        log.info("Google Calendar desconectado para tenant {}", tenantId);
    }

    @Scheduled(fixedRate = 300000)
    public void refreshExpiredTokens() {
        if (!googleCalendarConfig.isConfigured()) {
            return;
        }

        OffsetDateTime threshold = OffsetDateTime.now().plusMinutes(5);
        List<TenantGoogleCalendar> expiring = tenantGoogleCalendarRepository
                .findByStatusAndTokenExpiryBefore("ACTIVE", threshold, PageRequest.of(0, REFRESH_BATCH_SIZE));

        for (TenantGoogleCalendar record : expiring) {
            try {
                refreshToken(record);
            } catch (Exception e) {
                log.error("Erro ao renovar token do Google Calendar para tenant {}: {}", record.getTenantId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void refreshToken(TenantGoogleCalendar record) {
        String rawRefreshToken = tokenEncryptionService.decrypt(record.getRefreshToken());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", googleCalendarConfig.getClientId());
        body.add("client_secret", googleCalendarConfig.getClientSecret());
        body.add("refresh_token", rawRefreshToken);
        body.add("grant_type", "refresh_token");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<JsonNode> response;
        try {
            response = restTemplate.postForEntity("https://oauth2.googleapis.com/token", request, JsonNode.class);
        } catch (HttpClientErrorException e) {
            log.warn("Falha HTTP ao renovar token para tenant {}: {}", record.getTenantId(), e.getMessage());
            record.setStatus("EXPIRED");
            record.setAccessToken("");
            record.setRefreshToken("");
            tenantGoogleCalendarRepository.save(record);
            invalidateCache(record.getTenantId());
            return;
        } catch (Exception e) {
            log.error("Erro inesperado ao renovar token para tenant {}: {}", record.getTenantId(), e.getMessage());
            return;
        }

        JsonNode tokenData = response.getBody();
        if (tokenData != null && tokenData.has("error")) {
            String error = tokenData.get("error").asText();
            if ("invalid_grant".equals(error)) {
                log.warn("Refresh token revogado para tenant {}. Marcando como EXPIRED.", record.getTenantId());
                record.setStatus("EXPIRED");
                record.setAccessToken("");
                record.setRefreshToken("");
                tenantGoogleCalendarRepository.save(record);
                invalidateCache(record.getTenantId());
                return;
            }
        }

        if (tokenData != null && tokenData.has("access_token")) {
            String newAccessToken = tokenData.get("access_token").asText();
            int expiresIn = tokenData.has("expires_in") ? tokenData.get("expires_in").asInt() : 3600;
            record.setAccessToken(tokenEncryptionService.encrypt(newAccessToken));
            record.setTokenExpiry(OffsetDateTime.now().plusSeconds(expiresIn - 60));
            if (tokenData.has("refresh_token")) {
                record.setRefreshToken(tokenEncryptionService.encrypt(tokenData.get("refresh_token").asText()));
            }
            tenantGoogleCalendarRepository.save(record);
            invalidateCache(record.getTenantId());
            log.info("Token do Google Calendar renovado com sucesso para tenant {}", record.getTenantId());
        }
    }

    @Transactional
    public List<GoogleCalendarEventResponse> getEvents(UUID tenantId, LocalDateTime timeMin, LocalDateTime timeMax) {
        ensureConfigured();
        if (tenantId == null) {
            throw new BusinessException("Tenant não informado.");
        }

        String cacheKey = tenantId + ":" + timeMin + ":" + timeMax;
        CachedEvents cached = eventsCache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.cachedAt) < CACHE_TTL_MS) {
            return cached.events;
        }

        TenantGoogleCalendar tgc = tenantGoogleCalendarRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException("Google Calendar não conectado para este escritório."));

        if (!"ACTIVE".equalsIgnoreCase(tgc.getStatus())) {
            throw new BusinessException("Google Calendar não está ativo para este escritório.");
        }

        if (tgc.getTokenExpiry().isBefore(OffsetDateTime.now())) {
            refreshToken(tgc);
        }

        String decryptedAccessToken = tokenEncryptionService.decrypt(tgc.getAccessToken());

        List<GoogleCalendarEventResponse> allEvents = new ArrayList<>();

        if ("all".equalsIgnoreCase(tgc.getCalendarId())) {
            List<GoogleCalendarListItem> calendars = listCalendarsInternal(tgc, decryptedAccessToken);
            for (GoogleCalendarListItem cal : calendars) {
                try {
                    allEvents.addAll(fetchEventsFromCalendar(tgc, decryptedAccessToken, cal.id(), timeMin, timeMax));
                } catch (Exception e) {
                    log.warn("Erro ao buscar eventos do calendário '{}' para tenant {}: {}", cal.id(), tenantId, e.getMessage());
                }
            }
        } else {
            String calendarId = (tgc.getCalendarId() != null && !tgc.getCalendarId().isBlank()) ? tgc.getCalendarId() : "primary";
            allEvents.addAll(fetchEventsFromCalendar(tgc, decryptedAccessToken, calendarId, timeMin, timeMax));
        }

        // Limite de tamanho para evitar crescimento indefinido do cache em memória
        if (eventsCache.size() >= MAX_CACHE_ENTRIES) {
            eventsCache.clear();
        }
        eventsCache.put(cacheKey, new CachedEvents(allEvents, System.currentTimeMillis()));
        return allEvents;
    }

    private List<GoogleCalendarEventResponse> fetchEventsFromCalendar(TenantGoogleCalendar tgc, String accessToken, String calendarId, LocalDateTime timeMin, LocalDateTime timeMax) {
        ZoneId zone = ZoneId.systemDefault();
        String timeMinStr = timeMin != null ? timeMin.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null;
        String timeMaxStr = timeMax != null ? timeMax.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null;

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("https://www.googleapis.com/calendar/v3")
                .pathSegment("calendars", calendarId, "events")
                .queryParam("singleEvents", "true")
                .queryParam("orderBy", "startTime");

        if (timeMinStr != null) {
            builder.queryParam("timeMin", timeMinStr);
        }
        if (timeMaxStr != null) {
            builder.queryParam("timeMax", timeMaxStr);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<JsonNode> response;
        try {
            response = restTemplate.exchange(builder.toUriString(), HttpMethod.GET, entity, JsonNode.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Token do Google Calendar revogado para tenant {}. Marcando como EXPIRED.", tgc.getTenantId());
            tgc.setStatus("EXPIRED");
            tenantGoogleCalendarRepository.save(tgc);
            invalidateCache(tgc.getTenantId());
            throw new BusinessException("Conexão com Google Calendar expirada. Reconecte para continuar.");
        } catch (HttpClientErrorException e) {
            String msg = e.getMessage();
            if (e.getStatusCode().value() == 401
                    || (msg != null && (msg.contains("invalid_token") || msg.contains("invalid_grant")))) {
                log.warn("Token do Google Calendar inválido para tenant {}. Marcando como EXPIRED.", tgc.getTenantId());
                tgc.setStatus("EXPIRED");
                tenantGoogleCalendarRepository.save(tgc);
                invalidateCache(tgc.getTenantId());
                throw new BusinessException("Conexão com Google Calendar expirada. Reconecte para continuar.");
            }
            log.error("Erro ao buscar eventos do calendário {} para tenant {}: {}", calendarId, tgc.getTenantId(), e.getMessage(), e);
            throw new BusinessException("Erro ao buscar eventos no Google Calendar: " + e.getMessage());
        } catch (Exception e) {
            log.error("Erro inesperado ao buscar eventos do calendário {} para tenant {}: {}", calendarId, tgc.getTenantId(), e.getMessage(), e);
            throw new BusinessException("Erro ao buscar eventos no Google Calendar.");
        }

        List<GoogleCalendarEventResponse> result = new ArrayList<>();
        JsonNode root = response.getBody();
        int pageCount = 0;

        while (root != null && root.has("items") && pageCount < MAX_PAGES) {
            pageCount++;
            for (JsonNode item : root.get("items")) {
                String id = item.path("id").asText(null);
                String summary = item.path("summary").asText("");
                String description = item.path("description").asText(null);
                String htmlLink = item.path("htmlLink").asText(null);

                LocalDateTime start = parseDateTime(item.path("start"));
                LocalDateTime end = parseDateTime(item.path("end"));

                result.add(new GoogleCalendarEventResponse(id, summary, description, start, end, htmlLink));
            }

            if (root.hasNonNull("nextPageToken") && pageCount < MAX_PAGES) {
                String nextPageToken = root.get("nextPageToken").asText();
                UriComponentsBuilder nextBuilder = UriComponentsBuilder.fromHttpUrl("https://www.googleapis.com/calendar/v3")
                        .pathSegment("calendars", calendarId, "events")
                        .queryParam("singleEvents", "true")
                        .queryParam("orderBy", "startTime")
                        .queryParam("pageToken", nextPageToken);

                if (timeMinStr != null) {
                    nextBuilder.queryParam("timeMin", timeMinStr);
                }
                if (timeMaxStr != null) {
                    nextBuilder.queryParam("timeMax", timeMaxStr);
                }

                try {
                    ResponseEntity<JsonNode> nextResponse = restTemplate.exchange(
                            nextBuilder.toUriString(), HttpMethod.GET, entity, JsonNode.class);
                    root = nextResponse.getBody();
                } catch (Exception e) {
                    log.warn("Erro ao buscar página seguinte de eventos para tenant {}: {}", tgc.getTenantId(), e.getMessage());
                    break;
                }
            } else {
                break;
            }
        }

        return result;
    }

    private String generateSignedState(UUID tenantId, UUID userId) {
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String payload = tenantId + ":" + userId + ":" + timestamp + ":" + nonce;
        String signature = computeHmac(payload);
        String raw = payload + "." + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private record ValidatedState(UUID tenantId, UUID userId) {}

    private ValidatedState validateAndExtractState(String state) {
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("State com codificação Base64 inválida.");
        }

        int dotIndex = raw.lastIndexOf('.');
        if (dotIndex == -1) {
            throw new BusinessException("State sem assinatura HMAC válida.");
        }

        String payload = raw.substring(0, dotIndex);
        String signature = raw.substring(dotIndex + 1);

        String expectedSignature = computeHmac(payload);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("Assinatura do state OAuth inválida ou adulterada.");
        }

        String[] parts = payload.split(":");
        if (parts.length < 4) {
            throw new BusinessException("Payload do state incompleto.");
        }

        UUID tenantId;
        UUID userId;
        long timestamp;
        try {
            tenantId = UUID.fromString(parts[0]);
            userId = UUID.fromString(parts[1]);
            timestamp = Long.parseLong(parts[2]);
        } catch (Exception e) {
            throw new BusinessException("Dados do state inválidos.");
        }

        long now = System.currentTimeMillis();
        if (now - timestamp > STATE_EXPIRATION_MS || timestamp > now + 60000L) {
            throw new BusinessException("Fluxo de autorização expirado (limite de 10 minutos). Por favor, tente novamente.");
        }

        return new ValidatedState(tenantId, userId);
    }

    private String computeHmac(String data) {
        try {
            String derivedHmacKey = googleCalendarConfig.getTokenEncryptionKey() + ":oauth-state-hmac";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedKey = digest.digest(derivedHmacKey.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(hashedKey, "HmacSHA256");
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo de criptografia não disponível: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao calcular HMAC do state: " + e.getMessage(), e);
        }
    }

    private LocalDateTime parseDateTime(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.hasNonNull("dateTime")) {
            try {
                return OffsetDateTime.parse(node.get("dateTime").asText()).toLocalDateTime();
            } catch (Exception e) {
                try {
                    return LocalDateTime.parse(node.get("dateTime").asText());
                } catch (Exception ex) {
                    log.warn("Erro ao parsear dateTime: {}", node.get("dateTime").asText());
                }
            }
        }
        if (node.hasNonNull("date")) {
            try {
                return LocalDate.parse(node.get("date").asText()).atStartOfDay();
            } catch (Exception e) {
                log.warn("Erro ao parsear date: {}", node.get("date").asText());
            }
        }
        return null;
    }
}
