package com.testthispuppy;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL injection tests. Classic attack strings go into every text field and into the API.
 * A test passes when every table still exists, no table lost rows, the app did not crash, and any attack
 * string the app saved is stored as plain text (proof it was never run as SQL).
 * Only ever run against the internal throwaway database: an attack that works would really drop a table.
 */
public final class InjectionTester {
    private final Connection connection;
    private final List<TableInfo> tables;
    private final List<String> payloads;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public InjectionTester(Connection connection, List<TableInfo> tables) {
        this.connection = connection;
        this.tables = tables;
        String target = tables.isEmpty() ? "users" : tables.get(0).name();
        this.payloads = List.of(
                "' OR '1'='1",
                "x'); DROP TABLE " + target + "; --",
                "1; DELETE FROM " + target + "; --");
    }

    /** Every text field on the page, with every attack string. */
    public List<TestResult> testPage(BrowserTester browser, PageCase test, String baseUrl) {
        List<TestResult> results = new ArrayList<>();
        for (FormField field : browser.fieldsOn(test, baseUrl)) {
            if (List.of("text", "textarea", "email", "tel", "search").contains(field.type())) {
                for (String payload : payloads) {
                    long start = System.currentTimeMillis();
                    String name = test.name() + ": " + field.name() + " = " + payload;
                    TestResult result;
                    try {
                        State before = state();
                        BrowserTester.Submission submission = browser.submitWith(test, baseUrl, field.name(), payload);
                        result = judge(name, before, submission.crashed(), submission.saved(), payload, start);
                    } catch (Exception e) {
                        result = new TestResult("injection", name, false, "failed: " + e.getMessage(), System.currentTimeMillis() - start);
                    }
                    results.add(result);
                }
            }
        }
        return results;
    }

    /** The API: an attack in a path segment, and attacks in a JSON body. */
    public List<TestResult> testApi(String baseUrl, String readPath, String createPath, String createBodyTemplate) {
        List<TestResult> results = new ArrayList<>();
        for (String payload : payloads) {
            long start = System.currentTimeMillis();
            String name = "api path: " + readPath + payload;
            TestResult result;
            try {
                State before = state();
                HttpResponse<String> response = send("GET", baseUrl + readPath + URLEncoder.encode(payload, StandardCharsets.UTF_8).replace("+", "%20"), null);
                result = judge(name, before, response.statusCode() >= 500, false, payload, start);
            } catch (Exception e) {
                result = new TestResult("injection", name, false, "failed: " + e.getMessage(), System.currentTimeMillis() - start);
            }
            results.add(result);
        }
        for (String payload : payloads) {
            long start = System.currentTimeMillis();
            String name = "api body: " + createPath + " name = " + payload;
            TestResult result;
            try {
                State before = state();
                String body = createBodyTemplate.replace("{payload}", payload.replace("\\", "\\\\").replace("\"", "\\\""));
                HttpResponse<String> response = send("POST", baseUrl + createPath, body);
                boolean saved = response.statusCode() >= 200 && response.statusCode() < 300;
                result = judge(name, before, response.statusCode() >= 500, saved, payload, start);
            } catch (Exception e) {
                result = new TestResult("injection", name, false, "failed: " + e.getMessage(), System.currentTimeMillis() - start);
            }
            results.add(result);
        }
        return results;
    }

    private TestResult judge(String name, State before, boolean crashed, boolean saved, String payload, long start) throws SQLException {
        State after = state();
        List<String> problems = new ArrayList<>();
        if (!after.tables().keySet().containsAll(before.tables().keySet())) {
            problems.add("a TABLE WAS DROPPED");
        }
        for (Map.Entry<String, Integer> entry : before.tables().entrySet()) {
            Integer now = after.tables().get(entry.getKey());
            if (now != null && now < entry.getValue()) {
                problems.add("rows were DELETED from " + entry.getKey() + " (" + entry.getValue() + " to " + now + ")");
            }
        }
        if (crashed) {
            problems.add("the app crashed (server error)");
        }
        if (saved && !storedLiterally(payload)) {
            problems.add("the app said saved, but the attack text is not stored as plain text");
        }
        String detail = problems.isEmpty()
                ? (saved ? "stored safely as plain text; tables intact" : "refused; tables intact")
                : String.join("; ", problems);
        return new TestResult("injection", name, problems.isEmpty(), detail, System.currentTimeMillis() - start);
    }

    /** Which tables exist and how many rows each holds. */
    private record State(Map<String, Integer> tables) {
    }

    private State state() throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet names = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")) {
            List<String> found = new ArrayList<>();
            while (names.next()) {
                found.add(names.getString(1));
            }
            for (String table : found) {
                try (Statement count = connection.createStatement();
                     ResultSet result = count.executeQuery("SELECT count(*) FROM " + Sql.name(table))) {
                    result.next();
                    counts.put(table, result.getInt(1));
                }
            }
        }
        return new State(counts);
    }

    // True when some text column of some table holds exactly the attack string.
    private boolean storedLiterally(String payload) throws SQLException {
        boolean found = false;
        for (TableInfo table : tables) {
            for (ColumnInfo column : table.columns()) {
                if (!found && (column.sqlType().contains("char") || column.sqlType().equals("text"))) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT 1 FROM " + Sql.name(table.name()) + " WHERE " + Sql.name(column.name()) + " = ? LIMIT 1")) {
                        statement.setString(1, payload);
                        try (ResultSet result = statement.executeQuery()) {
                            found = result.next();
                        }
                    }
                }
            }
        }
        return found;
    }

    private HttpResponse<String> send(String method, String url, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
