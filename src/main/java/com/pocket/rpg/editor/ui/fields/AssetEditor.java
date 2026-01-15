package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.panels.AssetPickerPopup;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;

import java.util.function.Consumer;
import java.util.function.Supplier;

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

    // ========================================================================
    // GETTER/SETTER VARIANT (no reflection, uses Consumer for undo)
    // ========================================================================

    /**
     * Draws an asset field using getter/setter pattern with undo support.
     *
     * @param label     Display label
     * @param key       Unique key for undo tracking
     * @param getter    Supplier to get current asset value
     * @param setter    Consumer to set new asset value
     * @param assetType The asset class type
     * @param <T>       Asset type
     * @return true if value was changed (always false since change is via popup)
     */
    public static <T> boolean drawAsset(String label, String key,
                                         Supplier<T> getter, Consumer<T> setter,
                                         Class<T> assetType) {
        T value = getter.get();
        String display = FieldEditorUtils.getAssetDisplayName(value);

        ImGui.pushID(key);

        try {
            FieldEditorUtils.inspectorRow(label, () -> {
                if (value != null) {
                    ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, display);
                } else {
                    ImGui.textDisabled(display);
                }

                ImGui.sameLine();
                if (ImGui.smallButton("...")) {
                    T oldValue = getter.get();
                    String currentPath = oldValue != null ? Assets.getPathForResource(oldValue) : null;

                    assetPicker.open(assetType, currentPath, selectedAsset -> {
                        @SuppressWarnings("unchecked")
                        T typedAsset = (T) selectedAsset;

                        // Asset selection is instant - push undo immediately
                        UndoManager.getInstance().execute(
                                new SetterUndoCommand<>(setter, oldValue, typedAsset, "Change " + label)
                        );
                    });
                }
            });

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
