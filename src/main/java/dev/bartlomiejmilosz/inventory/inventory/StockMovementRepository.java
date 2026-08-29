package dev.bartlomiejmilosz.inventory.inventory;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

interface StockMovementRepository extends Repository<StockMovement, UUID> {
  StockMovement save(StockMovement movement);

  Page<StockMovement> findByStockLevelId(UUID stockLevelId, Pageable page);

  Optional<StockMovement> findByStockLevelIdAndId(UUID stockLevelId, UUID id);
}
