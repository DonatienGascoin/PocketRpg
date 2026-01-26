package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.core.EditorConfig;
import lombok.Getter;

/**
 * Abstract base class for editor panels with built-in visibility management and persistence.
 * Panels that extend this class automatically get:
 * - Open/closed state tracking
 * - Persistence of panel state via EditorConfig
 * - Toggle and focus functionality
 */
public abstract class EditorPanel {

    @Getter
    private final String panelId;

    private final boolean defaultOpen;

    @Getter
    private boolean open;

    /**
     * Whether the panel content was visible in the last frame.
     * This is true only when the panel is open AND ImGui.begin() returned true
     * (meaning the panel is not collapsed and is the active tab in its dock).
     */
    @Getter
    private boolean contentVisible;

    /**
     * Whether the panel window was focused in the last frame.
     * Use this for panel-scoped shortcuts.
     */
    @Getter
    private boolean focused;

    private EditorConfig config;

    /** Cached display name computed from panelId. */
    private final String displayName;

    /**
     * Creates a new panel with the specified ID and default open state.
     *
     * @param panelId     Unique identifier for this panel (used for persistence)
     * @param defaultOpen Whether the panel should be open by default
     */
    protected EditorPanel(String panelId, boolean defaultOpen) {
        this.panelId = panelId;
        this.defaultOpen = defaultOpen;
        this.open = defaultOpen;
        this.displayName = computeDisplayName(panelId);
    }

    /**
     * Initializes the panel with the editor config for persistence.
     * Call this once after construction before rendering.
     *
     * @param config The editor config to use for state persistence
     */
    public void initPanel(EditorConfig config) {
        this.config = config;
        this.open = config.isPanelOpen(panelId, defaultOpen);
    }

    /**
     * Sets the open state and persists to config.
     *
     * @param open Whether the panel should be open
     */
    public void setOpen(boolean open) {
        if (this.open != open) {
            this.open = open;
            if (config != null) {
                config.setPanelOpen(panelId, open);
            }
        }
    }

    /**
     * Toggles the panel visibility.
     */
    public void toggle() {
        setOpen(!open);
    }

    /**
     * Updates the content visibility state.
     * Call this with the result of ImGui.begin() in your render method.
     *
     * @param visible Whether ImGui.begin() returned true
     */
    protected void setContentVisible(boolean visible) {
        this.contentVisible = visible;
    }

    /**
     * Updates the focused state.
     * Call this with ImGui.isWindowFocused() inside your render method's begin/end block.
     *
     * @param focused Whether the window is focused
     */
    protected void setFocused(boolean focused) {
        this.focused = focused;
    }

    /**
     * Returns the default open state for this panel.
     *
     * @return true if the panel should be open by default
     */
    public boolean getDefaultOpen() {
        return defaultOpen;
    }

    /**
     * Renders the panel. Subclasses should check isOpen() before rendering content.
     */
    public abstract void render();

    /**
     * Returns the display name of the panel for the Window menu.
     * Override this if the display name should differ from the panel ID.
     *
     * @return The display name for this panel
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Converts a camelCase panelId to Title Case with spaces.
     */
    private static String computeDisplayName(String panelId) {
        StringBuilder sb = new StringBuilder();
        for (char c : panelId.toCharArray()) {
            if (Character.isUpperCase(c) && sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(sb.length() == 0 ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }
}
