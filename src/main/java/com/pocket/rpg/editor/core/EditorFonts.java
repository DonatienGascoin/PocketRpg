package com.pocket.rpg.editor.core;

import imgui.ImFont;

/**
 * Provides access to editor fonts at different sizes.
 * <p>
 * The default merged font (text + icons at 18px) is used automatically.
 * This class provides access to standalone icon fonts for specific use cases
 * like thumbnail fallbacks in the asset browser.
 */
public final class EditorFonts {

    private static ImFont iconFontTiny;    // 12px
    private static ImFont iconFontSmall;   // 24px
    private static ImFont iconFontMedium;  // 32px
    private static ImFont iconFontLarge;   // 48px

    private EditorFonts() {}

    /**
     * Called by ImGuiLayer during font initialization.
     */
    static void setIconFonts(ImFont tiny, ImFont small, ImFont medium, ImFont large) {
        iconFontTiny = tiny;
        iconFontSmall = small;
        iconFontMedium = medium;
        iconFontLarge = large;
    }

    /**
     * Returns the appropriate icon font for the given target size.
     * Use with ImGui.pushFont() / ImGui.popFont().
     *
     * @param targetSize desired icon size in pixels
     * @return the best matching icon font
     */
    public static ImFont getIconFont(float targetSize) {
        if (targetSize >= 40) {
            return iconFontLarge;
        } else if (targetSize >= 28) {
            return iconFontMedium;
        } else if (targetSize >= 20) {
            return iconFontSmall;
        }
        return iconFontTiny;
    }

    /**
     * Returns the tiny icon font (12px).
     */
    public static ImFont getIconFontTiny() {
        return iconFontTiny;
    }

    /**
     * Returns the small icon font (24px).
     */
    public static ImFont getIconFontSmall() {
        return iconFontSmall;
    }

    /**
     * Returns the medium icon font (32px).
     */
    public static ImFont getIconFontMedium() {
        return iconFontMedium;
    }

    /**
     * Returns the large icon font (48px).
     */
    public static ImFont getIconFontLarge() {
        return iconFontLarge;
    }
}
