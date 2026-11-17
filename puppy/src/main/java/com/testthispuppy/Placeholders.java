package com.testthispuppy;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills {table.column} in a test with a value from the first row this run seeded into that table.
 * So "/api/customers/{customer.id}" asks for a customer that is known to exist.
 */
public final class Placeholders {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z_][\\w]*)\\.([a-zA-Z_][\\w]*)}");

    private Placeholders() {
    }

    public static String fill(String text, Map<String, List<Map<String, Object>>> seeded) {
        Matcher matcher = PLACEHOLDER.matcher(text == null ? "" : text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            List<Map<String, Object>> rows = seeded.getOrDefault(matcher.group(1), List.of());
            Object value = rows.isEmpty() ? null : rows.get(0).get(matcher.group(2));
            String replacement = value == null ? matcher.group(0) : String.valueOf(value);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static HttpCase fill(HttpCase test, Map<String, List<Map<String, Object>>> seeded) {
        return new HttpCase(test.name(), test.method(), fill(test.path(), seeded), fill(test.body(), seeded),
                test.expectedStatus(), fill(test.expectedText(), seeded));
    }
}
