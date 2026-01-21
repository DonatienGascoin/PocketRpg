package com.pocket.rpg.editor.ui;

import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.EditorModeManager;
import com.pocket.rpg.editor.EditorToolController;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.shortcut.EditorShortcuts;
import com.pocket.rpg.editor.shortcut.ShortcutRegistry;
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
 * Uses EditorModeManager.switchTo() which triggers listeners for hierarchy sync.
 */
public class SceneViewToolbar {

    private final EditorContext context;
    private final EditorToolController toolController;

    @Setter
    private Consumer<String> messageCallback;

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

    private static final ToolDef[] ENTITY_TOOLS = {
            new ToolDef("Select", MaterialIcons.NearMe, "V"),
            new ToolDef("Place Entity", MaterialIcons.AddBox, "P"),
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

        renderToolButtons();

        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();

        renderModeDropdown();

        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();

        renderVisibilityToggles();

        ImGui.popStyleVar(2);
        ImGui.popTabStop();
    }

    private void renderToolButtons() {
        EditorModeManager modeManager = context.getModeManager();
        ToolManager toolManager = context.getToolManager();
        EditorTool activeTool = toolManager.getActiveTool();

        ToolDef[] tools = getToolsForMode(modeManager.getCurrentMode());

        for (ToolDef def : tools) {
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

    private ToolDef[] getToolsForMode(EditorModeManager.Mode mode) {
        return switch (mode) {
            case TILEMAP -> TILEMAP_TOOLS;
            case COLLISION -> COLLISION_TOOLS;
            case ENTITY -> ENTITY_TOOLS;
        };
    }

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
            String entityKey = ShortcutRegistry.getInstance().getBindingDisplay(EditorShortcuts.MODE_ENTITY);
            String tilemapKey = ShortcutRegistry.getInstance().getBindingDisplay(EditorShortcuts.MODE_TILEMAP);
            String collisionKey = ShortcutRegistry.getInstance().getBindingDisplay(EditorShortcuts.MODE_COLLISION);
            ImGui.setTooltip("Editor Mode (" + entityKey + "/" + tilemapKey + "/" + collisionKey + ")");
        }
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

    /**
     * Switches mode via EditorModeManager (triggers onModeChanged listeners).
     */
    private void switchToMode(EditorModeManager.Mode mode) {
        EditorModeManager modeManager = context.getModeManager();
        ToolManager toolManager = context.getToolManager();

        // switchTo() triggers listeners which sync HierarchyPanel
        modeManager.switchTo(mode);

        // Set default tool
        switch (mode) {
            case TILEMAP -> toolManager.setActiveTool("Brush");
            case COLLISION -> {
                toolManager.setActiveTool("Collision Brush");
                toolController.syncCollisionZLevels();
            }
            case ENTITY -> toolManager.setActiveTool("Select");
        }

        showMessage("Switched to " + mode.getDisplayName() + " Mode");
    }

    private void showMessage(String message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }

    private record ToolDef(String toolName, String icon, String shortcut) {}
}
