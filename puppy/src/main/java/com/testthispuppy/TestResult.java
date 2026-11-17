package com.testthispuppy;

/**
 * The outcome of one step or test.
 *
 * @param kind   what sort of step: setup, http, browser or cleanup
 * @param name   which test, in plain words
 * @param passed true when it did what was expected
 * @param detail what happened, especially when it failed
 * @param millis how long it took
 */
public record TestResult(String kind, String name, boolean passed, String detail, long millis) {
}
