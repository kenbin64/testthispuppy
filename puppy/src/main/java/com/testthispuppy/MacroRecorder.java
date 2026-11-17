package com.testthispuppy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Records a macro by watching you use the app. A visible Chrome opens; every page you visit, field you fill,
 * option you choose and button you press becomes a step. Press Enter in this window (or close Chrome) to finish,
 * and the macro is written as a table ready for MacroRunner. Typed values are kept as recorded; replace any
 * with {dummy:email}, {dummy:name} and so on to get fresh data on every run.
 *
 *   mvn -q exec:java -Dexec.mainClass=com.testthispuppy.MacroRecorder -Dexec.args="http://localhost:8080 macros/my-flow.txt"
 */
public final class MacroRecorder {
    // Injected on every page: records into sessionStorage, which survives page changes within the site.
    private static final String LISTENER = """
            if (!window.__puppyListening) {
              window.__puppyListening = true;
              const steps = JSON.parse(sessionStorage.getItem('__puppy') || '[]');
              const save = s => { steps.push(s); sessionStorage.setItem('__puppy', JSON.stringify(steps)); };
              save({action: 'open', target: location.pathname + location.search, value: ''});
              const pick = el => el.name ? 'name=' + el.name : el.id ? 'id=' + el.id
                  : (el.innerText || el.value || '').trim() ? 'text=' + (el.innerText || el.value).trim() : el.tagName.toLowerCase();
              document.addEventListener('change', e => {
                const el = e.target;
                if (el.tagName === 'SELECT') save({action: 'select', target: pick(el), value: el.value});
                else if (el.type === 'checkbox' || el.type === 'radio') { if (el.checked) save({action: 'check', target: pick(el), value: ''}); }
                else if (el.type !== 'password') save({action: 'type', target: pick(el), value: el.value});
                else save({action: 'type', target: pick(el), value: '{dummy:text}'});
              }, true);
              document.addEventListener('click', e => {
                const el = e.target.closest('button, a, input[type=submit], input[type=button]');
                if (el) save({action: el.type === 'submit' ? 'submit' : 'click', target: el.type === 'submit' ? '' : pick(el), value: ''});
              }, true);
            }
            return sessionStorage.getItem('__puppy');
            """;

    private MacroRecorder() {
    }

    public static void main(String[] args) throws Exception {
        String startUrl = args.length > 0 ? args[0] : "http://localhost:8080";
        Path output = Path.of(args.length > 1 ? args[1] : "macros/recorded.txt");
        WebDriver driver = new ChromeDriver();
        driver.get(startUrl);
        System.out.println("Recording. Use the app in the Chrome window, then press Enter here to finish.");
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        String recorded = "[]";
        boolean open = true;
        while (open && !console.ready()) {
            try {
                Object latest = ((JavascriptExecutor) driver).executeScript(LISTENER);
                recorded = latest == null ? recorded : latest.toString();
                Thread.sleep(300);
            } catch (Exception closedOrNavigating) {
                open = isOpen(driver);
            }
        }
        List<Map<String, String>> steps = parse(recorded);
        write(output, steps, URI.create(startUrl));
        System.out.println("Wrote " + steps.size() + " steps to " + output.toAbsolutePath());
        if (isOpen(driver)) {
            driver.quit();
        }
    }

    private static boolean isOpen(WebDriver driver) {
        boolean open;
        try {
            driver.getWindowHandles();
            open = true;
        } catch (Exception e) {
            open = false;
        }
        return open;
    }

    // The recorded steps are a small JSON array of {action, target, value}; read it without a JSON library.
    static List<Map<String, String>> parse(String json) {
        List<Map<String, String>> steps = new ArrayList<>();
        var step = java.util.regex.Pattern.compile("\\{\"action\":\"(.*?)\",\"target\":\"(.*?)\",\"value\":\"(.*?)\"}").matcher(json);
        while (step.find()) {
            steps.add(Map.of("action", unescape(step.group(1)), "target", unescape(step.group(2)), "value", unescape(step.group(3))));
        }
        return steps;
    }

    private static String unescape(String text) {
        return text.replace("\\\"", "\"").replace("\\\\", "\\").replace("|", "/");
    }

    static void write(Path output, List<Map<String, String>> steps, URI start) throws java.io.IOException {
        StringBuilder text = new StringBuilder("# Recorded from " + start + ". Replace typed values with {dummy:...} for fresh data each run.\n");
        text.append("action | target | value\n");
        String previous = "";
        for (Map<String, String> s : steps) {
            // A page load caused by a click or a submit is not a step of its own: replaying the click gets there.
            boolean causedByLastStep = s.get("action").equals("open") && (previous.equals("click") || previous.equals("submit"));
            if (!causedByLastStep) {
                text.append(s.get("action")).append(" | ").append(s.get("target")).append(" | ").append(s.get("value")).append('\n');
            }
            previous = s.get("action");
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, text.toString());
    }
}
