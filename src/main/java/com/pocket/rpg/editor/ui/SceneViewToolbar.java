package com.pocket.rpg.editor.ui;

import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.EditorToolController;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.CollisionPanel;
import com.pocket.rpg.editor.panels.TilesetPalettePanel;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.EditorTool;
import com.pocket.rpg.editor.tools.ToolManager;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Toolbar rendered inside the Scene viewport.
 * Tools are shown based on which panels are open (panel-driven visibility).
 */
public class SceneViewToolbar {

    private final EditorContext context;
    private final EditorToolController toolController;

    @Setter
    private Consumer<String> messageCallback;

    @Setter
    private TilesetPalettePanel tilesetPalettePanel;

    @Setter
    private CollisionPanel collisionPanel;

    // Selection tool is always visible
    private static final ToolDef SELECT_TOOL = new ToolDef("Select", MaterialIcons.NearMe, "V");

    private static final ToolDef[] TILEMAP_TOOLS = {
            new ToolDef("Brush", MaterialIcons.Brush, "B"),
            new ToolDef("Eraser", MaterialIcons.Delete, "E"),
            new ToolDef("Fill", MaterialIcons.FormatColorFill, "F"),
            new ToolDef("Rectangle", MaterialIcons.CropSquare, "R"),
            new ToolDef("Picker", MaterialIcons.Colorize, "I"),
    };

    private static final ToolDef[] COLLISION_TOOLS = {
            new ToolDef("Collision Brush", MaterialIcons.Brush, "C"),
            new ToolDef("Collision Eraser", MaterialIcons.Delete, "X"),
            new ToolDef("Collision Fill", MaterialIcons.FormatColorFill, "G"),
            new ToolDef("Collision Rectangle", MaterialIcons.CropSquare, "H"),
            new ToolDef("Collision Picker", MaterialIcons.Colorize, "V"),
    };

    @Getter
    @Setter
    private boolean showGrid = true;

    public SceneViewToolbar(EditorContext context, EditorToolController toolController) {
        this.context = context;
        this.toolController = toolController;
    }

    public void render(float viewportWidth) {
        ImGui.pushTabStop(false);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 4, 4);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6, 4);

        // If active tool's panel closed, switch to select
        ensureValidToolSelection();

        renderToolButtons();

        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();

        renderVisibilityToggles();

        ImGui.popStyleVar(2);
        ImGui.popTabStop();
    }

    /**
     * Ensures the active tool is valid based on visible panels.
     * If a panel becomes hidden while using its tool, switches to Select.
     */
    private void ensureValidToolSelection() {
        ToolManager toolManager = context.getToolManager();
        EditorTool activeTool = toolManager.getActiveTool();
        if (activeTool == null) return;

        String toolName = activeTool.getName();
        boolean isTilemapTool = Arrays.stream(TILEMAP_TOOLS).anyMatch(t -> t.toolName.equals(toolName));
        boolean isCollisionTool = Arrays.stream(COLLISION_TOOLS).anyMatch(t -> t.toolName.equals(toolName));

        boolean tilesetVisible = tilesetPalettePanel != null && tilesetPalettePanel.isContentVisible();
        boolean collisionVisible = collisionPanel != null && collisionPanel.isContentVisible();

        // If using tilemap tool but palette is not visible, switch to select
        if (isTilemapTool && !tilesetVisible) {
            toolManager.setActiveTool("Select");
        }
        // If using collision tool but panel is not visible, switch to select
        if (isCollisionTool && !collisionVisible) {
            toolManager.setActiveTool("Select");
        }
    }

    private void renderToolButtons() {
        ToolManager toolManager = context.getToolManager();
        EditorTool activeTool = toolManager.getActiveTool();

        List<ToolDef> visibleTools = getVisibleTools();

        for (ToolDef def : visibleTools) {
            boolean isActive = activeTool != null && activeTool.getName().equals(def.toolName);

            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.6f, 1.0f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.4f, 0.7f, 1.0f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.5f, 0.9f, 1.0f);
            }

            if (ImGui.button(def.icon + "##" + def.toolName)) {
                toolManager.setActiveTool(def.toolName);
                showMessage(def.toolName);
            }

            if (isActive) {
                ImGui.popStyleColor(3);
            }

            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(def.toolName + " (" + def.shortcut + ")");
            }

            ImGui.sameLine();
        }
    }

    /**
     * Returns tools that should be visible based on which panels are visible.
     * Select tool is always visible. Tilemap/collision tools visible when their panels are visible.
     */
    private List<ToolDef> getVisibleTools() {
        List<ToolDef> tools = new ArrayList<>();

        // Selection tool is always available
        tools.add(SELECT_TOOL);

        // Add tilemap tools if tileset palette is visible
        if (tilesetPalettePanel != null && tilesetPalettePanel.isContentVisible()) {
            tools.addAll(Arrays.asList(TILEMAP_TOOLS));
        }

        // Add collision tools if collision panel is visible
        if (collisionPanel != null && collisionPanel.isContentVisible()) {
            tools.addAll(Arrays.asList(COLLISION_TOOLS));
        }

        return tools;
    }

    private void renderVisibilityToggles() {
        EditorScene scene = context.getCurrentScene();

        boolean gridActive = showGrid;
        if (gridActive) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.2f, 1.0f);
        }
        if (ImGui.button(MaterialIcons.GridOn + "##Grid")) {
            showGrid = !showGrid;
        }
        if (gridActive) {
            ImGui.popStyleColor();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Toggle Grid");
        }

        ImGui.sameLine();

        boolean collisionVisible = scene != null && scene.isCollisionVisible();
        if (collisionVisible) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.5f, 0.2f, 0.2f, 1.0f);
        }
        if (ImGui.button(MaterialIcons.BorderAll + "##Collision")) {
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

    private void showMessage(String message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }

    private record ToolDef(String toolName, String icon, String shortcut) {}
}
