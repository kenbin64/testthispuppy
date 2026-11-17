package com.testthispuppy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the log reader (no AI): root causes, grouping repeats, ERROR lines, and hiding secrets. */
class LogAnalyzerTest {
    // Shaped like a real Spring Boot log: the same database failure twice, plus an unrelated error line.
    private static final String LOG = """
            2026-11-16 INFO  Started DemoApplication in 3.1 seconds
            2026-11-16 ERROR o.a.c.c.C.[dispatcherServlet] Servlet.service() threw exception
            org.springframework.dao.DataIntegrityViolationException: could not execute statement
            \tat org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:290)
            \tat com.testthispuppy.demo.PageController.saveOrder(PageController.java:61)
            Caused by: org.postgresql.util.PSQLException: ERROR: insert or update on table "purchase_order" violates foreign key constraint
            \tat org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2725)
            \t... 52 more
            2026-11-16 ERROR o.a.c.c.C.[dispatcherServlet] Servlet.service() threw exception
            org.springframework.dao.DataIntegrityViolationException: could not execute statement
            \tat org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:290)
            Caused by: org.postgresql.util.PSQLException: ERROR: insert or update on table "purchase_order" violates foreign key constraint
            \tat org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2725)
            2026-11-16 ERROR c.z.h.pool.HikariPool connecting with password=hunter2 to jdbc:postgresql://puppy:s3cret@db:5432/app
            """;

    @Test
    void findsTheDeepestCauseAndGroupsRepeats() {
        LogAnalyzer.Summary summary = LogAnalyzer.analyze(LOG, List.of());
        assertEquals(1, summary.findings().size(), "the same root cause twice is one finding");
        LogAnalyzer.Finding finding = summary.findings().get(0);
        assertTrue(finding.rootCause().startsWith("org.postgresql.util.PSQLException"), finding.rootCause());
        assertTrue(finding.headline().startsWith("org.springframework.dao.DataIntegrityViolationException"));
        assertTrue(finding.where().contains("QueryExecutorImpl"), "where the root cause happened: " + finding.where());
        assertEquals(2, finding.count());
    }

    @Test
    void keepsErrorLinesThatAreNotPartOfATrace() {
        LogAnalyzer.Summary summary = LogAnalyzer.analyze(LOG, List.of());
        assertEquals(3, summary.errorLines().size());
        assertFalse(summary.clean());
    }

    @Test
    void hidesPasswordsCredentialsInUrlsAndKeys() {
        String safe = LogAnalyzer.redact(LOG + "\nkey sk-ant-abc123_XYZ and Authorization: Bearer eyJhbGciOi", List.of("puppy-db-pass"));
        assertFalse(safe.contains("hunter2"), "password=... is hidden");
        assertFalse(safe.contains("s3cret"), "user:password@ in a JDBC URL is hidden");
        assertFalse(safe.contains("sk-ant-abc123"), "API keys are hidden");
        assertFalse(safe.contains("eyJhbGciOi"), "bearer tokens are hidden");
        assertTrue(safe.contains("jdbc:postgresql://[hidden]@db:5432/app"), "the host stays readable");
    }

    @Test
    void hidesGivenSecretsWherever_theyAppear() {
        assertEquals("connect as [hidden] now", LogAnalyzer.redact("connect as puppy-db-pass now", List.of("puppy-db-pass")));
    }

    @Test
    void aCleanLogIsClean() {
        assertTrue(LogAnalyzer.analyze("INFO all good\nINFO still good", List.of()).clean());
    }

    @Test
    void thePromptForTheAiCarriesTheEvidenceButNoSecrets() {
        LogAnalyzer.Summary summary = LogAnalyzer.analyze(LOG, List.of());
        String prompt = AiAnalyzer.prompt(List.of(new TestResult("wrong", "fill the order form", false, "the app CRASHED", 0)), summary);
        assertTrue(prompt.contains("fill the order form"));
        assertTrue(prompt.contains("violates foreign key constraint"));
        assertFalse(prompt.contains("hunter2") || prompt.contains("s3cret"), "nothing secret reaches the AI");
        assertTrue(prompt.contains("not enough to tell"), "it asks the AI to say so instead of guessing");
    }
}
