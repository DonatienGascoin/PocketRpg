package com.pocket.rpg.editor.panels;

import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.panels.uidesigner.*;
import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import com.pocket.rpg.editor.rendering.EditorUIBridge;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.ToolManager;
import com.pocket.rpg.rendering.pipeline.RenderParams;
import com.pocket.rpg.rendering.pipeline.RenderPipeline;
import com.pocket.rpg.rendering.targets.FramebufferTarget;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import lombok.Setter;
import org.joml.Vector4f;

import java.util.List;

/**
 * UI Designer panel - visual editor for UI elements.
 * <p>
 * Refactored to delegate to focused sub-components:
 * <ul>
 *   <li>{@link UIDesignerState} - Shared state (camera, zoom, drag state)</li>
 *   <li>{@link UIDesignerCoordinates} - Coordinate conversion, bounds calculation</li>
 *   <li>{@link UIDesignerGizmoDrawer} - Selection handles, anchor/pivot visualization</li>
 *   <li>{@link UIDesignerInputHandler} - Mouse/keyboard input handling</li>
 *   <li>{@link UIDesignerRenderer} - UI element rendering</li>
 * </ul>
 */
public class UIDesignerPanel {

    private final EditorContext context;
    private final GameConfig gameConfig;
    private final RenderingConfig renderingConfig;

    // Sub-components
    private final UIDesignerState state;
    private final UIDesignerCoordinates coords;
    private final UIDesignerGizmoDrawer gizmoDrawer;
    private final UIDesignerInputHandler inputHandler;
    private final UIDesignerRenderer renderer;

    // Unified rendering via RenderPipeline
    private RenderPipeline pipeline;
    private EditorFramebuffer framebuffer;
    private ViewportConfig viewportConfig;
    private final EditorUIBridge uiBridge = new EditorUIBridge();
    private boolean pipelineInitialized = false;

    // Clear color for UI rendering (transparent)
    private static final Vector4f CLEAR_COLOR = new Vector4f(0f, 0f, 0f, 0f);

    @Setter
    private ToolManager toolManager;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public UIDesignerPanel(EditorContext context) {
        this.context = context;
        this.gameConfig = context.getGameConfig();
        this.renderingConfig = context.getRenderingConfig();

        // Create sub-components
        this.state = new UIDesignerState(gameConfig);
        this.coords = new UIDesignerCoordinates(state);
        this.gizmoDrawer = new UIDesignerGizmoDrawer(state, coords);
        this.inputHandler = new UIDesignerInputHandler(state, coords, context);
        this.renderer = new UIDesignerRenderer(state, coords, renderingConfig);
    }

    /**
     * Initializes the RenderPipeline for UI rendering.
     * Called lazily on first render.
     */
    private void initPipeline() {
        if (pipelineInitialized) return;

        int width = state.getCanvasWidth();
        int height = state.getCanvasHeight();

        // Create framebuffer for UI rendering
        framebuffer = new EditorFramebuffer(width, height);
        framebuffer.init();

        // Create viewport config matching canvas dimensions
        viewportConfig = new ViewportConfig(width, height, width, height);

        // Create pipeline (no post-processing for UI)
        pipeline = new RenderPipeline(viewportConfig, renderingConfig);
        pipeline.init();

        pipelineInitialized = true;
        System.out.println("[UIDesignerPanel] Pipeline initialized (" + width + "x" + height + ")");
    }

    // ========================================================================
    // MAIN RENDER
    // ========================================================================

    public void render() {
        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        boolean visible = ImGui.begin("UI Designer", flags);

        if (!visible) {
            ImGui.end();
            return;
        }

        state.setFocused(ImGui.isWindowFocused());
        renderToolbar();
        ImGui.separator();
        renderViewport();
        ImGui.end();
    }

    // ========================================================================
    // TOOLBAR
    // ========================================================================

    private void renderToolbar() {
        ImGui.text("Background:");
        ImGui.sameLine();

        if (ImGui.radioButton("Gray", state.getBackgroundMode() == UIDesignerState.BackgroundMode.GRAY)) {
            state.setBackgroundMode(UIDesignerState.BackgroundMode.GRAY);
        }
        ImGui.sameLine();
        if (ImGui.radioButton("World", state.getBackgroundMode() == UIDesignerState.BackgroundMode.WORLD)) {
            state.setBackgroundMode(UIDesignerState.BackgroundMode.WORLD);
        }

        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        // Snap button with icon and green highlight when active
        boolean snapEnabled = state.isSnapEnabled();
        if (snapEnabled) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1.0f);
        }
        if (ImGui.button(FontAwesomeIcons.Magnet + " Snap")) {
            state.setSnapEnabled(!snapEnabled);
        }
        if (snapEnabled) {
            ImGui.popStyleColor(2);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Toggle snap to edges");
        }

        ImGui.sameLine();

        // Anchor Lines button with icon and green highlight when active
        boolean showAnchorLines = state.isShowAnchorLines();
        if (showAnchorLines) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1.0f);
        }
        if (ImGui.button(FontAwesomeIcons.ProjectDiagram + " Anchors")) {
            state.setShowAnchorLines(!showAnchorLines);
        }
        if (showAnchorLines) {
            ImGui.popStyleColor(2);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Toggle anchor lines visibility");
        }

        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        if (ImGui.button(FontAwesomeIcons.Crosshairs + " Reset")) {
            state.resetCamera();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Reset view to fit canvas");
        }

        ImGui.sameLine();
        ImGui.text(String.format("Zoom: %.0f%%", state.getZoom() * 100));
    }

    // ========================================================================
    // VIEWPORT
    // ========================================================================

    private void renderViewport() {
        // Initialize pipeline on first render
        if (!pipelineInitialized) {
            initPipeline();
        }

        // Get viewport bounds - start from current cursor position (after toolbar)
        ImVec2 cursorPos = ImGui.getCursorScreenPos();
        ImVec2 contentMax = ImGui.getWindowContentRegionMax();
        ImVec2 windowPos = ImGui.getWindowPos();
        ImVec2 contentMin = ImGui.getWindowContentRegionMin();

        float viewportX = cursorPos.x;
        float viewportY = cursorPos.y;
        float viewportWidth = contentMax.x - contentMin.x;
        float viewportHeight = (windowPos.y + contentMax.y) - cursorPos.y;

        // Update state
        state.setViewportX(viewportX);
        state.setViewportY(viewportY);
        state.setViewportWidth(viewportWidth);
        state.setViewportHeight(viewportHeight);

        // Check if hovered
        ImVec2 mousePos = ImGui.getMousePos();
        boolean isHovered = mousePos.x >= viewportX && mousePos.x <= viewportX + viewportWidth &&
                mousePos.y >= viewportY && mousePos.y <= viewportY + viewportHeight;
        state.setHovered(isHovered);

        // Get current scene
        EditorScene scene = context.getCurrentScene();

        // Render UI elements to texture using unified RenderPipeline
        renderUIToTexture(scene);

        // Get draw list
        ImDrawList drawList = ImGui.getWindowDrawList();

        // Clip all drawing to viewport area (prevents drawing over toolbar)
        drawList.pushClipRect(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight, true);

        // Draw gray background
        drawList.addRectFilled(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight,
                ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1.0f));

        // Draw canvas bounds
        gizmoDrawer.drawCanvasBounds(drawList);

        // Draw world background (if enabled)
        renderer.drawWorldBackground(drawList);

        // Display rendered UI texture
        displayUITexture(drawList);

        // Draw selection borders
        gizmoDrawer.drawSelectionBorders(drawList, scene);

        // Draw selection gizmos (handles, anchor, pivot)
        gizmoDrawer.drawSelectionGizmos(drawList, scene);

        // Draw snap guides
        gizmoDrawer.drawSnapGuides(drawList);

        // Pop clip rect (matches pushClipRect at start of viewport rendering)
        drawList.popClipRect();

        // Handle input
        if (isHovered || state.isAnyDragActive()) {
            inputHandler.handleInput();
        }
    }

    /**
     * Renders UI elements to texture using unified RenderPipeline.
     * This uses the same rendering path as GameViewPanel and runtime.
     */
    private void renderUIToTexture(EditorScene scene) {
        if (pipeline == null || framebuffer == null) return;

        // Get UICanvases via cached bridge (O(1) when hierarchy unchanged)
        List<UICanvas> uiCanvases = uiBridge.getUICanvases(scene);

        // Create render target
        FramebufferTarget target = new FramebufferTarget(framebuffer);

        // Build params - UI only (no scene, no post-fx)
        RenderParams params = RenderParams.builder()
                .renderables(List.of())
                .camera(null)  // UI doesn't need camera
                .clearColor(CLEAR_COLOR)
                .renderScene(false)
                .renderUI(!uiCanvases.isEmpty())
                .uiCanvases(uiCanvases)
                .renderPostFx(false)
                .renderOverlay(false)
                .build();

        // Execute pipeline
        pipeline.execute(target, params);
    }

    /**
     * Displays the rendered UI texture in the viewport.
     */
    private void displayUITexture(ImDrawList drawList) {
        if (framebuffer == null || framebuffer.getTextureId() == 0) return;

        float[] canvasBounds = coords.getCanvasScreenBounds();
        float left = state.getViewportX() + canvasBounds[0];
        float top = state.getViewportY() + canvasBounds[1];
        float right = state.getViewportX() + canvasBounds[2];
        float bottom = state.getViewportY() + canvasBounds[3];

        // Draw with flipped V coordinates (OpenGL texture origin is bottom-left)
        drawList.addImage(
                framebuffer.getTextureId(),
                left, top, right, bottom,
                0, 1, 1, 0  // Flipped V
        );
    }

    // ========================================================================
    // PUBLIC ACCESSORS
    // ========================================================================

    public float getViewportX() {
        return state.getViewportX();
    }

    public float getViewportY() {
        return state.getViewportY();
    }

    public float getViewportWidth() {
        return state.getViewportWidth();
    }

    public float getViewportHeight() {
        return state.getViewportHeight();
    }

    public boolean isHovered() {
        return state.isHovered();
    }

    public boolean isFocused() {
        return state.isFocused();
    }

    public void setSceneTextureId(int textureId) {
        renderer.setSceneTextureId(textureId);
    }

    public void resetCamera() {
        state.resetCamera();
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    public void destroy() {
        renderer.destroy();

        if (pipeline != null) {
            pipeline.destroy();
            pipeline = null;
        }

        if (framebuffer != null) {
            framebuffer.destroy();
            framebuffer = null;
        }

        uiBridge.clear();
        pipelineInitialized = false;
    }
}
