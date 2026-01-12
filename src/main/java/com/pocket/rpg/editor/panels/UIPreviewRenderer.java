package com.pocket.rpg.editor.panels;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.*;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders UI elements from EditorScene.
 * Can be used by both UI Designer panel and Game Panel.
 */
public class UIPreviewRenderer {

    private final GameConfig gameConfig;

    public UIPreviewRenderer(GameConfig gameConfig) {
        this.gameConfig = gameConfig;
    }

    /**
     * Renders all UI elements from the scene onto the given draw list.
     */
    public void render(ImDrawList drawList, EditorScene scene,
                       float viewportX, float viewportY,
                       float scale, float offsetX, float offsetY) {
        if (scene == null) return;

        // Collect and sort UI entities by canvas sort order
        List<UIRenderEntry> renderQueue = new ArrayList<>();

        for (EditorGameObject entity : scene.getEntities()) {
            if (!isUIEntity(entity)) continue;
            if (entity.hasComponent(UICanvas.class)) continue;

            int sortOrder = getCanvasSortOrder(entity);
            renderQueue.add(new UIRenderEntry(entity, sortOrder));
        }

        renderQueue.sort(Comparator.comparingInt(e -> e.sortOrder));

        for (UIRenderEntry entry : renderQueue) {
            renderUIElement(drawList, entry.entity, viewportX, viewportY, scale, offsetX, offsetY);
        }
    }

    private void renderUIElement(ImDrawList drawList, EditorGameObject entity,
                                 float viewportX, float viewportY,
                                 float scale, float offsetX, float offsetY) {
        float[] bounds = calculateElementBounds(entity);
        if (bounds == null) return;

        float x = bounds[0];
        float y = bounds[1];
        float width = bounds[2];
        float height = bounds[3];

        float left = viewportX + offsetX + x * scale;
        float top = viewportY + offsetY + y * scale;
        float right = left + width * scale;
        float bottom = top + height * scale;

        boolean contentRendered = false;

        // UIImage
        Component imageComp = entity.getComponentByType("UIImage");
        if (imageComp != null) {
            Sprite sprite = getSprite(imageComp, "sprite");
            Vector4f color = getColor(imageComp, "color");
            contentRendered = renderSprite(drawList, sprite, color, left, top, right, bottom);
        }

        // UIButton
        if (!contentRendered) {
            Component buttonComp = entity.getComponentByType("UIButton");
            if (buttonComp != null) {
                Sprite sprite = getSprite(buttonComp, "sprite");
                Vector4f color = getColor(buttonComp, "color");
                contentRendered = renderSprite(drawList, sprite, color, left, top, right, bottom);

                if (!contentRendered && color != null) {
                    int colorInt = ImGui.colorConvertFloat4ToU32(color.x, color.y, color.z, color.w);
                    drawList.addRectFilled(left, top, right, bottom, colorInt);
                    contentRendered = true;
                }
            }
        }

        // UIPanel
        if (!contentRendered) {
            Component panelComp = entity.getComponentByType("UIPanel");
            if (panelComp != null) {
                Sprite sprite = getSprite(panelComp, "backgroundSprite");
                Vector4f color = getColor(panelComp, "backgroundColor");
                contentRendered = renderSprite(drawList, sprite, color, left, top, right, bottom);

                if (!contentRendered && color != null) {
                    int colorInt = ImGui.colorConvertFloat4ToU32(color.x, color.y, color.z, color.w);
                    drawList.addRectFilled(left, top, right, bottom, colorInt);
                    contentRendered = true;
                }
            }
        }

        // UIText
        Component textComp = entity.getComponentByType("UIText");
        if (textComp != null) {
            renderTextElement(drawList, textComp, left, top, right, bottom);
        }

        // Fallback
        if (!contentRendered && textComp == null) {
            int fillColor = getElementFillColor(entity);
            if (fillColor != 0) {
                drawList.addRectFilled(left, top, right, bottom, fillColor);
            }
        }
    }

    private Sprite getSprite(Component comp, String fieldName) {
        Object value = ComponentReflectionUtils.getFieldValue(comp, fieldName);
        if (value instanceof Sprite s) return s;
        if (value instanceof String path && !path.isEmpty()) {
            try {
                return Assets.load(path, Sprite.class);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private Vector4f getColor(Component comp, String fieldName) {
        Object value = ComponentReflectionUtils.getFieldValue(comp, fieldName);
        if (value instanceof Vector4f v) return v;
        return new Vector4f(1f, 1f, 1f, 1f);
    }

    private boolean renderSprite(ImDrawList drawList, Sprite sprite, Vector4f color,
                                 float left, float top, float right, float bottom) {
        if (sprite == null || sprite.getTexture() == null) return false;

        int textureId = sprite.getTexture().getTextureId();
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();

        int tintColor = color != null
                ? ImGui.colorConvertFloat4ToU32(color.x, color.y, color.z, color.w)
                : ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f);
        drawList.addImage(textureId, left, top, right, bottom, u0, v0, u1, v1, tintColor);
        return true;
    }

    private void renderTextElement(ImDrawList drawList, Component textComp,
                                   float left, float top, float right, float bottom) {
        String text = ComponentReflectionUtils.getString(textComp, "text", "");
        if (text.isEmpty()) return;

        Vector4f colorVec = getColor(textComp, "color");
        int color = ImGui.colorConvertFloat4ToU32(colorVec.x, colorVec.y, colorVec.z, colorVec.w);

        Object hAlignObj = ComponentReflectionUtils.getFieldValue(textComp, "horizontalAlignment");
        String hAlign = hAlignObj != null ? hAlignObj.toString().toUpperCase() : "LEFT";

        Object vAlignObj = ComponentReflectionUtils.getFieldValue(textComp, "verticalAlignment");
        String vAlign = vAlignObj != null ? vAlignObj.toString().toUpperCase() : "TOP";

        boolean wordWrap = ComponentReflectionUtils.getBoolean(textComp, "wordWrap", false);

        com.pocket.rpg.ui.text.Font font = loadFontFromComponent(textComp);

        float boxWidth = right - left;
        float boxHeight = bottom - top;

        String[] lines = splitTextIntoLines(text, font, boxWidth, wordWrap);

        float lineHeight;
        float[] lineWidths = new float[lines.length];

        if (font != null) {
            lineHeight = font.getLineHeight();
            for (int i = 0; i < lines.length; i++) {
                lineWidths[i] = font.getStringWidth(lines[i]);
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

    private com.pocket.rpg.ui.text.Font loadFontFromComponent(Component comp) {
        Object fontObj = ComponentReflectionUtils.getFieldValue(comp, "font");
        if (fontObj == null) return null;

        if (fontObj instanceof com.pocket.rpg.ui.text.Font f) {
            return f;
        }

        if (fontObj instanceof String fontPath && !fontPath.isEmpty()) {
            try {
                return Assets.load(fontPath, com.pocket.rpg.ui.text.Font.class);
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    private int getElementFillColor(EditorGameObject entity) {
        if (entity.hasComponent(UIPanel.class)) {
            return ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.4f, 0.6f);
        } else if (entity.hasComponent(UIButton.class)) {
            return ImGui.colorConvertFloat4ToU32(0.3f, 0.5f, 0.3f, 0.6f);
        } else if (entity.hasComponent(UIImage.class)) {
            return ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.3f, 0.6f);
        } else if (entity.hasComponent(UIText.class)) {
            return 0;
        } else {
            return ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 0.4f);
        }
    }

    private float[] calculateElementBounds(EditorGameObject entity) {
        Component transformComp = entity.getComponentByType("UITransform");
        if (transformComp == null) return null;

        float width = ComponentReflectionUtils.getFloat(transformComp, "width", 100f);
        float height = ComponentReflectionUtils.getFloat(transformComp, "height", 100f);

        Object offsetObj = ComponentReflectionUtils.getFieldValue(transformComp, "offset");
        Vector2f offset = offsetObj instanceof Vector2f v ? v : new Vector2f(0, 0);

        Object anchorObj = ComponentReflectionUtils.getFieldValue(transformComp, "anchor");
        Vector2f anchor = anchorObj instanceof Vector2f v ? v : new Vector2f(0, 0);

        Object pivotObj = ComponentReflectionUtils.getFieldValue(transformComp, "pivot");
        Vector2f pivot = pivotObj instanceof Vector2f v ? v : new Vector2f(0, 0);

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

    private int getCanvasSortOrder(EditorGameObject entity) {
        EditorGameObject current = entity;
        while (current != null) {
            if (current.hasComponent(UICanvas.class)) {
                Component canvas = current.getComponentByType("UICanvas");
                if (canvas != null) {
                    return ComponentReflectionUtils.getInt(canvas, "sortOrder", 0);
                }
                return 0;
            }
            current = current.getParent();
        }
        return 0;
    }

    private boolean isUIEntity(EditorGameObject entity) {
        return entity.hasComponent(UICanvas.class) ||
                entity.hasComponent(UITransform.class) ||
                entity.hasComponent(UIPanel.class) ||
                entity.hasComponent(UIImage.class) ||
                entity.hasComponent(UIButton.class) ||
                entity.hasComponent(UIText.class);
    }

    private static class UIRenderEntry {
        EditorGameObject entity;
        int sortOrder;

        UIRenderEntry(EditorGameObject entity, int sortOrder) {
            this.entity = entity;
            this.sortOrder = sortOrder;
        }
    }
}
