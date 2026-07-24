package dev.bartlomiejmilosz.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderInventorySystemApplication {

  public static void main(String[] args) {
    var context = SpringApplication.run(OrderInventorySystemApplication.class, args);
    if (context.getEnvironment().matchesProfiles("bootstrap-admin")) {
      context.close();
    }
  }
}
