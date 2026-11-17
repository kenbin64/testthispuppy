package com.testthispuppy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Runs a macro: a multi-step browser workflow written as a table, one step per row.
 *
 *   action        | target            | value
 *   open          | /customers/new    |
 *   type          | name=email        | {dummy:email}
 *   submit        |                   |
 *   expect-text   |                   | Saved customer
 *
 * Actions: open, type, select (a value, a label, first or last), check, click, submit, expect-text, expect-no-text,
 *          expect-url, remember (save an element's text as {var:name}), no-validate (switch off the browser's own
 *          checks, as an attacker would, so the server is what gets tested), wait, screenshot.
 * Targets: name=..., id=..., text=... (a button or link by its words), xpath=..., or any CSS selector.
 * Values: {dummy:email|name|phone|text|number|date}, {var:x} (saved by remember), {table.column} (seeded data).
 * A failed step stops the macro, saves a screenshot next to the macro file, and reports the step.
 */
public final class MacroRunner {
    private static final Pattern DUMMY = Pattern.compile("\\{dummy:(\\w+)}");
    private static final Pattern VAR = Pattern.compile("\\{var:(\\w+)}");
    private final WebDriver driver;
    private final DummyValues dummy;
    private final Map<String, String> variables = new HashMap<>();

    public MacroRunner(WebDriver driver, DummyValues dummy) {
        this.driver = driver;
        this.dummy = dummy;
    }

    public TestResult run(Path macroFile, String baseUrl, Map<String, List<Map<String, Object>>> seeded) {
        long start = System.currentTimeMillis();
        String name = macroFile.getFileName().toString().replaceFirst("\\.txt$", "");
        TestResult result;
        int number = 0;
        String step = "";
        try {
            List<Map<String, String>> steps = GridFile.read(macroFile);
            for (Map<String, String> row : steps) {
                number++;
                String action = row.getOrDefault("action", "").toLowerCase();
                String target = fill(row.getOrDefault("target", ""), seeded);
                String value = fill(row.getOrDefault("value", ""), seeded);
                step = action + (target.isEmpty() ? "" : " " + target) + (value.isEmpty() ? "" : " \"" + value + "\"");
                perform(action, target, value, baseUrl);
            }
            result = new TestResult("macro", name, true, steps.size() + " steps passed", System.currentTimeMillis() - start);
        } catch (Exception e) {
            Path shot = screenshot(macroFile.resolveSibling(name + "-failed.png"));
            result = new TestResult("macro", name, false, "step " + number + " (" + step + ") failed: " + firstLine(e.getMessage())
                    + (shot == null ? "" : "; screenshot " + shot.getFileName()), System.currentTimeMillis() - start);
        }
        return result;
    }

    private void perform(String action, String target, String value, String baseUrl) throws Exception {
        switch (action) {
            case "open" -> driver.get(baseUrl + target);
            case "type" -> type(find(target), value);
            case "select" -> choose(find(target), value);
            case "check" -> {
                WebElement box = find(target);
                if (!box.isSelected()) {
                    box.click();
                }
            }
            case "click" -> clickAndSettle(find(target));
            case "submit" -> clickAndSettle(driver.findElement(By.cssSelector("button[type=submit], input[type=submit]")));
            case "expect-text" -> require(body().contains(value), "page does not contain \"" + value + "\"");
            case "expect-no-text" -> require(!body().contains(value), "page should not contain \"" + value + "\"");
            case "expect-url" -> require(driver.getCurrentUrl().contains(value), "address is " + driver.getCurrentUrl());
            case "remember" -> variables.put(value, find(target).getText().trim());
            case "no-validate" -> ((JavascriptExecutor) driver).executeScript(
                    "document.querySelectorAll('form').forEach(f => f.noValidate = true);");
            case "wait" -> Thread.sleep(Long.parseLong(value.isEmpty() ? "500" : value));
            case "screenshot" -> screenshot(Path.of(value.isEmpty() ? "macro-step.png" : value));
            default -> throw new IllegalArgumentException("unknown action \"" + action + "\"");
        }
    }

    private WebElement find(String target) {
        By by;
        if (target.startsWith("name=")) {
            by = By.name(target.substring(5));
        } else if (target.startsWith("id=")) {
            by = By.id(target.substring(3));
        } else if (target.startsWith("xpath=")) {
            by = By.xpath(target.substring(6));
        } else if (target.startsWith("text=")) {
            String words = target.substring(5).replace("'", "\\'");
            by = By.xpath("//*[self::button or self::a or self::input[@type='submit']][normalize-space(.)='" + words + "' or @value='" + words + "']");
        } else {
            by = By.cssSelector(target);
        }
        return new WebDriverWait(driver, Duration.ofSeconds(5)).until(d -> d.findElement(by));
    }

    // Date and number boxes only accept typed values in the browser's own format, so those are set directly.
    private void type(WebElement element, String value) {
        String type = String.valueOf(element.getAttribute("type"));
        if (type.equals("date") || type.equals("number")) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];", element, value);
        } else {
            element.clear();
            element.sendKeys(value);
        }
    }

    // "first" and "last" pick the first or last real option; anything else is matched by value, then by label.
    private void choose(WebElement element, String value) {
        Select select = new Select(element);
        List<WebElement> real = select.getOptions().stream().filter(o -> !String.valueOf(o.getAttribute("value")).isBlank()).toList();
        if (value.equals("first") || value.equals("last")) {
            require(!real.isEmpty(), "the dropdown has no options");
            select.selectByValue(real.get(value.equals("first") ? 0 : real.size() - 1).getAttribute("value"));
        } else if (real.stream().anyMatch(o -> value.equals(o.getAttribute("value")))) {
            select.selectByValue(value);
        } else {
            select.selectByVisibleText(value);
        }
    }

    private void clickAndSettle(WebElement element) {
        String before = driver.getCurrentUrl();
        element.click();
        try {
            new WebDriverWait(driver, Duration.ofSeconds(3)).until(d -> !d.getCurrentUrl().equals(before));
        } catch (org.openqa.selenium.TimeoutException stayedOnPage) {
            // Not every click leaves the page; that is fine.
        }
    }

    private String fill(String text, Map<String, List<Map<String, Object>>> seeded) {
        Matcher dummies = DUMMY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (dummies.find()) {
            String kind = dummies.group(1);
            String made = switch (kind) {
                case "email" -> dummy.forInput("email", "email");
                case "phone" -> dummy.forInput("tel", "phone");
                case "number" -> dummy.forInput("number", "quantity");
                case "date" -> dummy.forInput("date", "date");
                case "name" -> dummy.forInput("text", "name");
                default -> dummy.forInput("text", kind);
            };
            dummies.appendReplacement(out, Matcher.quoteReplacement(made));
        }
        dummies.appendTail(out);
        Matcher vars = VAR.matcher(out.toString());
        StringBuilder done = new StringBuilder();
        while (vars.find()) {
            vars.appendReplacement(done, Matcher.quoteReplacement(variables.getOrDefault(vars.group(1), vars.group(0))));
        }
        vars.appendTail(done);
        return Placeholders.fill(done.toString(), seeded);
    }

    private String body() {
        return driver.findElement(By.tagName("body")).getText();
    }

    private static void require(boolean condition, String problem) {
        if (!condition) {
            throw new IllegalStateException(problem);
        }
    }

    private Path screenshot(Path file) {
        Path saved;
        try {
            Files.write(file, ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
            saved = file;
        } catch (Exception e) {
            saved = null;
        }
        return saved;
    }

    private static String firstLine(String text) {
        String line = text == null ? "" : text.strip().split("\\R", 2)[0];
        return line.length() > 160 ? line.substring(0, 160) + "..." : line;
    }
}
