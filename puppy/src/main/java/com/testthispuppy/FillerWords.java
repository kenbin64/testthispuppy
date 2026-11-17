package com.testthispuppy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The Latin filler words used for every dummy text value, read from filler-words.csv
 * (one word per line under a "word" header). Edit the CSV to change the words; no code change needed.
 */
public final class FillerWords {
    private static final List<String> WORDS = load();

    private FillerWords() {
    }

    /** The word at this position, wrapping round the list, so any number picks a word. */
    public static String word(int position) {
        return WORDS.get(Math.floorMod(position, WORDS.size()));
    }

    /** A word with a capital first letter, for names. */
    public static String capitalized(int position) {
        String word = word(position);
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    public static int count() {
        return WORDS.size();
    }

    private static List<String> load() {
        try (InputStream stream = FillerWords.class.getResourceAsStream("/filler-words.csv")) {
            if (stream == null) {
                throw new IllegalStateException("filler-words.csv is missing from the classpath");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            List<String> words = reader.lines().map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.equalsIgnoreCase("word")).toList();
            if (words.isEmpty()) {
                throw new IllegalStateException("filler-words.csv has no words");
            }
            return words;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
