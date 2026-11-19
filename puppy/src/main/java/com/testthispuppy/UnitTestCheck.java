package com.testthispuppy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Runs a project's own unit tests and reports them: for Maven, every test's result from Surefire's reports (each
 * failing test gets its own line, with its message); for npm, the "test" script's outcome and the end of its output
 * (JavaScript test runners have no single report format). Running tests runs the project's code, so the quick check
 * only does this for a local folder, or for fetched code when the tester says it is trusted.
 */
final class UnitTestCheck {
    private UnitTestCheck() {
    }

    static List<TestResult> run(Path root, Path log) throws IOException, InterruptedException {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (Files.exists(root.resolve("pom.xml"))) {
            long start = System.currentTimeMillis();
            int exit = new ProcessBuilder(windows ? "mvn.cmd" : "mvn", "-q", "test", "-Dsurefire.failIfNoSpecifiedTests=false")
                    .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start().waitFor();
            List<TestResult> results = surefire(root, System.currentTimeMillis() - start);
            if (results.isEmpty()) {
                results.add(new TestResult("unit", "unit tests", exit == 0, exit == 0 ? "the build passed but no test reports were written (no tests?)"
                        : "the build failed before tests ran (exit " + exit + "); see " + log.getFileName(), System.currentTimeMillis() - start));
            }
            return results;
        }
        if (Files.exists(root.resolve("package.json")) && hasRealTestScript(Files.readString(root.resolve("package.json")))) {
            long start = System.currentTimeMillis();
            ProcessBuilder npm = new ProcessBuilder(windows ? "npm.cmd" : "npm", "test").directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
            npm.environment().put("CI", "true");          // test runners stop watching and exit when CI is set
            int exit = npm.start().waitFor();
            List<String> tail = Files.readAllLines(log, StandardCharsets.UTF_8);
            String end = String.join(" | ", tail.subList(Math.max(0, tail.size() - 6), tail.size())).strip();
            return List.of(new TestResult("unit", "unit tests (npm test)", exit == 0, (exit == 0 ? "passed" : "failed (exit " + exit + ")") + ": " + end,
                    System.currentTimeMillis() - start));
        }
        return List.of(new TestResult("unit", "unit tests", true, "no unit tests found to run (no pom.xml, and no test script in package.json)", 0));
    }

    /** npm's placeholder script ("Error: no test specified") is not a test suite. */
    static boolean hasRealTestScript(String packageJson) {
        try {
            String test = new com.fasterxml.jackson.databind.ObjectMapper().readTree(packageJson).path("scripts").path("test").asText("");
            return !test.isBlank() && !test.contains("no test specified");
        } catch (IOException e) {
            return false;                                 // a package.json that cannot be read has no usable test script
        }
    }

    /** Every Surefire report under root: a summary line, then a line per failing test. */
    static List<TestResult> surefire(Path root, long millis) throws IOException {
        List<Path> reports;
        try (Stream<Path> files = Files.walk(root)) {
            reports = files.filter(f -> f.getFileName().toString().matches("TEST-.*\\.xml") && f.toString().contains("surefire-reports")).toList();
        }
        if (reports.isEmpty()) {
            return new ArrayList<>();
        }
        int total = 0;
        List<TestResult> failures = new ArrayList<>();
        for (Path report : reports) {
            NodeList cases = parse(Files.readString(report)).getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                Element c = (Element) cases.item(i);
                total++;
                Element problem = first(c, "failure") != null ? first(c, "failure") : first(c, "error");
                if (problem != null) {
                    String cls = c.getAttribute("classname");
                    failures.add(new TestResult("unit", cls.substring(cls.lastIndexOf('.') + 1) + "." + c.getAttribute("name"), false,
                            problem.getAttribute("message").isBlank() ? problem.getAttribute("type") : problem.getAttribute("message"), 0));
                }
            }
        }
        List<TestResult> results = new ArrayList<>();
        results.add(new TestResult("unit", "unit tests: " + (total - failures.size()) + " of " + total + " passed", failures.isEmpty(),
                failures.isEmpty() ? "all passed" : failures.size() + " failed, listed below", millis));
        results.addAll(failures);
        return results;
    }

    private static org.w3c.dom.Document parse(String xml) throws IOException {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException("could not read a test report: " + e.getMessage());
        }
    }

    private static Element first(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }
}
