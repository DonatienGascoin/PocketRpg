package com.pocket.rpg.editor.panels;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.*;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.rendering.UIRenderingBackend;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.ToolManager;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.UITransformDragCommand;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UI Designer panel - visual editor for UI elements.
 */
public class UIDesignerPanel {

    private final EditorContext context;
    private final GameConfig gameConfig;
    private final RenderingConfig renderingConfig;

    private UIRenderingBackend uiRenderer;
    private boolean rendererInitialized = false;

    @Getter
    private float viewportX, viewportY;
    @Getter
    private float viewportWidth, viewportHeight;
    @Getter
    private boolean isHovered = false;
    @Getter
    private boolean isFocused = false;

    private float cameraX = 0;
    private float cameraY = 0;
    private float zoom = 1.0f;

    public enum BackgroundMode {WORLD, GRAY}

    @Getter
    @Setter
    private BackgroundMode backgroundMode = BackgroundMode.GRAY;

    @Getter
    @Setter
    private boolean snapEnabled = false;
    @Getter
    @Setter
    private boolean showAnchorLines = false;
    private static final float SNAP_THRESHOLD = 8f;

    private boolean isDraggingCamera = false;
    private boolean isDraggingElement = false;
    private boolean isDraggingHandle = false;
    private boolean isDraggingAnchor = false;
    private boolean isDraggingPivot = false;
    private EditorGameObject draggedEntity = null;
    private float dragStartX, dragStartY;
    private float entityStartOffsetX, entityStartOffsetY;
    private float entityStartWidth, entityStartHeight;
    private float entityStartAnchorX, entityStartAnchorY;
    private float entityStartPivotX, entityStartPivotY;
    private ResizeHandle activeHandle = null;

    private Vector2f dragOldOffset;
    private float dragOldWidth;
    private float dragOldHeight;
    private Vector2f dragOldAnchor;
    private Vector2f dragOldPivot;
    private List<UITransformDragCommand.ChildTransformState> dragChildStates;

    private enum ResizeHandle {
        TOP_LEFT, TOP, TOP_RIGHT,
        LEFT, RIGHT,
        BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
    }

    private static final float HANDLE_SIZE = 8f;
    private static final float HANDLE_HIT_SIZE = 12f;
    private ResizeHandle hoveredHandle = null;
    private EditorGameObject hoveredHandleEntity = null;

    @Setter
    private ToolManager toolManager;

    @Setter
    private int sceneTextureId = 0;

    private static final int COLOR_HANDLE = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f);
    private static final int COLOR_HANDLE_HOVERED = ImGui.colorConvertFloat4ToU32(1f, 0.8f, 0.2f, 1f);
    private static final int COLOR_HANDLE_BORDER = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f);
    private static final int COLOR_ANCHOR = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 1f);
    private static final int COLOR_PIVOT = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 1f, 1f);
    private static final int COLOR_SELECTION = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 1f, 1f);
    private static final int COLOR_SNAP_GUIDE = ImGui.colorConvertFloat4ToU32(1f, 0.5f, 0.2f, 0.8f);

    private static final Vector4f TINT_WHITE = new Vector4f(1f, 1f, 1f, 1f);

    public UIDesignerPanel(EditorContext context) {
        this.context = context;
        this.gameConfig = context.getGameConfig();
        this.renderingConfig = context.getRenderingConfig();
    }

    private void initRenderer() {
        if (rendererInitialized) return;
        uiRenderer = new UIRenderingBackend(renderingConfig);
        uiRenderer.init(gameConfig.getGameWidth(), gameConfig.getGameHeight());
        rendererInitialized = true;
    }

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

        ImGui.text(String.format("Zoom: %.0f%%", zoom * 100));
        ImGui.sameLine();

        if (ImGui.button("Reset")) {
            resetCamera();
        }

        ImGui.sameLine();
        ImGui.spacing();
        ImGui.sameLine();

        ImGui.textDisabled(String.format("Canvas: %dx%d",
                gameConfig.getGameWidth(), gameConfig.getGameHeight()));
    }

    private void renderViewport() {
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

        drawCanvasBounds(drawList);
        renderUIElementsToTexture();
        displayUITexture(drawList);
        drawTextElements(drawList);
        drawSelectionBorders(drawList);
        drawSelectionGizmos(drawList);

        if (snapEnabled && (isDraggingElement || isDraggingHandle || isDraggingAnchor || isDraggingPivot)) {
            drawSnapGuides(drawList);
        }

        ImGui.setCursorPos(cursorPos.x, cursorPos.y);
        ImGui.invisibleButton("ui_viewport", viewportWidth, viewportHeight);
        isHovered = ImGui.isItemHovered();

        if (isHovered || isDraggingCamera || isDraggingElement || isDraggingHandle) {
            handleInput();
        }
    }

    private void renderUIElementsToTexture() {
        if (!rendererInitialized) {
            initRenderer();
        }

        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        int canvasWidth = gameConfig.getGameWidth();
        int canvasHeight = gameConfig.getGameHeight();

        uiRenderer.begin(canvasWidth, canvasHeight);

        for (EditorGameObject entity : scene.getEntities()) {
            if (!isUIEntity(entity)) continue;
            if (entity.hasComponent(UICanvas.class)) continue;
            renderUIElementContent(entity);
        }

        uiRenderer.end();
    }

    private void renderUIElementContent(EditorGameObject entity) {
        Component transformComp = entity.getComponentByType("UITransform");
        if (transformComp == null) return;

        float[] bounds = calculateElementBounds(entity);
        if (bounds == null) return;

        float x = bounds[0];
        float y = bounds[1];
        float width = bounds[2];
        float height = bounds[3];

        float zIndex = getEntityZIndex(entity);

        boolean contentRendered = false;

        if (entity.hasComponent(UIImage.class)) {
            Component imageComp = entity.getComponentByType("UIImage");
            if (imageComp != null) {
                contentRendered = renderSpriteToBackend(
                        ComponentReflectionUtils.getFieldValue(imageComp, "sprite"),
                        ComponentReflectionUtils.getFieldValue(imageComp, "color"),
                        x, y, width, height, zIndex
                );
            }
        }

        if (!contentRendered && entity.hasComponent(UIButton.class)) {
            Component buttonComp = entity.getComponentByType("UIButton");
            if (buttonComp != null) {
                contentRendered = renderSpriteToBackend(
                        ComponentReflectionUtils.getFieldValue(buttonComp, "sprite"),
                        ComponentReflectionUtils.getFieldValue(buttonComp, "color"),
                        x, y, width, height, zIndex
                );

                if (!contentRendered) {
                    Vector4f color = parseColorVec4(ComponentReflectionUtils.getFieldValue(buttonComp, "color"));
                    if (color.w > 0) {
                        uiRenderer.drawRect(x, y, width, height, color, zIndex);
                        contentRendered = true;
                    }
                }
            }
        }

        if (!contentRendered && entity.hasComponent(UIPanel.class)) {
            Component panelComp = entity.getComponentByType("UIPanel");
            if (panelComp != null) {
                contentRendered = renderSpriteToBackend(
                        ComponentReflectionUtils.getFieldValue(panelComp, "backgroundSprite"),
                        ComponentReflectionUtils.getFieldValue(panelComp, "backgroundColor"),
                        x, y, width, height, zIndex
                );
            }
        }

        if (!contentRendered && !entity.hasComponent(UIText.class)) {
            Vector4f fillColor = getElementFillColorVec4(entity);
            if (fillColor.w > 0) {
                uiRenderer.drawRect(x, y, width, height, fillColor, zIndex);
            }
        }
    }

    private boolean renderSpriteToBackend(Object spriteObj, Object colorObj,
                                          float x, float y, float width, float height, float zIndex) {
        Sprite sprite = resolveSprite(spriteObj);
        if (sprite == null) return false;

        Vector4f tint = parseColorVec4(colorObj);
        uiRenderer.drawSprite(sprite, x, y, width, height, zIndex, tint);
        return true;
    }

    private Sprite resolveSprite(Object spriteObj) {
        if (spriteObj == null) return null;

        if (spriteObj instanceof Sprite s) {
            return s;
        }

        if (spriteObj instanceof String spritePath && !spritePath.isEmpty()) {
            try {
                return Assets.load(spritePath, Sprite.class);
            } catch (Exception e) {
                return null;
            }
        }

        if (spriteObj instanceof Map<?, ?> spriteMap) {
            String texturePath = getStringFromMap(spriteMap, "texturePath");
            if (texturePath == null || texturePath.isEmpty()) {
                texturePath = getStringFromMap(spriteMap, "name");
            }
            if (texturePath != null && !texturePath.isEmpty()) {
                try {
                    return Assets.load(texturePath, Sprite.class);
                } catch (Exception e) {
                    try {
                        Texture texture = Assets.load(texturePath, Texture.class);
                        if (texture != null) {
                            float u0 = getFloatFromMap(spriteMap, "u0", 0f);
                            float v0 = getFloatFromMap(spriteMap, "v0", 0f);
                            float u1 = getFloatFromMap(spriteMap, "u1", 1f);
                            float v1 = getFloatFromMap(spriteMap, "v1", 1f);
                            float w = getFloatFromMap(spriteMap, "width", texture.getWidth());
                            float h = getFloatFromMap(spriteMap, "height", texture.getHeight());
                            Sprite sprite = new Sprite(texture, w, h);
                            sprite.setUVs(u0, v0, u1, v1);
                            return sprite;
                        }
                    } catch (Exception e2) {
                        return null;
                    }
                }
            }
        }

        return null;
    }

    private void displayUITexture(imgui.ImDrawList drawList) {
        if (uiRenderer == null || uiRenderer.getOutputTexture() == 0) return;

        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

        Vector2f topLeft = canvasToScreen(0, 0);
        Vector2f bottomRight = canvasToScreen(canvasWidth, canvasHeight);

        float left = viewportX + topLeft.x;
        float top = viewportY + topLeft.y;
        float right = viewportX + bottomRight.x;
        float bottom = viewportY + bottomRight.y;

        drawList.addImage(
                uiRenderer.getOutputTexture(),
                left, top, right, bottom,
                0, 0, 1, 1
        );
    }

    private float getEntityZIndex(EditorGameObject entity) {
        return entity.getOrder();
    }

    private void drawTextElements(imgui.ImDrawList drawList) {
        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        for (EditorGameObject entity : scene.getEntities()) {
            if (!entity.hasComponent(UIText.class)) continue;

            Component textComp = entity.getComponentByType("UIText");
            if (textComp == null) continue;

            float[] bounds = calculateElementBounds(entity);
            if (bounds == null) continue;

            Vector2f screenPos = canvasToScreen(bounds[0], bounds[1]);
            Vector2f screenEnd = canvasToScreen(bounds[0] + bounds[2], bounds[1] + bounds[3]);

            float left = viewportX + screenPos.x;
            float top = viewportY + screenPos.y;
            float right = viewportX + screenEnd.x;
            float bottom = viewportY + screenEnd.y;

            renderTextElement(drawList, textComp, left, top, right, bottom);
        }
    }

    private void renderTextElement(imgui.ImDrawList drawList, Component textComp,
                                   float left, float top, float right, float bottom) {
        String text = ComponentReflectionUtils.getString(textComp, "text", "");
        if (text.isEmpty()) {
            text = "[Empty Text]";
        }

        int color = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f);
        Object colorObj = ComponentReflectionUtils.getFieldValue(textComp, "color");
        if (colorObj instanceof Vector4f v) {
            color = ImGui.colorConvertFloat4ToU32(v.x, v.y, v.z, v.w);
        }

        Object hAlignObj = ComponentReflectionUtils.getFieldValue(textComp, "horizontalAlignment");
        String hAlign = hAlignObj != null ? hAlignObj.toString().toUpperCase() : "LEFT";

        Object vAlignObj = ComponentReflectionUtils.getFieldValue(textComp, "verticalAlignment");
        String vAlign = vAlignObj != null ? vAlignObj.toString().toUpperCase() : "TOP";

        boolean wordWrap = ComponentReflectionUtils.getBoolean(textComp, "wordWrap", false);

        com.pocket.rpg.ui.text.Font font = loadFontFromField(ComponentReflectionUtils.getFieldValue(textComp, "font"));

        float boxWidth = right - left;
        float boxHeight = bottom - top;

        String[] lines = splitTextIntoLines(text, font, boxWidth, wordWrap);

        float lineHeight;
        float[] lineWidths = new float[lines.length];

        if (font != null) {
            lineHeight = font.getLineHeight() * zoom;
            for (int i = 0; i < lines.length; i++) {
                lineWidths[i] = font.getStringWidth(lines[i]) * zoom;
            }
        } else {
            ImVec2 textSize = new ImVec2();
            ImGui.calcTextSize(textSize, "Hg");
            lineHeight = textSize.y;
            for (int i = 0; i < lines.length; i++) {
                ImGui.calcTextSize(textSize, lines[i]);
                lineWidths[i] = textSize.x;
            }
        }

        float totalTextHeight = lines.length * lineHeight;

        float startY;
        switch (vAlign) {
            case "MIDDLE" -> startY = top + (boxHeight - totalTextHeight) / 2;
            case "BOTTOM" -> startY = bottom - totalTextHeight;
            default -> startY = top;
        }

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            float lineWidth = lineWidths[i];

            float lineX;
            switch (hAlign) {
                case "CENTER" -> lineX = left + (boxWidth - lineWidth) / 2;
                case "RIGHT" -> lineX = right - lineWidth;
                default -> lineX = left;
            }

            float lineY = startY + i * lineHeight;
            drawList.addText(lineX, lineY, color, line);
        }
    }

    private String[] splitTextIntoLines(String text, com.pocket.rpg.ui.text.Font font,
                                        float maxWidth, boolean wordWrap) {
        String[] paragraphs = text.split("\n", -1);

        if (!wordWrap || maxWidth <= 0) {
            return paragraphs;
        }

        List<String> wrappedLines = new ArrayList<>();

        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                wrappedLines.add("");
                continue;
            }

            String[] words = paragraph.split(" ");
            StringBuilder currentLine = new StringBuilder();
            float currentWidth = 0;
            float spaceWidth = getTextWidth(" ", font);

            for (String word : words) {
                float wordWidth = getTextWidth(word, font);

                if (currentLine.length() == 0) {
                    currentLine.append(word);
                    currentWidth = wordWidth;
                } else if (currentWidth + spaceWidth + wordWidth <= maxWidth) {
                    currentLine.append(" ").append(word);
                    currentWidth += spaceWidth + wordWidth;
                } else {
                    wrappedLines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                    currentWidth = wordWidth;
                }
            }

            if (currentLine.length() > 0) {
                wrappedLines.add(currentLine.toString());
            }
        }

        return wrappedLines.toArray(new String[0]);
    }

    private float getTextWidth(String text, com.pocket.rpg.ui.text.Font font) {
        if (font != null) {
            return font.getStringWidth(text);
        }
        ImVec2 size = new ImVec2();
        ImGui.calcTextSize(size, text);
        return size.x;
    }

    private com.pocket.rpg.ui.text.Font loadFontFromField(Object fontObj) {
        if (fontObj == null) return null;

        if (fontObj instanceof com.pocket.rpg.ui.text.Font f) {
            return f;
        }

        String fontPath = null;

        if (fontObj instanceof String s && !s.isEmpty()) {
            fontPath = s;
        } else if (fontObj instanceof Map<?, ?> fontMap) {
            Object pathObj = fontMap.get("path");
            if (pathObj != null) {
                fontPath = pathObj.toString();
            }
        }

        if (fontPath != null && !fontPath.isEmpty()) {
            try {
                return Assets.load(fontPath, com.pocket.rpg.ui.text.Font.class);
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    private void drawSelectionBorders(imgui.ImDrawList drawList) {
        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        for (EditorGameObject entity : scene.getEntities()) {
            if (!isUIEntity(entity)) continue;
            if (entity.hasComponent(UICanvas.class)) continue;

            float[] bounds = calculateElementBounds(entity);
            if (bounds == null) continue;

            Vector2f screenPos = canvasToScreen(bounds[0], bounds[1]);
            Vector2f screenEnd = canvasToScreen(bounds[0] + bounds[2], bounds[1] + bounds[3]);

            float left = viewportX + screenPos.x;
            float top = viewportY + screenPos.y;
            float right = viewportX + screenEnd.x;
            float bottom = viewportY + screenEnd.y;

            boolean selected = scene.isSelected(entity);

            int borderColor = selected ? COLOR_SELECTION
                    : ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 0.8f);
            drawList.addRect(left, top, right, bottom, borderColor, 0, 0, selected ? 2.0f : 1.0f);
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

        drawList.addRectFilled(left, top, right, bottom,
                ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1.0f));

        drawList.addRect(left, top, right, bottom,
                ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1.0f), 0, 0, 2.0f);

        String label = gameConfig.getGameWidth() + " x " + gameConfig.getGameHeight();
        drawList.addText(left + 5, top + 5,
                ImGui.colorConvertFloat4ToU32(0.6f, 0.6f, 0.6f, 1.0f), label);
    }

    private void drawSelectionGizmos(imgui.ImDrawList drawList) {
        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        hoveredHandle = null;
        hoveredHandleEntity = null;

        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;

        for (EditorGameObject entity : scene.getSelectedEntities()) {
            if (!isUIEntity(entity)) continue;
            if (entity.hasComponent(UICanvas.class)) continue;

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

    private void drawEntityGizmos(imgui.ImDrawList drawList, EditorGameObject entity) {
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

        drawResizeHandles(drawList, entity, left, top, right, bottom);
        drawAnchorPoint(drawList, entity, x, y, width, height);
        drawPivotPoint(drawList, entity, x, y, width, height);
    }

    private void drawResizeHandles(imgui.ImDrawList drawList, EditorGameObject entity,
                                   float left, float top, float right, float bottom) {
        float midX = (left + right) / 2;
        float midY = (top + bottom) / 2;

        boolean isThisEntity = entity == hoveredHandleEntity;

        drawHandle(drawList, left, top, isThisEntity && hoveredHandle == ResizeHandle.TOP_LEFT);
        drawHandle(drawList, right, top, isThisEntity && hoveredHandle == ResizeHandle.TOP_RIGHT);
        drawHandle(drawList, left, bottom, isThisEntity && hoveredHandle == ResizeHandle.BOTTOM_LEFT);
        drawHandle(drawList, right, bottom, isThisEntity && hoveredHandle == ResizeHandle.BOTTOM_RIGHT);

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

    private void drawAnchorPoint(imgui.ImDrawList drawList, EditorGameObject entity,
                                 float elemX, float elemY, float elemWidth, float elemHeight) {
        Component transform = entity.getComponentByType("UITransform");
        if (transform == null) return;

        Vector2f anchor = getVector2f(transform, "anchor");

        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

        float parentWidth = canvasWidth;
        float parentHeight = canvasHeight;
        float parentX = 0;
        float parentY = 0;

        EditorGameObject parent = findParentWithUITransform(entity);
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

        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;
        boolean hovered = Math.abs(localX - screenAnchor.x) < 12 && Math.abs(localY - screenAnchor.y) < 12;

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

        if (showAnchorLines) {
            float elemCenterX = elemX + elemWidth / 2;
            float elemCenterY = elemY + elemHeight / 2;
            Vector2f screenCenter = canvasToScreen(elemCenterX, elemCenterY);
            drawList.addLine(sx, sy, viewportX + screenCenter.x, viewportY + screenCenter.y,
                    ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 0.3f), 1f);
        }

        if (hovered || isDraggingAnchor) {
            String label = String.format("Anchor (%.2f, %.2f)", anchor.x, anchor.y);
            drawList.addText(sx + 10, sy - 10, COLOR_ANCHOR, label);
        }
    }

    private void drawPivotPoint(imgui.ImDrawList drawList, EditorGameObject entity,
                                float elemX, float elemY, float elemWidth, float elemHeight) {
        Component transform = entity.getComponentByType("UITransform");
        if (transform == null) return;

        Vector2f pivot = getVector2f(transform, "pivot");

        float pivotX = elemX + pivot.x * elemWidth;
        float pivotY = elemY + pivot.y * elemHeight;

        Vector2f screenPivot = canvasToScreen(pivotX, pivotY);
        float sx = viewportX + screenPivot.x;
        float sy = viewportY + screenPivot.y;

        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;
        boolean hovered = Math.abs(localX - screenPivot.x) < 10 && Math.abs(localY - screenPivot.y) < 10;

        float radius = hovered ? 7 : 5;
        int color = hovered ? ImGui.colorConvertFloat4ToU32(0.4f, 0.8f, 1f, 1f) : COLOR_PIVOT;

        drawList.addCircleFilled(sx, sy, radius, color);
        drawList.addCircle(sx, sy, radius, COLOR_HANDLE_BORDER);

        drawList.addLine(sx - radius - 2, sy, sx + radius + 2, sy, color, 1f);
        drawList.addLine(sx, sy - radius - 2, sx, sy + radius + 2, color, 1f);

        if (hovered || isDraggingPivot) {
            String label = String.format("Pivot (%.2f, %.2f)", pivot.x, pivot.y);
            drawList.addText(sx + 10, sy + 5, COLOR_PIVOT, label);
        }
    }

    private void drawSnapGuides(imgui.ImDrawList drawList) {
        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

        if (draggedEntity == null) return;

        float[] bounds = calculateElementBounds(draggedEntity);
        if (bounds == null) return;

        float elemLeft = bounds[0];
        float elemTop = bounds[1];
        float elemRight = elemLeft + bounds[2];
        float elemBottom = elemTop + bounds[3];

        float threshold = SNAP_THRESHOLD / zoom;

        if (Math.abs(elemLeft) < threshold || Math.abs(elemRight) < threshold) {
            Vector2f top = canvasToScreen(0, 0);
            Vector2f bottom = canvasToScreen(0, canvasHeight);
            drawList.addLine(viewportX + top.x, viewportY + top.y,
                    viewportX + bottom.x, viewportY + bottom.y, COLOR_SNAP_GUIDE, 1f);
        }

        if (Math.abs(elemLeft - canvasWidth) < threshold || Math.abs(elemRight - canvasWidth) < threshold) {
            Vector2f top = canvasToScreen(canvasWidth, 0);
            Vector2f bottom = canvasToScreen(canvasWidth, canvasHeight);
            drawList.addLine(viewportX + top.x, viewportY + top.y,
                    viewportX + bottom.x, viewportY + bottom.y, COLOR_SNAP_GUIDE, 1f);
        }

        if (Math.abs(elemTop) < threshold || Math.abs(elemBottom) < threshold) {
            Vector2f left = canvasToScreen(0, 0);
            Vector2f right = canvasToScreen(canvasWidth, 0);
            drawList.addLine(viewportX + left.x, viewportY + left.y,
                    viewportX + right.x, viewportY + right.y, COLOR_SNAP_GUIDE, 1f);
        }

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

        if (isHovered) {
            float scroll = ImGui.getIO().getMouseWheel();
            if (scroll != 0) {
                float zoomFactor = 1.0f + scroll * 0.1f;
                zoom = Math.max(0.1f, Math.min(5.0f, zoom * zoomFactor));
            }
        }

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
            commitDragCommand();

            isDraggingElement = false;
            isDraggingHandle = false;
            isDraggingAnchor = false;
            isDraggingPivot = false;
            draggedEntity = null;
            activeHandle = null;
            dragChildStates = null;
        }
    }

    private void handleClick() {
        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;

        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        for (EditorGameObject entity : scene.getSelectedEntities()) {
            if (!isUIEntity(entity) || entity.hasComponent(UICanvas.class)) continue;

            if (hitTestAnchor(entity, localX, localY)) {
                startAnchorDrag(entity);
                return;
            }

            if (hitTestPivot(entity, localX, localY)) {
                startPivotDrag(entity);
                return;
            }

            ResizeHandle handle = hitTestHandles(entity, localX, localY);
            if (handle != null) {
                startResizeDrag(entity, handle);
                return;
            }
        }

        Vector2f canvasPos = screenToCanvas(localX, localY);

        EditorGameObject clicked = null;
        var entities = scene.getEntities();
        for (int i = entities.size() - 1; i >= 0; i--) {
            EditorGameObject entity = entities.get(i);
            if (!isUIEntity(entity)) continue;
            if (entity.hasComponent(UICanvas.class)) continue;

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

    // ========================================================================
    // DRAG START METHODS
    // ========================================================================

    private void startMoveDrag(EditorGameObject entity, float canvasX, float canvasY) {
        isDraggingElement = true;
        isDraggingHandle = false;
        isDraggingAnchor = false;
        isDraggingPivot = false;
        draggedEntity = entity;
        dragStartX = canvasX;
        dragStartY = canvasY;

        Component transform = entity.getComponentByType("UITransform");
        if (transform != null) {
            Vector2f offset = getVector2f(transform, "offset");
            entityStartOffsetX = offset.x;
            entityStartOffsetY = offset.y;

            captureOldValuesForUndo(entity, transform);
        }
    }

    private void startResizeDrag(EditorGameObject entity, ResizeHandle handle) {
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

        Component transform = entity.getComponentByType("UITransform");
        if (transform != null) {
            Vector2f offset = getVector2f(transform, "offset");
            entityStartOffsetX = offset.x;
            entityStartOffsetY = offset.y;
            entityStartWidth = ComponentReflectionUtils.getFloat(transform, "width", 100f);
            entityStartHeight = ComponentReflectionUtils.getFloat(transform, "height", 100f);

            captureOldValuesForUndo(entity, transform);

            dragChildStates = new ArrayList<>();
            captureChildStates(entity, dragChildStates);
        }
    }

    private void startAnchorDrag(EditorGameObject entity) {
        isDraggingAnchor = true;
        isDraggingElement = false;
        isDraggingHandle = false;
        isDraggingPivot = false;
        draggedEntity = entity;

        Component transform = entity.getComponentByType("UITransform");
        if (transform != null) {
            Vector2f anchor = getVector2f(transform, "anchor");
            entityStartAnchorX = anchor.x;
            entityStartAnchorY = anchor.y;

            captureOldValuesForUndo(entity, transform);
        }
    }

    private void startPivotDrag(EditorGameObject entity) {
        isDraggingPivot = true;
        isDraggingElement = false;
        isDraggingHandle = false;
        isDraggingAnchor = false;
        draggedEntity = entity;

        Component transform = entity.getComponentByType("UITransform");
        if (transform != null) {
            Vector2f pivot = getVector2f(transform, "pivot");
            entityStartPivotX = pivot.x;
            entityStartPivotY = pivot.y;

            captureOldValuesForUndo(entity, transform);
        }
    }

    private void captureOldValuesForUndo(EditorGameObject entity, Component transform) {
        dragOldOffset = new Vector2f(getVector2f(transform, "offset"));
        dragOldWidth = ComponentReflectionUtils.getFloat(transform, "width", 100f);
        dragOldHeight = ComponentReflectionUtils.getFloat(transform, "height", 100f);
        dragOldAnchor = new Vector2f(getVector2f(transform, "anchor"));
        dragOldPivot = new Vector2f(getVector2f(transform, "pivot"));
    }

    private void captureChildStates(EditorGameObject parent, List<UITransformDragCommand.ChildTransformState> states) {
        for (EditorGameObject child : parent.getChildren()) {
            Component childTransform = child.getComponentByType("UITransform");
            if (childTransform != null) {
                Vector2f offset = getVector2f(childTransform, "offset");
                float width = ComponentReflectionUtils.getFloat(childTransform, "width", 100f);
                float height = ComponentReflectionUtils.getFloat(childTransform, "height", 100f);

                UITransformDragCommand.ChildTransformState state =
                        new UITransformDragCommand.ChildTransformState(child, childTransform, offset, width, height);
                states.add(state);

                captureChildStates(child, states);
            }
        }
    }

    private void commitDragCommand() {
        if (draggedEntity == null || dragOldOffset == null) return;

        Component transform = draggedEntity.getComponentByType("UITransform");
        if (transform == null) return;

        Vector2f newOffset = getVector2f(transform, "offset");
        float newWidth = ComponentReflectionUtils.getFloat(transform, "width", 100f);
        float newHeight = ComponentReflectionUtils.getFloat(transform, "height", 100f);
        Vector2f newAnchor = getVector2f(transform, "anchor");
        Vector2f newPivot = getVector2f(transform, "pivot");

        String description;
        if (isDraggingHandle) {
            description = "Resize " + draggedEntity.getName();
        } else if (isDraggingAnchor) {
            description = "Move Anchor " + draggedEntity.getName();
        } else if (isDraggingPivot) {
            description = "Move Pivot " + draggedEntity.getName();
        } else {
            description = "Move " + draggedEntity.getName();
        }

        UITransformDragCommand command = new UITransformDragCommand(
                draggedEntity, transform,
                dragOldOffset, dragOldWidth, dragOldHeight, dragOldAnchor, dragOldPivot,
                newOffset, newWidth, newHeight, newAnchor, newPivot,
                description
        );

        if (dragChildStates != null) {
            for (UITransformDragCommand.ChildTransformState state : dragChildStates) {
                state.captureNewValues();
                command.addChildState(state);
            }
        }

        if (command.hasChanges()) {
            command.undo();
            UndoManager.getInstance().execute(command);

            EditorScene scene = context.getCurrentScene();
            if (scene != null) {
                scene.markDirty();
            }
        }

        dragOldOffset = null;
    }

    // ========================================================================
    // DRAG HANDLERS
    // ========================================================================

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

        if (snapEnabled) {
            Component transform = draggedEntity.getComponentByType("UITransform");
            if (transform != null) {
                float width = ComponentReflectionUtils.getFloat(transform, "width", 100f);
                float height = ComponentReflectionUtils.getFloat(transform, "height", 100f);
                Vector2f anchor = getVector2f(transform, "anchor");
                Vector2f pivot = getVector2f(transform, "pivot");

                float canvasWidth = gameConfig.getGameWidth();
                float canvasHeight = gameConfig.getGameHeight();

                float anchorX = anchor.x * canvasWidth;
                float anchorY = anchor.y * canvasHeight;
                float elemX = anchorX + newOffsetX - pivot.x * width;
                float elemY = anchorY + newOffsetY - pivot.y * height;
                float elemRight = elemX + width;
                float elemBottom = elemY + height;

                float threshold = SNAP_THRESHOLD / zoom;

                if (Math.abs(elemX) < threshold) {
                    newOffsetX = pivot.x * width - anchorX;
                }
                if (Math.abs(elemRight - canvasWidth) < threshold) {
                    newOffsetX = canvasWidth - width + pivot.x * width - anchorX;
                }
                if (Math.abs(elemY) < threshold) {
                    newOffsetY = pivot.y * height - anchorY;
                }
                if (Math.abs(elemBottom - canvasHeight) < threshold) {
                    newOffsetY = canvasHeight - height + pivot.y * height - anchorY;
                }
            }
        }

        Component transform = draggedEntity.getComponentByType("UITransform");
        if (transform != null) {
            ComponentReflectionUtils.setFieldValue(transform, "offset", new Vector2f(newOffsetX, newOffsetY));

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

        Component transform = draggedEntity.getComponentByType("UITransform");
        if (transform == null) return;

        Vector2f pivot = getVector2f(transform, "pivot");

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

        float scaleX = newWidth / entityStartWidth;
        float scaleY = newHeight / entityStartHeight;

        ComponentReflectionUtils.setFieldValue(transform, "width", newWidth);
        ComponentReflectionUtils.setFieldValue(transform, "height", newHeight);
        ComponentReflectionUtils.setFieldValue(transform, "offset", new Vector2f(newOffsetX, newOffsetY));

        if (dragChildStates != null) {
            applyCascadingResize(scaleX, scaleY);
        }

        EditorScene scene = context.getCurrentScene();
        if (scene != null) {
            scene.markDirty();
        }
    }

    private void applyCascadingResize(float scaleX, float scaleY) {
        if (dragChildStates == null) return;

        for (UITransformDragCommand.ChildTransformState state : dragChildStates) {
            Component childTransform = state.entity.getComponentByType("UITransform");
            if (childTransform == null) continue;

            float newWidth = state.getOldWidth() * scaleX;
            float newHeight = state.getOldHeight() * scaleY;

            float newOffsetX = state.getOldOffset().x * scaleX;
            float newOffsetY = state.getOldOffset().y * scaleY;

            ComponentReflectionUtils.setFieldValue(childTransform, "width", Math.max(1, newWidth));
            ComponentReflectionUtils.setFieldValue(childTransform, "height", Math.max(1, newHeight));
            ComponentReflectionUtils.setFieldValue(childTransform, "offset", new Vector2f(newOffsetX, newOffsetY));
        }
    }

    private void handleAnchorDrag() {
        if (draggedEntity == null) return;

        ImVec2 mousePos = ImGui.getMousePos();
        Vector2f canvasPos = screenToCanvas(mousePos.x - viewportX, mousePos.y - viewportY);

        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

        float parentWidth = canvasWidth;
        float parentHeight = canvasHeight;
        float parentX = 0;
        float parentY = 0;

        EditorGameObject parent = findParentWithUITransform(draggedEntity);
        if (parent != null) {
            float[] parentBounds = calculateElementBounds(parent);
            if (parentBounds != null) {
                parentX = parentBounds[0];
                parentY = parentBounds[1];
                parentWidth = parentBounds[2];
                parentHeight = parentBounds[3];
            }
        }

        float newAnchorX = (canvasPos.x - parentX) / parentWidth;
        float newAnchorY = (canvasPos.y - parentY) / parentHeight;

        newAnchorX = Math.max(0, Math.min(1, newAnchorX));
        newAnchorY = Math.max(0, Math.min(1, newAnchorY));

        float snapThreshold = 0.05f;
        newAnchorX = snapToValue(newAnchorX, 0f, snapThreshold);
        newAnchorX = snapToValue(newAnchorX, 0.5f, snapThreshold);
        newAnchorX = snapToValue(newAnchorX, 1f, snapThreshold);
        newAnchorY = snapToValue(newAnchorY, 0f, snapThreshold);
        newAnchorY = snapToValue(newAnchorY, 0.5f, snapThreshold);
        newAnchorY = snapToValue(newAnchorY, 1f, snapThreshold);

        Component transform = draggedEntity.getComponentByType("UITransform");
        if (transform != null) {
            Vector2f oldAnchor = getVector2f(transform, "anchor");
            Vector2f offset = getVector2f(transform, "offset");

            float anchorDeltaX = (newAnchorX - oldAnchor.x) * parentWidth;
            float anchorDeltaY = (newAnchorY - oldAnchor.y) * parentHeight;

            ComponentReflectionUtils.setFieldValue(transform, "anchor", new Vector2f(newAnchorX, newAnchorY));
            ComponentReflectionUtils.setFieldValue(transform, "offset", new Vector2f(offset.x - anchorDeltaX, offset.y - anchorDeltaY));

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

        Component transform = draggedEntity.getComponentByType("UITransform");
        if (transform == null) return;

        float width = ComponentReflectionUtils.getFloat(transform, "width", 100f);
        float height = ComponentReflectionUtils.getFloat(transform, "height", 100f);

        Vector2f oldPivot = getVector2f(transform, "pivot");
        Vector2f offset = getVector2f(transform, "offset");
        Vector2f anchor = getVector2f(transform, "anchor");

        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();
        float parentWidth = canvasWidth;
        float parentHeight = canvasHeight;
        float parentX = 0;
        float parentY = 0;

        EditorGameObject parent = findParentWithUITransform(draggedEntity);
        if (parent != null) {
            float[] parentBounds = calculateElementBounds(parent);
            if (parentBounds != null) {
                parentX = parentBounds[0];
                parentY = parentBounds[1];
                parentWidth = parentBounds[2];
                parentHeight = parentBounds[3];
            }
        }

        float anchorPosX = parentX + anchor.x * parentWidth + offset.x;
        float anchorPosY = parentY + anchor.y * parentHeight + offset.y;

        float newPivotX = (canvasPos.x - anchorPosX + oldPivot.x * width) / width;
        float newPivotY = (canvasPos.y - anchorPosY + oldPivot.y * height) / height;

        newPivotX = Math.max(0, Math.min(1, newPivotX));
        newPivotY = Math.max(0, Math.min(1, newPivotY));

        float snapThreshold = 0.05f;
        newPivotX = snapToValue(newPivotX, 0f, snapThreshold);
        newPivotX = snapToValue(newPivotX, 0.5f, snapThreshold);
        newPivotX = snapToValue(newPivotX, 1f, snapThreshold);
        newPivotY = snapToValue(newPivotY, 0f, snapThreshold);
        newPivotY = snapToValue(newPivotY, 0.5f, snapThreshold);
        newPivotY = snapToValue(newPivotY, 1f, snapThreshold);

        float pivotDeltaX = (newPivotX - oldPivot.x) * width;
        float pivotDeltaY = (newPivotY - oldPivot.y) * height;

        ComponentReflectionUtils.setFieldValue(transform, "pivot", new Vector2f(newPivotX, newPivotY));
        ComponentReflectionUtils.setFieldValue(transform, "offset", new Vector2f(offset.x + pivotDeltaX, offset.y + pivotDeltaY));

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

    // ========================================================================
    // HIT TESTING
    // ========================================================================

    private boolean hitTestAnchor(EditorGameObject entity, float screenX, float screenY) {
        float[] anchorPos = calculateAnchorScreenPos(entity);
        if (anchorPos == null) return false;

        float hitSize = 12f;
        return Math.abs(screenX - anchorPos[0]) < hitSize &&
                Math.abs(screenY - anchorPos[1]) < hitSize;
    }

    private boolean hitTestPivot(EditorGameObject entity, float screenX, float screenY) {
        float[] pivotPos = calculatePivotScreenPos(entity);
        if (pivotPos == null) return false;

        float hitSize = 10f;
        return Math.abs(screenX - pivotPos[0]) < hitSize &&
                Math.abs(screenY - pivotPos[1]) < hitSize;
    }

    private float[] calculateAnchorScreenPos(EditorGameObject entity) {
        Component transform = entity.getComponentByType("UITransform");
        if (transform == null) return null;

        Vector2f anchor = getVector2f(transform, "anchor");

        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

        float parentWidth = canvasWidth;
        float parentHeight = canvasHeight;
        float parentX = 0;
        float parentY = 0;

        EditorGameObject parent = findParentWithUITransform(entity);
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

    private float[] calculatePivotScreenPos(EditorGameObject entity) {
        float[] bounds = calculateElementBounds(entity);
        if (bounds == null) return null;

        Component transform = entity.getComponentByType("UITransform");
        if (transform == null) return null;

        Vector2f pivot = getVector2f(transform, "pivot");

        float pivotX = bounds[0] + pivot.x * bounds[2];
        float pivotY = bounds[1] + pivot.y * bounds[3];

        Vector2f screenPos = canvasToScreen(pivotX, pivotY);
        return new float[]{screenPos.x, screenPos.y};
    }

    private ResizeHandle hitTestHandles(EditorGameObject entity, float screenX, float screenY) {
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

        if (hitTestHandle(screenX, screenY, left, top, hitSize)) return ResizeHandle.TOP_LEFT;
        if (hitTestHandle(screenX, screenY, right, top, hitSize)) return ResizeHandle.TOP_RIGHT;
        if (hitTestHandle(screenX, screenY, left, bottom, hitSize)) return ResizeHandle.BOTTOM_LEFT;
        if (hitTestHandle(screenX, screenY, right, bottom, hitSize)) return ResizeHandle.BOTTOM_RIGHT;

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

    private boolean hitTest(EditorGameObject entity, float canvasX, float canvasY) {
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

    private float[] calculateElementBounds(EditorGameObject entity) {
        Component transformComp = entity.getComponentByType("UITransform");
        if (transformComp == null) return null;

        float width = ComponentReflectionUtils.getFloat(transformComp, "width", 100f);
        float height = ComponentReflectionUtils.getFloat(transformComp, "height", 100f);
        Vector2f offset = getVector2f(transformComp, "offset");
        Vector2f anchor = getVector2f(transformComp, "anchor");
        Vector2f pivot = getVector2f(transformComp, "pivot");

        float canvasWidth = gameConfig.getGameWidth();
        float canvasHeight = gameConfig.getGameHeight();

        float parentWidth = canvasWidth;
        float parentHeight = canvasHeight;
        float parentX = 0;
        float parentY = 0;

        EditorGameObject parent = findParentWithUITransform(entity);
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

    private EditorGameObject findParentWithUITransform(EditorGameObject entity) {
        EditorGameObject parent = entity.getParent();
        while (parent != null) {
            if (parent.hasComponent(UITransform.class) || parent.hasComponent(UICanvas.class)) {
                if (parent.hasComponent(UITransform.class)) {
                    return parent;
                }
                return null;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private boolean isUIEntity(EditorGameObject entity) {
        return entity.hasComponent(UICanvas.class) ||
                entity.hasComponent(UITransform.class) ||
                entity.hasComponent(UIPanel.class) ||
                entity.hasComponent(UIImage.class) ||
                entity.hasComponent(UIButton.class) ||
                entity.hasComponent(UIText.class);
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

    private Vector2f getVector2f(Component comp, String fieldName) {
        Object value = ComponentReflectionUtils.getFieldValue(comp, fieldName);
        if (value instanceof Vector2f v) return new Vector2f(v);
        return new Vector2f(0, 0);
    }

    private Vector4f parseColorVec4(Object colorObj) {
        if (colorObj instanceof Vector4f v) {
            return new Vector4f(v);
        }
        if (colorObj instanceof Map<?, ?> colorMap) {
            float r = getFloatFromMap(colorMap, "x", 1f);
            float g = getFloatFromMap(colorMap, "y", 1f);
            float b = getFloatFromMap(colorMap, "z", 1f);
            float a = getFloatFromMap(colorMap, "w", 1f);
            return new Vector4f(r, g, b, a);
        }
        return new Vector4f(1f, 1f, 1f, 1f);
    }

    private Vector4f getElementFillColorVec4(EditorGameObject entity) {
        if (entity.hasComponent(UIPanel.class)) {
            return new Vector4f(0.3f, 0.3f, 0.4f, 0.6f);
        } else if (entity.hasComponent(UIButton.class)) {
            return new Vector4f(0.3f, 0.5f, 0.3f, 0.6f);
        } else if (entity.hasComponent(UIImage.class)) {
            return new Vector4f(0.4f, 0.4f, 0.3f, 0.6f);
        } else if (entity.hasComponent(UIText.class)) {
            return new Vector4f(0.4f, 0.3f, 0.4f, 0.6f);
        } else {
            return new Vector4f(0.3f, 0.3f, 0.3f, 0.4f);
        }
    }

    private String getStringFromMap(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private float getFloatFromMap(Map<?, ?> map, String key, float defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.floatValue();
        }
        return defaultValue;
    }

    public void destroy() {
        if (uiRenderer != null) {
            uiRenderer.destroy();
            uiRenderer = null;
        }
        rendererInitialized = false;
    }
}
