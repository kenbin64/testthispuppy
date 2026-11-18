package com.testthispuppy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Tests the TESTS ("mutation testing", with PIT). It plants small bugs in the app's code, one at a time (flips a
 * comparison, drops a call, changes a return value), reruns the app's own tests, and counts how many bugs the tests
 * catch. A bug that survives is a gap: the code could break that way and every test would still pass.
 *
 * Settings (all optional; leave mutation.project blank to turn it off):
 *   mutation.project      the folder of the Maven project to check (it needs the pitest plugin in its pom.xml)
 *   mutation.target       which classes get planted bugs, e.g. com.example.*   (default: the project's own package)
 *   mutation.level        quick | standard | thorough   (how many kinds of bug to plant; default standard)
 *   mutation.minScore     the % of planted bugs the tests must catch to pass (default 80)
 *   mutation.excludeTests tests to leave out, comma separated (for example ones that start a database)
 * The full PIT output goes to mutation.log; every surviving bug is listed in the report with its file and line.
 */
public final class MutationCheck {
    static final String PIT = "org.pitest:pitest-maven:mutationCoverage";
    static final Map<String, String> LEVELS = Map.of("quick", "DEFAULTS", "standard", "STRONGER", "thorough", "ALL");

    private MutationCheck() {
    }

    public static List<TestResult> run(PuppyConfig config) throws IOException, InterruptedException {
        String projectSetting = config.setting("mutation.project", "");
        if (projectSetting.isBlank()) {
            return List.of();
        }
        Path project = config.folder().resolve(projectSetting).normalize();
        String level = config.setting("mutation.level", "standard").trim().toLowerCase();
        if (!LEVELS.containsKey(level)) {
            return List.of(new TestResult("mutation", "test the tests", false,
                    "mutation.level must be quick, standard or thorough, not \"" + level + "\"", 0));
        }
        int minScore = Integer.parseInt(config.setting("mutation.minScore", "80").trim());
        List<String> command = command(level, config.setting("mutation.target", ""), config.setting("mutation.excludeTests", ""));
        System.out.println("  test the tests: planting bugs in " + project + " (" + level + ")");
        long start = System.currentTimeMillis();
        int exit = runLogged(command, project, config.folder().resolve("mutation.log"));
        long millis = System.currentTimeMillis() - start;
        Path report = project.resolve("target/pit-reports/mutations.xml");
        if (exit != 0 || !Files.exists(report)) {
            return List.of(new TestResult("mutation", "test the tests", false,
                    "PIT did not finish (exit " + exit + "); see mutation.log. The app's own tests must pass first.", millis));
        }
        return readReport(Files.readString(report), minScore, millis);
    }

    /** The Maven command line for one run. Settings left blank fall back to the project's own pitest settings. */
    static List<String> command(String level, String target, String excludeTests) {
        List<String> command = new ArrayList<>(List.of(isWindows() ? "mvn.cmd" : "mvn", "-q", "test-compile", PIT,
                "-Dmutators=" + LEVELS.get(level), "-DoutputFormats=XML,HTML", "-DtimestampedReports=false", "-Dthreads=4"));
        if (!target.isBlank()) {
            command.add("-DtargetClasses=" + target.trim());
        }
        if (!excludeTests.isBlank()) {
            command.add("-DexcludedTestClasses=" + excludeTests.replace(" ", ""));
        }
        return command;
    }

    /** Runs the command in folder, writing everything to log and echoing PIT's summary lines (">>") as they come. */
    private static int runLogged(List<String> command, Path folder, Path log) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(folder.toFile()).redirectErrorStream(true).start();
        try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             var writer = Files.newBufferedWriter(log, StandardCharsets.UTF_8)) {
            for (String line; (line = out.readLine()) != null; ) {
                writer.write(line);
                writer.newLine();
                if (line.startsWith(">>")) {
                    System.out.println("    " + line);
                }
            }
        }
        return process.waitFor();
    }

    /**
     * Turns PIT's mutations.xml into report lines: one score line (passes at minScore % or better), then one line
     * per planted bug the tests did not catch. Bugs in code no test reaches count as not caught.
     */
    static List<TestResult> readReport(String xml, int minScore, long millis) {
        NodeList mutations;
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);      // a broken report becomes a failed check below, not noise on the console
            mutations = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getElementsByTagName("mutation");
        } catch (Exception e) {
            return List.of(new TestResult("mutation", "test the tests", false, "could not read the PIT report: " + e.getMessage(), millis));
        }
        List<String[]> missed = new ArrayList<>();
        int caught = 0, counted = 0;
        for (int i = 0; i < mutations.getLength(); i++) {
            Element m = (Element) mutations.item(i);
            String status = m.getAttribute("status");
            if ("NON_VIABLE".equals(status)) {
                continue;                       // a bug that would not even compile or load says nothing about the tests
            }
            counted++;
            if ("KILLED".equals(status) || "TIMED_OUT".equals(status) || "MEMORY_ERROR".equals(status)) {
                caught++;
            } else {
                missed.add(new String[] {"not caught: " + text(m, "sourceFile") + ":" + text(m, "lineNumber"),
                        text(m, "mutatedMethod") + "(): " + text(m, "description")
                                + ("NO_COVERAGE".equals(status) ? " (no test runs this line)" : " (tests ran it but did not notice)")});
            }
        }
        // The score decides: at or above minScore the check passes and the bugs not caught are listed as things to
        // look at; below it, they are failures, because together they are why it failed.
        int score = counted == 0 ? 100 : Math.round(100f * caught / counted);
        boolean passed = score >= minScore;
        List<TestResult> results = new ArrayList<>();
        results.add(new TestResult("mutation", "test the tests: " + caught + " of " + counted + " planted bugs caught (" + score + "%)",
                passed, "needs " + minScore + "% to pass; each bug not caught is listed below", millis));
        missed.forEach(m -> results.add(new TestResult("mutation", m[0], passed, m[1], 0)));
        return results;
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "?" : nodes.item(0).getTextContent();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
