package dev.bartlomiejmilosz.inventory.catalog;

import dev.bartlomiejmilosz.inventory.shared.PageResponse;
import dev.bartlomiejmilosz.inventory.shared.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ProductService {
  private final ProductRepository repository;

  ProductService(ProductRepository repository) {
    this.repository = repository;
  }

  PageResponse<ProductDto.ProductView> list(int page, int size) {
    return PageResponse.from(
        repository
            .findAll(PageRequest.of(page, size, Sort.by("name", "id")))
            .map(ProductDto.ProductView::from));
  }

  ProductDto.ProductView get(UUID id) {
    return ProductDto.ProductView.from(
        repository.findById(id).orElseThrow(ResourceNotFoundException::new));
  }

  @Transactional
  ProductDto.ProductView create(ProductDto.ProductInput input) {
    return ProductDto.ProductView.from(
        repository.save(new Product(input.sku(), input.name(), input.unitPrice())));
  }

  @Transactional
  ProductDto.ProductView update(UUID id, ProductDto.ProductInput input) {
    Product value = repository.findForUpdate(id).orElseThrow(ResourceNotFoundException::new);
    value.update(input.sku(), input.name(), input.unitPrice());
    return ProductDto.ProductView.from(value);
  }

  @Transactional
  void deactivate(UUID id) {
    repository.findForUpdate(id).orElseThrow(ResourceNotFoundException::new).deactivate();
  }
}
