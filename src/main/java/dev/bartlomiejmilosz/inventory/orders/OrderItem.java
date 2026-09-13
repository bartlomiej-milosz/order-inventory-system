package dev.bartlomiejmilosz.inventory.orders;

import dev.bartlomiejmilosz.inventory.catalog.Product;
import dev.bartlomiejmilosz.inventory.shared.IdentifiedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items", schema = "inventory")
public class OrderItem extends IdentifiedEntity {
  @Column(name = "product_name", nullable = false, length = 200)
  private String productName;

  @Column(name = "product_sku", nullable = false, length = 50)
  private String productSku;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private SalesOrder order;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  protected OrderItem() {}

  public OrderItem(SalesOrder order, Product product, int quantity) {
    this.order = order;
    this.product = product;
    this.quantity = quantity;
    this.productName = product.getName();
    this.productSku = product.getSku();
    this.unitPrice = product.getUnitPrice();
  }

  void changeQuantity(int quantity) {
    this.quantity = quantity;
  }

  public BigDecimal getTotal() {
    return unitPrice.multiply(BigDecimal.valueOf(quantity));
  }

  public String getProductName() {
    return productName;
  }

  public String getProductSku() {
    return productSku;
  }

  public int getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public SalesOrder getOrder() {
    return order;
  }

  public Product getProduct() {
    return product;
  }
}
