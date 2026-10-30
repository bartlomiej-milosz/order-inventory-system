package dev.bartlomiejmilosz.inventory.users;

import dev.bartlomiejmilosz.inventory.shared.IdentifiedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users", schema = "inventory")
public class EmployeeUser extends IdentifiedEntity {
  @Column(name = "email", nullable = false, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false, columnDefinition = "text")
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private EmployeeRole role;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected EmployeeUser() {}

  public EmployeeUser(String email, String passwordHash, EmployeeRole role) {
    this.email = EmailAddress.normalize(email);
    this.passwordHash = passwordHash;
    this.role = role;
    this.active = true;
    this.createdAt = Instant.now();
  }

  void changeRole(EmployeeRole role) {
    this.role = role;
  }

  void deactivate() {
    this.active = false;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public EmployeeRole getRole() {
    return role;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
