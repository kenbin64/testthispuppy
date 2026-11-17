package com.testthispuppy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests the recorder's file handling (the recording itself needs a person at the browser). */
class MacroRecorderTest {

    @Test
    void readsRecordedStepsFromTheBrowsersJson() {
        String json = "[{\"action\":\"open\",\"target\":\"/customers/new\",\"value\":\"\"},"
                + "{\"action\":\"type\",\"target\":\"name=email\",\"value\":\"a@b.test\"}]";
        List<Map<String, String>> steps = MacroRecorder.parse(json);
        assertEquals(2, steps.size());
        assertEquals("name=email", steps.get(1).get("target"));
        assertEquals("a@b.test", steps.get(1).get("value"));
    }

    @Test
    void writesARunnableTableAndSkipsPageLoadsCausedBySubmit() throws Exception {
        Path file = Files.createTempDirectory("rec").resolve("flow.txt");
        MacroRecorder.write(file, List.of(
                Map.of("action", "open", "target", "/customers/new", "value", ""),
                Map.of("action", "type", "target", "name=name", "value", "Lorem"),
                Map.of("action", "submit", "target", "", "value", ""),
                Map.of("action", "open", "target", "/customers", "value", "")), URI.create("http://localhost:8080"));
        List<Map<String, String>> rows = GridFile.read(file);
        assertEquals(3, rows.size(), "the page load after submit is not replayed as its own step");
        assertEquals("submit", rows.get(2).get("action"));
        assertFalse(Files.readString(file).contains("open | /customers |"));
        assertTrue(Files.readString(file).startsWith("# Recorded from http://localhost:8080"));
    }
}
