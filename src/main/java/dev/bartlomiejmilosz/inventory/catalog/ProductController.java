package dev.bartlomiejmilosz.inventory.catalog;

import dev.bartlomiejmilosz.inventory.catalog.ProductDto.ProductInput;
import dev.bartlomiejmilosz.inventory.catalog.ProductDto.ProductView;
import dev.bartlomiejmilosz.inventory.shared.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping(ProductController.PATH)
class ProductController {
  static final String PATH = "/api/v1/products";
  private final ProductService service;

  ProductController(ProductService service) {
    this.service = service;
  }

  @GetMapping
  PageResponse<ProductView> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(page, size);
  }

  @GetMapping("/{id}")
  ProductView get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<ProductView> create(@Valid @RequestBody ProductInput input) {
    ProductView created = service.create(input);
    return ResponseEntity.created(URI.create(PATH + "/" + created.id())).body(created);
  }

  @PutMapping("/{id}")
  ProductView update(@PathVariable UUID id, @Valid @RequestBody ProductInput input) {
    return service.update(id, input);
  }

  @PostMapping("/{id}/deactivate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  ResponseEntity<Void> deactivate(@PathVariable UUID id) {
    service.deactivate(id);
    return ResponseEntity.noContent().build();
  }
}
