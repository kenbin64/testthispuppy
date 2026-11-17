package com.testthispuppy.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Unit tests for the plain objects: they hold what they are given, and start in the right state. */
class ObjectsTest {

    @Test
    void aCustomerHoldsItsValues() {
        Customer customer = new Customer();
        customer.setName("Lorem Ipsum");
        customer.setEmail("lorem@example.test");
        customer.setPhone("801-555-0001");
        assertEquals("Lorem Ipsum", customer.getName());
        assertEquals("lorem@example.test", customer.getEmail());
        assertEquals("801-555-0001", customer.getPhone());
        assertNull(customer.getId(), "the database assigns the id, not the object");
    }

    @Test
    void aNewOrderStartsAsNew() {
        PurchaseOrder order = new PurchaseOrder();
        order.setCustomerId(7L);
        order.setProduct("Dolor");
        order.setQuantity(3);
        order.setOrderDate(LocalDate.of(2026, 11, 16));
        assertEquals("NEW", order.getStatus());
        assertEquals(7L, order.getCustomerId());
        assertEquals("Dolor", order.getProduct());
        assertEquals(3, order.getQuantity());
        assertEquals(LocalDate.of(2026, 11, 16), order.getOrderDate());
    }
}
