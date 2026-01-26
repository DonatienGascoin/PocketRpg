package com.pocket.rpg.editor.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Central event bus for editor inter-component communication.
 * <p>
 * Provides a publish/subscribe mechanism that decouples event producers from consumers.
 * <p>
 * Usage:
 * <pre>
 * // Subscribe to events
 * EditorEventBus.get().subscribe(AssetChangedEvent.class, event -> {
 *     System.out.println("Asset changed: " + event.path());
 * });
 *
 * // Publish events
 * EditorEventBus.get().publish(new AssetChangedEvent("sprites/player.png", ChangeType.MODIFIED));
 * </pre>
 *
 * @see EditorEvent
 */
public class EditorEventBus {

    private static EditorEventBus instance;

    private final Map<Class<?>, List<Consumer<?>>> subscribers = new HashMap<>();
    private boolean debugLogging = false;

    private EditorEventBus() {
    }

    /**
     * Gets the singleton instance of the event bus.
     */
    public static EditorEventBus get() {
        if (instance == null) {
            instance = new EditorEventBus();
        }
        return instance;
    }

    /**
     * Resets the event bus (for testing or editor restart).
     */
    public static void reset() {
        if (instance != null) {
            instance.subscribers.clear();
            instance = null;
        }
    }

    /**
     * Enables or disables debug logging of events.
     */
    public void setDebugLogging(boolean enabled) {
        this.debugLogging = enabled;
    }

    /**
     * Subscribes to events of the specified type.
     *
     * @param eventType The event class to subscribe to
     * @param handler   The handler to call when events are published
     * @param <T>       The event type
     */
    public <T extends EditorEvent> void subscribe(Class<T> eventType, Consumer<T> handler) {
        subscribers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);

        if (debugLogging) {
            System.out.println("[EventBus] Subscribed to " + eventType.getSimpleName() +
                    " (total: " + subscribers.get(eventType).size() + ")");
        }
    }

    /**
     * Unsubscribes from events of the specified type.
     *
     * @param eventType The event class to unsubscribe from
     * @param handler   The handler to remove
     * @param <T>       The event type
     */
    public <T extends EditorEvent> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        List<Consumer<?>> handlers = subscribers.get(eventType);
        if (handlers != null) {
            handlers.remove(handler);

            if (debugLogging) {
                System.out.println("[EventBus] Unsubscribed from " + eventType.getSimpleName() +
                        " (remaining: " + handlers.size() + ")");
            }
        }
    }

    /**
     * Publishes an event to all subscribers.
     *
     * @param event The event to publish
     */
    @SuppressWarnings("unchecked")
    public void publish(EditorEvent event) {
        if (event == null) return;

        Class<?> eventType = event.getClass();
        List<Consumer<?>> handlers = subscribers.get(eventType);

        if (debugLogging) {
            int count = handlers != null ? handlers.size() : 0;
            System.out.println("[EventBus] Publishing " + eventType.getSimpleName() +
                    " to " + count + " subscriber(s): " + event);
        }

        if (handlers != null && !handlers.isEmpty()) {
            // Create a copy to avoid ConcurrentModificationException if handlers modify subscriptions
            List<Consumer<?>> handlersCopy = new ArrayList<>(handlers);
            for (Consumer<?> handler : handlersCopy) {
                try {
                    ((Consumer<EditorEvent>) handler).accept(event);
                } catch (Exception e) {
                    System.err.println("[EventBus] Error in handler for " + eventType.getSimpleName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Gets the number of subscribers for a given event type.
     * Useful for debugging and testing.
     */
    public int getSubscriberCount(Class<? extends EditorEvent> eventType) {
        List<Consumer<?>> handlers = subscribers.get(eventType);
        return handlers != null ? handlers.size() : 0;
    }
}
