package dev.bartlomiejmilosz.inventory.catalog;

import dev.bartlomiejmilosz.inventory.shared.IdentifiedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products", schema = "inventory")
public class Product extends IdentifiedEntity {
  @Column(name = "sku", nullable = false, length = 50)
  private String sku;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Product() {}

  public Product(String sku, String name, BigDecimal unitPrice) {
    update(sku, name, unitPrice);
    this.active = true;
    this.createdAt = Instant.now();
  }

  void update(String sku, String name, BigDecimal unitPrice) {
    this.sku = sku;
    this.name = name.strip();
    this.unitPrice = unitPrice;
  }

  void deactivate() {
    this.active = false;
  }

  public String getSku() {
    return sku;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
