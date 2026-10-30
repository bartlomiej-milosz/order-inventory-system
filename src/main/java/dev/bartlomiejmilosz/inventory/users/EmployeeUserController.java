package dev.bartlomiejmilosz.inventory.users;

import dev.bartlomiejmilosz.inventory.users.EmployeeUserService.EmployeeView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(
    name = "Employee accounts",
    description =
        "Administrator access. Writes require the CSRF cookie and token from /api/v1/auth/csrf.")
class EmployeeUserController {
  private final EmployeeUserService users;

  EmployeeUserController(EmployeeUserService users) {
    this.users = users;
  }

  @GetMapping("/{id}")
  ResponseEntity<EmployeeView> findById(@PathVariable UUID id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(users.findById(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<EmployeeView> create(@Valid @RequestBody EmployeeAccountInput input) {
    var user = users.create(input);
    return ResponseEntity.created(URI.create("/api/v1/users/" + user.id()))
        .cacheControl(CacheControl.noStore())
        .body(user);
  }

  @PutMapping("/{id}/role")
  ResponseEntity<EmployeeView> changeRole(
      @PathVariable UUID id, @Valid @RequestBody EmployeeRoleInput input) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(users.changeRole(id, input.role()));
  }

  @PostMapping("/{id}/deactivate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deactivate(@PathVariable UUID id) {
    users.deactivate(id);
  }

  record EmployeeRoleInput(@NotNull EmployeeRole role) {}
}
