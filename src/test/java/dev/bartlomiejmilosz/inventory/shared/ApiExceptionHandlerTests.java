package dev.bartlomiejmilosz.inventory.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTests {
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new ErrorProbe())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void returnsValidationErrorsWithoutRejectedValues() throws Exception {
    mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Request validation failed."));
  }

  @Test
  void returnsNotFoundAndConflictWithoutExceptionMessages() throws Exception {
    mvc.perform(get("/probe/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Resource not found."));

    mvc.perform(get("/probe/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(
            jsonPath("$.detail").value("The request conflicts with the current resource state."))
        .andExpect(jsonPath("$.instance").value("/probe/conflict"));
  }

  @Test
  void handlesMalformedJsonAndUnsupportedMethods() throws Exception {
    mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));

    mvc.perform(put("/probe"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.status").value(405));
  }

  @RestController
  @Profile("error-handler-probe")
  @RequestMapping("/probe")
  static class ErrorProbe {
    @PostMapping
    void validate(@Valid @RequestBody Input input) {}

    @GetMapping("/not-found")
    void notFound() {
      throw new ResourceNotFoundException();
    }

    @GetMapping("/conflict")
    void conflict() {
      throw new DataIntegrityViolationException("private SQL and constraint details");
    }

    record Input(@NotBlank String name) {}
  }
}
