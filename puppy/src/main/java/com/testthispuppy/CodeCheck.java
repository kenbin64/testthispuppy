package com.testthispuppy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.rule.RulePriority;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;

/**
 * Checks the code itself, without building or running it: syntax first (files that cannot be read as Java), then
 * structure, likely bugs, practices, security and style, using PMD's established rules (no AI). Writes a fix list,
 * code-fixes.md, that a developer or an AI can work straight from: file, line, the problem, and why it matters.
 *
 * Settings (all optional; leave code.source blank to turn it off):
 *   code.source  a folder, or a Git URL (copied to a temporary folder, read, then deleted)
 *   code.checks  which groups: structure, bugs, practices, security, style   (syntax is always checked)
 *   code.level   quick | standard | thorough: report only the most serious issues, or more, or everything
 *   code.failOn  high | medium | any | never: which issues fail the run (syntax errors fail unless never)
 *   code.skip    folders to leave out, comma separated, relative to the source (for example src/test)
 *   code.ignoreRules  rules that do not apply to this project, by name, comma separated (for example SystemPrintln
 *                for a command-line tool, whose job is printing). The fix list notes what was ignored.
 */
public final class CodeCheck {
    /** The groups a tester can switch on, and PMD's rule categories behind each. */
    static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    static {
        GROUPS.put("structure", List.of("design"));
        GROUPS.put("bugs", List.of("errorprone", "multithreading"));
        GROUPS.put("practices", List.of("bestpractices", "performance"));
        GROUPS.put("security", List.of("security"));
        GROUPS.put("style", List.of("codestyle", "documentation"));
    }
    /** PMD priorities run 1 (high) to 5 (low). The level is the lowest priority reported. */
    static final Map<String, Integer> LEVELS = Map.of("quick", 2, "standard", 3, "thorough", 5);
    static final Map<String, Integer> FAIL_ON = Map.of("high", 2, "medium", 3, "any", 5, "never", 0);
    private static final String[] PRIORITY_WORDS = {"", "high", "medium-high", "medium", "medium-low", "low"};

    /** One issue found. path is relative to the source; priority 0 means the file could not be read at all. */
    record Issue(String group, String path, int line, int priority, String rule, String message, String why, String link) {
    }

    private CodeCheck() {
    }

    public static List<TestResult> run(PuppyConfig config) throws IOException, InterruptedException {
        String source = config.setting("code.source", "").trim();
        if (source.isBlank()) {
            return List.of();
        }
        String level = config.setting("code.level", "standard").trim().toLowerCase();
        String failOn = config.setting("code.failOn", "high").trim().toLowerCase();
        List<String> groups = list(config.setting("code.checks", "structure,bugs,practices,security"));
        List<String> unknown = groups.stream().filter(g -> !GROUPS.containsKey(g)).toList();
        if (!LEVELS.containsKey(level) || !FAIL_ON.containsKey(failOn) || !unknown.isEmpty()) {
            return List.of(new TestResult("code", "check the code", false, "settings: code.level is quick, standard or thorough; "
                    + "code.failOn is high, medium, any or never; code.checks is any of " + GROUPS.keySet()
                    + (unknown.isEmpty() ? "" : "; not known: " + unknown), 0));
        }
        long start = System.currentTimeMillis();
        Path temp = null;
        try {
            Path root;
            if (isGitUrl(source)) {
                temp = Files.createTempDirectory("puppy-code-");
                System.out.println("  check the code: copying " + source);
                if (!gitClone(source, temp)) {
                    return List.of(new TestResult("code", "check the code", false, "could not copy " + source + " (is Git installed, and the URL right?)", 0));
                }
                root = temp;
            } else {
                root = config.folder().resolve(source).normalize();
                if (!Files.isDirectory(root)) {
                    return List.of(new TestResult("code", "check the code", false, "no such folder: " + root, 0));
                }
            }
            System.out.println("  check the code: " + String.join(", ", groups) + " (" + level + ") in " + root);
            List<String> ignored = Arrays.stream(config.setting("code.ignoreRules", "").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            List<Issue> issues = analyze(root, groups, LEVELS.get(level), list(config.setting("code.skip", ""))).stream()
                    .filter(i -> !ignored.contains(i.rule())).toList();
            Path fixes = config.folder().resolve("code-fixes.md");
            String md = fixList(source, groups, level, issues) + (ignored.isEmpty() ? "" : "\nRules ignored by this project's settings: " + String.join(", ", ignored) + "\n");
            Files.writeString(fixes, md, StandardCharsets.UTF_8);
            System.out.println("  check the code: " + issues.size() + " issue(s); fix list written to " + fixes);
            return results(issues, groups, FAIL_ON.get(failOn), System.currentTimeMillis() - start);
        } finally {
            if (temp != null) {
                deleteFolder(temp);   // the copy of someone else's code is not kept
            }
        }
    }

    /** Runs PMD over root with the chosen groups, reporting priorities 1..maxPriority, skipping the skip folders. */
    static List<Issue> analyze(Path root, List<String> groups, int maxPriority, List<String> skip) {
        PMDConfiguration pmd = new PMDConfiguration();
        pmd.setDefaultLanguageVersion(LanguageRegistry.PMD.getLanguageById("java").getVersion("21"));
        pmd.addInputPath(root);
        pmd.setMinimumPriority(RulePriority.valueOf(maxPriority));
        pmd.setIgnoreIncrementalAnalysis(true);
        for (String group : groups) {
            GROUPS.get(group).forEach(category -> pmd.addRuleSet("category/java/" + category + ".xml"));
        }
        List<Issue> issues = new ArrayList<>();
        try (PmdAnalysis analysis = PmdAnalysis.create(pmd)) {
            Report report = analysis.performAnalysisAndCollectReport();
            for (Report.ProcessingError error : report.getProcessingErrors()) {
                String path = relative(root, error.getFileId().getAbsolutePath());
                issues.add(new Issue("syntax", path, lineOf(error.getMsg()), 0, "cannot be read as Java", firstLine(error.getMsg()),
                        "Nothing else in this file can be checked, and it will not compile.", ""));
            }
            for (RuleViolation v : report.getViolations()) {
                String category = v.getRule().getRuleSetName().toLowerCase().replace(" ", "");
                String group = GROUPS.entrySet().stream().filter(e -> e.getValue().contains(category)).map(Map.Entry::getKey).findFirst().orElse(category);
                issues.add(new Issue(group, relative(root, v.getFileId().getAbsolutePath()), v.getBeginLine(), v.getRule().getPriority().getPriority(),
                        v.getRule().getName(), v.getDescription(), firstSentence(v.getRule().getDescription()), v.getRule().getExternalInfoUrl()));
            }
        }
        List<String> skipped = skip.stream().map(s -> s.replace('\\', '/').replaceAll("/+$", "") + "/").toList();
        return issues.stream().filter(i -> skipped.stream().noneMatch(s -> i.path().startsWith(s)))
                .sorted(Comparator.comparing(Issue::path).thenComparingInt(Issue::line)).toList();
    }

    /**
     * Report lines: syntax first, then one summary line per group; every issue serious enough to fail the run
     * (priority 1..failAt) gets its own line. The full list is in code-fixes.md.
     */
    static List<TestResult> results(List<Issue> issues, List<String> groups, int failAt, long millis) {
        List<TestResult> results = new ArrayList<>();
        List<Issue> unreadable = issues.stream().filter(i -> i.priority() == 0).toList();
        results.add(new TestResult("code", "syntax: " + unreadable.size() + " file(s) could not be read as Java", unreadable.isEmpty() || failAt == 0,
                unreadable.isEmpty() ? "every file parsed" : String.join("; ", unreadable.stream().map(i -> i.path() + ": " + i.message()).toList()), millis));
        for (String group : groups) {
            List<Issue> found = issues.stream().filter(i -> i.group().equals(group) && i.priority() > 0).toList();
            long failing = found.stream().filter(i -> i.priority() <= failAt).count();
            results.add(new TestResult("code", group + ": " + found.size() + " issue(s)", failing == 0,
                    failing == 0 ? "none serious enough to fail the run" : failing + " serious enough to fail the run, listed below", 0));
            found.stream().filter(i -> i.priority() <= failAt).forEach(i -> results.add(new TestResult("code",
                    i.path() + ":" + i.line() + " " + i.rule(), false, PRIORITY_WORDS[i.priority()] + ": " + i.message(), 0)));
        }
        return results;
    }

    /** The fix list, in Markdown: written to be handed to a developer or pasted to an AI as it is. */
    static String fixList(String source, List<String> groups, String level, List<Issue> issues) {
        StringBuilder md = new StringBuilder("# Code fix list\n\n");
        md.append("Source: ").append(source).append("  \nChecks: syntax, ").append(String.join(", ", groups)).append(" (").append(level).append(")  \n")
                .append("Found: ").append(issues.size()).append(" issue(s)\n\n")
                .append("How to use this list: fix each item at its file and line without changing what the code does, most serious first, ")
                .append("then run TestThisPuppy again (its tests and the test-the-tests check show whether behaviour changed). ")
                .append("An item that is right as it stands can be left, with a comment saying why.\n");
        Map<String, List<Issue>> byFile = new TreeMap<>();
        issues.forEach(i -> byFile.computeIfAbsent(i.path(), k -> new ArrayList<>()).add(i));
        for (var file : byFile.entrySet()) {
            md.append("\n## ").append(file.getKey()).append("\n\n");
            for (Issue i : file.getValue().stream().sorted(Comparator.comparingInt(Issue::priority).thenComparingInt(Issue::line)).toList()) {
                md.append("- **line ").append(i.line()).append("**, ").append(i.priority() == 0 ? "syntax" : PRIORITY_WORDS[i.priority()])
                        .append(", ").append(i.group()).append(": ").append(i.rule()).append(". ").append(i.message()).append('\n')
                        .append("  - Why: ").append(i.why()).append(i.link().isBlank() ? "" : " ([rule](" + i.link() + "))").append('\n');
            }
        }
        return md.toString();
    }

    static boolean isGitUrl(String source) {
        return source.matches("(?i)^(https?://|git@|ssh://).*") || source.toLowerCase().endsWith(".git");
    }

    private static boolean gitClone(String url, Path into) throws IOException, InterruptedException {
        Process git = new ProcessBuilder("git", "clone", "--depth", "1", "--quiet", url, into.toString()).inheritIO().start();
        return git.waitFor() == 0;
    }

    private static void deleteFolder(Path folder) throws IOException {
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                p.toFile().setWritable(true);    // Git marks some of its files read-only
                Files.deleteIfExists(p);
            }
        }
    }

    private static List<String> list(String commaSeparated) {
        return Arrays.stream(commaSeparated.split(",")).map(String::trim).map(String::toLowerCase).filter(s -> !s.isEmpty()).toList();
    }

    private static String relative(Path root, String absolute) {
        return root.toAbsolutePath().relativize(Path.of(absolute)).toString().replace('\\', '/');
    }

    private static int lineOf(String message) {
        var m = java.util.regex.Pattern.compile("line (\\d+)").matcher(String.valueOf(message));
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    private static String firstLine(String text) {
        return String.valueOf(text).strip().split("\\R", 2)[0];
    }

    private static String firstSentence(String text) {
        String flat = String.valueOf(text).strip().replaceAll("\\s+", " ");
        int end = flat.indexOf(". ");
        return end < 0 ? flat : flat.substring(0, end + 1);
    }
}
