package com.dams.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata plus the one security scheme every controller already references
 * ({@code @SecurityRequirement(name = "bearerAuth")}). Without the matching
 * {@link SecurityScheme} declared here, Swagger UI shows the lock icons but its
 * <b>Authorize</b> dialog has no field to paste a token into.
 *
 * <p>Usage: {@code POST /api/v1/auth/login} → copy {@code accessToken} → click
 * <b>Authorize</b> in Swagger UI → paste the raw JWT. Swagger then sends
 * {@code Authorization: Bearer <jwt>} on every call. With
 * {@code springdoc.swagger-ui.persist-authorization=true} (set in application.yml) the
 * token survives page reloads.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
    title = "DAMS API",
    version = "v1",
    description = "Dealership Accounts Management System — base path /api/v1. "
        + "Authenticate with POST /api/v1/auth/login, then use the Authorize button "
        + "with the returned accessToken."))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Paste the raw JWT from POST /api/v1/auth/login (no \"Bearer \" prefix).")
public class OpenApiConfig {
}
