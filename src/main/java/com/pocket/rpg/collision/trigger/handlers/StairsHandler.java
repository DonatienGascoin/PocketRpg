package com.pocket.rpg.collision.trigger.handlers;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.trigger.StairsData;
import com.pocket.rpg.collision.trigger.TriggerContext;
import com.pocket.rpg.collision.trigger.TriggerHandler;
import com.pocket.rpg.components.GridMovement;
import com.pocket.rpg.core.GameObject;

/**
 * Handles STAIRS trigger activation (ON_EXIT only).
 * <p>
 * Mono-direction stairs: elevation only changes when exiting in the
 * configured direction. Exiting any other direction has no effect.
 * <p>
 * Example: StairsData(exitDirection=UP, elevationChange=+1)
 * <ul>
 *   <li>Exit UP → elevation increases by 1</li>
 *   <li>Exit DOWN/LEFT/RIGHT → no change</li>
 * </ul>
 */
public class StairsHandler implements TriggerHandler<StairsData> {

    @Override
    public void handle(TriggerContext context) {
        StairsData data = context.getData();
        GameObject entity = context.entity();
        Direction exitDirection = context.exitDirection();

        // This should always be present for ON_EXIT triggers
        if (exitDirection == null) {
            System.err.println("[StairsHandler] Called without exit direction - " +
                    "stairs require ON_EXIT activation");
            return;
        }

        // Only trigger if exiting in the configured direction
        if (!data.triggersFor(exitDirection)) {
            return;
        }

        GridMovement movement = entity.getComponent(GridMovement.class);
        if (movement == null) {
            System.err.println("[StairsHandler] Entity has no GridMovement component");
            return;
        }

        // Apply elevation change
        int oldElevation = movement.getZLevel();
        int newElevation = oldElevation + data.elevationChange();
        movement.setZLevel(newElevation);

        System.out.println("[StairsHandler] " + entity.getName() +
                " exited " + exitDirection + ", elevation " +
                oldElevation + " → " + newElevation);
    }
}
