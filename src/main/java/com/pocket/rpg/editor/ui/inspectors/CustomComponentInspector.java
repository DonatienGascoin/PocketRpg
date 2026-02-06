package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyItem;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.ui.fields.FieldUndoTracker;

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

    /** Cached entity reference, set on bind(). Always non-null when bound. */
    protected HierarchyItem entity;

    /**
     * Called once when component is selected for editing.
     * Caches the cast so draw() doesn't cast every frame.
     *
     * @param component The component to edit
     * @param entity    The entity owning this component
     */
    @SuppressWarnings("unchecked")
    public void bind(Component component, HierarchyItem entity) {
        this.component = (T) component;
        this.entity = entity;
    }

    /**
     * Called when component is deselected or inspector is closed.
     */
    public void unbind() {
        this.component = null;
        this.entity = null;
        FieldUndoTracker.clear();
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
     * Returns the entity as {@link EditorGameObject}, or null if in play mode.
     * <p>
     * Use this for operations that only apply in the editor:
     * undo commands, prefab overrides, position access via
     * {@code EditorGameObject.getPosition()}.
     * <p>
     * For scene graph queries (getComponent, parent/children),
     * use {@link #entity} directly — it is always non-null.
     */
    protected EditorGameObject editorEntity() {
        return entity instanceof EditorGameObject ego ? ego : null;
    }

    /**
     * Draws the custom editor UI for the component.
     * Uses the cached component from bind() - no casting per frame.
     *
     * @return true if any field was changed
     */
    public abstract boolean draw();
}
