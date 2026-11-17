package com.testthispuppy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Puts the database back exactly as the snapshot recorded it, in one transaction:
 * rows added since are deleted (children first), rows changed are set back, rows removed are put back
 * (parents first), and every sequence is reset so the next id is the one it would have been.
 * This works even when the application wrote through its own connections, which rollback cannot undo.
 */
public final class DatabaseRestorer {
    private DatabaseRestorer() {
    }

    /** What the restore had to undo. */
    public record Summary(int deleted, int updated, int reinserted, int sequencesReset) {
    }

    public static Summary restore(Connection connection, List<TableInfo> tables, DatabaseSnapshot before) throws SQLException {
        DatabaseSnapshot now = SnapshotTaker.take(connection, tables);
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        Summary summary;
        try {
            int deleted = deleteAdded(connection, TableOrder.childrenFirst(tables), before, now);
            int updated = setBackChanged(connection, tables, before, now);
            int reinserted = putBackRemoved(connection, TableOrder.parentsFirst(tables), before, now);
            int sequencesReset = resetSequences(connection, before);
            connection.commit();
            summary = new Summary(deleted, updated, reinserted, sequencesReset);
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        return summary;
    }

    private static int deleteAdded(Connection connection, List<TableInfo> childrenFirst, DatabaseSnapshot before, DatabaseSnapshot now) throws SQLException {
        int count = 0;
        for (TableInfo table : childrenFirst) {
            Map<List<Object>, Map<String, Object>> original = before.rows().get(table.name());
            String sql = "DELETE FROM " + Sql.name(table.name()) + " WHERE " + Sql.matching(table.primaryKey());
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (List<Object> key : now.rows().get(table.name()).keySet()) {
                    if (!original.containsKey(key)) {
                        bind(statement, key);
                        count += statement.executeUpdate();
                    }
                }
            }
        }
        return count;
    }

    private static int setBackChanged(Connection connection, List<TableInfo> tables, DatabaseSnapshot before, DatabaseSnapshot now) throws SQLException {
        int count = 0;
        for (TableInfo table : tables) {
            Map<List<Object>, Map<String, Object>> current = now.rows().get(table.name());
            for (Map.Entry<List<Object>, Map<String, Object>> entry : before.rows().get(table.name()).entrySet()) {
                Map<String, Object> was = entry.getValue();
                Map<String, Object> is = current.get(entry.getKey());
                if (is != null && !Objects.equals(was, is)) {
                    List<String> columns = new ArrayList<>(was.keySet());
                    String sql = "UPDATE " + Sql.name(table.name()) + " SET "
                            + String.join(", ", columns.stream().map(c -> Sql.name(c) + " = ?").toList())
                            + " WHERE " + Sql.matching(table.primaryKey());
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        List<Object> values = new ArrayList<>(was.values());
                        values.addAll(entry.getKey());
                        bind(statement, values);
                        count += statement.executeUpdate();
                    }
                }
            }
        }
        return count;
    }

    private static int putBackRemoved(Connection connection, List<TableInfo> parentsFirst, DatabaseSnapshot before, DatabaseSnapshot now) throws SQLException {
        int count = 0;
        for (TableInfo table : parentsFirst) {
            Map<List<Object>, Map<String, Object>> current = now.rows().get(table.name());
            for (Map.Entry<List<Object>, Map<String, Object>> entry : before.rows().get(table.name()).entrySet()) {
                if (!current.containsKey(entry.getKey())) {
                    List<String> columns = new ArrayList<>(entry.getValue().keySet());
                    String sql = "INSERT INTO " + Sql.name(table.name()) + " (" + Sql.names(columns) + ") VALUES ("
                            + Sql.placeholders(columns.size()) + ")";
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        bind(statement, new ArrayList<>(entry.getValue().values()));
                        count += statement.executeUpdate();
                    }
                }
            }
        }
        return count;
    }

    private static int resetSequences(Connection connection, DatabaseSnapshot before) throws SQLException {
        int count = 0;
        for (Map.Entry<String, DatabaseSnapshot.SequenceState> entry : before.sequences().entrySet()) {
            String sql = "SELECT setval('" + Sql.name(entry.getKey()).replace("'", "''") + "', "
                    + entry.getValue().lastValue() + ", " + entry.getValue().called() + ")";
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
                count++;
            }
        }
        return count;
    }

    private static void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            statement.setObject(i + 1, values.get(i));
        }
    }
}
