package com.pocket.rpg.collision.trigger;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Trigger data for SPAWN_POINT collision type.
 * <p>
 * Marks a named arrival point that warps and doors can reference.
 * Spawn points don't fire events themselves - they're just markers.
 *
 * @param id          Unique identifier for this spawn point (e.g., "cave_entrance", "house_exit")
 * @param facingDirection Optional: direction player should face after spawning (null = keep current)
 * @param activationMode  Not used for spawn points, but required by interface
 * @param oneShot         Not used for spawn points
 * @param playerOnly      Not used for spawn points
 */
public record SpawnPointData(
        String id,
        Direction facingDirection,
        ActivationMode activationMode,
        boolean oneShot,
        boolean playerOnly
) implements TriggerData {

    /**
     * Creates a spawn point with just an ID.
     */
    public SpawnPointData(String id) {
        this(id, null, ActivationMode.ON_ENTER, false, true);
    }

    /**
     * Creates a spawn point with ID and facing direction.
     */
    public SpawnPointData(String id, Direction facing) {
        this(id, facing, ActivationMode.ON_ENTER, false, true);
    }

    /**
     * Creates empty spawn point data for editor initialization.
     */
    public SpawnPointData() {
        this("", null, ActivationMode.ON_ENTER, false, true);
    }

    @Override
    public CollisionType collisionType() {
        return CollisionType.SPAWN_POINT;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (id == null || id.isBlank()) {
            errors.add("Spawn point ID is required");
        }
        return errors;
    }

    /**
     * Creates a copy with updated ID.
     */
    public SpawnPointData withId(String id) {
        return new SpawnPointData(id, facingDirection, activationMode, oneShot, playerOnly);
    }

    /**
     * Creates a copy with updated facing direction.
     */
    public SpawnPointData withFacingDirection(Direction direction) {
        return new SpawnPointData(id, direction, activationMode, oneShot, playerOnly);
    }

    /**
     * Creates a copy with updated common trigger settings.
     */
    public SpawnPointData withCommon(ActivationMode activation, boolean oneShot, boolean playerOnly) {
        return new SpawnPointData(id, facingDirection, activation, oneShot, playerOnly);
    }
}
