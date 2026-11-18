package com.bugzoo;

/** Order prices in cents: unit price times quantity, with 10% off from 10 items. */
public final class OrderMath {
    static final int DISCOUNT_FROM = 10;

    private OrderMath() {
    }

    public static long totalCents(int unitCents, int quantity) {
        long subtotal = (long) unitCents * quantity;
        if (quantity >= DISCOUNT_FROM) {
            return subtotal - subtotal / 10;
        }
        return subtotal;
    }
}
