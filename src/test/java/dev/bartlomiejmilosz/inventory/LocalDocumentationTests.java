package dev.bartlomiejmilosz.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.bartlomiejmilosz.inventory.config.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "local"})
class LocalDocumentationTests {
  @LocalServerPort int port;

  @Test
  void publishesTheLocalApiWithHttpBasicAuthentication() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v3/api-docs")).build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body())
          .contains(
              "basicAuth",
              "\"scheme\":\"basic\"",
              "/api/v1/auth/me",
              "/api/v1/auth/csrf",
              "/api/v1/users/{id}");
    }
  }
}
