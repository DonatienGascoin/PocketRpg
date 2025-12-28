package com.pocket.rpg.editor.utils;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.serialization.ComponentData;

/**
 * Interface for custom component editors.
 * <p>
 * Implement this to provide a specialized UI for editing specific component types
 * instead of the default reflection-based field editor.
 * <p>
 * Register implementations via {@link CustomComponentEditorRegistry#register(String, CustomComponentEditor)}.
 */
public interface CustomComponentEditor {

    /**
     * Draws the custom editor UI for the component.
     *
     * @param data   The component data to edit
     * @param entity The entity owning this component (for undo support), may be null
     * @return true if any field was changed
     */
    boolean draw(ComponentData data, EditorEntity entity);
}
