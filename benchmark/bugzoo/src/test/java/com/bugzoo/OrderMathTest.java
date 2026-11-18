package com.bugzoo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderMathTest {
    @Test
    void aTotalIsWorkedOut() {
        assertTrue(OrderMath.totalCents(250, 2) > 0);
        assertTrue(OrderMath.totalCents(250, 20) > 0);
    }
}
