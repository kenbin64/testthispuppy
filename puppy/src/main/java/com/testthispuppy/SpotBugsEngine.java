package com.testthispuppy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * A second code engine for Java beside PMD: SpotBugs with the FindSecBugs plugin. It reads compiled classes, so it
 * finds things PMD's source rules miss (a shared date formatter, Math.abs on a hash code, injection paths). Because
 * it needs a build, it runs only on a local Maven project, never on code fetched from a URL: building runs the
 * project's own build steps, which is not something to do with code you have not looked at.
 */
final class SpotBugsEngine {
    static final String PLUGIN = "com.github.spotbugs:spotbugs-maven-plugin:4.10.5.0:spotbugs";
    static final String FIND_SEC_BUGS = "com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0";

    private SpotBugsEngine() {
    }

    static List<CodeCheck.Issue> run(Path root, Path log) throws IOException, InterruptedException {
        String mvn = System.getProperty("os.name", "").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";
        int got = new ProcessBuilder(mvn, "-q", "dependency:get", "-Dartifact=" + FIND_SEC_BUGS).directory(root.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start().waitFor();
        Path jar = Path.of(System.getProperty("user.home"), ".m2", "repository", "com", "h3xstream", "findsecbugs", "findsecbugs-plugin", "1.14.0", "findsecbugs-plugin-1.14.0.jar");
        List<String> command = new ArrayList<>(List.of(mvn, "-q", "compile", PLUGIN, "-Dspotbugs.xmlOutput=true", "-Dspotbugs.failOnError=false"));
        if (got == 0 && Files.exists(jar)) {
            command.add("-Dspotbugs.pluginList=" + jar);
        }
        int exit = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start().waitFor();
        List<Path> reports;
        try (Stream<Path> files = Files.walk(root)) {
            reports = files.filter(f -> f.getFileName().toString().equals("spotbugsXml.xml")).toList();
        }
        if (exit != 0 && reports.isEmpty()) {
            throw new IOException("SpotBugs did not run (exit " + exit + "); the project must build with Maven. See " + log.getFileName());
        }
        List<CodeCheck.Issue> issues = new ArrayList<>();
        for (Path report : reports) {
            issues.addAll(read(Files.readString(report), root));
        }
        return issues;
    }

    /** SpotBugs' XML as issues. sourcepath (com/x/A.java) is turned back into the file's place in the project. */
    static List<CodeCheck.Issue> read(String xml, Path root) throws IOException {
        NodeList bugs;
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            bugs = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .getElementsByTagName("BugInstance");
        } catch (Exception e) {
            throw new IOException("could not read the SpotBugs report: " + e.getMessage());
        }
        List<CodeCheck.Issue> issues = new ArrayList<>();
        for (int i = 0; i < bugs.getLength(); i++) {
            Element bug = (Element) bugs.item(i);
            Element line = firstChild(bug, "SourceLine");
            String source = line == null ? "?" : line.getAttribute("sourcepath");
            int at = line == null || line.getAttribute("start").isEmpty() ? 0 : Integer.parseInt(line.getAttribute("start"));
            issues.add(new CodeCheck.Issue(groupFor(bug.getAttribute("category")), locate(root, source), at,
                    priorityFor(Integer.parseInt(bug.getAttribute("rank").isEmpty() ? "20" : bug.getAttribute("rank"))),
                    bug.getAttribute("type"), text(bug, "LongMessage"), text(bug, "ShortMessage") + " (SpotBugs)",
                    "https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#" + bug.getAttribute("type").toLowerCase().replace('_', '-')));
        }
        return issues;
    }

    static String groupFor(String category) {
        return switch (category) {
            case "SECURITY", "MALICIOUS_CODE" -> "security";
            case "CORRECTNESS", "MT_CORRECTNESS" -> "bugs";
            case "BAD_PRACTICE", "PERFORMANCE" -> "practices";
            default -> "style";
        };
    }

    /** SpotBugs' rank (1 scariest to 20) to the code check's priority (1 high to 5 low). */
    static int priorityFor(int rank) {
        return rank <= 4 ? 1 : rank <= 9 ? 2 : rank <= 14 ? 3 : 4;
    }

    private static String locate(Path root, String sourcepath) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(f -> f.toString().replace('\\', '/').endsWith("/" + sourcepath) && !f.toString().contains("target"))
                    .findFirst().map(f -> root.relativize(f).toString().replace('\\', '/')).orElse(sourcepath);
        }
    }

    private static Element firstChild(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getParentNode() == parent) {
                return (Element) nodes.item(i);
            }
        }
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }
}
