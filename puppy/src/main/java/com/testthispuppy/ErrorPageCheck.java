package com.testthispuppy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks that error pages do not show the app's insides: it causes a few errors on purpose (a page that does not
 * exist, and a broken value on the injection read path if one is set) and fails if the answer contains a stack
 * trace. A stack trace tells an attacker the frameworks, versions, classes and file paths behind the app.
 *
 * Settings: errors.check (on | off, default on), errors.paths (extra paths to try, comma separated).
 */
public final class ErrorPageCheck {
    /** A Java stack frame, a JSON "trace" field (Spring), a Python traceback, or a Node.js stack frame. */
    static final Pattern TRACE = Pattern.compile("\\bat [\\w$.]+\\([\\w$]+\\.java:\\d+\\)|\"trace\"\\s*:|Traceback \\(most recent call last\\)|\\bat .+\\.m?js:\\d+:\\d+");

    private ErrorPageCheck() {
    }

    public static List<TestResult> check(PuppyConfig config) {
        if ("off".equalsIgnoreCase(config.setting("errors.check", "on").trim()) || config.baseUrl() == null) {
            return List.of();
        }
        List<TestResult> results = new ArrayList<>();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        for (String path : probes(config)) {
            long start = System.currentTimeMillis();
            try {
                HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                        .header("Accept", "text/html,application/json").timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
                boolean shows = showsStackTrace(response.body());
                results.add(new TestResult("errors", "error page " + path, !shows,
                        "status " + response.statusCode() + (shows ? ": the page shows a stack trace (app internals exposed)" : ": no stack trace shown"),
                        System.currentTimeMillis() - start));
            } catch (Exception e) {
                results.add(new TestResult("errors", "error page " + path, false, "request failed: " + e.getMessage(), System.currentTimeMillis() - start));
            }
        }
        return results;
    }

    static List<String> probes(PuppyConfig config) {
        List<String> paths = new ArrayList<>(List.of("/puppy-no-such-page"));
        String readPath = config.setting("injection.readPath", "");
        if (!readPath.isBlank()) {
            paths.add(readPath + "%27");                     // a lone quote: a common way to make a server fail
        }
        Arrays.stream(config.setting("errors.paths", "").split(",")).map(String::trim).filter(p -> !p.isEmpty()).forEach(paths::add);
        return paths;
    }

    static boolean showsStackTrace(String body) {
        return body != null && TRACE.matcher(body).find();
    }
}
