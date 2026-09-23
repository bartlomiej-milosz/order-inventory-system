package dev.bartlomiejmilosz.inventory.orders;

import dev.bartlomiejmilosz.inventory.catalog.Customer;
import dev.bartlomiejmilosz.inventory.catalog.CustomerRepository;
import dev.bartlomiejmilosz.inventory.catalog.Product;
import dev.bartlomiejmilosz.inventory.catalog.ProductRepository;
import dev.bartlomiejmilosz.inventory.inventory.StockReservationService;
import dev.bartlomiejmilosz.inventory.inventory.Warehouse;
import dev.bartlomiejmilosz.inventory.inventory.WarehouseRepository;
import dev.bartlomiejmilosz.inventory.orders.OrderDto.AddDraftOrderItemRequest;
import dev.bartlomiejmilosz.inventory.orders.OrderDto.CreateDraftOrderRequest;
import dev.bartlomiejmilosz.inventory.orders.OrderDto.OrderDetail;
import dev.bartlomiejmilosz.inventory.orders.OrderDto.OrderItemView;
import dev.bartlomiejmilosz.inventory.orders.OrderDto.OrderSummary;
import dev.bartlomiejmilosz.inventory.orders.OrderDto.UpdateDraftOrderItemRequest;
import dev.bartlomiejmilosz.inventory.orders.OrderDto.UpdateDraftOrderRequest;
import dev.bartlomiejmilosz.inventory.shared.PageResponse;
import dev.bartlomiejmilosz.inventory.shared.ResourceConflictException;
import dev.bartlomiejmilosz.inventory.shared.ResourceNotFoundException;
import dev.bartlomiejmilosz.inventory.users.EmployeeUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class OrderService {
  private static final BigDecimal ZERO_TOTAL = BigDecimal.ZERO.setScale(2);
  private final SalesOrderRepository orders;
  private final OrderItemRepository items;
  private final CustomerRepository customers;
  private final ProductRepository products;
  private final WarehouseRepository warehouses;
  private final StockReservationService reservations;
  private final EntityManager entities;

  OrderService(
      SalesOrderRepository orders,
      OrderItemRepository items,
      CustomerRepository customers,
      ProductRepository products,
      WarehouseRepository warehouses,
      StockReservationService reservations,
      EntityManager entities) {
    this.orders = orders;
    this.items = items;
    this.customers = customers;
    this.products = products;
    this.warehouses = warehouses;
    this.reservations = reservations;
    this.entities = entities;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  PageResponse<OrderSummary> list(
      OrderStatus status, UUID customerId, UUID warehouseId, int page, int size) {
    Specification<SalesOrder> filters =
        (order, query, criteria) -> {
          var matching = new ArrayList<Predicate>();
          if (status != null) {
            matching.add(criteria.equal(order.get("status"), status));
          }
          if (customerId != null) {
            matching.add(criteria.equal(order.get("customer").get("id"), customerId));
          }
          if (warehouseId != null) {
            matching.add(criteria.equal(order.get("warehouse").get("id"), warehouseId));
          }
          return criteria.and(matching.toArray(Predicate[]::new));
        };
    var result =
        orders.findAll(
            filters, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    if (result.isEmpty()) {
      return PageResponse.from(result.map(order -> OrderSummary.from(order, ZERO_TOTAL)));
    }
    var ids = result.map(SalesOrder::getId).getContent();
    Map<UUID, BigDecimal> totals =
        items.totalsForOrders(ids).stream()
            .collect(
                Collectors.toMap(
                    OrderItemRepository.OrderTotal::getOrderId,
                    OrderItemRepository.OrderTotal::getTotal));
    return PageResponse.from(
        result.map(
            order -> OrderSummary.from(order, totals.getOrDefault(order.getId(), ZERO_TOTAL))));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  OrderDetail get(UUID orderId) {
    SalesOrder order = orders.findById(orderId).orElseThrow(ResourceNotFoundException::new);
    return detail(order);
  }

  OrderItemView getItem(UUID orderId, UUID itemId) {
    return OrderItemView.from(findItem(orderId, itemId));
  }

  @Transactional
  OrderDetail create(UUID employeeId, CreateDraftOrderRequest request) {
    Customer customer = findCustomer(request.customerId());
    Warehouse warehouse = requireActiveWarehouse(request.warehouseId());
    EmployeeUser employee = entities.getReference(EmployeeUser.class, employeeId);
    var draft = new SalesOrder(request.orderNumber().strip(), customer, warehouse, employee);
    draft.updateHeader(customer, warehouse, normalizedNote(request.note()));
    return new OrderDetail(OrderSummary.from(orders.save(draft), ZERO_TOTAL), List.of());
  }

  @Transactional
  OrderDetail update(UUID orderId, UpdateDraftOrderRequest request) {
    SalesOrder order = lockDraft(orderId);
    order.updateHeader(
        findCustomer(request.customerId()),
        requireActiveWarehouse(request.warehouseId()),
        normalizedNote(request.note()));
    return detail(order);
  }

  @Transactional
  OrderItemView addItem(UUID orderId, AddDraftOrderItemRequest request) {
    SalesOrder draft = lockDraft(orderId);
    Product product = requireActiveProduct(request.productId());
    requireActiveWarehouse(draft.getWarehouse().getId());
    if (items.existsByOrderIdAndProductId(orderId, product.getId())) {
      throw new ResourceConflictException(
          "This product already has a line in the order. Edit its quantity instead.");
    }
    return OrderItemView.from(items.save(new OrderItem(draft, product, request.quantity())));
  }

  @Transactional
  OrderItemView updateItem(UUID orderId, UUID itemId, UpdateDraftOrderItemRequest request) {
    SalesOrder draft = lockDraft(orderId);
    OrderItem item = findItem(orderId, itemId);
    requireActiveProduct(item.getProduct().getId());
    requireActiveWarehouse(draft.getWarehouse().getId());
    item.changeQuantity(request.quantity());
    return OrderItemView.from(item);
  }

  @Transactional
  void removeItem(UUID orderId, UUID itemId) {
    lockDraft(orderId);
    items.delete(findItem(orderId, itemId));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  OrderDetail confirm(UUID orderId) {
    SalesOrder draft = lockDraft(orderId);
    List<OrderItem> lines = items.findByOrderIdOrderById(orderId);
    if (lines.isEmpty()) {
      throw new ResourceConflictException("Add at least one item before confirming the order.");
    }
    var quantities =
        lines.stream()
            .collect(Collectors.toMap(item -> item.getProduct().getId(), OrderItem::getQuantity));

    // Use the same product-before-warehouse lock order as draft edits and stock operations.
    if (products.findActiveWithSharedLock(quantities.keySet()).size() != quantities.size()) {
      throw new ResourceConflictException("All order products must be active.");
    }
    UUID warehouseId = requireActiveWarehouse(draft.getWarehouse().getId()).getId();
    draft.confirm(reservations.reserve(warehouseId, quantities));
    return detail(draft, lines);
  }

  private OrderDetail detail(SalesOrder order) {
    return detail(order, items.findByOrderIdOrderById(order.getId()));
  }

  private static OrderDetail detail(SalesOrder order, List<OrderItem> items) {
    var lines = items.stream().map(OrderItemView::from).toList();
    BigDecimal total =
        lines.stream().map(OrderItemView::lineTotal).reduce(ZERO_TOTAL, BigDecimal::add);
    return new OrderDetail(OrderSummary.from(order, total), lines);
  }

  private SalesOrder lockDraft(UUID id) {
    // Confirm and every draft edit take the same lock before reading the status.
    SalesOrder order = orders.findForUpdate(id).orElseThrow(ResourceNotFoundException::new);
    if (order.getStatus() != OrderStatus.DRAFT) {
      throw new ResourceConflictException("Only draft orders can be edited or confirmed.");
    }
    return order;
  }

  private OrderItem findItem(UUID orderId, UUID itemId) {
    return items.findByOrderIdAndId(orderId, itemId).orElseThrow(ResourceNotFoundException::new);
  }

  private Customer findCustomer(UUID id) {
    return customers.findById(id).orElseThrow(ResourceNotFoundException::new);
  }

  private Warehouse requireActiveWarehouse(UUID id) {
    Warehouse warehouse =
        warehouses.findWithSharedLock(id).orElseThrow(ResourceNotFoundException::new);
    if (!warehouse.isActive()) {
      throw new ResourceConflictException("The order warehouse must be active.");
    }
    return warehouse;
  }

  private Product requireActiveProduct(UUID id) {
    Product product = products.findWithSharedLock(id).orElseThrow(ResourceNotFoundException::new);
    if (!product.isActive()) {
      throw new ResourceConflictException("The order product must be active.");
    }
    return product;
  }

  private static String normalizedNote(String note) {
    return note == null || note.isBlank() ? null : note.strip();
  }
}
