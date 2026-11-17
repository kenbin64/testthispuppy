package com.testthispuppy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Forces every error path to run and proves each one fails safely: a clear failed result instead of a crash,
 * and a database that is never left half-changed. Uses a throwaway PostgreSQL in Docker.
 */
class FailurePathsTest {
    private static InternalDatabase database;
    private static Path folder;

    @BeforeAll
    static void startDatabase() throws Exception {
        folder = Files.createTempDirectory("puppy-failures");
        Path schema = folder.resolve("schema.sql");
        // "strict" only accepts the code 'abc', which no dummy value will ever be, so seeding it must fail.
        Files.writeString(schema, """
                CREATE TABLE strict (id SERIAL PRIMARY KEY, code VARCHAR(3) NOT NULL CHECK (code = 'abc'));
                INSERT INTO strict (code) VALUES ('abc');
                CREATE TABLE keep (id SERIAL PRIMARY KEY, label TEXT NOT NULL);
                INSERT INTO keep (label) VALUES ('original one'), ('original two');
                """);
        database = InternalDatabase.start(schema);
        Files.writeString(folder.resolve("http.txt"), "name | method | path | expectedStatus\n");
        Files.writeString(folder.resolve("pages.txt"), "name | path | expectedText\n");
    }

    @AfterAll
    static void stopDatabase() {
        database.close();
    }

    private static PuppyConfig config(String url, List<String> checks) {
        return new PuppyConfig("external", url, database.user(), database.password(), List.of(url), null, List.of(),
                "http://127.0.0.1:1", 2, folder.resolve("http.txt"), folder.resolve("pages.txt"), true,
                folder.resolve("report.json"), folder, checks, new Properties());
    }

    private static int count(String table) throws SQLException {
        try (Connection c = DriverManager.getConnection(database.url(), database.user(), database.password());
             Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM " + table)) {
            r.next();
            return r.getInt(1);
        }
    }

    @Test
    void whenSeedingFailsTheRunStopsAndStillRestoresEverything() throws Exception {
        List<TestResult> results = PuppyRun.run(config(database.url(), List.of("happy")));
        TestResult stopped = results.stream().filter(r -> r.name().equals("run stopped early")).findFirst().orElseThrow();
        TestResult cleanup = results.get(results.size() - 1);
        assertFalse(stopped.passed(), "the seeding failure is reported as a failure");
        assertTrue(cleanup.passed(), "and the cleanup still ran and verified: " + cleanup.detail());
        assertEquals(1, count("strict"));
        assertEquals(2, count("keep"), "rows seeded into 'keep' before the failure were removed");
    }

    @Test
    void anUnreachableDatabaseStopsTheRunBeforeAnythingIsTouched() throws Exception {
        List<TestResult> results = PuppyRun.run(config("jdbc:postgresql://127.0.0.1:1/nothing", List.of("happy")));
        assertTrue(results.stream().anyMatch(r -> r.name().equals("reach the database port") && !r.passed()));
        assertTrue(results.stream().anyMatch(r -> r.name().equals("run stopped before touching the database")));
        assertTrue(results.stream().noneMatch(r -> r.kind().equals("cleanup")), "nothing was changed, so nothing to restore");
    }

    @Test
    void sqlInjectionTestsAreRefusedOutsideInternalMode() throws Exception {
        List<TestResult> results = PuppyRun.run(config(database.url(), List.of("sql-injection")));
        assertTrue(results.stream().anyMatch(r -> r.kind().equals("injection") && r.detail().startsWith("SKIPPED for safety")));
    }

    @Test
    void aRestoreThatCannotFinishRollsBackCompletely() throws Exception {
        try (Connection c = DriverManager.getConnection(database.url(), database.user(), database.password())) {
            List<TableInfo> tables = TableOrder.parentsFirst(SchemaReader.read(c, "public"));
            DatabaseSnapshot before = SnapshotTaker.take(c, tables);
            try (Statement s = c.createStatement()) {
                s.execute("INSERT INTO keep (label) VALUES ('added by a test')");
                s.execute("UPDATE keep SET label = 'changed' WHERE label = 'original one'");
                s.execute("CREATE FUNCTION refuse() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'no deletes today'; END $$ LANGUAGE plpgsql");
                s.execute("CREATE TRIGGER no_delete BEFORE DELETE ON keep FOR EACH ROW EXECUTE FUNCTION refuse()");
            }
            assertThrows(SQLException.class, () -> DatabaseRestorer.restore(c, tables, before));
            assertEquals(3, count("keep"), "the failed restore deleted nothing");
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM keep WHERE label = 'changed'")) {
                r.next();
                assertEquals(1, r.getInt(1), "and set nothing back: it was all or nothing");
            }
            try (Statement s = c.createStatement()) {
                s.execute("DROP TRIGGER no_delete ON keep");
                s.execute("DROP FUNCTION refuse()");
            }
            DatabaseRestorer.restore(c, tables, before);
            assertEquals(before, SnapshotTaker.take(c, tables), "once the blocker is gone, the restore completes exactly");
        }
    }

    @Test
    void aTableWithoutAPrimaryKeyIsRefusedClearly() throws Exception {
        try (Connection c = DriverManager.getConnection(database.url(), database.user(), database.password())) {
            TableInfo noKey = new TableInfo("keep", List.of(), List.of(), List.of());
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> SnapshotTaker.take(c, List.of(noKey)));
            assertTrue(e.getMessage().contains("no primary key"));
        }
    }

    @Test
    void anAppThatExitsAtOnceIsReportedNotWaitedOnForever() {
        long start = System.currentTimeMillis();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AppProcess.start(List.of("java", "-version"), folder, folder.resolve("app.log"), "http://127.0.0.1:1"));
        assertTrue(e.getMessage().contains("did not start answering"));
        assertTrue(System.currentTimeMillis() - start < 30_000, "it noticed the process had died instead of waiting two minutes");
    }

    @Test
    void aPageWithNoSubmitButtonFailsTheTestNotTheRun() throws Exception {
        Path page = folder.resolve("nosubmit.html");
        Files.writeString(page, "<form><input name='a'></form>");
        try (BrowserTester browser = new BrowserTester(true, new PageScanner(), new DummyValues())) {
            TestResult result = browser.run(new PageCase("no button", "nosubmit.html", "Saved"), folder.toUri().toString());
            assertFalse(result.passed());
            assertTrue(result.detail().startsWith("failed:"), result.detail());
        }
    }

    @Test
    void aMissingTestFileIsAFailedRunNotACrash() throws Exception {
        PuppyConfig missing = new PuppyConfig("external", database.url(), database.user(), database.password(), List.of(database.url()),
                null, List.of(), "http://127.0.0.1:1", 0, folder.resolve("does-not-exist.txt"), folder.resolve("pages.txt"),
                true, folder.resolve("report.json"), folder, List.of("happy"), new Properties());
        List<TestResult> results = PuppyRun.run(missing);
        assertTrue(results.stream().anyMatch(r -> r.name().equals("run stopped early") && !r.passed()));
        assertTrue(results.get(results.size() - 1).passed(), "cleanup still verified");
    }
}
