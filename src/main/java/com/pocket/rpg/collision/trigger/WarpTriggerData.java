package com.pocket.rpg.collision.trigger;

import com.pocket.rpg.collision.CollisionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Trigger data for WARP collision type.
 * <p>
 * Teleports the player to a spawn point, either in the same scene or another scene.
 *
 * @param targetScene    Name of the destination scene (empty = same scene)
 * @param targetSpawnId  ID of the spawn point to teleport to
 * @param transition     Type of visual transition effect
 * @param activationMode When the trigger fires
 * @param oneShot        If true, only fires once per session
 * @param playerOnly     If true, only player can trigger
 */
public record WarpTriggerData(
        String targetScene,
        String targetSpawnId,
        TransitionType transition,
        ActivationMode activationMode,
        boolean oneShot,
        boolean playerOnly
) implements TriggerData {

    /**
     * Creates warp data for same-scene teleport.
     */
    public WarpTriggerData(String targetSpawnId) {
        this("", targetSpawnId,
                TransitionType.FADE,
                ActivationMode.ON_ENTER,
                false,
                true);
    }

    /**
     * Creates warp data for cross-scene teleport.
     */
    public WarpTriggerData(String targetScene, String targetSpawnId) {
        this(targetScene, targetSpawnId,
                TransitionType.FADE,
                ActivationMode.ON_ENTER,
                false,
                true);
    }

    /**
     * Creates empty warp data for editor initialization.
     */
    public WarpTriggerData() {
        this("", "",
                TransitionType.FADE,
                ActivationMode.ON_ENTER,
                false,
                true);
    }

    @Override
    public CollisionType collisionType() {
        return CollisionType.WARP;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (targetSpawnId == null || targetSpawnId.isBlank()) {
            errors.add("Target spawn point is required");
        }
        return errors;
    }

    /**
     * Returns true if this warp goes to another scene.
     */
    public boolean isCrossScene() {
        return targetScene != null && !targetScene.isBlank();
    }

    /**
     * Creates a copy with updated target scene.
     */
    public WarpTriggerData withTargetScene(String scene) {
        return new WarpTriggerData(scene, targetSpawnId, transition, activationMode, oneShot, playerOnly);
    }

    /**
     * Creates a copy with updated target spawn ID.
     */
    public WarpTriggerData withTargetSpawnId(String spawnId) {
        return new WarpTriggerData(targetScene, spawnId, transition, activationMode, oneShot, playerOnly);
    }

    /**
     * Creates a copy with updated transition type.
     */
    public WarpTriggerData withTransition(TransitionType type) {
        return new WarpTriggerData(targetScene, targetSpawnId, type, activationMode, oneShot, playerOnly);
    }

    /**
     * Creates a copy with updated common trigger settings.
     */
    public WarpTriggerData withCommon(ActivationMode activation, boolean oneShot, boolean playerOnly) {
        return new WarpTriggerData(targetScene, targetSpawnId, transition, activation, oneShot, playerOnly);
    }
}
