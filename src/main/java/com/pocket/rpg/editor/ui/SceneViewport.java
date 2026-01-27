package com.pocket.rpg.editor.ui;

import com.pocket.rpg.editor.assets.SceneViewportDropTarget;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.PlayModeStartedEvent;
import com.pocket.rpg.editor.events.PlayModeStoppedEvent;
import com.pocket.rpg.editor.gizmos.GizmoRenderer;
import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.*;
import com.pocket.rpg.editor.ui.viewport.*;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Scene viewport panel - orchestrates viewport rendering, grid, input, and tool overlays.
 */
public class SceneViewport {

    private final EditorCamera camera;

    private final ViewportRenderer renderer;
    private final GridOverlayRenderer gridRenderer = new GridOverlayRenderer();
    private final CoordinateDisplayRenderer coordRenderer = new CoordinateDisplayRenderer();
    private final GizmoRenderer gizmoRenderer = new GizmoRenderer();
    private final ViewportInputHandler inputHandler;

    @Getter
    private float viewportX, viewportY;
    @Getter
    private float viewportWidth, viewportHeight;
    @Getter
    private boolean isHovered = false;
    @Getter
    private boolean isFocused = false;

    @Getter
    private int hoveredTileX = Integer.MIN_VALUE;
    @Getter
    private int hoveredTileY = Integer.MIN_VALUE;

    @Setter
    private EditorScene scene;

    private boolean playModeActive = false;

    public SceneViewport(EditorCamera camera, EditorConfig config) {
        this.camera = camera;
        this.renderer = new ViewportRenderer(camera);
        this.inputHandler = new ViewportInputHandler(camera, config);

        // Subscribe to play mode events
        EditorEventBus.get().subscribe(PlayModeStartedEvent.class, e -> playModeActive = true);
        EditorEventBus.get().subscribe(PlayModeStoppedEvent.class, e -> playModeActive = false);
    }

    private boolean isPlayModeActive() {
        return playModeActive;
    }

    public void init(int initialWidth, int initialHeight) {
        renderer.init(initialWidth, initialHeight);
    }

    public void setToolManager(ToolManager toolManager) {
        inputHandler.setToolManager(toolManager);
        coordRenderer.setToolManager(toolManager);
    }

    public void setShowGrid(boolean show) {
        gridRenderer.setEnabled(show);
    }

    public boolean isShowGrid() {
        return gridRenderer.isEnabled();
    }

    public void setShowCoordinates(boolean show) {
        coordRenderer.setEnabled(show);
    }

    public void setShowGizmos(boolean show) {
        gizmoRenderer.setEnabled(show);
    }

    public boolean isShowGizmos() {
        return gizmoRenderer.isEnabled();
    }

    public void setContentVisible(boolean visible) {
        renderer.setContentVisible(visible);
    }

    public boolean isContentVisible() {
        return renderer.isContentVisible();
    }

    public EditorFramebuffer getFramebuffer() {
        return renderer.getFramebuffer();
    }

    /**
     * Renders the viewport panel and handles input.
     */
    public boolean render() {
        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;

        ImGui.begin("Scene", flags);

        isFocused = ImGui.isWindowFocused();
        isHovered = ImGui.isWindowHovered();

        calculateViewportBounds();
        renderer.render(viewportX, viewportY, viewportWidth, viewportHeight);
        updateHoveredTile();

        gridRenderer.render(camera, viewportX, viewportY, viewportWidth, viewportHeight);
        coordRenderer.render(camera, viewportX, viewportY, viewportWidth, viewportHeight,
                hoveredTileX, hoveredTileY, isHovered);

        if (isHovered || inputHandler.isDraggingCamera()) {
            inputHandler.handleInput(isHovered, isFocused, viewportX, viewportY,
                    hoveredTileX, hoveredTileY);
        }

        ImGui.end();

        return isFocused;
    }

    /**
     * Renders just the viewport content (without ImGui window management).
     */
    public void renderContent() {
        isFocused = ImGui.isWindowFocused();

        calculateViewportBoundsFromCursor();
        renderer.render(viewportX, viewportY, viewportWidth, viewportHeight);

        // FIX: Check hover IMMEDIATELY after image render, before any other ImGui calls
        isHovered = ImGui.isItemHovered();

        // Play mode overlay - blocks input and shows message
        if (isPlayModeActive()) {
            renderPlayModeOverlay();
            // Skip all input handling and drop target during play mode
            return;
        }

        if (scene != null) {
            SceneViewportDropTarget.handleDropTarget(camera, scene, viewportX, viewportY);
        }

        updateHoveredTile();

        gridRenderer.render(camera, viewportX, viewportY, viewportWidth, viewportHeight);
        coordRenderer.render(camera, viewportX, viewportY, viewportWidth, viewportHeight,
                hoveredTileX, hoveredTileY, isHovered);

        if (isHovered || inputHandler.isDraggingCamera()) {
            inputHandler.handleInput(isHovered, isFocused, viewportX, viewportY,
                    hoveredTileX, hoveredTileY);
        }
    }

    private void renderPlayModeOverlay() {
        ImDrawList drawList = ImGui.getWindowDrawList();

        // Dim overlay
        int dimColor = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 0.6f);
        drawList.addRectFilled(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight, dimColor);

        // Orange border (3px)
        int borderColor = ImGui.colorConvertFloat4ToU32(1f, 0.5f, 0.1f, 0.9f);
        drawList.addRect(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight,
                borderColor, 0, 0, 3.0f);

        // Center messages
        float centerX = viewportX + viewportWidth / 2;
        float centerY = viewportY + viewportHeight / 2;

        String message = MaterialIcons.PlayArrow + " PLAY MODE ACTIVE";
        String subMessage = "Use Game View to see runtime";

        ImVec2 textSize = new ImVec2();

        int textColor = ImGui.colorConvertFloat4ToU32(1f, 0.6f, 0.2f, 1f);
        ImGui.calcTextSize(textSize, message);
        drawList.addText(centerX - textSize.x / 2, centerY - 15, textColor, message);

        int subColor = ImGui.colorConvertFloat4ToU32(0.7f, 0.7f, 0.7f, 1f);
        ImGui.calcTextSize(textSize, subMessage);
        drawList.addText(centerX - textSize.x / 2, centerY + 10, subColor, subMessage);
    }

    /**
     * Renders tool overlay and gizmos after the main viewport render.
     */
    public void renderToolOverlay() {
        if (!renderer.isContentVisible()) return;
        if (isPlayModeActive()) return;
        if (ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId)) return;

        // Render gizmos for selected entities
        if (scene != null) {
            gizmoRenderer.render(scene, camera, viewportX, viewportY, viewportWidth, viewportHeight);
        }

        // Render tool overlay
        EditorTool activeTool = inputHandler.getToolManager() != null
                ? inputHandler.getToolManager().getActiveTool()
                : null;
        if (activeTool != null) {
            updateToolViewportBounds(activeTool);
            activeTool.renderOverlay(camera, hoveredTileX, hoveredTileY);
        }

        if (scene != null) {
            SceneViewportDropTarget.renderDragOverlay(
                    camera, viewportX, viewportY, viewportWidth, viewportHeight);
        }
    }

    private void calculateViewportBounds() {
        ImVec2 contentMin = ImGui.getWindowContentRegionMin();
        ImVec2 contentMax = ImGui.getWindowContentRegionMax();
        ImVec2 windowPos = ImGui.getWindowPos();

        viewportX = windowPos.x + contentMin.x;
        viewportY = windowPos.y + contentMin.y;
        viewportWidth = contentMax.x - contentMin.x;
        viewportHeight = contentMax.y - contentMin.y;
    }

    private void calculateViewportBoundsFromCursor() {
        ImVec2 contentMax = ImGui.getWindowContentRegionMax();
        ImVec2 windowPos = ImGui.getWindowPos();
        ImVec2 cursorPos = ImGui.getCursorPos();

        viewportX = windowPos.x + cursorPos.x;
        viewportY = windowPos.y + cursorPos.y;
        viewportWidth = contentMax.x - cursorPos.x;
        viewportHeight = contentMax.y - cursorPos.y;
    }

    private void updateHoveredTile() {
        if (!isHovered) {
            hoveredTileX = Integer.MIN_VALUE;
            hoveredTileY = Integer.MIN_VALUE;
            return;
        }

        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;

        Vector3f worldPos = camera.screenToWorld(localX, localY);

        hoveredTileX = (int) Math.floor(worldPos.x);
        hoveredTileY = (int) Math.floor(worldPos.y);
    }

    private void updateToolViewportBounds(EditorTool tool) {
        if (tool instanceof ViewportAwareTool vat) {
            vat.setViewportBounds(viewportX, viewportY, viewportWidth, viewportHeight);
        } else {
            // Legacy support for tools without ViewportAwareTool
            if (tool instanceof TileBrushTool t) {
                t.setViewportX(viewportX);
                t.setViewportY(viewportY);
                t.setViewportWidth(viewportWidth);
                t.setViewportHeight(viewportHeight);
            } else if (tool instanceof TileEraserTool t) {
                t.setViewportX(viewportX);
                t.setViewportY(viewportY);
                t.setViewportWidth(viewportWidth);
                t.setViewportHeight(viewportHeight);
            } else if (tool instanceof TileFillTool t) {
                t.setViewportX(viewportX);
                t.setViewportY(viewportY);
                t.setViewportWidth(viewportWidth);
                t.setViewportHeight(viewportHeight);
            } else if (tool instanceof TileRectangleTool t) {
                t.setViewportX(viewportX);
                t.setViewportY(viewportY);
                t.setViewportWidth(viewportWidth);
                t.setViewportHeight(viewportHeight);
            } else if (tool instanceof TilePickerTool t) {
                t.setViewportX(viewportX);
                t.setViewportY(viewportY);
                t.setViewportWidth(viewportWidth);
                t.setViewportHeight(viewportHeight);
            } else if (tool instanceof CollisionBrushTool t) {
                t.setViewportX(viewportX);
                t.setViewportY(viewportY);
                t.setViewportWidth(viewportWidth);
                t.setViewportHeight(viewportHeight);
            } else if (tool instanceof CollisionEraserTool t) {
                t.setViewportX(viewportX);
                t.setViewportY(viewportY);
                t.setViewportWidth(viewportWidth);
                t.setViewportHeight(viewportHeight);
            } else if (tool instanceof CollisionFillTool t) {
                t.setViewportX(viewportX);
                t.setViewportY(viewportY);
                t.setViewportWidth(viewportWidth);
                t.setViewportHeight(viewportHeight);
            } else if (tool instanceof CollisionRectangleTool t) {
                t.setViewportX(viewportX);
                t.setViewportY(viewportY);
                t.setViewportWidth(viewportWidth);
                t.setViewportHeight(viewportHeight);
            } else if (tool instanceof CollisionPickerTool t) {
                t.setViewportX(viewportX);
                t.setViewportY(viewportY);
                t.setViewportWidth(viewportWidth);
                t.setViewportHeight(viewportHeight);
            }
        }
    }

    public float[] getViewportBounds() {
        return new float[]{viewportX, viewportY, viewportWidth, viewportHeight};
    }

    /**
     * Forces the viewport to recalculate.
     * Call when the window moves between monitors.
     */
    public void invalidate() {
        renderer.invalidate();
    }

    public void destroy() {
        renderer.destroy();
    }
}
