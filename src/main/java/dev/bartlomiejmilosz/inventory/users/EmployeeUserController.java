package dev.bartlomiejmilosz.inventory.users;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
class EmployeeUserController {
  private final EmployeeUserService users;

  EmployeeUserController(EmployeeUserService users) {
    this.users = users;
  }

  @GetMapping("/{id}")
  ResponseEntity<EmployeeUserService.EmployeeView> findById(@PathVariable UUID id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(users.findById(id));
  }
}
