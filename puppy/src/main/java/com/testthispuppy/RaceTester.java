package com.testthispuppy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * A race-condition test: many identical create requests released at the same instant.
 * It passes when every request succeeds and the table grew by exactly that many rows, no more and no fewer.
 * Lost writes, duplicates and crashes under load all show up as a wrong count or a failed request.
 */
public final class RaceTester {
    private RaceTester() {
    }

    /** {n} in the body is replaced with each request's number, so values that must be unique stay unique. */
    public static TestResult run(Connection connection, String baseUrl, String method, String path, String bodyTemplate,
                                 String table, int requests) {
        long start = System.currentTimeMillis();
        String name = requests + " " + method + " " + path + " at once";
        TestResult result;
        try {
            int before = count(connection, table);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            CountDownLatch gate = new CountDownLatch(1);
            List<CompletableFuture<Integer>> answers = new ArrayList<>();
            for (int n = 1; n <= requests; n++) {
                String body = bodyTemplate.replace("{n}", String.valueOf(n));
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body)).build();
                answers.add(CompletableFuture.supplyAsync(() -> {
                    int status;
                    try {
                        gate.await();
                        status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                    } catch (Exception e) {
                        status = -1;
                    }
                    return status;
                }));
            }
            gate.countDown();
            long succeeded = answers.stream().map(CompletableFuture::join).filter(s -> s >= 200 && s < 300).count();
            int grew = count(connection, table) - before;
            boolean passed = succeeded == requests && grew == requests;
            String detail = succeeded + " of " + requests + " succeeded; " + table + " grew by " + grew
                    + (passed ? " (exact)" : " (expected " + requests + ")");
            result = new TestResult("race", name, passed, detail, System.currentTimeMillis() - start);
        } catch (Exception e) {
            result = new TestResult("race", name, false, "failed: " + e.getMessage(), System.currentTimeMillis() - start);
        }
        return result;
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT count(*) FROM " + Sql.name(table))) {
            result.next();
            return result.getInt(1);
        }
    }
}
