package com.pocket.rpg.collision.trigger.handlers;

import com.pocket.rpg.collision.trigger.TriggerContext;
import com.pocket.rpg.collision.trigger.TriggerHandler;
import com.pocket.rpg.collision.trigger.WarpTriggerData;
import com.pocket.rpg.core.GameObject;

/**
 * Handles WARP trigger activation.
 * <p>
 * Transitions to target scene and positions entity at target spawn point.
 * <p>
 * Note: This is a basic implementation that logs the warp action.
 * For full scene transition support, extend this class and inject
 * SceneManager and TransitionManager dependencies.
 */
public class WarpHandler implements TriggerHandler<WarpTriggerData> {

    /**
     * Callback interface for warp events.
     * Implement to provide actual scene transition logic.
     */
    @FunctionalInterface
    public interface WarpCallback {
        /**
         * Called when a warp should execute.
         *
         * @param entity        The entity being warped
         * @param targetScene   Destination scene name (empty = same scene)
         * @param targetSpawnId ID of spawn point to warp to
         * @param data          Full warp trigger data
         */
        void onWarp(GameObject entity, String targetScene, String targetSpawnId, WarpTriggerData data);
    }

    private final WarpCallback callback;

    /**
     * Creates a WarpHandler with a callback for actual warp execution.
     *
     * @param callback Called when warp triggers
     */
    public WarpHandler(WarpCallback callback) {
        this.callback = callback;
    }

    /**
     * Creates a WarpHandler that logs warp actions (for testing/development).
     */
    public WarpHandler() {
        this.callback = null;
    }

    @Override
    public void handle(TriggerContext context) {
        WarpTriggerData data = context.getData();
        GameObject entity = context.entity();

        String sceneDesc = data.isCrossScene() ? "scene '" + data.targetScene() + "'" : "same scene";
        System.out.println("[WarpHandler] " + entity.getName() +
                " triggered warp to " + sceneDesc +
                " spawn '" + data.targetSpawnId() + "'" +
                " with transition " + data.transition());

        if (callback != null) {
            callback.onWarp(entity, data.targetScene(), data.targetSpawnId(), data);
        }
    }
}
