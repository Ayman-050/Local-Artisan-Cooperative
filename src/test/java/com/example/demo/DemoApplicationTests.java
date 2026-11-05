package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Starts the full application: Flyway migrations run and Hibernate validates the schema against the entities. */
@SpringBootTest
@ActiveProfiles("test")
class DemoApplicationTests {

    @Test
    void contextLoadsAndMigrationsMatchEntities() {
    }
}
