package com.pocket.rpg.editor.utils;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.ImVec2;

public class ImGuiHelper {

    // ===========================================
    // Alignment
    // ===========================================

    public static void setCursorAlignment(float alignment, float size) {
        ImGuiStyle style = ImGui.getStyle();
        size += (style.getFramePaddingX() * 2f);
        float avail = ImGui.getContentRegionAvailX();

        float off = (avail - size) * alignment;
        if (off > 0f) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + off);
        }
    }

    public static void setCursorAlignment(float alignment, String... labels) {
        ImGuiStyle style = ImGui.getStyle();
        ImVec2 vec2 = new ImVec2();
        float size = 0f;
        for (String label : labels) {
            ImGui.calcTextSize(vec2, label);
            size += vec2.x + style.getFramePaddingX() * 2f;
        }
        size += style.getFramePaddingX();
        float avail = ImGui.getContentRegionAvailX();

        float off = (avail - size) * alignment;
        if (off > 0f) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + off);
        }
    }
}
