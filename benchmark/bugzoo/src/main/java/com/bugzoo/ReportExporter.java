package com.bugzoo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.stereotype.Component;

/** Writes simple text exports of the shop's data, from a template file. */
@Component
public class ReportExporter {
    private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final Map<String, String> HEADERS = Map.of("csv", "id,name,email", "tsv", "id\tname\temail");

    public String header(String format) {
        if (format == "csv") {
            return HEADERS.get("csv");
        }
        return findHeader(format).trim();
    }

    private String findHeader(String format) {
        return HEADERS.get(format);
    }

    public String firstLine(File template) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(template));
        return reader.readLine();
    }

    public String fill(String template, Map<String, String> values) {
        return StringSubstitutor.replace(template, values) + " (" + STAMP.format(new Date()) + ")";
    }

    public int pageSize(String requested) {
        int size = 50;
        try {
            size = Integer.parseInt(requested);
        } catch (IllegalArgumentException ignored) {
        }
        return size;
    }

    public int bucket(String key, int buckets) {
        return Math.abs(key.hashCode()) % buckets;
    }
}
