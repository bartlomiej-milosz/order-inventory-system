package dev.bartlomiejmilosz.inventory.orders;

import dev.bartlomiejmilosz.inventory.catalog.Customer;
import dev.bartlomiejmilosz.inventory.inventory.Warehouse;
import dev.bartlomiejmilosz.inventory.shared.IdentifiedEntity;
import dev.bartlomiejmilosz.inventory.users.EmployeeUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "sales_orders", schema = "inventory")
public class SalesOrder extends IdentifiedEntity {
  @Column(name = "order_number", nullable = false, length = 30)
  private String orderNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OrderStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "confirmed_at", nullable = true)
  private Instant confirmedAt;

  @Column(name = "note", columnDefinition = "text", nullable = true)
  private String note;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "warehouse_id", nullable = false)
  private Warehouse warehouse;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by", nullable = false)
  private EmployeeUser createdBy;

  protected SalesOrder() {}

  public SalesOrder(
      String orderNumber, Customer customer, Warehouse warehouse, EmployeeUser createdBy) {
    this.orderNumber = orderNumber;
    this.customer = customer;
    this.warehouse = warehouse;
    this.createdBy = createdBy;
    this.status = OrderStatus.DRAFT;
    this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  void updateHeader(Customer customer, Warehouse warehouse, String note) {
    this.customer = customer;
    this.warehouse = warehouse;
    this.note = note;
  }

  void confirm(Instant timestamp) {
    status = OrderStatus.CONFIRMED;
    confirmedAt = timestamp;
  }

  public String getOrderNumber() {
    return orderNumber;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }

  public String getNote() {
    return note;
  }

  public Customer getCustomer() {
    return customer;
  }

  public Warehouse getWarehouse() {
    return warehouse;
  }

  public EmployeeUser getCreatedBy() {
    return createdBy;
  }
}
