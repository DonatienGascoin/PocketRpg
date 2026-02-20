package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import imgui.ImGui;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Field editor for selecting a string from a dynamic list of options.
 * Handles undo automatically via {@link SetterUndoCommand}.
 * <p>
 * Use this for fields that pick from runtime-populated lists
 * (scene names, spawn IDs, event names, camera bounds, etc.)
 * <p>
 * Example usage:
 * <pre>
 * StringComboEditor.draw("Target Scene", "targetScene_" + id,
 *     component::getTargetScene, component::setTargetScene, sceneNames);
 * </pre>
 */
public final class StringComboEditor {

    private StringComboEditor() {}

    /**
     * Draws a combo box for selecting a string from a list of options.
     *
     * @param label    Display label
     * @param key      Unique key for ImGui ID
     * @param getter   Supplier to get current value
     * @param setter   Consumer to set new value
     * @param options  Available options to choose from
     * @return true if value was changed
     */
    public static boolean draw(String label, String key,
                                Supplier<String> getter, Consumer<String> setter,
                                List<String> options) {
        return draw(label, key, getter, setter, options, false);
    }

    /**
     * Draws a combo box for selecting a string from a list of options.
     *
     * @param label    Display label
     * @param key      Unique key for ImGui ID
     * @param getter   Supplier to get current value
     * @param setter   Consumer to set new value
     * @param options  Available options to choose from
     * @param nullable If true, includes a "None" option at the top to clear the value
     * @return true if value was changed
     */
    public static boolean draw(String label, String key,
                                Supplier<String> getter, Consumer<String> setter,
                                List<String> options, boolean nullable) {
        String current = getter.get();
        String displayLabel = (current == null || current.isEmpty()) ? "Select..." : current;
        boolean changed = false;

        ImGui.pushID(key);

        final boolean[] changedArr = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            if (ImGui.beginCombo("##" + key, displayLabel)) {
                if (nullable) {
                    if (ImGui.selectable("None", current == null || current.isEmpty())) {
                        String oldValue = current;
                        String newValue = "";
                        setter.accept(newValue);
                        if (!Objects.equals(oldValue, newValue)) {
                            UndoManager.getInstance().push(
                                    new SetterUndoCommand<>(setter, oldValue, newValue, "Clear " + label)
                            );
                        }
                        changedArr[0] = true;
                    }
                    ImGui.separator();
                }

                for (String option : options) {
                    boolean selected = option.equals(current);
                    if (ImGui.selectable(option, selected)) {
                        String oldValue = current;
                        setter.accept(option);
                        if (!Objects.equals(oldValue, option)) {
                            UndoManager.getInstance().push(
                                    new SetterUndoCommand<>(setter, oldValue, option, "Change " + label)
                            );
                        }
                        changedArr[0] = true;
                    }
                }
                ImGui.endCombo();
            }
        });

        ImGui.popID();
        return changedArr[0];
    }
}
