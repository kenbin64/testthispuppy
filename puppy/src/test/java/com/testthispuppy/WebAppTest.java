package com.testthispuppy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The browser app's form-to-arguments step: everything typed is checked, and nothing typed becomes an option. */
class WebAppTest {
    private final ObjectMapper json = new ObjectMapper();
    @TempDir
    Path folder;

    private ObjectNode req(String kind) {
        return json.createObjectNode().put("kind", kind).put("level", "quick");
    }

    @Test
    void pastedCodeIsSavedAsAFileAndCheckedAsCode() throws Exception {
        List<String> args = WebApp.argsFor(req("snippet").put("code", "class A {}").put("language", "java"), folder);
        assertEquals("code:" + folder.resolve("snippet.java"), args.get(0));
        assertEquals("class A {}", Files.readString(folder.resolve("snippet.java")));
        assertTrue(args.containsAll(List.of("--level", "quick", "--out", folder.toString())));
    }

    @Test
    void emptyCodeAndUnknownLanguageAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> WebApp.argsFor(req("snippet").put("code", "  "), folder));
        assertThrows(IllegalArgumentException.class, () -> WebApp.argsFor(req("snippet").put("code", "x").put("language", "cobol"), folder));
    }

    @Test
    void aFolderMustExist() {
        assertThrows(IllegalArgumentException.class, () -> WebApp.argsFor(req("project").put("target", folder.resolve("nope").toString()), folder));
    }

    @Test
    void aRepositoryRunsItsTestsOnlyWhenTrusted() throws Exception {
        List<String> read = WebApp.argsFor(req("repository").put("target", "https://github.com/a/b"), folder);
        assertEquals("repo:https://github.com/a/b", read.get(0));
        assertTrue(!read.contains("--trust"));
        assertTrue(WebApp.argsFor(req("repository").put("target", "https://github.com/a/b").put("trust", true), folder).contains("--trust"));
    }

    @Test
    void typedTextCannotBecomeAnOption() {
        // A space or option-looking text in an address is refused, so "--trust" cannot be smuggled in.
        assertThrows(IllegalArgumentException.class, () -> WebApp.argsFor(req("repository").put("target", "https://x.com/a --trust"), folder));
        assertThrows(IllegalArgumentException.class, () -> WebApp.argsFor(req("site").put("target", "--plan"), folder));
        assertThrows(IllegalArgumentException.class, () -> WebApp.argsFor(req("site").put("target", "file:///etc/passwd"), folder));
    }

    @Test
    void aSiteWithAPlanNeedsAProperCopyAddress() throws Exception {
        List<String> args = WebApp.argsFor(req("site").put("target", "https://example.com").put("plan", true).put("copy", "http://localhost:9090"), folder);
        assertEquals(List.of("site:https://example.com", "--plan", "--copy", "http://localhost:9090"), args.subList(0, 4));
        assertThrows(IllegalArgumentException.class, () -> WebApp.argsFor(req("site").put("target", "https://e.com").put("plan", true).put("copy", "ftp://x"), folder));
    }

    @Test
    void onlyKnownLevelsAndKinds() {
        assertThrows(IllegalArgumentException.class, () -> WebApp.argsFor(req("snippet").put("code", "x").put("level", "extreme"), folder));
        assertThrows(IllegalArgumentException.class, () -> WebApp.argsFor(req("shell").put("target", "rm -rf /"), folder));
    }

    @Test
    void theLogSplitsPrintedTextIntoLines() {
        List<String> lines = new ArrayList<>();
        PrintStream echo = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        try (PrintStream tee = new PrintStream(new WebApp.LineTee(lines, echo), true, StandardCharsets.UTF_8)) {
            tee.print("one\r\ntwo\nthree");
            tee.flush();
        }
        assertEquals(List.of("one", "two", "three"), lines);
    }
}
