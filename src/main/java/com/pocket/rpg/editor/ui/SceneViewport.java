package com.pocket.rpg.editor.ui;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import com.pocket.rpg.editor.tools.*;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Scene viewport panel that displays the rendered scene.
 * <p>
 * Handles:
 * - Displaying framebuffer texture
 * - Camera controls (pan, zoom)
 * - Grid overlay
 * - Tool input routing
 * - Coordinate display
 * <p>
 * Controls:
 * - WASD/Arrow keys: Pan camera
 * - Middle mouse drag: Pan camera
 * - Scroll wheel: Zoom
 * - Home: Reset camera
 * - Left click: Tool primary action
 * - Right click: Tool secondary action
 */
public class SceneViewport {

    private final EditorCamera camera;
    private final EditorConfig config;

    // Framebuffer for scene rendering
    @Getter
    private EditorFramebuffer framebuffer;

    // Tool system
    @Setter
    private ToolManager toolManager;

    // Viewport state
    @Getter
    private float viewportX, viewportY;
    @Getter
    private float viewportWidth, viewportHeight;
    @Getter
    private boolean isHovered = false;
    @Getter
    private boolean isFocused = false;

    // Mouse state for camera dragging
    private boolean isDraggingCamera = false;

    // Current hovered tile
    @Getter
    private int hoveredTileX = Integer.MIN_VALUE;
    @Getter
    private int hoveredTileY = Integer.MIN_VALUE;

    // Grid settings
    @Setter
    @Getter
    private boolean showGrid = true;
    @Setter
    private boolean showCoordinates = true;

    // Track if the viewport image was actually rendered/visible on screen
    @Getter
    @Setter
    private boolean contentVisible = false;

    public SceneViewport(EditorCamera camera, EditorConfig config) {
        this.camera = camera;
        this.config = config;
    }

    /**
     * Initializes the framebuffer. Call after OpenGL context is ready.
     */
    public void init(int initialWidth, int initialHeight) {
        framebuffer = new EditorFramebuffer(initialWidth, initialHeight);
        framebuffer.init();
    }

    /**
     * Renders the viewport panel and handles input.
     * Call this each frame.
     *
     * @return true if viewport should receive input (is focused)
     */
    public boolean render() {
        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;

        ImGui.begin("Scene", flags);

        // Track focus state
        isFocused = ImGui.isWindowFocused();
        isHovered = ImGui.isWindowHovered();

        // Get available content region
        ImVec2 contentMin = ImGui.getWindowContentRegionMin();
        ImVec2 contentMax = ImGui.getWindowContentRegionMax();
        ImVec2 windowPos = ImGui.getWindowPos();

        viewportX = windowPos.x + contentMin.x;
        viewportY = windowPos.y + contentMin.y;
        viewportWidth = contentMax.x - contentMin.x;
        viewportHeight = contentMax.y - contentMin.y;

        updateToolViewportBounds(viewportX, viewportY, viewportWidth, viewportHeight);

        // Resize framebuffer if needed
        if (framebuffer != null && viewportWidth > 0 && viewportHeight > 0) {
            int newWidth = (int) viewportWidth;
            int newHeight = (int) viewportHeight;

            if (newWidth != framebuffer.getWidth() || newHeight != framebuffer.getHeight()) {
                framebuffer.resize(newWidth, newHeight);
                camera.setViewportSize(newWidth, newHeight);
            }
        }

        // Display framebuffer texture
        if (framebuffer != null && framebuffer.isInitialized()) {
            // Flip UVs vertically (OpenGL texture origin is bottom-left)
            ImGui.image(
                    framebuffer.getTextureId(),
                    viewportWidth,
                    viewportHeight,
                    0, 1,  // UV min (top-left in ImGui = bottom-left in texture)
                    1, 0   // UV max (bottom-right in ImGui = top-right in texture)
            );
        }

        // Update hovered tile
        updateHoveredTile();

        // Draw grid overlay on top
        if (showGrid) {
            drawGridOverlay();
        }

        // Draw coordinate info
        if (showCoordinates && isHovered) {
            drawCoordinateInfo();
        }

        // Handle input
        if (isHovered || isDraggingCamera) {
            handleInput();
        }

        ImGui.end();

        return isFocused;
    }

    /**
     * Renders just the viewport content (without ImGui window management).
     * Used when toolbar is rendered separately in the same window.
     */
    public void renderContent() {
        // Track focus state
        isFocused = ImGui.isWindowFocused();

        // Get available content region (after toolbar)
        ImVec2 contentMin = ImGui.getWindowContentRegionMin();
        ImVec2 contentMax = ImGui.getWindowContentRegionMax();
        ImVec2 windowPos = ImGui.getWindowPos();
        ImVec2 cursorPos = ImGui.getCursorPos();

        // Calculate viewport bounds accounting for cursor position (after toolbar)
        viewportX = windowPos.x + cursorPos.x;
        viewportY = windowPos.y + cursorPos.y;
        viewportWidth = contentMax.x - cursorPos.x;
        viewportHeight = contentMax.y - cursorPos.y;

        updateToolViewportBounds(viewportX, viewportY, viewportWidth, viewportHeight);

        // Resize framebuffer if needed
        if (framebuffer != null && viewportWidth > 0 && viewportHeight > 0) {
            int newWidth = (int) viewportWidth;
            int newHeight = (int) viewportHeight;

            if (newWidth != framebuffer.getWidth() || newHeight != framebuffer.getHeight()) {
                framebuffer.resize(newWidth, newHeight);
                camera.setViewportSize(newWidth, newHeight);
            }
        }

        // Display framebuffer texture
        if (framebuffer != null && framebuffer.isInitialized()) {
            ImGui.image(
                    framebuffer.getTextureId(),
                    viewportWidth,
                    viewportHeight,
                    0, 1,
                    1, 0
            );
            // Use ImGui's z-order aware hover detection
            isHovered = ImGui.isItemHovered();
        } else {
            isHovered = false;
        }

        // Update hovered tile
        updateHoveredTile();

        // Draw grid overlay on top
        if (showGrid) {
            drawGridOverlay();
        }

        // Draw coordinate info
        if (showCoordinates && isHovered) {
            drawCoordinateInfo();
        }

        // Handle input
        if (isHovered || isDraggingCamera) {
            handleInput();
        }
    }

    /**
     * Updates the currently hovered tile coordinates.
     */
    private void updateHoveredTile() {
        if (!isHovered) {
            hoveredTileX = Integer.MIN_VALUE;
            hoveredTileY = Integer.MIN_VALUE;
            return;
        }

        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;

        // Convert to world coordinates
        Vector3f worldPos = camera.screenToWorld(localX, localY);

        // Calculate tile coordinates
        hoveredTileX = (int) Math.floor(worldPos.x);
        hoveredTileY = (int) Math.floor(worldPos.y);
    }

    /**
     * Draws the grid overlay using ImGui draw lists.
     */
    private void drawGridOverlay() {
        var drawList = ImGui.getWindowDrawList();

        // Calculate visible bounds in world space
        Vector3f worldMinVec = camera.screenToWorld(0, viewportHeight);
        Vector3f worldMaxVec = camera.screenToWorld(viewportWidth, 0);

        float worldMinX = worldMinVec.x;
        float worldMinY = worldMinVec.y;
        float worldMaxX = worldMaxVec.x;
        float worldMaxY = worldMaxVec.y;

        // Determine grid spacing based on zoom
        float baseSpacing = 1.0f; // 1 world unit
        float pixelsPerUnit = viewportWidth / (worldMaxX - worldMinX);

        // Adaptive spacing: ensure grid lines are 20-100 pixels apart
        float spacing = baseSpacing;
        while (spacing * pixelsPerUnit < 20) spacing *= 5;
        while (spacing * pixelsPerUnit > 100) spacing /= 5;

        int majorInterval = 5;

        int minorColor = ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 0.3f);
        int majorColor = ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 0.5f);

        // Draw vertical lines
        float startX = (float) (Math.floor(worldMinX / spacing) * spacing);
        for (float worldX = startX; worldX <= worldMaxX; worldX += spacing) {
            Vector2f screenPos = camera.worldToScreen(worldX, 0);
            float screenX = viewportX + screenPos.x;

            boolean isMajor = Math.abs(worldX) < 0.001f ||
                    Math.abs(worldX % (spacing * majorInterval)) < 0.001f;

            drawList.addLine(
                    screenX, viewportY,
                    screenX, viewportY + viewportHeight,
                    isMajor ? majorColor : minorColor,
                    isMajor ? 1.5f : 1.0f
            );
        }

        // Draw horizontal lines
        float startY = (float) (Math.floor(worldMinY / spacing) * spacing);
        for (float worldY = startY; worldY <= worldMaxY; worldY += spacing) {
            Vector2f screenPos = camera.worldToScreen(0, worldY);
            float screenY = viewportY + screenPos.y;

            boolean isMajor = Math.abs(worldY) < 0.001f ||
                    Math.abs(worldY % (spacing * majorInterval)) < 0.001f;

            drawList.addLine(
                    viewportX, screenY,
                    viewportX + viewportWidth, screenY,
                    isMajor ? majorColor : minorColor,
                    isMajor ? 1.5f : 1.0f
            );
        }

        // Draw origin crosshair with colored axes
        Vector2f originScreen = camera.worldToScreen(0, 0);
        float originX = viewportX + originScreen.x;
        float originY = viewportY + originScreen.y;

        int xAxisColor = ImGui.colorConvertFloat4ToU32(0.8f, 0.2f, 0.2f, 0.8f);  // Red
        int yAxisColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 0.8f);  // Green

        // X axis (red horizontal line at Y=0)
        drawList.addLine(
                viewportX, originY,
                viewportX + viewportWidth, originY,
                xAxisColor, 2.0f
        );

        // Y axis (green vertical line at X=0)
        drawList.addLine(
                originX, viewportY,
                originX, viewportY + viewportHeight,
                yAxisColor, 2.0f
        );
    }

    /**
     * Draws coordinate information at the bottom of the viewport.
     */
    private void drawCoordinateInfo() {
        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;

        Vector3f worldPos = camera.screenToWorld(localX, localY);

        var drawList = ImGui.getWindowDrawList();
        float barHeight = 20;
        float barY = viewportY + viewportHeight - barHeight;

        // Background
        drawList.addRectFilled(
                viewportX, barY,
                viewportX + viewportWidth, viewportY + viewportHeight,
                ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 0.8f)
        );

        // Text
        String toolName = (toolManager != null && toolManager.getActiveTool() != null)
                ? toolManager.getActiveTool().getName()
                : "No Tool";

        String coordInfo = String.format("%s | World: (%.2f, %.2f) | Tile: (%d, %d) | Zoom: %.0f%%",
                toolName,
                worldPos.x, worldPos.y,
                hoveredTileX, hoveredTileY,
                camera.getZoom() * 100);

        drawList.addText(viewportX + 5, barY + 3,
                ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.9f, 1.0f),
                coordInfo);
    }

    /**
     * Handles viewport input: camera controls and tool routing.
     */
    private void handleInput() {
        handleCameraInput();
        handleToolInput();

        handleKeyboardShortcuts();
    }

    private void handleKeyboardShortcuts() {
        if (!isFocused) return;

        // Escape - clear tool selection
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            if (toolManager != null) {
                EditorTool tool = toolManager.getActiveTool();
                if (tool instanceof TileBrushTool brushTool) {
                    brushTool.setSelection(null);
                } else if (tool instanceof TileFillTool fillTool) {
                    fillTool.setSelection(null);
                } else if (tool instanceof TileRectangleTool rectTool) {
                    rectTool.setSelection(null);
                }
            }
        }
    }

    /**
     * Handles camera pan and zoom.
     */
    private void handleCameraInput() {
        // WASD/Arrow key panning (only when focused)
        ImVec2 mousePos = ImGui.getMousePos();
        float screenX = mousePos.x - viewportX;
        float screenY = mousePos.y - viewportY;
        if (isFocused && !ImGui.getIO().getWantTextInput()) {
            float dt = ImGui.getIO().getDeltaTime();


            // Keyboard pan (WASD / Arrow keys)
            float moveX = 0, moveY = 0;

            // WASD - always handle for camera movement
            if (ImGui.isKeyDown(ImGuiKey.W)) {
                moveY = 1;
            }
            if (ImGui.isKeyDown(ImGuiKey.S)) {
                moveY = -1;
            }
            if (ImGui.isKeyDown(ImGuiKey.A)) {
                moveX = -1;
            }
            if (ImGui.isKeyDown(ImGuiKey.D)) {
                moveX = 1;
            }

            // Arrow keys - only if ImGui nav isn't using them
            if (!ImGui.getIO().getWantCaptureKeyboard()) {
                if (ImGui.isKeyDown(ImGuiKey.UpArrow)) {
                    moveY = 1;
                }
                if (ImGui.isKeyDown(ImGuiKey.DownArrow)) {
                    moveY = -1;
                }
                if (ImGui.isKeyDown(ImGuiKey.LeftArrow)) {
                    moveX = -1;
                }
                if (ImGui.isKeyDown(ImGuiKey.RightArrow)) {
                    moveX = 1;
                }
            }

            if (moveX != 0 || moveY != 0) {
                camera.updateKeyboardPan(moveX, moveY, dt);
            }
        }

        // Middle mouse drag for panning
        if (ImGui.isMouseClicked(ImGuiMouseButton.Middle) && isHovered) {
            isDraggingCamera = true;
        }
        if (ImGui.isMouseReleased(ImGuiMouseButton.Middle)) {
            isDraggingCamera = false;
        }

        if (isDraggingCamera) {
            ImVec2 delta = ImGui.getMouseDragDelta(ImGuiMouseButton.Middle);
            if (delta.x != 0 || delta.y != 0) {
                float worldDeltaX = -delta.x / (camera.getZoom() * config.getPixelsPerUnit());
                float worldDeltaY = delta.y / (camera.getZoom() * config.getPixelsPerUnit());
                camera.updatePan(worldDeltaX, worldDeltaY);
                ImGui.resetMouseDragDelta(ImGuiMouseButton.Middle);
            }
        }

        // Scroll wheel zoom
        if (isHovered) {
            float scroll = ImGui.getIO().getMouseWheel();
            if (scroll != 0) {
                camera.zoomToward(screenX, screenY, scroll);
            }
        }
    }

    /**
     * Routes input to the active tool.
     */
    private void handleToolInput() {
        if (toolManager == null) return;

        // Don't handle tool input if popup/combo is open
        if (ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId)) {
            return;
        }

        if (!isDraggingCamera && isHovered) {
            // Left mouse button
            if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                toolManager.handleMouseDown(hoveredTileX, hoveredTileY, 0);
            }
            if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
                toolManager.handleMouseUp(hoveredTileX, hoveredTileY, 0);
            }

            // Right mouse button
            if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
                toolManager.handleMouseDown(hoveredTileX, hoveredTileY, 1);
            }
            if (ImGui.isMouseReleased(ImGuiMouseButton.Right)) {
                toolManager.handleMouseUp(hoveredTileX, hoveredTileY, 1);
            }

            if (ImGui.isItemHovered()) { // TODO: Is tool button hovered ?

            }

            // Mouse move (for drag and hover)
            toolManager.handleMouseMove(hoveredTileX, hoveredTileY);
        }
    }

    /**
     * Renders tool overlay after the main viewport render.
     */
    public void renderToolOverlay() {
        if (toolManager == null) return;

        // Don't render overlay if content is not visible (hidden tab)
        if (!contentVisible) return;

        // Don't render overlay if popup/combo is open
        if (ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId)) {
            return;
        }

        EditorTool activeTool = toolManager.getActiveTool();
        if (activeTool == null) return;

        // Update viewport bounds for tools that need it
        if (activeTool instanceof ViewportAwareTool vat) {
            vat.setViewportBounds(viewportX, viewportY, viewportWidth, viewportHeight);
        } else {
            // Legacy support
            updateToolViewportBounds(viewportX, viewportY, viewportWidth, viewportHeight);
        }

        activeTool.renderOverlay(camera, hoveredTileX, hoveredTileY);
    }

    /**
     * Updates viewport bounds for all viewport-aware tools.
     */
    private void updateToolViewportBounds(float x, float y, float w, float h) {
        if (toolManager == null) return;

        for (EditorTool tool : toolManager.getTools()) {
            // Tilemap tools
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
            }
            // Collision tools
            else if (tool instanceof CollisionBrushTool t) {
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

    /**
     * Returns viewport bounds as array [x, y, width, height].
     */
    public float[] getViewportBounds() {
        return new float[]{viewportX, viewportY, viewportWidth, viewportHeight};
    }

    /**
     * Cleans up resources.
     */
    public void destroy() {
        if (framebuffer != null) {
            framebuffer.destroy();
            framebuffer = null;
        }
    }
}