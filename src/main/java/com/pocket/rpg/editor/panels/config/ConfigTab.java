package com.pocket.rpg.editor.panels.config;

/**
 * Interface for configuration tab panels.
 * Each tab handles one config type (Game, Input, Rendering, Transition).
 * <p>
 * Uses live editing model - edits apply directly to the live config object.
 * The panel tracks dirty state globally and calls save/revert/resetToDefaults
 * when the user clicks the corresponding buttons.
 */
public interface ConfigTab {

    /**
     * Renders the tab content.
     * Called every frame when the tab is visible.
     * <p>
     * Implementations should edit the live config directly and call
     * the markDirty callback when any field changes.
     */
    void renderContent();

    /**
     * Returns the tab display name (e.g., "Game", "Input").
     */
    String getTabName();

    /**
     * Saves the current config state to disk.
     * Called when the user clicks "Save" in the panel header.
     */
    void save();

    /**
     * Reverts the config to the last saved state by reloading from disk.
     * Called when the user clicks "Revert" in the panel header.
     */
    void revert();

    /**
     * Resets the config to default values.
     * Called when the user clicks "Reset to Defaults" in the panel header.
     */
    void resetToDefaults();
}
