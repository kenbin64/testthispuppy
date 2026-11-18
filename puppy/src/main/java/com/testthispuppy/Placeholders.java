package com.testthispuppy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills {table.column} in a test with a value from the first row this run seeded into that table, or
 * {table.column#2} from the second row (and so on). So "/api/customers/{customer.id}" asks for a customer that is
 * known to exist, and a test can check that a second row is still unchanged after the first was edited.
 * Values filled into a request path are URL-encoded, so a name with a space still makes a valid address.
 */
public final class Placeholders {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z_][\\w]*)\\.([a-zA-Z_][\\w]*)(?:#(\\d+))?}");

    private Placeholders() {
    }

    public static String fill(String text, Map<String, List<Map<String, Object>>> seeded) {
        return fill(text, seeded, false);
    }

    /** With encode set, each filled-in value is URL-encoded (spaces as %20, which works in a path and a query). */
    public static String fill(String text, Map<String, List<Map<String, Object>>> seeded, boolean encode) {
        Matcher matcher = PLACEHOLDER.matcher(text == null ? "" : text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            List<Map<String, Object>> rows = seeded.getOrDefault(matcher.group(1), List.of());
            int row = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3)) - 1;
            Object value = row >= 0 && row < rows.size() ? rows.get(row).get(matcher.group(2)) : null;
            String replacement = value == null ? matcher.group(0)
                    : encode ? URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8).replace("+", "%20") : String.valueOf(value);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static HttpCase fill(HttpCase test, Map<String, List<Map<String, Object>>> seeded) {
        return new HttpCase(test.name(), test.method(), fill(test.path(), seeded, true), fill(test.body(), seeded),
                test.expectedStatus(), fill(test.expectedText(), seeded));
    }
}
