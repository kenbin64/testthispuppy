package com.testthispuppy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Starts the application under test as its own process, waits until it answers HTTP, and stops it at the end.
 * Used in internal mode, so the app can be pointed at the throwaway database.
 */
public final class AppProcess implements AutoCloseable {
    private final Process process;

    private AppProcess(Process process) {
        this.process = process;
    }

    /** Starts the command and waits up to two minutes for any HTTP answer at baseUrl. */
    public static AppProcess start(List<String> command, Path workingFolder, Path logFile, String baseUrl) throws Exception {
        Process process = new ProcessBuilder(command).directory(workingFolder.toFile())
                .redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
        AppProcess app = new AppProcess(process);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        long deadline = System.currentTimeMillis() + 120_000;
        boolean answering = false;
        while (!answering && process.isAlive() && System.currentTimeMillis() < deadline) {
            try {
                client.send(HttpRequest.newBuilder(URI.create(baseUrl)).timeout(Duration.ofSeconds(2)).build(),
                        HttpResponse.BodyHandlers.discarding());
                answering = true;
            } catch (Exception notYet) {
                Thread.sleep(500);
            }
        }
        if (!answering) {
            app.close();
            throw new IllegalStateException("The app did not start answering at " + baseUrl + "; see " + logFile);
        }
        return app;
    }

    @Override
    public void close() {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(java.time.Duration.ofSeconds(15).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
