package com.pocket.rpg.editor.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MainThreadQueueTest {

    @BeforeEach
    void setUp() {
        // Drain any leftover tasks from previous tests
        MainThreadQueue.drain();
    }

    @Nested
    class Drain {
        @Test
        void executesEnqueuedTask() {
            boolean[] ran = {false};
            MainThreadQueue.enqueue(() -> ran[0] = true);
            MainThreadQueue.drain();
            assertTrue(ran[0]);
        }

        @Test
        void executesMultipleTasksInOrder() {
            List<Integer> order = new ArrayList<>();
            MainThreadQueue.enqueue(() -> order.add(1));
            MainThreadQueue.enqueue(() -> order.add(2));
            MainThreadQueue.enqueue(() -> order.add(3));
            MainThreadQueue.drain();
            assertEquals(List.of(1, 2, 3), order);
        }

        @Test
        void doesNothingWhenEmpty() {
            // Should not throw
            MainThreadQueue.drain();
        }

        @Test
        void continuesAfterTaskException() {
            boolean[] secondRan = {false};
            MainThreadQueue.enqueue(() -> { throw new RuntimeException("boom"); });
            MainThreadQueue.enqueue(() -> secondRan[0] = true);
            MainThreadQueue.drain();
            assertTrue(secondRan[0]);
        }
    }

    @Nested
    class ThreadSafety {
        @Test
        void enqueueFromBackgroundThread() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            boolean[] ran = {false};

            Thread t = new Thread(() -> {
                MainThreadQueue.enqueue(() -> ran[0] = true);
                latch.countDown();
            });
            t.start();
            assertTrue(latch.await(2, TimeUnit.SECONDS));

            MainThreadQueue.drain();
            assertTrue(ran[0]);
        }
    }
}
