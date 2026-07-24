package dev.bartlomiejmilosz.inventory.users;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBootstrapService {
  private final EmployeeUserRepository users;
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwords;
  private final Validator validator;

  public AdminBootstrapService(
      EmployeeUserRepository users,
      JdbcTemplate jdbc,
      PasswordEncoder passwords,
      Validator validator) {
    this.users = users;
    this.jdbc = jdbc;
    this.passwords = passwords;
    this.validator = validator;
  }

  @Transactional
  public UUID createFirstAdmin(String email, String password) {
    String normalized = EmailAddress.normalize(email);
    if (!validator.validate(new BootstrapEmail(normalized)).isEmpty()) {
      throw new IllegalArgumentException("A valid bootstrap email address is required.");
    }
    int bytes = password.getBytes(StandardCharsets.UTF_8).length;
    if (password.codePointCount(0, password.length()) < 14 || bytes > 72) {
      throw new IllegalArgumentException(
          "The bootstrap password must have at least 14 characters and at most 72 UTF-8 bytes.");
    }
    // Provisioning is rare: serialize the empty-table check with all account writes.
    jdbc.execute("LOCK TABLE inventory.users IN EXCLUSIVE MODE");
    if (users.count() != 0) {
      throw new IllegalStateException("Bootstrap requires an empty users table.");
    }
    return users
        .save(new EmployeeUser(normalized, passwords.encode(password), EmployeeRole.ADMIN))
        .getId();
  }

  private record BootstrapEmail(@NotBlank @Email(regexp = "[^:]+") @Size(max = 254) String email) {}
}
