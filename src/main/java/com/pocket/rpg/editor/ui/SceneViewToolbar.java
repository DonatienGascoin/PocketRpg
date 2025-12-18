package com.pocket.rpg.editor.ui;

import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.EditorModeManager;
import com.pocket.rpg.editor.EditorToolController;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.EditorTool;
import com.pocket.rpg.editor.tools.ToolManager;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Consumer;

/**
 * Toolbar rendered inside the Scene viewport.
 * <p>
 * Contains:
 * - Tool buttons with FontAwesome icons
 * - Mode dropdown (Tilemap/Collision)
 * - Visibility toggles (Grid, Collision overlay)
 */
public class SceneViewToolbar {

    private final EditorContext context;
    private final EditorToolController toolController;

    @Setter
    private Consumer<String> messageCallback;

    // Tool definitions for each mode
    private static final ToolDef[] TILEMAP_TOOLS = {
            new ToolDef("Brush", FontAwesomeIcons.PaintBrush, "B"),
            new ToolDef("Eraser", FontAwesomeIcons.Eraser, "E"),
            new ToolDef("Fill", FontAwesomeIcons.FillDrip, "F"),
            new ToolDef("Rectangle", FontAwesomeIcons.VectorSquare, "R"),
            new ToolDef("Picker", FontAwesomeIcons.EyeDropper, "I"),
    };

    private static final ToolDef[] COLLISION_TOOLS = {
            new ToolDef("Collision Brush", FontAwesomeIcons.PaintBrush, "C"),
            new ToolDef("Collision Eraser", FontAwesomeIcons.Eraser, "X"),
            new ToolDef("Collision Fill", FontAwesomeIcons.FillDrip, "G"),
            new ToolDef("Collision Rectangle", FontAwesomeIcons.VectorSquare, "H"),
            new ToolDef("Collision Picker", FontAwesomeIcons.EyeDropper, "V"),
    };

    // Visibility state
    @Getter
    @Setter
    private boolean showGrid = true;

    public SceneViewToolbar(EditorContext context, EditorToolController toolController) {
        this.context = context;
        this.toolController = toolController;
    }

    /**
     * Renders the toolbar. Call this at the top of the Scene viewport.
     *
     * @param viewportWidth Available width for the toolbar
     */
    public void render(float viewportWidth) {
        // Disable keyboard navigation for toolbar to prevent WASD interference
        ImGui.pushTabStop(false);

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 4, 4);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6, 4);

        // Tool buttons
        renderToolButtons();

        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();

        // Mode dropdown
        renderModeDropdown();

        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();

        // Visibility toggles
        renderVisibilityToggles();

        ImGui.popStyleVar(2);

        ImGui.popTabStop();
    }

    /**
     * Renders tool icon buttons based on current mode.
     */
    private void renderToolButtons() {
        EditorModeManager modeManager = context.getModeManager();
        ToolManager toolManager = context.getToolManager();
        EditorTool activeTool = toolManager.getActiveTool();

        ToolDef[] tools = modeManager.isTilemapMode() ? TILEMAP_TOOLS : COLLISION_TOOLS;

        for (ToolDef def : tools) {
            boolean isActive = activeTool != null && activeTool.getName().equals(def.toolName);

            // Highlight active tool
            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.6f, 1.0f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.4f, 0.7f, 1.0f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.5f, 0.9f, 1.0f);
            }

            // Icon button
            if (ImGui.button(def.icon + "##" + def.toolName)) {
                toolManager.setActiveTool(def.toolName);
                showMessage(def.toolName);
            }

            if (isActive) {
                ImGui.popStyleColor(3);
            }

            // Tooltip with name and shortcut
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(def.toolName + " (" + def.shortcut + ")");
            }

            ImGui.sameLine();
        }
    }

    /**
     * Renders the mode dropdown.
     */
    private void renderModeDropdown() {
        EditorModeManager modeManager = context.getModeManager();
        String currentModeName = modeManager.getCurrentMode().getDisplayName();

        ImGui.pushItemWidth(100);
        if (ImGui.beginCombo("##ModeCombo", currentModeName)) {
            for (EditorModeManager.Mode mode : EditorModeManager.Mode.values()) {
                boolean isSelected = mode == modeManager.getCurrentMode();
                if (ImGui.selectable(mode.getDisplayName(), isSelected)) {
                    switchToMode(mode);
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        ImGui.popItemWidth();

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Editor Mode (M/N)");
        }
    }

    /**
     * Renders visibility toggle buttons.
     */
    private void renderVisibilityToggles() {
        EditorScene scene = context.getCurrentScene();

        // Grid toggle
        boolean gridActive = showGrid;
        if (gridActive) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.2f, 1.0f);
        }
        if (ImGui.button(FontAwesomeIcons.Th + "##Grid")) {
            showGrid = !showGrid;
        }
        if (gridActive) {
            ImGui.popStyleColor();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Toggle Grid");
        }

        ImGui.sameLine();

        // Collision overlay toggle
        boolean collisionVisible = scene != null && scene.isCollisionVisible();
        if (collisionVisible) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.5f, 0.2f, 0.2f, 1.0f);
        }
        if (ImGui.button(FontAwesomeIcons.BorderAll + "##Collision")) {
            if (scene != null) {
                scene.setCollisionVisible(!collisionVisible);
            }
        }
        if (collisionVisible) {
            ImGui.popStyleColor();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Toggle Collision Overlay");
        }
    }

    /**
     * Switches to the specified mode.
     */
    private void switchToMode(EditorModeManager.Mode mode) {
        EditorModeManager modeManager = context.getModeManager();
        ToolManager toolManager = context.getToolManager();

        modeManager.switchTo(mode);

        // Set appropriate default tool
        if (mode == EditorModeManager.Mode.TILEMAP) {
            toolManager.setActiveTool("Brush");
            showMessage("Switched to Tilemap Mode");
        } else {
            toolManager.setActiveTool("Collision Brush");
            toolController.syncCollisionZLevels();
            showMessage("Switched to Collision Mode");
        }
    }

    private void showMessage(String message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }

    /**
     * Returns whether grid should be shown.
     */
    public boolean isShowGrid() {
        return showGrid;
    }

    /**
     * Tool definition record.
     */
    private record ToolDef(String toolName, String icon, String shortcut) {}
}