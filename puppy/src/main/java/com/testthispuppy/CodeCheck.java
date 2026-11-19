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
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.rule.RulePriority;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;

/**
 * Checks the code itself: syntax first (files that cannot be read), then structure, likely bugs, practices,
 * security and style with established rule engines (no AI), and libraries with known vulnerabilities. Writes a
 * fix list, code-fixes.md, that a developer or an AI can work straight from: file, line, the problem, and why.
 *
 * Settings (all optional; leave code.source blank to turn it off):
 *   code.source       a folder, or a Git URL (copied to a temporary folder, read, then deleted)
 *   code.language     auto | java | javascript   (auto picks whichever has more source files)
 *   code.checks       which groups: structure, bugs, practices, security, style, libraries   (syntax is always checked)
 *   code.engines      pmd, spotbugs   (spotbugs: Java, local Maven projects only, because it needs a build)
 *   code.level        quick | standard | thorough: only the most serious issues, more, or everything
 *   code.failOn       high | medium | any | never: which issues fail the run (syntax errors fail unless never)
 *   code.skip         folders to leave out, comma separated, relative to the source (for example src/test)
 *   code.ignoreRules  rules that do not apply to this project, by name, comma separated
 */
public final class CodeCheck {
    /** The groups a tester can switch on, and PMD's rule categories behind each (the same names for both languages). */
    static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    static {
        GROUPS.put("structure", List.of("design"));
        GROUPS.put("bugs", List.of("errorprone", "multithreading"));
        GROUPS.put("practices", List.of("bestpractices", "performance"));
        GROUPS.put("security", List.of("security"));
        GROUPS.put("style", List.of("codestyle", "documentation"));
        GROUPS.put("libraries", List.of());
        GROUPS.put("css", List.of());          // Stylelint, over the project's .css files, whatever its main language
    }
    /** For each language: PMD's name for it and the file endings that belong to it. */
    static final Map<String, String> PMD_LANGUAGE = Map.of("java", "java", "javascript", "ecmascript");
    static final Map<String, List<String>> ENDINGS = Map.of("java", List.of(".java"), "javascript", List.of(".js", ".mjs", ".cjs"));
    /** Folders that are never source: dependencies, build output, version control. */
    static final Set<String> NEVER = Set.of("node_modules", ".git", "target", "build", "dist", "out", ".gradle", ".idea", "vendor", "coverage");
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
        String language = config.setting("code.language", "auto").trim().toLowerCase();
        List<String> groups = list(config.setting("code.checks", "structure,bugs,practices,security,libraries,css"));
        String enginesSetting = config.setting("code.engines", "").trim();
        List<String> unknown = groups.stream().filter(g -> !GROUPS.containsKey(g)).toList();
        if (!LEVELS.containsKey(level) || !FAIL_ON.containsKey(failOn) || !unknown.isEmpty()
                || !(language.equals("auto") || PMD_LANGUAGE.containsKey(language))) {
            return List.of(new TestResult("code", "check the code", false, "settings: code.level is quick, standard or thorough; "
                    + "code.failOn is high, medium, any or never; code.language is auto, java or javascript; code.checks is any of "
                    + GROUPS.keySet() + (unknown.isEmpty() ? "" : "; not known: " + unknown), 0));
        }
        long start = System.currentTimeMillis();
        Path temp = null;
        try {
            Path root;
            boolean fromUrl = isGitUrl(source);
            if (fromUrl) {
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
            List<String> skip = list(config.setting("code.skip", ""));
            if (language.equals("auto")) {
                language = detectLanguage(root, skip);
            }
            // Default engines per language: Java gets PMD and SpotBugs; JavaScript gets ESLint (PMD's JavaScript parser
            // predates modern syntax and misreads it, so it is only used for JavaScript when asked for by name).
            List<String> engines = list(enginesSetting.isEmpty() ? (language.equals("java") ? "pmd,spotbugs" : "eslint") : enginesSetting);
            System.out.println("  check the code: " + language + " with " + String.join(", ", engines) + ", " + String.join(", ", groups) + " (" + level + ") in " + root);
            List<Issue> issues = new ArrayList<>();
            List<TestResult> notes = new ArrayList<>();
            if (engines.contains("pmd")) {
                issues.addAll(analyze(root, language, groups, LEVELS.get(level), skip));
            }
            if (engines.contains("eslint") && language.equals("javascript")) {
                try {
                    EslintEngine.run(root, skip, sourceFiles(root, "javascript", skip),
                                    Arrays.stream(config.setting("code.globals", "").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList(),   // case kept: names are case-sensitive
                                    config.folder().resolve("eslint.log")).stream()
                            .filter(i -> i.group().equals("syntax") || groups.contains(i.group()) && i.priority() <= LEVELS.get(level)).forEach(issues::add);
                } catch (IOException e) {
                    notes.add(new TestResult("code", "eslint: did not run", false, e.getMessage(), 0));
                }
            }
            // Building runs the project's own build steps, so it is allowed only for a local folder the tester controls.
            boolean mayBuild = !fromUrl && !"off".equalsIgnoreCase(config.setting("code.build", "on").trim());
            if (engines.contains("spotbugs") && language.equals("java")) {
                if (!mayBuild || !Files.exists(root.resolve("pom.xml"))) {
                    notes.add(new TestResult("code", "spotbugs: not run", true, !mayBuild
                            ? "SpotBugs needs a build, and this code may not be built (code from a URL, or code.build=off)"
                            : "SpotBugs needs a Maven project (no pom.xml at the source)", 0));
                } else {
                    System.out.println("  check the code: SpotBugs (building " + root.getFileName() + ")");
                    try {
                        SpotBugsEngine.run(root, config.folder().resolve("spotbugs.log")).stream()
                                .filter(i -> groups.contains(i.group()) && i.priority() <= LEVELS.get(level) && notInSkipped(i, skip)).forEach(issues::add);
                    } catch (IOException e) {
                        notes.add(new TestResult("code", "spotbugs: did not run", false, e.getMessage(), 0));
                    }
                }
            }
            if (groups.contains("css") && StylelintEngine.hasCss(root, skip)) {
                System.out.println("  check the code: CSS (Stylelint)");
                try {
                    StylelintEngine.run(root, skip, config.folder().resolve("stylelint.log")).stream()
                            .filter(i -> i.group().equals("syntax") || i.priority() <= LEVELS.get(level)).forEach(issues::add);
                } catch (IOException e) {
                    notes.add(new TestResult("code", "css: not checked", false, e.getMessage(), 0));
                }
            }
            if (groups.contains("libraries")) {
                System.out.println("  check the code: libraries (OSV-Scanner)");
                try {
                    List<Issue> libraries = LibraryCheck.scan(root, mayBuild);
                    issues.addAll(libraries);
                    libraries.stream().filter(i -> i.rule().equals("LibraryScanIncomplete")).findFirst()
                            .ifPresent(i -> notes.add(new TestResult("code", "libraries: may be incomplete", false, i.message(), 0)));
                    if (!mayBuild && Files.exists(root.resolve("pom.xml"))) {
                        notes.add(new TestResult("code", "libraries: may be incomplete", true, "Maven versions set by a parent POM can only be "
                                + "worked out by running Maven, which is not done for this code; libraries without a version in pom.xml were not checked", 0));
                    }
                } catch (IOException e) {
                    notes.add(new TestResult("code", "libraries: not checked", false, e.getMessage(), 0));
                }
            }
            List<String> ignored = Arrays.stream(config.setting("code.ignoreRules", "").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            issues = issues.stream().filter(i -> !ignored.contains(i.rule()))
                    .sorted(Comparator.comparing(Issue::path).thenComparingInt(Issue::line)).toList();
            Path fixes = config.folder().resolve("code-fixes.md");
            String md = fixList(source, groups, level, issues) + (ignored.isEmpty() ? "" : "\nRules ignored by this project's settings: " + String.join(", ", ignored) + "\n");
            Files.writeString(fixes, md, StandardCharsets.UTF_8);
            System.out.println("  check the code: " + issues.size() + " issue(s); fix list written to " + fixes);
            List<TestResult> results = new ArrayList<>(results(issues, groups, FAIL_ON.get(failOn), System.currentTimeMillis() - start));
            results.addAll(notes);
            return results;
        } finally {
            if (temp != null) {
                deleteFolder(temp);   // the copy of someone else's code is not kept
            }
        }
    }

    /** The language with more source files under root (Java when tied). */
    static String detectLanguage(Path root, List<String> skip) throws IOException {
        long java = sourceFiles(root, "java", skip).size(), javascript = sourceFiles(root, "javascript", skip).size();
        return javascript > java ? "javascript" : "java";
    }

    /** The source files of one language: never dependencies or build output, never minified files, never skipped folders. */
    static List<Path> sourceFiles(Path root, String language, List<String> skip) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(f -> ENDINGS.get(language).stream().anyMatch(e -> f.getFileName().toString().endsWith(e)))
                    .filter(f -> !f.getFileName().toString().contains(".min."))
                    .filter(f -> {
                        Path rel = root.relativize(f);
                        for (Path part : rel) {
                            if (NEVER.contains(part.toString())) {
                                return false;
                            }
                        }
                        String path = rel.toString().replace('\\', '/');
                        return skip.stream().noneMatch(s -> path.startsWith(s.replace('\\', '/').replaceAll("/+$", "") + "/"));
                    }).toList();
        }
    }

    /** Runs PMD over the language's source files with the chosen groups, reporting priorities 1..maxPriority. */
    static List<Issue> analyze(Path root, String language, List<String> groups, int maxPriority, List<String> skip) throws IOException {
        List<Path> files = sourceFiles(root, language, skip);
        List<String> categories = groups.stream().flatMap(g -> GROUPS.get(g).stream()).toList();
        List<Issue> issues = new ArrayList<>();
        if (files.isEmpty() || categories.isEmpty()) {
            return issues;
        }
        PMDConfiguration pmd = new PMDConfiguration();
        var pmdLanguage = LanguageRegistry.PMD.getLanguageById(PMD_LANGUAGE.get(language));
        pmd.setDefaultLanguageVersion(language.equals("java") ? pmdLanguage.getVersion("21") : pmdLanguage.getDefaultVersion());
        files.forEach(pmd::addInputPath);
        pmd.setMinimumPriority(RulePriority.valueOf(maxPriority));
        pmd.setIgnoreIncrementalAnalysis(true);
        categories.forEach(category -> pmd.addRuleSet("category/" + PMD_LANGUAGE.get(language) + "/" + category + ".xml"));
        try (PmdAnalysis analysis = PmdAnalysis.create(pmd)) {
            Report report = analysis.performAnalysisAndCollectReport();
            for (Report.ProcessingError error : report.getProcessingErrors()) {
                String path = relative(root, error.getFileId().getAbsolutePath());
                if (isParseError(error.getError())) {
                    // PMD names the file by its full path; the issue already says which file, so drop it.
                    String message = firstLine(error.getMsg()).replaceAll(" in file '[^']*'", "").replace("ParseException: Parse exception", "Parse error");
                    issues.add(new Issue("syntax", path, lineOf(error.getMsg()), 0, "cannot be read as " + language, message,
                            "Nothing else in this file can be checked" + (language.equals("java") ? ", and it will not compile." : "."), ""));
                } else {        // one of PMD's own rules failed on this file: a tool problem, not a problem in the code
                    issues.add(new Issue("tool", path, 0, 5, "RuleFailed", firstLine(error.getMsg()),
                            "A PMD rule failed on this file; that says nothing about the code. The other rules still ran.", ""));
                }
            }
            for (RuleViolation v : report.getViolations()) {
                String category = v.getRule().getRuleSetName().toLowerCase().replace(" ", "");
                String group = GROUPS.entrySet().stream().filter(e -> e.getValue().contains(category)).map(Map.Entry::getKey).findFirst().orElse(category);
                issues.add(new Issue(group, relative(root, v.getFileId().getAbsolutePath()), v.getBeginLine(), v.getRule().getPriority().getPriority(),
                        v.getRule().getName(), v.getDescription(), firstSentence(v.getRule().getDescription()), v.getRule().getExternalInfoUrl()));
            }
        }
        return issues;
    }

    /** A parse failure (the file is not valid code) as opposed to a rule crashing inside PMD. */
    static boolean isParseError(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String name = t.getClass().getSimpleName();
            if (name.contains("ParseException") || name.contains("LexException") || name.contains("TokenMgrError")) {
                return true;
            }
        }
        return false;
    }

    private static boolean notInSkipped(Issue issue, List<String> skip) {
        return skip.stream().noneMatch(s -> issue.path().startsWith(s.replace('\\', '/').replaceAll("/+$", "") + "/"));
    }

    /**
     * Report lines: syntax first, then one summary line per group; every issue serious enough to fail the run
     * (priority 1..failAt) gets its own line. The full list is in code-fixes.md.
     */
    static List<TestResult> results(List<Issue> issues, List<String> groups, int failAt, long millis) {
        List<TestResult> results = new ArrayList<>();
        List<Issue> unreadable = issues.stream().filter(i -> i.priority() == 0).toList();
        results.add(new TestResult("code", "syntax: " + unreadable.size() + " file(s) could not be read", unreadable.isEmpty() || failAt == 0,
                unreadable.isEmpty() ? "every file parsed" : String.join("; ", unreadable.stream().limit(10).map(i -> i.path() + ": " + i.message()).toList())
                        + (unreadable.size() > 10 ? "; and " + (unreadable.size() - 10) + " more in code-fixes.md" : ""), millis));
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
                md.append("- **").append(i.line() > 0 ? "line " + i.line() : "whole file").append("**, ")
                        .append(i.priority() == 0 ? "syntax" : PRIORITY_WORDS[i.priority()])
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
