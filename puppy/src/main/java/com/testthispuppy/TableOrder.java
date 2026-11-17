package com.testthispuppy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Puts tables in an order that respects their foreign keys.
 * Parents first is the order to insert in; children first (the reverse) is the order to delete in.
 */
public final class TableOrder {
    private TableOrder() {
    }

    public static List<TableInfo> parentsFirst(List<TableInfo> tables) {
        Map<String, TableInfo> byName = tables.stream().collect(Collectors.toMap(TableInfo::name, Function.identity()));
        Set<String> placed = new LinkedHashSet<>();
        for (TableInfo table : tables) {
            place(table, byName, placed, new LinkedHashSet<>());
        }
        return placed.stream().map(byName::get).toList();
    }

    public static List<TableInfo> childrenFirst(List<TableInfo> tables) {
        List<TableInfo> order = new ArrayList<>(parentsFirst(tables));
        java.util.Collections.reverse(order);
        return order;
    }

    // Place every parent before the table itself. "visiting" catches a loop of foreign keys.
    private static void place(TableInfo table, Map<String, TableInfo> byName, Set<String> placed, Set<String> visiting) {
        if (!placed.contains(table.name())) {
            if (!visiting.add(table.name())) {
                throw new IllegalStateException("Foreign keys form a loop through table " + table.name());
            }
            for (ForeignKey key : table.foreignKeys()) {
                TableInfo parent = byName.get(key.parentTable());
                if (parent != null && !parent.name().equals(table.name())) {
                    place(parent, byName, placed, visiting);
                }
            }
            placed.add(table.name());
        }
    }
}
