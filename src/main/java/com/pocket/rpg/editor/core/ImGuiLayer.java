package com.pocket.rpg.editor.core;

import imgui.*;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * ImGui integration layer for the Scene Editor.
 * Handles ImGui initialization, frame management, and rendering.
 * <p>
 * Compatible with imgui-java 1.90.0+
 */
public class ImGuiLayer {

    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    private boolean initialized = false;

    /**
     * Initializes ImGui with the given GLFW window.
     *
     * @param windowHandle     GLFW window handle
     * @param installCallbacks Whether to install GLFW callbacks
     */
    public void init(long windowHandle, boolean installCallbacks) {
        System.out.println("Initializing ImGui...");

        // Create ImGui context
        ImGui.createContext();

        // Configure ImGui
        ImGuiIO io = ImGui.getIO();

        // Enable docking
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);

        // Enable keyboard navigation
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);

        io.setIniFilename("editor/editor_layout.ini");

        initFonts(io, windowHandle);

        // Apply dark theme with custom colors
        applyDarkTheme();

        // Initialize GLFW backend
        imGuiGlfw.init(windowHandle, installCallbacks);

        // Initialize OpenGL3 backend
        // Use GLSL 3.30 for OpenGL 3.3 Core Profile
        imGuiGl3.init("#version 330 core");

        initialized = true;
        System.out.println("ImGui initialized successfully");
    }

    private void initFonts(final ImGuiIO io, long windowHandle) {
        // 1. Force Hinting for sharpness
        io.getFonts().setFreeTypeRenderer(true);
        io.setFontGlobalScale(1.0f); // Ensure this is exactly 1.0

        int[] fbW = new int[1];
        int[] fbH = new int[1];
        int[] winW = new int[1];
        int[] winH = new int[1];

        GLFW.glfwGetFramebufferSize(windowHandle, fbW, fbH);
        GLFW.glfwGetWindowSize(windowHandle, winW, winH);

        float dpiScale = (float) fbW[0] / winW[0];

        // This enables FreeType font renderer, which is disabled by default.
        io.getFonts().setFreeTypeRenderer(true);

        // Load icon font data once for reuse
        byte[] iconFontData = loadFromResources("editor/fonts/MaterialSymbolsSharp-Regular.ttf");

        // Build glyph ranges for icon fonts
        final ImFontGlyphRangesBuilder rangesBuilder = new ImFontGlyphRangesBuilder();
        rangesBuilder.addRanges(MaterialIcons._IconRange);
        final short[] glyphRanges = rangesBuilder.buildRanges();

        // Font config for main font
        final ImFontConfig fontConfig = new ImFontConfig();
        int oversample = (dpiScale > 1.0f) ? 1 : 3;
        fontConfig.setOversampleH(oversample); // Oversample horizontally
        fontConfig.setOversampleV(1); // Oversample vertically
        fontConfig.setPixelSnapH(true); // Force characters to land on integer pixels
//        fontConfig.setRasterizerMultiply(1.2f); // Slight boost to edge contrast

        // Add default font for latin glyphs
//        io.getFonts().addFontFromMemoryTTF(loadFromResources("editor/fonts/JetBrainsMonoNL-Medium.ttf"), Math.round(16 * dpiScale), fontConfig);
//        io.getFonts().addFontFromMemoryTTF(loadFromResources("editor/fonts/InterDisplay-Regular.ttf"), Math.round(16 * dpiScale), fontConfig);
//        io.getFonts().addFontFromMemoryTTF(loadFromResources("editor/fonts/Inter-Medium.ttf"), Math.round(16 * dpiScale), fontConfig);
//        io.getFonts().addFontFromMemoryTTF(loadFromResources("editor/fonts/InterDisplay-Medium.ttf"), Math.round(16 * dpiScale), fontConfig);
        io.getFonts().addFontFromMemoryTTF(loadFromResources("editor/fonts/Inter-Regular.ttf"), Math.round(16 * dpiScale), fontConfig);

        // Material Icons merged with default font (for seamless icon+text usage)
        final ImFontConfig mergedIconConfig = new ImFontConfig();
        mergedIconConfig.setOversampleH(3); // Oversample horizontally
        mergedIconConfig.setOversampleV(1); // Oversample vertically
        mergedIconConfig.setPixelSnapH(true); // Force characters to land on integer pixels
        mergedIconConfig.setMergeMode(true);
        mergedIconConfig.setGlyphOffset(0, 1 * dpiScale);  // Lower icons to align with text
        io.getFonts().addFontFromMemoryTTF(iconFontData, Math.round(16 * dpiScale), mergedIconConfig, glyphRanges);

        // Separate icon fonts (NOT merged) for thumbnail fallbacks at various sizes
        final ImFontConfig iconConfig = new ImFontConfig();
        iconConfig.setOversampleH(3); // Oversample horizontally
        iconConfig.setOversampleV(1); // Oversample vertically
        iconConfig.setPixelSnapH(true); // Force characters to land on integer pixels
        ImFont iconFontTiny = io.getFonts().addFontFromMemoryTTF(iconFontData, Math.round(12 * dpiScale), iconConfig, glyphRanges);
        ImFont iconFontSmall = io.getFonts().addFontFromMemoryTTF(iconFontData, Math.round(24 * dpiScale), iconConfig, glyphRanges);
        ImFont iconFontMedium = io.getFonts().addFontFromMemoryTTF(iconFontData, Math.round(32 * dpiScale), iconConfig, glyphRanges);
        ImFont iconFontLarge = io.getFonts().addFontFromMemoryTTF(iconFontData, Math.round(48 * dpiScale), iconConfig, glyphRanges);

        io.getFonts().build();

        // Register icon fonts with EditorFonts helper
        EditorFonts.setIconFonts(iconFontTiny, iconFontSmall, iconFontMedium, iconFontLarge);

        fontConfig.destroy();
        mergedIconConfig.destroy();
        iconConfig.destroy();

    }

    private byte[] loadFromResources(String fontPath) {
        try {
            return Files.readAllBytes(Paths.get(fontPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Begins a new ImGui frame.
     * Call this at the start of each frame before any ImGui calls.
     */
    public void newFrame() {
        if (!initialized) return;

        imGuiGl3.newFrame();
        imGuiGlfw.newFrame();
        ImGui.newFrame();
    }

    /**
     * Renders the ImGui draw data.
     * Call this at the end of each frame after all ImGui calls.
     */
    public void render() {
        if (!initialized) return;

        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    /**
     * Destroys ImGui and releases resources.
     */
    public void destroy() {
        if (!initialized) return;

        System.out.println("Destroying ImGui...");

        // Shutdown in reverse order of initialization
        imGuiGl3.shutdown();
        imGuiGlfw.shutdown();
        ImGui.destroyContext();

        initialized = false;
        System.out.println("ImGui destroyed");
    }

    /**
     * Checks if ImGui wants to capture mouse input.
     */
    public boolean wantCaptureMouse() {
        return ImGui.getIO().getWantCaptureMouse();
    }

    /**
     * Checks if ImGui wants to capture keyboard input.
     */
    public boolean wantCaptureKeyboard() {
        return ImGui.getIO().getWantCaptureKeyboard();
    }

    /**
     * Applies a dark theme with vivid blue accents on interactive elements.
     * Dark neutral backgrounds with flat styling, blue buttons, headers, and tabs.
     */
    private void applyDarkTheme() {
        ImGui.styleColorsDark();

        ImGuiStyle style = ImGui.getStyle();

        style.setWindowRounding(1.0f);
        style.setFrameRounding(1.0f);
        style.setScrollbarRounding(1.0f);
        style.setGrabRounding(1.0f);
        style.setTabRounding(1.0f);

        style.setWindowBorderSize(1.0f);
        style.setFrameBorderSize(1.0f);
        style.setPopupBorderSize(1.0f);

        style.setWindowPadding(8.0f, 8.0f);
        style.setFramePadding(4.0f, 3.0f);
        style.setItemSpacing(8.0f, 4.0f);
        style.setItemInnerSpacing(4.0f, 4.0f);

        // -- Text --
        style.setColor(ImGuiCol.Text,                 0.82f, 0.82f, 0.82f, 1.00f);  // #D2D2D2
        style.setColor(ImGuiCol.TextDisabled,          0.50f, 0.50f, 0.50f, 1.00f);

        // -- Backgrounds (same as applyDarkTheme - pure neutral) --
        style.setColor(ImGuiCol.WindowBg,              0.18f, 0.18f, 0.18f, 1.00f);  // #2E2E2E
        style.setColor(ImGuiCol.ChildBg,               0.18f, 0.18f, 0.18f, 1.00f);
        style.setColor(ImGuiCol.PopupBg,               0.20f, 0.20f, 0.20f, 1.00f);  // #333333
        style.setColor(ImGuiCol.Border,                0.10f, 0.10f, 0.10f, 1.00f);  // #1A1A1A
        style.setColor(ImGuiCol.BorderShadow,          0.00f, 0.00f, 0.00f, 0.00f);

        // -- Input fields (neutral dark) --
        style.setColor(ImGuiCol.FrameBg,               0.13f, 0.13f, 0.13f, 1.00f);  // #212121
        style.setColor(ImGuiCol.FrameBgHovered,        0.17f, 0.17f, 0.17f, 1.00f);  // #2B2B2B
        style.setColor(ImGuiCol.FrameBgActive,         0.17f, 0.36f, 0.53f, 1.00f);  // #2C5D87 blue

        // -- Title bars (neutral) --
        style.setColor(ImGuiCol.TitleBg,               0.15f, 0.15f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.TitleBgActive,         0.15f, 0.15f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.TitleBgCollapsed,      0.15f, 0.15f, 0.15f, 0.50f);

        // -- Menu bar --
        style.setColor(ImGuiCol.MenuBarBg,             0.19f, 0.19f, 0.19f, 1.00f);

        // -- Scrollbar --
        style.setColor(ImGuiCol.ScrollbarBg,           0.18f, 0.18f, 0.18f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrab,         0.31f, 0.31f, 0.31f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabHovered,  0.38f, 0.38f, 0.38f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabActive,   0.45f, 0.45f, 0.45f, 1.00f);

        // -- Accent blue (more vivid) --
        style.setColor(ImGuiCol.CheckMark,             0.35f, 0.62f, 0.95f, 1.00f);

        style.setColor(ImGuiCol.SliderGrab,            0.35f, 0.62f, 0.95f, 1.00f);
        style.setColor(ImGuiCol.SliderGrabActive,      0.28f, 0.52f, 0.82f, 1.00f);

        // -- Buttons (blue that pops against the dark background) --
        style.setColor(ImGuiCol.Button,                0.18f, 0.30f, 0.50f, 1.00f);  // visible blue at rest
        style.setColor(ImGuiCol.ButtonHovered,         0.22f, 0.40f, 0.65f, 1.00f);  // brighter on hover
        style.setColor(ImGuiCol.ButtonActive,          0.28f, 0.50f, 0.78f, 1.00f);  // vivid on press

        // -- Headers (blue that reads clearly) --
        style.setColor(ImGuiCol.Header,                0.16f, 0.26f, 0.42f, 1.00f);  // noticeable blue
        style.setColor(ImGuiCol.HeaderHovered,         0.20f, 0.36f, 0.58f, 1.00f);  // brighter on hover
        style.setColor(ImGuiCol.HeaderActive,          0.25f, 0.46f, 0.72f, 1.00f);  // vivid on active

        // -- Separators --
        style.setColor(ImGuiCol.Separator,             0.10f, 0.10f, 0.10f, 1.00f);
        style.setColor(ImGuiCol.SeparatorHovered,      0.28f, 0.52f, 0.82f, 0.70f);
        style.setColor(ImGuiCol.SeparatorActive,       0.35f, 0.62f, 0.95f, 1.00f);

        // -- Resize grips --
        style.setColor(ImGuiCol.ResizeGrip,            0.26f, 0.26f, 0.26f, 0.50f);
        style.setColor(ImGuiCol.ResizeGripHovered,     0.28f, 0.52f, 0.82f, 0.70f);
        style.setColor(ImGuiCol.ResizeGripActive,      0.35f, 0.62f, 0.95f, 1.00f);

        // -- Tabs (blue-tinted active) --
        style.setColor(ImGuiCol.Tab,                   0.14f, 0.14f, 0.14f, 1.00f);  // dark inactive
        style.setColor(ImGuiCol.TabHovered,            0.20f, 0.36f, 0.58f, 1.00f);  // blue on hover
        style.setColor(ImGuiCol.TabActive,             0.15f, 0.25f, 0.40f, 1.00f);  // clear blue tint
        style.setColor(ImGuiCol.TabUnfocused,          0.11f, 0.11f, 0.11f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocusedActive,    0.13f, 0.20f, 0.30f, 1.00f);  // visible blue

        // -- Docking --
        style.setColor(ImGuiCol.DockingPreview,        0.28f, 0.52f, 0.82f, 0.50f);
        style.setColor(ImGuiCol.DockingEmptyBg,        0.08f, 0.08f, 0.08f, 1.00f);

        // -- Plots --
        style.setColor(ImGuiCol.PlotLines,             0.60f, 0.60f, 0.60f, 1.00f);
        style.setColor(ImGuiCol.PlotLinesHovered,      1.00f, 0.70f, 0.40f, 1.00f);
        style.setColor(ImGuiCol.PlotHistogram,         0.35f, 0.62f, 0.95f, 1.00f);
        style.setColor(ImGuiCol.PlotHistogramHovered,  1.00f, 0.80f, 0.50f, 1.00f);

        // -- Tables --
        style.setColor(ImGuiCol.TableHeaderBg,         0.15f, 0.15f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.TableBorderStrong,     0.10f, 0.10f, 0.10f, 1.00f);
        style.setColor(ImGuiCol.TableBorderLight,      0.15f, 0.15f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.TableRowBg,            0.00f, 0.00f, 0.00f, 0.00f);
        style.setColor(ImGuiCol.TableRowBgAlt,         0.16f, 0.16f, 0.16f, 0.40f);

        // -- Selection & interaction --
        style.setColor(ImGuiCol.TextSelectedBg,        0.22f, 0.44f, 0.70f, 0.50f);
        style.setColor(ImGuiCol.DragDropTarget,        0.35f, 0.62f, 0.95f, 0.90f);

        // -- Navigation --
        style.setColor(ImGuiCol.NavHighlight,          0.35f, 0.62f, 0.95f, 1.00f);
        style.setColor(ImGuiCol.NavWindowingHighlight,  1.00f, 1.00f, 1.00f, 0.70f);
        style.setColor(ImGuiCol.NavWindowingDimBg,     0.80f, 0.80f, 0.80f, 0.20f);
        style.setColor(ImGuiCol.ModalWindowDimBg,      0.00f, 0.00f, 0.00f, 0.50f);
    }

    /**
     * Applies a Unity-inspired light gray theme with vivid blue accents on interactive elements.
     */
    @SuppressWarnings("unused")
    private void applyLightTheme() {
        ImGui.styleColorsLight();

        ImGuiStyle style = ImGui.getStyle();

        style.setWindowRounding(1.0f);
        style.setFrameRounding(1.0f);
        style.setScrollbarRounding(1.0f);
        style.setGrabRounding(1.0f);
        style.setTabRounding(1.0f);

        style.setWindowBorderSize(1.0f);
        style.setFrameBorderSize(1.0f);
        style.setPopupBorderSize(1.0f);

        style.setWindowPadding(8.0f, 8.0f);
        style.setFramePadding(4.0f, 3.0f);
        style.setItemSpacing(8.0f, 4.0f);
        style.setItemInnerSpacing(4.0f, 4.0f);

        // -- Text --
        style.setColor(ImGuiCol.Text,                 0.13f, 0.13f, 0.13f, 1.00f);  // #222222
        style.setColor(ImGuiCol.TextDisabled,          0.45f, 0.45f, 0.45f, 1.00f);

        // -- Backgrounds --
        style.setColor(ImGuiCol.WindowBg,              0.76f, 0.76f, 0.76f, 1.00f);  // #C2C2C2
        style.setColor(ImGuiCol.ChildBg,               0.76f, 0.76f, 0.76f, 1.00f);
        style.setColor(ImGuiCol.PopupBg,               0.82f, 0.82f, 0.82f, 1.00f);  // #D1D1D1
        style.setColor(ImGuiCol.Border,                0.60f, 0.60f, 0.60f, 1.00f);  // #999999
        style.setColor(ImGuiCol.BorderShadow,          0.00f, 0.00f, 0.00f, 0.00f);

        // -- Input fields --
        style.setColor(ImGuiCol.FrameBg,               0.88f, 0.88f, 0.88f, 1.00f);  // #E0E0E0
        style.setColor(ImGuiCol.FrameBgHovered,        0.84f, 0.84f, 0.84f, 1.00f);  // #D6D6D6
        style.setColor(ImGuiCol.FrameBgActive,         0.58f, 0.73f, 0.90f, 1.00f);  // blue tint on active

        // -- Title bars --
        style.setColor(ImGuiCol.TitleBg,               0.68f, 0.68f, 0.68f, 1.00f);  // #ADADAD
        style.setColor(ImGuiCol.TitleBgActive,         0.68f, 0.68f, 0.68f, 1.00f);
        style.setColor(ImGuiCol.TitleBgCollapsed,      0.68f, 0.68f, 0.68f, 0.50f);

        // -- Menu bar --
        style.setColor(ImGuiCol.MenuBarBg,             0.72f, 0.72f, 0.72f, 1.00f);  // #B8B8B8

        // -- Scrollbar --
        style.setColor(ImGuiCol.ScrollbarBg,           0.76f, 0.76f, 0.76f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrab,         0.55f, 0.55f, 0.55f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabHovered,  0.48f, 0.48f, 0.48f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabActive,   0.40f, 0.40f, 0.40f, 1.00f);

        // -- Accent blue (same vivid blue as dark theme) --
        style.setColor(ImGuiCol.CheckMark,             0.20f, 0.45f, 0.78f, 1.00f);

        style.setColor(ImGuiCol.SliderGrab,            0.20f, 0.45f, 0.78f, 1.00f);
        style.setColor(ImGuiCol.SliderGrabActive,      0.15f, 0.38f, 0.68f, 1.00f);

        // -- Buttons (blue that pops against light background) --
        style.setColor(ImGuiCol.Button,                0.42f, 0.58f, 0.80f, 1.00f);  // visible blue at rest
        style.setColor(ImGuiCol.ButtonHovered,         0.48f, 0.65f, 0.88f, 1.00f);  // brighter on hover
        style.setColor(ImGuiCol.ButtonActive,          0.30f, 0.50f, 0.76f, 1.00f);  // deeper on press

        // -- Headers (blue that reads clearly on light bg) --
        style.setColor(ImGuiCol.Header,                0.52f, 0.66f, 0.84f, 1.00f);
        style.setColor(ImGuiCol.HeaderHovered,         0.46f, 0.62f, 0.82f, 1.00f);
        style.setColor(ImGuiCol.HeaderActive,          0.38f, 0.56f, 0.80f, 1.00f);

        // -- Separators --
        style.setColor(ImGuiCol.Separator,             0.60f, 0.60f, 0.60f, 1.00f);
        style.setColor(ImGuiCol.SeparatorHovered,      0.28f, 0.52f, 0.82f, 0.70f);
        style.setColor(ImGuiCol.SeparatorActive,       0.35f, 0.62f, 0.95f, 1.00f);

        // -- Resize grips --
        style.setColor(ImGuiCol.ResizeGrip,            0.55f, 0.55f, 0.55f, 0.50f);
        style.setColor(ImGuiCol.ResizeGripHovered,     0.28f, 0.52f, 0.82f, 0.70f);
        style.setColor(ImGuiCol.ResizeGripActive,      0.35f, 0.62f, 0.95f, 1.00f);

        // -- Tabs (blue-tinted active) --
        style.setColor(ImGuiCol.Tab,                   0.68f, 0.68f, 0.68f, 1.00f);  // inactive matches title
        style.setColor(ImGuiCol.TabHovered,            0.52f, 0.66f, 0.84f, 1.00f);  // blue on hover
        style.setColor(ImGuiCol.TabActive,             0.76f, 0.76f, 0.76f, 1.00f);  // matches window bg
        style.setColor(ImGuiCol.TabUnfocused,          0.65f, 0.65f, 0.65f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocusedActive,    0.72f, 0.72f, 0.72f, 1.00f);

        // -- Docking --
        style.setColor(ImGuiCol.DockingPreview,        0.28f, 0.52f, 0.82f, 0.50f);
        style.setColor(ImGuiCol.DockingEmptyBg,        0.82f, 0.82f, 0.82f, 1.00f);

        // -- Plots --
        style.setColor(ImGuiCol.PlotLines,             0.40f, 0.40f, 0.40f, 1.00f);
        style.setColor(ImGuiCol.PlotLinesHovered,      0.90f, 0.50f, 0.20f, 1.00f);
        style.setColor(ImGuiCol.PlotHistogram,         0.20f, 0.45f, 0.78f, 1.00f);
        style.setColor(ImGuiCol.PlotHistogramHovered,  0.90f, 0.60f, 0.25f, 1.00f);

        // -- Tables --
        style.setColor(ImGuiCol.TableHeaderBg,         0.70f, 0.70f, 0.70f, 1.00f);
        style.setColor(ImGuiCol.TableBorderStrong,     0.60f, 0.60f, 0.60f, 1.00f);
        style.setColor(ImGuiCol.TableBorderLight,      0.68f, 0.68f, 0.68f, 1.00f);
        style.setColor(ImGuiCol.TableRowBg,            0.00f, 0.00f, 0.00f, 0.00f);
        style.setColor(ImGuiCol.TableRowBgAlt,         0.72f, 0.72f, 0.72f, 0.40f);

        // -- Selection & interaction --
        style.setColor(ImGuiCol.TextSelectedBg,        0.28f, 0.52f, 0.82f, 0.35f);
        style.setColor(ImGuiCol.DragDropTarget,        0.35f, 0.62f, 0.95f, 0.90f);

        // -- Navigation --
        style.setColor(ImGuiCol.NavHighlight,          0.35f, 0.62f, 0.95f, 1.00f);
        style.setColor(ImGuiCol.NavWindowingHighlight,  1.00f, 1.00f, 1.00f, 0.70f);
        style.setColor(ImGuiCol.NavWindowingDimBg,     0.20f, 0.20f, 0.20f, 0.20f);
        style.setColor(ImGuiCol.ModalWindowDimBg,      0.20f, 0.20f, 0.20f, 0.35f);
    }
}