package com.testthispuppy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills every table with dummy rows, parents first, so each foreign key points at a real parent row.
 * Columns the database fills itself (serial ids, default timestamps) are left to the database.
 */
public final class DatabaseSeeder {
    private DatabaseSeeder() {
    }

    /** Returns the rows it inserted, by table, exactly as the database stored them. */
    public static Map<String, List<Map<String, Object>>> seed(Connection connection, List<TableInfo> parentsFirst,
                                                              int rowsPerTable, DummyValues dummy) throws SQLException {
        Map<String, List<Map<String, Object>>> seeded = new LinkedHashMap<>();
        for (TableInfo table : parentsFirst) {
            List<Map<String, Object>> inserted = new ArrayList<>();
            for (int i = 0; i < rowsPerTable; i++) {
                inserted.add(insertRow(connection, table, seeded, dummy, i));
            }
            seeded.put(table.name(), inserted);
        }
        return seeded;
    }

    private static Map<String, Object> insertRow(Connection connection, TableInfo table,
                                                 Map<String, List<Map<String, Object>>> seeded, DummyValues dummy, int index) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (ColumnInfo column : table.columns()) {
            ForeignKey key = table.foreignKeyFor(column.name());
            if (key != null) {
                columns.add(column.name());
                values.add(parentValue(connection, key, seeded, index));
            } else if (!column.hasDefault()) {
                columns.add(column.name());
                values.add(dummy.forColumn(column));
            }
        }
        String sql = "INSERT INTO " + Sql.name(table.name()) + " (" + Sql.names(columns) + ") VALUES ("
                + Sql.placeholders(columns.size()) + ") RETURNING *";
        Map<String, Object> row = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                statement.setObject(i + 1, values.get(i));
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                ResultSetMetaData meta = result.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnName(i), result.getObject(i));
                }
            }
        }
        return row;
    }

    // A parent row this run seeded if there is one, otherwise any existing parent row.
    private static Object parentValue(Connection connection, ForeignKey key,
                                      Map<String, List<Map<String, Object>>> seeded, int index) throws SQLException {
        List<Map<String, Object>> parents = seeded.getOrDefault(key.parentTable(), List.of());
        Object value;
        if (!parents.isEmpty()) {
            value = parents.get(index % parents.size()).get(key.parentColumn());
        } else {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + Sql.name(key.parentColumn()) + " FROM " + Sql.name(key.parentTable()) + " LIMIT 1");
                 ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("No row in " + key.parentTable() + " for " + key.column() + " to point at.");
                }
                value = result.getObject(1);
            }
        }
        return value;
    }
}
