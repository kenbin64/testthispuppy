package com.testthispuppy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One full run, in order:
 *   guard -> snapshot -> seed -> HTTP tests -> browser tests -> restore -> verify -> report.
 * The restore always runs, even when a test or the seeding fails, so the database is never left dirty.
 *
 *   mvn -q -pl puppy exec:java                      uses puppy/puppy.properties
 *   mvn -q -pl puppy exec:java -Dexec.args=FILE     uses another properties file
 */
public final class PuppyRun {
    private PuppyRun() {
    }

    public static void main(String[] args) throws Exception {
        Path configFile = Path.of(args.length > 0 ? args[0] : "puppy.properties");
        PuppyConfig config = PuppyConfig.load(configFile);
        DatabaseGuard.check(config);
        List<TestResult> results = run(config);
        Reporter.print(results);
        Reporter.write(results, config.reportFile());
        System.out.println("  report written to " + config.reportFile());
        System.exit(results.stream().allMatch(TestResult::passed) ? 0 : 1);
    }

    public static List<TestResult> run(PuppyConfig config) throws Exception {
        List<TestResult> results = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(config.databaseUrl(), config.databaseUser(), config.databasePassword())) {
            List<TableInfo> tables = TableOrder.parentsFirst(SchemaReader.read(connection, "public"));
            DatabaseSnapshot before = SnapshotTaker.take(connection, tables);
            DummyValues dummy = new DummyValues();
            try {
                Map<String, List<Map<String, Object>>> seeded = seed(connection, tables, config, dummy, results);
                testHttp(config, seeded, results);
                testBrowser(config, dummy, results);
            } catch (Exception e) {
                results.add(new TestResult("setup", "run stopped early", false, String.valueOf(e.getMessage()), 0));
            } finally {
                results.add(restore(connection, tables, before));
            }
        }
        return results;
    }

    private static Map<String, List<Map<String, Object>>> seed(Connection connection, List<TableInfo> tables, PuppyConfig config,
                                                               DummyValues dummy, List<TestResult> results) throws Exception {
        long start = System.currentTimeMillis();
        Map<String, List<Map<String, Object>>> seeded = DatabaseSeeder.seed(connection, tables, config.rowsPerTable(), dummy);
        int rows = seeded.values().stream().mapToInt(List::size).sum();
        results.add(new TestResult("setup", "seed dummy data", true,
                rows + " rows into " + seeded.size() + " tables " + seeded.keySet(), System.currentTimeMillis() - start));
        return seeded;
    }

    private static void testHttp(PuppyConfig config, Map<String, List<Map<String, Object>>> seeded, List<TestResult> results) throws Exception {
        HttpTester tester = new HttpTester();
        for (Map<String, String> row : GridFile.read(config.httpCasesFile())) {
            try {
                results.add(tester.run(Placeholders.fill(HttpCase.fromRow(row), seeded), config.baseUrl()));
            } catch (RuntimeException e) {
                results.add(new TestResult("http", String.valueOf(row.get("name")), false, "bad test row: " + e.getMessage(), 0));
            }
        }
    }

    private static void testBrowser(PuppyConfig config, DummyValues dummy, List<TestResult> results) throws Exception {
        List<Map<String, String>> rows = GridFile.read(config.pageCasesFile());
        if (!rows.isEmpty()) {
            try (BrowserTester browser = new BrowserTester(config.headless(), new PageScanner(), dummy)) {
                for (Map<String, String> row : rows) {
                    results.add(browser.run(PageCase.fromRow(row), config.baseUrl()));
                }
            }
        }
    }

    private static TestResult restore(Connection connection, List<TableInfo> tables, DatabaseSnapshot before) {
        long start = System.currentTimeMillis();
        TestResult result;
        try {
            DatabaseRestorer.Summary summary = DatabaseRestorer.restore(connection, tables, before);
            DatabaseSnapshot after = SnapshotTaker.take(connection, tables);
            boolean identical = after.equals(before);
            String detail = "deleted " + summary.deleted() + ", set back " + summary.updated() + ", put back " + summary.reinserted()
                    + ", reset " + summary.sequencesReset() + " sequences; database "
                    + (identical ? "matches the snapshot exactly (" + before.rowCount() + " rows)" : "DOES NOT match the snapshot");
            result = new TestResult("cleanup", "restore the database", identical, detail, System.currentTimeMillis() - start);
        } catch (Exception e) {
            result = new TestResult("cleanup", "restore the database", false, "restore failed: " + e.getMessage(), System.currentTimeMillis() - start);
        }
        return result;
    }
}
