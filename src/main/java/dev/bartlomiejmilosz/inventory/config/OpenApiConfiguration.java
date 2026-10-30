package dev.bartlomiejmilosz.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local & !deploy & !bootstrap-admin")
class OpenApiConfiguration {
  @Bean
  OpenAPI inventoryOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Order and Inventory API")
                .version("v1")
                .description(
                    "Authenticate with employee HTTP Basic credentials. Before a write, fetch /api/v1/auth/csrf and send its cookie and named token header. Both roles can read the catalog and stock, receive stock, and process orders. Account administration, catalog changes and stock adjustments require ADMIN."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "basicAuth",
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
        .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
  }
}
