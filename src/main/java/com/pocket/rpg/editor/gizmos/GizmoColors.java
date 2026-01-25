package com.pocket.rpg.editor.gizmos;

import imgui.ImGui;

/**
 * Standard colors for gizmo rendering.
 * All colors are pre-converted to ImGui format for performance.
 */
public final class GizmoColors {

    // ========================================================================
    // GENERAL
    // ========================================================================

    /**
     * Default gizmo color (green).
     */
    public static final int DEFAULT = ImGui.colorConvertFloat4ToU32(0.0f, 1.0f, 0.0f, 0.8f);

    /**
     * White color for neutral elements.
     */
    public static final int WHITE = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 0.8f);

    // ========================================================================
    // TRANSFORM / POSITION
    // ========================================================================

    /**
     * Position/origin indicator (red).
     */
    public static final int POSITION = ImGui.colorConvertFloat4ToU32(1.0f, 0.4f, 0.4f, 1.0f);

    /**
     * X-axis color (red).
     */
    public static final int AXIS_X = ImGui.colorConvertFloat4ToU32(1.0f, 0.2f, 0.2f, 1.0f);

    /**
     * Y-axis color (green).
     */
    public static final int AXIS_Y = ImGui.colorConvertFloat4ToU32(0.2f, 1.0f, 0.2f, 1.0f);

    /**
     * X-axis hover color (lighter red).
     */
    public static final int AXIS_X_HOVER = ImGui.colorConvertFloat4ToU32(1.0f, 0.5f, 0.5f, 1.0f);

    /**
     * Y-axis hover color (lighter green).
     */
    public static final int AXIS_Y_HOVER = ImGui.colorConvertFloat4ToU32(0.5f, 1.0f, 0.5f, 1.0f);

    /**
     * Active/selected gizmo element (yellow).
     */
    public static final int ACTIVE = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 0.0f, 1.0f);

    /**
     * Active hover (lighter yellow).
     */
    public static final int ACTIVE_HOVER = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 0.5f, 1.0f);

    /**
     * Free movement center square (blue).
     */
    public static final int FREE_MOVE = ImGui.colorConvertFloat4ToU32(0.3f, 0.6f, 1.0f, 0.8f);

    /**
     * Free movement hover (lighter blue).
     */
    public static final int FREE_MOVE_HOVER = ImGui.colorConvertFloat4ToU32(0.5f, 0.8f, 1.0f, 1.0f);

    /**
     * Rotation ring color (cyan).
     */
    public static final int ROTATION = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 1.0f, 0.9f);

    /**
     * Rotation ring hover (lighter cyan).
     */
    public static final int ROTATION_HOVER = ImGui.colorConvertFloat4ToU32(0.5f, 0.9f, 1.0f, 1.0f);

    /**
     * Scale handle color (orange).
     */
    public static final int SCALE = ImGui.colorConvertFloat4ToU32(1.0f, 0.6f, 0.2f, 1.0f);

    /**
     * Scale handle hover (lighter orange).
     */
    public static final int SCALE_HOVER = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.4f, 1.0f);

    // ========================================================================
    // SPRITE / RENDERING
    // ========================================================================

    /**
     * Pivot point indicator (blue).
     */
    public static final int PIVOT = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 1.0f, 1.0f);

    /**
     * Bounding box (semi-transparent green).
     */
    public static final int BOUNDS = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 0.6f);

    /**
     * Sprite origin/center (yellow).
     */
    public static final int ORIGIN = ImGui.colorConvertFloat4ToU32(1.0f, 0.9f, 0.2f, 1.0f);

    // ========================================================================
    // GRID / TILES
    // ========================================================================

    /**
     * Tile highlight (semi-transparent cyan).
     */
    public static final int TILE = ImGui.colorConvertFloat4ToU32(0.4f, 0.8f, 1.0f, 0.4f);

    /**
     * Tile border (cyan).
     */
    public static final int TILE_BORDER = ImGui.colorConvertFloat4ToU32(0.4f, 0.8f, 1.0f, 0.8f);

    /**
     * Grid movement direction indicator (orange).
     */
    public static final int DIRECTION = ImGui.colorConvertFloat4ToU32(1.0f, 0.6f, 0.2f, 0.8f);

    // ========================================================================
    // AUDIO
    // ========================================================================

    /**
     * Audio zone/radius (purple).
     */
    public static final int AUDIO_ZONE = ImGui.colorConvertFloat4ToU32(0.8f, 0.4f, 1.0f, 0.5f);

    /**
     * Audio source indicator (magenta).
     */
    public static final int AUDIO_SOURCE = ImGui.colorConvertFloat4ToU32(1.0f, 0.2f, 0.8f, 0.8f);

    // ========================================================================
    // COLLISION
    // ========================================================================

    /**
     * Collision shape (semi-transparent red).
     */
    public static final int COLLISION = ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.3f, 0.4f);

    /**
     * Collision shape border (red).
     */
    public static final int COLLISION_BORDER = ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.3f, 0.8f);

    // ========================================================================
    // TRIGGERS
    // ========================================================================

    /**
     * Trigger zone (semi-transparent yellow).
     */
    public static final int TRIGGER = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.2f, 0.3f);

    /**
     * Trigger zone border (yellow).
     */
    public static final int TRIGGER_BORDER = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.2f, 0.8f);

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Creates a color from RGBA floats (0-1 range).
     */
    public static int fromRGBA(float r, float g, float b, float a) {
        return ImGui.colorConvertFloat4ToU32(r, g, b, a);
    }

    /**
     * Creates a color with modified alpha.
     * ImGui colors are packed as ABGR (alpha in high byte).
     */
    public static int withAlpha(int color, float alpha) {
        // Extract RGB components (ImGui uses ABGR format)
        int r = color & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = (color >> 16) & 0xFF;
        int a = (int) (alpha * 255) & 0xFF;
        return r | (g << 8) | (b << 16) | (a << 24);
    }

    private GizmoColors() {
        // Prevent instantiation
    }
}
