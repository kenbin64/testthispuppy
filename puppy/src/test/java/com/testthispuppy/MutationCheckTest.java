package com.testthispuppy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Reading PIT's report and building its command, without running PIT. */
class MutationCheckTest {

    private static String mutation(String status, String file, int line, String description) {
        return "<mutation status='" + status + "'><sourceFile>" + file + "</sourceFile><mutatedMethod>save</mutatedMethod>"
                + "<lineNumber>" + line + "</lineNumber><description>" + description + "</description></mutation>";
    }

    private static String report(String... mutations) {
        return "<?xml version='1.0'?><mutations>" + String.join("", mutations) + "</mutations>";
    }

    @Test
    void scoresCaughtBugsAndListsEachOneMissed() {
        List<TestResult> results = MutationCheck.readReport(report(
                mutation("KILLED", "A.java", 3, "negated conditional"),
                mutation("KILLED", "A.java", 4, "removed call"),
                mutation("TIMED_OUT", "A.java", 5, "changed increment"),
                mutation("SURVIVED", "B.java", 9, "removed call to setName"),
                mutation("NO_COVERAGE", "C.java", 2, "replaced return value with null"),
                mutation("NON_VIABLE", "C.java", 7, "does not load")), 80, 1000);
        assertEquals("test the tests: 3 of 5 planted bugs caught (60%)", results.get(0).name(), "a bug that would not load is not counted");
        assertFalse(results.get(0).passed(), "60% is under the 80% asked for");
        assertEquals(3, results.size(), "the score line, then one line per bug not caught");
        assertEquals("not caught: B.java:9", results.get(1).name());
        assertFalse(results.get(1).passed(), "below the score, each bug not caught is part of the failure");
        assertTrue(results.get(1).detail().contains("did not notice"));
        assertTrue(results.get(2).detail().contains("no test runs this line"));
    }

    @Test
    void passesAtTheMinimumScore() {
        List<TestResult> results = MutationCheck.readReport(report(
                mutation("KILLED", "A.java", 1, "x"), mutation("KILLED", "A.java", 2, "x"),
                mutation("KILLED", "A.java", 3, "x"), mutation("KILLED", "A.java", 4, "x"),
                mutation("SURVIVED", "A.java", 5, "x")), 80, 0);
        assertTrue(results.get(0).passed(), "4 of 5 is exactly 80%");
        assertTrue(results.get(1).passed(), "with the score met, a bug not caught is listed to look at, not a failure");
    }

    @Test
    void aBrokenReportIsAFailureNotACrash() {
        List<TestResult> results = MutationCheck.readReport("not xml", 80, 0);
        assertEquals(1, results.size());
        assertFalse(results.get(0).passed());
    }

    @Test
    void theLevelPicksHowManyKindsOfBugToPlant() {
        assertTrue(MutationCheck.command("quick", "", "").contains("-Dmutators=DEFAULTS"));
        assertTrue(MutationCheck.command("standard", "", "").contains("-Dmutators=STRONGER"));
        assertTrue(MutationCheck.command("thorough", "", "").contains("-Dmutators=ALL"));
        List<String> command = MutationCheck.command("quick", "com.example.*", "com.example.DbTest, com.example.SlowTest");
        assertTrue(command.contains("-DtargetClasses=com.example.*"));
        assertTrue(command.contains("-DexcludedTestClasses=com.example.DbTest,com.example.SlowTest"));
        assertFalse(MutationCheck.command("quick", "", "").stream().anyMatch(a -> a.startsWith("-DtargetClasses")), "blank settings add nothing");
    }
}
