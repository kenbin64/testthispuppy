package com.testthispuppy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads an application log without any AI: pulls out every stack trace, finds each one's root cause
 * (the deepest "Caused by"), groups repeats together, and keeps the ERROR lines.
 * It also hides secrets, so the summary is safe to show or to hand to an AI.
 */
public final class LogAnalyzer {
    private static final Pattern EXCEPTION_LINE = Pattern.compile("^(Caused by: )?([\\w$.]+(Exception|Error|Throwable))(: .*)?$");
    private static final Pattern FRAME_LINE = Pattern.compile("^\\s+at\\s.+");
    private static final Pattern ERROR_LINE = Pattern.compile("^.*\\bERROR\\b.*$");

    private LogAnalyzer() {
    }

    /**
     * One distinct problem found in the log.
     *
     * @param rootCause  the deepest exception and its message, the real reason
     * @param headline   the first exception reported, what the app saw
     * @param where      the first stack frame of the root cause, where it happened
     * @param count      how many times this exact root cause appeared
     */
    public record Finding(String rootCause, String headline, String where, int count) {
    }

    /** What the log held: distinct problems, and the ERROR lines (both with secrets hidden). */
    public record Summary(List<Finding> findings, List<String> errorLines) {
        public boolean clean() {
            return findings.isEmpty() && errorLines.isEmpty();
        }
    }

    public static Summary analyze(String log, List<String> secrets) {
        String safe = redact(log == null ? "" : log, secrets);
        List<String> lines = safe.lines().toList();
        Map<String, Finding> byRoot = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            var exception = EXCEPTION_LINE.matcher(line.trim());
            boolean startsTrace = exception.matches() && exception.group(1) == null
                    && i + 1 < lines.size() && FRAME_LINE.matcher(lines.get(i + 1)).matches();
            if (startsTrace) {
                String headline = line.trim();
                String root = headline;
                String where = lines.get(i + 1).trim();
                i++;
                while (i < lines.size() && (FRAME_LINE.matcher(lines.get(i)).matches()
                        || lines.get(i).trim().startsWith("Caused by:") || lines.get(i).trim().startsWith("... "))) {
                    String current = lines.get(i).trim();
                    if (current.startsWith("Caused by:")) {
                        root = current.substring("Caused by: ".length());
                        where = i + 1 < lines.size() && FRAME_LINE.matcher(lines.get(i + 1)).matches() ? lines.get(i + 1).trim() : where;
                    }
                    i++;
                }
                Finding seen = byRoot.get(root);
                byRoot.put(root, new Finding(root, headline, where, seen == null ? 1 : seen.count() + 1));
            } else {
                if (ERROR_LINE.matcher(line).matches() && errors.size() < 50) {
                    errors.add(line.trim());
                }
                i++;
            }
        }
        return new Summary(new ArrayList<>(byRoot.values()), errors);
    }

    /** Hides the given secrets, and anything that looks like a password, token or API key. */
    public static String redact(String text, List<String> secrets) {
        String safe = text;
        for (String secret : secrets) {
            if (secret != null && secret.length() >= 3) {
                safe = safe.replace(secret, "[hidden]");
            }
        }
        safe = safe.replaceAll("(?i)(password|passwd|pwd|secret|token|apikey|api_key)(\\s*[=:]\\s*)[^\\s,;&\"']+", "$1$2[hidden]");
        safe = safe.replaceAll("(?i)(authorization:\\s*bearer\\s+)\\S+", "$1[hidden]");
        safe = safe.replaceAll("sk-ant-[A-Za-z0-9_-]+", "[hidden-key]");
        safe = safe.replaceAll("(jdbc:[a-z]+://)[^:/@\\s]+:[^@\\s]+@", "$1[hidden]@");
        return safe;
    }
}
