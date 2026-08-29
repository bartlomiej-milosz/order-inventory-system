package dev.bartlomiejmilosz.inventory.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class ProductDto {
  private ProductDto() {}

  record ProductInput(
      @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]*") String sku,
      @NotBlank @Size(max = 200) String name,
      @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice) {}

  record ProductView(
      UUID id, String sku, String name, BigDecimal unitPrice, boolean active, Instant createdAt) {
    static ProductView from(Product value) {
      return new ProductView(
          value.getId(),
          value.getSku(),
          value.getName(),
          value.getUnitPrice(),
          value.isActive(),
          value.getCreatedAt());
    }
  }
}
