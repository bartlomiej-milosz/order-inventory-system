package dev.bartlomiejmilosz.inventory.config;

import dev.bartlomiejmilosz.inventory.shared.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
  private final ObjectMapper mapper;

  SecurityProblemHandler(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
      throws IOException {
    response.setHeader("WWW-Authenticate", "Basic realm=\"inventory\", charset=\"UTF-8\"");
    write(response, ApiProblems.create(HttpStatus.UNAUTHORIZED, request.getRequestURI()));
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException failure)
      throws IOException {
    var problem =
        failure instanceof CsrfException
            ? ApiProblems.create(
                HttpStatus.FORBIDDEN, request.getRequestURI(), "CSRF token is missing or invalid.")
            : ApiProblems.create(HttpStatus.FORBIDDEN, request.getRequestURI());
    write(response, problem);
  }

  private void write(HttpServletResponse response, org.springframework.http.ProblemDetail problem)
      throws IOException {
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), problem);
  }
}
