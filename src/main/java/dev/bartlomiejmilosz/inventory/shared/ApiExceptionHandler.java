package dev.bartlomiejmilosz.inventory.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return response(status, headers, request);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<Object> notFound(ResourceNotFoundException ex, WebRequest request) {
    return response(HttpStatus.NOT_FOUND, HttpHeaders.EMPTY, request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Object> conflict(DataIntegrityViolationException ex, WebRequest request) {
    return response(HttpStatus.CONFLICT, HttpHeaders.EMPTY, request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Object> forbidden(AccessDeniedException ex, WebRequest request) {
    return response(HttpStatus.FORBIDDEN, HttpHeaders.EMPTY, request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> unexpected(Exception ex, WebRequest request) {
    LOG.error("Unexpected request failure", ex);
    return response(HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY, request);
  }

  private ResponseEntity<Object> response(
      HttpStatusCode status, HttpHeaders headers, WebRequest request) {
    String path = ((ServletWebRequest) request).getRequest().getRequestURI();
    return new ResponseEntity<>(ApiProblems.create(status, path), headers, status);
  }
}
