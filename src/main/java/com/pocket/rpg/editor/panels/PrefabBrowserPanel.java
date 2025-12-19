package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.EditorModeManager;
import com.pocket.rpg.editor.tools.EditorTool;
import com.pocket.rpg.editor.tools.ToolManager;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.rendering.Sprite;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImString;
import lombok.Getter;
import lombok.Setter;

/**
 * Panel for browsing and selecting prefabs.
 * <p>
 * Features:
 * - Grid display of registered prefabs
 * - Search/filter functionality
 * - Preview images for each prefab
 * - Clicking prefab switches to entity mode
 */
public class PrefabBrowserPanel {

    @Setter private EditorModeManager modeManager;
    @Setter private ToolManager toolManager;
    @Setter private EditorTool entityPlacerTool;
    @Getter private String selectedPrefabId = null;

    // Filter text
    private final ImString filterText = new ImString(64);

    // UI settings
    private static final float BUTTON_SIZE = 64f;
    private static final float BUTTON_PADDING = 8f;
    private static final float IMAGE_SIZE = BUTTON_SIZE - 8f;

    /**
     * Renders the prefab browser panel.
     */
    public void render() {
        if (ImGui.begin("Prefabs")) {
            // Search filter
            ImGui.setNextItemWidth(-1);
            if (ImGui.inputTextWithHint("##filter", "Search prefabs...", filterText)) {
                // Filter changed
            }

            ImGui.separator();

            // Check if registry has any prefabs
            PrefabRegistry registry = PrefabRegistry.getInstance();
            if (registry.getPrefabCount() == 0) {
                ImGui.textDisabled("No prefabs registered.");
                ImGui.textDisabled("Register prefabs in PrefabRegistry.");
                ImGui.end();
                return;
            }

            // Calculate grid layout
            float windowWidth = ImGui.getContentRegionAvailX();
            int columns = Math.max(1, (int) (windowWidth / (BUTTON_SIZE + BUTTON_PADDING)));

            String filter = filterText.get().toLowerCase();

            int column = 0;
            for (Prefab prefab : registry.getAllPrefabs()) {
                // Apply filter
                if (!filter.isEmpty()) {
                    boolean matchesId = prefab.getId().toLowerCase().contains(filter);
                    boolean matchesName = prefab.getDisplayName().toLowerCase().contains(filter);
                    if (!matchesId && !matchesName) {
                        continue;
                    }
                }

                // Layout
                if (column > 0) {
                    ImGui.sameLine();
                }

                renderPrefabButton(prefab);

                column = (column + 1) % columns;
            }

            // Info about selected prefab
            if (selectedPrefabId != null) {
                ImGui.separator();
                Prefab selected = registry.getPrefab(selectedPrefabId);
                if (selected != null) {
                    ImGui.text("Selected: " + selected.getDisplayName());
                    ImGui.textDisabled("Click in viewport to place");
                }
            }
        }
        ImGui.end();
    }

    /**
     * Renders a single prefab button with preview.
     */
    private void renderPrefabButton(Prefab prefab) {
        boolean isSelected = prefab.getId().equals(selectedPrefabId);

        // Push selection style
        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.6f, 1.0f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.4f, 0.7f, 1.0f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.5f, 0.9f, 1.0f);
        }

        ImGui.pushID(prefab.getId());
        ImGui.beginGroup();

        // Get preview sprite
        Sprite preview = prefab.getPreviewSprite();
        if (preview == null) {
            preview = PrefabRegistry.getInstance().getDefaultPreviewSprite();
        }

        boolean clicked = false;

        if (preview != null && preview.getTexture() != null) {
            // Render preview image as button
            int texId = preview.getTexture().getTextureId();
            float u0 = preview.getU0();
            float v0 = preview.getV1(); // Flip V for OpenGL
            float u1 = preview.getU1();
            float v1 = preview.getV0();

            clicked = ImGui.imageButton(String.valueOf(texId), texId, IMAGE_SIZE, IMAGE_SIZE, u0, v0, u1, v1);
        } else {
            // Fallback button with text
            clicked = ImGui.button(prefab.getId().substring(0, Math.min(3, prefab.getId().length())).toUpperCase(), BUTTON_SIZE, BUTTON_SIZE);
        }

        ImGui.endGroup();

        // Handle click
        if (clicked) {
            selectPrefab(prefab.getId());
        }

        // Tooltip
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(prefab.getDisplayName());
            ImGui.textDisabled("ID: " + prefab.getId());
            if (prefab.getCategory() != null) {
                ImGui.textDisabled("Category: " + prefab.getCategory());
            }
            int propCount = prefab.getEditableProperties().size();
            if (propCount > 0) {
                ImGui.textDisabled(propCount + " editable properties");
            }
            ImGui.endTooltip();
        }

        ImGui.popID();

        if (isSelected) {
            ImGui.popStyleColor(3);
        }
    }

    /**
     * Selects a prefab and switches to entity mode.
     */
    public void selectPrefab(String prefabId) {
        selectedPrefabId = prefabId;

        // Switch to entity mode
        if (modeManager != null) {
            modeManager.switchTo(EditorModeManager.Mode.ENTITY);
        }

        // Activate entity placer tool
        if (toolManager != null && entityPlacerTool != null) {
            toolManager.setActiveTool(entityPlacerTool);
        }
    }

    /**
     * Clears the current selection.
     */
    public void clearSelection() {
        selectedPrefabId = null;
    }

    /**
     * Checks if a prefab is selected.
     */
    public boolean hasSelection() {
        return selectedPrefabId != null;
    }
}
