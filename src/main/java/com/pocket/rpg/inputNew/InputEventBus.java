package com.pocket.rpg.inputNew;

import com.pocket.rpg.inputNew.events.*;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central event bus for dispatching input events to registered listeners.
 * Thread-safe and supports priority ordering.
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * InputEventBus eventBus = new InputEventBus();
 *
 * // Register listeners
 * eventBus.register(player);
 * eventBus.register(ui);
 *
 * // Post events (usually done by InputManager)
 * eventBus.post(new KeyEvent(KeyCode.W, KeyEvent.Action.PRESS, false, false, false));
 * }</pre>
 */
public class InputEventBus {

    // Use CopyOnWriteArrayList for thread-safe iteration during modification
    private final List<InputEventListener> listeners = new CopyOnWriteArrayList<>();
    private boolean sorted = true;
    /**
     * -- SETTER --
     *  Enable or disable debug logging.
     */
    @Setter
    private boolean debugMode = false;

    /**
     * Register a listener to receive input events.
     * Listeners are called in priority order (highest first).
     */
    public void register(InputEventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }

        if (!listeners.contains(listener)) {
            listeners.add(listener);
            sorted = false;  // Need to re-sort

            if (debugMode) {
                System.out.println("[InputEventBus] Registered: " + listener.getListenerName() +
                        " (priority: " + listener.getPriority() + ")");
            }
        }
    }

    /**
     * Unregister a listener.
     */
    public void unregister(InputEventListener listener) {
        if (listeners.remove(listener) && debugMode) {
            System.out.println("[InputEventBus] Unregistered: " + listener.getListenerName());
        }
    }

    /**
     * Remove all listeners.
     */
    public void clear() {
        int count = listeners.size();
        listeners.clear();

        if (debugMode && count > 0) {
            System.out.println("[InputEventBus] Cleared " + count + " listeners");
        }
    }

    /**
     * Get the number of registered listeners.
     */
    public int getListenerCount() {
        return listeners.size();
    }

    /**
     * Post a key event to all registered listeners.
     */
    public void post(KeyEvent event) {
        if (event == null) return;

        ensureSorted();

        if (debugMode) {
            System.out.println("[InputEventBus] Posting: " + event);
        }

        for (InputEventListener listener : listeners) {
            if (event.isConsumed()) {
                if (debugMode) {
                    System.out.println("[InputEventBus] Event consumed, stopping propagation");
                }
                break;
            }
            listener.onKeyEvent(event);
        }
    }

    /**
     * Post a mouse button event to all registered listeners.
     */
    public void post(MouseButtonEvent event) {
        if (event == null) return;

        ensureSorted();

        for (InputEventListener listener : listeners) {
            if (event.isConsumed()) break;
            listener.onMouseButtonEvent(event);
        }
    }

    /**
     * Post a mouse move event to all registered listeners.
     */
    public void post(MouseMoveEvent event) {
        if (event == null) return;

        ensureSorted();

        for (InputEventListener listener : listeners) {
            if (event.isConsumed()) break;
            listener.onMouseMoveEvent(event);
        }
    }

    /**
     * Post a mouse scroll event to all registered listeners.
     */
    public void post(MouseScrollEvent event) {
        if (event == null) return;

        ensureSorted();

        for (InputEventListener listener : listeners) {
            if (event.isConsumed()) break;
            listener.onMouseScrollEvent(event);
        }
    }

    /**
     * Post an axis event to all registered listeners.
     */
    public void post(AxisEvent event) {
        if (event == null) return;

        ensureSorted();

        for (InputEventListener listener : listeners) {
            if (event.isConsumed()) break;
            listener.onAxisEvent(event);
        }
    }

    /**
     * Ensure listeners are sorted by priority (highest first).
     */
    private void ensureSorted() {
        if (!sorted) {
            List<InputEventListener> sortedList = new ArrayList<>(listeners);
            sortedList.sort(Comparator.comparingInt(InputEventListener::getPriority).reversed());
            listeners.clear();
            listeners.addAll(sortedList);
            sorted = true;

            if (debugMode) {
                System.out.println("[InputEventBus] Sorted listeners by priority:");
                for (InputEventListener listener : listeners) {
                    System.out.println("  - " + listener.getListenerName() +
                            " (priority: " + listener.getPriority() + ")");
                }
            }
        }
    }

    /**
     * Get all registered listeners (for debugging).
     */
    public List<InputEventListener> getListeners() {
        return new ArrayList<>(listeners);
    }
}