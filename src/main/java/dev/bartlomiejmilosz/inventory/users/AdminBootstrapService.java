package dev.bartlomiejmilosz.inventory.users;

import jakarta.validation.Validator;
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
    var input = new EmployeeAccountInput(email, password, EmployeeRole.ADMIN);
    if (!validator.validateProperty(input, "email").isEmpty()) {
      throw new IllegalArgumentException("A valid bootstrap email address is required.");
    }
    if (!EmployeeAccountInput.validPassword(password)) {
      throw new IllegalArgumentException(
          "The bootstrap password must have at least 14 characters and at most 72 UTF-8 bytes, without control characters.");
    }
    // Provisioning is rare: serialize the empty-table check with all account writes.
    jdbc.execute("LOCK TABLE inventory.users IN EXCLUSIVE MODE");
    if (users.count() != 0) {
      throw new IllegalStateException("Bootstrap requires an empty users table.");
    }
    return users
        .save(new EmployeeUser(input.email(), passwords.encode(password), EmployeeRole.ADMIN))
        .getId();
  }
}
