package dev.bartlomiejmilosz.inventory.inventory;

import dev.bartlomiejmilosz.inventory.shared.PageResponse;
import dev.bartlomiejmilosz.inventory.shared.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class WarehouseService {
  private final WarehouseRepository repository;

  WarehouseService(WarehouseRepository repository) {
    this.repository = repository;
  }

  PageResponse<WarehouseDto.WarehouseView> list(int page, int size) {
    return PageResponse.from(
        repository
            .findAll(PageRequest.of(page, size, Sort.by("name", "id")))
            .map(WarehouseDto.WarehouseView::from));
  }

  WarehouseDto.WarehouseView get(UUID id) {
    return WarehouseDto.WarehouseView.from(
        repository.findById(id).orElseThrow(ResourceNotFoundException::new));
  }

  @Transactional
  WarehouseDto.WarehouseView create(WarehouseDto.WarehouseInput input) {
    return WarehouseDto.WarehouseView.from(
        repository.save(new Warehouse(input.code(), input.name())));
  }

  @Transactional
  WarehouseDto.WarehouseView update(UUID id, WarehouseDto.WarehouseInput input) {
    Warehouse value = repository.findForUpdate(id).orElseThrow(ResourceNotFoundException::new);
    value.update(input.code(), input.name());
    return WarehouseDto.WarehouseView.from(value);
  }

  @Transactional
  void deactivate(UUID id) {
    repository.findForUpdate(id).orElseThrow(ResourceNotFoundException::new).deactivate();
  }
}
