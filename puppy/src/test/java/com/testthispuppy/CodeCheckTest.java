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
        List<CodeCheck.Issue> issues = CodeCheck.analyze(root, List.of("bugs"), 5, List.of());
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
        List<CodeCheck.Issue> all = CodeCheck.analyze(root, List.of("bugs"), 5, List.of());
        List<CodeCheck.Issue> skipped = CodeCheck.analyze(root, List.of("bugs"), 5, List.of("src/test"));
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
    void recognisesGitAddresses() {
        assertTrue(CodeCheck.isGitUrl("https://github.com/kenbin64/testthispuppy.git"));
        assertTrue(CodeCheck.isGitUrl("https://github.com/kenbin64/testthispuppy"));
        assertTrue(CodeCheck.isGitUrl("git@github.com:kenbin64/testthispuppy.git"));
        assertFalse(CodeCheck.isGitUrl("../demo-app"));
        assertFalse(CodeCheck.isGitUrl("C:\\projects\\demo-app"));
    }
}
