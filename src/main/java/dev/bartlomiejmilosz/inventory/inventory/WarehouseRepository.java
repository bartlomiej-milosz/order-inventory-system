package dev.bartlomiejmilosz.inventory.inventory;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface WarehouseRepository extends Repository<Warehouse, UUID> {
  Optional<Warehouse> findById(UUID id);

  Page<Warehouse> findAll(Pageable page);

  Warehouse save(Warehouse value);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Warehouse e where e.id = :id")
  Optional<Warehouse> findForUpdate(UUID id);

  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("select e from Warehouse e where e.id = :id")
  Optional<Warehouse> findWithSharedLock(UUID id);
}
