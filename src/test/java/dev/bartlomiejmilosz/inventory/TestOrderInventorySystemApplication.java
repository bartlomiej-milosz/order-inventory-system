package dev.bartlomiejmilosz.inventory;

import dev.bartlomiejmilosz.inventory.config.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

public class TestOrderInventorySystemApplication {

  public static void main(String[] args) {
    SpringApplication.from(OrderInventorySystemApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
