package dev.bartlomiejmilosz.inventory.shared;

public class ResourceConflictException extends RuntimeException {
  public ResourceConflictException(String message) {
    super(message);
  }
}
