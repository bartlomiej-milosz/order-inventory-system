package dev.bartlomiejmilosz.inventory.users;

import java.util.Locale;

final class EmailAddress {
  private EmailAddress() {}

  static String normalize(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
