package com.testthispuppy;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reflected cross-site scripting: appends a harmless, marked piece of HTML to each listed path and fails if the page
 * sends it back unescaped, which means a link could run someone else's script in a visitor's browser. The markers
 * do nothing; they are only looked for in the answer.
 *
 * Setting: xss.paths, comma separated, each ending where the value goes, for example /search?q= or /hello?name=
 */
public final class XssCheck {
    static final List<String> PAYLOADS = List.of(
            "<script>puppyXss7319()</script>",
            "\"><img src=x onerror=puppyXss7319()>",
            "<svg onload=puppyXss7319()>");

    private XssCheck() {
    }

    public static List<TestResult> check(PuppyConfig config) {
        List<String> paths = Arrays.stream(config.setting("xss.paths", "").split(",")).map(String::trim).filter(p -> !p.isEmpty()).toList();
        if (paths.isEmpty() || config.baseUrl() == null) {
            return List.of();
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        List<TestResult> results = new ArrayList<>();
        for (String path : paths) {
            for (String payload : PAYLOADS) {
                long start = System.currentTimeMillis();
                String name = "cross-site scripting: " + path + payload;
                try {
                    String url = config.baseUrl() + path + URLEncoder.encode(payload, StandardCharsets.UTF_8);
                    HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    boolean reflected = reflectsUnescaped(response.body(), payload);
                    results.add(new TestResult("xss", name, !reflected, reflected
                            ? "the page sent the HTML back unescaped (status " + response.statusCode() + "): a link could run a script in a visitor's browser"
                            : "escaped or not echoed (status " + response.statusCode() + ")", System.currentTimeMillis() - start));
                } catch (Exception e) {
                    results.add(new TestResult("xss", name, false, "request failed: " + e.getMessage(), System.currentTimeMillis() - start));
                }
            }
        }
        return results;
    }

    static boolean reflectsUnescaped(String body, String payload) {
        return body != null && body.contains(payload);
    }
}
