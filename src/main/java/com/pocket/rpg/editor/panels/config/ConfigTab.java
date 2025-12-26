package com.pocket.rpg.editor.panels.config;

/**
 * Interface for configuration tab panels.
 * Each tab handles one config type (Game, Input, Rendering, Transition).
 */
public interface ConfigTab {

    /**
     * Initializes working copies from live config.
     * Called when the config modal opens.
     */
    void initialize();

    /**
     * Renders the tab content.
     * Called every frame when the tab is visible.
     */
    void renderContent();

    /**
     * Returns the tab display name (e.g., "Game", "Input").
     */
    String getTabName();

    /**
     * Returns true if there are unsaved changes.
     */
    boolean isDirty();

    /**
     * Applies working copy to live config and saves to file.
     */
    void save();

    /**
     * Resets working copy to defaults.
     */
    void resetToDefaults();
}
