package com.testthispuppy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Sends one HTTP test to the running application and judges the response. */
public class HttpTester {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public TestResult run(HttpCase test, String baseUrl) {
        long start = System.currentTimeMillis();
        TestResult result;
        try {
            HttpRequest.BodyPublisher body = test.body() == null || test.body().isBlank()
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(test.body());
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + test.path()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .method(test.method().toUpperCase(), body)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean statusOk = response.statusCode() == test.expectedStatus();
            boolean textOk = test.expectedText() == null || test.expectedText().isBlank()
                    || response.body().contains(test.expectedText());
            String detail = "status " + response.statusCode() + " (expected " + test.expectedStatus() + ")"
                    + (textOk ? "" : ", body did not contain \"" + test.expectedText() + "\"");
            result = new TestResult("http", test.name(), statusOk && textOk, detail, System.currentTimeMillis() - start);
        } catch (Exception e) {
            result = new TestResult("http", test.name(), false, "request failed: " + e.getMessage(), System.currentTimeMillis() - start);
        }
        return result;
    }
}
