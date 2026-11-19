package com.testthispuppy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * TestThisPuppy in the browser (Ken, 2026-11-19): a small web server on this computer, with a page that runs the
 * quick check. Nothing is uploaded anywhere; the server only answers this computer unless phone mode is on.
 *
 *   web                 http://127.0.0.1:7777, this computer only
 *   web --phone         also answers phones and tablets on the same Wi-Fi, after a 6-digit PIN shown here
 *   web --port 8080     another port
 *
 * Safety, because a check can run code:
 *   - every request except the page itself needs a secret token. This computer gets it in the address it opens
 *     (after the #, which a browser never sends to other sites); a phone gets it by typing the PIN.
 *   - the Host header must be this computer's own address, so a web page elsewhere cannot reach the server by
 *     pointing a domain name at 127.0.0.1.
 *   - five wrong PINs and phone sign-in is locked until the server restarts.
 *   - one check at a time; report files are served by name from a fixed list only.
 */
public final class WebApp {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path RUNS = Path.of(System.getProperty("user.home"), ".testthispuppy", "web-runs");
    // In the order the page shows them: the full report first.
    private static final List<String> REPORT_FILES = List.of("report.html", "code-fixes.md", "css-fixes.md", "report.json");
    private static final Set<String> LEVELS = Set.of("quick", "standard", "thorough");
    private static final int MAX_PIN_TRIES = 5;

    private final String token = randomHex(16);
    private final String pin;
    private final Set<String> allowedHosts;
    private final AtomicInteger wrongPins = new AtomicInteger();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    private volatile Run current;

    /** One check: its id, its folder, its live log, and how it ended. */
    static final class Run {
        final String id;
        final Path folder;
        final String label;
        final List<String> log = Collections.synchronizedList(new ArrayList<>());
        volatile boolean done;
        volatile int exitCode = -1;

        Run(String id, Path folder, String label) {
            this.id = id;
            this.folder = folder;
            this.label = label;
        }
    }

    private WebApp(boolean phone, Set<String> allowedHosts) {
        this.pin = phone ? String.format("%06d", new SecureRandom().nextInt(1_000_000)) : null;
        this.allowedHosts = allowedHosts;
    }

    /** Starts the server and keeps it running until the window is closed or Ctrl+C. */
    public static void run(String[] args) throws Exception {
        Map<String, String> options = QuickCheck.options(withFirst(args));
        boolean phone = options.containsKey("phone");
        int port = Integer.parseInt(options.getOrDefault("port", "7777"));
        String lan = phone ? lanAddress() : null;
        Set<String> hosts = new java.util.HashSet<>(Set.of("127.0.0.1:" + port, "localhost:" + port));
        if (lan != null) {
            hosts.add(lan + ":" + port);
        }
        WebApp app = new WebApp(phone, hosts);
        Files.createDirectories(RUNS);
        HttpServer server = HttpServer.create(new InetSocketAddress(phone ? "0.0.0.0" : "127.0.0.1", port), 0);
        server.createContext("/", app::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        String local = "http://127.0.0.1:" + port + "/#" + app.token;
        System.out.println();
        System.out.println("  TestThisPuppy is running. Open: " + local);
        if (phone) {
            System.out.println("  Phone or tablet on the same Wi-Fi: http://" + (lan == null ? "<this computer's address>" : lan) + ":" + port
                    + "   PIN: " + app.pin);
        }
        System.out.println("  Close this window (or Ctrl+C) to stop it.");
        if (!options.containsKey("no-open")) {        // --no-open: for automated tests
            openBrowser(local);
        }
        Thread.currentThread().join();
    }

    private static String[] withFirst(String[] args) {
        String[] all = new String[args.length + 1];
        all[0] = "web";
        System.arraycopy(args, 0, all, 1, args.length);
        return all;
    }

    // ── Requests ────────────────────────────────────────────────────────────────────────────────

    private void handle(HttpExchange ex) throws IOException {
        try {
            String host = ex.getRequestHeaders().getFirst("Host");
            if (host == null || !allowedHosts.contains(host.toLowerCase())) {
                send(ex, 403, "text/plain", "Not this address.");
                return;
            }
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (path.equals("/") && method.equals("GET")) {
                sendPage(ex);
            } else if (path.equals("/api/login") && method.equals("POST")) {
                login(ex);
            } else if (!authorised(ex)) {
                send(ex, 401, "application/json", "{\"error\":\"sign in first\"}");
            } else if (path.equals("/api/info") && method.equals("GET")) {
                sendJson(ex, 200, JSON.createObjectNode().put("busy", current != null && !current.done).put("phone", pin != null));
            } else if (path.equals("/api/run") && method.equals("POST")) {
                startRun(ex);
            } else if (path.equals("/api/runs") && method.equals("GET")) {
                listRuns(ex);
            } else if (path.equals("/api/dirs") && method.equals("GET")) {
                listDirs(ex);
            } else if (path.startsWith("/api/run/") && method.equals("GET")) {
                runDetail(ex, path.substring("/api/run/".length()));
            } else {
                send(ex, 404, "text/plain", "Not found.");
            }
        } catch (Exception e) {
            send(ex, 500, "application/json", JSON.createObjectNode().put("error", String.valueOf(e.getMessage())).toString());
        } finally {
            ex.close();
        }
    }

    /** The token comes in a header from the page, or in ?t= on report links (a link cannot carry a header). */
    private boolean authorised(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("X-Puppy-Token");
        String query = queryParam(ex, "t");
        return token.equals(header) || token.equals(query);
    }

    private void login(HttpExchange ex) throws IOException {
        if (pin == null || wrongPins.get() >= MAX_PIN_TRIES) {
            sendJson(ex, 403, JSON.createObjectNode().put("error", pin == null ? "phone mode is off" : "too many wrong PINs: restart TestThisPuppy"));
            return;
        }
        String given = JSON.readTree(readBody(ex)).path("pin").asText("");
        if (!pin.equals(given)) {
            int tries = wrongPins.incrementAndGet();
            sendJson(ex, 401, JSON.createObjectNode().put("error", "wrong PIN (" + (MAX_PIN_TRIES - tries) + " tries left)"));
            return;
        }
        sendJson(ex, 200, JSON.createObjectNode().put("token", token));
    }

    // ── Running a check ─────────────────────────────────────────────────────────────────────────

    private void startRun(HttpExchange ex) throws IOException {
        if (current != null && !current.done) {
            sendJson(ex, 409, JSON.createObjectNode().put("error", "a check is already running"));
            return;
        }
        JsonNode req = JSON.readTree(readBody(ex));
        String id = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path folder = RUNS.resolve(id);
        Files.createDirectories(folder);
        List<String> args;
        try {
            args = argsFor(req, folder);
        } catch (IllegalArgumentException bad) {
            sendJson(ex, 400, JSON.createObjectNode().put("error", bad.getMessage()));
            return;
        }
        Run run = new Run(id, folder, label(req));
        Files.writeString(folder.resolve("label.txt"), run.label);
        runs.put(id, run);
        current = run;
        worker.submit(() -> execute(run, args));
        sendJson(ex, 200, JSON.createObjectNode().put("id", id));
    }

    /**
     * The quick check's arguments, built from the form. Every value is checked here; the arguments go to the
     * check as a list, never through a command line, so nothing typed can become an extra option or command.
     */
    static List<String> argsFor(JsonNode req, Path folder) throws IOException {
        String kind = req.path("kind").asText();
        List<String> args = new ArrayList<>();
        switch (kind) {
            case "snippet" -> {
                String code = req.path("code").asText("");
                if (code.isBlank()) {
                    throw new IllegalArgumentException("paste some code first");
                }
                String language = req.path("language").asText("java");
                String ext = switch (language) {
                    case "java" -> ".java";
                    case "javascript" -> ".js";
                    case "css" -> ".css";
                    default -> throw new IllegalArgumentException("language must be java, javascript or css");
                };
                Path file = folder.resolve("snippet" + ext);
                Files.writeString(file, code, StandardCharsets.UTF_8);
                args.add("code:" + file);
            }
            case "project" -> {
                Path dir = Path.of(req.path("target").asText("")).toAbsolutePath().normalize();
                if (!Files.isDirectory(dir)) {
                    throw new IllegalArgumentException("that folder does not exist: " + dir);
                }
                args.add(dir.toString());
            }
            case "repository" -> {
                String url = req.path("target").asText("").trim();
                if (!url.matches("(?i)^(https://|git@)[\\w.@:/~-]+$")) {
                    throw new IllegalArgumentException("a Git address starts with https:// or git@");
                }
                args.add("repo:" + url);
                if (req.path("trust").asBoolean(false)) {
                    args.add("--trust");
                }
            }
            case "site" -> {
                String url = req.path("target").asText("").trim();
                if (!url.matches("(?i)^https?://\\S+$")) {
                    throw new IllegalArgumentException("a site address starts with http:// or https://");
                }
                args.add("site:" + url);
                if (req.path("plan").asBoolean(false)) {
                    String copy = req.path("copy").asText("http://localhost:8080").trim();
                    if (!copy.matches("(?i)^https?://\\S+$")) {
                        throw new IllegalArgumentException("the copy's address starts with http:// or https://");
                    }
                    args.add("--plan");
                    args.add("--copy");
                    args.add(copy);
                }
            }
            default -> throw new IllegalArgumentException("choose what to test");
        }
        String level = req.path("level").asText("standard");
        if (!LEVELS.contains(level)) {
            throw new IllegalArgumentException("level must be quick, standard or thorough");
        }
        args.add("--level");
        args.add(level);
        if (req.path("noTests").asBoolean(false)) {
            args.add("--no-tests");
        }
        if (req.path("noMutation").asBoolean(false)) {
            args.add("--no-mutation");
        }
        args.add("--out");
        args.add(folder.toString());
        return args;
    }

    private static String label(JsonNode req) {
        String kind = req.path("kind").asText();
        return switch (kind) {
            case "snippet" -> "Pasted " + req.path("language").asText("java") + " code";
            case "project" -> "Folder " + req.path("target").asText();
            case "repository" -> "Repository " + req.path("target").asText();
            default -> "Site " + req.path("target").asText();
        };
    }

    /** Runs the quick check, copying everything it prints into the run's log as well as this window. */
    private void execute(Run run, List<String> args) {
        PrintStream original = System.out;
        PrintStream originalErr = System.err;
        PrintStream tee = new PrintStream(new LineTee(run.log, original), true, StandardCharsets.UTF_8);
        System.setOut(tee);
        System.setErr(tee);
        try {
            run.exitCode = QuickCheck.run(args.toArray(new String[0]));
        } catch (Exception e) {
            run.log.add("TestThisPuppy stopped with an error: " + e);
            run.exitCode = 2;
        } finally {
            tee.flush();
            System.setOut(original);
            System.setErr(originalErr);
            run.done = true;
        }
    }

    /** Splits what is printed into lines for the page, and still shows it in this window. */
    static final class LineTee extends OutputStream {
        private final List<String> lines;
        private final PrintStream echo;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        LineTee(List<String> lines, PrintStream echo) {
            this.lines = lines;
            this.echo = echo;
        }

        @Override
        public synchronized void write(int b) {
            echo.write(b);
            if (b == '\n') {
                lines.add(buffer.toString(StandardCharsets.UTF_8).replace("\r", ""));
                buffer.reset();
            } else {
                buffer.write(b);
            }
        }

        @Override
        public synchronized void flush() {
            echo.flush();
            if (buffer.size() > 0) {
                lines.add(buffer.toString(StandardCharsets.UTF_8));
                buffer.reset();
            }
        }
    }

    // ── Results ─────────────────────────────────────────────────────────────────────────────────

    private void runDetail(HttpExchange ex, String rest) throws IOException {
        String[] parts = rest.split("/", 2);
        String id = parts[0];
        if (!id.matches("\\d{8}-\\d{6}")) {
            send(ex, 404, "text/plain", "No such run.");
            return;
        }
        Path folder = RUNS.resolve(id);
        if (parts.length == 2) {                       // a report file, by name from the fixed list only
            String name = parts[1];
            Path file = folder.resolve(name);
            if (!REPORT_FILES.contains(name) || !Files.exists(file)) {
                send(ex, 404, "text/plain", "No such report.");
                return;
            }
            String type = name.endsWith(".html") ? "text/html" : name.endsWith(".json") ? "application/json" : "text/plain";
            send(ex, 200, type + "; charset=utf-8", Files.readString(file));
            return;
        }
        Run run = runs.get(id);
        int from = Integer.parseInt(queryParamOr(ex, "from", "0"));
        ObjectNode out = JSON.createObjectNode().put("id", id);
        if (run != null) {
            List<String> copy;
            synchronized (run.log) {
                copy = new ArrayList<>(run.log.subList(Math.min(from, run.log.size()), run.log.size()));
            }
            ArrayNode lines = out.putArray("lines");
            copy.forEach(lines::add);
            out.put("next", from + copy.size()).put("done", run.done).put("label", run.label);
        } else {
            out.put("done", true).put("next", from).putArray("lines");
        }
        addSummary(out, folder);
        sendJson(ex, 200, out);
    }

    /** Totals and failures from report.json, plus which report files exist. */
    private static void addSummary(ObjectNode out, Path folder) throws IOException {
        Path json = folder.resolve("report.json");
        if (Files.exists(json)) {
            JsonNode report = JSON.readTree(json.toFile());
            out.put("passed", report.path("passed").asInt()).put("failed", report.path("failed").asInt());
            ArrayNode failures = out.putArray("failures");
            ArrayNode lookAt = out.putArray("lookAt");     // passed, but found issues below the fail line: still worth a look
            int issues = 0;
            for (JsonNode r : report.path("results")) {
                String name = r.path("name").asText();
                if (!r.path("passed").asBoolean()) {
                    failures.addObject().put("name", name).put("detail", r.path("detail").asText());
                } else {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+) issue").matcher(name);
                    if (m.find() && Integer.parseInt(m.group(1)) > 0) {
                        issues += Integer.parseInt(m.group(1));
                        lookAt.addObject().put("name", name).put("detail", "below the fail line; see the fix list");
                    }
                }
            }
            out.put("issues", issues);
        }
        ArrayNode files = out.putArray("files");
        for (String name : REPORT_FILES) {
            if (Files.exists(folder.resolve(name))) {
                files.add(name);
            }
        }
    }

    private void listRuns(HttpExchange ex) throws IOException {
        ArrayNode list = JSON.createArrayNode();
        try (Stream<Path> dirs = Files.list(RUNS)) {
            for (Path d : dirs.filter(Files::isDirectory).sorted(Comparator.reverseOrder()).limit(20).toList()) {
                ObjectNode item = list.addObject().put("id", d.getFileName().toString());
                Path label = d.resolve("label.txt");
                item.put("label", Files.exists(label) ? Files.readString(label) : "");
                addSummary(item, d);
            }
        }
        sendJson(ex, 200, list);
    }

    /** The folders inside a folder, so a phone can pick a project folder on this computer. */
    private void listDirs(HttpExchange ex) throws IOException {
        String given = queryParamOr(ex, "path", System.getProperty("user.home"));
        Path dir = Path.of(given).toAbsolutePath().normalize();
        ObjectNode out = JSON.createObjectNode().put("path", dir.toString());
        out.put("parent", dir.getParent() == null ? "" : dir.getParent().toString());
        ArrayNode dirs = out.putArray("dirs");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(Files::isDirectory).filter(p -> !p.getFileName().toString().startsWith("."))
                        .map(p -> p.getFileName().toString()).sorted(String.CASE_INSENSITIVE_ORDER).limit(300).forEach(dirs::add);
            } catch (IOException noAccess) {
                out.put("error", "cannot open that folder");
            }
        }
        sendJson(ex, 200, out);
    }

    // ── Small helpers ───────────────────────────────────────────────────────────────────────────

    private void sendPage(HttpExchange ex) throws IOException {
        try (InputStream in = WebApp.class.getResourceAsStream("/web/index.html")) {
            if (in == null) {
                send(ex, 500, "text/plain", "The page is missing from the build.");
                return;
            }
            ex.getResponseHeaders().set("Content-Security-Policy", "default-src 'self' 'unsafe-inline'; frame-ancestors 'none'");
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            send(ex, 200, "text/html; charset=utf-8", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void sendJson(HttpExchange ex, int status, JsonNode body) throws IOException {
        send(ex, status, "application/json", JSON.writeValueAsString(body));
    }

    private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        byte[] body = ex.getRequestBody().readNBytes(2_000_001);
        if (body.length > 2_000_000) {
            throw new IOException("request too large");
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private static String queryParam(HttpExchange ex, String name) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) {
            return null;
        }
        for (String pair : q.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String queryParamOr(HttpExchange ex, String name, String fallback) {
        String v = queryParam(ex, name);
        return v == null ? fallback : v;
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    /** This computer's address on the home network (the first private IPv4 address found). */
    private static String lanAddress() throws IOException {
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                continue;
            }
            for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                    return a.getHostAddress();
                }
            }
        }
        return null;
    }

    private static void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (IOException ignored) {
            // the address is printed above; opening it by hand works the same
        }
    }
}
