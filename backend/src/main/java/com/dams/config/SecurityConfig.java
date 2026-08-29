package com.dams.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dams.common.dto.ApiError;
import com.dams.common.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtConfig jwtFilter;
    private final TenantFilter tenantFilter;
    private final ObjectMapper objectMapper;

    @Value("${dams.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    public SecurityConfig(JwtConfig jwtFilter,
                          TenantFilter tenantFilter,
                          ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.tenantFilter = tenantFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Unauthenticated public routes
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/accept-invite",
                    // Signed-URL attachment streaming (local storage backend): the sig + exp
                    // query params ARE the authorisation, like an S3 presigned URL.
                    "/api/v1/attachments/raw",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/api-docs/**",
                    "/actuator/health"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(this::handleAuthError)
                .accessDeniedHandler(this::handleAccessDenied)
            )
            // JWT filter runs before the standard username/password filter
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tenantFilter, JwtConfig.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // JwtConfig and TenantFilter are @Component OncePerRequestFilters, so Spring Boot would also
    // auto-register them with the servlet container — running them once at servlet level (in the
    // wrong order) and then skipping them in the security chain. Disable the servlet-level copies
    // so they run ONLY where SecurityConfig places them, in the right order.
    @Bean
    public FilterRegistrationBean<JwtConfig> disableJwtFilterAutoRegistration(JwtConfig filter) {
        FilterRegistrationBean<JwtConfig> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<TenantFilter> disableTenantFilterAutoRegistration(TenantFilter filter) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Request-ID"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private void handleAuthError(HttpServletRequest request,
                                 HttpServletResponse response,
                                 org.springframework.security.core.AuthenticationException ex)
            throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    private void handleAccessDenied(HttpServletRequest request,
                                    HttpServletResponse response,
                                    org.springframework.security.access.AccessDeniedException ex)
            throws IOException {
        writeError(response, HttpStatus.FORBIDDEN, "Access denied");
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        ApiError error = new ApiError(requestId, status.value(), status.getReasonPhrase(), message);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
    }
}
