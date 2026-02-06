package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.ui.fields.FieldEditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditorUtils;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Shared Component Key field drawer for component inspectors.
 * Shows the componentKey field with a full-width color-coded row background:
 * <ul>
 *   <li>Amber — empty (field is unset, key not assigned)</li>
 *   <li>Green — valid (key is set and unique in the scene)</li>
 *   <li>Red — error (key is duplicated by another component in the scene)</li>
 * </ul>
 * <p>
 * Drawn automatically by ReflectionFieldEditor for all components.
 * Also called explicitly by custom UI inspectors.
 */
public final class ComponentKeyField {

    // Amber hint for empty key — draws attention without alarming
    private static final int COLOR_EMPTY = ImGui.colorConvertFloat4ToU32(0.7f, 0.5f, 0.1f, 0.35f);
    // Subtle green for valid key
    private static final int COLOR_VALID = ImGui.colorConvertFloat4ToU32(0.1f, 0.6f, 0.25f, 0.3f);
    // Red for duplicate key
    private static final int COLOR_ERROR = ImGui.colorConvertFloat4ToU32(1f, 0.1f, 0.1f, 0.7f);

    private static final String TOOLTIP =
            "Unique identifier for ComponentKeyRegistry lookups.\n" +
            "Set this to reference the component from other components\n" +
            "via @ComponentReference(source = Source.KEY).\n\n" +
            "Must be unique across all components in the scene.\n" +
            "Leave empty if the component is not referenced by key.";

    /** Components whose key field has been manually expanded via the header button. */
    private static final Set<Component> expandedComponents =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private ComponentKeyField() {}

    /** Clears all toggle state. Called on scene transitions. */
    public static void clearExpandedState() {
        expandedComponents.clear();
    }

    /**
     * Returns true if the key field should be visible for this component.
     * Visible when the key is set (non-empty) or the user toggled it on.
     */
    public static boolean isVisible(Component component) {
        String key = component.getComponentKey();
        if (key != null && !key.isEmpty()) return true;
        return expandedComponents.contains(component);
    }

    /**
     * Draws the key toggle button for the component header row.
     * Call this with {@code ImGui.sameLine(x)} already set.
     * <ul>
     *   <li>Key is set → disabled button with tooltip</li>
     *   <li>Key is empty → toggle button to show/hide the key field</li>
     * </ul>
     */
    public static void drawHeaderButton(Component component) {
        String key = component.getComponentKey();
        boolean hasKey = key != null && !key.isEmpty();

        if (hasKey) {
            // Disabled — key is set, can't hide
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 0.5f);
            ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 0.5f);
            ImGui.smallButton(MaterialIcons.VpnKey + "##key");
            ImGui.popStyleColor(2);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Component Key is set.\nClear the key to hide this field.");
            }
        } else {
            boolean expanded = expandedComponents.contains(component);
            if (expanded) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.5f, 0.7f, 1f);
            }
            if (ImGui.smallButton(MaterialIcons.VpnKey + "##key")) {
                if (expanded) {
                    expandedComponents.remove(component);
                } else {
                    expandedComponents.add(component);
                }
            }
            if (expanded) {
                ImGui.popStyleColor();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(expanded ? "Hide Component Key field" : "Show Component Key field");
            }
        }
    }

    /**
     * Draws the Component Key field with color-coded row background and tooltip.
     * Hidden by default when empty — use {@link #drawHeaderButton} to toggle visibility.
     *
     * @param component the component being inspected
     * @return true if the field value changed
     */
    public static boolean draw(Component component) {
        if (!isVisible(component)) return false;

        String currentKey = component.getComponentKey() != null ? component.getComponentKey() : "";
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

        FieldEditorUtils.setNextLabelWidth(145f);
        boolean changed = FieldEditors.drawString(
                MaterialIcons.VpnKey + " Component Key",
                "componentKey",
                () -> component.getComponentKey() != null ? component.getComponentKey() : "",
                component::setComponentKey
        );

        // Tooltip
        if (ImGui.isItemHovered()) {
            if (isDuplicate) {
                ImGui.setTooltip("ERROR: Duplicate key \"" + currentKey + "\"!\n" +
                        "Another component in this scene uses the same key.\n" +
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
     * Checks whether the given key is used by more than one component in the current scene.
     * Early-returns on the second match for efficiency.
     */
    private static boolean isDuplicateKey(String key, Component self) {
        EditorScene scene = FieldEditorContext.getCurrentScene();
        if (scene == null) return false;

        int count = 0;
        for (EditorGameObject entity : scene.getEntities()) {
            for (Component comp : entity.getComponents()) {
                if (key.equals(comp.getComponentKey())) {
                    count++;
                    if (count > 1) return true;
                }
            }
        }
        return false;
    }
}
