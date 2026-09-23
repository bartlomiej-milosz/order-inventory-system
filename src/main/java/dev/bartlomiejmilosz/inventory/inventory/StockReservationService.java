package dev.bartlomiejmilosz.inventory.inventory;

import dev.bartlomiejmilosz.inventory.shared.ResourceConflictException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReservationService {
  private final StockLevelRepository stockLevels;

  StockReservationService(StockLevelRepository stockLevels) {
    this.stockLevels = stockLevels;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Instant reserve(UUID warehouseId, Map<UUID, Integer> quantitiesByProduct) {
    var locked = stockLevels.findAllForUpdateInIdOrder(warehouseId, quantitiesByProduct.keySet());
    if (locked.size() != quantitiesByProduct.size()) {
      throw new ResourceConflictException(
          "At least one product has no stock in the order warehouse.");
    }

    Instant confirmedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    for (StockLevel level : locked) {
      int quantity = quantitiesByProduct.get(level.getProduct().getId());
      level.reserve(quantity, confirmedAt);
    }
    return confirmedAt;
  }
}
