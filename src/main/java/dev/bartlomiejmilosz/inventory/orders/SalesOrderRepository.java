package dev.bartlomiejmilosz.inventory.orders;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface SalesOrderRepository
    extends Repository<SalesOrder, UUID>, JpaSpecificationExecutor<SalesOrder> {
  Optional<SalesOrder> findById(UUID id);

  SalesOrder save(SalesOrder order);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from SalesOrder o where o.id = :id")
  Optional<SalesOrder> findForUpdate(UUID id);
}
