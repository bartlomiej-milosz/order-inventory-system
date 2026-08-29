package dev.bartlomiejmilosz.inventory.inventory;

import dev.bartlomiejmilosz.inventory.shared.PageResponse;
import dev.bartlomiejmilosz.inventory.users.EmployeePrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouses/{warehouseId}/stock")
class StockController {
  private final StockService stock;

  StockController(StockService stock) {
    this.stock = stock;
  }

  @GetMapping
  PageResponse<StockDto.Level> list(
      @PathVariable UUID warehouseId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return stock.list(warehouseId, page, size);
  }

  @GetMapping("/{productId}")
  StockDto.Level get(@PathVariable UUID warehouseId, @PathVariable UUID productId) {
    return stock.get(warehouseId, productId);
  }

  @GetMapping("/{productId}/movements")
  PageResponse<StockDto.Movement> history(
      @PathVariable UUID warehouseId,
      @PathVariable UUID productId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return stock.history(warehouseId, productId, page, size);
  }

  @GetMapping("/{productId}/movements/{movementId}")
  StockDto.Movement getMovement(
      @PathVariable UUID warehouseId, @PathVariable UUID productId, @PathVariable UUID movementId) {
    return stock.getMovement(warehouseId, productId, movementId);
  }

  @PostMapping("/{productId}/receipts")
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<StockDto.Movement> receive(
      @PathVariable UUID warehouseId,
      @PathVariable UUID productId,
      @AuthenticationPrincipal EmployeePrincipal employee,
      @Valid @RequestBody StockDto.Receipt input) {
    return created(
        warehouseId, productId, stock.receive(warehouseId, productId, employee.getId(), input));
  }

  @PostMapping("/{productId}/adjustments")
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<StockDto.Movement> adjust(
      @PathVariable UUID warehouseId,
      @PathVariable UUID productId,
      @AuthenticationPrincipal EmployeePrincipal employee,
      @Valid @RequestBody StockDto.Adjustment input) {
    return created(
        warehouseId, productId, stock.adjust(warehouseId, productId, employee.getId(), input));
  }

  private ResponseEntity<StockDto.Movement> created(
      UUID warehouseId, UUID productId, StockDto.Movement movement) {
    URI url =
        URI.create(
            "/api/v1/warehouses/"
                + warehouseId
                + "/stock/"
                + productId
                + "/movements/"
                + movement.id());
    return ResponseEntity.created(url).body(movement);
  }
}
