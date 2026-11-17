package com.testthispuppy;

import java.util.Map;

/**
 * One HTTP test: send this request, expect this status, and expect the response body to contain this text.
 * An empty expectedText means only the status is checked. An empty body means no body is sent.
 */
public record HttpCase(String name, String method, String path, String body, int expectedStatus, String expectedText) {

    public static HttpCase fromRow(Map<String, String> row) {
        return new HttpCase(row.get("name"), row.get("method"), row.get("path"), row.getOrDefault("body", ""),
                Integer.parseInt(row.get("expectedStatus")), row.getOrDefault("expectedText", ""));
    }
}
