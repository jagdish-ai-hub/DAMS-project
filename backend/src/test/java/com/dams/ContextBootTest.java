package com.dams;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Full-context boot guard: every bean wires, every Spring Data derived query
 * parses, and every entity maps — on an H2 schema Hibernate generates itself.
 * Needs no Docker, so it runs on every machine and every PR (unlike the
 * Testcontainers tests, which only run where a Docker daemon exists).
 *
 * It does NOT replace Flyway validation on real Postgres (api-smoke and the
 * Testcontainers tests cover that in CI) — it catches wiring/query/mapping
 * regressions locally, where they are cheapest to fix.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:damsboot;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ContextBootTest {

    @Test
    void applicationContext_loads_allBeansAndQueries() {
        // Booting the context IS the assertion: a wiring error, an
        // unparseable derived query, or an unmappable entity fails here.
    }
}
