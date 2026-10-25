package com.flowboard.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Boots the whole application against a real PostgreSQL (Testcontainers by default).
 * Where Docker is unavailable, point TEST_DATABASE_URL / TEST_DATABASE_USER / TEST_DATABASE_PASSWORD at any
 * empty PostgreSQL 14+ database instead; the schema is created by Flyway either way.
 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(Api.class)
public abstract class AbstractIntegrationTest {

    private static PostgreSQLContainer<?> container;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("TEST_DATABASE_URL");
        if (url != null && !url.isBlank()) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "flowboard"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "flowboard"));
            return;
        }
        synchronized (AbstractIntegrationTest.class) {
            if (container == null) {
                container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
                container.start();
            }
        }
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    @Autowired
    protected Api api;
}
