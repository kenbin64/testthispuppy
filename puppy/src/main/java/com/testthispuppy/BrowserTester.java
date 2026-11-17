package com.testthispuppy;

import java.time.Duration;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Opens a page in a real browser, scans it, fills every field with dummy values, submits it,
 * and checks the page that comes back. Selenium finds and starts the right ChromeDriver itself.
 */
public class BrowserTester implements AutoCloseable {
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

    public TestResult run(PageCase test, String baseUrl) {
        long start = System.currentTimeMillis();
        TestResult result;
        try {
            driver.get(baseUrl + test.path());
            List<FormField> fields = scanner.scan(driver);
            for (FormField field : fields) {
                fill(field);
            }
            String before = driver.getCurrentUrl();
            driver.findElement(By.cssSelector("button[type=submit], input[type=submit]")).click();
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> !d.getCurrentUrl().equals(before)
                    || d.getPageSource().contains(test.expectedText()));
            String page = driver.findElement(By.tagName("body")).getText();
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

    private void fill(FormField field) {
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
            element.clear();
            String value = dummy.forInput(field.type(), field.name());
            if (field.type().equals("date")) {
                // A date box takes typed digits in the browser's own order, so set the ISO value directly.
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];", element, value);
            } else {
                element.sendKeys(value);
            }
        }
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
