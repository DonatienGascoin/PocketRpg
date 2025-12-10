package com.pocket.rpg.editor.ui;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import com.pocket.rpg.editor.tools.ToolManager;
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
 *
 * Handles:
 * - Displaying framebuffer texture
 * - Camera controls (pan, zoom)
 * - Grid overlay
 * - Tool input routing
 * - Coordinate display
 *
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
    private boolean showGrid = true;
    @Setter
    private boolean showCoordinates = true;

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
     * Returns the framebuffer for scene rendering.
     */
    public EditorFramebuffer getFramebuffer() {
        return framebuffer;
    }

    /**
     * Renders the viewport panel and handles input.
     * Call this each frame.
     *
     * @return true if viewport should receive input (is focused)
     */
    public boolean render() {
        // Window flags: no scrollbar, no collapse
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

        // Grid colors
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

        // Draw origin crosshair
        Vector2f originScreen = camera.worldToScreen(0, 0);
        float originX = viewportX + originScreen.x;
        float originY = viewportY + originScreen.y;

        int xAxisColor = ImGui.colorConvertFloat4ToU32(0.8f, 0.2f, 0.2f, 0.8f);
        int yAxisColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 0.8f);

        // X axis (red)
        drawList.addLine(
                viewportX, originY,
                viewportX + viewportWidth, originY,
                xAxisColor, 2.0f
        );

        // Y axis (green)
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
        // Get mouse position in viewport
        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;

        // Convert to world coordinates
        Vector3f worldPos = camera.screenToWorld(localX, localY);

        // Draw info bar at bottom
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
            ? toolManager.getActiveTool().getName() : "None";
        
        String coordText = String.format("Tool: %s | World: (%.2f, %.2f) | Tile: (%d, %d) | Zoom: %.0f%%",
                toolName, worldPos.x, worldPos.y, hoveredTileX, hoveredTileY, camera.getZoom() * 100);

        drawList.addText(
                viewportX + 5, barY + 3,
                ImGui.colorConvertFloat4ToU32(0.8f, 0.8f, 0.8f, 1.0f),
                coordText
        );
    }

    /**
     * Handles viewport input.
     */
    private void handleInput() {
        float deltaTime = ImGui.getIO().getDeltaTime();
        
        ImVec2 mousePos = ImGui.getMousePos();
        float screenX = mousePos.x - viewportX;
        float screenY = mousePos.y - viewportY;

        // =====================================================================
        // CAMERA CONTROLS
        // =====================================================================
        
        // Keyboard pan (WASD / Arrow keys)
        float moveX = 0, moveY = 0;
        
        if (ImGui.isKeyDown(ImGuiKey.W) || ImGui.isKeyDown(ImGuiKey.UpArrow)) {
            moveY = 1;
        }
        if (ImGui.isKeyDown(ImGuiKey.S) || ImGui.isKeyDown(ImGuiKey.DownArrow)) {
            moveY = -1;
        }
        if (ImGui.isKeyDown(ImGuiKey.A) || ImGui.isKeyDown(ImGuiKey.LeftArrow)) {
            moveX = -1;
        }
        if (ImGui.isKeyDown(ImGuiKey.D) || ImGui.isKeyDown(ImGuiKey.RightArrow)) {
            moveX = 1;
        }
        
        if (moveX != 0 || moveY != 0) {
            camera.updateKeyboardPan(moveX, moveY, deltaTime);
        }

        // Home key - reset camera
        if (ImGui.isKeyPressed(ImGuiKey.Home)) {
            camera.reset();
        }

        // Middle mouse drag to pan camera
        if (ImGui.isMouseClicked(ImGuiMouseButton.Middle)) {
            isDraggingCamera = true;
            camera.startPan(screenX, screenY);
        }

        if (ImGui.isMouseReleased(ImGuiMouseButton.Middle)) {
            isDraggingCamera = false;
            camera.endPan();
        }

        if (isDraggingCamera && ImGui.isMouseDown(ImGuiMouseButton.Middle)) {
            camera.updatePan(screenX, screenY);
        }

        // Scroll wheel to zoom
        float scroll = ImGui.getIO().getMouseWheel();
        if (scroll != 0 && isHovered) {
            camera.zoomToward(screenX, screenY, scroll * 0.1f);
        }
        
        // =====================================================================
        // TOOL INPUT (only when not camera dragging)
        // =====================================================================
        
        if (toolManager != null && !isDraggingCamera && isHovered) {
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
            
            // Mouse move (for drag and hover)
            toolManager.handleMouseMove(hoveredTileX, hoveredTileY);
        }
    }
    
    /**
     * Renders tool overlays (call after scene rendering).
     */
    public void renderToolOverlay() {
        if (toolManager != null && isHovered) {
            // Pass viewport position to tools for overlay rendering
            if (toolManager.getActiveTool() instanceof com.pocket.rpg.editor.tools.TileBrushTool brush) {
                brush.setViewportX(viewportX);
                brush.setViewportY(viewportY);
            }
            if (toolManager.getActiveTool() instanceof com.pocket.rpg.editor.tools.TileEraserTool eraser) {
                eraser.setViewportX(viewportX);
                eraser.setViewportY(viewportY);
            }
            
            toolManager.renderOverlay(camera, hoveredTileX, hoveredTileY);
        }
    }

    // ========================================================================
    // GETTERS / SETTERS
    // ========================================================================

    public boolean isShowGrid() {
        return showGrid;
    }

    public boolean isShowCoordinates() {
        return showCoordinates;
    }

    /**
     * Destroys viewport resources.
     */
    public void destroy() {
        if (framebuffer != null) {
            framebuffer.destroy();
            framebuffer = null;
        }
    }
}
