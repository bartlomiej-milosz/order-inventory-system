package dev.bartlomiejmilosz.inventory.orders;

import static org.assertj.core.api.Assertions.assertThat;

import dev.bartlomiejmilosz.inventory.config.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
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
    properties = "spring.application.name=draft-order-tests")
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderDraftTests {
  private static final UUID ADMIN = id(1);
  private static final UUID OPERATOR = id(2);
  private static final UUID DISABLED = id(3);
  private static final UUID CUSTOMER = id(4);
  private static final UUID ANOTHER_CUSTOMER = id(5);
  private static final UUID WAREHOUSE = id(6);
  private static final UUID ANOTHER_WAREHOUSE = id(7);
  private static final UUID PRODUCT = id(8);
  private static final UUID ANOTHER_PRODUCT = id(9);
  private static final String ADMIN_EMAIL = "admin@m5.test";
  private static final String OPERATOR_EMAIL = "operator@m5.test";
  private static final String DISABLED_EMAIL = "disabled@m5.test";
  private static final String PASSWORD = "Only-used-by-M5-tests!";
  private static final String ORDERS = "/api/v1/orders";

  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired OrderService orders;
  @Autowired EntityManager entities;
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
  void seedCatalogAndWarehouseStock() {
    jdbc.update(
        "INSERT INTO inventory.users (id,email,password_hash,role,active) VALUES (?,?,?,?,true),(?,?,?,?,true),(?,?,?,?,false)",
        ADMIN,
        ADMIN_EMAIL,
        passwordHash,
        "ADMIN",
        OPERATOR,
        OPERATOR_EMAIL,
        passwordHash,
        "OPERATOR",
        DISABLED,
        DISABLED_EMAIL,
        passwordHash,
        "OPERATOR");
    jdbc.update(
        "INSERT INTO inventory.customers (id,name,email) VALUES (?,'Alpha','alpha@m5.test'),(?,'Beta','beta@m5.test')",
        CUSTOMER,
        ANOTHER_CUSTOMER);
    jdbc.update(
        "INSERT INTO inventory.warehouses (id,code,name) VALUES (?,'NORTH','North'),(?,'SOUTH','South')",
        WAREHOUSE,
        ANOTHER_WAREHOUSE);
    jdbc.update(
        "INSERT INTO inventory.products (id,sku,name,unit_price) VALUES (?,'WIDGET','Original widget',12.50),(?, 'BRACKET','Bracket',0.10)",
        PRODUCT,
        ANOTHER_PRODUCT);
    jdbc.update(
        "INSERT INTO inventory.stock_levels (id,product_id,warehouse_id,on_hand,reserved,updated_at) VALUES (?,?,?,10,3,now())",
        id(10),
        PRODUCT,
        WAREHOUSE);
    jdbc.update(
        "INSERT INTO inventory.stock_movements (id,stock_level_id,performed_by,type,quantity_delta) VALUES (?,?,?,'IN',10)",
        id(11),
        id(10),
        ADMIN);
  }

  @AfterEach
  void cleanDatabase() {
    // This Spring context uses a PostgreSQL container dedicated to this test class.
    jdbc.execute(
        "TRUNCATE inventory.users, inventory.customers, inventory.products, inventory.warehouses CASCADE");
  }

  @Test
  void editsDraftAndKeepsProductSnapshotsAndStockUnchanged() throws Exception {
    var operator = csrf(OPERATOR_EMAIL);
    var admin = csrf(ADMIN_EMAIL);
    var levelBefore = jdbc.queryForList("SELECT * FROM inventory.stock_levels");
    var movementsBefore = jdbc.queryForList("SELECT * FROM inventory.stock_movements");
    var created =
        send(
            "POST",
            ORDERS,
            OPERATOR_EMAIL,
            operator,
            header(
                CUSTOMER,
                WAREHOUSE,
                ",\"orderNumber\":\" M5-001 \",\"createdBy\":\""
                    + ADMIN
                    + "\",\"status\":\"CONFIRMED\""));
    expect(created, 201);
    String draft = location(created);
    var initial = body(created);
    assertThat(initial.path("order").path("orderNumber").asString()).isEqualTo("M5-001");
    assertThat(initial.path("order").path("createdBy").asString()).isEqualTo(OPERATOR.toString());
    assertThat(initial.path("order").path("status").asString()).isEqualTo("DRAFT");
    assertThat(initial.path("order").path("confirmedAt").isNull()).isTrue();
    assertThat(initial.path("items").isEmpty()).isTrue();
    assertTotal(initial.path("order"), "0.00");
    assertThat(initial.path("order").path("createdAt"))
        .isEqualTo(body(get(draft, OPERATOR_EMAIL)).path("order").path("createdAt"));

    var productLine = send("POST", draft + "/items", OPERATOR_EMAIL, operator, line(PRODUCT, 30));
    expect(productLine, 201);
    String lineUrl = location(productLine);
    assertThat(body(get(lineUrl, ADMIN_EMAIL)).path("productSku").asString()).isEqualTo("WIDGET");
    expect(
        send(
            "PUT",
            "/api/v1/products/" + PRODUCT,
            ADMIN_EMAIL,
            admin,
            "{\"sku\":\"NEW-WIDGET\",\"name\":\"New widget\",\"unitPrice\":99.99}"),
        200);
    var updatedLine = send("PUT", lineUrl, ADMIN_EMAIL, admin, "{\"quantity\":4}");
    expect(updatedLine, 200);
    assertThat(body(updatedLine).path("productName").asString()).isEqualTo("Original widget");
    assertThat(body(updatedLine).path("productSku").asString()).isEqualTo("WIDGET");
    assertThat(body(updatedLine).path("unitPrice").decimalValue()).isEqualByComparingTo("12.50");
    assertThat(body(updatedLine).path("lineTotal").decimalValue()).isEqualByComparingTo("50.00");

    var second = send("POST", draft + "/items", OPERATOR_EMAIL, operator, line(ANOTHER_PRODUCT, 3));
    expect(second, 201);
    var changed =
        send(
            "PUT",
            draft,
            ADMIN_EMAIL,
            admin,
            header(
                ANOTHER_CUSTOMER, ANOTHER_WAREHOUSE, ",\"note\":\" Deliver at the south gate \""));
    expect(changed, 200);
    var summary = body(changed).path("order");
    assertThat(summary.path("warehouseId").asString()).isEqualTo(ANOTHER_WAREHOUSE.toString());
    assertThat(summary.path("customerId").asString()).isEqualTo(ANOTHER_CUSTOMER.toString());
    assertThat(summary.path("note").asString()).isEqualTo("Deliver at the south gate");
    assertThat(summary.path("orderNumber").asString()).isEqualTo("M5-001");
    assertThat(summary.path("createdBy").asString()).isEqualTo(OPERATOR.toString());
    assertTotal(summary, "50.30");
    assertThat(body(changed).path("items").size()).isEqualTo(2);
    expect(send("DELETE", location(second), OPERATOR_EMAIL, operator, null), 204);
    problem(get(location(second), OPERATOR_EMAIL), 404);
    assertTotal(body(get(draft, ADMIN_EMAIL)).path("order"), "50.00");
    assertThat(jdbc.queryForList("SELECT * FROM inventory.stock_levels")).isEqualTo(levelBefore);
    assertThat(jdbc.queryForList("SELECT * FROM inventory.stock_movements"))
        .isEqualTo(movementsBefore);
  }

  @Test
  void filtersPagesAndTotalsOrdersWithoutLosingEmptyDrafts() throws Exception {
    var operator = csrf(OPERATOR_EMAIL);
    var first = newOrder("M5-001", CUSTOMER, WAREHOUSE, operator);
    var second = newOrder("M5-002", CUSTOMER, WAREHOUSE, operator);
    var confirmed = newOrder("M5-003", CUSTOMER, WAREHOUSE, operator);
    newOrder("M5-004", ANOTHER_CUSTOMER, WAREHOUSE, operator);
    newOrder("M5-005", CUSTOMER, ANOTHER_WAREHOUSE, operator);
    expect(send("POST", first + "/items", OPERATOR_EMAIL, operator, line(PRODUCT, 2)), 201);
    expect(send("POST", first + "/items", OPERATOR_EMAIL, operator, line(ANOTHER_PRODUCT, 3)), 201);
    jdbc.update(
        "UPDATE inventory.sales_orders SET status='CONFIRMED', confirmed_at=now() WHERE id=?",
        orderId(confirmed));
    jdbc.update("UPDATE inventory.sales_orders SET created_at='2026-01-01T09:00:00Z'");
    var full = body(get(ORDERS, ADMIN_EMAIL));
    assertThat(full.path("totalElements").asLong()).isEqualTo(5);
    String filtered =
        ORDERS + "?status=DRAFT&customerId=" + CUSTOMER + "&warehouseId=" + WAREHOUSE + "&size=1";
    var ids = new ArrayList<String>();
    for (int num = 0; num < 2; num++) {
      var page = body(get(filtered + "&page=" + num, OPERATOR_EMAIL));
      assertThat(page.path("page").asInt()).isEqualTo(num);
      assertThat(page.path("totalPages").asInt()).isEqualTo(2);
      assertThat(page.path("totalElements").asLong()).isEqualTo(2);
      assertThat(page.path("items").size()).isEqualTo(1);
      var order = page.path("items").get(0);
      String id = order.path("id").asString();
      ids.add(id);
      assertTotal(order, id.equals(orderId(first).toString()) ? "25.30" : "0.00");
    }
    assertThat(ids)
        .containsExactlyElementsOf(
            List.of(orderId(first).toString(), orderId(second).toString()).stream()
                .sorted(Comparator.reverseOrder())
                .toList());
    assertThat(
            body(get(ORDERS + "?status=CONFIRMED", ADMIN_EMAIL))
                .path("items")
                .get(0)
                .path("id")
                .asString())
        .isEqualTo(orderId(confirmed).toString());
    var outOfRange = body(get(filtered + "&page=4", OPERATOR_EMAIL));
    assertThat(outOfRange.path("totalElements").asLong()).isEqualTo(2);
    assertThat(outOfRange.path("items").isEmpty()).isTrue();
    var none = body(get(ORDERS + "?warehouseId=" + UUID.randomUUID(), ADMIN_EMAIL));
    assertThat(none.path("totalElements").asLong()).isZero();
    assertThat(none.path("items").isEmpty()).isTrue();
  }

  @Test
  void enforcesAuthenticationCsrfAndValidInput() throws Exception {
    var operator = csrf(OPERATOR_EMAIL);
    String json = header(CUSTOMER, WAREHOUSE, ",\"orderNumber\":\"NEW\"");
    problem(get(ORDERS, null), 401);
    problem(get(ORDERS, DISABLED_EMAIL), 401);
    problem(send("POST", ORDERS, OPERATOR_EMAIL, null, json), 403);
    problem(
        send(
            "POST",
            ORDERS,
            OPERATOR_EMAIL,
            operator,
            header(CUSTOMER, WAREHOUSE, ",\"orderNumber\":\"  \"")),
        400);
    problem(
        send(
            "POST",
            ORDERS,
            OPERATOR_EMAIL,
            operator,
            header(CUSTOMER, WAREHOUSE, ",\"orderNumber\":\"" + "A".repeat(31) + "\"")),
        400);
    problem(
        send(
            "POST",
            ORDERS,
            OPERATOR_EMAIL,
            operator,
            header(
                CUSTOMER,
                WAREHOUSE,
                ",\"orderNumber\":\"OK\",\"note\":\"" + "x".repeat(501) + "\"")),
        400);
    problem(
        send(
            "POST",
            ORDERS,
            OPERATOR_EMAIL,
            operator,
            header(UUID.randomUUID(), WAREHOUSE, ",\"orderNumber\":\"NEW\"")),
        404);
    problem(
        send(
            "POST",
            ORDERS,
            OPERATOR_EMAIL,
            operator,
            header(CUSTOMER, UUID.randomUUID(), ",\"orderNumber\":\"NEW\"")),
        404);
    problem(get(ORDERS + "/" + UUID.randomUUID(), ADMIN_EMAIL), 404);
    problem(get(ORDERS + "/bad-id", ADMIN_EMAIL), 400);
    problem(get(ORDERS + "?status=UNKNOWN", ADMIN_EMAIL), 400);
    problem(get(ORDERS + "?customerId=bad-id", ADMIN_EMAIL), 400);
    problem(get(ORDERS + "?size=101", ADMIN_EMAIL), 400);
    problem(get(ORDERS + "?page=-1", ADMIN_EMAIL), 400);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.sales_orders", Long.class))
        .isZero();
    var draft = newOrder("NEW", CUSTOMER, WAREHOUSE, operator);
    problem(
        send(
            "POST",
            ORDERS,
            OPERATOR_EMAIL,
            operator,
            header(ANOTHER_CUSTOMER, WAREHOUSE, ",\"orderNumber\":\" NEW \"")),
        409);
    String items = draft + "/items";
    problem(send("POST", items, OPERATOR_EMAIL, operator, line(PRODUCT, 0)), 400);
    problem(
        send(
            "POST",
            items,
            OPERATOR_EMAIL,
            operator,
            "{\"productId\":\"" + PRODUCT + "\",\"quantity\":1.5}"),
        400);
    problem(send("POST", items, OPERATOR_EMAIL, operator, "{\"quantity\":1}"), 400);
    problem(send("POST", items, OPERATOR_EMAIL, operator, line(UUID.randomUUID(), 1)), 404);
    var added = send("POST", items, OPERATOR_EMAIL, operator, line(PRODUCT, 2));
    expect(added, 201);
    problem(send("POST", items, OPERATOR_EMAIL, operator, line(PRODUCT, 1)), 409);
    problem(send("PUT", location(added), OPERATOR_EMAIL, operator, "{\"quantity\":-1}"), 400);
    problem(send("PUT", location(added), OPERATOR_EMAIL, operator, "{}"), 400);
    problem(send("POST", draft + "/fulfill", OPERATOR_EMAIL, operator, null), 403);
    assertThat(body(get(draft, ADMIN_EMAIL)).path("items").size()).isEqualTo(1);
  }

  @Test
  void rejectsNullCharactersInOrderTextBeforePersistence() throws Exception {
    var operator = csrf(OPERATOR_EMAIL);
    String invalidNumber = header(CUSTOMER, WAREHOUSE, ",\"orderNumber\":\"A\\u0000B\"");
    problem(send("POST", ORDERS, OPERATOR_EMAIL, operator, invalidNumber), 400);
    String invalidNote = header(CUSTOMER, WAREHOUSE, ",\"note\":\"A\\u0000B\"");
    String create = header(CUSTOMER, WAREHOUSE, ",\"orderNumber\":\"NEW\",\"note\":\"A\\u0000B\"");
    problem(send("POST", ORDERS, OPERATOR_EMAIL, operator, create), 400);
    String draft = newOrder("NEW", CUSTOMER, WAREHOUSE, operator);
    problem(send("PUT", draft, OPERATOR_EMAIL, operator, invalidNote), 400);
    assertThat(body(get(draft, OPERATOR_EMAIL)).path("order").path("note").isNull()).isTrue();
  }

  @Test
  void preservesHistoryAndRejectsEditsOutsideDraftOrAgainstInactiveCatalog() throws Exception {
    var admin = csrf(ADMIN_EMAIL);
    var operator = csrf(OPERATOR_EMAIL);
    String draft = newOrder("EDITABLE", CUSTOMER, WAREHOUSE, operator);
    String other = newOrder("OTHER", CUSTOMER, WAREHOUSE, operator);
    var line = send("POST", draft + "/items", OPERATOR_EMAIL, operator, line(PRODUCT, 2));
    expect(line, 201);
    String lineUrl = location(line);
    String foreign = other + "/items/" + body(line).path("id").asString();
    problem(get(foreign, OPERATOR_EMAIL), 404);
    problem(send("PUT", foreign, OPERATOR_EMAIL, operator, "{\"quantity\":3}"), 404);
    problem(send("DELETE", foreign, OPERATOR_EMAIL, operator, null), 404);

    expect(
        send(
            "POST",
            "/api/v1/warehouses/" + ANOTHER_WAREHOUSE + "/deactivate",
            ADMIN_EMAIL,
            admin,
            null),
        204);
    problem(
        send(
            "POST",
            ORDERS,
            OPERATOR_EMAIL,
            operator,
            header(CUSTOMER, ANOTHER_WAREHOUSE, ",\"orderNumber\":\"INACTIVE\"")),
        409);
    problem(
        send("PUT", draft, OPERATOR_EMAIL, operator, header(CUSTOMER, ANOTHER_WAREHOUSE, "")), 409);
    expect(
        send(
            "POST",
            "/api/v1/products/" + ANOTHER_PRODUCT + "/deactivate",
            ADMIN_EMAIL,
            admin,
            null),
        204);
    problem(
        send("POST", draft + "/items", OPERATOR_EMAIL, operator, line(ANOTHER_PRODUCT, 1)), 409);
    expect(
        send("POST", "/api/v1/products/" + PRODUCT + "/deactivate", ADMIN_EMAIL, admin, null), 204);
    problem(send("PUT", lineUrl, OPERATOR_EMAIL, operator, "{\"quantity\":3}"), 409);
    jdbc.update("UPDATE inventory.products SET active = true");
    expect(
        send("POST", "/api/v1/warehouses/" + WAREHOUSE + "/deactivate", ADMIN_EMAIL, admin, null),
        204);
    problem(
        send("POST", draft + "/items", OPERATOR_EMAIL, operator, line(ANOTHER_PRODUCT, 1)), 409);
    problem(send("PUT", lineUrl, OPERATOR_EMAIL, operator, "{\"quantity\":3}"), 409);
    expect(send("DELETE", lineUrl, OPERATOR_EMAIL, operator, null), 204);
    jdbc.update("UPDATE inventory.warehouses SET active = true");
    var fresh = send("POST", draft + "/items", OPERATOR_EMAIL, operator, line(PRODUCT, 2));
    expect(fresh, 201);
    for (String status : List.of("CONFIRMED", "FULFILLED", "CANCELLED")) {
      jdbc.update(
          "UPDATE inventory.sales_orders SET status=?, confirmed_at=now() WHERE id=?",
          status,
          orderId(draft));
      problem(send("PUT", draft, ADMIN_EMAIL, admin, header(ANOTHER_CUSTOMER, WAREHOUSE, "")), 409);
      problem(
          send("POST", draft + "/items", OPERATOR_EMAIL, operator, line(ANOTHER_PRODUCT, 1)), 409);
      problem(send("PUT", location(fresh), OPERATOR_EMAIL, operator, "{\"quantity\":3}"), 409);
      problem(send("DELETE", location(fresh), ADMIN_EMAIL, admin, null), 409);
      assertThat(body(get(draft, OPERATOR_EMAIL)).path("order").path("status").asString())
          .isEqualTo(status);
      assertThat(body(get(location(fresh), OPERATOR_EMAIL)).path("quantity").asInt()).isEqualTo(2);
    }
  }

  @Test
  void waitsForTheOrderLockAndRechecksStatusBeforeEditingTheLine() throws Exception {
    var operator = csrf(OPERATOR_EMAIL);
    String draft = newOrder("CONCURRENT", CUSTOMER, WAREHOUSE, operator);
    var line = send("POST", draft + "/items", OPERATOR_EMAIL, operator, line(PRODUCT, 1));
    expect(line, 201);
    var transaction = new TransactionTemplate(transactionManager);
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      var waitingEdit =
          transaction.execute(state -> {
            int blocker = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
            // Isolate the M5 editing guard from the rest of the confirmation workflow.
            jdbc.update(
                "UPDATE inventory.sales_orders SET status='CONFIRMED', confirmed_at=now() WHERE id=?",
                orderId(draft));
            var future =
                workers.submit(() ->
                    send("PUT", location(line), OPERATOR_EMAIL, operator, "{\"quantity\":7}"));
            assertBlockedBy(blocker);
            return future;
          });
      problem(waitingEdit.get(10, TimeUnit.SECONDS), 409);
    }
    assertThat(body(get(location(line), OPERATOR_EMAIL)).path("quantity").asInt()).isEqualTo(1);
    assertThat(body(get(draft, OPERATOR_EMAIL)).path("order").path("status").asString())
        .isEqualTo("CONFIRMED");
  }

  @Nested
  class Confirmation {
    @BeforeEach
    void startWithoutPreexistingReservations() {
      jdbc.update("UPDATE inventory.stock_levels SET reserved=0");
    }

    @Test
    void confirmsAllItemsInTheOrderWarehouseWithoutPhysicalMovements() throws Exception {
      var operator = csrf(OPERATOR_EMAIL);
      var admin = csrf(ADMIN_EMAIL);
      addStock(id(20), ANOTHER_PRODUCT, WAREHOUSE, 6);
      addStock(id(21), PRODUCT, ANOTHER_WAREHOUSE, 70);
      var originalMovements =
          jdbc.queryForList("SELECT * FROM inventory.stock_movements ORDER BY id");
      String previous = newOrder("PREVIOUS", CUSTOMER, WAREHOUSE, operator);
      addLine(previous, PRODUCT, 3, operator);
      expect(send("POST", previous + "/confirm", ADMIN_EMAIL, admin, null), 200);
      String draft = newOrder("MULTI", CUSTOMER, WAREHOUSE, operator);
      String firstLine = addLine(draft, PRODUCT, 4, operator);
      addLine(draft, ANOTHER_PRODUCT, 2, operator);
      var originalLines = body(get(draft, OPERATOR_EMAIL)).path("items");

      var result = send("POST", draft + "/confirm", OPERATOR_EMAIL, operator, null);
      expect(result, 200);
      var confirmed = body(result).path("order");
      assertThat(confirmed.path("status").asString()).isEqualTo("CONFIRMED");
      Instant timestamp = Instant.parse(confirmed.path("confirmedAt").asString());
      assertThat(timestamp)
          .isEqualTo(
              Instant.parse(
                  body(get(draft, ADMIN_EMAIL)).path("order").path("confirmedAt").asString()));
      assertThat(confirmed.path("createdBy").asString()).isEqualTo(OPERATOR.toString());
      assertTotal(confirmed, "50.20");
      assertThat(body(result).path("items")).isEqualTo(originalLines);

      var levels =
          jdbc.queryForList("SELECT on_hand, reserved FROM inventory.stock_levels ORDER BY id");
      assertThat(levels)
          .containsExactly(
              Map.of("on_hand", 10, "reserved", 7),
              Map.of("on_hand", 6, "reserved", 2),
              Map.of("on_hand", 70, "reserved", 0));
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM inventory.stock_levels WHERE warehouse_id=? AND updated_at=(SELECT confirmed_at FROM inventory.sales_orders WHERE id=?)",
                  Long.class,
                  WAREHOUSE,
                  orderId(draft)))
          .isEqualTo(2);
      assertThat(jdbc.queryForList("SELECT * FROM inventory.stock_movements ORDER BY id"))
          .isEqualTo(originalMovements);
      assertThat(body(get(ORDERS + "?status=CONFIRMED", ADMIN_EMAIL)).path("totalElements").asInt())
          .isEqualTo(2);
      problem(send("PUT", firstLine, OPERATOR_EMAIL, operator, "{\"quantity\":9}"), 409);
      problem(send("POST", draft + "/confirm", OPERATOR_EMAIL, operator, null), 409);
      assertThat(
              jdbc.queryForList("SELECT on_hand, reserved FROM inventory.stock_levels ORDER BY id"))
          .isEqualTo(levels);
    }

    @Test
    void rejectsInvalidConfirmationWithoutCreatingStockOrWritingTransitions() throws Exception {
      var operator = csrf(OPERATOR_EMAIL);
      var admin = csrf(ADMIN_EMAIL);
      String draft = newOrder("INVALID", CUSTOMER, WAREHOUSE, operator);
      problem(send("POST", draft + "/confirm", null, operator, null), 401);
      problem(send("POST", draft + "/confirm", DISABLED_EMAIL, operator, null), 401);
      problem(send("POST", draft + "/confirm", OPERATOR_EMAIL, null, null), 403);
      problem(
          send(
              "POST",
              ORDERS + "/" + UUID.randomUUID() + "/confirm",
              OPERATOR_EMAIL,
              operator,
              null),
          404);
      problem(send("POST", ORDERS + "/invalid/confirm", OPERATOR_EMAIL, operator, null), 400);
      problem(send("POST", draft + "/confirm", OPERATOR_EMAIL, operator, null), 409);
      assertDraft(draft);
      addLine(draft, PRODUCT, 5, operator);
      addLine(draft, ANOTHER_PRODUCT, 1, operator);
      // Stock in a different warehouse cannot satisfy the missing second level.
      addStock(id(20), ANOTHER_PRODUCT, ANOTHER_WAREHOUSE, 60);
      var beforeMissing = stockRows();
      problem(send("POST", draft + "/confirm", OPERATOR_EMAIL, operator, null), 409);
      assertDraft(draft);
      assertThat(stockRows()).isEqualTo(beforeMissing);
      addStock(id(21), ANOTHER_PRODUCT, WAREHOUSE, 60);
      var before = stockRows();
      expect(
          send(
              "POST",
              "/api/v1/products/" + ANOTHER_PRODUCT + "/deactivate",
              ADMIN_EMAIL,
              admin,
              null),
          204);
      problem(send("POST", draft + "/confirm", OPERATOR_EMAIL, operator, null), 409);
      jdbc.update("UPDATE inventory.products SET active=true WHERE id=?", ANOTHER_PRODUCT);
      expect(
          send("POST", "/api/v1/warehouses/" + WAREHOUSE + "/deactivate", ADMIN_EMAIL, admin, null),
          204);
      problem(send("POST", draft + "/confirm", OPERATOR_EMAIL, operator, null), 409);
      assertDraft(draft);
      assertThat(stockRows()).isEqualTo(before);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.stock_movements", Long.class))
          .isEqualTo(1);
    }

    @Test
    void rechecksProductActivityAfterWaitingForDeactivation() throws Exception {
      var operator = csrf(OPERATOR_EMAIL);
      String draft = newOrder("DEACTIVATING", CUSTOMER, WAREHOUSE, operator);
      addLine(draft, PRODUCT, 3, operator);
      problem(
          runWhileLocked(
              () -> jdbc.update("UPDATE inventory.products SET active=false WHERE id=?", PRODUCT),
              () -> send("POST", draft + "/confirm", OPERATOR_EMAIL, operator, null)),
          409);
      assertDraft(draft);
      assertReserved(0);
    }

    @Test
    void rollsBackWhenTheSecondLockedProductHasInsufficientStock() throws Exception {
      var operator = csrf(OPERATOR_EMAIL);
      addStock(id(20), ANOTHER_PRODUCT, WAREHOUSE, 1);
      String draft = newOrder("PARTIAL", CUSTOMER, WAREHOUSE, operator);
      addLine(draft, PRODUCT, 5, operator);
      addLine(draft, ANOTHER_PRODUCT, 2, operator);
      var before = stockRows();
      var response = send("POST", draft + "/confirm", OPERATOR_EMAIL, operator, null);
      problem(response, 409);
      assertThat(body(response).path("detail").asString()).contains("available stock");
      assertDraft(draft);
      assertThat(stockRows()).isEqualTo(before);
    }

    @Test
    void rollsBackBothTheFlushedReservationsAndTheOrderTransition() throws Exception {
      var operator = csrf(OPERATOR_EMAIL);
      addStock(id(20), ANOTHER_PRODUCT, WAREHOUSE, 4);
      String draft = newOrder("ROLLBACK", CUSTOMER, WAREHOUSE, operator);
      addLine(draft, PRODUCT, 5, operator);
      addLine(draft, ANOTHER_PRODUCT, 2, operator);
      var before = stockRows();
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(state -> {
            orders.confirm(orderId(draft));
            entities.flush();
            assertThat(
                    jdbc.queryForObject(
                        "SELECT status FROM inventory.sales_orders WHERE id=?",
                        String.class,
                        orderId(draft)))
                .isEqualTo("CONFIRMED");
            assertThat(
                    jdbc.queryForList(
                        "SELECT reserved FROM inventory.stock_levels ORDER BY id", Integer.class))
                .containsExactly(5, 2);
            state.setRollbackOnly();
          });
      assertDraft(draft);
      assertThat(stockRows()).isEqualTo(before);
    }

    @Test
    void onlyOneOrderCanReserveTheLastUnitsInTwoIndependentTransactions() throws Exception {
      var operator = csrf(OPERATOR_EMAIL);
      String first = newOrder("WINNER", CUSTOMER, WAREHOUSE, operator);
      String second = newOrder("WAITER", CUSTOMER, WAREHOUSE, operator);
      addLine(first, PRODUCT, 10, operator);
      addLine(second, PRODUCT, 10, operator);
      problem(
          runWhileLocked(
              () -> orders.confirm(orderId(first)),
              () -> send("POST", second + "/confirm", OPERATOR_EMAIL, operator, null)),
          409);
      assertThat(body(get(first, OPERATOR_EMAIL)).path("order").path("status").asString())
          .isEqualTo("CONFIRMED");
      assertDraft(second);
      assertReserved(10);
    }

    @Test
    void confirmingTheSameOrderConcurrentlyReservesOnlyOnce() throws Exception {
      var operator = csrf(OPERATOR_EMAIL);
      String draft = newOrder("ONCE", CUSTOMER, WAREHOUSE, operator);
      addLine(draft, PRODUCT, 4, operator);
      problem(
          runWhileLocked(
              () -> orders.confirm(orderId(draft)),
              () -> send("POST", draft + "/confirm", OPERATOR_EMAIL, operator, null)),
          409);
      assertReserved(4);
    }

    @Test
    void editWaitingBehindConfirmationCannotChangeTheConfirmedLine() throws Exception {
      var operator = csrf(OPERATOR_EMAIL);
      String draft = newOrder("CONFIRM-FIRST", CUSTOMER, WAREHOUSE, operator);
      String line = addLine(draft, PRODUCT, 3, operator);
      problem(
          runWhileLocked(
              () -> orders.confirm(orderId(draft)),
              () -> send("PUT", line, OPERATOR_EMAIL, operator, "{\"quantity\":5}")),
          409);
      assertThat(body(get(line, OPERATOR_EMAIL)).path("quantity").asInt()).isEqualTo(3);
      assertReserved(3);
    }

    @Test
    void confirmationWaitingBehindAnEditReservesTheUpdatedQuantity() throws Exception {
      var operator = csrf(OPERATOR_EMAIL);
      String draft = newOrder("EDIT-FIRST", CUSTOMER, WAREHOUSE, operator);
      String line = addLine(draft, PRODUCT, 3, operator);
      var confirmed =
          runWhileLocked(
              () ->
                  orders.updateItem(
                      orderId(draft), orderId(line), new OrderDto.UpdateDraftOrderItemRequest(5)),
              () -> send("POST", draft + "/confirm", OPERATOR_EMAIL, operator, null));
      expect(confirmed, 200);
      assertThat(body(confirmed).path("items").get(0).path("quantity").asInt()).isEqualTo(5);
      assertReserved(5);
    }

    @Test
    void waitsForTheLowestStockIdBeforeLockingTheNextStockRow() throws Exception {
      var operator = csrf(OPERATOR_EMAIL);
      // Product and stock IDs are deliberately in opposite order.
      addStock(id(0), ANOTHER_PRODUCT, WAREHOUSE, 6);
      String draft = newOrder("STOCK-LOCK-ORDER", CUSTOMER, WAREHOUSE, operator);
      addLine(draft, PRODUCT, 1, operator);
      addLine(draft, ANOTHER_PRODUCT, 2, operator);
      var transaction = new TransactionTemplate(transactionManager);
      try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
        var waiter =
            transaction.execute(state -> {
              int blocker = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
              jdbc.queryForObject(
                  "SELECT id FROM inventory.stock_levels WHERE id=? FOR UPDATE", UUID.class, id(0));
              var future =
                  workers.submit(
                      () -> send("POST", draft + "/confirm", OPERATOR_EMAIL, operator, null));
              assertBlockedBy(blocker);
              // If confirm had locked the higher ID earlier, NOWAIT would fail here.
              jdbc.queryForObject(
                  "SELECT id FROM inventory.stock_levels WHERE id=? FOR UPDATE NOWAIT",
                  UUID.class,
                  id(10));
              return future;
            });
        expect(waiter.get(10, TimeUnit.SECONDS), 200);
      }
      assertThat(
              jdbc.queryForList(
                  "SELECT reserved FROM inventory.stock_levels ORDER BY id", Integer.class))
          .containsExactly(2, 1);
    }

    private HttpResponse<String> runWhileLocked(
        Runnable first, Callable<HttpResponse<String>> second) throws Exception {
      var transaction = new TransactionTemplate(transactionManager);
      try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
        var waiter =
            transaction.execute(state -> {
              int blocker = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
              first.run();
              var future = workers.submit(second);
              assertBlockedBy(blocker);
              return future;
            });
        return waiter.get(10, TimeUnit.SECONDS);
      }
    }

    private void addStock(UUID id, UUID product, UUID warehouse, int onHand) {
      jdbc.update(
          "INSERT INTO inventory.stock_levels(id,product_id,warehouse_id,on_hand,reserved,updated_at) VALUES (?,?,?,?,0,now())",
          id,
          product,
          warehouse,
          onHand);
    }

    private String addLine(String order, UUID product, int quantity, Csrf csrf) throws Exception {
      var result = send("POST", order + "/items", OPERATOR_EMAIL, csrf, line(product, quantity));
      expect(result, 201);
      return location(result);
    }

    private List<Map<String, Object>> stockRows() {
      return jdbc.queryForList("SELECT * FROM inventory.stock_levels ORDER BY id");
    }

    private void assertDraft(String draft) throws Exception {
      var header = body(get(draft, OPERATOR_EMAIL)).path("order");
      assertThat(header.path("status").asString()).isEqualTo("DRAFT");
      assertThat(header.path("confirmedAt").isNull()).isTrue();
    }

    private void assertReserved(int expected) {
      var stock =
          jdbc.queryForMap(
              "SELECT on_hand, reserved FROM inventory.stock_levels WHERE product_id=? AND warehouse_id=?",
              PRODUCT,
              WAREHOUSE);
      assertThat(stock).containsEntry("on_hand", 10).containsEntry("reserved", expected);
      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT coalesce(sum(i.quantity),0) FROM inventory.order_items i JOIN inventory.sales_orders o ON o.id=i.order_id
                  WHERE o.status='CONFIRMED' AND i.product_id=? AND o.warehouse_id=?
                  """,
                  Integer.class,
                  PRODUCT,
                  WAREHOUSE))
          .isEqualTo(expected);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.stock_movements", Long.class))
          .isEqualTo(1);
    }
  }

  private void assertBlockedBy(int blocker) {
    long cutoff = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < cutoff) {
      if (Boolean.TRUE.equals(
          jdbc.queryForObject(
              """
              SELECT EXISTS(SELECT 1 FROM pg_stat_activity
              WHERE datname=current_database() AND ? = ANY(pg_blocking_pids(pid)))
              """,
              Boolean.class,
              blocker))) {
        return;
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
    throw new AssertionError(
        "PostgreSQL did not report a transaction waiting for the expected blocker");
  }

  private String newOrder(String number, UUID customer, UUID warehouse, Csrf csrf)
      throws Exception {
    var response =
        send(
            "POST",
            ORDERS,
            OPERATOR_EMAIL,
            csrf,
            header(customer, warehouse, ",\"orderNumber\":\"" + number + "\""));
    expect(response, 201);
    return location(response);
  }

  private static String header(UUID customer, UUID warehouse, String extras) {
    return "{\"customerId\":\""
        + customer
        + "\",\"warehouseId\":\""
        + warehouse
        + "\""
        + extras
        + "}";
  }

  private static String line(UUID product, int quantity) {
    return "{\"productId\":\"" + product + "\",\"quantity\":" + quantity + "}";
  }

  private static UUID id(int suffix) {
    return UUID.fromString("50000000-0000-0000-0000-%012d".formatted(suffix));
  }

  private static UUID orderId(String path) {
    return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
  }

  private static void assertTotal(JsonNode summary, String expected) {
    assertThat(summary.path("total").decimalValue()).isEqualByComparingTo(expected);
  }

  private Csrf csrf(String employee) throws Exception {
    var result = get("/api/v1/auth/csrf", employee);
    expect(result, 200);
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

  private static String location(HttpResponse<String> response) {
    return response.headers().firstValue("Location").orElseThrow();
  }

  private static void expect(HttpResponse<String> response, int code) {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(code);
  }

  private void problem(HttpResponse<String> response, int code) {
    expect(response, code);
    assertThat(response.headers().firstValue("Content-Type").orElseThrow())
        .startsWith("application/problem+json");
    assertThat(body(response).path("status").asInt()).isEqualTo(code);
  }

  private record Csrf(String cookie, String header, String token) {}
}
