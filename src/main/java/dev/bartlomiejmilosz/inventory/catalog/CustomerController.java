package dev.bartlomiejmilosz.inventory.catalog;

import dev.bartlomiejmilosz.inventory.catalog.CustomerDto.CustomerInput;
import dev.bartlomiejmilosz.inventory.catalog.CustomerDto.CustomerView;
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
@RequestMapping(CustomerController.PATH)
class CustomerController {
  static final String PATH = "/api/v1/customers";
  private final CustomerService service;

  CustomerController(CustomerService service) {
    this.service = service;
  }

  @GetMapping
  PageResponse<CustomerView> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(page, size);
  }

  @GetMapping("/{id}")
  CustomerView get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<CustomerView> create(@Valid @RequestBody CustomerInput input) {
    CustomerView created = service.create(input);
    return ResponseEntity.created(URI.create(PATH + "/" + created.id())).body(created);
  }

  @PutMapping("/{id}")
  CustomerView update(@PathVariable UUID id, @Valid @RequestBody CustomerInput input) {
    return service.update(id, input);
  }
}
