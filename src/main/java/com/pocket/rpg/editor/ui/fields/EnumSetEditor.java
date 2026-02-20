package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.ListItemCommand;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Field editor that renders all values of an enum as inline checkboxes (flags-style).
 * Backed by a {@code List<E>} field. Handles undo automatically.
 * <p>
 * Example usage:
 * <pre>
 * EnumSetEditor.draw("Interact From", component, "interactFrom", Direction.class, entity);
 * </pre>
 */
public final class EnumSetEditor {

    private EnumSetEditor() {}

    /**
     * Draws enum checkboxes using reflection to access the list field.
     * Undo is handled via {@link ListItemCommand}.
     *
     * @param label     Display label
     * @param component The component containing the List field
     * @param fieldName The field name (must be a {@code List<E>})
     * @param enumClass The enum class
     * @param entity    The editor entity for undo support (null in play mode)
     * @param <E>       Enum type
     * @return true if any value was changed
     */
    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> boolean draw(String label, Component component,
                                                    String fieldName, Class<E> enumClass,
                                                    EditorGameObject entity) {
        List<E> list = (List<E>) ComponentReflectionUtils.getFieldValue(component, fieldName);
        if (list == null) {
            list = new ArrayList<>();
            ComponentReflectionUtils.setFieldValue(component, fieldName, list);
        }

        E[] constants = enumClass.getEnumConstants();
        boolean changed = false;

        ImGui.pushID(fieldName);

        List<E> finalList = list;
        FieldEditorUtils.inspectorRow(label, () -> {
            for (int i = 0; i < constants.length; i++) {
                E value = constants[i];
                boolean active = finalList.contains(value);

                if (ImGui.checkbox(value.name() + "##" + fieldName, active)) {
                    if (active) {
                        // Remove
                        int index = finalList.indexOf(value);
                        if (index >= 0 && entity != null) {
                            UndoManager.getInstance().execute(
                                    new ListItemCommand(component, fieldName,
                                            ListItemCommand.Operation.REMOVE, index,
                                            value, null, entity)
                            );
                        } else if (index >= 0) {
                            finalList.remove(index);
                        }
                    } else {
                        // Add
                        if (entity != null) {
                            UndoManager.getInstance().execute(
                                    new ListItemCommand(component, fieldName,
                                            ListItemCommand.Operation.ADD, finalList.size(),
                                            null, value, entity)
                            );
                        } else {
                            finalList.add(value);
                        }
                    }
                }

                if (i < constants.length - 1) {
                    ImGui.sameLine();
                }
            }
        });

        ImGui.popID();
        return changed;
    }

    /**
     * Draws enum checkboxes using getter/setter pattern.
     * Undo is handled via {@link SetterUndoCommand} with list snapshots.
     *
     * @param label     Display label
     * @param key       Unique key for undo tracking
     * @param getter    Supplier to get current list
     * @param setter    Consumer to set the list (for undo restore)
     * @param enumClass The enum class
     * @param <E>       Enum type
     * @return true if any value was changed
     */
    public static <E extends Enum<E>> boolean draw(String label, String key,
                                                    Supplier<List<E>> getter,
                                                    Consumer<List<E>> setter,
                                                    Class<E> enumClass) {
        List<E> list = getter.get();
        if (list == null) {
            list = new ArrayList<>();
            setter.accept(list);
        }

        E[] constants = enumClass.getEnumConstants();
        boolean changed = false;

        ImGui.pushID(key);

        List<E> finalList = list;
        FieldEditorUtils.inspectorRow(label, () -> {
            for (int i = 0; i < constants.length; i++) {
                E value = constants[i];
                boolean active = finalList.contains(value);

                if (ImGui.checkbox(value.name() + "##" + key, active)) {
                    List<E> snapshot = new ArrayList<>(finalList);

                    if (active) {
                        finalList.remove(value);
                    } else {
                        finalList.add(value);
                    }

                    List<E> after = new ArrayList<>(finalList);
                    UndoManager.getInstance().push(
                            new SetterUndoCommand<>(setter, snapshot, after, "Change " + label)
                    );
                }

                if (i < constants.length - 1) {
                    ImGui.sameLine();
                }
            }
        });

        ImGui.popID();
        return changed;
    }
}
