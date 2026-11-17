package com.testthispuppy;

import java.util.List;
import java.util.Map;

/**
 * The exact contents of the database at one moment.
 *
 * @param rows      for each table: each row's primary key, mapped to that row's column values
 * @param sequences for each sequence (the counters behind serial ids): where it stood
 */
public record DatabaseSnapshot(Map<String, Map<List<Object>, Map<String, Object>>> rows,
                               Map<String, SequenceState> sequences) {

    /** Where a sequence stood: its last value, and whether that value has been handed out yet. */
    public record SequenceState(long lastValue, boolean called) {
    }

    public int rowCount() {
        return rows.values().stream().mapToInt(Map::size).sum();
    }
}
