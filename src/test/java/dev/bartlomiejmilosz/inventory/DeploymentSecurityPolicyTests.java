package dev.bartlomiejmilosz.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.bartlomiejmilosz.inventory.config.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      // Testcontainers supplies all connection details; this is a policy test, not a TLS test.
      "spring.datasource.url=jdbc:postgresql://unused.invalid/unused",
      "spring.datasource.username=unused",
      "spring.datasource.password=unused",
      "spring.flyway.url=jdbc:postgresql://unused.invalid/unused",
      "spring.flyway.user=unused",
      "spring.flyway.password=unused",
      "spring.flyway.create-schemas=true",
      "springdoc.api-docs.enabled=true",
      "springdoc.swagger-ui.enabled=true"
    })
@ActiveProfiles({"test", "local", "deploy"})
class DeploymentSecurityPolicyTests {
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;
  @LocalServerPort int port;

  @Test
  void keepsDocumentationPrivateAndCsrfCookiesSecureWhenDeployAndLocalAreBothActive()
      throws Exception {
    var id = UUID.randomUUID();
    String password = "Deployment-policy-test-42!";
    String auth =
        "Basic "
            + Base64.getEncoder()
                .encodeToString(("policy@m3.test:" + password).getBytes(StandardCharsets.UTF_8));
    jdbc.update(
        "INSERT INTO inventory.users(id,email,password_hash,role) VALUES (?,?,?,'ADMIN')",
        id,
        "policy@m3.test",
        passwords.encode(password));
    try (var client = HttpClient.newHttpClient()) {
      for (String probe : new String[] {"/livez", "/readyz"}) {
        var probeRequest =
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + probe)).build();
        var publicProbe = client.send(probeRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(publicProbe.statusCode()).isEqualTo(200);
        assertThat(publicProbe.body())
            .contains("\"status\":\"UP\"")
            .doesNotContain("components", "details");
      }
      for (String path : new String[] {"/v3/api-docs", "/swagger-ui.html"}) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        assertThat(client.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode())
            .as("anonymous %s", path)
            .isEqualTo(401);
        assertThat(
                client
                    .send(
                        request.header("Authorization", auth).build(),
                        HttpResponse.BodyHandlers.ofString())
                    .statusCode())
            .as("admin %s", path)
            .isEqualTo(403);
      }
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/csrf"))
                  .header("Authorization", auth)
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Set-Cookie").orElseThrow())
          .contains("Secure", "HttpOnly", "SameSite=Strict");
    } finally {
      jdbc.update("DELETE FROM inventory.users WHERE id=?", id);
    }
  }
}
