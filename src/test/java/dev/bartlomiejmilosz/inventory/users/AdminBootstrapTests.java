package dev.bartlomiejmilosz.inventory.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.bartlomiejmilosz.inventory.config.TestcontainersConfiguration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminBootstrapTests {
  private static final String PASSWORD = "Bootstrap-test-password-42!";
  @Autowired AdminBootstrapService bootstrap;
  @Autowired EmployeeUserRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    jdbc.update("DELETE FROM inventory.users WHERE email LIKE '%@bootstrap.test'");
  }

  @Test
  void createsOneActiveAdminWithANormalizedEmailAndAnAdaptiveHash() {
    var id = bootstrap.createFirstAdmin(" ADMIN@BOOTSTRAP.TEST ", PASSWORD);
    var stored = users.findById(id).orElseThrow();
    assertThat(stored.getEmail()).isEqualTo("admin@bootstrap.test");
    assertThat(stored.getRole()).isEqualTo(EmployeeRole.ADMIN);
    assertThat(stored.isActive()).isTrue();
    assertThat(stored.getPasswordHash()).startsWith("{bcrypt}$2a$12$").doesNotContain(PASSWORD);
    assertThat(passwords.matches(PASSWORD, stored.getPasswordHash())).isTrue();
    assertThatThrownBy(() -> bootstrap.createFirstAdmin("other@bootstrap.test", PASSWORD))
        .isInstanceOf(IllegalStateException.class);
    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void refusesToOverwriteAnExistingOperatorOrPromoteIt() {
    var operator =
        users.save(
            new EmployeeUser("operator@bootstrap.test", "!TEST_ONLY!", EmployeeRole.OPERATOR));
    assertThatThrownBy(() -> bootstrap.createFirstAdmin("operator@bootstrap.test", PASSWORD))
        .isInstanceOf(IllegalStateException.class);
    assertThat(users.findById(operator.getId()).orElseThrow().getRole())
        .isEqualTo(EmployeeRole.OPERATOR);
  }

  @Test
  void rejectsInvalidEmailsAndPasswordsBeforeWritingAnything() {
    for (String email : List.of(" ", "invalid-email", "a".repeat(255) + "@bootstrap.test")) {
      assertThatThrownBy(() -> bootstrap.createFirstAdmin(email, PASSWORD))
          .isInstanceOf(IllegalArgumentException.class);
    }
    for (String password : List.of("short", "a".repeat(73), "ą".repeat(37))) {
      assertThatThrownBy(() -> bootstrap.createFirstAdmin("admin@bootstrap.test", password))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(users.count()).isZero();
  }

  @Test
  void rejectsAnEmailContainingTheHttpBasicUsernameDelimiter() {
    assertThatThrownBy(() -> bootstrap.createFirstAdmin("\"ops:admin\"@bootstrap.test", PASSWORD))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(users.count()).isZero();
  }

  @Test
  void serializesConcurrentAttemptsToCreateTheFirstAdministrator() throws Exception {
    var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var attempts =
          List.of("one@bootstrap.test", "two@bootstrap.test").stream()
              .map(email ->
                  workers.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                      throw new AssertionError("Start signal missing");
                    }
                    try {
                      bootstrap.createFirstAdmin(email, PASSWORD);
                      return true;
                    } catch (IllegalStateException expected) {
                      return false;
                    }
                  }))
              .toList();
      start.countDown();
      int created = 0;
      for (var attempt : attempts) {
        if (attempt.get(15, TimeUnit.SECONDS)) {
          created++;
        }
      }
      assertThat(created).isEqualTo(1);
      assertThat(users.count()).isEqualTo(1);
    }
  }
}
