package com.dams.db;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway refuses to start when two migrations share a version, but the unit tests run with
 * Flyway off and the Docker-based tests are skipped on a machine without Docker — so a clash
 * (two parallel changes both picking the next number) only showed up in CI. This catches it locally.
 */
class MigrationVersionsTest {

    private static final Pattern NAME = Pattern.compile("^V(\\d+)__.+\\.sql$");

    @Test
    void everyMigrationVersionIsUnique() throws Exception {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*.sql");
        assertThat(files).isNotEmpty();

        Map<Integer, String> seen = new HashMap<>();
        for (Resource f : files) {
            String name = f.getFilename();
            Matcher m = NAME.matcher(name);
            assertThat(m.matches()).as("migration file name %s", name).isTrue();
            String clash = seen.put(Integer.parseInt(m.group(1)), name);
            assertThat(clash).as("version V%s is used by both %s and %s", m.group(1), clash, name).isNull();
        }
    }
}
