package com.testthispuppy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * The quick check: point it at something and it works out what to do. No settings file needed.
 *
 *   check <target> [options]
 *
 * What the target can be:
 *   a snippet       "-" reads code from the keyboard or a pipe; or a single source file. The code is checked
 *                   (syntax first, then bugs and practices). Line numbers match what was given.
 *   a project       a local folder: the code check and its libraries, the project's own unit tests, and test the
 *                   tests (mutation testing, Maven projects with the pitest plugin).
 *   a repository    a Git URL: copied to a temporary folder and only read (code and libraries); its tests run only
 *                   with --trust, because running tests runs the project's code. The copy is deleted afterwards.
 *   a site          any other http(s) address: loaded in a real browser (console errors, same-site links, error
 *                   pages, security headers). Read-only: no forms are submitted.
 * Prefix site:, repo: or code: to say which, when the address is ambiguous.
 *
 * Options: --level quick|standard|thorough  --checks a,b  --language java|javascript  --globals A,B  --skip a,b
 *          --trust  --no-tests  --no-mutation  --xss /path?q=  --settle milliseconds  --out folder
 */
public final class QuickCheck {
    private QuickCheck() {
    }

    /** Runs the check and returns the process exit code: 0 all passed, 1 something failed. */
    public static int run(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("usage: check <snippet file | - | project folder | Git URL | site URL> [options]   (see QuickCheck.java)");
            return 2;
        }
        String target = args[0];
        Map<String, String> options = options(args);
        Path out = Path.of(options.getOrDefault("out", "puppy-check")).toAbsolutePath();
        Files.createDirectories(out);
        String kind = kindOf(target);
        String bare = target.replaceFirst("^(site|repo|code):", "");
        System.out.println("  TestThisPuppy quick check: " + kind + " " + (bare.equals("-") ? "(snippet from input)" : bare));
        List<TestResult> results = switch (kind) {
            case "site" -> site(bare, options, out);
            case "repository" -> repository(bare, options, out);
            case "project" -> project(Path.of(bare).toAbsolutePath(), options, out, true);
            default -> snippet(bare, options, out);
        };
        Reporter.print(results);
        Reporter.write(results, out.resolve("report.json"));
        Reporter.writeHtml(results, out.resolve("report.html"));
        System.out.println("  report: " + out.resolve("report.html") + (Files.exists(out.resolve("code-fixes.md")) ? "   fix list: " + out.resolve("code-fixes.md") : ""));
        return results.stream().allMatch(TestResult::passed) ? 0 : 1;
    }

    /** site, repository, project or snippet. */
    static String kindOf(String target) {
        if (target.startsWith("site:")) {
            return "site";
        }
        if (target.startsWith("repo:")) {
            return "repository";
        }
        String bare = target.replaceFirst("^code:", "");
        if (bare.matches("(?i)^https?://.*")) {
            return isRepositoryUrl(bare) ? "repository" : "site";
        }
        if (bare.matches("(?i)^(git@|ssh://).*")) {
            return "repository";
        }
        return !bare.equals("-") && Files.isDirectory(Path.of(bare)) ? "project" : "snippet";
    }

    /** An address ending in .git, or a repository's home on GitHub, GitLab or Bitbucket (owner/name and nothing more). */
    static boolean isRepositoryUrl(String url) {
        return url.toLowerCase().endsWith(".git")
                || url.matches("(?i)^https?://(www\\.)?(github\\.com|gitlab\\.com|bitbucket\\.org)/[\\w.-]+/[\\w.-]+/?$");
    }

    static Map<String, String> options(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                continue;
            }
            String name = args[i].substring(2);
            boolean hasValue = i + 1 < args.length && !args[i + 1].startsWith("--");
            options.put(name, hasValue ? args[++i] : "true");
        }
        return options;
    }

    private static List<TestResult> site(String url, Map<String, String> options, Path out) throws IOException {
        if (options.containsKey("plan")) {
            return plan(url, options, out);
        }
        List<TestResult> results = new ArrayList<>(SiteCheck.check(url, Long.parseLong(options.getOrDefault("settle", "3000")), out));
        if (options.containsKey("xss")) {
            Properties p = new Properties();
            p.setProperty("app.baseUrl", SiteCheck.origin(url));
            p.setProperty("xss.paths", options.get("xss"));
            results.addAll(XssCheck.check(PuppyConfig.of(p, out)));
        }
        return results;
    }

    /**
     * --plan: maps the site (read-only: opens pages, submits nothing) and writes a test plan to <out>/plan, aimed at a
     * copy of the site (--copy, default http://localhost:8080). The plan records the live address as a guard.
     */
    private static List<TestResult> plan(String url, Map<String, String> options, Path out) throws IOException {
        int maxPages = Integer.parseInt(options.getOrDefault("pages", "25"));
        String copy = options.getOrDefault("copy", "http://localhost:8080");
        if (SiteMapper.place(copy).equals(SiteMapper.place(url))) {
            return List.of(new TestResult("plan", "write a test plan", false, "--copy must be a copy of the site, not the site itself (" + copy + ")", 0));
        }
        long start = System.currentTimeMillis();
        System.out.println("  mapping " + url + " (up to " + maxPages + " pages, read only)");
        List<SiteMapper.Page> pages = SiteMapper.map(url, maxPages);
        Path folder = out.resolve("plan");
        PlanWriter.write(folder, url, copy, pages);
        long forms = pages.stream().mapToLong(p -> p.forms().size()).sum();
        long writing = pages.stream().flatMap(p -> p.forms().stream()).filter(f -> f.writes() && !f.signIn()).count();
        return List.of(new TestResult("plan", "map the site: " + pages.size() + " page(s), " + forms + " form(s), " + writing + " that write", true,
                "test plan written to " + folder + " (read PLAN.md first)", System.currentTimeMillis() - start));
    }

    private static List<TestResult> repository(String url, Map<String, String> options, Path out) throws Exception {
        Path copy = Files.createTempDirectory("puppy-repo-");
        try {
            System.out.println("  copying " + url);
            Process git = new ProcessBuilder("git", "clone", "--depth", "1", "--quiet", url, copy.toString()).inheritIO().start();
            if (git.waitFor() != 0) {
                return List.of(new TestResult("code", "copy the repository", false, "could not copy " + url + " (is Git installed, and the address right?)", 0));
            }
            boolean trusted = options.containsKey("trust");
            List<TestResult> results = new ArrayList<>(project(copy, options, out, trusted));
            if (!trusted) {
                results.add(new TestResult("unit", "unit tests: not run", true,
                        "code from a URL is only read; add --trust to run its tests (that runs the project's own code)", 0));
            }
            return results;
        } finally {
            deleteFolder(copy);        // the copy of someone else's code is not kept
        }
    }

    /** A project folder: its code and libraries, then (when it may be run) its unit tests and test the tests. */
    private static List<TestResult> project(Path root, Map<String, String> options, Path out, boolean mayRun) throws Exception {
        Properties p = codeSettings(root.toString(), options);
        if (!mayRun) {
            p.setProperty("code.build", "off");      // code that may not be run is not built either (SpotBugs, Maven)
        }
        List<TestResult> results = new ArrayList<>(CodeCheck.run(PuppyConfig.of(p, out)));
        if (mayRun && !options.containsKey("no-tests")) {
            System.out.println("  running the project's unit tests");
            results.addAll(UnitTestCheck.run(root, out.resolve("unit-tests.log")));
        }
        if (mayRun && !options.containsKey("no-mutation") && Files.exists(root.resolve("pom.xml"))) {
            if (Files.readString(root.resolve("pom.xml")).contains("pitest-maven")) {
                p.setProperty("mutation.project", root.toString());
                p.setProperty("mutation.level", options.getOrDefault("level", "quick").equals("thorough") ? "standard" : "quick");
                results.addAll(MutationCheck.run(PuppyConfig.of(p, out)));
            } else {
                results.add(new TestResult("mutation", "test the tests: not run", true,
                        "add the pitest plugin to pom.xml once to include it (see the GUIDE, \"Test the tests\")", 0));
            }
        }
        return results;
    }

    /** A snippet or a single file: copied alone into a temporary folder and checked there. */
    private static List<TestResult> snippet(String target, Map<String, String> options, Path out) throws Exception {
        String code = target.equals("-") ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8) : Files.readString(Path.of(target));
        String name = target.equals("-") ? null : Path.of(target).getFileName().toString();
        String language = options.getOrDefault("language", guessLanguage(name, code));
        Path folder = Files.createTempDirectory("puppy-snippet-");
        try {
            String file = name != null ? name : language.equals("java") ? "Snippet.java" : language.equals("css") ? "snippet.css" : "snippet.js";
            Files.writeString(folder.resolve(file), language.equals("java") ? wrapJava(code) : code, StandardCharsets.UTF_8);
            Properties p = codeSettings(folder.toString(), options);
            if (language.equals("css")) {               // CSS has its own engine; the Java and JavaScript engines have nothing to read
                p.setProperty("code.language", "javascript");
                p.setProperty("code.engines", "none");
                p.setProperty("code.checks", "css");
            } else {
                p.setProperty("code.language", language);
                p.setProperty("code.engines", options.getOrDefault("engines", language.equals("java") ? "pmd" : "eslint"));   // a snippet is never built
                p.setProperty("code.checks", options.getOrDefault("checks", "structure,bugs,practices,security"));
            }
            return CodeCheck.run(PuppyConfig.of(p, out));
        } finally {
            deleteFolder(folder);
        }
    }

    private static Properties codeSettings(String source, Map<String, String> options) {
        Properties p = new Properties();
        p.setProperty("code.source", source);
        p.setProperty("code.level", options.getOrDefault("level", "standard"));
        p.setProperty("code.failOn", options.getOrDefault("fail-on", "high"));
        for (String key : List.of("checks", "language", "globals", "skip", "engines")) {
            if (options.containsKey(key)) {
                p.setProperty("code." + key, options.get(key));
            }
        }
        return p;
    }

    /** From the file name if there is one, otherwise from what the code looks like. */
    static String guessLanguage(String fileName, String code) {
        if (fileName != null) {
            return fileName.endsWith(".java") ? "java" : fileName.endsWith(".css") ? "css" : "javascript";
        }
        // CSS: selector blocks of property: value; with no JavaScript or Java keywords.
        if (code.matches("(?s)\\s*[^{}]+\\{\\s*[-\\w]+\\s*:[^{}]*}.*") && !code.matches("(?s).*\\b(function|const|let|var|return|class|public)\\b.*")) {
            return "css";
        }
        boolean java = code.matches("(?s).*\\b(public|private|protected)\\s+(static\\s+)?[\\w<>\\[\\]]+\\s+\\w+\\s*\\(.*")
                || code.matches("(?s).*\\b(class|interface|record|enum)\\s+\\w+.*") && code.contains(";") && !code.contains("=>")
                || code.matches("(?s).*\\bString\\s+\\w+\\s*=.*") || code.contains("System.out");
        return java ? "java" : "javascript";
    }

    /**
     * Makes a Java fragment a file the parser accepts, keeping every line where it was: a whole class is left alone;
     * methods get a class around them; loose statements get a class and a method. The wrapper is written on the
     * same first and last lines as the snippet, so reported line numbers match the snippet.
     */
    static String wrapJava(String code) {
        if (code.matches("(?s).*\\b(class|interface|record|enum)\\s+\\w+.*\\{.*")) {
            return code;
        }
        boolean hasMethods = code.matches("(?s)\\s*(@\\w+\\s*)*((public|private|protected|static|final|synchronized)\\s+)*[\\w<>\\[\\],\\s]+\\s+\\w+\\s*\\([^)]*\\)\\s*(throws [\\w., ]+)?\\s*\\{.*");
        return hasMethods ? "class Snippet { " + code + "\n}" : "class Snippet { void snippet() throws Exception { " + code + "\n}}";
    }

    private static void deleteFolder(Path folder) throws IOException {
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                p.toFile().setWritable(true);
                Files.deleteIfExists(p);
            }
        }
    }
}
