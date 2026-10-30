package dev.bartlomiejmilosz.inventory.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

record EmployeeAccountInput(
    @NotBlank @Email(regexp = "[^:\\s\\p{Cc}]+") @Size(max = 254) String email,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(
            format = "password",
            minLength = 14,
            maxLength = 72,
            description =
                "At least 14 Unicode characters, at most 72 UTF-8 bytes. Control characters are not accepted.")
        @NotNull
        String password,
    @NotNull EmployeeRole role) {
  EmployeeAccountInput {
    if (email != null) {
      email = EmailAddress.normalize(email);
    }
  }

  static boolean validPassword(String value) {
    return value != null
        && value.length() <= 72
        && value.codePointCount(0, value.length()) >= 14
        && value.getBytes(StandardCharsets.UTF_8).length <= 72
        && value.codePoints().noneMatch(Character::isISOControl);
  }

  @Override
  public String toString() {
    return "EmployeeAccountInput[role=" + role + "]";
  }
}
