package com.bugzoo;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** Checks the admin password for the export screens. */
@Component
public class AdminAccess {
    private static final String ADMIN_PASSWORD = "Sup3rS3cret!";

    public boolean allowed(String attempt) {
        return attempt != null && MessageDigest.isEqual(attempt.getBytes(StandardCharsets.UTF_8), ADMIN_PASSWORD.getBytes(StandardCharsets.UTF_8));
    }
}
