package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.panels.AssetPickerPopup;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.editor.events.AssetFocusRequestEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;

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
                // Picker button first
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
                        var scene = FieldEditorContext.getCurrentScene();
                        if (scene != null) scene.markDirty();
                    });
                }

                // Asset name (truncated if needed)
                ImGui.sameLine();
                String truncated = truncateAssetName(display);
                drawClickableAssetName(truncated, display, value);
            });

            FieldEditorContext.popOverrideStyle();
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
                // Picker button first
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
                        var scene = FieldEditorContext.getCurrentScene();
                        if (scene != null) scene.markDirty();
                    });
                }

                // Asset name (truncated if needed)
                ImGui.sameLine();
                String truncated = truncateAssetName(display);
                drawClickableAssetName(truncated, display, value);
            });

        } finally {
            ImGui.popID();
        }
        return false;
    }

    /**
     * Truncates asset name to fit in available space.
     */
    private static String truncateAssetName(String name) {
        if (name == null) return "(none)";

        // Calculate available width (approximate)
        float availWidth = ImGui.getContentRegionAvailX();
        float textWidth = ImGui.calcTextSize(name).x;

        if (textWidth <= availWidth) {
            return name;
        }

        // Truncate with ellipsis
        String ellipsis = "...";
        float ellipsisWidth = ImGui.calcTextSize(ellipsis).x;

        // Binary search for max chars that fit
        int maxChars = name.length();
        while (maxChars > 0) {
            String truncated = name.substring(0, maxChars) + ellipsis;
            if (ImGui.calcTextSize(truncated).x <= availWidth) {
                return truncated;
            }
            maxChars--;
        }

        return ellipsis;
    }

    /**
     * Draws a clickable asset name with hover effects (brighter color, underline, hand cursor).
     * Clicking publishes an {@link AssetFocusRequestEvent} to highlight the asset in the browser.
     *
     * @param truncated Truncated display name
     * @param fullName  Full display name (for tooltip when truncated)
     * @param value     The asset object (null shows disabled text)
     */
    static void drawClickableAssetName(String truncated, String fullName, Object value) {
        if (value != null) {
            boolean hovered = ImGui.isMouseHoveringRect(
                    ImGui.getCursorScreenPosX(),
                    ImGui.getCursorScreenPosY(),
                    ImGui.getCursorScreenPosX() + ImGui.calcTextSize(truncated).x,
                    ImGui.getCursorScreenPosY() + ImGui.getTextLineHeight()
            );
            if (hovered) {
                ImGui.textColored(0.7f, 1.0f, 0.7f, 1.0f, truncated);
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
                // Underline
                var min = ImGui.getItemRectMin();
                var max = ImGui.getItemRectMax();
                ImGui.getWindowDrawList().addLine(min.x, max.y, max.x, max.y,
                        ImGui.getColorU32(0.7f, 1.0f, 0.7f, 1.0f));
            } else {
                ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, truncated);
            }
            if (ImGui.isItemClicked()) {
                String path = Assets.getPathForResource(value);
                if (path != null) {
                    EditorEventBus.get().publish(new AssetFocusRequestEvent(path));
                }
            }
        } else {
            ImGui.textDisabled(truncated);
        }
        // Tooltip with full name if truncated
        if (!truncated.equals(fullName) && ImGui.isItemHovered()) {
            ImGui.setTooltip(fullName);
        }
    }

    /**
     * Opens the asset picker popup without drawing a field.
     * Useful when a custom field editor needs to handle the picker button itself.
     *
     * @param assetType   The asset class type
     * @param currentPath Current asset path (for initial selection)
     * @param callback    Callback when an asset is selected
     */
    public static void openPicker(Class<?> assetType, String currentPath, Consumer<Object> callback) {
        assetPicker.open(assetType, currentPath, callback);
    }

    /**
     * Renders the asset picker popup. Call once per frame.
     */
    public static void renderAssetPicker() {
        assetPicker.render();
    }
}
