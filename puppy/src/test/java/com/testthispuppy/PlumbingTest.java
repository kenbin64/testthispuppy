package com.testthispuppy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for the parts that are not drills: the grid reader, table order, placeholders and the guard. */
class PlumbingTest {

    @Test
    void gridFileReadsRowsAndKeepsAnEmptyLastCell() throws IOException {
        Path file = Files.createTempFile("grid", ".txt");
        Files.writeString(file, """
                # a comment
                name | status | text
                one  | 200    | hello

                two  | 404    |
                """);
        List<Map<String, String>> rows = GridFile.read(file);
        assertEquals(2, rows.size());
        assertEquals("hello", rows.get(0).get("text"));
        assertEquals("404", rows.get(1).get("status"), "a row ending in an empty cell must still split (the first-run bug)");
        assertEquals("", rows.get(1).get("text"));
    }

    @Test
    void gridFileIgnoresAByteOrderMark() throws IOException {
        Path file = Files.createTempFile("grid-bom", ".txt");
        Files.writeString(file, "﻿# saved by a Windows editor\nname | status\none  | 200\n");
        List<Map<String, String>> rows = GridFile.read(file);
        assertEquals(1, rows.size(), "the comment line is still a comment");
        assertEquals("one", rows.get(0).get("name"));
    }

    @Test
    void fillerWordsComeFromTheCsvAndWrapAround() {
        assertEquals("lorem", FillerWords.word(0), "the first word in filler-words.csv");
        assertEquals("Lorem", FillerWords.capitalized(0));
        assertEquals(FillerWords.word(0), FillerWords.word(FillerWords.count()), "positions wrap round the list");
    }

    @Test
    void parentsComeBeforeChildren() {
        TableInfo child = new TableInfo("purchase_order", List.of(), List.of("id"), List.of(new ForeignKey("customer_id", "customer", "id")));
        TableInfo parent = new TableInfo("customer", List.of(), List.of("id"), List.of());
        assertEquals(List.of("customer", "purchase_order"), TableOrder.parentsFirst(List.of(child, parent)).stream().map(TableInfo::name).toList());
        assertEquals(List.of("purchase_order", "customer"), TableOrder.childrenFirst(List.of(child, parent)).stream().map(TableInfo::name).toList());
    }

    @Test
    void aLoopOfForeignKeysIsReported() {
        TableInfo a = new TableInfo("a", List.of(), List.of("id"), List.of(new ForeignKey("b_id", "b", "id")));
        TableInfo b = new TableInfo("b", List.of(), List.of("id"), List.of(new ForeignKey("a_id", "a", "id")));
        assertThrows(IllegalStateException.class, () -> TableOrder.parentsFirst(List.of(a, b)));
    }

    @Test
    void placeholdersFillFromSeededRowsAndLeaveUnknownOnesAlone() {
        Map<String, List<Map<String, Object>>> seeded = Map.of("customer", List.of(Map.of("id", 42L, "name", "Rex")));
        assertEquals("/api/customers/42", Placeholders.fill("/api/customers/{customer.id}", seeded));
        assertEquals("hi Rex, {dog.id}", Placeholders.fill("hi {customer.name}, {dog.id}", seeded));
    }

    @Test
    void placeholdersReachLaterRowsAndEncodeInsideAnAddress() {
        Map<String, List<Map<String, Object>>> seeded = Map.of("customer",
                List.of(Map.of("id", 1L, "name", "Lorem Ipsum"), Map.of("id", 2L, "name", "Dolor & Sit")));
        assertEquals("Dolor & Sit", Placeholders.fill("{customer.name#2}", seeded), "#2 is the second seeded row");
        assertEquals("{customer.name#3}", Placeholders.fill("{customer.name#3}", seeded), "a row that was not seeded is left alone");
        HttpCase filled = Placeholders.fill(new HttpCase("t", "GET", "/search?name={customer.name}", "{customer.name}", 200, "{customer.name#2}"), seeded);
        assertEquals("/search?name=Lorem%20Ipsum", filled.path(), "a space in an address is encoded");
        assertEquals("Lorem Ipsum", filled.body(), "the body is not encoded");
        assertEquals("Dolor & Sit", filled.expectedText(), "expected text is not encoded");
    }

    @Test
    void theGuardRefusesUnlistedAndProductionDatabases() {
        PuppyConfig ok = config("jdbc:postgresql://localhost:5433/puppy_demo", List.of("jdbc:postgresql://localhost:5433/puppy_demo"));
        PuppyConfig unlisted = config("jdbc:postgresql://other/db", List.of("jdbc:postgresql://localhost:5433/puppy_demo"));
        PuppyConfig production = config("jdbc:postgresql://prod-db/app", List.of("jdbc:postgresql://prod-db/app"));
        assertDoesNotThrow(() -> DatabaseGuard.check(ok));
        assertThrows(IllegalStateException.class, () -> DatabaseGuard.check(unlisted));
        assertThrows(IllegalStateException.class, () -> DatabaseGuard.check(production), "even when listed, a prod database is refused");
    }

    private static PuppyConfig config(String url, List<String> allowed) {
        return new PuppyConfig("external", url, "u", "p", allowed, null, List.of(), "http://localhost", 1,
                Path.of("h"), Path.of("p"), true, Path.of("r"), Path.of("."), List.of("happy"), new java.util.Properties());
    }
}
