package com.testthispuppy;

import java.util.Map;

/** One browser test: open this page, fill every field, submit, and expect the next page to contain this text. */
public record PageCase(String name, String path, String expectedText) {

    public static PageCase fromRow(Map<String, String> row) {
        return new PageCase(row.get("name"), row.get("path"), row.getOrDefault("expectedText", ""));
    }
}
