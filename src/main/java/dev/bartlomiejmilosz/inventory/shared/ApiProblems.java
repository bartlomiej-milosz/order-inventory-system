package dev.bartlomiejmilosz.inventory.shared;

import java.net.URI;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

public final class ApiProblems {
  private ApiProblems() {}

  public static ProblemDetail create(HttpStatusCode status, String path) {
    String detail =
        switch (status.value()) {
          case 400 -> "Request validation failed.";
          case 401 -> "Authentication is required or credentials are invalid.";
          case 403 -> "Access denied.";
          case 404 -> "Resource not found.";
          case 405 -> "HTTP method is not supported for this resource.";
          case 409 -> "The request conflicts with the current resource state.";
          default ->
              status.is5xxServerError()
                  ? "An unexpected server error occurred."
                  : "The request could not be processed.";
        };
    return create(status, path, detail);
  }

  public static ProblemDetail create(HttpStatusCode status, String path, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setInstance(URI.create(path));
    return problem;
  }
}
