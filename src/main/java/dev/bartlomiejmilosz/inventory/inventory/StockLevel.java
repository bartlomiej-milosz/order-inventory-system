package dev.bartlomiejmilosz.inventory.inventory;

import dev.bartlomiejmilosz.inventory.catalog.Product;
import dev.bartlomiejmilosz.inventory.shared.IdentifiedEntity;
import dev.bartlomiejmilosz.inventory.shared.ResourceConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "stock_levels", schema = "inventory")
public class StockLevel extends IdentifiedEntity {
  @Column(name = "on_hand", nullable = false)
  private int onHand;

  @Column(name = "reserved", nullable = false)
  private int reserved;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "warehouse_id", nullable = false)
  private Warehouse warehouse;

  protected StockLevel() {}

  public StockLevel(Product product, Warehouse warehouse) {
    this.product = product;
    this.warehouse = warehouse;
    this.updatedAt = Instant.now();
  }

  void applyDelta(int delta) {
    long nextOnHand = (long) onHand + delta;
    if (nextOnHand < reserved) {
      throw new ResourceConflictException("Physical stock cannot fall below reserved stock.");
    }
    if (nextOnHand > Integer.MAX_VALUE) {
      throw new ResourceConflictException(
          "The resulting stock quantity exceeds the supported range.");
    }
    onHand = (int) nextOnHand;
    updatedAt = Instant.now();
  }

  void reserve(int quantity, Instant timestamp) {
    if (quantity <= 0 || quantity > getAvailable()) {
      throw new ResourceConflictException("There is not enough available stock for this order.");
    }
    reserved += quantity;
    updatedAt = timestamp;
  }

  void release(int quantity, Instant timestamp) {
    if (quantity <= 0 || quantity > reserved) {
      throw new ResourceConflictException("There is not enough reserved stock for this order.");
    }
    reserved -= quantity;
    updatedAt = timestamp;
  }

  void issueReserved(int quantity, Instant timestamp) {
    release(quantity, timestamp);
    onHand -= quantity;
  }

  public int getAvailable() {
    return onHand - reserved;
  }

  public int getOnHand() {
    return onHand;
  }

  public int getReserved() {
    return reserved;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Product getProduct() {
    return product;
  }

  public Warehouse getWarehouse() {
    return warehouse;
  }
}
