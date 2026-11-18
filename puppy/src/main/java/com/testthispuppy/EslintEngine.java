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
 * The JavaScript engine: ESLint, the standard JavaScript checker, with its recommended rules and a modern parser.
 * It is installed once, at a pinned version, into TestThisPuppy's own tool folder (~/.testthispuppy), and run with a
 * settings file kept there too, so nothing is ever written into the project being checked. Needs Node.js and npm.
 */
final class EslintEngine {
    static final String VERSION = "10.14.0";
    static final Path TOOL = Path.of(System.getProperty("user.home"), ".testthispuppy", "eslint-" + VERSION);
    /** Rules whose findings are likely real bugs (wrong results or crashes), rated higher than the rest. */
    static final Set<String> BUG_RULES = Set.of("no-undef", "no-unreachable", "no-dupe-keys", "no-dupe-args", "no-dupe-class-members",
            "no-dupe-else-if", "no-duplicate-case", "no-func-assign", "no-cond-assign", "valid-typeof", "no-self-assign", "no-const-assign",
            "use-isnan", "no-unsafe-negation", "no-unsafe-finally", "no-unsafe-optional-chaining", "no-import-assign", "no-setter-return",
            "getter-return", "constructor-super", "no-this-before-super", "no-class-assign", "no-compare-neg-zero", "no-sparse-arrays",
            "no-unexpected-multiline", "no-loss-of-precision", "no-invalid-regexp", "no-obj-calls", "for-direction", "no-fallthrough",
            "no-async-promise-executor", "no-constant-binary-expression", "no-ex-assign", "no-new-native-nonconstructor", "no-global-assign");

    private EslintEngine() {
    }

    /**
     * files: the project's JavaScript files. Files with import or export are checked as modules; the rest as browser
     * scripts, which share one global scope: a name declared at the top of one script is known in all of them.
     * extraGlobals: names provided from outside the project (a library loaded from a CDN, like THREE).
     */
    static List<CodeCheck.Issue> run(Path root, List<String> skip, List<Path> files, List<String> extraGlobals, Path log)
            throws IOException, InterruptedException {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        install(windows ? "npm.cmd" : "npm", log);
        List<String> modules = new ArrayList<>();
        Set<String> globals = new java.util.TreeSet<>(extraGlobals);
        for (Path f : files) {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            if (MODULE.matcher(text).find()) {
                modules.add(root.relativize(f).toString().replace('\\', '/'));
            } else {
                globals.addAll(topLevelNames(text));
            }
        }
        Path config = TOOL.resolve("eslint.config.mjs");
        Files.writeString(config, config(skip, globals, modules), StandardCharsets.UTF_8);
        Process p = new ProcessBuilder("node", TOOL.resolve("node_modules/eslint/bin/eslint.js").toString(), "--config", config.toString(),
                "--format", "json", ".").directory(root.toFile()).redirectError(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
        String json = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        if (exit > 1) {                         // 0 clean, 1 problems found, 2 ESLint itself failed
            throw new IOException("ESLint did not run (exit " + exit + "); see " + log.getFileName());
        }
        return read(json, root);
    }

    /** Installs the pinned ESLint into the tool folder the first time; later runs reuse it. */
    private static void install(String npm, Path log) throws IOException, InterruptedException {
        if (Files.exists(TOOL.resolve("node_modules/eslint/bin/eslint.js"))) {
            return;
        }
        Files.createDirectories(TOOL);
        Files.writeString(TOOL.resolve("package.json"), "{\"name\":\"testthispuppy-eslint\",\"private\":true,\"type\":\"module\"}\n");
        System.out.println("  check the code: installing ESLint " + VERSION + " (first run only)");
        int exit = new ProcessBuilder(npm, "install", "--no-audit", "--no-fund", "eslint@" + VERSION, "@eslint/js@10.0.1", "globals@17.13.0")
                .directory(TOOL.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start().waitFor();
        if (exit != 0) {
            throw new IOException("could not install ESLint with npm (exit " + exit + "); is Node.js installed? See " + log.getFileName());
        }
    }

    static final java.util.regex.Pattern MODULE = java.util.regex.Pattern.compile("(?m)^\\s*(import\\s|import\\{|export\\s|export\\{)");
    /** A declaration at the start of a line (top level of a script), or window.NAME = ..., anywhere. */
    static final java.util.regex.Pattern TOP_LEVEL = java.util.regex.Pattern.compile(
            "(?m)^(?:var|let|const|function\\*?|async\\s+function|class)\\s+([A-Za-z_$][\\w$]*)|\\bwindow\\.([A-Za-z_$][\\w$]*)\\s*=[^=]");

    /** The names a browser script makes global: its top-level declarations and anything it assigns to window. */
    static Set<String> topLevelNames(String script) {
        Set<String> names = new java.util.TreeSet<>();
        var m = TOP_LEVEL.matcher(script);
        while (m.find()) {
            names.add(m.group(1) != null ? m.group(1) : m.group(2));
        }
        return names;
    }

    /**
     * ESLint's settings: its recommended rules and the latest JavaScript; browser scripts share globals (the project's
     * own top-level names, plus browser and Node built-ins) and are not flagged for top-level names used elsewhere;
     * module files are checked as modules. Folders that are never source, and skipped folders, are left out.
     */
    static String config(List<String> skip, Set<String> projectGlobals, List<String> moduleFiles) {
        StringBuilder ignores = new StringBuilder("\"**/*.min.js\"");
        for (String never : CodeCheck.NEVER) {
            ignores.append(", \"**/").append(never).append("/**\"");
        }
        for (String s : skip) {
            ignores.append(", \"").append(s.replace('\\', '/').replaceAll("/+$", "")).append("/**\"");
        }
        StringBuilder known = new StringBuilder();
        projectGlobals.forEach(g -> known.append(" \"").append(g).append("\": \"writable\","));
        StringBuilder modules = new StringBuilder();
        moduleFiles.forEach(f -> modules.append(modules.isEmpty() ? "" : ", ").append('"').append(f.replace("\"", "")).append('"'));
        return "import js from \"@eslint/js\";\nimport globals from \"globals\";\nexport default [\n"
                + "  { ignores: [" + ignores + "] },\n  js.configs.recommended,\n"
                + "  { languageOptions: { ecmaVersion: \"latest\", sourceType: \"script\", globals: { ...globals.browser, ...globals.node,"
                + known + " } },\n    rules: { \"no-unused-vars\": [\"error\", { vars: \"local\" }], \"no-redeclare\": [\"error\", { builtinGlobals: false }] } },\n"
                + (moduleFiles.isEmpty() ? "" : "  { files: [" + modules + "], languageOptions: { sourceType: \"module\" }, rules: { \"no-unused-vars\": \"error\" } },\n")
                + "  { files: [\"**/*.mjs\"], languageOptions: { sourceType: \"module\" } },\n"
                + "  { files: [\"**/*.cjs\"], languageOptions: { sourceType: \"commonjs\" } },\n];\n";
    }

    /** ESLint's JSON report as issues. A message without a rule is a file ESLint could not parse: a syntax error. */
    static List<CodeCheck.Issue> read(String json, Path root) throws IOException {
        List<CodeCheck.Issue> issues = new ArrayList<>();
        if (json.isBlank()) {
            return issues;
        }
        for (JsonNode file : new ObjectMapper().readTree(json)) {
            String path = root.toAbsolutePath().relativize(Path.of(file.path("filePath").asText())).toString().replace('\\', '/');
            for (JsonNode m : file.path("messages")) {
                String rule = m.path("ruleId").asText("");
                int line = m.path("line").asInt(0);
                if (m.path("fatal").asBoolean(false)) {
                    issues.add(new CodeCheck.Issue("syntax", path, line, 0, "cannot be read as JavaScript", m.path("message").asText(),
                            "Nothing else in this file can be checked, and the browser or Node will refuse to run it.", ""));
                } else if (rule.isEmpty() || "null".equals(rule)) {     // ESLint's own notes, such as an unused disable comment
                    issues.add(new CodeCheck.Issue("practices", path, line, 4, "eslint-comment", m.path("message").asText(),
                            "A leftover comment that switches a rule off where it is no longer needed.", ""));
                } else {
                    boolean bug = BUG_RULES.contains(rule);
                    issues.add(new CodeCheck.Issue(bug ? "bugs" : "practices", path, line, bug ? 2 : 3, rule, m.path("message").asText(),
                            bug ? "ESLint rates this a likely bug: code that gives wrong results or throws when it runs."
                                    : "ESLint's recommended rules flag this as a mistake or dead code.",
                            "https://eslint.org/docs/latest/rules/" + rule));
                }
            }
        }
        return issues;
    }
}
