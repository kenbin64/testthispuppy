package com.testthispuppy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The stack-trace and cross-site scripting checks, on sample responses. */
class ExposureChecksTest {

    @Test
    void recognisesStackTracesFromCommonStacks() {
        assertTrue(ErrorPageCheck.showsStackTrace("Whitelabel\n\tat com.example.Shop.save(Shop.java:42)\n"), "a Java frame");
        assertTrue(ErrorPageCheck.showsStackTrace("{\"status\":500,\"trace\":\"org.postgresql...\"}"), "Spring's JSON trace field");
        assertTrue(ErrorPageCheck.showsStackTrace("Traceback (most recent call last):\n  File \"app.py\""), "a Python traceback");
        assertTrue(ErrorPageCheck.showsStackTrace("TypeError: x\n    at render (/srv/app/server.js:12:5)"), "a Node.js frame");
        assertFalse(ErrorPageCheck.showsStackTrace("<h1>Something went wrong</h1><p>Please try again.</p>"), "a plain error page");
        assertFalse(ErrorPageCheck.showsStackTrace("{\"status\":404,\"error\":\"Not Found\"}"));
    }

    @Test
    void anEscapedEchoIsSafeAndARawOneIsNot() {
        String payload = XssCheck.PAYLOADS.get(0);
        assertTrue(XssCheck.reflectsUnescaped("<h1>Hello, " + payload + "!</h1>", payload));
        assertFalse(XssCheck.reflectsUnescaped("<h1>Hello, &lt;script&gt;puppyXss7319()&lt;/script&gt;!</h1>", payload));
        assertFalse(XssCheck.reflectsUnescaped(null, payload));
    }

    @Test
    void probesIncludeTheInjectionPathWithAQuote(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) throws Exception {
        java.nio.file.Path file = folder.resolve("p.properties");
        java.nio.file.Files.writeString(file, "app.baseUrl=http://localhost:1\ninjection.readPath=/api/x?q=\nerrors.paths=/boom, /bang\n");
        PuppyConfig config = PuppyConfig.load(file);
        assertEquals(java.util.List.of("/puppy-no-such-page", "/api/x?q=%27", "/boom", "/bang"), ErrorPageCheck.probes(config));
    }
}
