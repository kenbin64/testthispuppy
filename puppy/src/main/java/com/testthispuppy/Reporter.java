package com.testthispuppy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** Prints the results as a table and writes them to a JSON file for other tools to read. */
public final class Reporter {
    private Reporter() {
    }

    public static void print(List<TestResult> results) {
        System.out.println();
        for (TestResult r : results) {
            System.out.printf("  %-4s  %-8s %-34s %5d ms  %s%n", r.passed() ? "PASS" : "FAIL", r.kind(), r.name(), r.millis(), r.detail());
        }
        long passed = results.stream().filter(TestResult::passed).count();
        System.out.printf("%n  %d passed, %d failed%n%n", passed, results.size() - passed);
    }

    public static void write(List<TestResult> results, Path file) throws IOException {
        String items = results.stream().map(r -> String.format(
                "    {\"kind\": %s, \"name\": %s, \"passed\": %b, \"millis\": %d, \"detail\": %s}",
                json(r.kind()), json(r.name()), r.passed(), r.millis(), json(r.detail()))).collect(Collectors.joining(",\n"));
        long passed = results.stream().filter(TestResult::passed).count();
        String text = "{\n  \"ranAt\": " + json(LocalDateTime.now().withNano(0).toString()) + ",\n  \"passed\": " + passed
                + ",\n  \"failed\": " + (results.size() - passed) + ",\n  \"results\": [\n" + items + "\n  ]\n}\n";
        Files.writeString(file, text);
    }

    private static String json(String text) {
        String safe = text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
        return "\"" + safe + "\"";
    }
}
