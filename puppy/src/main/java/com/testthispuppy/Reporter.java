package com.testthispuppy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** Prints the results as a table and writes them to a JSON file for other tools to read. */
public final class Reporter {
    private Reporter() {
    }

    public static void print(List<TestResult> results) {
        System.out.println();
        for (TestResult r : results) {
            System.out.printf("  %-4s  %-8s %-34s %5d ms  %s%n", r.passed() ? "PASS" : "FAIL", r.kind(), r.name(), r.millis(), r.detail());
        }
        long passed = results.stream().filter(TestResult::passed).count();
        System.out.printf("%n  %d passed, %d failed%n%n", passed, results.size() - passed);
    }

    public static void write(List<TestResult> results, Path file) throws IOException {
        String items = results.stream().map(r -> String.format(
                "    {\"kind\": %s, \"name\": %s, \"passed\": %b, \"millis\": %d, \"detail\": %s}",
                json(r.kind()), json(r.name()), r.passed(), r.millis(), json(r.detail()))).collect(Collectors.joining(",\n"));
        long passed = results.stream().filter(TestResult::passed).count();
        String text = "{\n  \"ranAt\": " + json(LocalDateTime.now().withNano(0).toString()) + ",\n  \"passed\": " + passed
                + ",\n  \"failed\": " + (results.size() - passed) + ",\n  \"results\": [\n" + items + "\n  ]\n}\n";
        Files.writeString(file, text);
    }

    /** A self-contained HTML report: totals by kind of test, a failures-only filter, and every result. */
    public static void writeHtml(List<TestResult> results, Path file) throws IOException {
        long passed = results.stream().filter(TestResult::passed).count();
        long failed = results.size() - passed;
        java.util.Map<String, long[]> byKind = new java.util.LinkedHashMap<>();
        for (TestResult r : results) {
            long[] counts = byKind.computeIfAbsent(r.kind(), k -> new long[2]);
            counts[r.passed() ? 0 : 1]++;
        }
        String kinds = byKind.entrySet().stream().map(e -> "<div class=\"kind\"><b>" + html(e.getKey()) + "</b><span>"
                + e.getValue()[0] + " passed" + (e.getValue()[1] > 0 ? ", <em>" + e.getValue()[1] + " failed</em>" : "") + "</span></div>")
                .collect(Collectors.joining());
        String rows = results.stream().map(r -> "<tr class=\"" + (r.passed() ? "pass" : "fail") + "\"><td class=\"st\">"
                + (r.passed() ? "PASS" : "FAIL") + "</td><td>" + html(r.kind()) + "</td><td>" + html(r.name()) + "</td><td class=\"ms\">"
                + r.millis() + " ms</td><td>" + html(r.detail()) + "</td></tr>").collect(Collectors.joining("\n"));
        String page = """
                <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>TestThisPuppy report</title><style>
                :root{--bg:#f6f7f9;--card:#fff;--ink:#16202a;--mute:#5b6875;--line:#dde3e9;--ok:#0f7b4a;--bad:#b3261e}
                @media (prefers-color-scheme:dark){:root{--bg:#0d1117;--card:#161b22;--ink:#e6edf3;--mute:#9aa7b4;--line:#2b333d;--ok:#3fb97a;--bad:#ff7b72}}
                body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.45 system-ui,Segoe UI,sans-serif}
                main{max-width:1200px;margin:0 auto;padding:24px 16px}
                h1{margin:0 0 4px;font-size:24px} .sub{color:var(--mute);margin:0 0 18px}
                .totals{display:flex;gap:12px;flex-wrap:wrap;margin-bottom:14px}
                .big{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 18px}
                .big b{display:block;font-size:28px} .big.ok b{color:var(--ok)} .big.bad b{color:var(--bad)}
                .kinds{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:8px;margin-bottom:16px}
                .kind{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:8px 12px;display:flex;flex-direction:column}
                .kind span{color:var(--mute);font-size:13px} .kind em{color:var(--bad);font-style:normal}
                label{display:inline-flex;gap:6px;align-items:center;margin-bottom:10px;cursor:pointer}
                .wrap{overflow-x:auto;background:var(--card);border:1px solid var(--line);border-radius:10px}
                table{border-collapse:collapse;width:100%%;min-width:760px} td{padding:7px 10px;border-top:1px solid var(--line);vertical-align:top}
                .st{font-weight:700} tr.pass .st{color:var(--ok)} tr.fail .st{color:var(--bad)} .ms{color:var(--mute);white-space:nowrap}
                body.only-fail tr.pass{display:none}
                </style></head><body><main>
                <h1>TestThisPuppy report</h1><p class="sub">Ran %s. Every row is one step or test, in the order it ran.</p>
                <div class="totals"><div class="big ok"><b>%d</b>passed</div><div class="big bad"><b>%d</b>failed</div><div class="big"><b>%d</b>in all</div></div>
                <div class="kinds">%s</div>
                <label><input type="checkbox" onchange="document.body.classList.toggle('only-fail', this.checked)"> Show only failures</label>
                <div class="wrap"><table>%s</table></div>
                </main></body></html>
                """.formatted(html(LocalDateTime.now().withNano(0).toString().replace('T', ' ')), passed, failed, results.size(), kinds, rows);
        Files.writeString(file, page);
    }

    private static String html(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String json(String text) {
        String safe = text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
        return "\"" + safe + "\"";
    }
}
