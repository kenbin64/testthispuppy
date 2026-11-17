package com.testthispuppy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Reads the tables, columns, primary keys and foreign keys of one schema from the database itself. */
public final class SchemaReader {
    private SchemaReader() {
    }

    public static List<TableInfo> read(Connection connection, String schema) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        for (String table : tableNames(connection, schema)) {
            tables.add(new TableInfo(table, columns(connection, schema, table),
                    primaryKey(connection, schema, table), foreignKeys(connection, schema, table)));
        }
        return tables;
    }

    private static List<String> tableNames(Connection connection, String schema) throws SQLException {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name";
        List<String> names = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    names.add(rows.getString(1));
                }
            }
        }
        return names;
    }

    private static List<ColumnInfo> columns(Connection connection, String schema, String table) throws SQLException {
        String sql = "SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";
        List<ColumnInfo> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    columns.add(new ColumnInfo(rows.getString(1), rows.getString(2),
                            "YES".equals(rows.getString(3)), rows.getString(4) != null));
                }
            }
        }
        return columns;
    }

    private static List<String> primaryKey(Connection connection, String schema, String table) throws SQLException {
        String sql = "SELECT k.column_name FROM information_schema.table_constraints c "
                + "JOIN information_schema.key_column_usage k ON c.constraint_name = k.constraint_name AND c.table_schema = k.table_schema "
                + "WHERE c.constraint_type = 'PRIMARY KEY' AND c.table_schema = ? AND c.table_name = ? ORDER BY k.ordinal_position";
        List<String> key = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    key.add(rows.getString(1));
                }
            }
        }
        return key;
    }

    private static List<ForeignKey> foreignKeys(Connection connection, String schema, String table) throws SQLException {
        String sql = "SELECT k.column_name, u.table_name, u.column_name FROM information_schema.table_constraints c "
                + "JOIN information_schema.key_column_usage k ON c.constraint_name = k.constraint_name AND c.table_schema = k.table_schema "
                + "JOIN information_schema.constraint_column_usage u ON c.constraint_name = u.constraint_name AND c.table_schema = u.table_schema "
                + "WHERE c.constraint_type = 'FOREIGN KEY' AND c.table_schema = ? AND c.table_name = ?";
        List<ForeignKey> keys = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    keys.add(new ForeignKey(rows.getString(1), rows.getString(2), rows.getString(3)));
                }
            }
        }
        return keys;
    }
}
