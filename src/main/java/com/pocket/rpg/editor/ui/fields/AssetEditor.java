package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.panels.AssetPickerPopup;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;

/**
 * Field editor for asset types (Sprite, Texture, etc.).
 */
public final class AssetEditor {

    private static final AssetPickerPopup assetPicker = new AssetPickerPopup();

    // State for async callback
    private static Component assetPickerTargetComponent = null;
    private static String assetPickerFieldName = null;

    private AssetEditor() {}

    public static boolean drawAsset(String label, Component component, String fieldName,
                                    Class<?> assetType, EditorGameObject entity) {
        Object value = ComponentReflectionUtils.getFieldValue(component, fieldName);
        String display = FieldEditorUtils.getAssetDisplayName(value);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        try {
            FieldEditorUtils.inspectorRow(label, () -> {
                if (value != null) {
                    ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, display);
                } else {
                    ImGui.textDisabled(display);
                }

                ImGui.sameLine();
                if (ImGui.smallButton("...")) {
                    assetPickerTargetComponent = component;
                    assetPickerFieldName = fieldName;
                    Object oldValue = ComponentReflectionUtils.getFieldValue(component, fieldName);
                    String currentPath = oldValue != null ? Assets.getPathForResource(oldValue) : null;

                    assetPicker.open(assetType, currentPath, selectedAsset -> {
                        UndoManager.getInstance().execute(
                                new SetComponentFieldCommand(
                                        assetPickerTargetComponent,
                                        assetPickerFieldName,
                                        oldValue,
                                        selectedAsset,
                                        FieldEditorContext.getEntity()
                                )
                        );
                        FieldEditorContext.markFieldOverridden(assetPickerFieldName, selectedAsset);
                    });
                }
            });

            FieldEditorContext.popOverrideStyle(fieldName);
            FieldEditorUtils.drawResetButtonIfNeeded(component, fieldName);

        } finally {
            ImGui.popID();
        }
        return false;
    }

    /**
     * Renders the asset picker popup. Call once per frame.
     */
    public static void renderAssetPicker() {
        assetPicker.render();
    }
}
