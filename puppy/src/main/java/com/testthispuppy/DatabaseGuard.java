package com.testthispuppy;

/**
 * Refuses to touch any database that is not on the approved list.
 * TestThisPuppy writes to and restores real tables, so it must never be pointed at production by accident.
 */
public final class DatabaseGuard {
    private DatabaseGuard() {
    }

    public static void check(PuppyConfig config) {
        String url = config.databaseUrl();
        boolean approved = url != null && config.allowedDatabases().contains(url);
        boolean looksLikeProduction = url != null && url.toLowerCase().contains("prod");
        if (!approved || looksLikeProduction) {
            throw new IllegalStateException("Refusing to run against " + url
                    + ". Add it to db.allowed in puppy.properties, and never use a production database.");
        }
    }
}
