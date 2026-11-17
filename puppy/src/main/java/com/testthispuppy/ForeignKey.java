package com.testthispuppy;

/** A column that must hold a value from another table, for example purchase_order.customer_id to customer.id. */
public record ForeignKey(String column, String parentTable, String parentColumn) {
}
