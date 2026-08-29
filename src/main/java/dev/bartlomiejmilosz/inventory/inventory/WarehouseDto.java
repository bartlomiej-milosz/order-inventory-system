package dev.bartlomiejmilosz.inventory.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

final class WarehouseDto {
  private WarehouseDto() {}

  record WarehouseInput(
      @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]*") String code,
      @NotBlank @Size(max = 100) String name) {}

  record WarehouseView(UUID id, String code, String name, boolean active) {
    static WarehouseView from(Warehouse value) {
      return new WarehouseView(value.getId(), value.getCode(), value.getName(), value.isActive());
    }
  }
}
