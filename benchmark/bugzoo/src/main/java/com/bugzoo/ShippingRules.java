package com.bugzoo;

/** How many days an order takes to ship. */
public final class ShippingRules {
    private ShippingRules() {
    }

    public static int shippingDays(String region, int quantity, boolean express, boolean member, boolean holiday, int weightKg) {
        int days;
        if ("north".equals(region)) {
            if (express) {
                days = member ? 1 : 2;
            } else if (quantity > 10) {
                days = holiday ? 9 : 6;
            } else {
                days = holiday ? 7 : 4;
            }
        } else if ("south".equals(region)) {
            if (express) {
                days = member ? 2 : 3;
            } else if (weightKg > 20) {
                days = holiday ? 10 : (quantity > 10 ? 8 : 7);
            } else {
                days = holiday ? 8 : 5;
            }
        } else if ("island".equals(region)) {
            if (express && member) {
                days = 3;
            } else if (express) {
                days = weightKg > 20 ? 6 : 4;
            } else if (holiday && weightKg > 20) {
                days = 14;
            } else if (holiday || quantity > 10) {
                days = 11;
            } else {
                days = 9;
            }
        } else {
            days = express ? 3 : (holiday ? 9 : (weightKg > 20 ? 8 : 6));
        }
        if (member && !express && days > 4) {
            days--;
        }
        return days;
    }
}
