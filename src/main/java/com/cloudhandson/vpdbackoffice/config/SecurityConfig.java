package com.cloudhandson.vpdbackoffice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/css/**", "/js/**", "/webjars/**").permitAll()
            .anyRequest().authenticated())
        .formLogin(login -> login
            .loginPage("/login")
            .permitAll())
        .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
        .build();
  }

  @Bean
  UserDetailsService userDetailsService(
      BackofficeProperties properties,
      PasswordEncoder passwordEncoder
  ) {
    var security = properties.security();
    var user = User.withUsername(security.adminUser())
        .password(passwordEncoder.encode(security.adminPassword()))
        .roles("ADMIN")
        .build();
    return new InMemoryUserDetailsManager(user);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }
}
