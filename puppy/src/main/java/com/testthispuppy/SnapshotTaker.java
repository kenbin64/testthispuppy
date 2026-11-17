package com.testthispuppy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Records every row of every table, and where every sequence stands, so the database can be put back exactly. */
public final class SnapshotTaker {
    private SnapshotTaker() {
    }

    public static DatabaseSnapshot take(Connection connection, List<TableInfo> tables) throws SQLException {
        Map<String, Map<List<Object>, Map<String, Object>>> rows = new LinkedHashMap<>();
        for (TableInfo table : tables) {
            rows.put(table.name(), rowsOf(connection, table));
        }
        DatabaseSnapshot snapshot = new DatabaseSnapshot(rows, sequences(connection));
        return snapshot;
    }

    private static Map<List<Object>, Map<String, Object>> rowsOf(Connection connection, TableInfo table) throws SQLException {
        if (table.primaryKey().isEmpty()) {
            throw new IllegalStateException("Table " + table.name() + " has no primary key, so its rows cannot be told apart.");
        }
        Map<List<Object>, Map<String, Object>> rows = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT * FROM " + Sql.name(table.name()))) {
            ResultSetMetaData meta = result.getMetaData();
            while (result.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnName(i), result.getObject(i));
                }
                List<Object> key = new ArrayList<>();
                for (String column : table.primaryKey()) {
                    key.add(row.get(column));
                }
                rows.put(key, row);
            }
        }
        return rows;
    }

    private static Map<String, DatabaseSnapshot.SequenceState> sequences(Connection connection) throws SQLException {
        Map<String, DatabaseSnapshot.SequenceState> states = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public' ORDER BY sequence_name")) {
            while (result.next()) {
                names.add(result.getString(1));
            }
        }
        for (String name : names) {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT last_value, is_called FROM " + Sql.name(name))) {
                result.next();
                states.put(name, new DatabaseSnapshot.SequenceState(result.getLong(1), result.getBoolean(2)));
            }
        }
        return states;
    }
}
