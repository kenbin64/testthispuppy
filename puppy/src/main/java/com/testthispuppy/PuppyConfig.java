package com.testthispuppy;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/** Everything a run needs to know, read from puppy.properties. */
public record PuppyConfig(
        String databaseUrl,
        String databaseUser,
        String databasePassword,
        List<String> allowedDatabases,
        String baseUrl,
        int rowsPerTable,
        Path httpCasesFile,
        Path pageCasesFile,
        boolean headless,
        Path reportFile) {

    public static PuppyConfig load(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }
        Path folder = file.toAbsolutePath().getParent();
        PuppyConfig config = new PuppyConfig(
                properties.getProperty("db.url"),
                properties.getProperty("db.user"),
                properties.getProperty("db.password"),
                Arrays.stream(properties.getProperty("db.allowed", "").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList(),
                properties.getProperty("app.baseUrl"),
                Integer.parseInt(properties.getProperty("seed.rowsPerTable", "3")),
                folder.resolve(properties.getProperty("http.cases", "http-cases.txt")),
                folder.resolve(properties.getProperty("browser.pages", "pages.txt")),
                Boolean.parseBoolean(properties.getProperty("browser.headless", "true")),
                folder.resolve(properties.getProperty("report.file", "puppy-report.json")));
        return config;
    }
}
