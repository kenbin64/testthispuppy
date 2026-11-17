package com.testthispuppy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Tests pages in a real browser. Two kinds of check:
 *   happy path   fill every field with a good value, submit, expect it saved
 *   wrong input  for each field in turn, put in a wrong value (the rest stay good), switch off the browser's
 *                own checks the way an attacker would, submit, and expect the server to refuse it cleanly:
 *                not saved, and not a server crash
 * Selenium finds and starts the right ChromeDriver itself.
 */
public class BrowserTester implements AutoCloseable {
    private static final List<String> CRASH_SIGNS = List.of("Internal Server Error", "status=500", "HTTP Status 500");
    private final WebDriver driver;
    private final PageScanner scanner;
    private final DummyValues dummy;

    public BrowserTester(boolean headless, PageScanner scanner, DummyValues dummy) {
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new", "--window-size=1280,900");
        }
        this.driver = new ChromeDriver(options);
        this.scanner = scanner;
        this.dummy = dummy;
    }

    /** Happy path: good values everywhere, and the page must say it saved. */
    public TestResult run(PageCase test, String baseUrl) {
        long start = System.currentTimeMillis();
        TestResult result;
        try {
            driver.get(baseUrl + test.path());
            List<FormField> fields = scanner.scan(driver);
            for (FormField field : fields) {
                fillGood(field);
            }
            String page = submit(test.expectedText());
            boolean passed = page.contains(test.expectedText());
            String detail = "filled " + fields.size() + " fields; " + (passed ? "page said \"" + firstLine(page) + "\""
                    : "page did not contain \"" + test.expectedText() + "\": " + firstLine(page));
            result = new TestResult("browser", test.name(), passed, detail, System.currentTimeMillis() - start);
        } catch (Exception e) {
            result = new TestResult("browser", test.name(), false, "failed: " + firstLine(String.valueOf(e.getMessage())),
                    System.currentTimeMillis() - start);
        }
        return result;
    }

    /** Wrong input: one test per field that has a wrong value to try. */
    public List<TestResult> runWrongInputs(PageCase test, String baseUrl) {
        List<TestResult> results = new ArrayList<>();
        try {
            driver.get(baseUrl + test.path());
            for (FormField target : scanner.scan(driver)) {
                results.addAll(tryWrong(test, baseUrl, target));
            }
        } catch (Exception e) {
            results.add(new TestResult("wrong", test.name(), false, "could not scan the page: " + firstLine(String.valueOf(e.getMessage())), 0));
        }
        return results;
    }

    private List<TestResult> tryWrong(PageCase test, String baseUrl, FormField target) {
        List<TestResult> results = new ArrayList<>();
        long start = System.currentTimeMillis();
        driver.get(baseUrl + test.path());
        boolean required = driver.findElement(By.name(target.name())).getAttribute("required") != null;
        Optional<String> wrong = WrongValues.forField(target, required);
        if (wrong.isPresent()) {
            String name = test.name() + ": " + WrongValues.describe(target, wrong.get());
            TestResult result;
            try {
                Submission submission = submitWith(test, baseUrl, target.name(), wrong.get());
                boolean saved = submission.saved();
                String detail = saved ? "the app SAVED the wrong value"
                        : submission.crashed() ? "the app CRASHED (server error) instead of refusing cleanly"
                        : "refused cleanly: " + firstLine(submission.pageText());
                result = new TestResult("wrong", name, !saved && !submission.crashed(), detail, System.currentTimeMillis() - start);
            } catch (Exception e) {
                result = new TestResult("wrong", name, false, "failed: " + firstLine(String.valueOf(e.getMessage())), System.currentTimeMillis() - start);
            }
            results.add(result);
        }
        return results;
    }

    /** What came back after a submit: the page text, whether it said saved, and whether the server crashed. */
    public record Submission(String pageText, boolean saved, boolean crashed) {
    }

    /** The fields on a page, freshly loaded. */
    public List<FormField> fieldsOn(PageCase test, String baseUrl) {
        driver.get(baseUrl + test.path());
        return scanner.scan(driver);
    }

    /**
     * Opens the page, fills every field with a good value except targetField, which gets value, switches off
     * the browser's own checks the way an attacker would, and submits.
     */
    public Submission submitWith(PageCase test, String baseUrl, String targetField, String value) {
        driver.get(baseUrl + test.path());
        for (FormField field : scanner.scan(driver)) {
            if (field.name().equals(targetField)) {
                fillWrong(field, value);
            } else {
                fillGood(field);
            }
        }
        ((JavascriptExecutor) driver).executeScript("document.querySelectorAll('form').forEach(f => f.noValidate = true);");
        String page = submit(test.expectedText());
        boolean crashed = CRASH_SIGNS.stream().anyMatch(driver.getPageSource()::contains);
        return new Submission(page, page.contains(test.expectedText()), crashed);
    }

    private String submit(String expectedText) {
        String before = driver.getCurrentUrl();
        driver.findElement(By.cssSelector("button[type=submit], input[type=submit]")).click();
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> !d.getCurrentUrl().equals(before)
                || d.getPageSource().contains(expectedText));
        return driver.findElement(By.tagName("body")).getText();
    }

    private void fillGood(FormField field) {
        WebElement element = driver.findElement(By.name(field.name()));
        if (field.tag().equals("select")) {
            if (!field.options().isEmpty()) {
                new Select(element).selectByValue(field.options().get(0));
            }
        } else if (field.type().equals("checkbox") || field.type().equals("radio")) {
            if (!element.isSelected()) {
                element.click();
            }
        } else {
            setValue(element, field, dummy.forInput(field.type(), field.name()));
        }
    }

    private void fillWrong(FormField field, String value) {
        WebElement element = driver.findElement(By.name(field.name()));
        if (field.tag().equals("select")) {
            // Add the option the page never offered, then choose it, exactly as a tampered request would.
            ((JavascriptExecutor) driver).executeScript(
                    "const o = document.createElement('option'); o.value = arguments[1]; o.text = 'tampered'; arguments[0].add(o);",
                    element, value);
            new Select(element).selectByValue(value);
        } else {
            setValue(element, field, value);
        }
    }

    // Typing works for most boxes. A date or number box only accepts a real date or number when typed,
    // so values are set directly, which is also how a wrong value gets past the browser.
    private void setValue(WebElement element, FormField field, String value) {
        if (field.type().equals("date") || field.type().equals("number")) {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].type = 'text'; arguments[0].value = arguments[1];", element, value);
        } else {
            element.clear();
            element.sendKeys(value);
        }
    }

    /** The browser itself, for macros that drive it step by step. */
    public WebDriver driver() {
        return driver;
    }

    private static String firstLine(String text) {
        String line = text == null ? "" : text.strip().split("\\R", 2)[0];
        return line.length() > 160 ? line.substring(0, 160) + "..." : line;
    }

    @Override
    public void close() {
        driver.quit();
    }
}
