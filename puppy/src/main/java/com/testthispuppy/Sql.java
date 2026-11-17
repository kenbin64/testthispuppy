package com.testthispuppy;

import java.util.List;
import java.util.stream.Collectors;

/** Small helpers for writing SQL safely: quoted names and placeholder lists. */
public final class Sql {
    private Sql() {
    }

    /** A table, column or sequence name, double-quoted so unusual names cannot break the statement. */
    public static String name(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /** "a", "b" for a list of names. */
    public static String names(List<String> identifiers) {
        return identifiers.stream().map(Sql::name).collect(Collectors.joining(", "));
    }

    /** ?, ?, ? for that many values. */
    public static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    /** "a" = ? AND "b" = ? for a primary key. */
    public static String matching(List<String> identifiers) {
        return identifiers.stream().map(c -> name(c) + " = ?").collect(Collectors.joining(" AND "));
    }
}
