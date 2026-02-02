package com.pocket.rpg.editor;

import lombok.Getter;

/**
 * Enumeration of editor panel types that can be opened for specific asset types.
 * Used by AssetLoader to indicate which panel should open on double-click.
 */
@Getter
public enum EditorPanelType {
    ANIMATION_EDITOR("Animation Editor"),
    SPRITE_EDITOR("Sprite Editor"),
    ANIMATOR_EDITOR("Animator Editor"),
    PREFAB_EDITOR("Prefab Editor");

    private final String windowName;

    EditorPanelType(String windowName) {
        this.windowName = windowName;
    }

}
