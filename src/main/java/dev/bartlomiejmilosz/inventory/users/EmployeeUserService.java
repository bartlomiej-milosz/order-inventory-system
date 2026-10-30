package dev.bartlomiejmilosz.inventory.users;

import dev.bartlomiejmilosz.inventory.shared.InvalidRequestException;
import dev.bartlomiejmilosz.inventory.shared.ResourceConflictException;
import dev.bartlomiejmilosz.inventory.shared.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeUserService {
  private final EmployeeUserRepository users;
  private final PasswordEncoder passwords;
  private final JdbcTemplate jdbc;

  public EmployeeUserService(
      EmployeeUserRepository users, PasswordEncoder passwords, JdbcTemplate jdbc) {
    this.users = users;
    this.passwords = passwords;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public EmployeeView findById(UUID id) {
    return EmployeeView.from(find(id));
  }

  @Transactional
  public EmployeeView create(EmployeeAccountInput input) {
    if (!EmployeeAccountInput.validPassword(input.password())) {
      throw new InvalidRequestException(
          "Use at least 14 characters and at most 72 UTF-8 bytes, without control characters.");
    }
    var user = new EmployeeUser(input.email(), passwords.encode(input.password()), input.role());
    return EmployeeView.from(users.save(user));
  }

  @Transactional
  public EmployeeView changeRole(UUID id, EmployeeRole role) {
    var user = lockForAccountChange(id);
    if (role == EmployeeRole.OPERATOR) {
      requireAnotherAdminIfNeeded(user);
    }
    user.changeRole(role);
    return EmployeeView.from(user);
  }

  @Transactional
  public void deactivate(UUID id) {
    var user = lockForAccountChange(id);
    requireAnotherAdminIfNeeded(user);
    user.deactivate();
  }

  private EmployeeUser lockForAccountChange(UUID id) {
    // Serialize rare account writes before reading the target and counting administrators.
    // Plain reads, including HTTP Basic authentication, can still proceed.
    jdbc.execute("LOCK TABLE inventory.users IN SHARE ROW EXCLUSIVE MODE");
    return find(id);
  }

  private EmployeeUser find(UUID id) {
    return users.findById(id).orElseThrow(ResourceNotFoundException::new);
  }

  private void requireAnotherAdminIfNeeded(EmployeeUser user) {
    if (user.isActive()
        && user.getRole() == EmployeeRole.ADMIN
        && users.countByRoleAndActiveTrue(EmployeeRole.ADMIN) == 1) {
      throw new ResourceConflictException("At least one active administrator is required.");
    }
  }

  public record EmployeeView(
      UUID id, String email, EmployeeRole role, boolean active, Instant createdAt) {
    static EmployeeView from(EmployeeUser user) {
      return new EmployeeView(
          user.getId(), user.getEmail(), user.getRole(), user.isActive(), user.getCreatedAt());
    }
  }
}
