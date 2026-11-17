package com.testthispuppy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The reports are written, including text with characters that break formatting or HTML. */
class ReporterTest {

    @Test
    void writesTheHtmlReportEvenWithPercentAndHtmlCharactersInResults() throws Exception {
        Path file = Files.createTempFile("report", ".html");
        List<TestResult> results = List.of(
                new TestResult("http", "100% of <customers> & \"orders\"", true, "fine", 5),
                new TestResult("wrong", "bad email", false, "the app SAVED the wrong value", 7));
        Reporter.writeHtml(results, file);
        String page = Files.readString(file);
        assertTrue(page.contains("100% of &lt;customers&gt; &amp; &quot;orders&quot;"), "text is escaped, not run as HTML");
        assertTrue(page.contains("<b>1</b>passed") && page.contains("<b>1</b>failed"));
        assertTrue(page.contains("width:100%;"), "the stylesheet survives formatting (the first demo crashed here)");
    }
}
