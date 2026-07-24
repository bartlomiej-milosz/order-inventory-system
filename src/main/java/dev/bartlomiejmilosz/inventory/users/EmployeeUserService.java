package dev.bartlomiejmilosz.inventory.users;

import dev.bartlomiejmilosz.inventory.shared.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeUserService {
  private final EmployeeUserRepository users;

  public EmployeeUserService(EmployeeUserRepository users) {
    this.users = users;
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public EmployeeView findById(UUID id) {
    EmployeeUser user = users.findById(id).orElseThrow(ResourceNotFoundException::new);
    return new EmployeeView(
        user.getId(), user.getEmail(), user.getRole(), user.isActive(), user.getCreatedAt());
  }

  public record EmployeeView(
      UUID id, String email, EmployeeRole role, boolean active, Instant createdAt) {}
}
