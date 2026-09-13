package dev.bartlomiejmilosz.inventory.orders;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class OrderDto {
  private static final String POSTGRES_TEXT = "[^\\x00]*";

  private OrderDto() {}

  record CreateDraftOrderRequest(
      @NotBlank @Size(max = 30) @Pattern(regexp = POSTGRES_TEXT) String orderNumber,
      @NotNull UUID customerId,
      @NotNull UUID warehouseId,
      @Size(max = 500) @Pattern(regexp = POSTGRES_TEXT) String note) {}

  record UpdateDraftOrderRequest(
      @NotNull UUID customerId,
      @NotNull UUID warehouseId,
      @Size(max = 500) @Pattern(regexp = POSTGRES_TEXT) String note) {}

  record AddDraftOrderItemRequest(@NotNull UUID productId, @NotNull @Positive Integer quantity) {}

  record UpdateDraftOrderItemRequest(@NotNull @Positive Integer quantity) {}

  record OrderSummary(
      UUID id,
      String orderNumber,
      UUID customerId,
      UUID warehouseId,
      UUID createdBy,
      OrderStatus status,
      Instant createdAt,
      Instant confirmedAt,
      String note,
      BigDecimal total) {
    static OrderSummary from(SalesOrder order, BigDecimal total) {
      return new OrderSummary(
          order.getId(),
          order.getOrderNumber(),
          order.getCustomer().getId(),
          order.getWarehouse().getId(),
          order.getCreatedBy().getId(),
          order.getStatus(),
          order.getCreatedAt(),
          order.getConfirmedAt(),
          order.getNote(),
          total);
    }
  }

  record OrderDetail(OrderSummary order, List<OrderItemView> items) {}

  record OrderItemView(
      UUID id,
      UUID productId,
      String productName,
      String productSku,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal lineTotal) {
    static OrderItemView from(OrderItem item) {
      return new OrderItemView(
          item.getId(),
          item.getProduct().getId(),
          item.getProductName(),
          item.getProductSku(),
          item.getQuantity(),
          item.getUnitPrice(),
          item.getTotal());
    }
  }
}
