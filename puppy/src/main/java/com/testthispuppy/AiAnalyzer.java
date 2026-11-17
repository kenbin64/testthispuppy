package com.testthispuppy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Asks Claude to explain failed tests, using the redacted log findings as evidence.
 * Only redacted text is sent: LogAnalyzer hides passwords, tokens and keys first.
 * Off unless ai.analysis=claude, because it costs money and logs can hold real data.
 * Credentials come from the usual places (ANTHROPIC_API_KEY, or an `ant auth login` profile).
 */
public final class AiAnalyzer {
    private static final String MODEL = "claude-opus-5-5";
    private static final int MAX_EVIDENCE_CHARS = 20_000;

    private AiAnalyzer() {
    }

    public static String prompt(List<TestResult> failures, LogAnalyzer.Summary logs) {
        String failed = failures.stream()
                .map(r -> "- [" + r.kind() + "] " + r.name() + ": " + r.detail())
                .collect(Collectors.joining("\n"));
        String findings = logs.findings().stream()
                .map(f -> "- root cause: " + f.rootCause() + "\n  first reported as: " + f.headline()
                        + "\n  at: " + f.where() + "\n  seen " + f.count() + " time(s)")
                .collect(Collectors.joining("\n"));
        String errors = String.join("\n", logs.errorLines());
        String evidence = ("FAILED TESTS\n" + failed + "\n\nSTACK TRACE ROOT CAUSES\n" + (findings.isEmpty() ? "(none)" : findings)
                + "\n\nERROR LOG LINES\n" + (errors.isEmpty() ? "(none)" : errors));
        if (evidence.length() > MAX_EVIDENCE_CHARS) {
            evidence = evidence.substring(0, MAX_EVIDENCE_CHARS) + "\n[evidence shortened to fit]";
        }
        return "You are helping a developer understand why automated tests failed against a Java Spring Boot application "
                + "with a PostgreSQL database. Below are the failed tests and evidence from the application log "
                + "(secrets already hidden).\n\n" + evidence
                + "\n\nFor each failed test, give: the most likely cause, the evidence that points to it, and a concrete fix. "
                + "Say plainly when the evidence is not enough to tell, instead of guessing. Keep it short and use plain language.";
    }

    /** Returns Claude's explanation, or a plain reason why no analysis was made. Never throws. */
    public static String explain(List<TestResult> failures, LogAnalyzer.Summary logs) {
        String answer;
        try {
            AnthropicClient client = AnthropicOkHttpClient.fromEnv();
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(16000L)
                    .addBeta("server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                    .addUserMessage(prompt(failures, logs))
                    .build();
            BetaMessage response = client.beta().messages().create(params);
            boolean refused = response.stopReason().map(reason -> reason.toString().equals("refusal")).orElse(false);
            answer = refused ? "The AI declined to analyze this evidence." : response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .collect(Collectors.joining("\n"));
        } catch (RateLimitException e) {
            answer = "AI analysis skipped: rate limited, try again later.";
        } catch (AnthropicServiceException e) {
            answer = "AI analysis skipped: the API refused the request (" + e.statusCode() + ").";
        } catch (AnthropicIoException e) {
            answer = "AI analysis skipped: could not reach the API (" + e.getMessage() + ").";
        } catch (RuntimeException e) {
            answer = "AI analysis skipped: " + e.getMessage();
        }
        return answer;
    }
}
