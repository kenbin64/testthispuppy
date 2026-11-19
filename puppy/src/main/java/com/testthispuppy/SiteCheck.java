package com.testthispuppy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

/**
 * Checks a running site or web app at an address, without a database and without changing anything there:
 *   - the page answers, and how fast
 *   - in a real browser (headless Chrome): JavaScript errors on the console while the page loads and settles
 *   - links on the page that point into the same site, and whether each one answers
 *   - error pages: whether they show a stack trace (app internals)
 *   - security headers, listed as recommendations (they are advice, not failures)
 * It only reads: it never submits forms, so it is safe to point at any address.
 */
public final class SiteCheck {
    static final int MAX_LINKS = 30;
    /** Header -> why it is recommended. */
    static final Map<String, String> HEADERS = Map.of(
            "content-security-policy", "limits where scripts may load from, which blunts cross-site scripting",
            "x-content-type-options", "stops the browser guessing file types (set to nosniff)",
            "strict-transport-security", "keeps visitors on HTTPS (only meaningful on an https site)",
            "referrer-policy", "controls how much of the address is passed to other sites");

    private SiteCheck() {
    }

    public static List<TestResult> check(String url, long settleMs) {
        List<TestResult> results = new ArrayList<>();
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> page;
        long start = System.currentTimeMillis();
        try {
            page = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            results.add(new TestResult("site", "the site answers: " + url, false, "no answer: " + e.getMessage(), System.currentTimeMillis() - start));
            return results;
        }
        long ms = System.currentTimeMillis() - start;
        results.add(new TestResult("site", "the site answers: " + url, page.statusCode() < 400,
                "status " + page.statusCode() + " in " + ms + " ms, " + page.body().length() / 1024 + " KB", ms));
        results.addAll(headers(page, url));
        results.addAll(inBrowser(url, settleMs, client));
        java.util.Properties p = new java.util.Properties();
        p.setProperty("app.baseUrl", origin(url));
        results.addAll(ErrorPageCheck.check(PuppyConfig.of(p, java.nio.file.Path.of("."))));
        return results;
    }

    /** One line per recommended security header: present, or missing with the reason to add it (never a failure). */
    static List<TestResult> headers(HttpResponse<?> page, String url) {
        List<TestResult> results = new ArrayList<>();
        for (var h : new java.util.TreeMap<>(HEADERS).entrySet()) {
            if (h.getKey().equals("strict-transport-security") && !url.startsWith("https")) {
                continue;
            }
            boolean present = page.headers().firstValue(h.getKey()).isPresent();
            results.add(new TestResult("headers", h.getKey(), true, present ? "present" : "missing (recommended: " + h.getValue() + ")", 0));
        }
        return results;
    }

    /** Loads the page in headless Chrome, collects console errors, and follows same-site links. */
    static List<TestResult> inBrowser(String url, long settleMs, HttpClient client) {
        List<TestResult> results = new ArrayList<>();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1280,900");
        options.setCapability("goog:loggingPrefs", Map.of("browser", "ALL"));
        WebDriver driver = null;
        long start = System.currentTimeMillis();
        try {
            driver = new ChromeDriver(options);
            driver.get(url);
            Thread.sleep(settleMs);                       // let scripts start, load their files and draw
            List<String> errors = new ArrayList<>();
            for (LogEntry entry : driver.manage().logs().get(LogType.BROWSER)) {
                if ("SEVERE".equals(entry.getLevel().getName())) {
                    errors.add(entry.getMessage());
                }
            }
            String title = driver.getTitle();
            results.add(new TestResult("browser", "page loads in a browser: " + url, true, "title \"" + title + "\"", System.currentTimeMillis() - start));
            results.add(new TestResult("console", "no JavaScript errors while loading", errors.isEmpty(),
                    errors.isEmpty() ? "the console showed no errors" : errors.size() + " error(s): " + String.join(" | ", errors.stream().limit(5).toList()), 0));
            results.addAll(links(driver, url, client));
        } catch (Exception e) {
            results.add(new TestResult("browser", "page loads in a browser: " + url, false, "the browser could not load it: " + firstLine(e.getMessage()),
                    System.currentTimeMillis() - start));
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
        return results;
    }

    /** Same-site links on the page (up to MAX_LINKS): each must answer below 400. */
    static List<TestResult> links(WebDriver driver, String url, HttpClient client) {
        String origin = origin(url);
        Set<String> targets = new LinkedHashSet<>();
        for (WebElement a : driver.findElements(By.cssSelector("a[href]"))) {
            String href = a.getDomProperty("href");          // the browser's absolute form of the link
            if (href != null && href.startsWith(origin) && !href.contains("#") && !href.toLowerCase(Locale.ROOT).startsWith("javascript:")) {
                targets.add(href);
            }
        }
        List<TestResult> results = new ArrayList<>();
        List<String> broken = new ArrayList<>();
        int checked = 0;
        for (String target : targets) {
            if (checked++ >= MAX_LINKS) {
                break;
            }
            try {
                int status = client.send(HttpRequest.newBuilder(URI.create(target)).timeout(Duration.ofSeconds(15)).GET().build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode();
                if (status >= 400) {
                    broken.add(target + " (" + status + ")");
                }
            } catch (Exception e) {
                broken.add(target + " (no answer)");
            }
        }
        results.add(new TestResult("links", "same-site links answer (" + Math.min(targets.size(), MAX_LINKS) + " checked)", broken.isEmpty(),
                broken.isEmpty() ? (targets.isEmpty() ? "no same-site links on the page" : "all answered") : "broken: " + String.join(", ", broken), 0));
        return results;
    }

    static String origin(String url) {
        URI u = URI.create(url);
        return u.getScheme() + "://" + u.getAuthority();
    }

    private static String firstLine(String text) {
        return String.valueOf(text).strip().split("\\R", 2)[0];
    }
}
