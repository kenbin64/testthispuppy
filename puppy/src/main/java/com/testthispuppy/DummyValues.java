package com.testthispuppy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/** Makes believable dummy values, for database columns and for form fields on a page. */
public class DummyValues {
    private final AtomicInteger counter = new AtomicInteger();

    /** A value for a database column, chosen by the column's type and, for text, by its name. */
    public Object forColumn(ColumnInfo column) {
        String type = column.sqlType().toLowerCase();
        Object value;
        if (type.contains("int")) {
            value = 1 + counter.incrementAndGet() % 9;
        } else if (type.equals("numeric") || type.contains("double") || type.equals("real")) {
            value = new java.math.BigDecimal("9.99");
        } else if (type.equals("boolean")) {
            value = Boolean.TRUE;
        } else if (type.equals("date")) {
            value = LocalDate.now();
        } else if (type.startsWith("timestamp")) {
            value = LocalDateTime.now().withNano(0);
        } else {
            value = textFor(column.name());
        }
        return value;
    }

    /** A value to type into a form field, chosen by the field's input type and, for text, by its name. */
    public String forInput(String inputType, String fieldName) {
        String type = inputType == null ? "text" : inputType.toLowerCase();
        String value = switch (type) {
            case "email" -> textFor("email");
            case "tel" -> textFor("phone");
            case "number" -> String.valueOf(1 + counter.incrementAndGet() % 9);
            case "date" -> LocalDate.now().toString();
            default -> textFor(fieldName == null ? "" : fieldName);
        };
        return value;
    }

    private String textFor(String name) {
        int n = counter.incrementAndGet();
        String lower = name.toLowerCase();
        String text;
        if (lower.contains("email")) {
            text = "puppy" + n + "@example.test";
        } else if (lower.contains("phone")) {
            text = String.format("801-555-%04d", n % 10000);
        } else if (lower.contains("name")) {
            text = "Puppy Tester " + n;
        } else if (lower.contains("status")) {
            text = "NEW";
        } else {
            text = "puppy-" + name + "-" + n;
        }
        return text;
    }
}
