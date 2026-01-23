package com.pocket.rpg.collision.trigger;

import com.pocket.rpg.collision.CollisionType;

import java.util.List;

/**
 * Base interface for all trigger data types.
 * <p>
 * Uses sealed interface pattern for type-safe trigger configuration.
 * Each collision type that requires metadata has a corresponding record.
 * <p>
 * Benefits of sealed interface:
 * <ul>
 *   <li>Exhaustive switch expressions - compiler ensures all cases handled</li>
 *   <li>Adding new type forces updates everywhere it's used</li>
 *   <li>Registry-based serialization via {@code getPermittedSubclasses()}</li>
 * </ul>
 */
public sealed interface TriggerData
        permits WarpTriggerData, DoorTriggerData, StairsData, SpawnPointData {

    /**
     * When the trigger activates.
     */
    ActivationMode activationMode();

    /**
     * If true, trigger only fires once per game session.
     */
    boolean oneShot();

    /**
     * If true, only the player can activate this trigger.
     */
    boolean playerOnly();

    /**
     * Returns the collision type this data is for.
     * May return null if the type is context-dependent (e.g., stairs).
     */
    CollisionType collisionType();

    /**
     * Validates that all required fields are set.
     *
     * @return List of validation errors, empty if valid.
     */
    default List<String> validate() {
        return List.of();
    }

    /**
     * Returns true if this trigger data has validation errors.
     */
    default boolean hasErrors() {
        return !validate().isEmpty();
    }
}
