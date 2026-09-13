package dev.bartlomiejmilosz.inventory.orders;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface OrderItemRepository extends Repository<OrderItem, UUID> {
  List<OrderItem> findByOrderIdOrderById(UUID orderId);

  Optional<OrderItem> findByOrderIdAndId(UUID orderId, UUID id);

  boolean existsByOrderIdAndProductId(UUID orderId, UUID productId);

  OrderItem save(OrderItem item);

  void delete(OrderItem item);

  @Query(
      """
      select i.order.id as orderId, sum(i.quantity * i.unitPrice) as total
      from OrderItem i where i.order.id in :orderIds group by i.order.id
      """)
  List<OrderTotal> totalsForOrders(Collection<UUID> orderIds);

  interface OrderTotal {
    UUID getOrderId();

    BigDecimal getTotal();
  }
}
