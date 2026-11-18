package com.testthispuppy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The "libraries" group of the code check: finds libraries with known vulnerabilities by running OSV-Scanner
 * (Google's open-source scanner, using the public OSV.dev database) in Docker over the source folder. It reads the
 * project's dependency files (pom.xml, package-lock.json, requirements.txt and others) and reports each vulnerable
 * library once, with its known vulnerability ids. Needs Docker and an internet connection.
 */
final class LibraryCheck {
    static final String IMAGE = "ghcr.io/google/osv-scanner:v2.7.1";
    static final String WHY = "A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version.";

    private LibraryCheck() {
    }

    /** Returns the vulnerable libraries as issues, or throws IOException with a plain reason if the scan could not run. */
    static List<CodeCheck.Issue> scan(Path root) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("docker", "run", "--rm", "-v", root.toAbsolutePath() + ":/src:ro", IMAGE,
                "scan", "source", "-r", "--format", "json", "/src").redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String json = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        if (exit != 0 && exit != 1) {          // 1 means "vulnerabilities found"; anything else is a failed scan
            throw new IOException("OSV-Scanner did not run (exit " + exit + "); is Docker running and the internet reachable?");
        }
        return read(json);
    }

    /** OSV-Scanner's JSON report as issues: one per vulnerable library, rated by its most severe known vulnerability. */
    static List<CodeCheck.Issue> read(String json) throws IOException {
        List<CodeCheck.Issue> issues = new ArrayList<>();
        if (json.isBlank()) {
            return issues;
        }
        JsonNode root = new ObjectMapper().readTree(json);
        for (JsonNode result : root.path("results")) {
            String manifest = result.path("source").path("path").asText("").replaceFirst("^/src/?", "");
            for (JsonNode pkg : result.path("packages")) {
                List<String> ids = new ArrayList<>();
                pkg.path("vulnerabilities").forEach(v -> {
                    String cve = null;
                    for (JsonNode alias : v.path("aliases")) {
                        if (alias.asText().startsWith("CVE-")) {
                            cve = alias.asText();
                        }
                    }
                    ids.add(cve != null ? cve : v.path("id").asText());
                });
                double worst = 0;
                for (JsonNode group : pkg.path("groups")) {
                    try {
                        worst = Math.max(worst, Double.parseDouble(group.path("max_severity").asText("0")));
                    } catch (NumberFormatException ignored) {
                        // a group without a numeric score does not change the rating
                    }
                }
                String name = pkg.path("package").path("name").asText(), version = pkg.path("package").path("version").asText();
                String shown = String.join(", ", ids.subList(0, Math.min(6, ids.size()))) + (ids.size() > 6 ? " and " + (ids.size() - 6) + " more" : "");
                issues.add(new CodeCheck.Issue("libraries", manifest, 0, priorityFor(worst), "VulnerableLibrary",
                        name + " " + version + ": " + ids.size() + " known vulnerabilit" + (ids.size() == 1 ? "y" : "ies") + " (" + shown + ")",
                        WHY, ids.isEmpty() ? "" : "https://osv.dev/" + pkg.path("vulnerabilities").path(0).path("id").asText()));
            }
        }
        return issues;
    }

    /** CVSS score to the code check's priority: 9+ high, 7+ medium-high, 4+ medium, otherwise medium-low. */
    static int priorityFor(double cvss) {
        return cvss >= 9 ? 1 : cvss >= 7 ? 2 : cvss >= 4 ? 3 : 4;
    }
}
