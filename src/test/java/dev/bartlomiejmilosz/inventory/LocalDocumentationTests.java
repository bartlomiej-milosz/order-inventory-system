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
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "local"})
class LocalDocumentationTests {
  @LocalServerPort int port;
  @Autowired ObjectMapper mapper;

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
      var spec = mapper.readTree(response.body());
      var paths = spec.path("paths");
      assertCatalogSchemas(spec, "/api/v1/customers", "email");
      assertCatalogSchemas(spec, "/api/v1/products", "sku");
      assertCatalogSchemas(spec, "/api/v1/warehouses", "code");
      assertThat(paths.path("/api/v1/products").path("post").path("responses").has("201")).isTrue();
      assertThat(
              paths
                  .path("/api/v1/products/{id}/deactivate")
                  .path("post")
                  .path("responses")
                  .has("204"))
          .isTrue();
      String stock = "/api/v1/warehouses/{warehouseId}/stock/{productId}";
      assertThat(paths.path(stock + "/receipts").path("post").path("responses").has("201"))
          .isTrue();
      assertThat(paths.path(stock + "/adjustments").path("post").path("responses").has("201"))
          .isTrue();
      assertThat(paths.path(stock + "/movements").has("get")).isTrue();
      assertThat(paths.path(stock + "/movements").has("delete")).isFalse();
    }
  }

  private void assertCatalogSchemas(JsonNode spec, String path, String field) {
    var collection = spec.path("paths").path(path);
    var create = collection.path("post");
    var input = create.path("requestBody").path("content").path("application/json").path("schema");
    assertThat(properties(spec, input).has(field)).as("%s input has %s", path, field).isTrue();
    var view = responseSchema(create, "201");
    assertThat(properties(spec, view).has(field)).as("%s output has %s", path, field).isTrue();
    var page = responseSchema(collection.path("get"), "200");
    var item = properties(spec, page).path("items").path("items");
    assertThat(properties(spec, item).has(field)).as("%s list item has %s", path, field).isTrue();
  }

  private JsonNode responseSchema(JsonNode operation, String status) {
    return operation
        .path("responses")
        .path(status)
        .path("content")
        .iterator()
        .next()
        .path("schema");
  }

  private JsonNode properties(JsonNode spec, JsonNode schema) {
    String ref = schema.path("$ref").asString();
    assertThat(ref).startsWith("#/");
    return spec.at(ref.substring(1)).path("properties");
  }
}
