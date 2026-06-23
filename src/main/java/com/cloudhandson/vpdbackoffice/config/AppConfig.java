package com.cloudhandson.vpdbackoffice.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BackofficeProperties.class)
public class AppConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
