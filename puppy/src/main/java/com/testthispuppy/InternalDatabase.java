package com.testthispuppy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A throwaway PostgreSQL database just for one run: started fresh in Docker, given the schema,
 * and deleted completely when the run ends. Nothing it touches exists before or after.
 */
public final class InternalDatabase implements AutoCloseable {
    private final PostgreSQLContainer<?> container;

    private InternalDatabase(PostgreSQLContainer<?> container) {
        this.container = container;
    }

    public static InternalDatabase start(Path schemaFile) throws Exception {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("puppy_internal").withUsername("puppy").withPassword("puppy");
        container.start();
        InternalDatabase database = new InternalDatabase(container);
        if (schemaFile != null) {
            database.apply(Files.readString(schemaFile));
        }
        return database;
    }

    private void apply(String schemaSql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url(), user(), password());
             Statement statement = connection.createStatement()) {
            statement.execute(schemaSql);
        }
    }

    public String url() {
        return container.getJdbcUrl();
    }

    public String user() {
        return container.getUsername();
    }

    public String password() {
        return container.getPassword();
    }

    @Override
    public void close() {
        container.stop();
    }
}
