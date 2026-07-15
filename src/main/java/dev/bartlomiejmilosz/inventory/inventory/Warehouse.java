package dev.bartlomiejmilosz.inventory.inventory;

import dev.bartlomiejmilosz.inventory.shared.IdentifiedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "warehouses", schema = "inventory")
public class Warehouse extends IdentifiedEntity {
  @Column(name = "code", nullable = false, length = 30)
  private String code;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "active", nullable = false)
  private boolean active;

  protected Warehouse() {}

  public Warehouse(String code, String name) {
    this.code = code;
    this.name = name;
    this.active = true;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public boolean isActive() {
    return active;
  }
}
