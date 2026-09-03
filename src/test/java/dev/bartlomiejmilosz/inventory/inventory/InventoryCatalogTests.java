package dev.bartlomiejmilosz.inventory.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.bartlomiejmilosz.inventory.config.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.application.name=inventory-catalog-tests")
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryCatalogTests {
  private static final UUID ADMIN = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final UUID OPERATOR = UUID.fromString("40000000-0000-0000-0000-000000000002");
  private static final UUID WAREHOUSE = UUID.fromString("40000000-0000-0000-0000-000000000003");
  private static final UUID PRODUCT = UUID.fromString("40000000-0000-0000-0000-000000000004");
  private static final String ADMIN_EMAIL = "admin@m4.test";
  private static final String OPERATOR_EMAIL = "operator@m4.test";
  private static final String PASSWORD = "Only-used-by-M4-tests!";
  private static final String API = "/api/v1";
  private static final String STOCK = API + "/warehouses/" + WAREHOUSE + "/stock/" + PRODUCT;

  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired StockService stock;
  @Autowired PlatformTransactionManager transactions;
  @LocalServerPort int port;
  private HttpClient client;
  private String passwordHash;

  @BeforeAll
  void openClient() {
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    passwordHash = "{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);
  }

  @AfterAll
  void closeClient() {
    client.close();
  }

  @BeforeEach
  void seedCatalogAndEmployees() {
    jdbc.update(
        "INSERT INTO inventory.users (id,email,password_hash,role) VALUES (?,?,?,?),(?,?,?,?)",
        ADMIN,
        ADMIN_EMAIL,
        passwordHash,
        "ADMIN",
        OPERATOR,
        OPERATOR_EMAIL,
        passwordHash,
        "OPERATOR");
    jdbc.update(
        "INSERT INTO inventory.warehouses (id,code,name) VALUES (?,'NORTH','North warehouse')",
        WAREHOUSE);
    jdbc.update(
        "INSERT INTO inventory.products (id,sku,name,unit_price) VALUES (?,'WIDGET','Widget',2.50)",
        PRODUCT);
  }

  @AfterEach
  void cleanDatabase() {
    // This Spring test context has a PostgreSQL container dedicated to these tests.
    jdbc.execute(
        "TRUNCATE inventory.users, inventory.customers, inventory.products, inventory.warehouses CASCADE");
  }

  @Test
  void administersCatalogAndLetsBothRolesReadIt() throws Exception {
    Csrf admin = csrf(ADMIN_EMAIL);
    var customer =
        send(
            "POST",
            API + "/customers",
            ADMIN_EMAIL,
            admin,
            """
            {"name":" New customer ","email":"buyer@example.test","phone":"123456789"}
            """);
    var product =
        send(
            "POST",
            API + "/products",
            ADMIN_EMAIL,
            admin,
            """
            {"sku":"BRACKET_2","name":"Bracket","unitPrice":19.99}
            """);
    var warehouse =
        send(
            "POST",
            API + "/warehouses",
            ADMIN_EMAIL,
            admin,
            """
            {"code":"SOUTH-2","name":"South warehouse"}
            """);
    for (var response : List.of(customer, product, warehouse)) {
      assertThat(response.statusCode()).isEqualTo(201);
    }
    String customerUrl = location(customer);
    String productUrl = location(product);
    String warehouseUrl = location(warehouse);
    assertThat(body(get(customerUrl, OPERATOR_EMAIL)).path("name").asString())
        .isEqualTo("New customer");
    assertThat(body(get(warehouseUrl, OPERATOR_EMAIL)).path("code").asString())
        .isEqualTo("SOUTH-2");
    assertThat(body(get(productUrl, OPERATOR_EMAIL)).path("unitPrice").asDouble()).isEqualTo(19.99);

    var updated =
        send(
            "PUT",
            customerUrl,
            ADMIN_EMAIL,
            admin,
            """
            {"name":"Changed customer","email":"other@example.test"}
            """);
    assertThat(updated.statusCode()).isEqualTo(200);
    assertThat(body(updated).path("phone").isNull()).isTrue();
    assertThat(
            send(
                    "PUT",
                    warehouseUrl,
                    ADMIN_EMAIL,
                    admin,
                    """
                    {"code":"SOUTH-2","name":"Renamed warehouse"}
                    """)
                .statusCode())
        .isEqualTo(200);
    assertThat(send("POST", productUrl + "/deactivate", ADMIN_EMAIL, admin, null).statusCode())
        .isEqualTo(204);
    var edited =
        send(
            "PUT",
            productUrl,
            ADMIN_EMAIL,
            admin,
            """
            {"sku":"BRACKET_2","name":"Renamed bracket","unitPrice":21.30}
            """);
    assertThat(edited.statusCode()).isEqualTo(200);
    assertThat(body(edited).path("active").asBoolean()).isFalse();
    assertThat(send("POST", warehouseUrl + "/deactivate", ADMIN_EMAIL, admin, null).statusCode())
        .isEqualTo(204);
    assertThat(body(get(warehouseUrl, ADMIN_EMAIL)).path("active").asBoolean()).isFalse();

    var first = body(get(API + "/products?page=0&size=1", OPERATOR_EMAIL));
    var second = body(get(API + "/products?page=1&size=1", ADMIN_EMAIL));
    assertThat(first.path("totalElements").asLong()).isEqualTo(2);
    assertThat(first.path("totalPages").asInt()).isEqualTo(2);
    assertThat(first.path("items").get(0).path("sku").asString()).isEqualTo("BRACKET_2");
    assertThat(second.path("items").get(0).path("sku").asString()).isEqualTo("WIDGET");
  }

  @Test
  void enforcesCatalogRolesCsrfAndInputLimits() throws Exception {
    Csrf admin = csrf(ADMIN_EMAIL);
    Csrf operator = csrf(OPERATOR_EMAIL);
    String valid =
        """
        {"sku":"OTHER","name":"Other","unitPrice":12.55}
        """;
    problem(get(API + "/products", null), 401);
    problem(send("POST", API + "/products", ADMIN_EMAIL, null, valid), 403);
    problem(send("POST", API + "/products", OPERATOR_EMAIL, operator, valid), 403);
    problem(
        send(
            "PUT",
            API + "/warehouses/" + WAREHOUSE,
            OPERATOR_EMAIL,
            operator,
            """
            {"code":"NORTH","name":"Changed"}
            """),
        403);
    problem(
        send("POST", API + "/products/" + PRODUCT + "/deactivate", OPERATOR_EMAIL, operator, null),
        403);
    problem(send("DELETE", API + "/products/" + PRODUCT, ADMIN_EMAIL, admin, null), 403);
    problem(
        send(
            "POST",
            API + "/products",
            ADMIN_EMAIL,
            admin,
            """
            {"sku":"WIDGET","name":"Duplicate","unitPrice":0}
            """),
        409);
    problem(
        send(
            "POST",
            API + "/warehouses",
            ADMIN_EMAIL,
            admin,
            """
            {"code":"NORTH","name":"Duplicate"}
            """),
        409);
    for (String json :
        new String[] {
          "{\"sku\":\"lowercase\",\"name\":\"Name\",\"unitPrice\":1}",
          "{\"sku\":\"TEST\",\"name\":\"   \",\"unitPrice\":1}",
          "{\"sku\":\"TEST\",\"name\":\"Name\",\"unitPrice\":0.001}",
          "{\"sku\":\"TEST\",\"name\":\"Name\",\"unitPrice\":-1}",
          "{\"sku\":\"TEST\",\"name\":\"Name\",\"unitPrice\":10000000000}"
        }) {
      problem(send("POST", API + "/products", ADMIN_EMAIL, admin, json), 400);
    }
    problem(
        send(
            "POST",
            API + "/customers",
            ADMIN_EMAIL,
            admin,
            """
            {"name":"Buyer","email":"invalid"}
            """),
        400);
    problem(get(API + "/warehouses?size=101", OPERATOR_EMAIL), 400);
    problem(get(API + "/products?page=-1", ADMIN_EMAIL), 400);
    problem(get(API + "/products/not-a-uuid", ADMIN_EMAIL), 400);
    problem(get(API + "/products/" + UUID.randomUUID(), ADMIN_EMAIL), 404);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.products", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void receivesAndAdjustsStockWithAppendOnlyEmployeeHistory() throws Exception {
    Csrf operator = csrf(OPERATOR_EMAIL);
    Csrf admin = csrf(ADMIN_EMAIL);
    assertLevel(0, 0, 0);
    assertThat(body(get(STOCK, OPERATOR_EMAIL)).path("updatedAt").isNull()).isTrue();
    assertThat(body(get(STOCK + "/movements", OPERATOR_EMAIL)).path("totalElements").asInt())
        .isZero();
    assertThat(stockRowCount()).isZero();
    problem(
        send(
            "POST",
            STOCK + "/adjustments",
            ADMIN_EMAIL,
            admin,
            """
            {"quantityDelta":3,"note":"Counted stock"}
            """),
        409);
    var receipt =
        send(
            "POST",
            STOCK + "/receipts",
            OPERATOR_EMAIL,
            operator,
            """
            {"quantity":10,"note":" Initial delivery ","performedBy":"40000000-0000-0000-0000-000000000001"}
            """);
    assertThat(receipt.statusCode()).isEqualTo(201);
    assertThat(body(receipt).path("performedBy").asString()).isEqualTo(OPERATOR.toString());
    assertThat(body(receipt).path("type").asString()).isEqualTo("IN");
    assertThat(body(receipt).path("note").asString()).isEqualTo("Initial delivery");
    assertThat(body(receipt).path("orderId").isNull()).isTrue();
    assertThat(get(location(receipt), ADMIN_EMAIL).statusCode()).isEqualTo(200);

    var adjustment =
        send(
            "POST",
            STOCK + "/adjustments",
            ADMIN_EMAIL,
            admin,
            """
            {"quantityDelta":-2,"note":"Damaged packaging"}
            """);
    assertThat(adjustment.statusCode()).isEqualTo(201);
    assertThat(body(adjustment).path("performedBy").asString()).isEqualTo(ADMIN.toString());
    assertThat(body(adjustment).path("type").asString()).isEqualTo("ADJUSTMENT");
    assertThat(body(adjustment).path("quantityDelta").asInt()).isEqualTo(-2);
    assertLevel(8, 0, 8);
    assertThat(
            body(get(API + "/warehouses/" + WAREHOUSE + "/stock", OPERATOR_EMAIL))
                .path("items")
                .size())
        .isEqualTo(1);
    var history = body(get(STOCK + "/movements?size=1", OPERATOR_EMAIL));
    assertThat(history.path("totalElements").asInt()).isEqualTo(2);
    assertThat(history.path("items").get(0).path("id").asString())
        .isEqualTo(body(adjustment).path("id").asString());
    assertThat(
            body(get(STOCK + "/movements?page=1&size=1", OPERATOR_EMAIL))
                .path("items")
                .get(0)
                .path("id")
                .asString())
        .isEqualTo(body(receipt).path("id").asString());
    problem(send("DELETE", location(receipt), ADMIN_EMAIL, admin, null), 403);
    assertThat(movementCount()).isEqualTo(2);
  }

  @Test
  void rejectsInvalidMovementsAndPreservesReservedStockAndHistory() throws Exception {
    Csrf admin = csrf(ADMIN_EMAIL);
    Csrf operator = csrf(OPERATOR_EMAIL);
    assertThat(
            send("POST", STOCK + "/receipts", ADMIN_EMAIL, admin, "{\"quantity\":10}").statusCode())
        .isEqualTo(201);
    // Seed an existing reservation to check the lower bound for adjustments.
    jdbc.update("UPDATE inventory.stock_levels SET reserved = 4");
    var before = body(get(STOCK, ADMIN_EMAIL));
    problem(
        send(
            "POST",
            STOCK + "/adjustments",
            OPERATOR_EMAIL,
            operator,
            "{\"quantityDelta\":-1,\"note\":\"Count\"}"),
        403);
    problem(
        send(
            "POST",
            STOCK + "/adjustments",
            ADMIN_EMAIL,
            admin,
            "{\"quantityDelta\":-7,\"note\":\"Count\"}"),
        409);
    problem(
        send(
            "POST",
            STOCK + "/adjustments",
            ADMIN_EMAIL,
            admin,
            "{\"quantityDelta\":0,\"note\":\"Count\"}"),
        400);
    problem(
        send(
            "POST",
            STOCK + "/adjustments",
            ADMIN_EMAIL,
            admin,
            "{\"quantityDelta\":-1,\"note\":\"  \"}"),
        400);
    problem(send("POST", STOCK + "/receipts", ADMIN_EMAIL, admin, "{\"quantity\":0}"), 400);
    problem(send("POST", STOCK + "/receipts", ADMIN_EMAIL, admin, "{\"quantity\":1.5}"), 400);
    problem(
        send(
            "POST",
            STOCK + "/adjustments",
            ADMIN_EMAIL,
            admin,
            "{\"quantityDelta\":-1.5,\"note\":\"Physical count\"}"),
        400);
    problem(send("POST", STOCK + "/receipts", ADMIN_EMAIL, admin, "{\"quantity\":-2}"), 400);
    problem(
        send("POST", STOCK + "/receipts", ADMIN_EMAIL, admin, "{\"quantity\":2147483647}"), 409);
    problem(send("POST", STOCK + "/receipts", ADMIN_EMAIL, admin, "{}"), 400);
    problem(
        get(API + "/warehouses/" + WAREHOUSE + "/stock/" + UUID.randomUUID(), ADMIN_EMAIL), 404);
    assertThat(body(get(STOCK, ADMIN_EMAIL))).isEqualTo(before);
    assertThat(movementCount()).isEqualTo(1);
    assertThat(
            send(
                    "POST",
                    STOCK + "/adjustments",
                    ADMIN_EMAIL,
                    admin,
                    "{\"quantityDelta\":-6,\"note\":\"Physical count\"}")
                .statusCode())
        .isEqualTo(201);
    assertLevel(4, 4, 0);
    assertThat(
            send(
                    "POST",
                    STOCK + "/adjustments",
                    ADMIN_EMAIL,
                    admin,
                    "{\"quantityDelta\":2,\"note\":\"Found additional stock\"}")
                .statusCode())
        .isEqualTo(201);
    assertLevel(6, 4, 2);

    assertThat(
            send("POST", API + "/warehouses/" + WAREHOUSE + "/deactivate", ADMIN_EMAIL, admin, null)
                .statusCode())
        .isEqualTo(204);
    problem(send("POST", STOCK + "/receipts", OPERATOR_EMAIL, operator, "{\"quantity\":2}"), 409);
    jdbc.update("UPDATE inventory.warehouses SET active = true WHERE id = ?", WAREHOUSE);
    assertThat(
            send("POST", API + "/products/" + PRODUCT + "/deactivate", ADMIN_EMAIL, admin, null)
                .statusCode())
        .isEqualTo(204);
    problem(
        send(
            "POST",
            STOCK + "/adjustments",
            ADMIN_EMAIL,
            admin,
            "{\"quantityDelta\":-1,\"note\":\"Count\"}"),
        409);
    assertLevel(6, 4, 2);
    assertThat(body(get(STOCK + "/movements", OPERATOR_EMAIL)).path("totalElements").asInt())
        .isEqualTo(3);
  }

  @Test
  void rollsBackTheFirstReceiptWhenTheMovementCannotBeSaved() {
    assertThatThrownBy(() ->
            stock.receive(WAREHOUSE, PRODUCT, UUID.randomUUID(), new StockDto.Receipt(10, null)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(stockRowCount()).isZero();
    assertThat(movementCount()).isZero();
  }

  @Test
  void concurrentFirstReceiptsWaitAndAccumulateIntoOneStockRow() throws Exception {
    var transaction = new TransactionTemplate(transactions);
    var firstStockWritten = new CountDownLatch(1);
    var allowFirstCommit = new CountDownLatch(1);
    var secondConnected = new CountDownLatch(1);
    var secondBackendPid = new AtomicInteger();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(() ->
              transaction.executeWithoutResult(status -> {
                stock.receive(WAREHOUSE, PRODUCT, ADMIN, new StockDto.Receipt(5, null));
                firstStockWritten.countDown();
                await(allowFirstCommit);
              }));
      try {
        await(firstStockWritten);
        var second =
            executor.submit(() ->
                transaction.executeWithoutResult(status -> {
                  secondBackendPid.set(
                      jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                  secondConnected.countDown();
                  stock.receive(WAREHOUSE, PRODUCT, OPERATOR, new StockDto.Receipt(8, null));
                }));
        await(secondConnected);
        assertDatabaseBlocked(secondBackendPid.get());
        allowFirstCommit.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
      } finally {
        allowFirstCommit.countDown();
      }
    }
    assertThat(stockRowCount()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT on_hand FROM inventory.stock_levels", Integer.class))
        .isEqualTo(13);
    assertThat(
            jdbc.queryForList(
                "SELECT quantity_delta FROM inventory.stock_movements", Integer.class))
        .containsExactlyInAnyOrder(5, 8);
  }

  private void assertDatabaseBlocked(int pid) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      if (Boolean.TRUE.equals(
          jdbc.queryForObject("select cardinality(pg_blocking_pids(?)) > 0", Boolean.class, pid))) {
        return;
      }
      Thread.sleep(20);
    }
    throw new AssertionError("PostgreSQL did not report the second receipt as blocked");
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private void assertLevel(int onHand, int reserved, int available) throws Exception {
    var result = get(STOCK, OPERATOR_EMAIL);
    assertThat(result.statusCode()).isEqualTo(200);
    var level = body(result);
    assertThat(level.path("onHand").asInt()).isEqualTo(onHand);
    assertThat(level.path("reserved").asInt()).isEqualTo(reserved);
    assertThat(level.path("available").asInt()).isEqualTo(available);
  }

  private int stockRowCount() {
    return jdbc.queryForObject("SELECT count(*) FROM inventory.stock_levels", Integer.class);
  }

  private int movementCount() {
    return jdbc.queryForObject("SELECT count(*) FROM inventory.stock_movements", Integer.class);
  }

  private Csrf csrf(String employee) throws Exception {
    var result = get(API + "/auth/csrf", employee);
    assertThat(result.statusCode()).isEqualTo(200);
    return new Csrf(
        result.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0],
        body(result).path("headerName").asString(),
        body(result).path("token").asString());
  }

  private HttpResponse<String> get(String path, String email) throws Exception {
    return send("GET", path, email, null, null);
  }

  private HttpResponse<String> send(
      String method, String path, String email, Csrf csrf, String json) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10));
    if (email != null) {
      request.header(
          "Authorization",
          "Basic "
              + Base64.getEncoder()
                  .encodeToString((email + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8)));
    }
    if (csrf != null) {
      request.header("Cookie", csrf.cookie()).header(csrf.header(), csrf.token());
    }
    if (json != null) {
      request.header("Content-Type", "application/json");
    }
    return client.send(
        request
            .method(
                method,
                json == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode body(HttpResponse<String> response) {
    return mapper.readTree(response.body());
  }

  private String location(HttpResponse<String> response) {
    return response.headers().firstValue("Location").orElseThrow();
  }

  private void problem(HttpResponse<String> response, int code) {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(code);
    assertThat(response.headers().firstValue("Content-Type").orElseThrow())
        .startsWith("application/problem+json");
    assertThat(body(response).path("status").asInt()).isEqualTo(code);
  }

  private record Csrf(String cookie, String header, String token) {}
}
