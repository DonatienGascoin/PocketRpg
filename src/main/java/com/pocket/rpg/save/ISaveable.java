package com.pocket.rpg.save;

import java.util.Map;

/**
 * Interface for components that need custom save/load logic.
 * <p>
 * By default, components are NOT saved. Only components implementing
 * ISaveable will have their state captured in save files.
 * <p>
 * The interface uses Map&lt;String, Object&gt; for flexibility:
 * - Simple values: int, float, boolean, String
 * - Complex values: List, Map (nested)
 * - Assets: store as path strings, reload on load
 * <p>
 * DO NOT save:
 * - Transient runtime state (animation timers, cached values)
 * - Component references (use @ComponentRef instead)
 * - Assets directly (save paths, reload on load)
 */
public interface ISaveable {

    /**
     * Captures current state for saving.
     * <p>
     * Called when SaveManager.save() is invoked.
     * Return a map of field names to values that should be saved.
     * <p>
     * Guidelines:
     * - Only include state that needs persistence
     * - Skip derived/computed values
     * - Use simple types (primitives, strings, lists, maps)
     * - For assets, save the path string
     *
     * @return Map of field names to values, or null/empty if nothing to save
     */
    Map<String, Object> getSaveState();

    /**
     * Restores state from a save file.
     * <p>
     * Called during scene load when saved state exists for this component.
     * The map contains the same structure returned by getSaveState().
     * <p>
     * Guidelines:
     * - Handle missing keys gracefully (save might be from older version)
     * - Use SerializationUtils.fromSerializable() for type conversion
     * - Reload assets from paths
     *
     * @param state Previously saved state (may be null)
     */
    void loadSaveState(Map<String, Object> state);

    /**
     * Check if this component has meaningful state to save.
     * <p>
     * Used to skip components that haven't changed from defaults.
     * Override to return false if nothing worth saving.
     *
     * @return true if getSaveState() should be called
     */
    default boolean hasSaveableState() {
        return true;
    }
}
