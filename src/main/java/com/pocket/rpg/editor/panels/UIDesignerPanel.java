package com.pocket.rpg.editor.panels;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.ToolManager;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Map;

/**
 * UI Designer panel - visual editor for UI elements.
 * <p>
 * Features:
 * - Canvas bounds rectangle showing game resolution
 * - Background toggle: World (scene behind) or Gray (neutral)
 * - Selection, move, resize of UI elements
 * - Separate camera from Scene viewport
 */
public class UIDesignerPanel {

    private final EditorContext context;
    private final GameConfig gameConfig;

    // Viewport state
    @Getter
    private float viewportX, viewportY;
    @Getter
    private float viewportWidth, viewportHeight;
    @Getter
    private boolean isHovered = false;
    @Getter
    private boolean isFocused = false;

    // Camera for UI viewport (separate from scene camera)
    private float cameraX = 0;
    private float cameraY = 0;
    private float zoom = 1.0f;

    // Background mode
    public enum BackgroundMode { WORLD, GRAY }
    @Getter @Setter
    private BackgroundMode backgroundMode = BackgroundMode.GRAY;

    // Dragging state
    private boolean isDraggingCamera = false;
    private boolean isDraggingElement = false;
    private EditorEntity draggedEntity = null;
    private float dragStartX, dragStartY;
    private float entityStartOffsetX, entityStartOffsetY;

    // Selection
    @Setter
    private ToolManager toolManager;

    // Canvas framebuffer texture (from scene renderer, for WORLD mode)
    @Setter
    private int sceneTextureId = 0;

    public UIDesignerPanel(EditorContext context) {
        this.context = context;
        this.gameConfig = context.getGameConfig();
    }

    /**
     * Renders the UI Designer panel.
     */
    public void render() {
        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;

        boolean visible = ImGui.begin("UI Designer", flags);

        if (!visible) {
            ImGui.end();
            return;
        }

        isFocused = ImGui.isWindowFocused();

        renderToolbar();
        ImGui.separator();

        renderViewport();

        ImGui.end();
    }

    private void renderToolbar() {
        // Background mode toggle
        ImGui.text("Background:");
        ImGui.sameLine();

        boolean isWorld = backgroundMode == BackgroundMode.WORLD;
        if (ImGui.radioButton("World", isWorld)) {
            backgroundMode = BackgroundMode.WORLD;
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Gray", !isWorld)) {
            backgroundMode = BackgroundMode.GRAY;
        }

        ImGui.sameLine();
        ImGui.spacing();
        ImGui.sameLine();

        // Zoom display
        ImGui.text(String.format("Zoom: %.0f%%", zoom * 100));
        ImGui.sameLine();

        if (ImGui.button("Reset")) {
            resetCamera();
        }

        ImGui.sameLine();
        ImGui.spacing();
        ImGui.sameLine();

        // Canvas size info
        ImGui.textDisabled(String.format("Canvas: %dx%d",
                gameConfig.getGameWidth(), gameConfig.getGameHeight()));
    }

    private void renderViewport() {
        // Get content region
        ImVec2 contentMax = ImGui.getWindowContentRegionMax();
        ImVec2 windowPos = ImGui.getWindowPos();
        ImVec2 cursorPos = ImGui.getCursorPos();

        viewportX = windowPos.x + cursorPos.x;
        viewportY = windowPos.y + cursorPos.y;
        viewportWidth = contentMax.x - cursorPos.x;
        viewportHeight = contentMax.y - cursorPos.y;

        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }

        // Draw background
        var drawList = ImGui.getWindowDrawList();

        if (backgroundMode == BackgroundMode.GRAY) {
            // Gray background
            drawList.addRectFilled(
                    viewportX, viewportY,
                    viewportX + viewportWidth, viewportY + viewportHeight,
                    ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1.0f)
            );
        } else if (sceneTextureId != 0) {
            // Scene as background (render texture)
            ImGui.image(sceneTextureId, viewportWidth, viewportHeight, 0, 1, 1, 0);
            // Semi-transparent overlay
            drawList.addRectFilled(
                    viewportX, viewportY,
                    viewportX + viewportWidth, viewportY + viewportHeight,
                    ImGui.colorConvertFloat4ToU32(0.0f, 0.0f, 0.0f, 0.3f)
            );
        }

        // Draw canvas bounds
        drawCanvasBounds(drawList);

        // Draw UI elements
        drawUIElements(drawList);

        // Draw selection gizmos
        drawSelectionGizmos(drawList);

        // Invisible button for input handling
        ImGui.setCursorPos(cursorPos.x, cursorPos.y);
        ImGui.invisibleButton("ui_viewport", viewportWidth, viewportHeight);
        isHovered = ImGui.isItemHovered();

        // Handle input
        if (isHovered || isDraggingCamera || isDraggingElement) {
            handleInput();
        }
    }

    private void drawCanvasBounds(imgui.ImDrawList drawList) {
        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

        // Convert canvas corners to screen space
        Vector2f topLeft = canvasToScreen(0, 0);
        Vector2f bottomRight = canvasToScreen(canvasWidth, canvasHeight);

        float left = viewportX + topLeft.x;
        float top = viewportY + topLeft.y;
        float right = viewportX + bottomRight.x;
        float bottom = viewportY + bottomRight.y;

        // Canvas area (slightly lighter gray)
        drawList.addRectFilled(left, top, right, bottom,
                ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1.0f));

        // Canvas border
        drawList.addRect(left, top, right, bottom,
                ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1.0f), 0, 0, 2.0f);

        // Resolution label
        String label = gameConfig.getGameWidth() + " x " + gameConfig.getGameHeight();
        drawList.addText(left + 5, top + 5,
                ImGui.colorConvertFloat4ToU32(0.6f, 0.6f, 0.6f, 1.0f), label);
    }

    private void drawUIElements(imgui.ImDrawList drawList) {
        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        // Find all entities with UICanvas or UITransform
        for (EditorEntity entity : scene.getEntities()) {
            if (!isUIEntity(entity)) continue;

            drawUIElement(drawList, entity);
        }
    }

    private void drawUIElement(imgui.ImDrawList drawList, EditorEntity entity) {
        var transformComp = entity.getComponentByType("UITransform");
        if (transformComp == null && !entity.hasComponent("UICanvas")) return;

        // Canvas itself doesn't render
        if (entity.hasComponent("UICanvas")) return;

        if (transformComp == null) return;

        Map<String, Object> fields = transformComp.getFields();

        // Read UITransform fields
        float width = getFloat(fields, "width", 100);
        float height = getFloat(fields, "height", 100);
        Vector2f offset = getVector2f(fields, "offset", 0, 0);
        Vector2f anchor = getVector2f(fields, "anchor", 0, 0);
        Vector2f pivot = getVector2f(fields, "pivot", 0, 0);

        // Calculate position relative to canvas
        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

        // Get parent bounds
        float parentWidth = canvasWidth;
        float parentHeight = canvasHeight;
        float parentX = 0;
        float parentY = 0;

        // Check if has parent UITransform
        EditorEntity parent = findParentWithUITransform(entity);
        if (parent != null) {
            var parentTransform = parent.getComponentByType("UITransform");
            if (parentTransform != null) {
                Map<String, Object> pFields = parentTransform.getFields();
                parentWidth = getFloat(pFields, "width", canvasWidth);
                parentHeight = getFloat(pFields, "height", canvasHeight);

                // Get parent's calculated position
                float[] parentBounds = calculateElementBounds(parent);
                parentX = parentBounds[0];
                parentY = parentBounds[1];
            }
        }

        float anchorX = anchor.x * parentWidth;
        float anchorY = anchor.y * parentHeight;

        float x = parentX + anchorX + offset.x - pivot.x * width;
        float y = parentY + anchorY + offset.y - pivot.y * height;

        // Convert to screen
        Vector2f screenPos = canvasToScreen(x, y);
        Vector2f screenEnd = canvasToScreen(x + width, y + height);

        float left = viewportX + screenPos.x;
        float top = viewportY + screenPos.y;
        float right = viewportX + screenEnd.x;
        float bottom = viewportY + screenEnd.y;

        EditorScene scene = context.getCurrentScene();
        boolean selected = scene != null && scene.isSelected(entity);

        // Try to render sprite content
        boolean contentRendered = false;

        // UIImage - render sprite
        if (entity.hasComponent("UIImage")) {
            var imageComp = entity.getComponentByType("UIImage");
            if (imageComp != null) {
                contentRendered = renderSprite(drawList, imageComp.getFields().get("sprite"),
                        imageComp.getFields().get("color"), left, top, right, bottom);
            }
        }

        // UIButton - render sprite
        if (!contentRendered && entity.hasComponent("UIButton")) {
            var buttonComp = entity.getComponentByType("UIButton");
            if (buttonComp != null) {
                contentRendered = renderSprite(drawList, buttonComp.getFields().get("sprite"),
                        buttonComp.getFields().get("color"), left, top, right, bottom);
            }
        }

        // UIPanel - render background
        if (!contentRendered && entity.hasComponent("UIPanel")) {
            var panelComp = entity.getComponentByType("UIPanel");
            if (panelComp != null) {
                contentRendered = renderSprite(drawList, panelComp.getFields().get("backgroundSprite"),
                        panelComp.getFields().get("backgroundColor"), left, top, right, bottom);
            }
        }

        // Fallback: colored rectangle
        if (!contentRendered) {
            int fillColor = getElementFillColor(entity);
            if (fillColor != 0) {
                drawList.addRectFilled(left, top, right, bottom, fillColor);
            }
        }

        // Border
        int borderColor = selected
                ? ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 1.0f, 1.0f)
                : ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 0.8f);
        drawList.addRect(left, top, right, bottom, borderColor, 0, 0, selected ? 2.0f : 1.0f);

        // Element name
        drawList.addText(left + 2, top + 2,
                ImGui.colorConvertFloat4ToU32(0.8f, 0.8f, 0.8f, 1.0f),
                entity.getName());
    }

    private boolean renderSprite(imgui.ImDrawList drawList, Object spriteObj, Object colorObj,
                                  float left, float top, float right, float bottom) {
        if (spriteObj == null) return false;

        // Handle direct Sprite object
        if (spriteObj instanceof Sprite sprite) {
            if (sprite.getTexture() == null) return false;

            int textureId = sprite.getTexture().getTextureId();
            float u0 = sprite.getU0();
            float v0 = sprite.getV0();
            float u1 = sprite.getU1();
            float v1 = sprite.getV1();

            int tintColor = parseColor(colorObj);
            drawList.addImage(textureId, left, top, right, bottom, u0, v0, u1, v1, tintColor);
            return true;
        }

        // Handle string path (asset reference)
        if (spriteObj instanceof String spritePath && !spritePath.isEmpty()) {
            Sprite sprite = Assets.load(spritePath);
            if (sprite != null && sprite.getTexture() != null) {
                int textureId = sprite.getTexture().getTextureId();
                int tintColor = parseColor(colorObj);
                drawList.addImage(textureId, left, top, right, bottom,
                        sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), tintColor);
                return true;
            }
        }

        return false;
    }

    private int parseColor(Object colorObj) {
        if (colorObj instanceof Vector4f v) {
            return ImGui.colorConvertFloat4ToU32(v.x, v.y, v.z, v.w);
        }
        if (colorObj instanceof Map<?, ?> colorMap) {
            float r = getFloatFromMap(colorMap, "x", 1f);
            float g = getFloatFromMap(colorMap, "y", 1f);
            float b = getFloatFromMap(colorMap, "z", 1f);
            float a = getFloatFromMap(colorMap, "w", 1f);
            return ImGui.colorConvertFloat4ToU32(r, g, b, a);
        }
        return ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f);
    }

    private int getElementFillColor(EditorEntity entity) {
        if (entity.hasComponent("UIPanel")) {
            return ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.4f, 0.6f);
        } else if (entity.hasComponent("UIButton")) {
            return ImGui.colorConvertFloat4ToU32(0.3f, 0.5f, 0.3f, 0.6f);
        } else if (entity.hasComponent("UIImage")) {
            return ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.3f, 0.6f);
        } else if (entity.hasComponent("UIText")) {
            return ImGui.colorConvertFloat4ToU32(0.4f, 0.3f, 0.4f, 0.6f);
        } else {
            return ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 0.4f);
        }
    }

    private void drawSelectionGizmos(imgui.ImDrawList drawList) {
        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        for (EditorEntity entity : scene.getSelectedEntities()) {
            if (!isUIEntity(entity)) continue;
            if (entity.hasComponent("UICanvas")) continue;

            float[] bounds = calculateElementBounds(entity);
            if (bounds == null) continue;

            float x = bounds[0];
            float y = bounds[1];
            float width = bounds[2];
            float height = bounds[3];

            Vector2f screenPos = canvasToScreen(x, y);
            Vector2f screenEnd = canvasToScreen(x + width, y + height);

            float left = viewportX + screenPos.x;
            float top = viewportY + screenPos.y;
            float right = viewportX + screenEnd.x;
            float bottom = viewportY + screenEnd.y;

            int handleColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 1.0f);
            float handleSize = 6;

            // Corner handles
            drawHandle(drawList, left, top, handleSize, handleColor);
            drawHandle(drawList, right, top, handleSize, handleColor);
            drawHandle(drawList, left, bottom, handleSize, handleColor);
            drawHandle(drawList, right, bottom, handleSize, handleColor);

            // Edge handles
            drawHandle(drawList, (left + right) / 2, top, handleSize, handleColor);
            drawHandle(drawList, (left + right) / 2, bottom, handleSize, handleColor);
            drawHandle(drawList, left, (top + bottom) / 2, handleSize, handleColor);
            drawHandle(drawList, right, (top + bottom) / 2, handleSize, handleColor);
        }
    }

    private void drawHandle(imgui.ImDrawList drawList, float x, float y, float size, int color) {
        float half = size / 2;
        drawList.addRectFilled(x - half, y - half, x + half, y + half, color);
        drawList.addRect(x - half, y - half, x + half, y + half,
                ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1.0f));
    }

    private void handleInput() {
        // Middle mouse drag for camera pan
        if (ImGui.isMouseClicked(ImGuiMouseButton.Middle) && isHovered) {
            isDraggingCamera = true;
        }
        if (ImGui.isMouseReleased(ImGuiMouseButton.Middle)) {
            isDraggingCamera = false;
        }

        if (isDraggingCamera) {
            ImVec2 delta = ImGui.getMouseDragDelta(ImGuiMouseButton.Middle);
            if (delta.x != 0 || delta.y != 0) {
                cameraX -= delta.x / zoom;
                cameraY -= delta.y / zoom;
                ImGui.resetMouseDragDelta(ImGuiMouseButton.Middle);
            }
        }

        // Scroll wheel zoom
        if (isHovered) {
            float scroll = ImGui.getIO().getMouseWheel();
            if (scroll != 0) {
                float zoomFactor = 1.0f + scroll * 0.1f;
                zoom = Math.max(0.1f, Math.min(5.0f, zoom * zoomFactor));
            }
        }

        // Left click for selection
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && isHovered) {
            handleClick();
        }

        // Left drag for moving
        if (ImGui.isMouseDragging(ImGuiMouseButton.Left) && isDraggingElement && draggedEntity != null) {
            handleDrag();
        }

        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            isDraggingElement = false;
            draggedEntity = null;
        }
    }

    private void handleClick() {
        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;

        Vector2f canvasPos = screenToCanvas(localX, localY);

        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        // Find clicked element (reverse order = top first)
        EditorEntity clicked = null;
        var entities = scene.getEntities();
        for (int i = entities.size() - 1; i >= 0; i--) {
            EditorEntity entity = entities.get(i);
            if (!isUIEntity(entity)) continue;
            if (entity.hasComponent("UICanvas")) continue;

            if (hitTest(entity, canvasPos.x, canvasPos.y)) {
                clicked = entity;
                break;
            }
        }

        boolean shift = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);
        boolean ctrl = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);

        if (clicked != null) {
            if (ctrl) {
                scene.toggleSelection(clicked);
            } else if (shift) {
                scene.addToSelection(clicked);
            } else {
                scene.setSelectedEntity(clicked);
            }

            // Start drag
            isDraggingElement = true;
            draggedEntity = clicked;
            dragStartX = canvasPos.x;
            dragStartY = canvasPos.y;

            var transform = clicked.getComponentByType("UITransform");
            if (transform != null) {
                Vector2f offset = getVector2f(transform.getFields(), "offset", 0, 0);
                entityStartOffsetX = offset.x;
                entityStartOffsetY = offset.y;
            }
        } else {
            if (!shift && !ctrl) {
                scene.clearSelection();
            }
        }
    }

    private void handleDrag() {
        if (draggedEntity == null) return;

        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;

        Vector2f canvasPos = screenToCanvas(localX, localY);
        float deltaX = canvasPos.x - dragStartX;
        float deltaY = canvasPos.y - dragStartY;

        var transform = draggedEntity.getComponentByType("UITransform");
        if (transform != null) {
            Map<String, Object> fields = transform.getFields();

            // Write as Vector2f directly so Inspector updates
            Vector2f newOffset = new Vector2f(entityStartOffsetX + deltaX, entityStartOffsetY + deltaY);
            fields.put("offset", newOffset);

            EditorScene scene = context.getCurrentScene();
            if (scene != null) {
                scene.markDirty();
            }
        }
    }

    private boolean hitTest(EditorEntity entity, float canvasX, float canvasY) {
        float[] bounds = calculateElementBounds(entity);
        if (bounds == null) return false;

        float x = bounds[0];
        float y = bounds[1];
        float width = bounds[2];
        float height = bounds[3];

        return canvasX >= x && canvasX <= x + width &&
                canvasY >= y && canvasY <= y + height;
    }

    /**
     * Calculates element bounds in canvas space.
     * @return float[4]: {x, y, width, height} or null if no transform
     */
    private float[] calculateElementBounds(EditorEntity entity) {
        var transformComp = entity.getComponentByType("UITransform");
        if (transformComp == null) return null;

        Map<String, Object> fields = transformComp.getFields();
        float width = getFloat(fields, "width", 100);
        float height = getFloat(fields, "height", 100);
        Vector2f offset = getVector2f(fields, "offset", 0, 0);
        Vector2f anchor = getVector2f(fields, "anchor", 0, 0);
        Vector2f pivot = getVector2f(fields, "pivot", 0, 0);

        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

        // Get parent bounds
        float parentWidth = canvasWidth;
        float parentHeight = canvasHeight;
        float parentX = 0;
        float parentY = 0;

        EditorEntity parent = findParentWithUITransform(entity);
        if (parent != null) {
            float[] parentBounds = calculateElementBounds(parent);
            if (parentBounds != null) {
                parentX = parentBounds[0];
                parentY = parentBounds[1];
                parentWidth = parentBounds[2];
                parentHeight = parentBounds[3];
            }
        }

        float anchorX = anchor.x * parentWidth;
        float anchorY = anchor.y * parentHeight;

        float x = parentX + anchorX + offset.x - pivot.x * width;
        float y = parentY + anchorY + offset.y - pivot.y * height;

        return new float[]{x, y, width, height};
    }

    private EditorEntity findParentWithUITransform(EditorEntity entity) {
        EditorEntity parent = entity.getParent();
        while (parent != null) {
            if (parent.hasComponent("UITransform") || parent.hasComponent("UICanvas")) {
                if (parent.hasComponent("UITransform")) {
                    return parent;
                }
                return null; // Canvas is the root, no transform needed
            }
            parent = parent.getParent();
        }
        return null;
    }

    private boolean isUIEntity(EditorEntity entity) {
        return entity.hasComponent("UICanvas") ||
                entity.hasComponent("UITransform") ||
                entity.hasComponent("UIPanel") ||
                entity.hasComponent("UIImage") ||
                entity.hasComponent("UIButton") ||
                entity.hasComponent("UIText");
    }

    // ========================================================================
    // Coordinate Conversion
    // ========================================================================

    /**
     * Converts canvas coordinates to viewport screen coordinates.
     */
    private Vector2f canvasToScreen(float canvasX, float canvasY) {
        float centerX = viewportWidth / 2;
        float centerY = viewportHeight / 2;

        float screenX = centerX + (canvasX - cameraX) * zoom;
        float screenY = centerY + (canvasY - cameraY) * zoom;

        return new Vector2f(screenX, screenY);
    }

    /**
     * Converts viewport screen coordinates to canvas coordinates.
     */
    private Vector2f screenToCanvas(float screenX, float screenY) {
        float centerX = viewportWidth / 2;
        float centerY = viewportHeight / 2;

        float canvasX = (screenX - centerX) / zoom + cameraX;
        float canvasY = (screenY - centerY) / zoom + cameraY;

        return new Vector2f(canvasX, canvasY);
    }

    /**
     * Resets camera to center canvas in viewport.
     */
    public void resetCamera() {
        cameraX = gameConfig.getGameWidth() / 2f;
        cameraY = gameConfig.getGameHeight() / 2f;
        zoom = Math.min(
                viewportWidth / gameConfig.getGameWidth(),
                viewportHeight / gameConfig.getGameHeight()
        ) * 0.9f;
        if (zoom <= 0) zoom = 1.0f;
    }

    // ========================================================================
    // Utility
    // ========================================================================

    private float getFloat(Map<String, Object> map, String key, float defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.floatValue();
        }
        return defaultValue;
    }

    private Vector2f getVector2f(Map<String, Object> map, String key, float defaultX, float defaultY) {
        Object value = map.get(key);
        if (value instanceof Vector2f v) {
            return new Vector2f(v);
        }
        if (value instanceof Map<?, ?> m) {
            float x = getFloatFromMap(m, "x", defaultX);
            float y = getFloatFromMap(m, "y", defaultY);
            return new Vector2f(x, y);
        }
        if (value instanceof List<?> list && list.size() >= 2) {
            float x = ((Number) list.get(0)).floatValue();
            float y = ((Number) list.get(1)).floatValue();
            return new Vector2f(x, y);
        }
        return new Vector2f(defaultX, defaultY);
    }

    private float getFloatFromMap(Map<?, ?> map, String key, float defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.floatValue();
        }
        return defaultValue;
    }
}
