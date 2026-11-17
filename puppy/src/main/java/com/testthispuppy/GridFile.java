package com.testthispuppy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a test table: the first line names the columns, every line after it is one test (a row).
 * Cells are separated by "|", so a cell cannot contain one. Blank lines and lines starting with # are skipped.
 *
 *   name          | method | path            | expectedStatus
 *   list people   | GET    | /api/customers  | 200
 */
public final class GridFile {
    private GridFile() {
    }

    public static List<Map<String, String>> read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file).stream()
                .filter(line -> !line.isBlank() && !line.trim().startsWith("#")).toList();
        List<Map<String, String>> rows = new ArrayList<>();
        if (!lines.isEmpty()) {
            List<String> headers = cells(lines.get(0));
            for (String line : lines.subList(1, lines.size())) {
                List<String> cells = cells(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < cells.size() ? cells.get(i) : "");
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<String> cells(String line) {
        return Arrays.stream(line.split("\\|", -1)).map(String::trim).toList();
    }
}
