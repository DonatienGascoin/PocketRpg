package com.pocket.rpg.editor.scene;

/**
 * Controls how layers are displayed in the editor viewport.
 */
public enum LayerVisibilityMode {
    /**
     * All layers are fully visible at 100% opacity.
     */
    ALL,
    
    /**
     * Only the selected/active layer is visible.
     * All other layers are hidden.
     */
    SELECTED_ONLY,
    
    /**
     * Selected layer is at 100% opacity.
     * All other layers are rendered at reduced opacity (dimmed).
     */
    SELECTED_DIMMED
}
