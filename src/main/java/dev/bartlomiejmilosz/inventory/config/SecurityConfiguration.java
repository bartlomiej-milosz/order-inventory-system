package dev.bartlomiejmilosz.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment)
      throws Exception {
    http.authorizeHttpRequests(requests -> {
      requests.requestMatchers(HttpMethod.GET, "/livez", "/readyz").permitAll();

      if (environment.matchesProfiles("local & !deploy")) {
        requests
            .requestMatchers(
                HttpMethod.GET,
                "/v3/api-docs",
                "/v3/api-docs/**",
                "/swagger-ui.html",
                "/swagger-ui/**")
            .permitAll();
      }

      requests.anyRequest().denyAll();
    });
    return http.build();
  }
}
