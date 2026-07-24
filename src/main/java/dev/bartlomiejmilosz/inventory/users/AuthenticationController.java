package dev.bartlomiejmilosz.inventory.users;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthenticationController {
  @GetMapping("/me")
  ResponseEntity<CurrentEmployee> me(@AuthenticationPrincipal EmployeePrincipal user) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new CurrentEmployee(user.getId(), user.getUsername(), user.getRole()));
  }

  @GetMapping("/csrf")
  ResponseEntity<CsrfResponse> csrf(CsrfToken csrf) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new CsrfResponse(csrf.getHeaderName(), csrf.getToken()));
  }

  record CurrentEmployee(UUID id, String email, EmployeeRole role) {}

  record CsrfResponse(String headerName, String token) {}
}
