package com.pocket.rpg.collision.trigger;

import com.pocket.rpg.collision.CollisionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Trigger data for DOOR collision type.
 * <p>
 * Represents a door that may be locked and optionally leads to a spawn point.
 * <p>
 * Door modes:
 * <ul>
 *   <li><b>Unlock in place</b>: targetSpawnId is empty - door just unlocks, player stays</li>
 *   <li><b>Same scene</b>: targetScene is empty, targetSpawnId set - teleport within scene</li>
 *   <li><b>Cross scene</b>: both targetScene and targetSpawnId set - teleport to another scene</li>
 * </ul>
 *
 * @param locked         Whether the door starts locked
 * @param requiredKey    Item ID required to unlock (empty if not locked)
 * @param consumeKey     Whether to remove key from inventory on use
 * @param lockedMessage  Message shown when door is locked
 * @param targetScene    Destination scene (empty = same scene or unlock in place)
 * @param targetSpawnId  ID of spawn point to teleport to (empty = unlock in place only)
 * @param transition     Type of visual transition effect
 * @param activationMode When the trigger fires
 * @param oneShot        If true, only fires once per session
 * @param playerOnly     If true, only player can trigger
 */
public record DoorTriggerData(
        boolean locked,
        String requiredKey,
        boolean consumeKey,
        String lockedMessage,
        String targetScene,
        String targetSpawnId,
        TransitionType transition,
        ActivationMode activationMode,
        boolean oneShot,
        boolean playerOnly
) implements TriggerData {

    /**
     * Creates a simple unlocked door that teleports to a spawn point.
     */
    public DoorTriggerData(String targetScene, String targetSpawnId) {
        this(false, "", false, "",
                targetScene, targetSpawnId,
                TransitionType.FADE,
                ActivationMode.ON_INTERACT,
                false,
                true);
    }

    /**
     * Creates a locked door.
     */
    public DoorTriggerData(String requiredKey, String lockedMessage,
                           String targetScene, String targetSpawnId) {
        this(true, requiredKey, true, lockedMessage,
                targetScene, targetSpawnId,
                TransitionType.FADE,
                ActivationMode.ON_INTERACT,
                false,
                true);
    }

    /**
     * Creates empty door data for editor initialization.
     */
    public DoorTriggerData() {
        this(false, "", false, "The door is locked.",
                "", "",
                TransitionType.FADE,
                ActivationMode.ON_INTERACT,
                false,
                true);
    }

    @Override
    public CollisionType collisionType() {
        return CollisionType.DOOR;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (locked && (requiredKey == null || requiredKey.isBlank())) {
            errors.add("Locked door requires a key item");
        }
        // targetSpawnId can be empty - door may just unlock in place without teleporting
        return errors;
    }

    /**
     * Returns true if this door teleports the player (vs just unlocking in place).
     */
    public boolean hasDestination() {
        return targetSpawnId != null && !targetSpawnId.isBlank();
    }

    /**
     * Returns true if this door goes to another scene.
     */
    public boolean isCrossScene() {
        return targetScene != null && !targetScene.isBlank();
    }

    /**
     * Creates a copy with updated lock state.
     */
    public DoorTriggerData withLocked(boolean locked) {
        return new DoorTriggerData(locked, requiredKey, consumeKey, lockedMessage,
                targetScene, targetSpawnId, transition, activationMode, oneShot, playerOnly);
    }

    /**
     * Creates a copy with updated required key.
     */
    public DoorTriggerData withRequiredKey(String key) {
        return new DoorTriggerData(locked, key, consumeKey, lockedMessage,
                targetScene, targetSpawnId, transition, activationMode, oneShot, playerOnly);
    }

    /**
     * Creates a copy with updated consume key setting.
     */
    public DoorTriggerData withConsumeKey(boolean consume) {
        return new DoorTriggerData(locked, requiredKey, consume, lockedMessage,
                targetScene, targetSpawnId, transition, activationMode, oneShot, playerOnly);
    }

    /**
     * Creates a copy with updated locked message.
     */
    public DoorTriggerData withLockedMessage(String message) {
        return new DoorTriggerData(locked, requiredKey, consumeKey, message,
                targetScene, targetSpawnId, transition, activationMode, oneShot, playerOnly);
    }

    /**
     * Creates a copy with updated target scene.
     */
    public DoorTriggerData withTargetScene(String scene) {
        return new DoorTriggerData(locked, requiredKey, consumeKey, lockedMessage,
                scene, targetSpawnId, transition, activationMode, oneShot, playerOnly);
    }

    /**
     * Creates a copy with updated target spawn ID.
     */
    public DoorTriggerData withTargetSpawnId(String spawnId) {
        return new DoorTriggerData(locked, requiredKey, consumeKey, lockedMessage,
                targetScene, spawnId, transition, activationMode, oneShot, playerOnly);
    }

    /**
     * Creates a copy with updated transition type.
     */
    public DoorTriggerData withTransition(TransitionType type) {
        return new DoorTriggerData(locked, requiredKey, consumeKey, lockedMessage,
                targetScene, targetSpawnId, type, activationMode, oneShot, playerOnly);
    }

    /**
     * Creates a copy with updated common trigger settings.
     */
    public DoorTriggerData withCommon(ActivationMode activation, boolean oneShot, boolean playerOnly) {
        return new DoorTriggerData(locked, requiredKey, consumeKey, lockedMessage,
                targetScene, targetSpawnId, transition, activation, oneShot, playerOnly);
    }
}
