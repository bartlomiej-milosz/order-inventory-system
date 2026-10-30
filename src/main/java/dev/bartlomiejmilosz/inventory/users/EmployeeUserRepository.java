package dev.bartlomiejmilosz.inventory.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface EmployeeUserRepository extends Repository<EmployeeUser, UUID> {
  Optional<EmployeeUser> findById(UUID id);

  Optional<EmployeeUser> findByEmail(String email);

  long count();

  long countByRoleAndActiveTrue(EmployeeRole role);

  EmployeeUser save(EmployeeUser user);
}
