package dev.bartlomiejmilosz.inventory.catalog;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

final class CustomerDto {
  private CustomerDto() {}

  record CustomerInput(
      @NotBlank @Size(max = 200) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @Size(max = 30) String phone) {}

  record CustomerView(UUID id, String name, String email, String phone, Instant createdAt) {
    static CustomerView from(Customer value) {
      return new CustomerView(
          value.getId(), value.getName(), value.getEmail(), value.getPhone(), value.getCreatedAt());
    }
  }
}
