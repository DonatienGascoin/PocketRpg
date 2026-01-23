package com.pocket.rpg.collision.trigger;

/**
 * Handler for a specific trigger data type.
 * <p>
 * Implement this interface to define custom behavior when a trigger activates.
 * Register handlers with {@link TriggerSystem#registerHandler}.
 * <p>
 * Example:
 * <pre>
 * public class WarpHandler implements TriggerHandler&lt;WarpTriggerData&gt; {
 *     public void handle(TriggerContext context) {
 *         WarpTriggerData data = context.getData();
 *         // Perform warp logic...
 *     }
 * }
 * </pre>
 *
 * @param <T> The specific TriggerData type this handler processes
 */
@FunctionalInterface
public interface TriggerHandler<T extends TriggerData> {

    /**
     * Handles trigger activation.
     * <p>
     * Called by TriggerSystem when an entity activates a trigger of this type.
     *
     * @param context Context containing entity, tile position, and trigger data
     */
    void handle(TriggerContext context);
}
