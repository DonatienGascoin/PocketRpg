package com.pocket.rpg.editor.panels;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.ToolManager;
import com.pocket.rpg.editor.utils.FieldEditors;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
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

import java.util.Map;

/**
 * UI Designer panel - visual editor for UI elements.
 * <p>
 * Features:
 * - Canvas bounds rectangle showing game resolution
 * - Background toggle: World (scene behind) or Gray (neutral)
 * - Selection, move, resize of UI elements
 * - Resize handles on selected elements
 * - Anchor and pivot visualization
 * - Optional snap to canvas edges
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

    // Snap settings
    @Getter @Setter
    private boolean snapEnabled = false;
    @Getter @Setter
    private boolean showAnchorLines = false;
    private static final float SNAP_THRESHOLD = 8f; // pixels in screen space

    // Dragging state
    private boolean isDraggingCamera = false;
    private boolean isDraggingElement = false;
    private boolean isDraggingHandle = false;
    private boolean isDraggingAnchor = false;
    private boolean isDraggingPivot = false;
    private EditorEntity draggedEntity = null;
    private float dragStartX, dragStartY;
    private float entityStartOffsetX, entityStartOffsetY;
    private float entityStartWidth, entityStartHeight;
    private float entityStartAnchorX, entityStartAnchorY;
    private float entityStartPivotX, entityStartPivotY;
    private ResizeHandle activeHandle = null;

    // Resize handles
    private enum ResizeHandle {
        TOP_LEFT, TOP, TOP_RIGHT,
        LEFT, RIGHT,
        BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
    }

    private static final float HANDLE_SIZE = 8f;
    private static final float HANDLE_HIT_SIZE = 12f; // Larger hit area
    private ResizeHandle hoveredHandle = null;
    private EditorEntity hoveredHandleEntity = null;

    // Selection
    @Setter
    private ToolManager toolManager;

    // Canvas framebuffer texture (from scene renderer, for WORLD mode)
    @Setter
    private int sceneTextureId = 0;

    // Colors
    private static final int COLOR_HANDLE = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f);
    private static final int COLOR_HANDLE_HOVERED = ImGui.colorConvertFloat4ToU32(1f, 0.8f, 0.2f, 1f);
    private static final int COLOR_HANDLE_BORDER = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f);
    private static final int COLOR_ANCHOR = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 1f);
    private static final int COLOR_PIVOT = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 1f, 1f);
    private static final int COLOR_SELECTION = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 1f, 1f);
    private static final int COLOR_SNAP_GUIDE = ImGui.colorConvertFloat4ToU32(1f, 0.5f, 0.2f, 0.8f);

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

        // Snap toggle (save state before button to fix push/pop)
        boolean wasSnapEnabled = snapEnabled;
        if (wasSnapEnabled) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.3f, 0.6f, 0.3f, 1f);
        }
        if (ImGui.button(FontAwesomeIcons.Magnet + " Snap")) {
            snapEnabled = !snapEnabled;
        }
        if (wasSnapEnabled) {
            ImGui.popStyleColor();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Snap to canvas edges: " + (snapEnabled ? "ON" : "OFF"));
        }

        ImGui.sameLine();

        // Anchor lines toggle
        boolean wasShowLines = showAnchorLines;
        if (wasShowLines) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.3f, 0.5f, 0.3f, 1f);
        }
        if (ImGui.button(FontAwesomeIcons.ProjectDiagram + "##lines")) {
            showAnchorLines = !showAnchorLines;
        }
        if (wasShowLines) {
            ImGui.popStyleColor();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Show anchor lines: " + (showAnchorLines ? "ON" : "OFF"));
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
            drawList.addRectFilled(
                    viewportX, viewportY,
                    viewportX + viewportWidth, viewportY + viewportHeight,
                    ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1.0f)
            );
        } else if (sceneTextureId != 0) {
            ImGui.image(sceneTextureId, viewportWidth, viewportHeight, 0, 1, 1, 0);
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

        // Draw selection gizmos (handles, anchor, pivot)
        drawSelectionGizmos(drawList);

        // Draw snap guides if snapping
        if (snapEnabled && (isDraggingElement || isDraggingHandle || isDraggingAnchor || isDraggingPivot)) {
            drawSnapGuides(drawList);
        }

        // Invisible button for input handling
        ImGui.setCursorPos(cursorPos.x, cursorPos.y);
        ImGui.invisibleButton("ui_viewport", viewportWidth, viewportHeight);
        isHovered = ImGui.isItemHovered();

        // Handle input
        if (isHovered || isDraggingCamera || isDraggingElement || isDraggingHandle) {
            handleInput();
        }
    }

    private void drawCanvasBounds(imgui.ImDrawList drawList) {
        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

        Vector2f topLeft = canvasToScreen(0, 0);
        Vector2f bottomRight = canvasToScreen(canvasWidth, canvasHeight);

        float left = viewportX + topLeft.x;
        float top = viewportY + topLeft.y;
        float right = viewportX + bottomRight.x;
        float bottom = viewportY + bottomRight.y;

        // Canvas area
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

        for (EditorEntity entity : scene.getEntities()) {
            if (!isUIEntity(entity)) continue;
            drawUIElement(drawList, entity);
        }
    }

    private void drawUIElement(imgui.ImDrawList drawList, EditorEntity entity) {
        var transformComp = entity.getComponentByType("UITransform");
        if (transformComp == null && !entity.hasComponent("UICanvas")) return;
        if (entity.hasComponent("UICanvas")) return;
        if (transformComp == null) return;

        float[] bounds = calculateElementBounds(entity);
        if (bounds == null) return;

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

        EditorScene scene = context.getCurrentScene();
        boolean selected = scene != null && scene.isSelected(entity);

        // Render sprite content
        boolean contentRendered = false;

        if (entity.hasComponent("UIImage")) {
            var imageComp = entity.getComponentByType("UIImage");
            if (imageComp != null) {
                contentRendered = renderSprite(drawList, imageComp.getFields().get("sprite"),
                        imageComp.getFields().get("color"), left, top, right, bottom);
            }
        }

        if (!contentRendered && entity.hasComponent("UIButton")) {
            var buttonComp = entity.getComponentByType("UIButton");
            if (buttonComp != null) {
                contentRendered = renderSprite(drawList, buttonComp.getFields().get("sprite"),
                        buttonComp.getFields().get("color"), left, top, right, bottom);
            }
        }

        if (!contentRendered && entity.hasComponent("UIPanel")) {
            var panelComp = entity.getComponentByType("UIPanel");
            if (panelComp != null) {
                contentRendered = renderSprite(drawList, panelComp.getFields().get("backgroundSprite"),
                        panelComp.getFields().get("backgroundColor"), left, top, right, bottom);
            }
        }

        if (!contentRendered) {
            int fillColor = getElementFillColor(entity);
            if (fillColor != 0) {
                drawList.addRectFilled(left, top, right, bottom, fillColor);
            }
        }

        // Border
        int borderColor = selected ? COLOR_SELECTION
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

        Sprite sprite = null;

        if (spriteObj instanceof Sprite s) {
            sprite = s;
        } else if (spriteObj instanceof String spritePath && !spritePath.isEmpty()) {
            try {
                sprite = Assets.load(spritePath, Sprite.class);
            } catch (Exception e) {
                // Ignore load errors
            }
        } else if (spriteObj instanceof Map<?, ?> spriteMap) {
            // Sprite was serialized as JSON - load from texturePath or name
            String texturePath = getStringFromMap(spriteMap, "texturePath");
            if (texturePath == null || texturePath.isEmpty()) {
                texturePath = getStringFromMap(spriteMap, "name");
            }
            if (texturePath != null && !texturePath.isEmpty()) {
                try {
                    sprite = Assets.load(texturePath, Sprite.class);
                } catch (Exception e) {
                    // Try loading as texture and creating sprite
                    try {
                        Texture texture = Assets.load(texturePath, Texture.class);
                        if (texture != null) {
                            float u0 = getFloatFromMap(spriteMap, "u0", 0f);
                            float v0 = getFloatFromMap(spriteMap, "v0", 0f);
                            float u1 = getFloatFromMap(spriteMap, "u1", 1f);
                            float v1 = getFloatFromMap(spriteMap, "v1", 1f);
                            float w = getFloatFromMap(spriteMap, "width", texture.getWidth());
                            float h = getFloatFromMap(spriteMap, "height", texture.getHeight());
                            sprite = new Sprite(texture, w, h);
                            sprite.setUVs(u0, v0, u1, v1);
                        }
                    } catch (Exception e2) {
                        // Ignore
                    }
                }
            }
        }

        if (sprite == null || sprite.getTexture() == null) return false;

        int textureId = sprite.getTexture().getTextureId();
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();

        int tintColor = parseColor(colorObj);
        drawList.addImage(textureId, left, top, right, bottom, u0, v0, u1, v1, tintColor);
        return true;
    }

    private String getStringFromMap(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
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

    // ========================================================================
    // SELECTION GIZMOS
    // ========================================================================

    private void drawSelectionGizmos(imgui.ImDrawList drawList) {
        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        // Reset hover state
        hoveredHandle = null;
        hoveredHandleEntity = null;

        // Get mouse position for hover detection
        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;

        for (EditorEntity entity : scene.getSelectedEntities()) {
            if (!isUIEntity(entity)) continue;
            if (entity.hasComponent("UICanvas")) continue;

            // Check for hovered handle
            if (isHovered && !isDraggingElement && !isDraggingHandle && !isDraggingAnchor && !isDraggingPivot) {
                ResizeHandle handle = hitTestHandles(entity, localX, localY);
                if (handle != null) {
                    hoveredHandle = handle;
                    hoveredHandleEntity = entity;
                }
            }

            drawEntityGizmos(drawList, entity);
        }
    }

    private void drawEntityGizmos(imgui.ImDrawList drawList, EditorEntity entity) {
        float[] bounds = calculateElementBounds(entity);
        if (bounds == null) return;

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

        // Draw resize handles
        drawResizeHandles(drawList, entity, left, top, right, bottom);

        // Draw anchor point
        drawAnchorPoint(drawList, entity, x, y, width, height);

        // Draw pivot point
        drawPivotPoint(drawList, entity, x, y, width, height);
    }

    private void drawResizeHandles(imgui.ImDrawList drawList, EditorEntity entity,
                                   float left, float top, float right, float bottom) {
        float midX = (left + right) / 2;
        float midY = (top + bottom) / 2;

        boolean isThisEntity = entity == hoveredHandleEntity;

        // Corner handles
        drawHandle(drawList, left, top, isThisEntity && hoveredHandle == ResizeHandle.TOP_LEFT);
        drawHandle(drawList, right, top, isThisEntity && hoveredHandle == ResizeHandle.TOP_RIGHT);
        drawHandle(drawList, left, bottom, isThisEntity && hoveredHandle == ResizeHandle.BOTTOM_LEFT);
        drawHandle(drawList, right, bottom, isThisEntity && hoveredHandle == ResizeHandle.BOTTOM_RIGHT);

        // Edge handles
        drawHandle(drawList, midX, top, isThisEntity && hoveredHandle == ResizeHandle.TOP);
        drawHandle(drawList, midX, bottom, isThisEntity && hoveredHandle == ResizeHandle.BOTTOM);
        drawHandle(drawList, left, midY, isThisEntity && hoveredHandle == ResizeHandle.LEFT);
        drawHandle(drawList, right, midY, isThisEntity && hoveredHandle == ResizeHandle.RIGHT);
    }

    private void drawHandle(imgui.ImDrawList drawList, float x, float y, boolean hovered) {
        float half = (hovered ? HANDLE_SIZE + 2 : HANDLE_SIZE) / 2;
        int fillColor = hovered ? COLOR_HANDLE_HOVERED : COLOR_HANDLE;
        drawList.addRectFilled(x - half, y - half, x + half, y + half, fillColor);
        drawList.addRect(x - half, y - half, x + half, y + half, COLOR_HANDLE_BORDER);
    }

    private void drawAnchorPoint(imgui.ImDrawList drawList, EditorEntity entity,
                                 float elemX, float elemY, float elemWidth, float elemHeight) {
        var transform = entity.getComponentByType("UITransform");
        if (transform == null) return;

        Map<String, Object> fields = transform.getFields();
        Vector2f anchor = FieldEditors.getVector2f(fields, "anchor");

        // Anchor is relative to parent, calculate its position
        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

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

        float anchorX = parentX + anchor.x * parentWidth;
        float anchorY = parentY + anchor.y * parentHeight;

        Vector2f screenAnchor = canvasToScreen(anchorX, anchorY);
        float sx = viewportX + screenAnchor.x;
        float sy = viewportY + screenAnchor.y;

        // Check if hovered
        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;
        boolean hovered = Math.abs(localX - screenAnchor.x) < 12 && Math.abs(localY - screenAnchor.y) < 12;

        // Draw anchor as diamond (larger if hovered)
        float size = hovered ? 8 : 6;
        int color = hovered ? ImGui.colorConvertFloat4ToU32(0.4f, 1f, 0.4f, 1f) : COLOR_ANCHOR;

        drawList.addQuadFilled(
                sx, sy - size,
                sx + size, sy,
                sx, sy + size,
                sx - size, sy,
                color
        );
        drawList.addQuad(
                sx, sy - size,
                sx + size, sy,
                sx, sy + size,
                sx - size, sy,
                COLOR_HANDLE_BORDER
        );

        // Draw line from anchor to element center (only if enabled)
        if (showAnchorLines) {
            float elemCenterX = elemX + elemWidth / 2;
            float elemCenterY = elemY + elemHeight / 2;
            Vector2f screenCenter = canvasToScreen(elemCenterX, elemCenterY);
            drawList.addLine(sx, sy, viewportX + screenCenter.x, viewportY + screenCenter.y,
                    ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 0.3f), 1f);
        }

        // Label
        if (hovered || isDraggingAnchor) {
            String label = String.format("Anchor (%.2f, %.2f)", anchor.x, anchor.y);
            drawList.addText(sx + 10, sy - 10, COLOR_ANCHOR, label);
        }
    }

    private void drawPivotPoint(imgui.ImDrawList drawList, EditorEntity entity,
                                float elemX, float elemY, float elemWidth, float elemHeight) {
        var transform = entity.getComponentByType("UITransform");
        if (transform == null) return;

        Map<String, Object> fields = transform.getFields();
        Vector2f pivot = FieldEditors.getVector2f(fields, "pivot");

        // Pivot is relative to element bounds
        float pivotX = elemX + pivot.x * elemWidth;
        float pivotY = elemY + pivot.y * elemHeight;

        Vector2f screenPivot = canvasToScreen(pivotX, pivotY);
        float sx = viewportX + screenPivot.x;
        float sy = viewportY + screenPivot.y;

        // Check if hovered
        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;
        boolean hovered = Math.abs(localX - screenPivot.x) < 10 && Math.abs(localY - screenPivot.y) < 10;

        // Draw pivot as circle with crosshair (larger if hovered)
        float radius = hovered ? 7 : 5;
        int color = hovered ? ImGui.colorConvertFloat4ToU32(0.4f, 0.8f, 1f, 1f) : COLOR_PIVOT;

        drawList.addCircleFilled(sx, sy, radius, color);
        drawList.addCircle(sx, sy, radius, COLOR_HANDLE_BORDER);

        // Crosshair
        drawList.addLine(sx - radius - 2, sy, sx + radius + 2, sy, color, 1f);
        drawList.addLine(sx, sy - radius - 2, sx, sy + radius + 2, color, 1f);

        // Label
        if (hovered || isDraggingPivot) {
            String label = String.format("Pivot (%.2f, %.2f)", pivot.x, pivot.y);
            drawList.addText(sx + 10, sy + 5, COLOR_PIVOT, label);
        }
    }

    private void drawSnapGuides(imgui.ImDrawList drawList) {
        // Draw canvas edge snap guides when close
        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

        // Get current drag position in canvas coords
        if (draggedEntity == null) return;

        float[] bounds = calculateElementBounds(draggedEntity);
        if (bounds == null) return;

        float elemLeft = bounds[0];
        float elemTop = bounds[1];
        float elemRight = elemLeft + bounds[2];
        float elemBottom = elemTop + bounds[3];

        // Check proximity to canvas edges and draw guides
        float threshold = SNAP_THRESHOLD / zoom;

        // Left edge
        if (Math.abs(elemLeft) < threshold || Math.abs(elemRight) < threshold) {
            Vector2f top = canvasToScreen(0, 0);
            Vector2f bottom = canvasToScreen(0, canvasHeight);
            drawList.addLine(viewportX + top.x, viewportY + top.y,
                    viewportX + bottom.x, viewportY + bottom.y, COLOR_SNAP_GUIDE, 1f);
        }

        // Right edge
        if (Math.abs(elemLeft - canvasWidth) < threshold || Math.abs(elemRight - canvasWidth) < threshold) {
            Vector2f top = canvasToScreen(canvasWidth, 0);
            Vector2f bottom = canvasToScreen(canvasWidth, canvasHeight);
            drawList.addLine(viewportX + top.x, viewportY + top.y,
                    viewportX + bottom.x, viewportY + bottom.y, COLOR_SNAP_GUIDE, 1f);
        }

        // Top edge
        if (Math.abs(elemTop) < threshold || Math.abs(elemBottom) < threshold) {
            Vector2f left = canvasToScreen(0, 0);
            Vector2f right = canvasToScreen(canvasWidth, 0);
            drawList.addLine(viewportX + left.x, viewportY + left.y,
                    viewportX + right.x, viewportY + right.y, COLOR_SNAP_GUIDE, 1f);
        }

        // Bottom edge
        if (Math.abs(elemTop - canvasHeight) < threshold || Math.abs(elemBottom - canvasHeight) < threshold) {
            Vector2f left = canvasToScreen(0, canvasHeight);
            Vector2f right = canvasToScreen(canvasWidth, canvasHeight);
            drawList.addLine(viewportX + left.x, viewportY + left.y,
                    viewportX + right.x, viewportY + right.y, COLOR_SNAP_GUIDE, 1f);
        }
    }

    // ========================================================================
    // INPUT HANDLING
    // ========================================================================

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

        // Left click/drag
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && isHovered) {
            handleClick();
        }

        if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            if (isDraggingHandle && draggedEntity != null) {
                handleResizeDrag();
            } else if (isDraggingAnchor && draggedEntity != null) {
                handleAnchorDrag();
            } else if (isDraggingPivot && draggedEntity != null) {
                handlePivotDrag();
            } else if (isDraggingElement && draggedEntity != null) {
                handleMoveDrag();
            }
        }

        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            isDraggingElement = false;
            isDraggingHandle = false;
            isDraggingAnchor = false;
            isDraggingPivot = false;
            draggedEntity = null;
            activeHandle = null;
        }
    }

    private void handleClick() {
        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;

        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        // First check if clicking on anchor or pivot of selected entity
        for (EditorEntity entity : scene.getSelectedEntities()) {
            if (!isUIEntity(entity) || entity.hasComponent("UICanvas")) continue;

            // Check anchor hit
            if (hitTestAnchor(entity, localX, localY)) {
                startAnchorDrag(entity);
                return;
            }

            // Check pivot hit
            if (hitTestPivot(entity, localX, localY)) {
                startPivotDrag(entity);
                return;
            }

            // Check resize handles
            ResizeHandle handle = hitTestHandles(entity, localX, localY);
            if (handle != null) {
                startResizeDrag(entity, handle);
                return;
            }
        }

        // Otherwise, check for element click
        Vector2f canvasPos = screenToCanvas(localX, localY);

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

            startMoveDrag(clicked, canvasPos.x, canvasPos.y);
        } else {
            if (!shift && !ctrl) {
                scene.clearSelection();
            }
        }
    }

    private void startMoveDrag(EditorEntity entity, float canvasX, float canvasY) {
        isDraggingElement = true;
        isDraggingHandle = false;
        isDraggingAnchor = false;
        isDraggingPivot = false;
        draggedEntity = entity;
        dragStartX = canvasX;
        dragStartY = canvasY;

        var transform = entity.getComponentByType("UITransform");
        if (transform != null) {
            Vector2f offset = FieldEditors.getVector2f(transform.getFields(), "offset");
            entityStartOffsetX = offset.x;
            entityStartOffsetY = offset.y;
        }
    }

    private void startResizeDrag(EditorEntity entity, ResizeHandle handle) {
        isDraggingHandle = true;
        isDraggingElement = false;
        isDraggingAnchor = false;
        isDraggingPivot = false;
        draggedEntity = entity;
        activeHandle = handle;

        ImVec2 mousePos = ImGui.getMousePos();
        Vector2f canvasPos = screenToCanvas(mousePos.x - viewportX, mousePos.y - viewportY);
        dragStartX = canvasPos.x;
        dragStartY = canvasPos.y;

        var transform = entity.getComponentByType("UITransform");
        if (transform != null) {
            Map<String, Object> fields = transform.getFields();
            Vector2f offset = FieldEditors.getVector2f(fields, "offset");
            entityStartOffsetX = offset.x;
            entityStartOffsetY = offset.y;
            entityStartWidth = FieldEditors.getFloat(fields, "width", 100);
            entityStartHeight = FieldEditors.getFloat(fields, "height", 100);
        }
    }

    private void startAnchorDrag(EditorEntity entity) {
        isDraggingAnchor = true;
        isDraggingElement = false;
        isDraggingHandle = false;
        isDraggingPivot = false;
        draggedEntity = entity;

        var transform = entity.getComponentByType("UITransform");
        if (transform != null) {
            Map<String, Object> fields = transform.getFields();
            Vector2f anchor = FieldEditors.getVector2f(fields, "anchor");
            entityStartAnchorX = anchor.x;
            entityStartAnchorY = anchor.y;
        }
    }

    private void startPivotDrag(EditorEntity entity) {
        isDraggingPivot = true;
        isDraggingElement = false;
        isDraggingHandle = false;
        isDraggingAnchor = false;
        draggedEntity = entity;

        var transform = entity.getComponentByType("UITransform");
        if (transform != null) {
            Map<String, Object> fields = transform.getFields();
            Vector2f pivot = FieldEditors.getVector2f(fields, "pivot");
            entityStartPivotX = pivot.x;
            entityStartPivotY = pivot.y;
        }
    }

    private boolean hitTestAnchor(EditorEntity entity, float screenX, float screenY) {
        float[] anchorPos = calculateAnchorScreenPos(entity);
        if (anchorPos == null) return false;

        float hitSize = 12f;
        return Math.abs(screenX - anchorPos[0]) < hitSize &&
                Math.abs(screenY - anchorPos[1]) < hitSize;
    }

    private boolean hitTestPivot(EditorEntity entity, float screenX, float screenY) {
        float[] pivotPos = calculatePivotScreenPos(entity);
        if (pivotPos == null) return false;

        float hitSize = 10f;
        return Math.abs(screenX - pivotPos[0]) < hitSize &&
                Math.abs(screenY - pivotPos[1]) < hitSize;
    }

    private float[] calculateAnchorScreenPos(EditorEntity entity) {
        var transform = entity.getComponentByType("UITransform");
        if (transform == null) return null;

        Map<String, Object> fields = transform.getFields();
        Vector2f anchor = FieldEditors.getVector2f(fields, "anchor");

        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

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

        float anchorX = parentX + anchor.x * parentWidth;
        float anchorY = parentY + anchor.y * parentHeight;

        Vector2f screenPos = canvasToScreen(anchorX, anchorY);
        return new float[]{screenPos.x, screenPos.y};
    }

    private float[] calculatePivotScreenPos(EditorEntity entity) {
        float[] bounds = calculateElementBounds(entity);
        if (bounds == null) return null;

        var transform = entity.getComponentByType("UITransform");
        if (transform == null) return null;

        Map<String, Object> fields = transform.getFields();
        Vector2f pivot = FieldEditors.getVector2f(fields, "pivot");

        float pivotX = bounds[0] + pivot.x * bounds[2];
        float pivotY = bounds[1] + pivot.y * bounds[3];

        Vector2f screenPos = canvasToScreen(pivotX, pivotY);
        return new float[]{screenPos.x, screenPos.y};
    }

    private void handleMoveDrag() {
        if (draggedEntity == null) return;

        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;

        Vector2f canvasPos = screenToCanvas(localX, localY);
        float deltaX = canvasPos.x - dragStartX;
        float deltaY = canvasPos.y - dragStartY;

        float newOffsetX = entityStartOffsetX + deltaX;
        float newOffsetY = entityStartOffsetY + deltaY;

        // Apply snap if enabled
        if (snapEnabled) {
            var transform = draggedEntity.getComponentByType("UITransform");
            if (transform != null) {
                Map<String, Object> fields = transform.getFields();
                float width = FieldEditors.getFloat(fields, "width", 100);
                float height = FieldEditors.getFloat(fields, "height", 100);
                Vector2f anchor = FieldEditors.getVector2f(fields, "anchor");
                Vector2f pivot = FieldEditors.getVector2f(fields, "pivot");

                float canvasWidth = gameConfig.getGameWidth();
                float canvasHeight = gameConfig.getGameHeight();

                // Calculate element edges with new offset
                float anchorX = anchor.x * canvasWidth;
                float anchorY = anchor.y * canvasHeight;
                float elemX = anchorX + newOffsetX - pivot.x * width;
                float elemY = anchorY + newOffsetY - pivot.y * height;
                float elemRight = elemX + width;
                float elemBottom = elemY + height;

                float threshold = SNAP_THRESHOLD / zoom;

                // Snap left edge to canvas left
                if (Math.abs(elemX) < threshold) {
                    newOffsetX = pivot.x * width - anchorX;
                }
                // Snap right edge to canvas right
                if (Math.abs(elemRight - canvasWidth) < threshold) {
                    newOffsetX = canvasWidth - width + pivot.x * width - anchorX;
                }
                // Snap top edge to canvas top
                if (Math.abs(elemY) < threshold) {
                    newOffsetY = pivot.y * height - anchorY;
                }
                // Snap bottom edge to canvas bottom
                if (Math.abs(elemBottom - canvasHeight) < threshold) {
                    newOffsetY = canvasHeight - height + pivot.y * height - anchorY;
                }
            }
        }

        var transform = draggedEntity.getComponentByType("UITransform");
        if (transform != null) {
            transform.getFields().put("offset", new Vector2f(newOffsetX, newOffsetY));

            EditorScene scene = context.getCurrentScene();
            if (scene != null) {
                scene.markDirty();
            }
        }
    }

    private void handleResizeDrag() {
        if (draggedEntity == null || activeHandle == null) return;

        ImVec2 mousePos = ImGui.getMousePos();
        Vector2f canvasPos = screenToCanvas(mousePos.x - viewportX, mousePos.y - viewportY);
        float deltaX = canvasPos.x - dragStartX;
        float deltaY = canvasPos.y - dragStartY;

        float newWidth = entityStartWidth;
        float newHeight = entityStartHeight;
        float newOffsetX = entityStartOffsetX;
        float newOffsetY = entityStartOffsetY;

        var transform = draggedEntity.getComponentByType("UITransform");
        if (transform == null) return;

        Map<String, Object> fields = transform.getFields();
        Vector2f pivot = FieldEditors.getVector2f(fields, "pivot");

        switch (activeHandle) {
            case TOP_LEFT -> {
                newWidth = Math.max(1, entityStartWidth - deltaX);
                newHeight = Math.max(1, entityStartHeight - deltaY);
                newOffsetX = entityStartOffsetX + deltaX * (1 - pivot.x);
                newOffsetY = entityStartOffsetY + deltaY * (1 - pivot.y);
            }
            case TOP -> {
                newHeight = Math.max(1, entityStartHeight - deltaY);
                newOffsetY = entityStartOffsetY + deltaY * (1 - pivot.y);
            }
            case TOP_RIGHT -> {
                newWidth = Math.max(1, entityStartWidth + deltaX);
                newHeight = Math.max(1, entityStartHeight - deltaY);
                newOffsetX = entityStartOffsetX + deltaX * pivot.x;
                newOffsetY = entityStartOffsetY + deltaY * (1 - pivot.y);
            }
            case LEFT -> {
                newWidth = Math.max(1, entityStartWidth - deltaX);
                newOffsetX = entityStartOffsetX + deltaX * (1 - pivot.x);
            }
            case RIGHT -> {
                newWidth = Math.max(1, entityStartWidth + deltaX);
                newOffsetX = entityStartOffsetX + deltaX * pivot.x;
            }
            case BOTTOM_LEFT -> {
                newWidth = Math.max(1, entityStartWidth - deltaX);
                newHeight = Math.max(1, entityStartHeight + deltaY);
                newOffsetX = entityStartOffsetX + deltaX * (1 - pivot.x);
                newOffsetY = entityStartOffsetY + deltaY * pivot.y;
            }
            case BOTTOM -> {
                newHeight = Math.max(1, entityStartHeight + deltaY);
                newOffsetY = entityStartOffsetY + deltaY * pivot.y;
            }
            case BOTTOM_RIGHT -> {
                newWidth = Math.max(1, entityStartWidth + deltaX);
                newHeight = Math.max(1, entityStartHeight + deltaY);
                newOffsetX = entityStartOffsetX + deltaX * pivot.x;
                newOffsetY = entityStartOffsetY + deltaY * pivot.y;
            }
        }

        // Apply snap to canvas edges if enabled
        if (snapEnabled) {
            // TODO: Snap during resize (more complex, skipping for now)
        }

        fields.put("width", newWidth);
        fields.put("height", newHeight);
        fields.put("offset", new Vector2f(newOffsetX, newOffsetY));

        EditorScene scene = context.getCurrentScene();
        if (scene != null) {
            scene.markDirty();
        }
    }

    private void handleAnchorDrag() {
        if (draggedEntity == null) return;

        ImVec2 mousePos = ImGui.getMousePos();
        Vector2f canvasPos = screenToCanvas(mousePos.x - viewportX, mousePos.y - viewportY);

        // Calculate parent bounds
        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

        float parentWidth = canvasWidth;
        float parentHeight = canvasHeight;
        float parentX = 0;
        float parentY = 0;

        EditorEntity parent = findParentWithUITransform(draggedEntity);
        if (parent != null) {
            float[] parentBounds = calculateElementBounds(parent);
            if (parentBounds != null) {
                parentX = parentBounds[0];
                parentY = parentBounds[1];
                parentWidth = parentBounds[2];
                parentHeight = parentBounds[3];
            }
        }

        // Calculate new anchor based on mouse position relative to parent
        float newAnchorX = (canvasPos.x - parentX) / parentWidth;
        float newAnchorY = (canvasPos.y - parentY) / parentHeight;

        // Clamp to 0-1
        newAnchorX = Math.max(0, Math.min(1, newAnchorX));
        newAnchorY = Math.max(0, Math.min(1, newAnchorY));

        // Snap to common values (0, 0.5, 1)
        float snapThreshold = 0.05f;
        newAnchorX = snapToValue(newAnchorX, 0f, snapThreshold);
        newAnchorX = snapToValue(newAnchorX, 0.5f, snapThreshold);
        newAnchorX = snapToValue(newAnchorX, 1f, snapThreshold);
        newAnchorY = snapToValue(newAnchorY, 0f, snapThreshold);
        newAnchorY = snapToValue(newAnchorY, 0.5f, snapThreshold);
        newAnchorY = snapToValue(newAnchorY, 1f, snapThreshold);

        var transform = draggedEntity.getComponentByType("UITransform");
        if (transform != null) {
            Map<String, Object> fields = transform.getFields();

            // Get current values to adjust offset
            Vector2f oldAnchor = FieldEditors.getVector2f(fields, "anchor");
            Vector2f offset = FieldEditors.getVector2f(fields, "offset");

            // Adjust offset to keep element in same visual position
            float anchorDeltaX = (newAnchorX - oldAnchor.x) * parentWidth;
            float anchorDeltaY = (newAnchorY - oldAnchor.y) * parentHeight;

            fields.put("anchor", new Vector2f(newAnchorX, newAnchorY));
            fields.put("offset", new Vector2f(offset.x - anchorDeltaX, offset.y - anchorDeltaY));

            EditorScene scene = context.getCurrentScene();
            if (scene != null) {
                scene.markDirty();
            }
        }
    }

    private void handlePivotDrag() {
        if (draggedEntity == null) return;

        ImVec2 mousePos = ImGui.getMousePos();
        Vector2f canvasPos = screenToCanvas(mousePos.x - viewportX, mousePos.y - viewportY);

        float[] bounds = calculateElementBounds(draggedEntity);
        if (bounds == null) return;

        var transform = draggedEntity.getComponentByType("UITransform");
        if (transform == null) return;

        Map<String, Object> fields = transform.getFields();
        float width = FieldEditors.getFloat(fields, "width", 100);
        float height = FieldEditors.getFloat(fields, "height", 100);

        // Calculate new pivot based on mouse position relative to element
        // Need to account for current pivot affecting position
        Vector2f oldPivot = FieldEditors.getVector2f(fields, "pivot");
        Vector2f offset = FieldEditors.getVector2f(fields, "offset");
        Vector2f anchor = FieldEditors.getVector2f(fields, "anchor");

        // Calculate parent anchor position
        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();
        float parentWidth = canvasWidth;
        float parentHeight = canvasHeight;
        float parentX = 0;
        float parentY = 0;

        EditorEntity parent = findParentWithUITransform(draggedEntity);
        if (parent != null) {
            float[] parentBounds = calculateElementBounds(parent);
            if (parentBounds != null) {
                parentX = parentBounds[0];
                parentY = parentBounds[1];
                parentWidth = parentBounds[2];
                parentHeight = parentBounds[3];
            }
        }

        // Calculate where the element would be positioned (anchor + offset point)
        float anchorPosX = parentX + anchor.x * parentWidth + offset.x;
        float anchorPosY = parentY + anchor.y * parentHeight + offset.y;

        // Calculate new pivot: where is mouse relative to anchor+offset, normalized by size
        float newPivotX = (canvasPos.x - anchorPosX + oldPivot.x * width) / width;
        float newPivotY = (canvasPos.y - anchorPosY + oldPivot.y * height) / height;

        // Clamp to 0-1
        newPivotX = Math.max(0, Math.min(1, newPivotX));
        newPivotY = Math.max(0, Math.min(1, newPivotY));

        // Snap to common values
        float snapThreshold = 0.05f;
        newPivotX = snapToValue(newPivotX, 0f, snapThreshold);
        newPivotX = snapToValue(newPivotX, 0.5f, snapThreshold);
        newPivotX = snapToValue(newPivotX, 1f, snapThreshold);
        newPivotY = snapToValue(newPivotY, 0f, snapThreshold);
        newPivotY = snapToValue(newPivotY, 0.5f, snapThreshold);
        newPivotY = snapToValue(newPivotY, 1f, snapThreshold);

        // Adjust offset to keep element in same visual position
        float pivotDeltaX = (newPivotX - oldPivot.x) * width;
        float pivotDeltaY = (newPivotY - oldPivot.y) * height;

        fields.put("pivot", new Vector2f(newPivotX, newPivotY));
        fields.put("offset", new Vector2f(offset.x + pivotDeltaX, offset.y + pivotDeltaY));

        EditorScene scene = context.getCurrentScene();
        if (scene != null) {
            scene.markDirty();
        }
    }

    private float snapToValue(float value, float target, float threshold) {
        if (Math.abs(value - target) < threshold) {
            return target;
        }
        return value;
    }

    private ResizeHandle hitTestHandles(EditorEntity entity, float screenX, float screenY) {
        float[] bounds = calculateElementBounds(entity);
        if (bounds == null) return null;

        float x = bounds[0];
        float y = bounds[1];
        float width = bounds[2];
        float height = bounds[3];

        Vector2f screenPos = canvasToScreen(x, y);
        Vector2f screenEnd = canvasToScreen(x + width, y + height);

        float left = screenPos.x;
        float top = screenPos.y;
        float right = screenEnd.x;
        float bottom = screenEnd.y;
        float midX = (left + right) / 2;
        float midY = (top + bottom) / 2;

        float hitSize = HANDLE_HIT_SIZE;

        // Check corners first (higher priority)
        if (hitTestHandle(screenX, screenY, left, top, hitSize)) return ResizeHandle.TOP_LEFT;
        if (hitTestHandle(screenX, screenY, right, top, hitSize)) return ResizeHandle.TOP_RIGHT;
        if (hitTestHandle(screenX, screenY, left, bottom, hitSize)) return ResizeHandle.BOTTOM_LEFT;
        if (hitTestHandle(screenX, screenY, right, bottom, hitSize)) return ResizeHandle.BOTTOM_RIGHT;

        // Then edges
        if (hitTestHandle(screenX, screenY, midX, top, hitSize)) return ResizeHandle.TOP;
        if (hitTestHandle(screenX, screenY, midX, bottom, hitSize)) return ResizeHandle.BOTTOM;
        if (hitTestHandle(screenX, screenY, left, midY, hitSize)) return ResizeHandle.LEFT;
        if (hitTestHandle(screenX, screenY, right, midY, hitSize)) return ResizeHandle.RIGHT;

        return null;
    }

    private boolean hitTestHandle(float mouseX, float mouseY, float handleX, float handleY, float size) {
        float half = size / 2;
        return mouseX >= handleX - half && mouseX <= handleX + half &&
                mouseY >= handleY - half && mouseY <= handleY + half;
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

    // ========================================================================
    // BOUNDS CALCULATION
    // ========================================================================

    private float[] calculateElementBounds(EditorEntity entity) {
        var transformComp = entity.getComponentByType("UITransform");
        if (transformComp == null) return null;

        Map<String, Object> fields = transformComp.getFields();
        float width = FieldEditors.getFloat(fields, "width", 100);
        float height = FieldEditors.getFloat(fields, "height", 100);
        Vector2f offset = FieldEditors.getVector2f(fields, "offset");
        Vector2f anchor = FieldEditors.getVector2f(fields, "anchor");
        Vector2f pivot = FieldEditors.getVector2f(fields, "pivot");

        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

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
                return null;
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
    // COORDINATE CONVERSION
    // ========================================================================

    private Vector2f canvasToScreen(float canvasX, float canvasY) {
        float centerX = viewportWidth / 2;
        float centerY = viewportHeight / 2;

        float screenX = centerX + (canvasX - cameraX) * zoom;
        float screenY = centerY + (canvasY - cameraY) * zoom;

        return new Vector2f(screenX, screenY);
    }

    private Vector2f screenToCanvas(float screenX, float screenY) {
        float centerX = viewportWidth / 2;
        float centerY = viewportHeight / 2;

        float canvasX = (screenX - centerX) / zoom + cameraX;
        float canvasY = (screenY - centerY) / zoom + cameraY;

        return new Vector2f(canvasX, canvasY);
    }

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
    // UTILITY
    // ========================================================================

    private float getFloatFromMap(Map<?, ?> map, String key, float defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.floatValue();
        }
        return defaultValue;
    }
}