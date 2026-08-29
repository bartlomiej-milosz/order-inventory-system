package dev.bartlomiejmilosz.inventory.shared;

public class InvalidRequestException extends RuntimeException {
  public InvalidRequestException(String message) {
    super(message);
  }
}
