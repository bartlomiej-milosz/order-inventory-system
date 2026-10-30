package dev.bartlomiejmilosz.inventory.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, Environment environment, SecurityProblemHandler problems)
      throws Exception {
    var csrf = new CookieCsrfTokenRepository();
    csrf.setCookieCustomizer(cookie ->
        cookie.httpOnly(true).sameSite("Strict").secure(environment.matchesProfiles("deploy")));
    http.httpBasic(basic -> basic.authenticationEntryPoint(problems))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .csrf(config -> config.csrfTokenRepository(csrf))
        .exceptionHandling(
            errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
        .authorizeHttpRequests(requests -> {
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
          requests
              .requestMatchers(HttpMethod.GET, "/api/v1/auth/me", "/api/v1/auth/csrf")
              .hasAnyRole("ADMIN", "OPERATOR");
          requests.requestMatchers(HttpMethod.GET, "/api/v1/users/*").hasRole("ADMIN");
          requests
              .requestMatchers(
                  HttpMethod.GET,
                  "/api/v1/customers",
                  "/api/v1/customers/*",
                  "/api/v1/products",
                  "/api/v1/products/*",
                  "/api/v1/warehouses",
                  "/api/v1/warehouses/*",
                  "/api/v1/warehouses/*/stock",
                  "/api/v1/warehouses/*/stock/*",
                  "/api/v1/warehouses/*/stock/*/movements",
                  "/api/v1/warehouses/*/stock/*/movements/*")
              .hasAnyRole("ADMIN", "OPERATOR");
          requests
              .requestMatchers(HttpMethod.POST, "/api/v1/warehouses/*/stock/*/receipts")
              .hasAnyRole("ADMIN", "OPERATOR");
          requests
              .requestMatchers(
                  HttpMethod.POST,
                  "/api/v1/users",
                  "/api/v1/users/*/deactivate",
                  "/api/v1/customers",
                  "/api/v1/products",
                  "/api/v1/products/*/deactivate",
                  "/api/v1/warehouses",
                  "/api/v1/warehouses/*/deactivate",
                  "/api/v1/warehouses/*/stock/*/adjustments")
              .hasRole("ADMIN");
          requests
              .requestMatchers(
                  HttpMethod.PUT,
                  "/api/v1/users/*/role",
                  "/api/v1/customers/*",
                  "/api/v1/products/*",
                  "/api/v1/warehouses/*")
              .hasRole("ADMIN");
          requests
              .requestMatchers(
                  HttpMethod.GET, "/api/v1/orders", "/api/v1/orders/*", "/api/v1/orders/*/items/*")
              .hasAnyRole("ADMIN", "OPERATOR");
          requests
              .requestMatchers(
                  HttpMethod.POST,
                  "/api/v1/orders",
                  "/api/v1/orders/*/items",
                  "/api/v1/orders/*/confirm",
                  "/api/v1/orders/*/fulfill",
                  "/api/v1/orders/*/cancel")
              .hasAnyRole("ADMIN", "OPERATOR");
          requests
              .requestMatchers(HttpMethod.PUT, "/api/v1/orders/*", "/api/v1/orders/*/items/*")
              .hasAnyRole("ADMIN", "OPERATOR");
          requests
              .requestMatchers(HttpMethod.DELETE, "/api/v1/orders/*/items/*")
              .hasAnyRole("ADMIN", "OPERATOR");
          requests.anyRequest().denyAll();
        });
    return http.build();
  }
}
