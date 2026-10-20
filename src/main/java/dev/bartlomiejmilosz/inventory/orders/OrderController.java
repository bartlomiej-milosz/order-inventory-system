package dev.bartlomiejmilosz.inventory.orders;

import dev.bartlomiejmilosz.inventory.orders.OrderDto.AddDraftOrderItemRequest;
import dev.bartlomiejmilosz.inventory.orders.OrderDto.CreateDraftOrderRequest;
import dev.bartlomiejmilosz.inventory.orders.OrderDto.OrderDetail;
import dev.bartlomiejmilosz.inventory.orders.OrderDto.OrderItemView;
import dev.bartlomiejmilosz.inventory.orders.OrderDto.OrderSummary;
import dev.bartlomiejmilosz.inventory.orders.OrderDto.UpdateDraftOrderItemRequest;
import dev.bartlomiejmilosz.inventory.orders.OrderDto.UpdateDraftOrderRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(OrderController.PATH)
class OrderController {
  static final String PATH = "/api/v1/orders";
  private final OrderService orders;

  OrderController(OrderService orders) {
    this.orders = orders;
  }

  @GetMapping
  PageResponse<OrderSummary> list(
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) UUID warehouseId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return orders.list(status, customerId, warehouseId, page, size);
  }

  @GetMapping("/{orderId}")
  OrderDetail get(@PathVariable UUID orderId) {
    return orders.get(orderId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<OrderDetail> create(
      @AuthenticationPrincipal EmployeePrincipal employee,
      @Valid @RequestBody CreateDraftOrderRequest request) {
    OrderDetail draft = orders.create(employee.getId(), request);
    return ResponseEntity.created(URI.create(PATH + "/" + draft.order().id())).body(draft);
  }

  @PutMapping("/{orderId}")
  OrderDetail update(
      @PathVariable UUID orderId, @Valid @RequestBody UpdateDraftOrderRequest request) {
    return orders.update(orderId, request);
  }

  @PostMapping("/{orderId}/confirm")
  OrderDetail confirm(@PathVariable UUID orderId) {
    return orders.confirm(orderId);
  }

  @PostMapping("/{orderId}/fulfill")
  OrderDetail fulfill(
      @PathVariable UUID orderId, @AuthenticationPrincipal EmployeePrincipal employee) {
    return orders.fulfill(orderId, employee.getId());
  }

  @PostMapping("/{orderId}/cancel")
  OrderDetail cancel(@PathVariable UUID orderId) {
    return orders.cancel(orderId);
  }

  @GetMapping("/{orderId}/items/{itemId}")
  OrderItemView getItem(@PathVariable UUID orderId, @PathVariable UUID itemId) {
    return orders.getItem(orderId, itemId);
  }

  @PostMapping("/{orderId}/items")
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<OrderItemView> addItem(
      @PathVariable UUID orderId, @Valid @RequestBody AddDraftOrderItemRequest request) {
    OrderItemView item = orders.addItem(orderId, request);
    return ResponseEntity.created(URI.create(PATH + "/" + orderId + "/items/" + item.id()))
        .body(item);
  }

  @PutMapping("/{orderId}/items/{itemId}")
  OrderItemView updateItem(
      @PathVariable UUID orderId,
      @PathVariable UUID itemId,
      @Valid @RequestBody UpdateDraftOrderItemRequest request) {
    return orders.updateItem(orderId, itemId, request);
  }

  @DeleteMapping("/{orderId}/items/{itemId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  ResponseEntity<Void> removeItem(@PathVariable UUID orderId, @PathVariable UUID itemId) {
    orders.removeItem(orderId, itemId);
    return ResponseEntity.noContent().build();
  }
}
