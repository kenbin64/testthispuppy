package com.testthispuppy;

import java.util.Optional;

/**
 * Wrong values for the wrong-input tests: something of the wrong type for each kind of field.
 * A field with no sensible wrong value (an optional text box, a checkbox) is not tested.
 */
public final class WrongValues {
    /** The value put in a dropdown: an option the page never offered, such as a record that does not exist. */
    public static final String MISSING_OPTION = "999999999";

    private WrongValues() {
    }

    public static Optional<String> forField(FormField field, boolean required) {
        String value = switch (field.type()) {
            case "email" -> "not-an-email";
            case "number" -> "abc";
            case "date" -> "not-a-date";
            case "select" -> MISSING_OPTION;
            case "text", "textarea", "tel" -> required ? "" : null;
            default -> null;
        };
        return Optional.ofNullable(value);
    }

    /** Words for the result detail, so a report says what was tried. */
    public static String describe(FormField field, String value) {
        String description;
        if (value.isEmpty()) {
            description = "required " + field.name() + " left empty";
        } else if (value.equals(MISSING_OPTION)) {
            description = field.name() + " set to an option that does not exist";
        } else {
            description = field.name() + " = \"" + value + "\"";
        }
        return description;
    }
}
