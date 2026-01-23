package com.pocket.rpg.collision.trigger;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Trigger data for STAIRS collision type.
 * <p>
 * Mono-direction stairs: each stair tile has ONE exit direction that triggers
 * elevation change. This simplifies level design and enables validation.
 * <p>
 * Example: Stair going up when exiting north:
 * <pre>
 *   exitDirection = UP, elevationChange = +1
 *   - Player walks onto stair tile
 *   - Player exits north (UP) → elevation increases by 1
 *   - Player exits any other direction → no change
 * </pre>
 * <p>
 * For bidirectional stairs, place two tiles:
 * - At z=0: exitDirection=UP, elevationChange=+1
 * - At z=1: exitDirection=DOWN, elevationChange=-1
 *
 * @param exitDirection   The direction player must exit to trigger elevation change
 * @param elevationChange The elevation delta (+1 to go up, -1 to go down)
 */
public record StairsData(
        Direction exitDirection,
        int elevationChange
) implements TriggerData {

    /**
     * Creates stairs with the given direction and elevation change.
     */
    public StairsData {
        if (exitDirection == null) {
            exitDirection = Direction.UP;
        }
        if (elevationChange == 0) {
            elevationChange = 1; // Default to going up
        }
    }

    /**
     * Creates default stairs (exit UP to go up one floor).
     */
    public StairsData() {
        this(Direction.UP, 1);
    }

    /**
     * Creates stairs going up when exiting in the given direction.
     */
    public static StairsData goingUp(Direction exitDirection) {
        return new StairsData(exitDirection, 1);
    }

    /**
     * Creates stairs going down when exiting in the given direction.
     */
    public static StairsData goingDown(Direction exitDirection) {
        return new StairsData(exitDirection, -1);
    }

    @Override
    public ActivationMode activationMode() {
        return ActivationMode.ON_EXIT;  // Mandatory for stairs
    }

    @Override
    public boolean oneShot() {
        return false;
    }

    @Override
    public boolean playerOnly() {
        return false;  // NPCs can use stairs
    }

    @Override
    public CollisionType collisionType() {
        return CollisionType.STAIRS;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (exitDirection == null) {
            errors.add("Exit direction is required");
        }
        if (elevationChange == 0) {
            errors.add("Elevation change cannot be zero");
        }
        return errors;
    }

    /**
     * Returns true if this stair triggers for the given exit direction.
     */
    public boolean triggersFor(Direction direction) {
        return exitDirection == direction;
    }

    /**
     * Creates a copy with updated exit direction.
     */
    public StairsData withExitDirection(Direction direction) {
        return new StairsData(direction, elevationChange);
    }

    /**
     * Creates a copy with updated elevation change.
     */
    public StairsData withElevationChange(int change) {
        return new StairsData(exitDirection, change);
    }

    /**
     * Returns true if this stair goes up (positive elevation change).
     */
    public boolean goesUp() {
        return elevationChange > 0;
    }

    /**
     * Returns true if this stair goes down (negative elevation change).
     */
    public boolean goesDown() {
        return elevationChange < 0;
    }

    @Override
    public String toString() {
        String dir = goesUp() ? "up" : "down";
        return "Stairs[exit " + exitDirection + " → " + dir + " " + Math.abs(elevationChange) + "]";
    }
}
