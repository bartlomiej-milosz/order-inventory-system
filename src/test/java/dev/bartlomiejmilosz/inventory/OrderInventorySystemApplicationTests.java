package dev.bartlomiejmilosz.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.bartlomiejmilosz.inventory.config.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderInventorySystemApplicationTests {

  @Autowired private JdbcTemplate jdbc;

  @LocalServerPort private int port;

  @Test
  void connectsToPostgresAndInitializesFlyway() {
    assertThat(
            jdbc.queryForObject(
                "SELECT current_setting('server_version_num')::int / 10000", Integer.class))
        .isEqualTo(17);
    assertThat(
            jdbc.queryForObject("SELECT to_regnamespace('inventory') IS NOT NULL", Boolean.class))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT to_regclass('flyway_history.flyway_schema_history') IS NOT NULL",
                Boolean.class))
        .isTrue();
  }

  @Test
  void exposesOnlyMinimalHealthInformationToAnonymousClients() throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      for (String path : new String[] {"/livez", "/readyz"}) {
        HttpResponse<String> response = get(client, path);
        assertThat(response.statusCode()).as(path).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
      }

      for (String path :
          new String[] {"/api/v1/products", "/actuator/env", "/v3/api-docs", "/swagger-ui.html"}) {
        assertThat(get(client, path).statusCode()).as(path).isEqualTo(403);
      }
    }
  }

  private HttpResponse<String> get(HttpClient client, String path) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
