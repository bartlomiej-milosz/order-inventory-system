package dev.bartlomiejmilosz.inventory.users;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DatabaseUserDetailsService implements UserDetailsService {
  private final EmployeeUserRepository users;

  DatabaseUserDetailsService(EmployeeUserRepository users) {
    this.users = users;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) {
    EmployeeUser user =
        users
            .findByEmail(EmailAddress.normalize(username))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials."));
    if (!user.getPasswordHash().startsWith("{bcrypt}")) {
      throw new UsernameNotFoundException("Invalid credentials.");
    }
    return new EmployeePrincipal(user);
  }
}
