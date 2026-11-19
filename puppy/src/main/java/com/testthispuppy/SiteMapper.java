package com.testthispuppy;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Maps a site, read-only: opens pages in headless Chrome, follows links within the same site (up to a page limit),
 * and reads every form on each page from the page as the browser built it (so forms made by JavaScript count too).
 * Nothing is ever submitted. PlanWriter turns the map into a test plan to run against a copy of the site.
 */
public final class SiteMapper {
    /** One form field. options: a dropdown's values. label: its label or placeholder, for the plan. */
    public record Field(String name, String tag, String type, boolean required, String min, String max, List<String> options, String label) {
    }

    /** One form: where it sends (action, absolute), how (get or post), its fields and its submit button's words. */
    public record Form(String page, String action, String method, List<Field> fields, String submit) {
        boolean writes() {
            return method.equals("post");
        }

        /** A password and at most two other fields (a username or email, perhaps "remember me"). A sign-up form has more. */
        boolean signIn() {
            long passwords = fields.stream().filter(f -> f.type().equals("password")).count();
            return passwords > 0 && fields.size() - passwords <= 2;
        }
    }

    public record Page(String url, String title, List<Form> forms) {
    }

    /** Files that are not pages: never opened. */
    static final Set<String> NOT_PAGES = Set.of("pdf", "jpg", "jpeg", "png", "gif", "webp", "svg", "zip", "mp4", "webm", "mp3", "css", "js", "json", "xml", "ico");

    /** Reads the forms from the live page, as the browser built it. Hidden fields and buttons are left out of the fields. */
    static final String READ_FORMS = """
            return Array.from(document.forms).map(f => ({
              action: new URL(f.getAttribute('action') || location.href, location.href).href,
              method: (f.getAttribute('method') || 'get').toLowerCase(),
              fields: Array.from(f.elements)
                .filter(e => e.name && !['submit', 'button', 'reset', 'image', 'hidden', 'fieldset'].includes((e.type || '').toLowerCase()))
                .map(e => ({
                  name: e.name, tag: e.tagName.toLowerCase(), type: (e.type || '').toLowerCase(), required: !!e.required,
                  min: e.min || '', max: e.max || '',
                  options: e.tagName === 'SELECT' ? Array.from(e.options).map(o => o.value).filter(v => v).slice(0, 20) : [],
                  label: ((e.labels && e.labels[0] ? e.labels[0].innerText : e.placeholder) || '').trim().slice(0, 60)
                })),
              submit: ((f.querySelector('button[type=submit], button:not([type])') || {}).innerText
                       || (f.querySelector('input[type=submit]') || {}).value || '').trim()
            }));
            """;

    private SiteMapper() {
    }

    @SuppressWarnings("unchecked")
    public static List<Page> map(String start, int maxPages) {
        String origin = SiteCheck.origin(start);
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1280,900");
        WebDriver driver = new ChromeDriver(options);
        List<Page> pages = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(withoutFragment(start)));
        try {
            while (!queue.isEmpty() && pages.size() < maxPages) {
                String url = queue.poll();
                if (!seen.add(url)) {
                    continue;
                }
                try {
                    driver.get(url);
                    Thread.sleep(800);                     // let scripts build the page
                    List<Map<String, Object>> raw = (List<Map<String, Object>>) ((JavascriptExecutor) driver).executeScript(READ_FORMS);
                    List<Form> forms = new ArrayList<>();
                    for (Map<String, Object> f : raw) {
                        List<Field> fields = new ArrayList<>();
                        for (Map<String, Object> e : (List<Map<String, Object>>) f.get("fields")) {
                            fields.add(new Field(str(e.get("name")), str(e.get("tag")), str(e.get("type")), Boolean.TRUE.equals(e.get("required")),
                                    str(e.get("min")), str(e.get("max")), ((List<Object>) e.get("options")).stream().map(String::valueOf).toList(), str(e.get("label"))));
                        }
                        forms.add(new Form(url, str(f.get("action")), str(f.get("method")), fields, str(f.get("submit"))));
                    }
                    pages.add(new Page(url, driver.getTitle(), forms));
                    List<String> links = (List<String>) ((JavascriptExecutor) driver).executeScript(
                            "return Array.from(document.querySelectorAll('a[href]')).map(a => a.href);");
                    for (String link : links) {
                        String clean = withoutFragment(link);
                        if (clean.startsWith(origin) && isPage(clean) && !seen.contains(clean)) {
                            queue.add(clean);
                        }
                    }
                } catch (Exception e) {
                    pages.add(new Page(url, "could not be opened: " + e.getClass().getSimpleName(), List.of()));
                }
            }
        } finally {
            driver.quit();
        }
        return pages;
    }

    static boolean isPage(String url) {
        String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
        int dot = path.lastIndexOf('.');
        return dot < 0 || dot < path.lastIndexOf('/') || !NOT_PAGES.contains(path.substring(dot + 1));
    }

    static String withoutFragment(String url) {
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }

    /**
     * The safety guard for generated plans: a plan records the live site it was mapped from (guard.liveUrl), and a run
     * whose app.baseUrl is that same site is refused, because plans submit forms and would write to the live site.
     * Returns the reason to refuse, or null when the run may go ahead.
     */
    public static String guardRefusal(PuppyConfig config) {
        String live = config.setting("guard.liveUrl", "").trim();
        String target = config.baseUrl() == null ? "" : config.baseUrl().trim();
        if (live.isEmpty() || target.isEmpty()) {
            return null;
        }
        return place(live).equals(place(target))
                ? "REFUSED: app.baseUrl (" + target + ") is the live site this plan was mapped from (" + live + "). "
                  + "Plans submit forms, so they run only against a copy: point app.baseUrl at the copy."
                : null;
    }

    /** Host and port, so a copy on the same machine but another port is a different place; www. is ignored. */
    static String place(String url) {
        URI u = URI.create(url.contains("://") ? url : "http://" + url);
        String h = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        int port = u.getPort() != -1 ? u.getPort() : "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
        return h + ":" + port;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
