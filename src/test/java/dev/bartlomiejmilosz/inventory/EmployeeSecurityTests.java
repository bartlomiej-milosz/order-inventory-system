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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
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

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping routes;

  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;
  @Autowired ObjectMapper mapper;
  @Autowired EmployeeUserService users;
  @LocalServerPort int port;
  private String hash;
  private HttpClient client;

  @BeforeAll
  void prepareHash() {
    hash = "{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);
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

  private static final String USERS = "/api/v1/users";
  private static final String ADMIN_EMAIL = "admin@m3.test";
  private static final String OPERATOR_EMAIL = "operator@m3.test";

  private record Csrf(String cookie, String header, String token) {}

  private Csrf csrf(String email) throws Exception {
    var response = get("/api/v1/auth/csrf", email);
    assertThat(response.statusCode()).isEqualTo(200);
    return new Csrf(
        response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0],
        body(response).get("headerName").asString(),
        body(response).get("token").asString());
  }

  private HttpResponse<String> call(
      String method, String path, String email, Csrf csrf, String json) throws Exception {
    var builder = request(path, email, PASSWORD);
    if (csrf != null) {
      builder.header("Cookie", csrf.cookie()).header(csrf.header(), csrf.token());
    }
    if (json != null) {
      builder.header("Content-Type", "application/json");
    }
    return send(
        builder.method(
            method,
            json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json)));
  }

  private String account(String email, String password, String role) {
    return mapper.writeValueAsString(Map.of("email", email, "password", password, "role", role));
  }

  @Test
  void managesEmployeeAccessThroughTheApi() throws Exception {
    var token = csrf(ADMIN_EMAIL);
    String email = "new@m3.test";
    String input = account(" NEW@M3.TEST ", PASSWORD, "OPERATOR");
    problem(call("POST", USERS, ADMIN_EMAIL, null, input), 403);
    var created = call("POST", USERS, ADMIN_EMAIL, token, input);
    assertThat(created.statusCode()).isEqualTo(201);
    var view = body(created);
    String path = USERS + "/" + view.path("id").asString();
    assertThat(created.headers().firstValue("Location")).contains(path);
    assertThat(view.path("email").asString()).isEqualTo(email);
    assertThat(view.path("role").asString()).isEqualTo("OPERATOR");
    assertThat(view.path("active").asBoolean()).isTrue();
    assertThat(created.body()).doesNotContain("password", PASSWORD, "{bcrypt}");
    String stored =
        jdbc.queryForObject(
            "SELECT password_hash FROM inventory.users WHERE email=?", String.class, email);
    assertThat(stored).startsWith("{bcrypt}");
    assertThat(passwords.matches(PASSWORD, stored)).isTrue();
    assertThat(get("/api/v1/auth/me", email).statusCode()).isEqualTo(200);
    problem(get(path, email), 403);
    problem(call("POST", USERS, ADMIN_EMAIL, token, input), 409);

    var promoted = call("PUT", path + "/role", ADMIN_EMAIL, token, "{\"role\":\"ADMIN\"}");
    assertThat(promoted.statusCode()).isEqualTo(200);
    assertThat(body(promoted).path("role").asString()).isEqualTo("ADMIN");
    assertThat(get(path, email).statusCode()).isEqualTo(200);
    assertThat(
            call("PUT", path + "/role", ADMIN_EMAIL, token, "{\"role\":\"OPERATOR\"}").statusCode())
        .isEqualTo(200);
    problem(get(path, email), 403);
    for (int i = 0; i < 2; i++) {
      assertThat(call("POST", path + "/deactivate", ADMIN_EMAIL, token, null).statusCode())
          .isEqualTo(204);
    }
    problem(get("/api/v1/auth/me", email), 401);
    assertThat(body(get(path, ADMIN_EMAIL)).path("active").asBoolean()).isFalse();
    problem(call("POST", USERS, ADMIN_EMAIL, token, input), 409);
  }

  @Test
  void validatesAccountInputAndPreservesTheLastAdministrator() throws Exception {
    var token = csrf(ADMIN_EMAIL);
    for (String input :
        List.of(
            "{}",
            account("invalid", PASSWORD, "ADMIN"),
            account("\"a:b\"@m3.test", PASSWORD, "ADMIN"),
            account("new@m3.test", "short", "ADMIN"),
            account("new@m3.test", "ą".repeat(37), "ADMIN"),
            account("new@m3.test", "😀".repeat(10), "ADMIN"),
            account("new@m3.test", PASSWORD, "OWNER"),
            account("new@m3.test", PASSWORD, "ADMIN").replace("\"ADMIN\"", "0"))) {
      problem(call("POST", USERS, ADMIN_EMAIL, token, input), 400);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM inventory.users WHERE email='new@m3.test'", Integer.class))
        .isZero();
    problem(call("PUT", USERS + "/" + ADMIN + "/role", ADMIN_EMAIL, token, "{}"), 400);
    problem(
        call("PUT", USERS + "/" + ADMIN + "/role", ADMIN_EMAIL, token, "{\"role\":\"OPERATOR\"}"),
        409);
    problem(call("POST", USERS + "/" + ADMIN + "/deactivate", ADMIN_EMAIL, token, null), 409);
    assertThat(get(USERS + "/" + ADMIN, ADMIN_EMAIL).statusCode()).isEqualTo(200);
  }

  @Test
  void concurrentDemotionAndDeactivationKeepOneActiveAdministrator() throws Exception {
    jdbc.update("UPDATE inventory.users SET role='ADMIN' WHERE id=?", OPERATOR);
    var firstToken = csrf(ADMIN_EMAIL);
    var secondToken = csrf(OPERATOR_EMAIL);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var demotion =
          executor.submit(() -> {
            start.await();
            return call(
                    "PUT",
                    USERS + "/" + ADMIN + "/role",
                    ADMIN_EMAIL,
                    firstToken,
                    "{\"role\":\"OPERATOR\"}")
                .statusCode();
          });
      var deactivation =
          executor.submit(() -> {
            start.await();
            return call(
                    "POST",
                    USERS + "/" + OPERATOR + "/deactivate",
                    OPERATOR_EMAIL,
                    secondToken,
                    null)
                .statusCode();
          });
      start.countDown();
      assertThat(
              List.of(demotion.get(10, TimeUnit.SECONDS), deactivation.get(10, TimeUnit.SECONDS)))
          .isIn(List.of(200, 409), List.of(409, 204));
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM inventory.users WHERE role='ADMIN' AND active",
                Integer.class))
        .isOne();
  }

  @Test
  void checksEveryBusinessEndpointForAllFourCallers() throws Exception {
    // Deliberately use missing IDs and invalid write bodies to test successful authorization
    // without changing the fixture. Domain workflow tests cover successful payloads.
    var expected = new HashMap<String, Integer>();
    var adminOnly = new HashSet<String>();
    for (String catalog : List.of("customers", "products", "warehouses")) {
      String path = "/" + catalog;
      expected.put("GET " + path, 200);
      expected.put("GET " + path + "/{id}", 404);
      expected.put("POST " + path, 400);
      expected.put("PUT " + path + "/{id}", 400);
      adminOnly.add("POST " + path);
      adminOnly.add("PUT " + path + "/{id}");
    }
    for (String path :
        List.of(
            "/products/{id}/deactivate", "/warehouses/{id}/deactivate", "/users/{id}/deactivate")) {
      expected.put("POST " + path, 404);
      adminOnly.add("POST " + path);
    }
    String stock = "/warehouses/{warehouseId}/stock";
    String pair = stock + "/{productId}";
    String movement = pair + "/movements";
    for (String path : List.of(stock, pair, movement, movement + "/{movementId}")) {
      expected.put("GET " + path, 404);
    }
    expected.put("POST " + pair + "/receipts", 400);
    expected.put("POST " + pair + "/adjustments", 400);
    adminOnly.add("POST " + pair + "/adjustments");
    expected.put("GET /auth/me", 200);
    expected.put("GET /auth/csrf", 200);
    expected.put("GET /users/{id}", 404);
    expected.put("POST /users", 400);
    expected.put("PUT /users/{id}/role", 400);
    adminOnly.addAll(List.of("GET /users/{id}", "POST /users", "PUT /users/{id}/role"));
    expected.put("GET /orders", 200);
    expected.put("POST /orders", 400);
    String order = "/orders/{orderId}";
    expected.put("GET " + order, 404);
    expected.put("PUT " + order, 400);
    for (String action : List.of("confirm", "fulfill", "cancel")) {
      expected.put("POST " + order + "/" + action, 404);
    }
    expected.put("POST " + order + "/items", 400);
    String item = order + "/items/{itemId}";
    expected.put("GET " + item, 404);
    expected.put("PUT " + item, 400);
    expected.put("DELETE " + item, 404);

    var mapped = new HashSet<String>();
    routes
        .getHandlerMethods()
        .keySet()
        .forEach(mapping -> {
          for (String path : mapping.getPatternValues()) {
            if (path.startsWith("/api/v1/")) {
              mapping
                  .getMethodsCondition()
                  .getMethods()
                  .forEach(method ->
                      mapped.add(method.name() + " " + path.substring("/api/v1".length())));
            }
          }
        });
    assertThat(expected.keySet()).containsExactlyInAnyOrderElementsOf(mapped);
    var token = csrf(ADMIN_EMAIL);
    UUID missing = UUID.randomUUID();
    for (var entry : expected.entrySet()) {
      String[] route = entry.getKey().split(" ", 2);
      String path = "/api/v1" + route[1].replaceAll("\\{[^}]+}", missing.toString());
      boolean reads = route[0].equals("GET");
      String payload = entry.getValue() == 400 ? "{}" : null;
      for (String email : new String[] {ADMIN_EMAIL, OPERATOR_EMAIL, null, "disabled@m3.test"}) {
        var result = call(route[0], path, email, reads ? null : token, payload);
        int status =
            email == null || email.startsWith("disabled@")
                ? 401
                : email.equals(OPERATOR_EMAIL) && adminOnly.contains(entry.getKey())
                    ? 403
                    : entry.getValue();
        assertThat(result.statusCode()).as("%s %s", email, entry.getKey()).isEqualTo(status);
        if (status >= 400) {
          problem(result, status);
        }
      }
      if (!reads) {
        problem(call(route[0], path, ADMIN_EMAIL, null, payload), 403);
      }
    }
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
    // The token passes CSRF; this unsupported method is still denied.
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
