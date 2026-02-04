package com.pocket.rpg.editor.core;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe queue for executing tasks on the main (render) thread.
 * Background threads enqueue runnables; the main loop drains them once per frame.
 *
 * <p><b>Important:</b> ImGui is single-threaded and may only be called from the main thread.
 * Any code that touches ImGui state (widgets, windows, draw lists, IO) must be enqueued
 * here rather than called directly from a background thread.</p>
 */
public final class MainThreadQueue {

    private static final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();

    private MainThreadQueue() {}

    /**
     * Enqueue a task to run on the main thread. Thread-safe.
     */
    public static void enqueue(Runnable task) {
        queue.add(task);
    }

    /**
     * Drain all queued tasks, executing each on the calling (main) thread.
     * Exceptions in individual tasks are caught and printed so one failure
     * doesn't prevent subsequent tasks from running.
     */
    public static void drain() {
        Runnable task;
        while ((task = queue.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                System.err.println("MainThreadQueue task failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
