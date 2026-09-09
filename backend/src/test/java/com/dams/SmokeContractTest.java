package com.dams;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the CI api-smoke contract locally (no Docker, no Postgres): the
 * exact probes {@code .github/workflows/ci.yml} runs against the booted app —
 * health is UP, OpenAPI docs serve, protected routes 401 without a token,
 * and a bad login is refused with 401, never a 500. A failure here fails
 * {@code api-smoke} in CI the same way, so fix it here where it is cheap.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:damssmoke;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SmokeContractTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void health_isUp() {
        ResponseEntity<Map> res = http.getForEntity("/actuator/health", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("status", "UP");
    }

    @Test
    void openApiDocs_serveWithoutAuth() {
        ResponseEntity<String> res = http.getForEntity("/api-docs", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void dashboard_withoutToken_isRefused() {
        ResponseEntity<String> res =
                http.getForEntity("/api/v1/dashboard/summary?period=today", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void badLogin_isRefused_notA500() {
        ResponseEntity<String> res = http.postForEntity("/api/v1/auth/login",
                Map.of("email", "nobody@x.in", "password", "wrong"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
