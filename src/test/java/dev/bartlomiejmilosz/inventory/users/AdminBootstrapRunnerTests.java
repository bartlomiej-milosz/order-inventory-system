package dev.bartlomiejmilosz.inventory.users;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

class AdminBootstrapRunnerTests {
  @TempDir Path directory;

  @Test
  void readsThePasswordFileWithAnOptionalTerminalLineEnding() throws Exception {
    var bootstrap = mock(AdminBootstrapService.class);
    Path file = directory.resolve("password");
    Files.writeString(file, "Temporary-test-password!\r\n");
    new AdminBootstrapRunner(bootstrap, "admin@example.test", file.toString())
        .run(new DefaultApplicationArguments());
    verify(bootstrap).createFirstAdmin("admin@example.test", "Temporary-test-password!");
  }

  @Test
  void refusesMissingOversizedAndMultilineFiles() throws Exception {
    var bootstrap = mock(AdminBootstrapService.class);
    assertThatThrownBy(() ->
            new AdminBootstrapRunner(bootstrap, "admin@example.test", "")
                .run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalArgumentException.class);
    Path file = directory.resolve("password");
    Files.writeString(file, "x".repeat(257));
    assertThatThrownBy(() ->
            new AdminBootstrapRunner(bootstrap, "admin@example.test", file.toString())
                .run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalArgumentException.class);
    Files.writeString(file, "line one\nline two");
    assertThatThrownBy(() ->
            new AdminBootstrapRunner(bootstrap, "admin@example.test", file.toString())
                .run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(bootstrap);
  }
}
