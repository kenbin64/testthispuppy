package com.testthispuppy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The code check on small sample files: syntax, finding issues by group, skipping folders, and pass or fail. */
class CodeCheckTest {

    private static final String SWALLOWS = """
            package sample;
            public class Swallows {
                public int read(String text) {
                    try {
                        return Integer.parseInt(text);
                    } catch (NumberFormatException e) {
                    }
                    return 0;
                }
            }
            """;

    private static void write(Path root, String file, String text) throws Exception {
        Path path = root.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }

    @Test
    void findsSyntaxErrorsAndIssuesInTheirGroups(@TempDir Path root) throws Exception {
        write(root, "src/main/java/sample/Swallows.java", SWALLOWS);
        write(root, "src/main/java/sample/Broken.java", "package sample; public class Broken { void x( { }");
        List<CodeCheck.Issue> issues = CodeCheck.analyze(root, "java", List.of("bugs"), 5, List.of());
        assertTrue(issues.stream().anyMatch(i -> i.group().equals("syntax") && i.path().endsWith("Broken.java")),
                "a file that is not valid Java is reported as a syntax issue");
        assertTrue(issues.stream().anyMatch(i -> i.group().equals("bugs") && i.rule().equals("EmptyCatchBlock") && i.line() == 6),
                "the swallowed exception is found, at its line, in the bugs group");
        assertTrue(issues.stream().allMatch(i -> i.group().equals("syntax") || i.group().equals("bugs")), "only the groups asked for");
    }

    @Test
    void skippedFoldersAreLeftOut(@TempDir Path root) throws Exception {
        write(root, "src/main/java/sample/Swallows.java", SWALLOWS);
        write(root, "src/test/java/sample/Swallows.java", SWALLOWS);
        List<CodeCheck.Issue> all = CodeCheck.analyze(root, "java", List.of("bugs"), 5, List.of());
        List<CodeCheck.Issue> skipped = CodeCheck.analyze(root, "java", List.of("bugs"), 5, List.of("src/test"));
        assertTrue(all.stream().anyMatch(i -> i.path().startsWith("src/test/")));
        assertTrue(skipped.stream().noneMatch(i -> i.path().startsWith("src/test/")));
        assertFalse(skipped.isEmpty(), "the main code is still checked");
    }

    @Test
    void onlyIssuesAtTheFailLevelFailTheRun() {
        List<CodeCheck.Issue> issues = List.of(
                new CodeCheck.Issue("bugs", "A.java", 3, 1, "HighRule", "a serious one", "why", ""),
                new CodeCheck.Issue("bugs", "A.java", 9, 4, "LowRule", "a minor one", "why", ""));
        List<TestResult> failHigh = CodeCheck.results(issues, List.of("bugs"), 2, 0);
        assertTrue(failHigh.get(0).passed(), "no syntax errors");
        assertEquals("bugs: 2 issue(s)", failHigh.get(1).name());
        assertFalse(failHigh.get(1).passed());
        assertEquals(3, failHigh.size(), "the serious issue gets its own line; the minor one is only in the fix list");
        assertTrue(CodeCheck.results(issues, List.of("bugs"), 0, 0).stream().allMatch(TestResult::passed), "failOn=never reports but never fails");
    }

    @Test
    void theFixListGivesFileLineAndWhy() {
        String md = CodeCheck.fixList("../demo-app", List.of("bugs"), "standard", List.of(
                new CodeCheck.Issue("bugs", "src/A.java", 6, 3, "EmptyCatchBlock", "Avoid empty catch blocks", "Swallowing errors hides failures.", "https://x")));
        assertTrue(md.contains("## src/A.java"));
        assertTrue(md.contains("**line 6**, medium, bugs: EmptyCatchBlock. Avoid empty catch blocks"));
        assertTrue(md.contains("Why: Swallowing errors hides failures."));
    }

    @Test
    void checksJavaScriptAndLeavesDependenciesAndMinifiedFilesAlone(@TempDir Path root) throws Exception {
        // == 1 (EqualComparison), an if without braces (IfStmtsMustUseBraces), and a variable never declared (GlobalVariable).
        write(root, "src/game.js", "function move(a) {\n  if (a == 1) score = 2;\n  return a + 1;\n}\n");
        write(root, "src/also.js", "var x = 1;\n");
        write(root, "node_modules/lib/index.js", "eval('1');\n");
        write(root, "src/lib.min.js", "eval('1');\n");
        assertEquals("javascript", CodeCheck.detectLanguage(root, List.of()));
        List<Path> files = CodeCheck.sourceFiles(root, "javascript", List.of());
        assertEquals(2, files.size(), "only src/game.js and src/also.js: not node_modules, not .min.js");
        List<CodeCheck.Issue> issues = CodeCheck.analyze(root, "javascript", List.of("bugs", "practices", "style"), 5, List.of());
        assertTrue(issues.stream().noneMatch(i -> i.group().equals("syntax")), "valid JavaScript parses");
        assertTrue(issues.stream().anyMatch(i -> i.path().equals("src/game.js") && i.rule().equals("EqualComparison") && i.line() == 2),
                "the loose comparison is found in the JavaScript: " + issues);
    }

    @Test
    void readsVulnerableLibrariesFromOsvScanner() throws Exception {
        String json = """
                {"results": [{"source": {"path": "/src/pom.xml"}, "packages": [
                  {"package": {"name": "org.apache.commons:commons-text", "version": "1.9"},
                   "vulnerabilities": [{"id": "GHSA-599f-7c49-w659", "aliases": ["CVE-2022-42889"]}],
                   "groups": [{"max_severity": "9.8"}]}]}]}
                """;
        List<CodeCheck.Issue> issues = LibraryCheck.read(json);
        assertEquals(1, issues.size());
        assertEquals("pom.xml", issues.get(0).path());
        assertEquals(1, issues.get(0).priority(), "a 9.8 score is high");
        assertTrue(issues.get(0).message().contains("commons-text 1.9") && issues.get(0).message().contains("CVE-2022-42889"));
        assertTrue(LibraryCheck.read("").isEmpty());
    }

    @Test
    void anOsvComplaintIsCaughtButItsStandingAdviceIsNot() {
        assertEquals("Error: failed to resolve transitive dependencies: timeout",
                LibraryCheck.complaint(List.of("Scanning dir /src", "Error: failed to resolve transitive dependencies: timeout")));
        assertEquals(null, LibraryCheck.complaint(List.of("Scanned /src/pom.xml file and found 5 packages",
                "Warning: enricher transitivedependency/pomxml may be risky when run on untrusted artifacts.", "No issues found")),
                "the routine advisory and summary lines are not complaints");
    }

    @Test
    void readsSpotBugsAndPlacesEachBugInItsFile(@TempDir Path root) throws Exception {
        write(root, "src/main/java/sample/Swallows.java", SWALLOWS);
        String xml = "<BugCollection><BugInstance type='RV_ABSOLUTE_VALUE_OF_HASHCODE' category='CORRECTNESS' rank='9'>"
                + "<ShortMessage>Bad attempt to compute absolute value of signed 32-bit hashcode</ShortMessage><LongMessage>Bad abs</LongMessage>"
                + "<SourceLine sourcepath='sample/Swallows.java' start='5' end='5'/></BugInstance></BugCollection>";
        List<CodeCheck.Issue> issues = SpotBugsEngine.read(xml, root);
        assertEquals("src/main/java/sample/Swallows.java", issues.get(0).path());
        assertEquals("bugs", issues.get(0).group());
        assertEquals(2, issues.get(0).priority(), "rank 9 is medium-high");
        assertEquals(5, issues.get(0).line());
    }

    @Test
    void readsEslintAndTellsSyntaxErrorsFromRuleFindings(@TempDir Path root) throws Exception {
        String a = root.resolve("src/a.js").toString().replace("\\", "\\\\");
        String json = "[{\"filePath\": \"" + a + "\", \"messages\": ["
                + "{\"ruleId\": \"no-undef\", \"severity\": 2, \"message\": \"'y' is not defined.\", \"line\": 2},"
                + "{\"ruleId\": \"no-unused-vars\", \"severity\": 2, \"message\": \"'u' is assigned a value but never used.\", \"line\": 3},"
                + "{\"ruleId\": null, \"fatal\": true, \"severity\": 2, \"message\": \"Parsing error: Unexpected token }\", \"line\": 9}]}]";
        List<CodeCheck.Issue> issues = EslintEngine.read(json, root);
        assertEquals("src/a.js", issues.get(0).path());
        assertEquals("bugs", issues.get(0).group(), "an undefined name is a likely bug");
        assertEquals("practices", issues.get(1).group());
        assertEquals("syntax", issues.get(2).group(), "a parse failure is a syntax error");
        assertTrue(EslintEngine.config(List.of("tests/"), java.util.Set.of(), List.of()).contains("\"tests/**\""), "skipped folders are passed to ESLint");
    }

    @Test
    void browserScriptsShareTheirTopLevelNames() {
        String script = "const ROOM_WIDTH = 10;\nfunction buildBoard() {\n  const inner = 1;\n}\nclass Peg {}\nwindow.FastTrackCore = {};\nif (window.x == 1) {}\n";
        assertEquals(java.util.Set.of("ROOM_WIDTH", "buildBoard", "Peg", "FastTrackCore"), EslintEngine.topLevelNames(script),
                "top-level declarations and window assignments, not indented locals or comparisons");
        String config = EslintEngine.config(List.of(), java.util.Set.of("THREE", "ROOM_WIDTH"), List.of("src/app.js"));
        assertTrue(config.contains("\"THREE\": \"writable\"") && config.contains("\"ROOM_WIDTH\": \"writable\""));
        assertTrue(config.contains("files: [\"src/app.js\"], languageOptions: { sourceType: \"module\" }"), "module files are checked as modules");
        assertTrue(EslintEngine.MODULE.matcher("import x from './x.js';").find());
        assertFalse(EslintEngine.MODULE.matcher("// import nothing\nconst a = 1;").find());
    }

    @Test
    void readsStylelintAndRatesIgnoredStylesHigher(@TempDir Path root) throws Exception {
        String css = root.resolve("css/site.css").toString().replace("\\", "\\\\");
        String json = "[{\"source\": \"" + css + "\", \"warnings\": ["
                + "{\"line\": 1, \"rule\": \"property-no-unknown\", \"severity\": \"error\", \"text\": \"Unknown property \\\"colr\\\" (property-no-unknown)\"},"
                + "{\"line\": 3, \"rule\": \"block-no-empty\", \"severity\": \"error\", \"text\": \"Empty block (block-no-empty)\"},"
                + "{\"line\": 9, \"rule\": \"CssSyntaxError\", \"severity\": \"error\", \"text\": \"Unclosed block (CssSyntaxError)\"}]}]";
        List<CodeCheck.Issue> issues = StylelintEngine.read(json, root);
        assertEquals("css/site.css", issues.get(0).path());
        assertEquals(2, issues.get(0).priority(), "an unknown property is ignored by the browser: rated higher");
        assertEquals("Unknown property \"colr\"", issues.get(0).message(), "the rule name is not repeated in the message");
        assertEquals(3, issues.get(1).priority());
        assertEquals("syntax", issues.get(2).group());
    }

    @Test
    void findsCssOnlyWhereItIsTheProjectsOwn(@TempDir Path root) throws Exception {
        write(root, "node_modules/lib/x.css", "a{}");
        write(root, "web/app.min.css", "a{}");
        assertFalse(StylelintEngine.hasCss(root, List.of()), "dependencies and minified files do not count");
        write(root, "web/app.css", "a { color: red; }");
        assertTrue(StylelintEngine.hasCss(root, List.of()));
        assertFalse(StylelintEngine.hasCss(root, List.of("web")), "a skipped folder does not count");
        assertEquals("css", QuickCheck.guessLanguage(null, ".card { color: red; margin: 0 }"));
        assertEquals("javascript", QuickCheck.guessLanguage(null, "const card = { color: 'red' };"));
        assertEquals("css", QuickCheck.guessLanguage("site.css", "anything"));
    }

    @Test
    void recognisesGitAddresses() {
        assertTrue(CodeCheck.isGitUrl("https://github.com/kenbin64/testthispuppy.git"));
        assertTrue(CodeCheck.isGitUrl("https://github.com/kenbin64/testthispuppy"));
        assertTrue(CodeCheck.isGitUrl("git@github.com:kenbin64/testthispuppy.git"));
        assertFalse(CodeCheck.isGitUrl("../demo-app"));
        assertFalse(CodeCheck.isGitUrl("C:\\projects\\demo-app"));
    }
}
