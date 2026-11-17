package com.testthispuppy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/**
 * Demo recording: when switched on (demo.frames), saves a screenshot with a caption at each browser step,
 * and pauses a moment (demo.pauseMs) so a person watching can follow. frames.json lists every frame in order,
 * which the demo player on the website replays. Off by default, so normal runs are not slowed down.
 */
public final class FrameRecorder {
    private static Path folder;
    private static long pauseMs;
    private static final List<String[]> FRAMES = new ArrayList<>();

    private FrameRecorder() {
    }

    public static void start(Path frameFolder, long pause) throws IOException {
        folder = frameFolder;
        pauseMs = pause;
        FRAMES.clear();
        Files.createDirectories(folder);
        try (var old = Files.list(folder)) {
            for (Path file : old.toList()) {
                Files.delete(file);
            }
        }
    }

    public static boolean on() {
        return folder != null;
    }

    /** Saves the browser as it is now, with a caption saying what just happened. */
    public static void capture(WebDriver driver, String caption) {
        if (folder != null) {
            try {
                String name = String.format("frame-%03d.png", FRAMES.size() + 1);
                Files.write(folder.resolve(name), ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
                FRAMES.add(new String[] {name, caption});
                Thread.sleep(pauseMs);
            } catch (Exception e) {
                // A missed demo frame never fails a test.
            }
        }
    }

    /** Writes frames.json: [{"file": ..., "caption": ...}, ...] in the order captured. */
    public static void finish() throws IOException {
        if (folder != null) {
            StringBuilder json = new StringBuilder("[\n");
            for (int i = 0; i < FRAMES.size(); i++) {
                json.append("  {\"file\": \"").append(FRAMES.get(i)[0]).append("\", \"caption\": \"")
                        .append(FRAMES.get(i)[1].replace("\\", "\\\\").replace("\"", "\\\"")).append("\"}")
                        .append(i < FRAMES.size() - 1 ? ",\n" : "\n");
            }
            Files.writeString(folder.resolve("frames.json"), json.append("]\n").toString());
            folder = null;
        }
    }
}
