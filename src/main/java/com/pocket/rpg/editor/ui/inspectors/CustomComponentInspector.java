package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;

/**
 * Abstract base class for custom component editors.
 * <p>
 * Extend this to provide a specialized UI for editing specific component types
 * instead of the default reflection-based field editor.
 * <p>
 * The bind/draw pattern caches the component cast once on selection,
 * avoiding per-frame casting overhead.
 * <p>
 * Register implementations via {@link CustomComponentEditorRegistry#register(Class, CustomComponentInspector)}.
 *
 * @param <T> The component type this inspector handles
 */
public abstract class CustomComponentInspector<T extends Component> {

    /** Cached component reference, set on bind(). */
    protected T component;

    /** Cached entity reference, set on bind(). */
    protected EditorGameObject entity;

    /**
     * Called once when component is selected for editing.
     * Caches the cast so draw() doesn't cast every frame.
     *
     * @param component The component to edit
     * @param entity    The entity owning this component (for undo support), may be null
     */
    @SuppressWarnings("unchecked")
    public void bind(Component component, EditorGameObject entity) {
        this.component = (T) component;
        this.entity = entity;
    }

    /**
     * Called when component is deselected or inspector is closed.
     */
    public void unbind() {
        this.component = null;
        this.entity = null;
    }

    /**
     * Check if currently bound to a component.
     *
     * @return true if bound to a component
     */
    public boolean isBound() {
        return component != null;
    }

    /**
     * Draws the custom editor UI for the component.
     * Uses the cached component from bind() - no casting per frame.
     *
     * @return true if any field was changed
     */
    public abstract boolean draw();
}
