package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.config.BackofficeProperties;
import com.cloudhandson.vpdbackoffice.domain.audit.AuditEvent;
import com.cloudhandson.vpdbackoffice.domain.probe.ProbeCommand;
import com.cloudhandson.vpdbackoffice.domain.probe.ProbeResult;
import com.cloudhandson.vpdbackoffice.domain.probe.ProbeStatus;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedColumn;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedObject;
import com.cloudhandson.vpdbackoffice.domain.token.BearerTokenRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OrdsProbeService {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };

  private final BearerTokenService tokenService;
  private final ProtectedObjectService protectedObjectService;
  private final AuditService auditService;
  private final ProbeErrorClassifier errorClassifier;
  private final RestTemplate ordsRestTemplate;
  private final ObjectMapper objectMapper;
  private final BackofficeProperties properties;
  private final Clock clock;

  public OrdsProbeService(
      BearerTokenService tokenService,
      ProtectedObjectService protectedObjectService,
      AuditService auditService,
      ProbeErrorClassifier errorClassifier,
      RestTemplate ordsRestTemplate,
      ObjectMapper objectMapper,
      BackofficeProperties properties,
      Clock clock
  ) {
    this.tokenService = tokenService;
    this.protectedObjectService = protectedObjectService;
    this.auditService = auditService;
    this.errorClassifier = errorClassifier;
    this.ordsRestTemplate = ordsRestTemplate;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.clock = clock;
  }

  public ProbeResult runProbe(ProbeCommand command) {
    if (properties.ords().baseUrl() == null || properties.ords().baseUrl().isBlank()) {
      return auditAndReturn(command, ProbeResult.blocked(
          ProbeStatus.ORDS_NOT_CONFIGURED,
          ProbeStatus.ORDS_NOT_CONFIGURED.name(),
          "ORDS base URL이 설정되지 않았습니다. 실제 ORDS 도메인을 BACKOFFICE_ORDS_BASE_URL에 설정한 뒤 백오피스를 재시작하세요."
      ));
    }

    BearerTokenRecord token = tokenService.findById(command.keyId());
    if (token == null) {
      return auditAndReturn(command, ProbeResult.blocked(
          ProbeStatus.TOKEN_NOT_FOUND, "TOKEN_NOT_FOUND", "토큰을 찾을 수 없습니다."));
    }
    if (!token.active(LocalDateTime.now(clock.withZone(ZoneId.systemDefault())))) {
      return auditAndReturn(command, ProbeResult.blocked(
          ProbeStatus.TOKEN_INACTIVE, "TOKEN_INACTIVE", "만료되었거나 회수된 토큰입니다."));
    }
    if (!tokenService.matches(token, command.bearerToken())) {
      return auditAndReturn(command, ProbeResult.blocked(
          ProbeStatus.INVALID_TOKEN, "INVALID_TOKEN", "입력한 Bearer Token이 선택한 key와 일치하지 않습니다."));
    }

    ProtectedObject object;
    try {
      object = protectedObjectService.assertEnabled(command.objectId());
    } catch (AppException e) {
      return auditAndReturn(command, ProbeResult.blocked(
          ProbeStatus.OBJECT_DISABLED, "OBJECT_DISABLED", e.getMessage()));
    }

    try {
      URI uri = buildUri(object.ordsPath(), command.limit());
      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(command.bearerToken());
      ResponseEntity<String> response = ordsRestTemplate.exchange(
          uri, HttpMethod.POST, new HttpEntity<>(headers), String.class);
      ProbeResult result = parseSuccess(response.getBody(), object.objectId());
      return auditAndReturn(command, result);
    } catch (HttpStatusCodeException e) {
      ProbeStatus status = errorClassifier.classify(e.getStatusCode(), e.getResponseBodyAsString());
      return auditAndReturn(command, ProbeResult.blocked(
          status, status.name(), trimMessage(e.getResponseBodyAsString())));
    } catch (ResourceAccessException e) {
      ProbeStatus status = classifyResourceAccess(e);
      return auditAndReturn(command, ProbeResult.blocked(status, status.name(), resourceAccessMessage(status, e)));
    } catch (Exception e) {
      return auditAndReturn(command, ProbeResult.blocked(
          ProbeStatus.INVALID_ORDS_RESPONSE, "INVALID_ORDS_RESPONSE", e.getMessage()));
    }
  }

  private URI buildUri(String ordsPath, int limit) {
    String baseUrl = properties.ords().baseUrl();
    String path = ordsPath.startsWith("/") ? ordsPath.substring(1) : ordsPath;
    return UriComponentsBuilder.fromUriString(baseUrl)
        .path("/")
        .path(path)
        .queryParam("limit", limit)
        .build()
        .toUri();
  }

  private ProbeResult parseSuccess(String body, long objectId) throws Exception {
    JsonNode root = objectMapper.readTree(body);
    JsonNode rowsNode = root.has("rows") ? root.get("rows") : root;
    rowsNode = root.has("items") ? root.get("items") : rowsNode;
    if (!rowsNode.isArray()) {
      throw new AppException("ORDS 응답에 rows 배열이 없습니다.");
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    Set<String> columns = new LinkedHashSet<>();
    for (JsonNode rowNode : rowsNode) {
      Map<String, Object> row = objectMapper.convertValue(rowNode, MAP_TYPE);
      rows.add(row);
      columns.addAll(row.keySet());
    }

    ProbeStatus status = rows.isEmpty() ? ProbeStatus.VPD_DENY_EMPTY_RESULT : ProbeStatus.SUCCESS;
    return new ProbeResult(
        status,
        List.copyOf(columns),
        rows,
        rows.size(),
        findMaskedColumns(objectId, rows),
        null,
        null
    );
  }

  private List<String> findMaskedColumns(long objectId, List<Map<String, Object>> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }
    List<String> sensitiveColumns = protectedObjectService.findColumns(objectId).stream()
        .filter(ProtectedColumn::sensitive)
        .map(column -> column.columnName().toLowerCase(Locale.ROOT))
        .toList();
    if (sensitiveColumns.isEmpty()) {
      return List.of();
    }

    List<String> masked = new ArrayList<>();
    for (String column : sensitiveColumns) {
      boolean present = false;
      boolean allNull = true;
      for (Map<String, Object> row : rows) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
          if (entry.getKey().equalsIgnoreCase(column)) {
            present = true;
            allNull = allNull && entry.getValue() == null;
          }
        }
      }
      if (present && allNull) {
        masked.add(column);
      }
    }
    return masked;
  }

  private ProbeResult auditAndReturn(ProbeCommand command, ProbeResult result) {
    auditService.record(new AuditEvent(
        "ORDS_PROBE",
        command.keyId(),
        command.objectId(),
        result.status().name(),
        result.rowCount(),
        result.errorCode(),
        result.errorMessage()
    ));
    return result;
  }

  private ProbeStatus classifyResourceAccess(ResourceAccessException exception) {
    if (errorClassifier.isTimeout(exception)) {
      return ProbeStatus.ORDS_TIMEOUT;
    }
    if (errorClassifier.isUnavailable(exception)) {
      return ProbeStatus.ORDS_UNAVAILABLE;
    }
    return ProbeStatus.UNKNOWN_ERROR;
  }

  private String resourceAccessMessage(ProbeStatus status, ResourceAccessException exception) {
    String detail = trimMessage(exception.getMessage());
    if (status == ProbeStatus.ORDS_UNAVAILABLE) {
      return "ORDS 서버에 연결할 수 없습니다. BACKOFFICE_ORDS_BASE_URL의 실제 ORDS 도메인, ORDS 실행 상태, 네트워크 접근을 확인하세요. 상세: "
          + detail;
    }
    if (status == ProbeStatus.ORDS_TIMEOUT) {
      return "ORDS 응답 시간이 초과되었습니다. ORDS 상태와 BACKOFFICE_ORDS_TIMEOUT_SECONDS 설정을 확인하세요. 상세: "
          + detail;
    }
    return detail;
  }

  private String trimMessage(String body) {
    if (body == null) {
      return null;
    }
    return body.length() <= 500 ? body : body.substring(0, 500);
  }
}
