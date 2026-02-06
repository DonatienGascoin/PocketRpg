package com.pocket.rpg.editor.panels;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal popup shown when a scene/prefab has stale component class references.
 * Prompts user to resave to update references.
 */
public class StaleReferencesPopup {

    private static final String POPUP_TITLE = "Stale Component References";

    private boolean showPopup = false;
    private List<String> resolutions = new ArrayList<>();
    private Runnable onSave;

    /**
     * Opens the popup with the list of stale resolutions and a save callback.
     */
    public void open(List<String> staleResolutions, Runnable onSave) {
        this.resolutions = new ArrayList<>(staleResolutions);
        this.onSave = onSave;
        this.showPopup = true;
    }

    /**
     * Renders the popup. Must be called from a context with a valid ImGui current window
     * (e.g., renderUIPreShortcuts, right after newFrame) — calling after all windows have
     * been End()'ed causes openPopup to silently fail.
     */
    public void render() {
        if (!showPopup) return;

        // Center popup on screen (matches compilation modal pattern)
        float centerX = ImGui.getIO().getDisplaySizeX() * 0.5f;
        float centerY = ImGui.getIO().getDisplaySizeY() * 0.5f;
        ImGui.setNextWindowPos(centerX, centerY, ImGuiCond.Appearing, 0.5f, 0.5f);

        ImGui.openPopup(POPUP_TITLE);

        if (ImGui.beginPopupModal(POPUP_TITLE, ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoMove)) {
            ImGui.text("This scene contains outdated component class paths.");
            ImGui.text("Components loaded correctly, but save to update references.");
            ImGui.spacing();

            // Show resolutions
            for (String resolution : resolutions) {
                String[] parts = resolution.split("\\|");
                if (parts.length == 2) {
                    ImGui.bulletText(parts[0]);
                    ImGui.text("  -> " + parts[1]);
                }
            }

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            // "Don't Save" button (red)
            ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.2f, 0.2f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.7f, 0.3f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.5f, 0.15f, 0.15f, 1.0f);
            if (ImGui.button("Don't Save", 120, 0)) {
                showPopup = false;
                ImGui.closeCurrentPopup();
            }
            ImGui.popStyleColor(3);

            ImGui.sameLine();

            // "Save" button (green)
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1.0f);
            if (ImGui.button("Save", 120, 0)) {
                if (onSave != null) {
                    onSave.run();
                }
                showPopup = false;
                ImGui.closeCurrentPopup();
            }
            ImGui.popStyleColor(3);

            ImGui.endPopup();
        }
    }
}
