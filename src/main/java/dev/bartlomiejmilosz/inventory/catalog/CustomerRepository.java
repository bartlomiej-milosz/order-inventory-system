package dev.bartlomiejmilosz.inventory.catalog;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface CustomerRepository extends Repository<Customer, UUID> {
  Optional<Customer> findById(UUID id);

  Page<Customer> findAll(Pageable page);

  Customer save(Customer value);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Customer e where e.id = :id")
  Optional<Customer> findForUpdate(UUID id);
}
