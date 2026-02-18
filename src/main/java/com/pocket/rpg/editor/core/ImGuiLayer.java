package com.pocket.rpg.editor.core;

import imgui.*;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiKey;
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
    private String activeTheme = "Dark";

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
        ImFont iconFontSmall = io.getFonts().addFontFromMemoryTTF(iconFontData, Math.round(20 * dpiScale), iconConfig, glyphRanges);
        ImFont iconFontMedium = io.getFonts().addFontFromMemoryTTF(iconFontData, Math.round(28 * dpiScale), iconConfig, glyphRanges);
        ImFont iconFontLarge = io.getFonts().addFontFromMemoryTTF(iconFontData, Math.round(44 * dpiScale), iconConfig, glyphRanges);

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

        // TEMP: Theme switching shortcuts for testing
        processThemeShortcuts();
    }

    /**
     * Temporary: polls Ctrl+Shift+F/G/H/J to switch themes at runtime.
     */
    private void processThemeShortcuts() {
        boolean ctrl = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
        boolean shift = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);
        if (!ctrl || !shift) return;

        if (ImGui.isKeyPressed(ImGuiKey.F, false) && !activeTheme.equals("Dark")) {
            applyDarkTheme();
            activeTheme = "Dark";
            System.out.println("Theme: Dark");
        } else if (ImGui.isKeyPressed(ImGuiKey.G, false) && !activeTheme.equals("Nord Aurora")) {
            applyNordAuroraTheme();
            activeTheme = "Nord Aurora";
            System.out.println("Theme: Nord Aurora");
        } else if (ImGui.isKeyPressed(ImGuiKey.H, false) && !activeTheme.equals("Catppuccin Mocha")) {
            applyCatppuccinMochaTheme();
            activeTheme = "Catppuccin Mocha";
            System.out.println("Theme: Catppuccin Mocha");
        } else if (ImGui.isKeyPressed(ImGuiKey.J, false) && !activeTheme.equals("Dark Catppuccin")) {
            applyDarkCatppuccinTheme();
            activeTheme = "Dark Catppuccin";
            System.out.println("Theme: Dark Catppuccin");
        } else if (ImGui.isKeyPressed(ImGuiKey.K, false) && !activeTheme.equals("Island Dark")) {
            applyIslandDarkTheme();
            activeTheme = "Island Dark";
            System.out.println("Theme: Island Dark");
        } else if (ImGui.isKeyPressed(ImGuiKey.L, false) && !activeTheme.equals("Dark Vivid")) {
            applyDarkVividTheme();
            activeTheme = "Dark Vivid";
            System.out.println("Theme: Dark Vivid");
        }
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
        EditorColors.applyDarkPalette();
        EditorColors.onThemeChanged();
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
        EditorColors.applyDarkPalette();
        EditorColors.onThemeChanged();
    }

    /**
     * Nord Aurora theme: blue-grey backgrounds with multiple accent colours.
     * Teal headers, frost blue buttons, aurora green checkmarks, soft purple hovers.
     */
    private void applyNordAuroraTheme() {
        ImGui.styleColorsDark();

        ImGuiStyle style = ImGui.getStyle();

        style.setWindowRounding(2.0f);
        style.setFrameRounding(2.0f);
        style.setScrollbarRounding(2.0f);
        style.setGrabRounding(2.0f);
        style.setTabRounding(2.0f);

        style.setWindowBorderSize(1.0f);
        style.setFrameBorderSize(1.0f);
        style.setPopupBorderSize(1.0f);

        style.setWindowPadding(8.0f, 8.0f);
        style.setFramePadding(4.0f, 3.0f);
        style.setItemSpacing(8.0f, 4.0f);
        style.setItemInnerSpacing(4.0f, 4.0f);

        // -- Text --
        style.setColor(ImGuiCol.Text,                  0.85f, 0.87f, 0.91f, 1.00f);  // Nord Snow Storm #D8DEE9
        style.setColor(ImGuiCol.TextDisabled,           0.53f, 0.57f, 0.63f, 1.00f);  // muted polar

        // -- Backgrounds (Nord Polar Night - blue-grey) --
        style.setColor(ImGuiCol.WindowBg,               0.18f, 0.20f, 0.25f, 1.00f);  // #2E3440
        style.setColor(ImGuiCol.ChildBg,                0.18f, 0.20f, 0.25f, 1.00f);
        style.setColor(ImGuiCol.PopupBg,                0.23f, 0.26f, 0.32f, 1.00f);  // #3B4252
        style.setColor(ImGuiCol.Border,                 0.13f, 0.15f, 0.19f, 1.00f);  // darker polar
        style.setColor(ImGuiCol.BorderShadow,           0.00f, 0.00f, 0.00f, 0.00f);

        // -- Input fields (blue-tinted, not just grey) --
        style.setColor(ImGuiCol.FrameBg,                0.22f, 0.26f, 0.34f, 1.00f);  // blue-tinted surface
        style.setColor(ImGuiCol.FrameBgHovered,         0.27f, 0.32f, 0.42f, 1.00f);  // lifted with blue
        style.setColor(ImGuiCol.FrameBgActive,          0.26f, 0.42f, 0.56f, 1.00f);  // clear frost blue tint

        // -- Title bars --
        style.setColor(ImGuiCol.TitleBg,                0.15f, 0.17f, 0.21f, 1.00f);
        style.setColor(ImGuiCol.TitleBgActive,          0.18f, 0.20f, 0.25f, 1.00f);
        style.setColor(ImGuiCol.TitleBgCollapsed,       0.15f, 0.17f, 0.21f, 0.50f);

        // -- Menu bar --
        style.setColor(ImGuiCol.MenuBarBg,              0.20f, 0.22f, 0.27f, 1.00f);

        // -- Scrollbar --
        style.setColor(ImGuiCol.ScrollbarBg,            0.18f, 0.20f, 0.25f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrab,          0.30f, 0.34f, 0.42f, 1.00f);  // #4C566A
        style.setColor(ImGuiCol.ScrollbarGrabHovered,   0.37f, 0.51f, 0.67f, 1.00f);  // Frost
        style.setColor(ImGuiCol.ScrollbarGrabActive,    0.51f, 0.63f, 0.76f, 1.00f);  // #81A1C1

        // -- Checkmarks/sliders: Aurora Green --
        style.setColor(ImGuiCol.CheckMark,              0.64f, 0.75f, 0.55f, 1.00f);  // #A3BE8C
        style.setColor(ImGuiCol.SliderGrab,             0.64f, 0.75f, 0.55f, 1.00f);  // #A3BE8C
        style.setColor(ImGuiCol.SliderGrabActive,       0.55f, 0.65f, 0.46f, 1.00f);

        // -- Buttons: Frost Blue (brighter) --
        style.setColor(ImGuiCol.Button,                 0.44f, 0.58f, 0.74f, 1.00f);  // vivid frost at rest
        style.setColor(ImGuiCol.ButtonHovered,          0.51f, 0.63f, 0.76f, 1.00f);  // #81A1C1
        style.setColor(ImGuiCol.ButtonActive,           0.56f, 0.74f, 0.73f, 1.00f);  // #8FBCBB teal pop

        // -- Headers: Teal (vivid) --
        style.setColor(ImGuiCol.Header,                 0.25f, 0.50f, 0.54f, 1.00f);  // noticeable teal
        style.setColor(ImGuiCol.HeaderHovered,          0.38f, 0.65f, 0.68f, 1.00f);  // bright teal
        style.setColor(ImGuiCol.HeaderActive,           0.53f, 0.75f, 0.82f, 1.00f);  // #88C0D0

        // -- Separators --
        style.setColor(ImGuiCol.Separator,              0.13f, 0.15f, 0.19f, 1.00f);
        style.setColor(ImGuiCol.SeparatorHovered,       0.71f, 0.56f, 0.68f, 0.70f);  // Aurora purple
        style.setColor(ImGuiCol.SeparatorActive,        0.71f, 0.56f, 0.68f, 1.00f);  // #B48EAD

        // -- Resize grips: Aurora Purple --
        style.setColor(ImGuiCol.ResizeGrip,             0.30f, 0.34f, 0.42f, 0.50f);
        style.setColor(ImGuiCol.ResizeGripHovered,      0.71f, 0.56f, 0.68f, 0.70f);  // #B48EAD
        style.setColor(ImGuiCol.ResizeGripActive,       0.71f, 0.56f, 0.68f, 1.00f);

        // -- Tabs --
        style.setColor(ImGuiCol.Tab,                    0.23f, 0.26f, 0.32f, 1.00f);  // #3B4252
        style.setColor(ImGuiCol.TabHovered,             0.40f, 0.60f, 0.63f, 1.00f);  // teal hover
        style.setColor(ImGuiCol.TabActive,              0.28f, 0.45f, 0.49f, 1.00f);  // muted teal
        style.setColor(ImGuiCol.TabUnfocused,           0.18f, 0.20f, 0.25f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocusedActive,     0.23f, 0.33f, 0.38f, 1.00f);

        // -- Docking --
        style.setColor(ImGuiCol.DockingPreview,         0.53f, 0.75f, 0.82f, 0.50f);  // #88C0D0
        style.setColor(ImGuiCol.DockingEmptyBg,         0.12f, 0.14f, 0.17f, 1.00f);

        // -- Plots --
        style.setColor(ImGuiCol.PlotLines,              0.53f, 0.75f, 0.82f, 1.00f);  // Frost
        style.setColor(ImGuiCol.PlotLinesHovered,       0.75f, 0.62f, 0.44f, 1.00f);  // Aurora yellow
        style.setColor(ImGuiCol.PlotHistogram,          0.64f, 0.75f, 0.55f, 1.00f);  // Aurora green
        style.setColor(ImGuiCol.PlotHistogramHovered,   0.75f, 0.62f, 0.44f, 1.00f);

        // -- Tables --
        style.setColor(ImGuiCol.TableHeaderBg,          0.23f, 0.26f, 0.32f, 1.00f);
        style.setColor(ImGuiCol.TableBorderStrong,      0.13f, 0.15f, 0.19f, 1.00f);
        style.setColor(ImGuiCol.TableBorderLight,       0.20f, 0.22f, 0.27f, 1.00f);
        style.setColor(ImGuiCol.TableRowBg,             0.00f, 0.00f, 0.00f, 0.00f);
        style.setColor(ImGuiCol.TableRowBgAlt,          0.23f, 0.26f, 0.32f, 0.30f);

        // -- Selection & interaction --
        style.setColor(ImGuiCol.TextSelectedBg,         0.37f, 0.51f, 0.67f, 0.50f);
        style.setColor(ImGuiCol.DragDropTarget,         0.75f, 0.62f, 0.44f, 0.90f);  // Aurora yellow

        // -- Navigation --
        style.setColor(ImGuiCol.NavHighlight,           0.53f, 0.75f, 0.82f, 1.00f);
        style.setColor(ImGuiCol.NavWindowingHighlight,  1.00f, 1.00f, 1.00f, 0.70f);
        style.setColor(ImGuiCol.NavWindowingDimBg,      0.80f, 0.80f, 0.80f, 0.20f);
        style.setColor(ImGuiCol.ModalWindowDimBg,       0.00f, 0.00f, 0.00f, 0.50f);
        EditorColors.applyNordAuroraPalette();
        EditorColors.onThemeChanged();
    }

    /**
     * Catppuccin Mocha theme: warm pastel dark with rich colour variety.
     * Blue buttons, teal headers, sapphire checkmarks, peach accents.
     */
    private void applyCatppuccinMochaTheme() {
        ImGui.styleColorsDark();

        ImGuiStyle style = ImGui.getStyle();

        style.setWindowRounding(3.0f);
        style.setFrameRounding(3.0f);
        style.setScrollbarRounding(3.0f);
        style.setGrabRounding(3.0f);
        style.setTabRounding(3.0f);

        style.setWindowBorderSize(1.0f);
        style.setFrameBorderSize(1.0f);
        style.setPopupBorderSize(1.0f);

        style.setWindowPadding(8.0f, 8.0f);
        style.setFramePadding(4.0f, 3.0f);
        style.setItemSpacing(8.0f, 4.0f);
        style.setItemInnerSpacing(4.0f, 4.0f);

        // -- Text --
        style.setColor(ImGuiCol.Text,                  0.80f, 0.84f, 0.96f, 1.00f);  // #CDD6F4
        style.setColor(ImGuiCol.TextDisabled,           0.44f, 0.46f, 0.56f, 1.00f);  // Overlay0

        // -- Backgrounds (Mocha Base/Mantle/Crust) --
        style.setColor(ImGuiCol.WindowBg,               0.12f, 0.12f, 0.18f, 1.00f);  // #1E1E2E Base
        style.setColor(ImGuiCol.ChildBg,                0.12f, 0.12f, 0.18f, 1.00f);
        style.setColor(ImGuiCol.PopupBg,                0.15f, 0.15f, 0.22f, 1.00f);  // slightly lighter
        style.setColor(ImGuiCol.Border,                 0.27f, 0.28f, 0.35f, 1.00f);  // #45475A Surface0
        style.setColor(ImGuiCol.BorderShadow,           0.00f, 0.00f, 0.00f, 0.00f);

        // -- Input fields (brighter surface tones) --
        style.setColor(ImGuiCol.FrameBg,                0.22f, 0.23f, 0.31f, 1.00f);  // between Surface0 and Surface1
        style.setColor(ImGuiCol.FrameBgHovered,         0.30f, 0.31f, 0.40f, 1.00f);  // lifted
        style.setColor(ImGuiCol.FrameBgActive,          0.24f, 0.38f, 0.58f, 1.00f);  // blue tint on active

        // -- Title bars --
        style.setColor(ImGuiCol.TitleBg,                0.07f, 0.07f, 0.11f, 1.00f);  // Crust
        style.setColor(ImGuiCol.TitleBgActive,          0.10f, 0.10f, 0.15f, 1.00f);  // Mantle
        style.setColor(ImGuiCol.TitleBgCollapsed,       0.07f, 0.07f, 0.11f, 0.50f);

        // -- Menu bar --
        style.setColor(ImGuiCol.MenuBarBg,              0.10f, 0.10f, 0.15f, 1.00f);  // Mantle

        // -- Scrollbar --
        style.setColor(ImGuiCol.ScrollbarBg,            0.12f, 0.12f, 0.18f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrab,          0.34f, 0.35f, 0.44f, 1.00f);  // Surface2
        style.setColor(ImGuiCol.ScrollbarGrabHovered,   0.44f, 0.46f, 0.56f, 1.00f);  // Overlay0
        style.setColor(ImGuiCol.ScrollbarGrabActive,    0.54f, 0.55f, 0.65f, 1.00f);  // Overlay1

        // -- Checkmarks/sliders: Sapphire --
        style.setColor(ImGuiCol.CheckMark,              0.45f, 0.78f, 0.93f, 1.00f);  // #74C7EC Sapphire
        style.setColor(ImGuiCol.SliderGrab,             0.45f, 0.78f, 0.93f, 1.00f);  // Sapphire
        style.setColor(ImGuiCol.SliderGrabActive,       0.54f, 0.71f, 0.98f, 1.00f);  // #89B4FA Blue

        // -- Buttons: Blue --
        style.setColor(ImGuiCol.Button,                 0.30f, 0.42f, 0.68f, 1.00f);  // visible blue
        style.setColor(ImGuiCol.ButtonHovered,          0.42f, 0.57f, 0.84f, 1.00f);  // brighter
        style.setColor(ImGuiCol.ButtonActive,           0.54f, 0.71f, 0.98f, 1.00f);  // #89B4FA Blue

        // -- Headers: Teal (brighter) --
        style.setColor(ImGuiCol.Header,                 0.20f, 0.42f, 0.42f, 1.00f);  // visible teal
        style.setColor(ImGuiCol.HeaderHovered,          0.32f, 0.60f, 0.58f, 1.00f);  // brighter
        style.setColor(ImGuiCol.HeaderActive,           0.45f, 0.78f, 0.73f, 1.00f);  // vivid teal

        // -- Separators --
        style.setColor(ImGuiCol.Separator,              0.27f, 0.28f, 0.35f, 1.00f);  // Surface0
        style.setColor(ImGuiCol.SeparatorHovered,       0.54f, 0.71f, 0.98f, 0.70f);  // Blue
        style.setColor(ImGuiCol.SeparatorActive,        0.54f, 0.71f, 0.98f, 1.00f);

        // -- Resize grips: Peach --
        style.setColor(ImGuiCol.ResizeGrip,             0.34f, 0.35f, 0.44f, 0.50f);
        style.setColor(ImGuiCol.ResizeGripHovered,      0.98f, 0.70f, 0.53f, 0.70f);  // #FAB387 Peach
        style.setColor(ImGuiCol.ResizeGripActive,       0.98f, 0.70f, 0.53f, 1.00f);

        // -- Tabs --
        style.setColor(ImGuiCol.Tab,                    0.19f, 0.19f, 0.27f, 1.00f);  // Surface0
        style.setColor(ImGuiCol.TabHovered,             0.32f, 0.60f, 0.58f, 1.00f);  // Teal hover
        style.setColor(ImGuiCol.TabActive,              0.20f, 0.42f, 0.42f, 1.00f);  // teal active
        style.setColor(ImGuiCol.TabUnfocused,           0.12f, 0.12f, 0.18f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocusedActive,     0.17f, 0.32f, 0.32f, 1.00f);

        // -- Docking --
        style.setColor(ImGuiCol.DockingPreview,         0.54f, 0.71f, 0.98f, 0.50f);  // Blue
        style.setColor(ImGuiCol.DockingEmptyBg,         0.07f, 0.07f, 0.11f, 1.00f);

        // -- Plots --
        style.setColor(ImGuiCol.PlotLines,              0.54f, 0.71f, 0.98f, 1.00f);  // Blue
        style.setColor(ImGuiCol.PlotLinesHovered,       0.98f, 0.70f, 0.53f, 1.00f);  // Peach
        style.setColor(ImGuiCol.PlotHistogram,          0.64f, 0.75f, 0.55f, 1.00f);  // Green
        style.setColor(ImGuiCol.PlotHistogramHovered,   0.98f, 0.70f, 0.53f, 1.00f);

        // -- Tables --
        style.setColor(ImGuiCol.TableHeaderBg,          0.19f, 0.19f, 0.27f, 1.00f);
        style.setColor(ImGuiCol.TableBorderStrong,      0.27f, 0.28f, 0.35f, 1.00f);
        style.setColor(ImGuiCol.TableBorderLight,       0.19f, 0.19f, 0.27f, 1.00f);
        style.setColor(ImGuiCol.TableRowBg,             0.00f, 0.00f, 0.00f, 0.00f);
        style.setColor(ImGuiCol.TableRowBgAlt,          0.19f, 0.19f, 0.27f, 0.30f);

        // -- Selection & interaction --
        style.setColor(ImGuiCol.TextSelectedBg,         0.54f, 0.71f, 0.98f, 0.40f);  // Blue
        style.setColor(ImGuiCol.DragDropTarget,         0.98f, 0.70f, 0.53f, 0.90f);  // Peach

        // -- Navigation --
        style.setColor(ImGuiCol.NavHighlight,           0.54f, 0.71f, 0.98f, 1.00f);
        style.setColor(ImGuiCol.NavWindowingHighlight,  1.00f, 1.00f, 1.00f, 0.70f);
        style.setColor(ImGuiCol.NavWindowingDimBg,      0.80f, 0.80f, 0.80f, 0.20f);
        style.setColor(ImGuiCol.ModalWindowDimBg,       0.00f, 0.00f, 0.00f, 0.50f);
        EditorColors.applyCatppuccinMochaPalette();
        EditorColors.onThemeChanged();
    }

    /**
     * Dark Catppuccin hybrid: Dark theme's neutral backgrounds with
     * Catppuccin Mocha's colourful buttons, headers, inputs, and accents.
     */
    private void applyDarkCatppuccinTheme() {
        ImGui.styleColorsDark();

        ImGuiStyle style = ImGui.getStyle();

        // Dark theme styling
        style.setWindowRounding(1.0f);
        style.setFrameRounding(3.0f);  // Catppuccin's softer frame rounding
        style.setScrollbarRounding(1.0f);
        style.setGrabRounding(3.0f);
        style.setTabRounding(3.0f);

        style.setWindowBorderSize(1.0f);
        style.setFrameBorderSize(1.0f);
        style.setPopupBorderSize(1.0f);

        style.setWindowPadding(8.0f, 8.0f);
        style.setFramePadding(4.0f, 3.0f);
        style.setItemSpacing(8.0f, 4.0f);
        style.setItemInnerSpacing(4.0f, 4.0f);

        // -- Text (Dark) --
        style.setColor(ImGuiCol.Text,                  0.82f, 0.82f, 0.82f, 1.00f);  // #D2D2D2
        style.setColor(ImGuiCol.TextDisabled,           0.50f, 0.50f, 0.50f, 1.00f);

        // -- Backgrounds (Dark - pure neutral) --
        style.setColor(ImGuiCol.WindowBg,               0.18f, 0.18f, 0.18f, 1.00f);  // #2E2E2E
        style.setColor(ImGuiCol.ChildBg,                0.18f, 0.18f, 0.18f, 1.00f);
        style.setColor(ImGuiCol.PopupBg,                0.20f, 0.20f, 0.20f, 1.00f);  // #333333
        style.setColor(ImGuiCol.Border,                 0.10f, 0.10f, 0.10f, 1.00f);  // #1A1A1A
        style.setColor(ImGuiCol.BorderShadow,           0.00f, 0.00f, 0.00f, 0.00f);

        // -- Input fields (Catppuccin - brighter with blue active tint) --
        style.setColor(ImGuiCol.FrameBg,                0.22f, 0.23f, 0.31f, 1.00f);  // blue-tinted surface
        style.setColor(ImGuiCol.FrameBgHovered,         0.30f, 0.31f, 0.40f, 1.00f);  // lifted
        style.setColor(ImGuiCol.FrameBgActive,          0.24f, 0.38f, 0.58f, 1.00f);  // blue tint on active

        // -- Title bars (Dark - neutral) --
        style.setColor(ImGuiCol.TitleBg,                0.15f, 0.15f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.TitleBgActive,          0.15f, 0.15f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.TitleBgCollapsed,       0.15f, 0.15f, 0.15f, 0.50f);

        // -- Menu bar (Dark) --
        style.setColor(ImGuiCol.MenuBarBg,              0.19f, 0.19f, 0.19f, 1.00f);

        // -- Scrollbar (Dark) --
        style.setColor(ImGuiCol.ScrollbarBg,            0.18f, 0.18f, 0.18f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrab,          0.31f, 0.31f, 0.31f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabHovered,   0.38f, 0.38f, 0.38f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabActive,    0.45f, 0.45f, 0.45f, 1.00f);

        // -- Checkmarks/sliders: Sapphire (Catppuccin) --
        style.setColor(ImGuiCol.CheckMark,              0.45f, 0.78f, 0.93f, 1.00f);  // #74C7EC Sapphire
        style.setColor(ImGuiCol.SliderGrab,             0.45f, 0.78f, 0.93f, 1.00f);  // Sapphire
        style.setColor(ImGuiCol.SliderGrabActive,       0.54f, 0.71f, 0.98f, 1.00f);  // #89B4FA Blue

        // -- Buttons: Blue (Catppuccin) --
        style.setColor(ImGuiCol.Button,                 0.30f, 0.42f, 0.68f, 1.00f);  // visible blue
        style.setColor(ImGuiCol.ButtonHovered,          0.42f, 0.57f, 0.84f, 1.00f);  // brighter
        style.setColor(ImGuiCol.ButtonActive,           0.54f, 0.71f, 0.98f, 1.00f);  // #89B4FA Blue

        // -- Headers: Teal (Catppuccin) --
        style.setColor(ImGuiCol.Header,                 0.20f, 0.42f, 0.42f, 1.00f);  // visible teal
        style.setColor(ImGuiCol.HeaderHovered,          0.32f, 0.60f, 0.58f, 1.00f);  // brighter
        style.setColor(ImGuiCol.HeaderActive,           0.45f, 0.78f, 0.73f, 1.00f);  // vivid teal

        // -- Separators (Dark) --
        style.setColor(ImGuiCol.Separator,              0.10f, 0.10f, 0.10f, 1.00f);
        style.setColor(ImGuiCol.SeparatorHovered,       0.54f, 0.71f, 0.98f, 0.70f);  // Blue
        style.setColor(ImGuiCol.SeparatorActive,        0.54f, 0.71f, 0.98f, 1.00f);

        // -- Resize grips: Peach (Catppuccin) --
        style.setColor(ImGuiCol.ResizeGrip,             0.26f, 0.26f, 0.26f, 0.50f);
        style.setColor(ImGuiCol.ResizeGripHovered,      0.98f, 0.70f, 0.53f, 0.70f);  // #FAB387 Peach
        style.setColor(ImGuiCol.ResizeGripActive,       0.98f, 0.70f, 0.53f, 1.00f);

        // -- Tabs (Catppuccin teal on Dark base) --
        style.setColor(ImGuiCol.Tab,                    0.14f, 0.14f, 0.14f, 1.00f);  // dark inactive
        style.setColor(ImGuiCol.TabHovered,             0.32f, 0.60f, 0.58f, 1.00f);  // Teal hover
        style.setColor(ImGuiCol.TabActive,              0.20f, 0.42f, 0.42f, 1.00f);  // teal active
        style.setColor(ImGuiCol.TabUnfocused,           0.11f, 0.11f, 0.11f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocusedActive,     0.17f, 0.32f, 0.32f, 1.00f);

        // -- Docking (Catppuccin Blue) --
        style.setColor(ImGuiCol.DockingPreview,         0.54f, 0.71f, 0.98f, 0.50f);
        style.setColor(ImGuiCol.DockingEmptyBg,         0.08f, 0.08f, 0.08f, 1.00f);

        // -- Plots (Dark base + Catppuccin accents) --
        style.setColor(ImGuiCol.PlotLines,              0.60f, 0.60f, 0.60f, 1.00f);
        style.setColor(ImGuiCol.PlotLinesHovered,       0.98f, 0.70f, 0.53f, 1.00f);  // Peach
        style.setColor(ImGuiCol.PlotHistogram,          0.45f, 0.78f, 0.93f, 1.00f);  // Sapphire
        style.setColor(ImGuiCol.PlotHistogramHovered,   0.98f, 0.70f, 0.53f, 1.00f);

        // -- Tables (Dark) --
        style.setColor(ImGuiCol.TableHeaderBg,          0.15f, 0.15f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.TableBorderStrong,      0.10f, 0.10f, 0.10f, 1.00f);
        style.setColor(ImGuiCol.TableBorderLight,       0.15f, 0.15f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.TableRowBg,             0.00f, 0.00f, 0.00f, 0.00f);
        style.setColor(ImGuiCol.TableRowBgAlt,          0.16f, 0.16f, 0.16f, 0.40f);

        // -- Selection & interaction (Catppuccin) --
        style.setColor(ImGuiCol.TextSelectedBg,         0.54f, 0.71f, 0.98f, 0.40f);  // Blue
        style.setColor(ImGuiCol.DragDropTarget,         0.98f, 0.70f, 0.53f, 0.90f);  // Peach

        // -- Navigation (Catppuccin Blue) --
        style.setColor(ImGuiCol.NavHighlight,           0.54f, 0.71f, 0.98f, 1.00f);
        style.setColor(ImGuiCol.NavWindowingHighlight,  1.00f, 1.00f, 1.00f, 0.70f);
        style.setColor(ImGuiCol.NavWindowingDimBg,      0.80f, 0.80f, 0.80f, 0.20f);
        style.setColor(ImGuiCol.ModalWindowDimBg,       0.00f, 0.00f, 0.00f, 0.50f);
        EditorColors.applyDarkCatppuccinPalette();
        EditorColors.onThemeChanged();
    }

    /**
     * JetBrains Island Dark theme: panels float on a near-black canvas.
     * WindowBg is the lighter panel surface (#1E1F22), DockingEmptyBg is the dark canvas (#131217).
     * Blue accent (#548AF7), rounded corners, visible depth layering.
     */
    private void applyIslandDarkTheme() {
        ImGui.styleColorsDark();

        ImGuiStyle style = ImGui.getStyle();

        style.setWindowRounding(6.0f);   // Islands uses rounded "floating" panels
        style.setFrameRounding(4.0f);
        style.setScrollbarRounding(4.0f);
        style.setGrabRounding(4.0f);
        style.setTabRounding(4.0f);

        style.setWindowBorderSize(1.0f);
        style.setFrameBorderSize(1.0f);
        style.setPopupBorderSize(1.0f);

        style.setWindowPadding(8.0f, 8.0f);
        style.setFramePadding(4.0f, 3.0f);
        style.setItemSpacing(8.0f, 4.0f);
        style.setItemInnerSpacing(4.0f, 4.0f);

        // -- Text --
        style.setColor(ImGuiCol.Text,                  0.74f, 0.75f, 0.77f, 1.00f);  // #BCBEC4
        style.setColor(ImGuiCol.TextDisabled,           0.48f, 0.49f, 0.52f, 1.00f);  // #7A7E85

        // -- Backgrounds: panel surface is LIGHTER than the canvas behind it --
        style.setColor(ImGuiCol.WindowBg,               0.12f, 0.12f, 0.13f, 1.00f);  // #1E1F22 panel surface
        style.setColor(ImGuiCol.ChildBg,                0.00f, 0.00f, 0.00f, 0.00f);  // inherit from parent
        style.setColor(ImGuiCol.PopupBg,                0.15f, 0.16f, 0.17f, 1.00f);  // #27282B slightly raised
        style.setColor(ImGuiCol.Border,                 0.20f, 0.21f, 0.22f, 1.00f);  // #343638 visible border
        style.setColor(ImGuiCol.BorderShadow,           0.00f, 0.00f, 0.00f, 0.00f);

        // -- Input fields (recessed into panel - darker than panel bg) --
        style.setColor(ImGuiCol.FrameBg,                0.09f, 0.10f, 0.11f, 1.00f);  // #181A1D recessed
        style.setColor(ImGuiCol.FrameBgHovered,         0.13f, 0.14f, 0.15f, 1.00f);  // #212325 lifted
        style.setColor(ImGuiCol.FrameBgActive,          0.15f, 0.20f, 0.30f, 1.00f);  // #25324D blue focus

        // -- Title bars (darker than panels, close to canvas) --
        style.setColor(ImGuiCol.TitleBg,                0.07f, 0.07f, 0.09f, 1.00f);  // #131217
        style.setColor(ImGuiCol.TitleBgActive,          0.10f, 0.10f, 0.12f, 1.00f);  // #1A1A1E
        style.setColor(ImGuiCol.TitleBgCollapsed,       0.07f, 0.07f, 0.09f, 0.50f);

        // -- Menu bar (matches panel surface) --
        style.setColor(ImGuiCol.MenuBarBg,              0.12f, 0.12f, 0.13f, 1.00f);  // #1E1F22

        // -- Scrollbar --
        style.setColor(ImGuiCol.ScrollbarBg,            0.12f, 0.12f, 0.13f, 1.00f);  // match panel
        style.setColor(ImGuiCol.ScrollbarGrab,          0.24f, 0.25f, 0.27f, 1.00f);  // #3C3F45
        style.setColor(ImGuiCol.ScrollbarGrabHovered,   0.30f, 0.31f, 0.33f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabActive,    0.37f, 0.38f, 0.40f, 1.00f);

        // -- Checkmarks/sliders: Island blue accent --
        style.setColor(ImGuiCol.CheckMark,              0.33f, 0.54f, 0.97f, 1.00f);  // #548AF7
        style.setColor(ImGuiCol.SliderGrab,             0.33f, 0.54f, 0.97f, 1.00f);  // #548AF7
        style.setColor(ImGuiCol.SliderGrabActive,       0.43f, 0.62f, 0.97f, 1.00f);  // #6D9DF8

        // -- Buttons (Island blue) --
        style.setColor(ImGuiCol.Button,                 0.33f, 0.54f, 0.97f, 1.00f);  // #548AF7
        style.setColor(ImGuiCol.ButtonHovered,          0.43f, 0.62f, 0.97f, 1.00f);  // #6D9DF8
        style.setColor(ImGuiCol.ButtonActive,           0.49f, 0.67f, 0.97f, 1.00f);  // #7CACF8

        // -- Headers (visible selection with blue tint) --
        style.setColor(ImGuiCol.Header,                 0.16f, 0.19f, 0.25f, 1.00f);  // blue-tinted selection
        style.setColor(ImGuiCol.HeaderHovered,          0.20f, 0.27f, 0.42f, 1.00f);  // brighter blue
        style.setColor(ImGuiCol.HeaderActive,           0.25f, 0.35f, 0.55f, 1.00f);  // vivid blue

        // -- Separators --
        style.setColor(ImGuiCol.Separator,              0.20f, 0.21f, 0.22f, 1.00f);  // #343638
        style.setColor(ImGuiCol.SeparatorHovered,       0.33f, 0.54f, 0.97f, 0.70f);
        style.setColor(ImGuiCol.SeparatorActive,        0.33f, 0.54f, 0.97f, 1.00f);

        // -- Resize grips --
        style.setColor(ImGuiCol.ResizeGrip,             0.24f, 0.25f, 0.27f, 0.50f);
        style.setColor(ImGuiCol.ResizeGripHovered,      0.33f, 0.54f, 0.97f, 0.70f);
        style.setColor(ImGuiCol.ResizeGripActive,       0.33f, 0.54f, 0.97f, 1.00f);

        // -- Tabs: inactive darker than panel, active matches panel bg --
        style.setColor(ImGuiCol.Tab,                    0.09f, 0.09f, 0.10f, 1.00f);  // #161619 recessed
        style.setColor(ImGuiCol.TabHovered,             0.20f, 0.27f, 0.42f, 1.00f);  // blue tint hover
        style.setColor(ImGuiCol.TabActive,              0.12f, 0.12f, 0.13f, 1.00f);  // #1E1F22 matches panel
        style.setColor(ImGuiCol.TabUnfocused,           0.07f, 0.07f, 0.09f, 1.00f);  // canvas dark
        style.setColor(ImGuiCol.TabUnfocusedActive,     0.12f, 0.12f, 0.13f, 1.00f);  // still matches panel

        // -- Docking: canvas is the DARK layer panels float on --
        style.setColor(ImGuiCol.DockingPreview,         0.33f, 0.54f, 0.97f, 0.50f);
        style.setColor(ImGuiCol.DockingEmptyBg,         0.07f, 0.07f, 0.09f, 1.00f);  // #131217 dark canvas

        // -- Plots --
        style.setColor(ImGuiCol.PlotLines,              0.74f, 0.75f, 0.77f, 1.00f);
        style.setColor(ImGuiCol.PlotLinesHovered,       0.91f, 0.64f, 0.24f, 1.00f);  // #E8A33E warning
        style.setColor(ImGuiCol.PlotHistogram,          0.33f, 0.54f, 0.97f, 1.00f);
        style.setColor(ImGuiCol.PlotHistogramHovered,   0.91f, 0.64f, 0.24f, 1.00f);

        // -- Tables --
        style.setColor(ImGuiCol.TableHeaderBg,          0.14f, 0.14f, 0.16f, 1.00f);  // slightly raised
        style.setColor(ImGuiCol.TableBorderStrong,      0.20f, 0.21f, 0.22f, 1.00f);
        style.setColor(ImGuiCol.TableBorderLight,       0.15f, 0.16f, 0.17f, 1.00f);
        style.setColor(ImGuiCol.TableRowBg,             0.00f, 0.00f, 0.00f, 0.00f);
        style.setColor(ImGuiCol.TableRowBgAlt,          0.14f, 0.14f, 0.16f, 0.40f);

        // -- Selection & interaction --
        style.setColor(ImGuiCol.TextSelectedBg,         0.22f, 0.24f, 0.22f, 1.00f);  // #373B39
        style.setColor(ImGuiCol.DragDropTarget,         0.33f, 0.54f, 0.97f, 0.90f);

        // -- Navigation --
        style.setColor(ImGuiCol.NavHighlight,           0.33f, 0.54f, 0.97f, 1.00f);
        style.setColor(ImGuiCol.NavWindowingHighlight,  1.00f, 1.00f, 1.00f, 0.70f);
        style.setColor(ImGuiCol.NavWindowingDimBg,      0.80f, 0.80f, 0.80f, 0.20f);
        style.setColor(ImGuiCol.ModalWindowDimBg,       0.00f, 0.00f, 0.00f, 0.50f);
        EditorColors.applyIslandDarkPalette();
        EditorColors.onThemeChanged();
    }

    /**
     * Dark Vivid: original Dark theme's neutral grey backgrounds with
     * punchier, more saturated interactive elements.
     * Vivid blue buttons, teal-blue headers, cyan checkmarks, colourful fields.
     */
    private void applyDarkVividTheme() {
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

        // -- Text (same as Dark) --
        style.setColor(ImGuiCol.Text,                  0.82f, 0.82f, 0.82f, 1.00f);  // #D2D2D2
        style.setColor(ImGuiCol.TextDisabled,           0.50f, 0.50f, 0.50f, 1.00f);

        // -- Backgrounds (same as Dark - pure neutral) --
        style.setColor(ImGuiCol.WindowBg,               0.18f, 0.18f, 0.18f, 1.00f);  // #2E2E2E
        style.setColor(ImGuiCol.ChildBg,                0.18f, 0.18f, 0.18f, 1.00f);
        style.setColor(ImGuiCol.PopupBg,                0.20f, 0.20f, 0.20f, 1.00f);  // #333333
        style.setColor(ImGuiCol.Border,                 0.10f, 0.10f, 0.10f, 1.00f);  // #1A1A1A
        style.setColor(ImGuiCol.BorderShadow,           0.00f, 0.00f, 0.00f, 0.00f);

        // -- Input fields (colourful: blue-tinted, vivid active) --
        style.setColor(ImGuiCol.FrameBg,                0.14f, 0.16f, 0.20f, 1.00f);  // blue-grey tinted
        style.setColor(ImGuiCol.FrameBgHovered,         0.18f, 0.22f, 0.30f, 1.00f);  // noticeable blue lift
        style.setColor(ImGuiCol.FrameBgActive,          0.20f, 0.40f, 0.65f, 1.00f);  // vivid blue focus

        // -- Title bars (same as Dark) --
        style.setColor(ImGuiCol.TitleBg,                0.15f, 0.15f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.TitleBgActive,          0.15f, 0.15f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.TitleBgCollapsed,       0.15f, 0.15f, 0.15f, 0.50f);

        // -- Menu bar (same as Dark) --
        style.setColor(ImGuiCol.MenuBarBg,              0.19f, 0.19f, 0.19f, 1.00f);

        // -- Scrollbar (same as Dark) --
        style.setColor(ImGuiCol.ScrollbarBg,            0.18f, 0.18f, 0.18f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrab,          0.31f, 0.31f, 0.31f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabHovered,   0.38f, 0.38f, 0.38f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabActive,    0.45f, 0.45f, 0.45f, 1.00f);

        // -- Checkmarks/sliders: Cyan (eye-catching) --
        style.setColor(ImGuiCol.CheckMark,              0.30f, 0.85f, 0.95f, 1.00f);  // bright cyan
        style.setColor(ImGuiCol.SliderGrab,             0.30f, 0.85f, 0.95f, 1.00f);  // cyan
        style.setColor(ImGuiCol.SliderGrabActive,       0.40f, 0.70f, 0.98f, 1.00f);  // blue shift on drag

        // -- Buttons: Vivid blue (brighter than original) --
        style.setColor(ImGuiCol.Button,                 0.22f, 0.42f, 0.72f, 1.00f);  // punchy blue
        style.setColor(ImGuiCol.ButtonHovered,          0.30f, 0.52f, 0.85f, 1.00f);  // bright on hover
        style.setColor(ImGuiCol.ButtonActive,           0.38f, 0.62f, 0.98f, 1.00f);  // vivid on press

        // -- Headers: Teal-blue (distinct from buttons) --
        style.setColor(ImGuiCol.Header,                 0.14f, 0.35f, 0.40f, 1.00f);  // teal-blue
        style.setColor(ImGuiCol.HeaderHovered,          0.20f, 0.48f, 0.55f, 1.00f);  // brighter teal
        style.setColor(ImGuiCol.HeaderActive,           0.28f, 0.60f, 0.68f, 1.00f);  // vivid teal

        // -- Separators (same as Dark) --
        style.setColor(ImGuiCol.Separator,              0.10f, 0.10f, 0.10f, 1.00f);
        style.setColor(ImGuiCol.SeparatorHovered,       0.30f, 0.85f, 0.95f, 0.70f);  // cyan
        style.setColor(ImGuiCol.SeparatorActive,        0.30f, 0.85f, 0.95f, 1.00f);

        // -- Resize grips --
        style.setColor(ImGuiCol.ResizeGrip,             0.26f, 0.26f, 0.26f, 0.50f);
        style.setColor(ImGuiCol.ResizeGripHovered,      0.38f, 0.62f, 0.98f, 0.70f);  // blue
        style.setColor(ImGuiCol.ResizeGripActive,       0.38f, 0.62f, 0.98f, 1.00f);

        // -- Tabs (teal active, distinct from grey inactive) --
        style.setColor(ImGuiCol.Tab,                    0.14f, 0.14f, 0.14f, 1.00f);  // dark inactive
        style.setColor(ImGuiCol.TabHovered,             0.20f, 0.48f, 0.55f, 1.00f);  // teal hover
        style.setColor(ImGuiCol.TabActive,              0.14f, 0.35f, 0.40f, 1.00f);  // teal active
        style.setColor(ImGuiCol.TabUnfocused,           0.11f, 0.11f, 0.11f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocusedActive,     0.12f, 0.26f, 0.30f, 1.00f);  // muted teal

        // -- Docking --
        style.setColor(ImGuiCol.DockingPreview,         0.30f, 0.52f, 0.85f, 0.50f);
        style.setColor(ImGuiCol.DockingEmptyBg,         0.08f, 0.08f, 0.08f, 1.00f);

        // -- Plots --
        style.setColor(ImGuiCol.PlotLines,              0.60f, 0.60f, 0.60f, 1.00f);
        style.setColor(ImGuiCol.PlotLinesHovered,       1.00f, 0.70f, 0.40f, 1.00f);
        style.setColor(ImGuiCol.PlotHistogram,          0.30f, 0.85f, 0.95f, 1.00f);  // cyan
        style.setColor(ImGuiCol.PlotHistogramHovered,   1.00f, 0.80f, 0.50f, 1.00f);

        // -- Tables (same as Dark) --
        style.setColor(ImGuiCol.TableHeaderBg,          0.15f, 0.15f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.TableBorderStrong,      0.10f, 0.10f, 0.10f, 1.00f);
        style.setColor(ImGuiCol.TableBorderLight,       0.15f, 0.15f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.TableRowBg,             0.00f, 0.00f, 0.00f, 0.00f);
        style.setColor(ImGuiCol.TableRowBgAlt,          0.16f, 0.16f, 0.16f, 0.40f);

        // -- Selection & interaction --
        style.setColor(ImGuiCol.TextSelectedBg,         0.22f, 0.44f, 0.70f, 0.50f);
        style.setColor(ImGuiCol.DragDropTarget,         0.30f, 0.85f, 0.95f, 0.90f);  // cyan

        // -- Navigation --
        style.setColor(ImGuiCol.NavHighlight,           0.30f, 0.85f, 0.95f, 1.00f);  // cyan
        style.setColor(ImGuiCol.NavWindowingHighlight,  1.00f, 1.00f, 1.00f, 0.70f);
        style.setColor(ImGuiCol.NavWindowingDimBg,      0.80f, 0.80f, 0.80f, 0.20f);
        style.setColor(ImGuiCol.ModalWindowDimBg,       0.00f, 0.00f, 0.00f, 0.50f);
        EditorColors.applyDarkVividPalette();
        EditorColors.onThemeChanged();
    }
}