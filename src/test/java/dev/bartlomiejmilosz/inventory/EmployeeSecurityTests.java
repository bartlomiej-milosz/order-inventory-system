package dev.bartlomiejmilosz.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.bartlomiejmilosz.inventory.config.TestcontainersConfiguration;
import dev.bartlomiejmilosz.inventory.users.EmployeeUserService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmployeeSecurityTests {
  private static final String PASSWORD = "Inventory-test-password-42!";
  private static final UUID ADMIN = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID OPERATOR = UUID.fromString("10000000-0000-0000-0000-000000000002");
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;
  @Autowired ObjectMapper mapper;
  @Autowired EmployeeUserService users;
  @LocalServerPort int port;
  private String hash;
  private HttpClient client;

  @BeforeAll
  void prepareHash() {
    hash = passwords.encode(PASSWORD);
    client = HttpClient.newHttpClient();
  }

  @AfterAll
  void closeClient() {
    client.close();
  }

  @BeforeEach
  void seedAccounts() {
    insert(ADMIN, "admin@m3.test", "ADMIN", true, hash);
    insert(OPERATOR, "operator@m3.test", "OPERATOR", true, hash);
    insert(UUID.randomUUID(), "disabled@m3.test", "OPERATOR", false, hash);
    insert(UUID.randomUUID(), "malformed@m3.test", "OPERATOR", true, "{noop}unsupported");
  }

  @AfterEach
  void removeAccounts() {
    SecurityContextHolder.clearContext();
    jdbc.update("DELETE FROM inventory.users WHERE email LIKE '%@m3.test'");
  }

  private void insert(UUID id, String email, String role, boolean active, String hash) {
    jdbc.update(
        "INSERT INTO inventory.users(id,email,password_hash,role,active) VALUES (?,?,?,?,?)",
        id,
        email,
        hash,
        role,
        active);
  }

  private HttpRequest.Builder request(String path, String username, String password) {
    var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    if (username != null) {
      String basic =
          Base64.getEncoder()
              .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
      builder.header("Authorization", "Basic " + basic);
    }
    return builder;
  }

  private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path, String username) throws Exception {
    return send(request(path, username, PASSWORD));
  }

  private JsonNode body(HttpResponse<String> response) {
    return mapper.readTree(response.body());
  }

  private void problem(HttpResponse<String> response, int status) {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type").orElseThrow())
        .startsWith("application/problem+json");
    assertThat(body(response).get("status").asInt()).isEqualTo(status);
    assertThat(body(response).get("detail").asString()).isNotBlank();
    assertThat(response.body())
        .doesNotContain(PASSWORD, hash, "passwordHash", "stackTrace", "SELECT ");
  }

  @Test
  void requiresBasicAuthentication() throws Exception {
    var response = get("/api/v1/auth/me", null);
    problem(response, 401);
    assertThat(response.headers().firstValue("WWW-Authenticate").orElseThrow())
        .startsWith("Basic ");
  }

  @ParameterizedTest
  @CsvSource({
    "admin@m3.test,wrong-password",
    "unknown@m3.test,Inventory-test-password-42!",
    "disabled@m3.test,Inventory-test-password-42!",
    "malformed@m3.test,unsupported",
    "admin@m3.test,aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  })
  void rejectsInvalidUnknownDisabledAndUnsupportedCredentials(String username, String password)
      throws Exception {
    problem(send(request("/api/v1/auth/me", username, password)), 401);
  }

  @Test
  void returnsSafeProfileAndNormalizesTheLoginEmail() throws Exception {
    var response = get("/api/v1/auth/me", " OPERATOR@M3.TEST ");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body(response).get("id").asString()).isEqualTo(OPERATOR.toString());
    assertThat(body(response).get("email").asString()).isEqualTo("operator@m3.test");
    assertThat(body(response).get("role").asString()).isEqualTo("OPERATOR");
    assertThat(response.body()).doesNotContain("password", hash);
    assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
    assertThat(response.headers().allValues("Set-Cookie"))
        .noneMatch(cookie -> cookie.contains("JSESSIONID"));
  }

  @Test
  void allowsOnlyAnAdminToReadAnotherAccount() throws Exception {
    var response = get("/api/v1/users/" + OPERATOR, "admin@m3.test");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body(response).get("active").asBoolean()).isTrue();
    assertThat(body(response).get("role").asString()).isEqualTo("OPERATOR");
    assertThat(response.body()).doesNotContain("password", hash);
    problem(get("/api/v1/users/" + OPERATOR, "operator@m3.test"), 403);
  }

  @Test
  void handlesInvalidAndUnknownIdsWithoutLeakingInternals() throws Exception {
    problem(get("/api/v1/users/not-a-uuid", "admin@m3.test"), 400);
    problem(get("/api/v1/users/" + UUID.randomUUID(), "admin@m3.test"), 404);
  }

  @Test
  void deniesUnlistedEndpointsEvenToAnAdministrator() throws Exception {
    problem(
        get("/api/v1/orders/00000000-0000-0000-0000-000000000000/fulfill", "admin@m3.test"), 403);
    problem(get("/actuator/env", "admin@m3.test"), 403);
  }

  @Test
  void authenticatesEveryRequestAndPicksUpDeactivationAndRoleChanges() throws Exception {
    assertThat(get("/api/v1/auth/me", "admin@m3.test").statusCode()).isEqualTo(200);
    problem(get("/api/v1/auth/me", null), 401);
    jdbc.update("UPDATE inventory.users SET role='OPERATOR' WHERE id=?", ADMIN);
    problem(get("/api/v1/users/" + OPERATOR, "admin@m3.test"), 403);
    jdbc.update("UPDATE inventory.users SET active=false WHERE id=?", ADMIN);
    problem(get("/api/v1/auth/me", "admin@m3.test"), 401);
  }

  @Test
  void protectsUnsafeMethodsWithTheCookieAndMaskedCsrfToken() throws Exception {
    var tokenResponse = get("/api/v1/auth/csrf", "admin@m3.test");
    assertThat(tokenResponse.statusCode()).isEqualTo(200);
    String cookie = tokenResponse.headers().firstValue("Set-Cookie").orElseThrow();
    assertThat(cookie).contains("HttpOnly", "SameSite=Strict").doesNotContain("JSESSIONID");
    String cookiePair = cookie.split(";", 2)[0];
    String header = body(tokenResponse).get("headerName").asString();
    String token = body(tokenResponse).get("token").asString();
    assertThat(token).isNotBlank();
    var missing =
        send(
            request("/api/v1/users/" + OPERATOR, "admin@m3.test", PASSWORD)
                .POST(HttpRequest.BodyPublishers.noBody()));
    problem(missing, 403);
    assertThat(body(missing).get("detail").asString()).contains("CSRF");
    var invalid =
        send(
            request("/api/v1/users/" + OPERATOR, "admin@m3.test", PASSWORD)
                .header("Cookie", cookiePair)
                .header(header, "incorrect")
                .POST(HttpRequest.BodyPublishers.noBody()));
    assertThat(body(invalid).get("detail").asString()).contains("CSRF");
    var valid =
        send(
            request("/api/v1/users/" + OPERATOR, "admin@m3.test", PASSWORD)
                .header("Cookie", cookiePair)
                .header(header, token)
                .POST(HttpRequest.BodyPublishers.noBody()));
    // The token passes CSRF; the unimplemented write endpoint is still denied.
    problem(valid, 403);
    assertThat(body(valid).get("detail").asString()).isEqualTo("Access denied.");
  }

  @Test
  void enforcesTheAdminRoleAtTheServiceBoundary() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "operator", null, java.util.List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))));
    assertThatThrownBy(() -> users.findById(ADMIN)).isInstanceOf(AccessDeniedException.class);
  }
}
