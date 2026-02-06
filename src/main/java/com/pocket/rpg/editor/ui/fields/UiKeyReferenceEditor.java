package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.UIComponent;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import com.pocket.rpg.serialization.UiKeyRefMeta;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.List;

/**
 * Field editor for @UiKeyReference fields.
 * Draws a combo dropdown populated with all uiKey values in the current scene,
 * filtered by the expected UIComponent type from the @UiKeyReference annotation.
 */
public final class UiKeyReferenceEditor {

    private static final ImInt intBuffer = new ImInt();

    private UiKeyReferenceEditor() {}

    /**
     * Draws a dropdown of available UI keys for a @UiKeyReference field.
     *
     * @param label     Display label
     * @param component The component being edited
     * @param fieldName The @UiKeyReference field name
     * @param refMeta   The @UiKeyReference metadata (for type filtering)
     * @return true if the value changed
     */
    public static boolean draw(String label, Component component, String fieldName, UiKeyRefMeta refMeta) {
        String currentKey = ComponentReflectionUtils.getString(component, fieldName, "");

        // Collect all available UI keys from the scene
        List<String> availableKeys = collectAvailableKeys(refMeta.componentType());

        // Build display array: first entry is "(none)" for empty/unset
        String[] displayNames = new String[availableKeys.size() + 1];
        displayNames[0] = "(none)";
        for (int i = 0; i < availableKeys.size(); i++) {
            displayNames[i + 1] = availableKeys.get(i);
        }

        // Find current selection index
        int currentIndex = 0;
        if (!currentKey.isEmpty()) {
            for (int i = 0; i < availableKeys.size(); i++) {
                if (availableKeys.get(i).equals(currentKey)) {
                    currentIndex = i + 1;
                    break;
                }
            }
            // If key is set but not found in scene, add it as a special entry
            if (currentIndex == 0) {
                // Key exists but isn't in the scene — show it with a warning marker
                String[] extended = new String[displayNames.length + 1];
                extended[0] = displayNames[0];
                extended[1] = currentKey + " (missing)";
                System.arraycopy(displayNames, 1, extended, 2, displayNames.length - 1);
                displayNames = extended;
                currentIndex = 1;
            }
        }

        intBuffer.set(currentIndex);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        final String[] names = displayNames;
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.combo("##" + fieldName, intBuffer, names);
        });

        FieldEditorContext.popOverrideStyle();

        if (changed[0]) {
            String oldValue = currentKey;
            String newValue;
            int selectedIndex = intBuffer.get();

            if (selectedIndex == 0) {
                newValue = ""; // (none) selected
            } else {
                // Account for possible "(missing)" entry
                String selectedDisplay = names[selectedIndex];
                if (selectedDisplay.endsWith(" (missing)")) {
                    newValue = selectedDisplay.substring(0, selectedDisplay.length() - " (missing)".length());
                } else {
                    newValue = selectedDisplay;
                }
            }

            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            FieldEditorContext.markFieldOverridden(fieldName, newValue);

            // Combo is instant change — push undo immediately
            UndoManager.getInstance().push(
                    new SetComponentFieldCommand(component, fieldName, oldValue, newValue, FieldEditorContext.getEntity())
            );
        }

        // Tooltip showing the resolved type
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("UI Key reference → " + refMeta.componentType().getSimpleName() +
                    "\nResolved at runtime via UIManager.");
        }

        boolean reset = FieldEditorUtils.drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    /**
     * Collects all non-empty uiKey values from UIComponents in the current editor scene,
     * filtered to only include components assignable to the expected type.
     */
    private static List<String> collectAvailableKeys(Class<? extends UIComponent> expectedType) {
        List<String> keys = new ArrayList<>();
        EditorScene scene = FieldEditorContext.getCurrentScene();
        if (scene == null) {
            return keys;
        }

        for (EditorGameObject entity : scene.getEntities()) {
            for (Component comp : entity.getComponents()) {
                if (comp instanceof UIComponent uiComp) {
                    String key = uiComp.getUiKey();
                    if (key != null && !key.isBlank() && expectedType.isInstance(uiComp)) {
                        if (!keys.contains(key)) {
                            keys.add(key);
                        }
                    }
                }
            }
        }

        keys.sort(String::compareTo);
        return keys;
    }
}
