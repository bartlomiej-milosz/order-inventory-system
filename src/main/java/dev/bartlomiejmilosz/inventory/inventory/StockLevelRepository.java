package dev.bartlomiejmilosz.inventory.inventory;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface StockLevelRepository extends Repository<StockLevel, UUID> {
  Optional<StockLevel> findByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

  Page<StockLevel> findByWarehouseId(UUID warehouseId, Pageable page);

  @Modifying
  @Query(
      value =
          """
          INSERT INTO inventory.stock_levels (id, warehouse_id, product_id, on_hand, reserved, updated_at)
          VALUES (:id, :warehouseId, :productId, 0, 0, now())
          ON CONFLICT (product_id, warehouse_id) DO NOTHING
          """,
      nativeQuery = true)
  void insertIfAbsent(UUID id, UUID warehouseId, UUID productId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from StockLevel s where s.warehouse.id = :warehouseId and s.product.id = :productId")
  Optional<StockLevel> findForUpdate(UUID warehouseId, UUID productId);
}
