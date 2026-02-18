package com.pocket.rpg.editor.core;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;

/**
 * Centralized editor color system providing semantic colors and theme-derived colors.
 * <p>
 * <b>Semantic colors</b> are set per-theme via palette methods (e.g. {@link #applyDarkPalette()}).
 * <b>Theme-derived colors</b> are updated whenever the theme changes via {@link #onThemeChanged()}.
 * <p>
 * Convenience methods handle common push/pop patterns to reduce boilerplate and prevent
 * push/pop count mismatches.
 */
public final class EditorColors {

    private EditorColors() {}

    // ========================================================================
    // SEMANTIC COLORS (updated per-theme via palette methods)
    // ========================================================================

    // -- Success (green) --
    public static final float[] SUCCESS         = new float[4];
    public static final float[] SUCCESS_HOVERED = new float[4];
    public static final float[] SUCCESS_ACTIVE  = new float[4];

    // -- Danger (red) --
    public static final float[] DANGER          = new float[4];
    public static final float[] DANGER_HOVERED  = new float[4];
    public static final float[] DANGER_ACTIVE   = new float[4];

    // -- Warning (amber) --
    public static final float[] WARNING         = new float[4];
    public static final float[] WARNING_HOVERED = new float[4];
    public static final float[] WARNING_ACTIVE  = new float[4];

    // -- Info (blue) --
    public static final float[] INFO            = new float[4];
    public static final float[] INFO_HOVERED    = new float[4];
    public static final float[] INFO_ACTIVE     = new float[4];

    // -- Prefab/Override --
    public static final float[] PREFAB          = new float[4];
    public static final float[] OVERRIDE        = new float[4];
    public static final float[] DIRTY           = new float[4];

    // -- Disabled --
    public static final float[] DISABLED_TEXT   = new float[4];

    // -- Log levels --
    public static final float[] LOG_TRACE       = new float[4];
    public static final float[] LOG_DEBUG       = new float[4];
    public static final float[] LOG_INFO        = new float[4];
    public static final float[] LOG_WARN        = new float[4];
    public static final float[] LOG_ERROR       = new float[4];

    // -- Accent (field editor red buttons) --
    public static final float[] ACCENT          = new float[4];
    public static final float[] ACCENT_HOVERED  = new float[4];
    public static final float[] ACCENT_ACTIVE   = new float[4];

    // Initialize with dark palette as safe default
    static {
        applyDarkPalette();
    }

    // ========================================================================
    // THEME-DERIVED COLORS (updated by onThemeChanged)
    // ========================================================================

    public static final float[] themeButton        = new float[4];
    public static final float[] themeButtonHovered = new float[4];
    public static final float[] themeButtonActive  = new float[4];
    public static final float[] themeHeader        = new float[4];
    public static final float[] themeHeaderHovered = new float[4];
    public static final float[] themeHeaderActive  = new float[4];
    public static final float[] themeFrameBg       = new float[4];
    public static final float[] themeFrameBgHovered = new float[4];
    public static final float[] themeFrameBgActive = new float[4];
    public static final float[] themeText          = new float[4];
    public static final float[] themeTextDisabled  = new float[4];
    public static final float[] themeWindowBg      = new float[4];
    public static final float[] themeBorder        = new float[4];
    public static final float[] themeCheckMark     = new float[4];
    public static final float[] themeTabActive     = new float[4];

    // ========================================================================
    // THEME PALETTES — Semantic colors tuned for each theme
    // ========================================================================

    /** Dark theme palette: neutral green/red/amber/blue on dark grey backgrounds. */
    public static void applyDarkPalette() {
        set(SUCCESS,         0.31f, 0.79f, 0.41f, 1.0f);  // #4EC969
        set(SUCCESS_HOVERED, 0.37f, 0.85f, 0.48f, 1.0f);  // #5ED97A
        set(SUCCESS_ACTIVE,  0.24f, 0.73f, 0.35f, 1.0f);  // #3EB959

        set(DANGER,          0.88f, 0.33f, 0.33f, 1.0f);  // #E05555
        set(DANGER_HOVERED,  0.94f, 0.40f, 0.40f, 1.0f);  // #F06565
        set(DANGER_ACTIVE,   0.82f, 0.27f, 0.27f, 1.0f);  // #D04545

        set(WARNING,         0.88f, 0.69f, 0.31f, 1.0f);  // #E0B050
        set(WARNING_HOVERED, 0.94f, 0.75f, 0.38f, 1.0f);  // #F0C060
        set(WARNING_ACTIVE,  0.82f, 0.63f, 0.25f, 1.0f);  // #D0A040

        set(INFO,            0.33f, 0.67f, 0.87f, 1.0f);  // #55AADD
        set(INFO_HOVERED,    0.40f, 0.73f, 0.93f, 1.0f);  // #65BBED
        set(INFO_ACTIVE,     0.27f, 0.63f, 0.80f, 1.0f);  // #45A0CD

        set(PREFAB,          0.33f, 0.80f, 0.93f, 1.0f);  // #55CCEE (cyan)
        set(OVERRIDE,        0.88f, 0.80f, 0.20f, 1.0f);  // #E0CC33 (amber)
        set(DIRTY,           0.88f, 0.69f, 0.31f, 1.0f);  // #E0B050

        set(DISABLED_TEXT,   0.50f, 0.50f, 0.50f, 1.0f);  // #808080

        set(LOG_TRACE,       0.53f, 0.53f, 0.53f, 1.0f);  // #888888
        set(LOG_DEBUG,       0.67f, 0.67f, 0.67f, 1.0f);  // #AAAAAA
        set(LOG_INFO,        0.87f, 0.87f, 0.87f, 1.0f);  // #DDDDDD
        set(LOG_WARN,        0.88f, 0.69f, 0.31f, 1.0f);  // #E0B050
        set(LOG_ERROR,       0.88f, 0.33f, 0.33f, 1.0f);  // #E05555

        set(ACCENT,          0.88f, 0.20f, 0.20f, 1.0f);  // #E03333
        set(ACCENT_HOVERED,  0.94f, 0.27f, 0.27f, 1.0f);  // #F04444
        set(ACCENT_ACTIVE,   0.82f, 0.13f, 0.13f, 1.0f);  // #D02222
    }

    /** Nord Aurora palette: soft pastel tones from the Nord colour scheme. */
    public static void applyNordAuroraPalette() {
        // Aurora Green #A3BE8C
        set(SUCCESS,         0.64f, 0.75f, 0.55f, 1.0f);
        set(SUCCESS_HOVERED, 0.70f, 0.81f, 0.62f, 1.0f);
        set(SUCCESS_ACTIVE,  0.57f, 0.68f, 0.48f, 1.0f);

        // Aurora Red #BF616A
        set(DANGER,          0.75f, 0.38f, 0.42f, 1.0f);
        set(DANGER_HOVERED,  0.82f, 0.45f, 0.49f, 1.0f);
        set(DANGER_ACTIVE,   0.68f, 0.32f, 0.36f, 1.0f);

        // Aurora Yellow #EBCB8B
        set(WARNING,         0.92f, 0.80f, 0.55f, 1.0f);
        set(WARNING_HOVERED, 0.96f, 0.85f, 0.62f, 1.0f);
        set(WARNING_ACTIVE,  0.86f, 0.74f, 0.48f, 1.0f);

        // Frost #81A1C1
        set(INFO,            0.51f, 0.63f, 0.76f, 1.0f);
        set(INFO_HOVERED,    0.58f, 0.70f, 0.82f, 1.0f);
        set(INFO_ACTIVE,     0.44f, 0.57f, 0.70f, 1.0f);

        set(PREFAB,          0.53f, 0.75f, 0.82f, 1.0f);  // Frost #88C0D0
        set(OVERRIDE,        0.92f, 0.80f, 0.55f, 1.0f);  // Aurora Yellow
        set(DIRTY,           0.82f, 0.53f, 0.44f, 1.0f);  // Aurora Orange #D08770

        set(DISABLED_TEXT,   0.43f, 0.46f, 0.52f, 1.0f);  // muted polar

        set(LOG_TRACE,       0.30f, 0.34f, 0.42f, 1.0f);  // Polar Night #4C566A
        set(LOG_DEBUG,       0.53f, 0.57f, 0.63f, 1.0f);  // muted snow
        set(LOG_INFO,        0.85f, 0.87f, 0.91f, 1.0f);  // Snow Storm #D8DEE9
        set(LOG_WARN,        0.92f, 0.80f, 0.55f, 1.0f);  // Aurora Yellow
        set(LOG_ERROR,       0.75f, 0.38f, 0.42f, 1.0f);  // Aurora Red

        // Aurora Red for accent
        set(ACCENT,          0.75f, 0.38f, 0.42f, 1.0f);
        set(ACCENT_HOVERED,  0.82f, 0.45f, 0.49f, 1.0f);
        set(ACCENT_ACTIVE,   0.68f, 0.32f, 0.36f, 1.0f);
    }

    /** Catppuccin Mocha palette: warm pastels from the Catppuccin colour scheme. */
    public static void applyCatppuccinMochaPalette() {
        // Green #A6E3A1
        set(SUCCESS,         0.65f, 0.89f, 0.63f, 1.0f);
        set(SUCCESS_HOVERED, 0.72f, 0.93f, 0.70f, 1.0f);
        set(SUCCESS_ACTIVE,  0.58f, 0.83f, 0.56f, 1.0f);

        // Red #F38BA8
        set(DANGER,          0.95f, 0.55f, 0.66f, 1.0f);
        set(DANGER_HOVERED,  0.98f, 0.62f, 0.72f, 1.0f);
        set(DANGER_ACTIVE,   0.88f, 0.48f, 0.59f, 1.0f);

        // Yellow #F9E2AF
        set(WARNING,         0.98f, 0.89f, 0.69f, 1.0f);
        set(WARNING_HOVERED, 1.00f, 0.93f, 0.76f, 1.0f);
        set(WARNING_ACTIVE,  0.92f, 0.83f, 0.62f, 1.0f);

        // Blue #89B4FA
        set(INFO,            0.54f, 0.71f, 0.98f, 1.0f);
        set(INFO_HOVERED,    0.61f, 0.77f, 1.00f, 1.0f);
        set(INFO_ACTIVE,     0.47f, 0.64f, 0.92f, 1.0f);

        set(PREFAB,          0.45f, 0.78f, 0.93f, 1.0f);  // Sapphire #74C7EC
        set(OVERRIDE,        0.98f, 0.89f, 0.69f, 1.0f);  // Yellow
        set(DIRTY,           0.98f, 0.70f, 0.53f, 1.0f);  // Peach #FAB387

        set(DISABLED_TEXT,   0.42f, 0.44f, 0.53f, 1.0f);  // Overlay0 #6C7086

        set(LOG_TRACE,       0.35f, 0.36f, 0.44f, 1.0f);  // Surface2 #585B70
        set(LOG_DEBUG,       0.50f, 0.52f, 0.61f, 1.0f);  // Overlay1 #7F849C
        set(LOG_INFO,        0.80f, 0.84f, 0.96f, 1.0f);  // Text #CDD6F4
        set(LOG_WARN,        0.98f, 0.89f, 0.69f, 1.0f);  // Yellow
        set(LOG_ERROR,       0.95f, 0.55f, 0.66f, 1.0f);  // Red

        // Red #F38BA8 for accent
        set(ACCENT,          0.95f, 0.55f, 0.66f, 1.0f);
        set(ACCENT_HOVERED,  0.98f, 0.62f, 0.72f, 1.0f);
        set(ACCENT_ACTIVE,   0.88f, 0.48f, 0.59f, 1.0f);
    }

    /** Dark Catppuccin palette: Catppuccin Mocha accents on neutral dark backgrounds. */
    public static void applyDarkCatppuccinPalette() {
        // Same semantic colors as Catppuccin Mocha — the accents match,
        // only the ImGui theme backgrounds differ.
        applyCatppuccinMochaPalette();
    }

    /** Island Dark palette: JetBrains-style colours for the floating panel theme. */
    public static void applyIslandDarkPalette() {
        // Green #6AAB73
        set(SUCCESS,         0.42f, 0.67f, 0.45f, 1.0f);
        set(SUCCESS_HOVERED, 0.49f, 0.74f, 0.52f, 1.0f);
        set(SUCCESS_ACTIVE,  0.35f, 0.60f, 0.38f, 1.0f);

        // Red #F75464
        set(DANGER,          0.97f, 0.33f, 0.39f, 1.0f);
        set(DANGER_HOVERED,  1.00f, 0.40f, 0.46f, 1.0f);
        set(DANGER_ACTIVE,   0.90f, 0.27f, 0.33f, 1.0f);

        // Warning #E8A33E
        set(WARNING,         0.91f, 0.64f, 0.24f, 1.0f);
        set(WARNING_HOVERED, 0.96f, 0.71f, 0.31f, 1.0f);
        set(WARNING_ACTIVE,  0.85f, 0.58f, 0.18f, 1.0f);

        // Blue #548AF7
        set(INFO,            0.33f, 0.54f, 0.97f, 1.0f);
        set(INFO_HOVERED,    0.40f, 0.61f, 1.00f, 1.0f);
        set(INFO_ACTIVE,     0.27f, 0.48f, 0.90f, 1.0f);

        set(PREFAB,          0.21f, 0.57f, 0.77f, 1.0f);  // Teal #3592C4
        set(OVERRIDE,        0.91f, 0.64f, 0.24f, 1.0f);  // Warning
        set(DIRTY,           0.91f, 0.64f, 0.24f, 1.0f);  // Warning

        set(DISABLED_TEXT,   0.48f, 0.49f, 0.52f, 1.0f);  // #7A7E85

        set(LOG_TRACE,       0.36f, 0.37f, 0.40f, 1.0f);  // #5D5F65
        set(LOG_DEBUG,       0.48f, 0.49f, 0.52f, 1.0f);  // #7A7E85
        set(LOG_INFO,        0.74f, 0.75f, 0.77f, 1.0f);  // #BCBEC4
        set(LOG_WARN,        0.91f, 0.64f, 0.24f, 1.0f);  // Warning
        set(LOG_ERROR,       0.97f, 0.33f, 0.39f, 1.0f);  // Red

        // Red #F75464 for accent
        set(ACCENT,          0.97f, 0.33f, 0.39f, 1.0f);
        set(ACCENT_HOVERED,  1.00f, 0.40f, 0.46f, 1.0f);
        set(ACCENT_ACTIVE,   0.90f, 0.27f, 0.33f, 1.0f);
    }

    /** Dark Vivid palette: higher saturation variants on neutral dark backgrounds. */
    public static void applyDarkVividPalette() {
        // Bright green
        set(SUCCESS,         0.25f, 0.85f, 0.40f, 1.0f);
        set(SUCCESS_HOVERED, 0.32f, 0.92f, 0.48f, 1.0f);
        set(SUCCESS_ACTIVE,  0.20f, 0.78f, 0.34f, 1.0f);

        // Vivid red
        set(DANGER,          0.95f, 0.28f, 0.28f, 1.0f);
        set(DANGER_HOVERED,  1.00f, 0.35f, 0.35f, 1.0f);
        set(DANGER_ACTIVE,   0.88f, 0.22f, 0.22f, 1.0f);

        // Punchy amber
        set(WARNING,         0.95f, 0.72f, 0.25f, 1.0f);
        set(WARNING_HOVERED, 1.00f, 0.78f, 0.32f, 1.0f);
        set(WARNING_ACTIVE,  0.88f, 0.66f, 0.18f, 1.0f);

        // Bright blue
        set(INFO,            0.28f, 0.70f, 0.95f, 1.0f);
        set(INFO_HOVERED,    0.35f, 0.77f, 1.00f, 1.0f);
        set(INFO_ACTIVE,     0.22f, 0.64f, 0.88f, 1.0f);

        set(PREFAB,          0.28f, 0.85f, 0.95f, 1.0f);  // bright cyan
        set(OVERRIDE,        0.95f, 0.85f, 0.15f, 1.0f);  // vivid amber
        set(DIRTY,           0.95f, 0.72f, 0.25f, 1.0f);  // same as warning

        set(DISABLED_TEXT,   0.50f, 0.50f, 0.50f, 1.0f);  // #808080

        set(LOG_TRACE,       0.50f, 0.50f, 0.50f, 1.0f);
        set(LOG_DEBUG,       0.65f, 0.65f, 0.65f, 1.0f);
        set(LOG_INFO,        0.90f, 0.90f, 0.90f, 1.0f);
        set(LOG_WARN,        0.95f, 0.72f, 0.25f, 1.0f);
        set(LOG_ERROR,       0.95f, 0.28f, 0.28f, 1.0f);

        // Vivid red accent
        set(ACCENT,          0.95f, 0.15f, 0.15f, 1.0f);
        set(ACCENT_HOVERED,  1.00f, 0.22f, 0.22f, 1.0f);
        set(ACCENT_ACTIVE,   0.88f, 0.10f, 0.10f, 1.0f);
    }

    // ========================================================================
    // CONVENIENCE METHODS — Button color push/pop
    // ========================================================================

    /** Pushes green success button colors (Button + ButtonHovered + ButtonActive = 3 colors). */
    public static void pushSuccessButton() {
        pushButtonColors(SUCCESS, SUCCESS_HOVERED, SUCCESS_ACTIVE);
    }

    /** Pushes red danger button colors (3 colors). */
    public static void pushDangerButton() {
        pushButtonColors(DANGER, DANGER_HOVERED, DANGER_ACTIVE);
    }

    /** Pushes amber warning button colors (3 colors). */
    public static void pushWarningButton() {
        pushButtonColors(WARNING, WARNING_HOVERED, WARNING_ACTIVE);
    }

    /**
     * Pushes amber warning button colors + dark text for readability (4 colors).
     * Pop with {@link #popWarningButtonWithText()}.
     */
    public static void pushWarningButtonWithText() {
        pushButtonColors(WARNING, WARNING_HOVERED, WARNING_ACTIVE);
        ImGui.pushStyleColor(ImGuiCol.Text, 0.1f, 0.1f, 0.1f, 1.0f);
    }

    /** Pops the 4 style colors pushed by {@link #pushWarningButtonWithText()}. */
    public static void popWarningButtonWithText() {
        ImGui.popStyleColor(4);
    }

    /** Pushes blue info button colors (3 colors). */
    public static void pushInfoButton() {
        pushButtonColors(INFO, INFO_HOVERED, INFO_ACTIVE);
    }

    /** Pushes red accent button colors for field editors (3 colors). */
    public static void pushAccentButton() {
        pushButtonColors(ACCENT, ACCENT_HOVERED, ACCENT_ACTIVE);
    }

    /** Pops the 3 button style colors pushed by any push*Button() method. */
    public static void popButtonColors() {
        ImGui.popStyleColor(3);
    }

    /** Pushes Button + ButtonHovered + ButtonActive from the given color arrays (3 colors). */
    public static void pushButtonColors(float[] rest, float[] hovered, float[] active) {
        ImGui.pushStyleColor(ImGuiCol.Button, rest[0], rest[1], rest[2], rest[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hovered[0], hovered[1], hovered[2], hovered[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, active[0], active[1], active[2], active[3]);
    }

    // ========================================================================
    // CONVERSION UTILITIES
    // ========================================================================

    /** Converts a float[4] RGBA color to a packed U32 integer. */
    public static int toU32(float[] color) {
        return ImGui.colorConvertFloat4ToU32(color[0], color[1], color[2], color[3]);
    }

    /** Converts RGBA float components to a packed U32 integer. */
    public static int toU32(float r, float g, float b, float a) {
        return ImGui.colorConvertFloat4ToU32(r, g, b, a);
    }

    /** Returns a new float[4] color with the alpha replaced. */
    public static float[] withAlpha(float[] color, float alpha) {
        return new float[]{color[0], color[1], color[2], alpha};
    }

    // ========================================================================
    // TEXT HELPERS
    // ========================================================================

    /** Shorthand for {@code ImGui.textColored(color, text)}. */
    public static void textColored(float[] color, String text) {
        ImGui.textColored(color[0], color[1], color[2], color[3], text);
    }

    // ========================================================================
    // THEME SNAPSHOT
    // ========================================================================

    /**
     * Reads the current ImGui style colors into the theme-derived fields.
     * Must be called after every theme application (from ImGuiLayer).
     */
    public static void onThemeChanged() {
        ImGuiStyle style = ImGui.getStyle();
        readColor(style, ImGuiCol.Button, themeButton);
        readColor(style, ImGuiCol.ButtonHovered, themeButtonHovered);
        readColor(style, ImGuiCol.ButtonActive, themeButtonActive);
        readColor(style, ImGuiCol.Header, themeHeader);
        readColor(style, ImGuiCol.HeaderHovered, themeHeaderHovered);
        readColor(style, ImGuiCol.HeaderActive, themeHeaderActive);
        readColor(style, ImGuiCol.FrameBg, themeFrameBg);
        readColor(style, ImGuiCol.FrameBgHovered, themeFrameBgHovered);
        readColor(style, ImGuiCol.FrameBgActive, themeFrameBgActive);
        readColor(style, ImGuiCol.Text, themeText);
        readColor(style, ImGuiCol.TextDisabled, themeTextDisabled);
        readColor(style, ImGuiCol.WindowBg, themeWindowBg);
        readColor(style, ImGuiCol.Border, themeBorder);
        readColor(style, ImGuiCol.CheckMark, themeCheckMark);
        readColor(style, ImGuiCol.TabActive, themeTabActive);
    }

    private static void readColor(ImGuiStyle style, int colorIdx, float[] out) {
        ImVec4 color = style.getColor(colorIdx);
        out[0] = color.x;
        out[1] = color.y;
        out[2] = color.z;
        out[3] = color.w;
    }

    private static void set(float[] arr, float r, float g, float b, float a) {
        arr[0] = r;
        arr[1] = g;
        arr[2] = b;
        arr[3] = a;
    }
}
