package com.testthispuppy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** DRILL 2 grader: run with  mvn -q -pl puppy test -Dtest=HttpTesterTest */
class HttpTesterTest {
    private static HttpServer server;
    private static String baseUrl;
    private final HttpTester tester = new HttpTester();

    // A tiny real server: /hello answers 200, /echo answers 201 with the body it was sent, anything else 404.
    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (path.equals("/hello") && exchange.getRequestMethod().equals("GET")) {
                reply(exchange, 200, "hello puppy");
            } else if (path.equals("/echo") && exchange.getRequestMethod().equals("POST")) {
                reply(exchange, 201, "you sent " + body);
            } else {
                reply(exchange, 404, "not here");
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void reply(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void passesWhenStatusAndTextMatch() {
        TestResult result = tester.run(new HttpCase("hello", "GET", "/hello", "", 200, "puppy"), baseUrl);
        assertTrue(result.passed(), result.detail());
        assertEquals("http", result.kind());
        assertEquals("hello", result.name());
    }

    @Test
    void passesOnStatusAloneWhenNoTextIsExpected() {
        assertTrue(tester.run(new HttpCase("missing", "GET", "/nowhere", "", 404, ""), baseUrl).passed());
    }

    @Test
    void failsWhenTheStatusIsWrong() {
        TestResult result = tester.run(new HttpCase("wrong status", "GET", "/hello", "", 500, ""), baseUrl);
        assertFalse(result.passed());
        assertTrue(result.detail().contains("200"), "the detail should say what status came back: " + result.detail());
    }

    @Test
    void failsWhenTheTextIsMissing() {
        TestResult result = tester.run(new HttpCase("wrong text", "GET", "/hello", "", 200, "kitten"), baseUrl);
        assertFalse(result.passed());
        assertTrue(result.detail().contains("kitten"), "the detail should name the missing text: " + result.detail());
    }

    @Test
    void sendsTheBodyOnAPost() {
        TestResult result = tester.run(new HttpCase("post", "POST", "/echo", "{\"a\": 1}", 201, "{\"a\": 1}"), baseUrl);
        assertTrue(result.passed(), result.detail());
    }

    @Test
    void anUnreachableServerIsAFailureNotACrash() {
        TestResult result = tester.run(new HttpCase("down", "GET", "/hello", "", 200, ""), "http://127.0.0.1:1");
        assertFalse(result.passed());
    }
}
