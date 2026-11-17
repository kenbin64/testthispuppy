package com.testthispuppy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** DRILL 1 grader: run with  mvn -q -pl puppy test -Dtest=DummyValuesTest */
class DummyValuesTest {
    private final DummyValues dummy = new DummyValues();

    private static ColumnInfo column(String name, String type) {
        return new ColumnInfo(name, type, false, false);
    }

    @Test
    void anEmailColumnGetsAnEmailAddress() {
        Object value = dummy.forColumn(column("email", "character varying"));
        assertTrue(value.toString().contains("@"), "expected an email address, got " + value);
    }

    @Test
    void emailsAreDifferentEveryTime() {
        Object first = dummy.forColumn(column("email", "character varying"));
        Object second = dummy.forColumn(column("email", "character varying"));
        assertNotEquals(first, second, "two dummy emails must not collide");
    }

    @Test
    void aPhoneColumnGetsAPhoneNumber() {
        Object value = dummy.forColumn(column("phone", "character varying"));
        assertTrue(value.toString().matches("\\d{3}-\\d{3}-\\d{4}"), "expected 801-555-0001 style, got " + value);
    }

    @Test
    void aNameColumnGetsANameThatChanges() {
        Object first = dummy.forColumn(column("name", "character varying"));
        Object second = dummy.forColumn(column("name", "character varying"));
        assertFalse(first.toString().isBlank());
        assertNotEquals(first, second);
    }

    @Test
    void anIntegerColumnGetsAPositiveWholeNumberUnderTen() {
        Object value = dummy.forColumn(column("quantity", "integer"));
        assertInstanceOf(Integer.class, value);
        int number = (Integer) value;
        assertTrue(number >= 1 && number <= 9, "expected 1 to 9, got " + number);
    }

    @Test
    void dateTimestampAndBooleanColumnsGetTheRightJavaTypes() {
        assertInstanceOf(LocalDate.class, dummy.forColumn(column("order_date", "date")));
        assertInstanceOf(LocalDateTime.class, dummy.forColumn(column("created_at", "timestamp without time zone")));
        assertInstanceOf(Boolean.class, dummy.forColumn(column("active", "boolean")));
    }

    @Test
    void formInputsGetValuesThatTheBrowserWillAccept() {
        assertTrue(dummy.forInput("email", "contact").contains("@"));
        assertTrue(dummy.forInput("tel", "phone").matches("\\d{3}-\\d{3}-\\d{4}"));
        assertTrue(Integer.parseInt(dummy.forInput("number", "quantity")) > 0);
        assertEquals(LocalDate.now(), LocalDate.parse(dummy.forInput("date", "orderDate")));
        assertFalse(dummy.forInput("text", "product").isBlank());
        assertFalse(dummy.forInput(null, "notes").isBlank(), "a missing type is treated as text");
    }
}
