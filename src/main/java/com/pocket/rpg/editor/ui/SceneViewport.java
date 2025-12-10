package com.pocket.rpg.editor.ui;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiWindowFlags;
import org.joml.Vector2f;

/**
 * Scene viewport panel that displays the rendered scene.
 *
 * Phase 2 update: Now renders the scene to a framebuffer and displays
 * the resulting texture in ImGui. Grid overlay is drawn using ImGui draw lists.
 *
 * Controls:
 * - WASD/Arrow keys: Pan camera
 * - Middle mouse drag: Pan camera
 * - Scroll wheel: Zoom
 * - Home: Reset camera
 */
public class SceneViewport {

    private final EditorCamera camera;
    private final EditorConfig config;

    // Framebuffer for scene rendering
    private EditorFramebuffer framebuffer;

    // Viewport state
    private float viewportX, viewportY;
    private float viewportWidth, viewportHeight;
    private boolean isHovered = false;
    private boolean isFocused = false;

    // Mouse state for dragging
    private boolean isDragging = false;
    private float lastMouseX, lastMouseY;

    // Grid settings
    private boolean showGrid = true;
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

        // Draw grid overlay on top
        if (showGrid) {
            drawGridOverlay();
        }

        // Draw coordinate info
        if (showCoordinates && isHovered) {
            drawCoordinateInfo();
        }

        // Handle input
        if (isHovered || isDragging) {
            handleInput();
        }

        ImGui.end();

        return isFocused;
    }

    /**
     * Draws the grid overlay using ImGui draw lists.
     */
    private void drawGridOverlay() {
        var drawList = ImGui.getWindowDrawList();

        // Calculate visible bounds in world space
        Vector2f worldMin = camera.screenToWorld(0, viewportHeight);
        Vector2f worldMax = camera.screenToWorld(viewportWidth, 0);

        // Determine grid spacing based on zoom
        float baseSpacing = 1.0f; // 1 world unit
        float pixelsPerUnit = viewportWidth / (worldMax.x - worldMin.x);

        // Adaptive spacing: ensure grid lines are 20-100 pixels apart
        float spacing = baseSpacing;
        while (spacing * pixelsPerUnit < 20) spacing *= 5;
        while (spacing * pixelsPerUnit > 100) spacing /= 5;

        int majorInterval = 5;

        // Grid colors
        int minorColor = ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 0.3f);
        int majorColor = ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 0.5f);

        // Draw vertical lines
        float startX = (float) (Math.floor(worldMin.x / spacing) * spacing);
        for (float worldX = startX; worldX <= worldMax.x; worldX += spacing) {
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
        float startY = (float) (Math.floor(worldMin.y / spacing) * spacing);
        for (float worldY = startY; worldY <= worldMax.y; worldY += spacing) {
            Vector2f screenPos = camera.worldToScreen(0, worldY);
            float screenY = viewportY + viewportHeight - screenPos.y; // Flip Y

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
        float originY = viewportY + viewportHeight - originScreen.y;

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
        Vector2f worldPos = camera.screenToWorld(localX, localY);

        // Calculate tile coordinates
        int tileX = (int) Math.floor(worldPos.x);
        int tileY = (int) Math.floor(worldPos.y);

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
        String coordText = String.format("World: (%.2f, %.2f)  Tile: (%d, %d)  Zoom: %.0f%%",
                worldPos.x, worldPos.y, tileX, tileY, camera.getZoom() * 100);

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

        // Keyboard pan (WASD / Arrow keys)
        float panSpeed = config.getCameraPanSpeed() / camera.getZoom();

        if (ImGui.isKeyDown(ImGuiKey.W) || ImGui.isKeyDown(ImGuiKey.UpArrow)) {
            camera.pan(0, panSpeed * deltaTime);
        }
        if (ImGui.isKeyDown(ImGuiKey.S) || ImGui.isKeyDown(ImGuiKey.DownArrow)) {
            camera.pan(0, -panSpeed * deltaTime);
        }
        if (ImGui.isKeyDown(ImGuiKey.A) || ImGui.isKeyDown(ImGuiKey.LeftArrow)) {
            camera.pan(-panSpeed * deltaTime, 0);
        }
        if (ImGui.isKeyDown(ImGuiKey.D) || ImGui.isKeyDown(ImGuiKey.RightArrow)) {
            camera.pan(panSpeed * deltaTime, 0);
        }

        // Home key - reset camera
        if (ImGui.isKeyPressed(ImGuiKey.Home)) {
            camera.reset();
        }

        // Middle mouse drag to pan
        ImVec2 mousePos = ImGui.getMousePos();

        if (ImGui.isMouseClicked(2)) { // Middle button
            isDragging = true;
            lastMouseX = mousePos.x;
            lastMouseY = mousePos.y;
        }

        if (ImGui.isMouseReleased(2)) {
            isDragging = false;
        }

        if (isDragging && ImGui.isMouseDown(2)) {
            float deltaX = mousePos.x - lastMouseX;
            float deltaY = mousePos.y - lastMouseY;

            // Convert screen delta to world delta
            float worldDeltaX = -deltaX / (viewportWidth / (camera.getVisibleWidth()));
            float worldDeltaY = deltaY / (viewportHeight / (camera.getVisibleHeight()));

            camera.pan(worldDeltaX, worldDeltaY);

            lastMouseX = mousePos.x;
            lastMouseY = mousePos.y;
        }

        // Scroll wheel to zoom
        float scroll = ImGui.getIO().getMouseWheel();
        if (scroll != 0 && isHovered) {
            // Zoom toward mouse position
            float localX = mousePos.x - viewportX;
            float localY = mousePos.y - viewportY;

            camera.zoomToward(scroll * 0.1f, localX, localY);
        }
    }

    // ========================================================================
    // GETTERS / SETTERS
    // ========================================================================

    public boolean isHovered() {
        return isHovered;
    }

    public boolean isFocused() {
        return isFocused;
    }

    public float getViewportWidth() {
        return viewportWidth;
    }

    public float getViewportHeight() {
        return viewportHeight;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowCoordinates(boolean showCoordinates) {
        this.showCoordinates = showCoordinates;
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
