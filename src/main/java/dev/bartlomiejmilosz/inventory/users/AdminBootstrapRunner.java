package dev.bartlomiejmilosz.inventory.users;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap-admin")
class AdminBootstrapRunner implements ApplicationRunner {
  private final AdminBootstrapService bootstrap;
  private final String email;
  private final String passwordFile;

  AdminBootstrapRunner(
      AdminBootstrapService bootstrap,
      @Value("${inventory.bootstrap.email}") String email,
      @Value("${inventory.bootstrap.password-file}") String passwordFile) {
    this.bootstrap = bootstrap;
    this.email = email;
    this.passwordFile = passwordFile;
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    if (passwordFile.isBlank()) {
      throw new IllegalArgumentException("A bootstrap password file is required.");
    }
    Path path = Path.of(passwordFile);
    if (!Files.isRegularFile(path) || Files.size(path) > 256) {
      throw new IllegalArgumentException(
          "The bootstrap password file must be a small UTF-8 text file.");
    }
    String password = Files.readString(path, StandardCharsets.UTF_8);
    if (password.endsWith("\n")) {
      password = password.substring(0, password.length() - 1);
    }
    if (password.endsWith("\r")) {
      password = password.substring(0, password.length() - 1);
    }
    if (password.indexOf('\n') >= 0 || password.indexOf('\r') >= 0) {
      throw new IllegalArgumentException("The bootstrap password file must contain a single line.");
    }
    bootstrap.createFirstAdmin(email, password);
    System.out.println("Initial administrator created. Remove the bootstrap password file.");
  }
}
