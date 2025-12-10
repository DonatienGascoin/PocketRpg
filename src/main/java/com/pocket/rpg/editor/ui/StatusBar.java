package com.pocket.rpg.editor.ui;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import lombok.Setter;

/**
 * Bottom status bar for the Scene Editor.
 * Shows current tool, selection info, and helpful hints.
 */
public class StatusBar {

    @Setter
    private EditorScene currentScene;
    @Setter
    private EditorCamera camera;
    @Setter
    private String currentTool = "Select";
    @Setter
    private String statusMessage = "Ready";

    private float fps = 60.0f;
    private int frameCount = 0;
    private float fpsTimer = 0;

    /**
     * Renders the status bar at the bottom of the screen.
     */
    public void render(float viewportHeight) {
        float statusBarHeight = 24;
        
        // Update FPS counter
        updateFPS();

        ImGui.setNextWindowPos(0, viewportHeight - statusBarHeight);
        ImGui.setNextWindowSize(ImGui.getIO().getDisplaySizeX(), statusBarHeight);

        int flags = ImGuiWindowFlags.NoTitleBar |
                    ImGuiWindowFlags.NoResize |
                    ImGuiWindowFlags.NoMove |
                    ImGuiWindowFlags.NoScrollbar |
                    ImGuiWindowFlags.NoSavedSettings |
                    ImGuiWindowFlags.NoBringToFrontOnFocus |
                    ImGuiWindowFlags.NoNav;

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 8, 4);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowRounding, 0);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.WindowBg, 0.12f, 0.12f, 0.12f, 1.0f);

        if (ImGui.begin("##StatusBar", flags)) {
            // Left section: Tool and status
            ImGui.text("Tool: " + currentTool);
            ImGui.sameLine();
            ImGui.separator();
            ImGui.sameLine();
            ImGui.text(statusMessage);

            // Right section: Scene info and FPS
            float rightWidth = 350;
            ImGui.sameLine(ImGui.getWindowWidth() - rightWidth);

            // Scene object count
            if (currentScene != null) {
                ImGui.text("Objects: " + currentScene.getObjectCount());
                ImGui.sameLine();
                ImGui.separator();
                ImGui.sameLine();
            }

            // Camera info
            if (camera != null) {
                ImGui.text(String.format("Zoom: %.0f%%", camera.getZoom() * 100));
                ImGui.sameLine();
                ImGui.separator();
                ImGui.sameLine();
            }

            // FPS
            ImGui.text(String.format("%.0f FPS", fps));
        }
        ImGui.end();

        ImGui.popStyleColor();
        ImGui.popStyleVar(2);
    }

    private void updateFPS() {
        frameCount++;
        fpsTimer += ImGui.getIO().getDeltaTime();

        if (fpsTimer >= 0.5f) {
            fps = frameCount / fpsTimer;
            frameCount = 0;
            fpsTimer = 0;
        }
    }

    /**
     * Sets a temporary status message.
     */
    public void showMessage(String message) {
        this.statusMessage = message;
    }

    /**
     * Clears the status message.
     */
    public void clearMessage() {
        this.statusMessage = "Ready";
    }
}
