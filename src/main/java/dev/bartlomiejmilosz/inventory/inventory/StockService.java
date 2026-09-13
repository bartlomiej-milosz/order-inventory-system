package dev.bartlomiejmilosz.inventory.inventory;

import dev.bartlomiejmilosz.inventory.catalog.ProductRepository;
import dev.bartlomiejmilosz.inventory.shared.InvalidRequestException;
import dev.bartlomiejmilosz.inventory.shared.PageResponse;
import dev.bartlomiejmilosz.inventory.shared.ResourceConflictException;
import dev.bartlomiejmilosz.inventory.shared.ResourceNotFoundException;
import dev.bartlomiejmilosz.inventory.users.EmployeeUser;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class StockService {
  private final WarehouseRepository warehouses;
  private final ProductRepository products;
  private final StockLevelRepository levels;
  private final StockMovementRepository movements;
  private final EntityManager entities;

  StockService(
      WarehouseRepository warehouses,
      ProductRepository products,
      StockLevelRepository levels,
      StockMovementRepository movements,
      EntityManager entities) {
    this.warehouses = warehouses;
    this.products = products;
    this.levels = levels;
    this.movements = movements;
    this.entities = entities;
  }

  PageResponse<StockDto.Level> list(UUID warehouseId, int page, int size) {
    warehouses.findById(warehouseId).orElseThrow(ResourceNotFoundException::new);
    return PageResponse.from(
        levels
            .findByWarehouseId(warehouseId, PageRequest.of(page, size, Sort.by("product.id")))
            .map(StockDto.Level::from));
  }

  StockDto.Level get(UUID warehouseId, UUID productId) {
    requireCatalogPair(warehouseId, productId);
    return levels
        .findByWarehouseIdAndProductId(warehouseId, productId)
        .map(StockDto.Level::from)
        .orElseGet(() -> StockDto.Level.empty(warehouseId, productId));
  }

  PageResponse<StockDto.Movement> history(UUID warehouseId, UUID productId, int page, int size) {
    requireCatalogPair(warehouseId, productId);
    var paging = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    return PageResponse.from(
        levels
            .findByWarehouseIdAndProductId(warehouseId, productId)
            .map(level ->
                movements.findByStockLevelId(level.getId(), paging).map(StockDto.Movement::from))
            .orElseGet(() -> Page.empty(paging)));
  }

  StockDto.Movement getMovement(UUID warehouseId, UUID productId, UUID movementId) {
    return levels
        .findByWarehouseIdAndProductId(warehouseId, productId)
        .flatMap(level -> movements.findByStockLevelIdAndId(level.getId(), movementId))
        .map(StockDto.Movement::from)
        .orElseThrow(ResourceNotFoundException::new);
  }

  @Transactional
  StockDto.Movement receive(
      UUID warehouseId, UUID productId, UUID employeeId, StockDto.Receipt input) {
    requireActivePair(warehouseId, productId);
    // The unique pair identifies the same row for both concurrent first receipts.
    levels.insertIfAbsent(UUID.randomUUID(), warehouseId, productId);
    StockLevel level = levels.findForUpdate(warehouseId, productId).orElseThrow();
    String note = input.note() == null || input.note().isBlank() ? null : input.note().strip();
    return recordMovement(level, employeeId, MovementType.IN, input.quantity(), note);
  }

  @Transactional
  StockDto.Movement adjust(
      UUID warehouseId, UUID productId, UUID employeeId, StockDto.Adjustment input) {
    if (input.quantityDelta() == 0) {
      throw new InvalidRequestException("An adjustment must change the quantity.");
    }
    requireActivePair(warehouseId, productId);
    StockLevel level =
        levels
            .findForUpdate(warehouseId, productId)
            .orElseThrow(
                () -> new ResourceConflictException("Record a receipt before adjusting stock."));
    return recordMovement(
        level, employeeId, MovementType.ADJUSTMENT, input.quantityDelta(), input.note().strip());
  }

  private StockDto.Movement recordMovement(
      StockLevel level, UUID employeeId, MovementType type, int delta, String note) {
    level.applyDelta(delta);
    EmployeeUser actor = entities.getReference(EmployeeUser.class, employeeId);
    return StockDto.Movement.from(
        movements.save(new StockMovement(level, actor, null, type, delta, note)));
  }

  private void requireCatalogPair(UUID warehouseId, UUID productId) {
    warehouses.findById(warehouseId).orElseThrow(ResourceNotFoundException::new);
    products.findById(productId).orElseThrow(ResourceNotFoundException::new);
  }

  private void requireActivePair(UUID warehouseId, UUID productId) {
    // Always acquire catalog locks in this order. A shared lock lets independent receipts
    // run concurrently but keeps a product or warehouse from being deactivated mid-operation.
    var product =
        products.findWithSharedLock(productId).orElseThrow(ResourceNotFoundException::new);
    var warehouse =
        warehouses.findWithSharedLock(warehouseId).orElseThrow(ResourceNotFoundException::new);
    if (!product.isActive() || !warehouse.isActive()) {
      throw new ResourceConflictException("The product and warehouse must both be active.");
    }
  }
}
