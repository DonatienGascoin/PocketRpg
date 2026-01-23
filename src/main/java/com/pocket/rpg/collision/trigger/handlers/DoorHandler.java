package com.pocket.rpg.collision.trigger.handlers;

import com.pocket.rpg.collision.trigger.DoorTriggerData;
import com.pocket.rpg.collision.trigger.TriggerContext;
import com.pocket.rpg.collision.trigger.TriggerHandler;
import com.pocket.rpg.core.GameObject;

/**
 * Handles DOOR trigger activation.
 * <p>
 * Checks lock status and key requirements before allowing passage.
 * Optionally teleports to a spawn point after unlocking.
 * <p>
 * Note: This is a basic implementation that logs door interactions.
 * For full functionality (inventory checks, dialogue), extend this class
 * and inject the required system dependencies.
 */
public class DoorHandler implements TriggerHandler<DoorTriggerData> {

    /**
     * Callback interface for checking if entity has a key.
     */
    @FunctionalInterface
    public interface KeyChecker {
        /**
         * Checks if entity has the specified key item.
         *
         * @param entity The entity to check
         * @param keyId  The key item ID
         * @return true if entity has the key
         */
        boolean hasKey(GameObject entity, String keyId);
    }

    /**
     * Callback interface for consuming a key from entity.
     */
    @FunctionalInterface
    public interface KeyConsumer {
        /**
         * Removes the key from entity's inventory.
         *
         * @param entity The entity
         * @param keyId  The key item ID to remove
         */
        void consumeKey(GameObject entity, String keyId);
    }

    /**
     * Callback interface for showing locked message.
     */
    @FunctionalInterface
    public interface MessageDisplay {
        /**
         * Shows a message to the player.
         *
         * @param message The message to display
         */
        void showMessage(String message);
    }

    /**
     * Callback interface for door transition.
     */
    @FunctionalInterface
    public interface DoorCallback {
        /**
         * Called when door passage is allowed.
         *
         * @param entity The entity passing through
         * @param data   Full door trigger data
         */
        void onDoorOpen(GameObject entity, DoorTriggerData data);
    }

    private final KeyChecker keyChecker;
    private final KeyConsumer keyConsumer;
    private final MessageDisplay messageDisplay;
    private final DoorCallback doorCallback;

    /**
     * Creates a fully configured DoorHandler.
     */
    public DoorHandler(KeyChecker keyChecker, KeyConsumer keyConsumer,
                       MessageDisplay messageDisplay, DoorCallback doorCallback) {
        this.keyChecker = keyChecker;
        this.keyConsumer = keyConsumer;
        this.messageDisplay = messageDisplay;
        this.doorCallback = doorCallback;
    }

    /**
     * Creates a DoorHandler that logs interactions (for testing/development).
     */
    public DoorHandler() {
        this.keyChecker = null;
        this.keyConsumer = null;
        this.messageDisplay = null;
        this.doorCallback = null;
    }

    @Override
    public void handle(TriggerContext context) {
        DoorTriggerData data = context.getData();
        GameObject entity = context.entity();

        if (data.locked()) {
            // Check for required key
            boolean hasKey = keyChecker != null && keyChecker.hasKey(entity, data.requiredKey());

            if (!hasKey) {
                // Show locked message
                String message = data.lockedMessage() != null && !data.lockedMessage().isBlank()
                        ? data.lockedMessage()
                        : "The door is locked.";

                System.out.println("[DoorHandler] " + entity.getName() +
                        " tried locked door: " + message);

                if (messageDisplay != null) {
                    messageDisplay.showMessage(message);
                }
                return;
            }

            // Consume key if configured
            if (data.consumeKey() && keyConsumer != null) {
                keyConsumer.consumeKey(entity, data.requiredKey());
                System.out.println("[DoorHandler] Consumed key: " + data.requiredKey());
            }
        }

        // Door is open or was unlocked
        if (data.hasDestination()) {
            String sceneDesc = data.isCrossScene() ? "scene '" + data.targetScene() + "'" : "same scene";
            System.out.println("[DoorHandler] " + entity.getName() +
                    " opened door to " + sceneDesc +
                    " spawn '" + data.targetSpawnId() + "'");
        } else {
            System.out.println("[DoorHandler] " + entity.getName() + " opened door (no destination)");
        }

        if (doorCallback != null) {
            doorCallback.onDoorOpen(entity, data);
        }
    }
}
