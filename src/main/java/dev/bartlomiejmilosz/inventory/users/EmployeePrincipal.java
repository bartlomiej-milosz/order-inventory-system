package dev.bartlomiejmilosz.inventory.users;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class EmployeePrincipal implements UserDetails, CredentialsContainer {
  private final UUID id;
  private final String email;
  private final EmployeeRole role;
  private final boolean active;
  private String passwordHash;

  EmployeePrincipal(EmployeeUser user) {
    id = user.getId();
    email = user.getEmail();
    role = user.getRole();
    active = user.isActive();
    passwordHash = user.getPasswordHash();
  }

  public UUID getId() {
    return id;
  }

  public EmployeeRole getRole() {
    return role;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @JsonIgnore
  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public boolean isEnabled() {
    return active;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public void eraseCredentials() {
    passwordHash = null;
  }
}
