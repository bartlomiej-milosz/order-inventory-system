package dev.bartlomiejmilosz.inventory.catalog;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ProductRepository extends Repository<Product, UUID> {
  Optional<Product> findById(UUID id);

  Page<Product> findAll(Pageable page);

  Product save(Product value);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Product e where e.id = :id")
  Optional<Product> findForUpdate(UUID id);

  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("select e from Product e where e.id = :id")
  Optional<Product> findWithSharedLock(UUID id);
}
