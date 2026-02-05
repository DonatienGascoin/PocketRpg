package com.pocket.rpg.editor.utils;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiStyle;

/**
 * Material Design-style indeterminate spinner widget for ImGui.
 * Translated from the C++ imgui_spinner implementation.
 */
public final class ImGuiSpinner {

    private ImGuiSpinner() {}

    // Fast-out-slow-in bezier control points (Material Design)
    private static final float P1X = 0.4f, P1Y = 0.0f;
    private static final float P2X = 0.2f, P2Y = 1.0f;

    private static final int NUM_DETENTS = 5;
    private static final int SKIP_DETENTS = 3;
    private static final float PERIOD = 5.0f;
    private static final int NUM_SEGMENTS = 24;

    /**
     * Draw a Material Design spinner at the current cursor position.
     *
     * @param label     ImGui ID (use ## prefix for hidden label)
     * @param radius    circle radius in pixels
     * @param thickness stroke thickness in pixels
     * @param color     ImU32 color value
     */
    public static void spinner(String label, float radius, int thickness, int color) {
        ImGuiStyle style = ImGui.getStyle();
        float posX = ImGui.getCursorScreenPosX();
        float posY = ImGui.getCursorScreenPosY();

        // Reserve space for the widget
        ImGui.dummy(radius * 2, (radius + style.getFramePaddingY()) * 2);

        ImDrawList drawList = ImGui.getWindowDrawList();

        float centerX = posX + radius;
        float centerY = posY + radius + thickness + style.getFramePaddingY();

        float startAngle = (float) (-Math.PI / 2.0); // Start at the top
        float t = (float) (ImGui.getTime() % PERIOD) / PERIOD;

        // Sawtooth: maps t into NUM_DETENTS sub-periods
        float tSaw = (NUM_DETENTS * t) % 1.0f;

        // Head tween: grows arc in first half of each sub-period
        float headT = tSaw > 0.5f ? 1.0f : bezierEase(tSaw / 0.5f);

        // Tail tween: shrinks arc in second half of each sub-period
        float tailT = tSaw < 0.5f ? 0.0f : bezierEase((tSaw - 0.5f) / 0.5f);

        // Step: discrete jumps at each detent
        float stepValue = (float) Math.floor(NUM_DETENTS * t);

        // Rotation: continuous rotation within detent period
        float rotationValue = (NUM_DETENTS * t) % 1.0f;

        float minArc = (float) (30.0 / 360.0 * 2.0 * Math.PI);
        float maxArc = (float) (270.0 / 360.0 * 2.0 * Math.PI);
        float stepOffset = (float) (SKIP_DETENTS * 2.0 * Math.PI / NUM_DETENTS);
        float rotationCompensation = (float) (((4.0 * Math.PI - stepOffset - maxArc) % (2.0 * Math.PI)));

        float aMin = startAngle + tailT * maxArc + rotationValue * rotationCompensation - stepValue * stepOffset;
        float aMax = aMin + (headT - tailT) * maxArc + minArc;

        drawList.pathClear();
        for (int i = 0; i < NUM_SEGMENTS; i++) {
            float a = aMin + ((float) i / (float) NUM_SEGMENTS) * (aMax - aMin);
            drawList.pathLineTo(
                    centerX + (float) Math.cos(a) * radius,
                    centerY + (float) Math.sin(a) * radius
            );
        }
        drawList.pathStroke(color, 0, thickness); // 0 = open path (not closed)
    }

    /**
     * Evaluate the fast-out-slow-in cubic bezier easing at parameter x.
     * Uses Newton-Raphson with bisection fallback.
     */
    private static float bezierEase(float x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;

        // Newton-Raphson: solve for t where bezierX(t) = x
        float t = x;
        for (int i = 0; i < 8; i++) {
            float dx = cubicBezierX(t) - x;
            if (Math.abs(dx) < 1e-6f) break;
            float dxdt = cubicBezierDX(t);
            if (Math.abs(dxdt) < 1e-6f) break;
            t -= dx / dxdt;
        }

        t = Math.clamp(t, 0.0f, 1.0f);

        // Bisection fallback if Newton diverged
        if (Math.abs(cubicBezierX(t) - x) > 1e-4f) {
            float lo = 0, hi = 1;
            for (int i = 0; i < 20; i++) {
                t = (lo + hi) / 2;
                if (cubicBezierX(t) < x) lo = t; else hi = t;
            }
        }

        return cubicBezierY(t);
    }

    // Cubic bezier x(t) with P0=(0,0), P3=(1,1)
    private static float cubicBezierX(float t) {
        float mt = 1 - t;
        return 3 * mt * mt * t * P1X + 3 * mt * t * t * P2X + t * t * t;
    }

    // Derivative dx/dt for Newton-Raphson
    private static float cubicBezierDX(float t) {
        float mt = 1 - t;
        return 3 * mt * mt * P1X + 6 * mt * t * (P2X - P1X) + 3 * t * t * (1 - P2X);
    }

    // Cubic bezier y(t) with P0=(0,0), P3=(1,1)
    private static float cubicBezierY(float t) {
        float mt = 1 - t;
        return 3 * mt * mt * t * P1Y + 3 * mt * t * t * P2Y + t * t * t;
    }
}
