package com.testthispuppy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/** DRILL 3 grader: run with  mvn -q -pl puppy test -Dtest=PageScannerTest  (opens a hidden Chrome) */
class PageScannerTest {
    private static WebDriver driver;
    private static Map<String, FormField> byName;
    private static List<FormField> fields;

    @BeforeAll
    static void scanATestPage() throws IOException {
        Path page = Files.createTempFile("puppy-scan", ".html");
        Files.writeString(page, """
                <form>
                  <input type="text" name="name">
                  <input type="email" name="email">
                  <input name="nickname">
                  <input type="hidden" name="token" value="x">
                  <input type="checkbox" name="agree">
                  <input type="text">
                  <select name="size"><option value="">Pick one</option><option value="S">Small</option><option value="L">Large</option></select>
                  <textarea name="notes"></textarea>
                  <input type="submit" name="go" value="Go">
                  <button type="submit">Save</button>
                </form>""");
        driver = new ChromeDriver(new ChromeOptions().addArguments("--headless=new"));
        driver.get(page.toUri().toString());
        fields = new PageScanner().scan(driver);
        byName = fields.stream().collect(Collectors.toMap(FormField::name, f -> f));
    }

    @AfterAll
    static void closeBrowser() {
        driver.quit();
    }

    @Test
    void findsEveryFieldAPersonCouldFillIn() {
        assertEquals(List.of("name", "email", "nickname", "agree", "size", "notes"), fields.stream().map(FormField::name).toList(),
                "in page order, and nothing else");
    }

    @Test
    void skipsHiddenSubmitAndUnnamedInputs() {
        assertTrue(!byName.containsKey("token") && !byName.containsKey("go"));
    }

    @Test
    void recordsEachInputsType() {
        assertEquals("text", byName.get("name").type());
        assertEquals("email", byName.get("email").type());
        assertEquals("text", byName.get("nickname").type(), "an input with no type is a text box");
        assertEquals("checkbox", byName.get("agree").type());
    }

    @Test
    void recordsTagsAndSelectOptions() {
        assertEquals("select", byName.get("size").tag());
        assertEquals(List.of("S", "L"), byName.get("size").options(), "the empty 'Pick one' option is left out");
        assertEquals("textarea", byName.get("notes").tag());
        assertEquals(List.of(), byName.get("name").options());
    }
}
