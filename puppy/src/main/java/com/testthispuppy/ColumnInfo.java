package com.testthispuppy;

/**
 * One column of a table, as the database describes it.
 *
 * @param name       the column name, for example "email"
 * @param sqlType    the database type, for example "character varying", "integer", "date"
 * @param nullable   true when the column may be left empty
 * @param hasDefault true when the database fills it itself (a serial id, a default timestamp)
 */
public record ColumnInfo(String name, String sqlType, boolean nullable, boolean hasDefault) {
}
