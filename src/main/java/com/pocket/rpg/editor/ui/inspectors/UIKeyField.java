package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.UIComponent;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.ui.fields.FieldEditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/**
 * Shared UI Key field drawer for all UI component inspectors.
 * Shows the uiKey field with a full-width color-coded row background:
 * <ul>
 *   <li>Amber — empty (field is unset, key not assigned)</li>
 *   <li>Green — valid (key is set and unique in the scene)</li>
 *   <li>Red — error (key is duplicated by another component in the scene)</li>
 * </ul>
 */
final class UIKeyField {

    // Amber hint for empty key — draws attention without alarming
    private static final int COLOR_EMPTY = ImGui.colorConvertFloat4ToU32(0.7f, 0.5f, 0.1f, 0.35f);
    // Subtle green for valid key
    private static final int COLOR_VALID = ImGui.colorConvertFloat4ToU32(0.1f, 0.6f, 0.25f, 0.3f);
    // Red for duplicate key
    private static final int COLOR_ERROR = ImGui.colorConvertFloat4ToU32(1f, 0.1f, 0.1f, 0.7f);

    private static final String TOOLTIP =
            "Unique identifier for UIManager lookups.\n" +
            "Set this to access the component from code at runtime\n" +
            "(e.g. UIManager.getText(\"ElevationText\")).\n\n" +
            "Must be unique across all UI components in the scene.\n" +
            "Leave empty if the component is not accessed by code.";

    private UIKeyField() {}

    /**
     * Draws the UI Key field with color-coded row background and tooltip.
     * Call this in place of {@code FieldEditors.drawString("UI Key", component, "uiKey")}.
     *
     * @param component the UI component being inspected
     * @return true if the field value changed
     */
    static boolean draw(Component component) {
        String currentKey = ComponentReflectionUtils.getString(component, "uiKey", "");
        boolean isEmpty = currentKey.isEmpty();
        boolean isDuplicate = !isEmpty && isDuplicateKey(currentKey, component);

        // Select row background color
        int rowColor;
        if (isDuplicate) {
            rowColor = COLOR_ERROR;
        } else if (isEmpty) {
            rowColor = COLOR_EMPTY;
        } else {
            rowColor = COLOR_VALID;
        }

        // Begin row highlight using draw list channels (background behind content)
        ImVec2 cursorPos = ImGui.getCursorScreenPos();
        float startY = cursorPos.y;

        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.channelsSplit(2);
        drawList.channelsSetCurrent(1); // Draw content on foreground channel

        boolean changed = FieldEditors.drawString(MaterialIcons.VpnKey + " UI Key", component, "uiKey");

        // Tooltip
        if (ImGui.isItemHovered()) {
            if (isDuplicate) {
                ImGui.setTooltip("ERROR: Duplicate key \"" + currentKey + "\"!\n" +
                        "Another UI component in this scene uses the same key.\n" +
                        "The last registered component will overwrite the first.\n\n" +
                        TOOLTIP);
            } else {
                ImGui.setTooltip(TOOLTIP);
            }
        }

        // Draw background rectangle on background channel
        drawList.channelsSetCurrent(0);

        float padding = 2f;
        float startX = ImGui.getWindowPos().x + ImGui.getWindowContentRegionMin().x - padding;
        float endX = ImGui.getWindowPos().x + ImGui.getWindowContentRegionMax().x + padding;
        float endY = ImGui.getCursorScreenPos().y;

        drawList.addRectFilled(startX, startY - padding, endX, endY, rowColor);

        // Merge channels
        drawList.channelsMerge();

        ImGui.separator();

        return changed;
    }

    /**
     * Checks whether the given key is used by more than one UIComponent in the current scene.
     * Early-returns on the second match for efficiency.
     */
    private static boolean isDuplicateKey(String key, Component self) {
        EditorScene scene = FieldEditorContext.getCurrentScene();
        if (scene == null) return false;

        int count = 0;
        for (EditorGameObject entity : scene.getEntities()) {
            for (Component comp : entity.getComponents()) {
                if (comp instanceof UIComponent uiComp && key.equals(uiComp.getUiKey())) {
                    count++;
                    if (count > 1) return true;
                }
            }
        }
        return false;
    }
}
