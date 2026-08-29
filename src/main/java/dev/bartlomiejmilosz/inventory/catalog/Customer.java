package dev.bartlomiejmilosz.inventory.catalog;

import dev.bartlomiejmilosz.inventory.shared.IdentifiedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customers", schema = "inventory")
public class Customer extends IdentifiedEntity {
  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "email", nullable = false, length = 254)
  private String email;

  @Column(name = "phone", length = 30, nullable = true)
  private String phone;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Customer() {}

  public Customer(String name, String email, String phone) {
    update(name, email, phone);
    this.createdAt = Instant.now();
  }

  void update(String name, String email, String phone) {
    this.name = name.strip();
    this.email = email.strip();
    this.phone = phone == null || phone.isBlank() ? null : phone.strip();
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
