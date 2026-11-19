package com.testthispuppy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The CSS engine: Stylelint with its recommended rules, which flag mistakes (a misspelt property, an invalid value,
 * a duplicate rule, broken syntax), not style preferences: it checks that CSS works, not how a page looks. Installed
 * once, at a pinned version, into ~/.testthispuppy with its settings kept there, so nothing is written into the
 * project. Needs Node.js and npm.
 */
final class StylelintEngine {
    static final String VERSION = "17.16.0";
    static final Path TOOL = Path.of(System.getProperty("user.home"), ".testthispuppy", "stylelint-" + VERSION);
    /** Rules whose findings mean the browser ignores or misreads the CSS: the styling silently does not apply. */
    static final Set<String> BROKEN = Set.of("property-no-unknown", "declaration-property-value-no-unknown", "color-no-invalid-hex",
            "unit-no-unknown", "function-calc-no-unspaced-operator", "at-rule-no-unknown", "media-feature-name-no-unknown",
            "selector-pseudo-class-no-unknown", "selector-pseudo-element-no-unknown", "selector-type-no-unknown", "string-no-newline",
            "declaration-block-no-shorthand-property-overrides", "named-grid-areas-no-invalid", "keyframe-declaration-no-important",
            "function-linear-gradient-no-nonstandard-direction", "annotation-no-unknown", "media-query-no-invalid");
    /** Rules that are worth knowing but rarely break anything. */
    static final Set<String> MINOR = Set.of("no-descending-specificity", "comment-no-empty");

    private StylelintEngine() {
    }

    /** True when root holds CSS files worth checking (not in dependencies or build output, not minified). */
    static boolean hasCss(Path root, List<String> skip) throws IOException {
        try (var files = Files.walk(root)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".css") && !f.getFileName().toString().contains(".min."))
                    .map(f -> root.relativize(f).toString().replace('\\', '/'))
                    .anyMatch(rel -> java.util.Arrays.stream(rel.split("/")).noneMatch(CodeCheck.NEVER::contains)
                            && skip.stream().noneMatch(s -> rel.startsWith(s.replace('\\', '/').replaceAll("/+$", "") + "/")));
        }
    }

    static List<CodeCheck.Issue> run(Path root, List<String> skip, Path log) throws IOException, InterruptedException {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        install(windows ? "npm.cmd" : "npm", log);
        Path config = TOOL.resolve("stylelint.config.json");
        Files.writeString(config, "{\"extends\": [\"stylelint-config-recommended\"]}\n");
        Path report = Files.createTempFile("puppy-stylelint-", ".json");
        try {
            List<String> command = new ArrayList<>(List.of("node", TOOL.resolve("node_modules/stylelint/bin/stylelint.mjs").toString(), "**/*.css",
                    "--config", config.toString(), "--formatter", "json", "--output-file", report.toString(), "--allow-empty-input",
                    "--ignore-pattern", "**/*.min.css"));
            for (String never : CodeCheck.NEVER) {
                command.addAll(List.of("--ignore-pattern", "**/" + never + "/**"));
            }
            for (String s : skip) {
                command.addAll(List.of("--ignore-pattern", s.replace('\\', '/').replaceAll("/+$", "") + "/**"));
            }
            int exit = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start().waitFor();
            if (exit != 0 && exit != 2) {         // 0 clean, 2 problems found; 1 or 78 means Stylelint itself failed
                throw new IOException("Stylelint did not run (exit " + exit + "); see " + log.getFileName());
            }
            return read(Files.readString(report, StandardCharsets.UTF_8), root);
        } finally {
            Files.deleteIfExists(report);
        }
    }

    private static void install(String npm, Path log) throws IOException, InterruptedException {
        if (Files.exists(TOOL.resolve("node_modules/stylelint/bin/stylelint.mjs"))) {
            return;
        }
        Files.createDirectories(TOOL);
        Files.writeString(TOOL.resolve("package.json"), "{\"name\":\"testthispuppy-stylelint\",\"private\":true}\n");
        System.out.println("  check the code: installing Stylelint " + VERSION + " (first run only)");
        int exit = new ProcessBuilder(npm, "install", "--no-audit", "--no-fund", "stylelint@" + VERSION, "stylelint-config-recommended@18.0.0")
                .directory(TOOL.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start().waitFor();
        if (exit != 0) {
            throw new IOException("could not install Stylelint with npm (exit " + exit + "); is Node.js installed? See " + log.getFileName());
        }
    }

    /** Stylelint's JSON report as issues: a CssSyntaxError is a syntax error; rules that mean the CSS is ignored rate higher. */
    static List<CodeCheck.Issue> read(String json, Path root) throws IOException {
        List<CodeCheck.Issue> issues = new ArrayList<>();
        if (json.isBlank()) {
            return issues;
        }
        for (JsonNode file : new ObjectMapper().readTree(json)) {
            String path = root.toAbsolutePath().relativize(Path.of(file.path("source").asText())).toString().replace('\\', '/');
            for (JsonNode w : file.path("warnings")) {
                String rule = w.path("rule").asText();
                String text = w.path("text").asText().replaceAll("\\s*\\(" + java.util.regex.Pattern.quote(rule) + "\\)$", "");
                int line = w.path("line").asInt(0);
                if (rule.equals("CssSyntaxError")) {
                    issues.add(new CodeCheck.Issue("syntax", path, line, 0, "cannot be read as CSS", text,
                            "The browser skips CSS it cannot read, often everything after the mistake in that file.", ""));
                } else {
                    boolean broken = BROKEN.contains(rule);
                    issues.add(new CodeCheck.Issue("css", path, line, broken ? 2 : MINOR.contains(rule) ? 4 : 3, rule, text,
                            broken ? "The browser ignores this, so the style silently does not apply." : "Stylelint's recommended rules flag this as a likely mistake.",
                            "https://stylelint.io/user-guide/rules/" + rule));
                }
            }
        }
        return issues;
    }
}
