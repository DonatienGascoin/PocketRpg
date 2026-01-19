package com.pocket.rpg.editor;

import lombok.Getter;

/**
 * Enumeration of editor panels that can be opened for specific asset types.
 * Used by AssetLoader to indicate which panel should open on double-click.
 */
@Getter
public enum EditorPanel {
    ANIMATION_EDITOR("Animation Editor");

    private final String windowName;

    EditorPanel(String windowName) {
        this.windowName = windowName;
    }

}
