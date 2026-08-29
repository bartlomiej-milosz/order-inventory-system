package dev.bartlomiejmilosz.inventory.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

final class StockDto {
  private StockDto() {}

  record Receipt(@NotNull @Positive Integer quantity, @Size(max = 500) String note) {}

  record Adjustment(@NotNull Integer quantityDelta, @NotBlank @Size(max = 500) String note) {}

  record Level(
      UUID warehouseId,
      UUID productId,
      int onHand,
      int reserved,
      int available,
      Instant updatedAt) {
    static Level from(StockLevel level) {
      return new Level(
          level.getWarehouse().getId(),
          level.getProduct().getId(),
          level.getOnHand(),
          level.getReserved(),
          level.getAvailable(),
          level.getUpdatedAt());
    }

    static Level empty(UUID warehouseId, UUID productId) {
      return new Level(warehouseId, productId, 0, 0, 0, null);
    }
  }

  record Movement(
      UUID id,
      MovementType type,
      int quantityDelta,
      String note,
      UUID performedBy,
      UUID orderId,
      Instant createdAt) {
    static Movement from(StockMovement movement) {
      var order = movement.getOrder();
      return new Movement(
          movement.getId(),
          movement.getType(),
          movement.getQuantityDelta(),
          movement.getNote(),
          movement.getPerformedBy().getId(),
          order == null ? null : order.getId(),
          movement.getCreatedAt());
    }
  }
}
