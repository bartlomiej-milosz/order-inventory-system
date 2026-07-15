package dev.bartlomiejmilosz.inventory.inventory;

import dev.bartlomiejmilosz.inventory.orders.SalesOrder;
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

@Entity
@Table(name = "stock_movements", schema = "inventory")
public class StockMovement extends IdentifiedEntity {
  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  private MovementType type;

  @Column(name = "quantity_delta", nullable = false)
  private int quantityDelta;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "note", columnDefinition = "text", nullable = true)
  private String note;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "stock_level_id", nullable = false)
  private StockLevel stockLevel;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "performed_by", nullable = false)
  private EmployeeUser performedBy;

  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "order_id", nullable = true)
  private SalesOrder order;

  protected StockMovement() {}

  public StockMovement(
      StockLevel stockLevel,
      EmployeeUser performedBy,
      SalesOrder order,
      MovementType type,
      int quantityDelta,
      String note) {
    this.stockLevel = stockLevel;
    this.performedBy = performedBy;
    this.order = order;
    this.type = type;
    this.quantityDelta = quantityDelta;
    this.note = note;
    this.createdAt = Instant.now();
  }

  public MovementType getType() {
    return type;
  }

  public int getQuantityDelta() {
    return quantityDelta;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getNote() {
    return note;
  }

  public StockLevel getStockLevel() {
    return stockLevel;
  }

  public EmployeeUser getPerformedBy() {
    return performedBy;
  }

  public SalesOrder getOrder() {
    return order;
  }
}
