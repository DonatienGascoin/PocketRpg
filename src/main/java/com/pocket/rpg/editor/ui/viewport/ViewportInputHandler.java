package com.pocket.rpg.editor.ui.viewport;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.shortcut.EditorShortcuts;
import com.pocket.rpg.editor.shortcut.ShortcutContext;
import com.pocket.rpg.editor.shortcut.ShortcutRegistry;
import com.pocket.rpg.editor.tools.*;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import lombok.Getter;
import lombok.Setter;

/**
 * Handles viewport input for camera controls and tool routing.
 */
public class ViewportInputHandler {

    private final EditorCamera camera;
    private final EditorConfig config;

    @Setter
    @Getter
    private ToolManager toolManager;

    private boolean isDraggingCamera = false;

    public ViewportInputHandler(EditorCamera camera, EditorConfig config) {
        this.camera = camera;
        this.config = config;
    }

    /**
     * Handles all input when viewport is hovered or focused.
     */
    public void handleInput(boolean isHovered, boolean isFocused, 
                           float viewportX, float viewportY,
                           int hoveredTileX, int hoveredTileY) {
        handleCameraInput(isHovered, isFocused, viewportX, viewportY);
        handleToolInput(isHovered, hoveredTileX, hoveredTileY);
        handleKeyboardShortcuts(isFocused);
    }

    private void handleCameraInput(boolean isHovered, boolean isFocused, 
                                   float viewportX, float viewportY) {
        // Keyboard panning (only when focused)
        if (isFocused && !ImGui.getIO().getWantTextInput()) {
            float dt = ImGui.getIO().getDeltaTime();
            float moveX = 0, moveY = 0;

            // WASD via shortcut registry (respects modifier state, so Ctrl+S won't pan)
            ShortcutRegistry registry = ShortcutRegistry.getInstance();
            ShortcutContext context = ShortcutContext.builder()
                    .popupOpen(ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopup))
                    .textInputActive(ImGui.getIO().getWantTextInput())
                    .panelFocused(EditorShortcuts.PanelIds.SCENE_VIEW)
                    .build();
            if (registry.isActionHeld(EditorShortcuts.CAMERA_PAN_UP, context)) moveY = 1;
            if (registry.isActionHeld(EditorShortcuts.CAMERA_PAN_DOWN, context)) moveY = -1;
            if (registry.isActionHeld(EditorShortcuts.CAMERA_PAN_LEFT, context)) moveX = -1;
            if (registry.isActionHeld(EditorShortcuts.CAMERA_PAN_RIGHT, context)) moveX = 1;

            // Arrow keys (if ImGui nav isn't using them)
            if (!ImGui.getIO().getWantCaptureKeyboard()) {
                if (ImGui.isKeyDown(ImGuiKey.UpArrow)) moveY = 1;
                if (ImGui.isKeyDown(ImGuiKey.DownArrow)) moveY = -1;
                if (ImGui.isKeyDown(ImGuiKey.LeftArrow)) moveX = -1;
                if (ImGui.isKeyDown(ImGuiKey.RightArrow)) moveX = 1;
            }

            if (moveX != 0 || moveY != 0) {
                camera.updateKeyboardPan(moveX, moveY, dt);
            }
        }

        // Middle mouse drag for panning
        if (ImGui.isMouseClicked(ImGuiMouseButton.Middle) && isHovered) {
            isDraggingCamera = true;
        }
        if (ImGui.isMouseReleased(ImGuiMouseButton.Middle)) {
            isDraggingCamera = false;
        }

        if (isDraggingCamera) {
            ImVec2 delta = ImGui.getMouseDragDelta(ImGuiMouseButton.Middle);
            if (delta.x != 0 || delta.y != 0) {
                float worldDeltaX = -delta.x / (camera.getZoom() * config.getPixelsPerUnit());
                float worldDeltaY = delta.y / (camera.getZoom() * config.getPixelsPerUnit());
                camera.translate(worldDeltaX, worldDeltaY);
                ImGui.resetMouseDragDelta(ImGuiMouseButton.Middle);
            }
        }

        // Scroll wheel zoom
        if (isHovered) {
            ImVec2 mousePos = ImGui.getMousePos();
            float screenX = mousePos.x - viewportX;
            float screenY = mousePos.y - viewportY;

            float scroll = ImGui.getIO().getMouseWheel();
            if (scroll != 0) {
                camera.zoomToward(screenX, screenY, scroll);
            }
        }
    }

    private void handleToolInput(boolean isHovered, int hoveredTileX, int hoveredTileY) {
        if (toolManager == null || !isHovered || isDraggingCamera) return;

        // Don't handle tool input if popup is open
        if (ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId)) {
            return;
        }

        // Left mouse button
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            toolManager.handleMouseDown(hoveredTileX, hoveredTileY, 0);
        }
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            toolManager.handleMouseUp(hoveredTileX, hoveredTileY, 0);
        }

        // Right mouse button
        if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            toolManager.handleMouseDown(hoveredTileX, hoveredTileY, 1);
        }
        if (ImGui.isMouseReleased(ImGuiMouseButton.Right)) {
            toolManager.handleMouseUp(hoveredTileX, hoveredTileY, 1);
        }

        // Mouse move
        toolManager.handleMouseMove(hoveredTileX, hoveredTileY);
    }

    private void handleKeyboardShortcuts(boolean isFocused) {
        if (!isFocused) return;

        // Escape - clear tool selection
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            if (toolManager != null) {
                EditorTool tool = toolManager.getActiveTool();
                if (tool instanceof TileBrushTool brushTool) {
                    brushTool.setSelection(null);
                } else if (tool instanceof TileFillTool fillTool) {
                    fillTool.setSelection(null);
                } else if (tool instanceof TileRectangleTool rectTool) {
                    rectTool.setSelection(null);
                }
            }
        }
    }

    public boolean isDraggingCamera() {
        return isDraggingCamera;
    }
}
