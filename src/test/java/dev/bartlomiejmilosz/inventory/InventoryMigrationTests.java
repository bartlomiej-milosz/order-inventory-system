package dev.bartlomiejmilosz.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.bartlomiejmilosz.inventory.config.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InventoryMigrationTests {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void migratesAnEmptyDatabase() {
    assertThat(
            jdbc.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'inventory' AND table_type = 'BASE TABLE'
                """,
                String.class))
        .containsExactlyInAnyOrder(
            "users",
            "customers",
            "products",
            "warehouses",
            "stock_levels",
            "sales_orders",
            "order_items",
            "stock_movements");

    assertThat(
            jdbc.queryForObject(
                "SELECT success FROM flyway_history.flyway_schema_history WHERE version = '1'",
                Boolean.class))
        .isTrue();

    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*)
                FROM pg_constraint
                WHERE connamespace = 'inventory'::regnamespace
                  AND contype = 'f' AND confdeltype = 'r'
                """,
                Integer.class))
        .isEqualTo(10);
  }

  @Test
  @Transactional
  @Sql("/sql/seed.sql")
  void keepsStockSeparateByWarehouseAndStoresTheOrderedPrice() {
    assertThat(
            jdbc.queryForList(
                """
                SELECT s.on_hand - s.reserved
                FROM inventory.stock_levels s
                JOIN inventory.products p ON p.id = s.product_id
                JOIN inventory.warehouses w ON w.id = s.warehouse_id
                WHERE p.sku = 'WIDGET'
                ORDER BY w.code
                """,
                Integer.class))
        .containsExactly(7, 4);

    assertThat(
            jdbc.queryForObject(
                """
                SELECT SUM(i.quantity * i.unit_price)
                FROM inventory.order_items i
                JOIN inventory.sales_orders o ON o.id = i.order_id
                WHERE o.order_number = 'DEMO-003'
                """,
                BigDecimal.class))
        .isEqualByComparingTo("23.00");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          duplicate primary key           | 23505 | INSERT INTO inventory.products SELECT * FROM inventory.products WHERE sku = 'WIDGET'
          missing customer name           | 23502 | UPDATE inventory.customers SET name = NULL
          blank warehouse code            | 23514 | UPDATE inventory.warehouses SET code = E'  \\t'
          unnormalized login              | 23514 | UPDATE inventory.users SET email = 'OPERATOR@example.test'
          unsupported role                | 23514 | UPDATE inventory.users SET role = 'MANAGER'
          negative price                  | 23514 | UPDATE inventory.products SET unit_price = -0.01
          NaN price                       | 23514 | UPDATE inventory.products SET unit_price = 'NaN'
          duplicate stock pair            | 23505 | INSERT INTO inventory.stock_levels SELECT gen_random_uuid(), product_id, warehouse_id, 0, 0, now() FROM inventory.stock_levels WHERE id = '00000000-0000-0000-0000-000000000501'
          unknown product                 | 23503 | UPDATE inventory.stock_levels SET product_id = 'ffffffff-ffff-ffff-ffff-ffffffffffff' WHERE id = '00000000-0000-0000-0000-000000000501'
          stock below zero                | 23514 | UPDATE inventory.stock_levels SET on_hand = -1
          negative reservation            | 23514 | UPDATE inventory.stock_levels SET reserved = -1
          reservation exceeds stock       | 23514 | UPDATE inventory.stock_levels SET reserved = on_hand + 1
          unknown order status            | 23514 | UPDATE inventory.sales_orders SET status = 'PENDING'
          confirmation without timestamp  | 23514 | UPDATE inventory.sales_orders SET confirmed_at = NULL WHERE status = 'CONFIRMED'
          draft with confirmation time    | 23514 | UPDATE inventory.sales_orders SET confirmed_at = now() WHERE status = 'DRAFT'
          duplicate product in order      | 23505 | INSERT INTO inventory.order_items SELECT gen_random_uuid(), order_id, product_id, product_name, product_sku, quantity, unit_price FROM inventory.order_items WHERE id = '00000000-0000-0000-0000-000000000701'
          zero order quantity             | 23514 | UPDATE inventory.order_items SET quantity = 0
          positive outbound quantity      | 23514 | UPDATE inventory.stock_movements SET quantity_delta = 1 WHERE type = 'OUT'
          outbound without order          | 23514 | UPDATE inventory.stock_movements SET order_id = NULL WHERE type = 'OUT'
          inbound with order              | 23514 | UPDATE inventory.stock_movements SET order_id = '00000000-0000-0000-0000-000000000603' WHERE type = 'IN'
          adjustment without note         | 23514 | UPDATE inventory.stock_movements SET note = NULL WHERE type = 'ADJUSTMENT'
          adjustment with blank note      | 23514 | UPDATE inventory.stock_movements SET note = E'  \\t' WHERE type = 'ADJUSTMENT'
          zero adjustment                 | 23514 | UPDATE inventory.stock_movements SET quantity_delta = 0 WHERE type = 'ADJUSTMENT'
          delete referenced warehouse     | 23503 | DELETE FROM inventory.warehouses WHERE code = 'NORTH'
          """)
  @Transactional
  @Sql("/sql/seed.sql")
  void rejectsInvalidData(String description, String sqlState, String sql) {
    assertThatThrownBy(() -> jdbc.update(sql))
        .as(description)
        .isInstanceOfSatisfying(
            DataIntegrityViolationException.class,
            failure ->
                assertThat(failure.getMostSpecificCause())
                    .isInstanceOfSatisfying(
                        SQLException.class,
                        cause -> assertThat(cause.getSQLState()).isEqualTo(sqlState)));
  }

  @Test
  void rollsBackEarlierWritesWhenALaterStatementViolatesAConstraint() {
    var transaction = new TransactionTemplate(transactionManager);

    assertThatThrownBy(() ->
            transaction.executeWithoutResult(status -> {
              jdbc.update(
                  "INSERT INTO inventory.warehouses (id, code, name) VALUES (gen_random_uuid(),"
                      + " 'ROLLBACK', 'Rollback probe')");
              jdbc.update(
                  "INSERT INTO inventory.warehouses (id, code, name) VALUES (gen_random_uuid(),"
                      + " 'ROLLBACK', 'Duplicate')");
            }))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM inventory.warehouses WHERE code = 'ROLLBACK'", Integer.class))
        .isZero();
  }
}
