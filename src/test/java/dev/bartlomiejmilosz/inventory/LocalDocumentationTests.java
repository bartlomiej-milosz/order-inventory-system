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
      var createEmployee = paths.path("/api/v1/users").path("post");
      var employeeInput = properties(spec, requestSchema(createEmployee));
      assertFields(employeeInput, "email", "password", "role");
      assertThat(employeeInput.path("password").path("writeOnly").asBoolean()).isTrue();
      var employee = properties(spec, responseSchema(createEmployee, "201"));
      assertFields(employee, "id", "role", "active", "createdAt");
      assertThat(employee.has("password") || employee.has("passwordHash")).isFalse();
      var changeRole = paths.path("/api/v1/users/{id}/role").path("put");
      assertFields(properties(spec, requestSchema(changeRole)), "role");
      assertThat(
              paths.path("/api/v1/users/{id}/deactivate").path("post").path("responses").has("204"))
          .isTrue();
      for (String probe : new String[] {"/livez", "/readyz", "/swagger-ui.html"}) {
        var publicResponse =
            client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + probe)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (probe.equals("/swagger-ui.html")) {
          assertThat(publicResponse.statusCode()).isBetween(300, 399);
        } else {
          assertThat(publicResponse.statusCode()).isEqualTo(200);
          assertThat(mapper.readTree(publicResponse.body()).path("status").asString())
              .isEqualTo("UP");
        }
      }
      assertDraftOrderSchemas(spec);
      for (String action : new String[] {"confirm", "fulfill", "cancel"}) {
        var operation = paths.path("/api/v1/orders/{orderId}/" + action).path("post");
        assertFields(properties(spec, responseSchema(operation, "200")), "order", "items");
        assertThat(operation.has("requestBody")).isFalse();
      }
    }
  }

  private void assertDraftOrderSchemas(JsonNode spec) {
    var paths = spec.path("paths");
    String order = "/api/v1/orders";
    var collection = paths.path(order);
    var create = collection.path("post");
    assertFields(
        properties(spec, requestSchema(create)),
        "orderNumber",
        "customerId",
        "warehouseId",
        "note");
    var detail = properties(spec, responseSchema(create, "201"));
    assertFields(detail, "order", "items");
    assertFields(
        properties(spec, detail.path("order")),
        "id",
        "orderNumber",
        "createdBy",
        "status",
        "total");
    var page = properties(spec, responseSchema(collection.path("get"), "200"));
    assertFields(properties(spec, page.path("items").path("items")), "id", "status", "total");
    var update = paths.path(order + "/{orderId}").path("put");
    assertFields(properties(spec, requestSchema(update)), "customerId", "warehouseId", "note");
    assertThat(properties(spec, requestSchema(update)).has("status")).isFalse();
    assertThat(properties(spec, requestSchema(update)).has("orderNumber")).isFalse();
    var add = paths.path(order + "/{orderId}/items").path("post");
    assertFields(properties(spec, requestSchema(add)), "productId", "quantity");
    assertFields(
        properties(spec, responseSchema(add, "201")),
        "productSku",
        "productName",
        "unitPrice",
        "lineTotal");
    var item = paths.path(order + "/{orderId}/items/{itemId}");
    assertFields(properties(spec, requestSchema(item.path("put"))), "quantity");
    assertThat(properties(spec, requestSchema(item.path("put"))).has("productId")).isFalse();
    assertThat(item.path("delete").path("responses").has("204")).isTrue();
  }

  private static void assertFields(JsonNode properties, String... names) {
    for (String name : names) {
      assertThat(properties.has(name)).as("schema field %s", name).isTrue();
    }
  }

  private static JsonNode requestSchema(JsonNode operation) {
    return operation.path("requestBody").path("content").path("application/json").path("schema");
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
