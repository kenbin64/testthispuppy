package com.testthispuppy;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Everything a run needs to know, read from a properties file.
 *
 * Two database modes:
 *   external  connect to an existing database with the credentials given (it is restored afterwards)
 *   internal  start a throwaway database, apply schemaFile, start the app with appCommand pointed at it,
 *             and delete everything at the end
 *
 * Browser checks (browser.checks, comma separated): happy, wrong-input, sql-injection.
 * sql-injection only runs in internal mode, never against a real database.
 */
public record PuppyConfig(
        String mode,
        String databaseUrl,
        String databaseUser,
        String databasePassword,
        List<String> allowedDatabases,
        Path schemaFile,
        List<String> appCommand,
        String baseUrl,
        int rowsPerTable,
        Path httpCasesFile,
        Path pageCasesFile,
        boolean headless,
        Path reportFile,
        Path folder,
        List<String> checks,
        Properties settings) {

    /** True when this browser check is switched on: happy, wrong-input or sql-injection. */
    public boolean checks(String name) {
        return checks.contains(name);
    }

    /** Any other setting from the file, with a fallback. */
    public String setting(String key, String fallback) {
        return settings.getProperty(key, fallback);
    }

    public boolean internal() {
        return "internal".equalsIgnoreCase(mode);
    }

    public static PuppyConfig load(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }
        Path folder = file.toAbsolutePath().getParent();
        String schema = properties.getProperty("db.schema", "").trim();
        String command = properties.getProperty("app.command", "").trim();
        PuppyConfig config = new PuppyConfig(
                properties.getProperty("db.mode", "external").trim(),
                properties.getProperty("db.url"),
                properties.getProperty("db.user"),
                properties.getProperty("db.password"),
                Arrays.stream(properties.getProperty("db.allowed", "").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList(),
                schema.isEmpty() ? null : folder.resolve(schema).normalize(),
                command.isEmpty() ? List.of() : Arrays.asList(command.split("\\s+")),
                properties.getProperty("app.baseUrl"),
                Integer.parseInt(properties.getProperty("seed.rowsPerTable", "3")),
                folder.resolve(properties.getProperty("http.cases", "http-cases.txt")),
                folder.resolve(properties.getProperty("browser.pages", "pages.txt")),
                Boolean.parseBoolean(properties.getProperty("browser.headless", "true")),
                folder.resolve(properties.getProperty("report.file", "puppy-report.json")),
                folder,
                Arrays.stream(properties.getProperty("browser.checks", "happy").split(",")).map(String::trim).filter(c -> !c.isEmpty()).toList(),
                properties);
        return config;
    }

    /** The same settings, pointed at another database (used when internal mode creates one). */
    public PuppyConfig withDatabase(String url, String user, String password) {
        return new PuppyConfig(mode, url, user, password, List.of(url), schemaFile, appCommand, baseUrl, rowsPerTable,
                httpCasesFile, pageCasesFile, headless, reportFile, folder, checks, settings);
    }
}
