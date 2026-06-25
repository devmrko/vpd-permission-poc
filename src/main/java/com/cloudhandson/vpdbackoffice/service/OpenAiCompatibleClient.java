package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.config.BackofficeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class OpenAiCompatibleClient {

  private final BackofficeProperties properties;
  private final ObjectMapper objectMapper;
  private final RestTemplateBuilder restTemplateBuilder;

  public OpenAiCompatibleClient(
      BackofficeProperties properties,
      ObjectMapper objectMapper,
      RestTemplateBuilder restTemplateBuilder
  ) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restTemplateBuilder = restTemplateBuilder;
  }

  public boolean configured() {
    BackofficeProperties.Ai ai = properties.ai();
    return ai != null
        && ai.enabled()
        && hasText(ai.baseUrl())
        && hasText(ai.model())
        && hasText(ai.apiKey());
  }

  public String modelName() {
    BackofficeProperties.Ai ai = properties.ai();
    return ai == null ? "" : ai.model();
  }

  public String chat(String systemPrompt, String userPrompt) {
    if (!configured()) {
      throw new AppException("AI 호출 설정이 없습니다.");
    }

    BackofficeProperties.Ai ai = properties.ai();
    ObjectNode request = objectMapper.createObjectNode();
    request.put("model", ai.model());
    request.put("temperature", 0.1);
    if (usesCompletionTokenLimit(ai.model())) {
      request.put("max_completion_tokens", 1200);
    } else {
      request.put("max_tokens", 1200);
    }
    ArrayNode messages = request.putArray("messages");
    messages.add(message("system", systemPrompt));
    messages.add(message("user", userPrompt));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(ai.apiKey());

    Duration timeout = ai.timeout() == null ? Duration.ofSeconds(30) : ai.timeout();
    RestTemplate restTemplate = restTemplateBuilder
        .setConnectTimeout(timeout)
        .setReadTimeout(timeout)
        .build();
    ResponseEntity<String> response = restTemplate.postForEntity(
        endpoint(ai.baseUrl()),
        new HttpEntity<>(request.toString(), headers),
        String.class
    );
    return extractAnswer(response.getBody());
  }

  private ObjectNode message(String role, String content) {
    ObjectNode message = objectMapper.createObjectNode();
    message.put("role", role);
    message.put("content", content);
    return message;
  }

  private URI endpoint(String baseUrl) {
    String trimmed = baseUrl.trim();
    if (trimmed.endsWith("/chat/completions")) {
      return URI.create(trimmed);
    }
    String withoutSlash = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    if (withoutSlash.endsWith("/v1")) {
      return URI.create(withoutSlash + "/chat/completions");
    }
    return URI.create(withoutSlash + "/v1/chat/completions");
  }

  private boolean usesCompletionTokenLimit(String model) {
    return model != null && model.startsWith("openai.gpt-5.4");
  }

  private String extractAnswer(String body) {
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode content = root.path("choices").path(0).path("message").path("content");
      if (content.isTextual() && !content.asText().isBlank()) {
        return content.asText();
      }
      JsonNode outputText = root.path("output_text");
      if (outputText.isTextual() && !outputText.asText().isBlank()) {
        return outputText.asText();
      }
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    } catch (Exception e) {
      throw new AppException("AI 응답을 해석할 수 없습니다: " + e.getMessage());
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
