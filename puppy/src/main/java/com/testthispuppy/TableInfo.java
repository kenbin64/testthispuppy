package com.testthispuppy;

import java.util.List;

/** One table: its columns, the columns of its primary key, and the foreign keys pointing at its parents. */
public record TableInfo(String name, List<ColumnInfo> columns, List<String> primaryKey, List<ForeignKey> foreignKeys) {

    public ForeignKey foreignKeyFor(String column) {
        ForeignKey found = null;
        for (ForeignKey key : foreignKeys) {
            if (key.column().equals(column)) {
                found = key;
            }
        }
        return found;
    }
}
