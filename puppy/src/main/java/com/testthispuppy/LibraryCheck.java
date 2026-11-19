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

    /**
     * Returns the vulnerable libraries as issues, or throws IOException with a plain reason if the scan could not run.
     * mayBuild: allowed to run Maven. A Maven pom.xml often leaves versions to a parent POM (Spring Boot does), and
     * OSV-Scanner then skips those libraries and says "no issues found". So for a Maven project, Maven writes its
     * effective POM (every version filled in) to a temporary folder, and that is what gets scanned.
     */
    static List<CodeCheck.Issue> scan(Path root, boolean mayBuild) throws IOException, InterruptedException {
        List<CodeCheck.Issue> issues = new ArrayList<>(osv(root, true));
        if (mayBuild && java.nio.file.Files.exists(root.resolve("pom.xml"))) {
            Path temp = java.nio.file.Files.createTempDirectory("puppy-pom-");
            try {
                String mvn = System.getProperty("os.name", "").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";
                int exit = new ProcessBuilder(mvn, "-q", "org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom",
                        "-Doutput=" + temp.resolve("pom.xml")).directory(root.toFile()).redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
                if (exit == 0 && java.nio.file.Files.exists(temp.resolve("pom.xml"))) {
                    issues.removeIf(i -> i.path().equals("pom.xml"));     // replaced by the complete scan below
                    osv(temp, false).forEach(i -> issues.add(new CodeCheck.Issue(i.group(), "pom.xml", i.line(), i.priority(), i.rule(), i.message(), i.why(), i.link())));
                }
            } finally {
                try (var files = java.nio.file.Files.walk(temp)) {
                    for (Path f : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                        java.nio.file.Files.deleteIfExists(f);
                    }
                }
            }
        }
        return issues;
    }

    /**
     * One OSV-Scanner run. Working out which libraries a library pulls in needs the network; when that lookup fails,
     * OSV-Scanner still answers, with fewer libraries. Seen once on 2026-11-18: 1 library reported instead of 16. So its
     * messages are kept: on an error or failure it is run again once, and if that also complains, a "tool" issue says
     * the list may be incomplete and quotes it, so a short list never passes for a clean one.
     */
    private static List<CodeCheck.Issue> osv(Path folder, boolean recursive) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("docker", "run", "--rm", "-v", folder.toAbsolutePath() + ":/src:ro", IMAGE,
                "scan", "source", "--format", "json"));
        command.addAll(recursive ? List.of("-r", "/src") : List.of("-L", "/src/pom.xml"));
        String complaint = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            Path messages = java.nio.file.Files.createTempFile("puppy-osv-", ".log");
            try {
                Process p = new ProcessBuilder(command).redirectError(messages.toFile()).start();
                String json = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int exit = p.waitFor();
                if (exit != 0 && exit != 1) {          // 1 means "vulnerabilities found"; anything else is a failed scan
                    throw new IOException("OSV-Scanner did not run (exit " + exit + "); is Docker running and the internet reachable?");
                }
                complaint = complaint(java.nio.file.Files.readAllLines(messages, StandardCharsets.UTF_8));
                if (complaint == null || attempt == 2) {
                    List<CodeCheck.Issue> issues = new ArrayList<>(read(json));
                    if (complaint != null) {
                        issues.add(new CodeCheck.Issue("tool", recursive ? "(project)" : "pom.xml", 0, 5, "LibraryScanIncomplete",
                                "the library list may be incomplete: OSV-Scanner reported \"" + complaint + "\"",
                                "Libraries that other libraries pull in are looked up online; when that fails, fewer are checked.", ""));
                    }
                    return issues;
                }
            } finally {
                java.nio.file.Files.deleteIfExists(messages);
            }
        }
        return List.of();
    }

    /** The first message line that reports an error or failure, leaving out OSV-Scanner's standing advisory about enrichers. */
    static String complaint(List<String> messages) {
        return messages.stream().map(String::strip)
                .filter(l -> l.toLowerCase().matches(".*\\b(error|failed|failure|unable|timeout|timed out)\\b.*"))
                .filter(l -> !l.contains("may be risky"))
                .findFirst().orElse(null);
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
