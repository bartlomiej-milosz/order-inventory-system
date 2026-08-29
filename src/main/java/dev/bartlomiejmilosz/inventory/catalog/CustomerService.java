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
class CustomerService {
  private final CustomerRepository repository;

  CustomerService(CustomerRepository repository) {
    this.repository = repository;
  }

  PageResponse<CustomerDto.CustomerView> list(int page, int size) {
    return PageResponse.from(
        repository
            .findAll(PageRequest.of(page, size, Sort.by("name", "id")))
            .map(CustomerDto.CustomerView::from));
  }

  CustomerDto.CustomerView get(UUID id) {
    return CustomerDto.CustomerView.from(
        repository.findById(id).orElseThrow(ResourceNotFoundException::new));
  }

  @Transactional
  CustomerDto.CustomerView create(CustomerDto.CustomerInput input) {
    return CustomerDto.CustomerView.from(
        repository.save(new Customer(input.name(), input.email(), input.phone())));
  }

  @Transactional
  CustomerDto.CustomerView update(UUID id, CustomerDto.CustomerInput input) {
    Customer value = repository.findForUpdate(id).orElseThrow(ResourceNotFoundException::new);
    value.update(input.name(), input.email(), input.phone());
    return CustomerDto.CustomerView.from(value);
  }
}
