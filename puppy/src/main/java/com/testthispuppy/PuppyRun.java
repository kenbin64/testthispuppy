package com.testthispuppy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One full run, in order:
 *   guard -> connection check -> snapshot -> seed -> HTTP tests -> race test -> browser tests -> restore -> verify -> report.
 * The restore always runs, even when a test or the seeding fails, so the database is never left dirty.
 *
 *   mvn -q -pl puppy exec:java                      uses puppy/puppy.properties
 *   mvn -q -pl puppy exec:java -Dexec.args=FILE     uses another properties file
 */
public final class PuppyRun {
    private PuppyRun() {
    }

    /**
     * Always ends the process: 0 when everything passed, 1 when a test failed, 2 when the run itself crashed.
     * (Selenium and Testcontainers leave background threads running, so without an explicit exit a crash
     * would leave the program hanging instead of reporting.)
     */
    public static void main(String[] args) {
        int code;
        try {
            code = runFromSettings(args) ? 0 : 1;
        } catch (Exception e) {
            System.err.println("TestThisPuppy stopped with an error: " + e);
            e.printStackTrace();
            code = 2;
        }
        System.exit(code);
    }

    private static boolean runFromSettings(String[] args) throws Exception {
        Path configFile = Path.of(args.length > 0 ? args[0] : "puppy.properties");
        PuppyConfig config = PuppyConfig.load(configFile);
        String frames = config.setting("demo.frames", "");
        if (!frames.isBlank()) {
            FrameRecorder.start(config.folder().resolve(frames), Long.parseLong(config.setting("demo.pauseMs", "0")));
        }
        // db.mode=none skips the app and database steps, for runs that only check code or tests.
        List<TestResult> results = new ArrayList<>("none".equalsIgnoreCase(config.mode()) ? List.of()
                : config.internal() ? runInternal(config) : runExternal(config));
        FrameRecorder.finish();
        results.addAll(CodeCheck.run(config));              // check the code itself (off unless code.source is set)
        results.addAll(MutationCheck.run(config));          // test the tests (off unless mutation.project is set)
        results.addAll(analyzeLogs(config, results));
        Reporter.print(results);
        Reporter.write(results, config.reportFile());
        Path html = config.reportFile().resolveSibling(config.reportFile().getFileName().toString().replaceFirst("\\.json$", "") + ".html");
        Reporter.writeHtml(results, html);
        System.out.println("  HTML report written to " + html);
        System.out.println("  report written to " + config.reportFile());
        return results.stream().allMatch(TestResult::passed);
    }

    /** External mode: an existing database, approved by the guard, restored to its snapshot afterwards. */
    public static List<TestResult> runExternal(PuppyConfig config) throws Exception {
        DatabaseGuard.check(config);
        return run(config);
    }

    /**
     * Internal mode: a throwaway database with the schema applied, the app started against it,
     * then the app stopped and the database deleted. The restore still runs and is verified,
     * which proves it works; the delete is the final guarantee.
     */
    public static List<TestResult> runInternal(PuppyConfig config) throws Exception {
        long start = System.currentTimeMillis();
        List<TestResult> results = new ArrayList<>();
        try (InternalDatabase database = InternalDatabase.start(config.schemaFile())) {
            PuppyConfig pointed = config.withDatabase(database.url(), database.user(), database.password());
            results.add(new TestResult("setup", "create a throwaway database", true,
                    "PostgreSQL 16 in Docker, schema " + (config.schemaFile() == null ? "none" : config.schemaFile().getFileName()),
                    System.currentTimeMillis() - start));
            if (config.appCommand().isEmpty()) {
                results.addAll(run(pointed));
            } else {
                List<String> command = config.appCommand().stream()
                        .map(part -> part.replace("{db.url}", database.url()).replace("{db.user}", database.user())
                                .replace("{db.password}", database.password())).toList();
                long appStart = System.currentTimeMillis();
                try (AppProcess app = AppProcess.start(command, config.folder(), config.folder().resolve("app-under-test.log"), config.baseUrl())) {
                    results.add(new TestResult("setup", "start the app on the throwaway database", true,
                            "answering at " + config.baseUrl(), System.currentTimeMillis() - appStart));
                    results.addAll(run(pointed));
                }
            }
        }
        results.add(new TestResult("cleanup", "delete the throwaway database", true, "container stopped and removed", 0));
        return results;
    }

    public static List<TestResult> run(PuppyConfig config) throws Exception {
        List<TestResult> results = new ArrayList<>();
        if (config.checks("sql-injection") && !config.internal()) {
            // Reported first, so the refusal is in the report however far the run gets.
            results.add(new TestResult("injection", "sql injection tests", false,
                    "SKIPPED for safety: SQL injection tests only run in internal mode, never against a real database", 0));
        }
        results.addAll(ConnectionCheck.check(config));
        if (!ConnectionCheck.reachable(results)) {
            results.add(new TestResult("setup", "run stopped before touching the database", false,
                    "the database could not be reached, so nothing was seeded, tested or changed", 0));
            return results;
        }
        try (Connection connection = DriverManager.getConnection(config.databaseUrl(), config.databaseUser(), config.databasePassword())) {
            List<TableInfo> tables = TableOrder.parentsFirst(SchemaReader.read(connection, "public"));
            DatabaseSnapshot before = SnapshotTaker.take(connection, tables);
            DummyValues dummy = new DummyValues();
            try {
                Map<String, List<Map<String, Object>>> seeded = seed(connection, tables, config, dummy, results);
                testHttp(config, seeded, results);
                results.addAll(ErrorPageCheck.check(config));   // do error pages expose stack traces?
                results.addAll(XssCheck.check(config));         // reflected cross-site scripting (xss.paths)
                testRace(config, connection, results);
                testBrowser(config, dummy, results, connection, tables);
            } catch (Exception e) {
                results.add(new TestResult("setup", "run stopped early", false, String.valueOf(e.getMessage()), 0));
            } finally {
                results.add(restore(connection, tables, before));
            }
        }
        return results;
    }

    /**
     * Reads the app's log (internal mode: the log of the app it started; external mode: log.file), lists each
     * distinct root cause, and writes puppy-analysis.md. With ai.analysis=claude and at least one failure,
     * Claude's explanation of the failures is added to that file. Findings are information, never failures.
     */
    static List<TestResult> analyzeLogs(PuppyConfig config, List<TestResult> results) throws java.io.IOException {
        List<TestResult> findings = new ArrayList<>();
        String logSetting = config.setting("log.file", config.internal() ? "app-under-test.log" : "");
        Path logFile = logSetting.isBlank() ? null : config.folder().resolve(logSetting);
        List<TestResult> failures = results.stream().filter(r -> !r.passed()).toList();
        if (logFile != null && java.nio.file.Files.exists(logFile)) {
            LogAnalyzer.Summary summary = LogAnalyzer.analyze(java.nio.file.Files.readString(logFile),
                    java.util.Arrays.asList(config.databasePassword(), config.setting("db.password", null)));
            for (LogAnalyzer.Finding finding : summary.findings()) {
                findings.add(new TestResult("log", "root cause seen " + finding.count() + " time(s)", true,
                        finding.rootCause() + "  " + finding.where(), 0));
            }
            if (summary.findings().isEmpty()) {
                findings.add(new TestResult("log", "read the app log", true, "no stack traces; "
                        + summary.errorLines().size() + " ERROR line(s)", 0));
            }
            boolean useAi = "claude".equalsIgnoreCase(config.setting("ai.analysis", "off")) && !failures.isEmpty();
            String ai = useAi ? AiAnalyzer.explain(failures, summary) : null;
            writeAnalysis(config.folder().resolve("puppy-analysis.md"), failures, summary, ai);
            if (useAi) {
                findings.add(new TestResult("ai", "AI analysis of the failures", true, "written to puppy-analysis.md", 0));
            }
        }
        return findings;
    }

    private static void writeAnalysis(Path file, List<TestResult> failures, LogAnalyzer.Summary summary, String ai) throws java.io.IOException {
        StringBuilder text = new StringBuilder("# TestThisPuppy analysis\n\n## Failed tests\n\n");
        failures.forEach(r -> text.append("- **").append(r.name()).append("** (").append(r.kind()).append("): ").append(r.detail()).append('\n'));
        if (failures.isEmpty()) {
            text.append("None.\n");
        }
        text.append("\n## Root causes in the app log (secrets hidden)\n\n");
        summary.findings().forEach(f -> text.append("- ").append(f.rootCause()).append("\n  - first reported as: ").append(f.headline())
                .append("\n  - at: ").append(f.where()).append("\n  - seen ").append(f.count()).append(" time(s)\n"));
        if (summary.findings().isEmpty()) {
            text.append("No stack traces.\n");
        }
        if (ai != null) {
            text.append("\n## AI analysis (Claude, from the redacted evidence above)\n\n").append(ai).append('\n');
        }
        java.nio.file.Files.writeString(file, text.toString());
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

    private static void testRace(PuppyConfig config, Connection connection, List<TestResult> results) {
        String path = config.setting("race.path", "");
        if (!path.isBlank()) {
            results.add(RaceTester.run(connection, config.baseUrl(), config.setting("race.method", "POST"), path,
                    config.setting("race.body", ""), config.setting("race.table", ""),
                    Integer.parseInt(config.setting("race.requests", "20"))));
        }
    }

    // Every .txt in the macros folder, each run macro.repeat times with fresh dummy data.
    private static void runMacros(PuppyConfig config, BrowserTester browser, DummyValues dummy, List<TestResult> results) throws Exception {
        Path folder = config.folder().resolve(config.setting("browser.macros", "macros"));
        int repeat = Integer.parseInt(config.setting("macro.repeat", "1"));
        if (java.nio.file.Files.isDirectory(folder)) {
            List<Path> macros;
            try (var files = java.nio.file.Files.list(folder)) {
                macros = files.filter(f -> f.toString().endsWith(".txt")).sorted().toList();
            }
            MacroRunner runner = new MacroRunner(browser.driver(), dummy);
            for (Path macro : macros) {
                for (int run = 1; run <= repeat; run++) {
                    TestResult result = runner.run(macro, config.baseUrl(), Map.of(), run == 1 && FrameRecorder.on());
                    results.add(repeat == 1 ? result : new TestResult(result.kind(), result.name() + " #" + run,
                            result.passed(), result.detail(), result.millis()));
                }
            }
        }
    }

    private static void testBrowser(PuppyConfig config, DummyValues dummy, List<TestResult> results,
                                    Connection connection, List<TableInfo> tables) throws Exception {
        List<Map<String, String>> rows = GridFile.read(config.pageCasesFile());
        boolean injection = config.checks("sql-injection") && config.internal();
        if (!rows.isEmpty()) {
            try (BrowserTester browser = new BrowserTester(config.headless(), new PageScanner(), dummy)) {
                InjectionTester injector = new InjectionTester(connection, tables);
                for (Map<String, String> row : rows) {
                    PageCase page = PageCase.fromRow(row);
                    if (config.checks("happy")) {
                        results.add(browser.run(page, config.baseUrl()));
                    }
                    if (config.checks("wrong-input")) {
                        results.addAll(browser.runWrongInputs(page, config.baseUrl()));
                    }
                    if (injection) {
                        results.addAll(injector.testPage(browser, page, config.baseUrl()));
                    }
                }
                runMacros(config, browser, dummy, results);
                if (injection && !config.setting("injection.createPath", "").isBlank()) {
                    results.addAll(injector.testApi(config.baseUrl(), config.setting("injection.readPath", "/"),
                            config.setting("injection.createPath", ""), config.setting("injection.createBody", "")));
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
