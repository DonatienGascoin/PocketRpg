package com.pocket.rpg.editor.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Runs {@code mvnw compile} in a background daemon thread.
 * Log output streams into {@link #getOutputLines()} for real-time display.
 * Callbacks are always dispatched to the main thread via {@link MainThreadQueue}.
 */
public final class MavenCompiler {

    private static volatile boolean compiling;
    private static final CopyOnWriteArrayList<String> outputLines = new CopyOnWriteArrayList<>();

    private MavenCompiler() {}

    /**
     * @return true while a background compilation is in progress.
     */
    public static boolean isCompiling() {
        return compiling;
    }

    /**
     * @return the log lines produced by the current (or last) compilation.
     * Thread-safe for reading from the main thread while the background thread writes.
     */
    public static List<String> getOutputLines() {
        return outputLines;
    }

    /**
     * Start an asynchronous Maven compile.
     * Does nothing if a compile is already running.
     *
     * @param onSuccess called on the main thread when compilation succeeds
     * @param onFailure called on the main thread with truncated output when compilation fails
     */
    public static void compileAsync(Runnable onSuccess, Consumer<String> onFailure) {
        if (compiling) return;
        compiling = true;
        outputLines.clear();

        Thread thread = new Thread(() -> {
            try {
                String workingDir = System.getProperty("user.dir");
                boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
                String mvnCmd = isWindows ? "mvnw.cmd" : "./mvnw";

                ProcessBuilder pb = new ProcessBuilder(mvnCmd, "compile");
                pb.directory(new java.io.File(workingDir));
                pb.redirectErrorStream(true);

                Process process = pb.start();

                // Read output to prevent buffer deadlock
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append('\n');
                        outputLines.add(line);
                    }
                }

                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    MainThreadQueue.enqueue(() -> {
                        compiling = false;
                        onSuccess.run();
                    });
                } else {
                    String fullOutput = output.toString();
                    System.err.println("Maven compile failed:\n" + fullOutput);
                    String truncated = fullOutput.length() > 500
                            ? fullOutput.substring(fullOutput.length() - 500)
                            : fullOutput;
                    MainThreadQueue.enqueue(() -> {
                        compiling = false;
                        onFailure.accept(truncated);
                    });
                }
            } catch (Exception e) {
                System.err.println("Maven compile exception: " + e.getMessage());
                e.printStackTrace();
                MainThreadQueue.enqueue(() -> {
                    compiling = false;
                    onFailure.accept(e.getMessage());
                });
            }
        }, "maven-compile");
        thread.setDaemon(true);
        thread.start();
    }
}
