package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import imgui.ImGui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Centralized undo tracking for inspector fields.
 * <p>
 * Replaces the per-class undo boilerplate (capture on activation, push on deactivation)
 * with a single method call after each ImGui widget.
 * <p>
 * <b>Ordering contract:</b> Call {@link #track} or {@link #trackReflection} immediately
 * after the ImGui widget, before any call that changes the "last item"
 * (text, button, checkbox, drag, etc.). Safe calls between widget and track:
 * {@code setTooltip()}, {@code popStyleColor()}, {@code popID()}.
 */
public final class FieldUndoTracker {

    private static final Map<String, Object> startValues = new HashMap<>();

    private FieldUndoTracker() {}

    /**
     * Track undo for the last ImGui widget using a setter (no reflection).
     *
     * @param key         Unique key — use {@link #undoKey(Component, String)} to avoid cross-entity collisions
     * @param current     Current value of the field (read from component, not from buffer)
     * @param setter      Consumer to apply value on undo/redo
     * @param description Undo menu description
     * @param <T>         Value type
     * @return true if an undo command was pushed (edit completed)
     */
    public static <T> boolean track(String key, T current,
                                     Consumer<T> setter, String description) {
        if (ImGui.isItemActivated()) {
            startValues.put(key, current);
        }

        if (ImGui.isItemDeactivatedAfterEdit() && startValues.containsKey(key)) {
            @SuppressWarnings("unchecked")
            T startValue = (T) startValues.remove(key);
            if (!Objects.equals(startValue, current)) {
                UndoManager.getInstance().push(
                    new SetterUndoCommand<>(setter, startValue, current, description)
                );
                return true;
            }
        }
        return false;
    }

    /**
     * Track undo for a reflection field. Creates {@link SetComponentFieldCommand}
     * for proper prefab override sync.
     *
     * @param key       Unique key — use {@link #undoKey(Component, String)}
     * @param current   Current field value
     * @param component The component being edited
     * @param fieldName The reflection field name
     * @param entity    The entity — pass explicitly, NOT from FieldEditorContext
     * @return true if an undo command was pushed
     */
    public static boolean trackReflection(String key, Object current,
                                           Component component, String fieldName,
                                           EditorGameObject entity) {
        if (ImGui.isItemActivated()) {
            startValues.put(key, current);
        }

        if (ImGui.isItemDeactivatedAfterEdit() && startValues.containsKey(key)) {
            Object startValue = startValues.remove(key);
            if (!Objects.equals(startValue, current)) {
                if (entity != null) {
                    UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, fieldName, startValue, current, entity)
                    );
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Build a standard undo key for a component field.
     * Uses identityHashCode of the component instance to avoid cross-entity key collisions.
     */
    public static String undoKey(Component component, String fieldId) {
        return System.identityHashCode(component) + "@" + fieldId;
    }

    /**
     * Clear all tracking state. Must be called on:
     * <ul>
     *   <li>Inspector unbind</li>
     *   <li>Entity selection change</li>
     *   <li>Play mode enter/exit</li>
     * </ul>
     */
    public static void clear() {
        startValues.clear();
        PrimitiveEditors.clearUndoState();
        VectorEditors.clearUndoState();
    }

    /**
     * Number of pending undo entries (for debugging).
     */
    public static int pendingCount() {
        return startValues.size();
    }
}
