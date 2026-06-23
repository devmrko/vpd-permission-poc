package com.cloudhandson.vpdbackoffice.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OrdsClientConfig {

  @Bean
  RestTemplate ordsRestTemplate(BackofficeProperties properties) {
    Duration timeout = properties.ords().timeout();
    return new RestTemplateBuilder()
        .setConnectTimeout(timeout)
        .setReadTimeout(timeout)
        .build();
  }
}
