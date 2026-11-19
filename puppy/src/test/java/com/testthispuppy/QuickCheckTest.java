package com.testthispuppy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** How the quick check decides what a target is, and how it prepares snippets and reads test results. */
class QuickCheckTest {

    @Test
    void tellsSitesRepositoriesProjectsAndSnippetsApart(@TempDir Path folder) {
        assertEquals("site", QuickCheck.kindOf("https://butterflyfx.us/work/podcaster.html"));
        assertEquals("site", QuickCheck.kindOf("http://localhost:8080"));
        assertEquals("repository", QuickCheck.kindOf("https://github.com/kenbin64/testthispuppy"));
        assertEquals("repository", QuickCheck.kindOf("https://example.com/team/app.git"));
        assertEquals("site", QuickCheck.kindOf("https://github.com/kenbin64/testthispuppy/blob/main/README.md"), "a page on GitHub is a page");
        assertEquals("repository", QuickCheck.kindOf("repo:https://git.internal/shop"), "a prefix settles an ambiguous address");
        assertEquals("site", QuickCheck.kindOf("site:https://github.com/kenbin64/testthispuppy"));
        assertEquals("project", QuickCheck.kindOf(folder.toString()));
        assertEquals("snippet", QuickCheck.kindOf("-"));
        assertEquals("snippet", QuickCheck.kindOf(folder.resolve("Game.java").toString()));
    }

    @Test
    void readsOptionsWithAndWithoutValues() {
        var options = QuickCheck.options(new String[] {"target", "--level", "quick", "--trust", "--globals", "THREE,KG"});
        assertEquals("quick", options.get("level"));
        assertEquals("true", options.get("trust"));
        assertEquals("THREE,KG", options.get("globals"));
    }

    @Test
    void guessesTheLanguageOfASnippet() {
        assertEquals("java", QuickCheck.guessLanguage(null, "public int add(int a, int b) { return a + b; }"));
        assertEquals("java", QuickCheck.guessLanguage(null, "String name = \"x\";\nSystem.out.println(name);"));
        assertEquals("javascript", QuickCheck.guessLanguage(null, "const add = (a, b) => a + b;"));
        assertEquals("javascript", QuickCheck.guessLanguage("game.js", "anything"));
        assertEquals("java", QuickCheck.guessLanguage("Game.java", "anything"));
    }

    @Test
    void wrapsJavaFragmentsWithoutMovingAnyLine() {
        String method = "public int add(int a, int b) {\n  return a + b;\n}";
        String statements = "int x = 1;\nif (x == 1) { x = 2; }";
        String whole = "class Shop {\n  int x;\n}";
        assertEquals(whole, QuickCheck.wrapJava(whole), "a whole class is left alone");
        assertTrue(QuickCheck.wrapJava(method).startsWith("class Snippet { public int add"));
        assertTrue(QuickCheck.wrapJava(statements).startsWith("class Snippet { void snippet() throws Exception { int x = 1;"));
        for (String code : List.of(method, statements)) {
            String[] original = code.split("\n"), wrapped = QuickCheck.wrapJava(code).split("\n");
            for (int i = 1; i < original.length; i++) {
                assertEquals(original[i], wrapped[i], "line " + (i + 1) + " stays on line " + (i + 1));
            }
        }
    }

    @Test
    void aJavaSnippetIsCheckedWithItsOwnLineNumbers(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("Snippet.java"), QuickCheck.wrapJava("int x = 1;\ntry { x = 2; } catch (Exception e) { }\n"));
        List<CodeCheck.Issue> issues = CodeCheck.analyze(root, "java", List.of("bugs"), 5, List.of());
        assertTrue(issues.stream().noneMatch(i -> i.group().equals("syntax")), "the wrapped fragment parses: " + issues);
        assertTrue(issues.stream().anyMatch(i -> i.rule().equals("EmptyCatchBlock") && i.line() == 2), "found on the snippet's own line 2: " + issues);
    }

    @Test
    void knowsARealTestScriptFromNpmsPlaceholder() {
        assertTrue(UnitTestCheck.hasRealTestScript("{\"scripts\": {\"test\": \"node --test\"}}"));
        assertFalse(UnitTestCheck.hasRealTestScript("{\"scripts\": {\"test\": \"echo \\\"Error: no test specified\\\" && exit 1\"}}"));
        assertFalse(UnitTestCheck.hasRealTestScript("{\"name\": \"x\"}"));
    }

    @Test
    void readsSurefireReportsWithEachFailureByName(@TempDir Path root) throws Exception {
        Path reports = Files.createDirectories(root.resolve("target/surefire-reports"));
        Files.writeString(reports.resolve("TEST-com.shop.CartTest.xml"), "<testsuite><testcase classname='com.shop.CartTest' name='addsItems'/>"
                + "<testcase classname='com.shop.CartTest' name='appliesDiscount'><failure message='expected 90 but was 100' type='AssertionError'/></testcase></testsuite>");
        List<TestResult> results = UnitTestCheck.surefire(root, 0);
        assertEquals("unit tests: 1 of 2 passed", results.get(0).name());
        assertEquals("CartTest.appliesDiscount", results.get(1).name());
        assertEquals("expected 90 but was 100", results.get(1).detail());
    }
}
