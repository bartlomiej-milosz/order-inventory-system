package dev.bartlomiejmilosz.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.bartlomiejmilosz.inventory.catalog.Customer;
import dev.bartlomiejmilosz.inventory.catalog.Product;
import dev.bartlomiejmilosz.inventory.config.TestcontainersConfiguration;
import dev.bartlomiejmilosz.inventory.inventory.MovementType;
import dev.bartlomiejmilosz.inventory.inventory.StockLevel;
import dev.bartlomiejmilosz.inventory.inventory.StockMovement;
import dev.bartlomiejmilosz.inventory.inventory.Warehouse;
import dev.bartlomiejmilosz.inventory.orders.OrderItem;
import dev.bartlomiejmilosz.inventory.orders.OrderStatus;
import dev.bartlomiejmilosz.inventory.orders.SalesOrder;
import dev.bartlomiejmilosz.inventory.users.EmployeeRole;
import dev.bartlomiejmilosz.inventory.users.EmployeeUser;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class EntityMappingTests {
  @Autowired EntityManager entities;

  private UUID id(int suffix) {
    return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
  }

  @Test
  @Sql("/sql/demo-data.sql")
  void readsAllEightEntitiesFromTheExistingMigration() {
    assertThat(entities.getMetamodel().getEntities()).hasSize(8);
    EmployeeUser user = entities.find(EmployeeUser.class, id(101));
    assertThat(user.getEmail()).isEqualTo("operator@example.test");
    assertThat(user.getRole()).isEqualTo(EmployeeRole.OPERATOR);
    assertThat(user.isActive()).isFalse();
    assertThat(user.getCreatedAt()).isNotNull();
    Customer customer = entities.find(Customer.class, id(201));
    assertThat(customer.getName()).isEqualTo("Demo Retail");
    assertThat(customer.getPhone()).isNull();
    Product product = entities.find(Product.class, id(301));
    assertThat(product.getUnitPrice()).isEqualByComparingTo("12.50");
    assertThat(product.isActive()).isTrue();
    Warehouse warehouse = entities.find(Warehouse.class, id(401));
    assertThat(warehouse.getCode()).isEqualTo("NORTH");
    assertThat(warehouse.isActive()).isTrue();
    StockLevel stock = entities.find(StockLevel.class, id(501));
    assertThat(stock.getProduct().getId()).isEqualTo(product.getId());
    assertThat(stock.getWarehouse().getId()).isEqualTo(warehouse.getId());
    assertThat(stock.getOnHand()).isEqualTo(10);
    assertThat(stock.getReserved()).isEqualTo(3);
    assertThat(stock.getAvailable()).isEqualTo(7);
    assertThat(stock.getUpdatedAt()).isNotNull();
    SalesOrder order = entities.find(SalesOrder.class, id(603));
    assertThat(order.getOrderNumber()).isEqualTo("DEMO-003");
    assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
    assertThat(order.getCustomer().getId()).isEqualTo(customer.getId());
    assertThat(order.getWarehouse().getId()).isEqualTo(warehouse.getId());
    assertThat(order.getCreatedBy().getId()).isEqualTo(user.getId());
    assertThat(order.getConfirmedAt()).isNotNull();
    assertThat(order.getNote()).isNull();
    OrderItem item = entities.find(OrderItem.class, id(703));
    assertThat(item.getOrder().getId()).isEqualTo(order.getId());
    assertThat(item.getProduct().getId()).isEqualTo(product.getId());
    assertThat(item.getProductName()).isEqualTo("Classic Widget");
    assertThat(item.getProductSku()).isEqualTo("WIDGET");
    assertThat(item.getQuantity()).isEqualTo(2);
    assertThat(item.getUnitPrice()).isEqualByComparingTo("11.50");
    StockMovement movement = entities.find(StockMovement.class, id(804));
    assertThat(movement.getStockLevel().getId()).isEqualTo(stock.getId());
    assertThat(movement.getPerformedBy().getId()).isEqualTo(user.getId());
    assertThat(movement.getOrder().getId()).isEqualTo(order.getId());
    assertThat(movement.getType()).isEqualTo(MovementType.OUT);
    assertThat(movement.getQuantityDelta()).isEqualTo(-2);
    assertThat(movement.getCreatedAt()).isNotNull();
    assertThat(entities.find(StockMovement.class, id(805)).getOrder()).isNull();
    assertThat(entities.find(StockMovement.class, id(805)).getNote()).isNotBlank();
  }

  @Test
  void persistsNewEntitiesWithUuidDefaultsAndProductSnapshots() {
    var user = new EmployeeUser("MAPPING@EXAMPLE.TEST", "!TEST_ONLY!", EmployeeRole.ADMIN);
    var customer = new Customer("Mapping customer", "customer@example.test", "123456789");
    var product = new Product("MAPPING", "Original name", new BigDecimal("19.99"));
    var warehouse = new Warehouse("MAPPING", "Mapping warehouse");
    for (var entity : new Object[] {user, customer, product, warehouse}) {
      entities.persist(entity);
    }
    var stock = new StockLevel(product, warehouse);
    var order = new SalesOrder("MAPPING-001", customer, warehouse, user);
    entities.persist(stock);
    entities.persist(order);
    var item = new OrderItem(order, product, 2);
    entities.persist(item);
    var movement = new StockMovement(stock, user, null, MovementType.IN, 10, "Mapping test");
    entities.persist(movement);
    entities.flush();
    // Exercise mappings directly; the receipt service is implemented in M4.
    entities
        .createNativeQuery("UPDATE inventory.stock_levels SET on_hand=10 WHERE id=:id")
        .setParameter("id", stock.getId())
        .executeUpdate();
    entities
        .createNativeQuery(
            "UPDATE inventory.products SET unit_price=25, name='New name', sku='NEW-MAPPING' WHERE id=:id")
        .setParameter("id", product.getId())
        .executeUpdate();
    entities.clear();
    assertThat(user.getId()).isNotNull();
    assertThat(entities.find(EmployeeUser.class, user.getId()).getEmail())
        .isEqualTo("mapping@example.test");
    assertThat(entities.find(Customer.class, customer.getId()).getPhone()).isEqualTo("123456789");
    assertThat(entities.find(Warehouse.class, warehouse.getId()).getName())
        .isEqualTo("Mapping warehouse");
    assertThat(entities.find(StockLevel.class, stock.getId()).getAvailable()).isEqualTo(10);
    var stored = entities.find(SalesOrder.class, order.getId());
    assertThat(stored.getStatus()).isEqualTo(OrderStatus.DRAFT);
    assertThat(stored.getCreatedAt()).isNotNull();
    assertThat(stored.getConfirmedAt()).isNull();
    var snapshot = entities.find(OrderItem.class, item.getId());
    assertThat(snapshot.getUnitPrice()).isEqualByComparingTo("19.99");
    assertThat(snapshot.getProductName()).isEqualTo("Original name");
    assertThat(snapshot.getProductSku()).isEqualTo("MAPPING");
    assertThat(entities.find(StockMovement.class, movement.getId()).getQuantityDelta())
        .isEqualTo(10);
  }

  @Test
  @Sql("/sql/demo-data.sql")
  void keepsAssociationsLazyAndRequiresLoadingBeforeDetachment() {
    SalesOrder order = entities.find(SalesOrder.class, id(602));
    assertThat(Hibernate.isInitialized(order.getCustomer())).isFalse();
    entities.clear();
    assertThatThrownBy(() -> order.getCustomer().getName())
        .isInstanceOf(LazyInitializationException.class);
  }
}
