package dev.bartlomiejmilosz.inventory.inventory;

import dev.bartlomiejmilosz.inventory.orders.SalesOrder;
import dev.bartlomiejmilosz.inventory.shared.ResourceConflictException;
import dev.bartlomiejmilosz.inventory.users.EmployeeUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReservationService {
  private final StockLevelRepository stockLevels;
  private final StockMovementRepository movements;

  StockReservationService(StockLevelRepository stockLevels, StockMovementRepository movements) {
    this.stockLevels = stockLevels;
    this.movements = movements;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Instant reserve(UUID warehouseId, Map<UUID, Integer> quantitiesByProduct) {
    var locked = lockRequiredStock(warehouseId, quantitiesByProduct);

    Instant confirmedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    for (StockLevel level : locked) {
      int quantity = quantitiesByProduct.get(level.getProduct().getId());
      level.reserve(quantity, confirmedAt);
    }
    return confirmedAt;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void issue(
      SalesOrder order, Map<UUID, Integer> quantitiesByProduct, EmployeeUser employee) {
    var locked = lockRequiredStock(order.getWarehouse().getId(), quantitiesByProduct);
    Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MICROS);
    for (StockLevel level : locked) {
      int quantity = quantitiesByProduct.get(level.getProduct().getId());
      level.issueReserved(quantity, timestamp);
      movements.save(new StockMovement(level, employee, order, MovementType.OUT, -quantity, null));
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void release(UUID warehouseId, Map<UUID, Integer> quantitiesByProduct) {
    var locked = lockRequiredStock(warehouseId, quantitiesByProduct);
    Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MICROS);
    for (StockLevel level : locked) {
      level.release(quantitiesByProduct.get(level.getProduct().getId()), timestamp);
    }
  }

  private List<StockLevel> lockRequiredStock(
      UUID warehouseId, Map<UUID, Integer> quantitiesByProduct) {
    if (quantitiesByProduct.isEmpty()) {
      throw new ResourceConflictException("The order has no items.");
    }
    var locked = stockLevels.findAllForUpdateInIdOrder(warehouseId, quantitiesByProduct.keySet());
    if (locked.size() != quantitiesByProduct.size()) {
      throw new ResourceConflictException(
          "At least one product has no stock in the order warehouse.");
    }
    return locked;
  }
}
