package com.testthispuppy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Checks the road to the database before anything touches it:
 *   resolve   the host name turns into an address
 *   reach     the database port accepts a connection (a TCP ping; ICMP ping needs admin rights on many systems)
 *   ping      the database answers SELECT 1 over a real login, timed
 *   route     the network path to the host, hop by hop (tracert on Windows, traceroute elsewhere)
 * The first three must pass for the run to continue. The route is information, so it never fails the run.
 */
public final class ConnectionCheck {
    private ConnectionCheck() {
    }

    public static List<TestResult> check(PuppyConfig config) {
        List<TestResult> results = new ArrayList<>();
        URI address = URI.create(config.databaseUrl().replaceFirst("^jdbc:", ""));
        String host = address.getHost();
        int port = address.getPort() > 0 ? address.getPort() : 5432;
        results.add(resolve(host));
        if (results.get(0).passed()) {
            results.add(reach(host, port));
        }
        if (results.stream().allMatch(TestResult::passed)) {
            results.add(ping(config));
            results.add(route(host));
        }
        return results;
    }

    /** True when every check the run depends on passed (the route trace does not count). */
    public static boolean reachable(List<TestResult> results) {
        return results.stream().filter(r -> !r.name().startsWith("trace")).allMatch(TestResult::passed)
                && results.stream().anyMatch(r -> r.name().startsWith("ping"));
    }

    private static TestResult resolve(String host) {
        long start = System.currentTimeMillis();
        TestResult result;
        try {
            InetAddress ip = InetAddress.getByName(host);
            result = new TestResult("network", "resolve the database host", true, host + " is " + ip.getHostAddress(), System.currentTimeMillis() - start);
        } catch (IOException e) {
            result = new TestResult("network", "resolve the database host", false, "cannot resolve " + host + ": " + e.getMessage(), System.currentTimeMillis() - start);
        }
        return result;
    }

    private static TestResult reach(String host, int port) {
        long start = System.currentTimeMillis();
        TestResult result;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            result = new TestResult("network", "reach the database port", true, host + ":" + port + " accepted a connection", System.currentTimeMillis() - start);
        } catch (IOException e) {
            result = new TestResult("network", "reach the database port", false, host + ":" + port + " refused or timed out: " + e.getMessage(), System.currentTimeMillis() - start);
        }
        return result;
    }

    private static TestResult ping(PuppyConfig config) {
        long start = System.currentTimeMillis();
        TestResult result;
        try (Connection connection = DriverManager.getConnection(config.databaseUrl(), config.databaseUser(), config.databasePassword());
             Statement statement = connection.createStatement();
             ResultSet answer = statement.executeQuery("SELECT 1, version()")) {
            answer.next();
            String version = answer.getString(2).split(",")[0];
            result = new TestResult("network", "ping the database", answer.getInt(1) == 1, "logged in and SELECT 1 answered; " + version, System.currentTimeMillis() - start);
        } catch (Exception e) {
            result = new TestResult("network", "ping the database", false, "login or query failed: " + e.getMessage(), System.currentTimeMillis() - start);
        }
        return result;
    }

    private static TestResult route(String host) {
        long start = System.currentTimeMillis();
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        List<String> command = windows ? List.of("tracert", "-d", "-h", "8", "-w", "500", host)
                : List.of("traceroute", "-n", "-m", "8", "-w", "1", host);
        String detail;
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            long hops = output.lines().filter(line -> line.trim().matches("^\\d+\\s.*")).count();
            detail = finished ? hops + " hop(s) to " + host : "trace still running after 30 s, stopped";
            if (!finished) {
                process.destroyForcibly();
            }
        } catch (Exception e) {
            detail = "route trace not available here (" + e.getMessage() + ")";
        }
        return new TestResult("network", "trace the route to the database", true, detail, System.currentTimeMillis() - start);
    }
}
