package com.pocket.rpg.editor.panels;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.utils.FieldEditors;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentData;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
     *
     * @param drawList   ImGui draw list to render to
     * @param scene      The scene containing UI entities
     * @param viewportX  Screen X position of viewport
     * @param viewportY  Screen Y position of viewport
     * @param scale      Scale factor (1.0 = game resolution maps 1:1 to screen)
     * @param offsetX    Offset within viewport (for centering)
     * @param offsetY    Offset within viewport (for centering)
     */
    public void render(ImDrawList drawList, EditorScene scene,
                       float viewportX, float viewportY,
                       float scale, float offsetX, float offsetY) {
        if (scene == null) return;

        // Collect and sort UI entities by canvas sort order
        List<UIRenderEntry> renderQueue = new ArrayList<>();

        for (EditorEntity entity : scene.getEntities()) {
            if (!isUIEntity(entity)) continue;
            if (entity.hasComponent("UICanvas")) continue; // Canvases don't render

            // Find canvas for sort order
            int sortOrder = getCanvasSortOrder(entity);
            renderQueue.add(new UIRenderEntry(entity, sortOrder));
        }

        // Sort by canvas sort order
        renderQueue.sort(Comparator.comparingInt(e -> e.sortOrder));

        // Render
        for (UIRenderEntry entry : renderQueue) {
            renderUIElement(drawList, entry.entity, viewportX, viewportY, scale, offsetX, offsetY);
        }
    }

    private void renderUIElement(ImDrawList drawList, EditorEntity entity,
                                 float viewportX, float viewportY,
                                 float scale, float offsetX, float offsetY) {
        float[] bounds = calculateElementBounds(entity);
        if (bounds == null) return;

        float x = bounds[0];
        float y = bounds[1];
        float width = bounds[2];
        float height = bounds[3];

        // Transform to screen coordinates
        float left = viewportX + offsetX + x * scale;
        float top = viewportY + offsetY + y * scale;
        float right = left + width * scale;
        float bottom = top + height * scale;

        // Render based on component type
        boolean contentRendered = false;

        // UIImage
        if (entity.hasComponent("UIImage")) {
            var imageComp = entity.getComponentByType("UIImage");
            if (imageComp != null) {
                contentRendered = renderSprite(drawList,
                        imageComp.getFields().get("sprite"),
                        imageComp.getFields().get("color"),
                        left, top, right, bottom);
            }
        }

        // UIButton
        if (!contentRendered && entity.hasComponent("UIButton")) {
            var buttonComp = entity.getComponentByType("UIButton");
            if (buttonComp != null) {
                contentRendered = renderSprite(drawList,
                        buttonComp.getFields().get("sprite"),
                        buttonComp.getFields().get("color"),
                        left, top, right, bottom);

                // If no sprite, draw solid color
                if (!contentRendered) {
                    int color = parseColor(buttonComp.getFields().get("color"));
                    drawList.addRectFilled(left, top, right, bottom, color);
                    contentRendered = true;
                }
            }
        }

        // UIPanel
        if (!contentRendered && entity.hasComponent("UIPanel")) {
            var panelComp = entity.getComponentByType("UIPanel");
            if (panelComp != null) {
                // Try background sprite first
                contentRendered = renderSprite(drawList,
                        panelComp.getFields().get("backgroundSprite"),
                        panelComp.getFields().get("color"),
                        left, top, right, bottom);

                // If no sprite, draw solid color
                if (!contentRendered) {
                    int color = parseColor(panelComp.getFields().get("color"));
                    drawList.addRectFilled(left, top, right, bottom, color);
                    contentRendered = true;
                }
            }
        }

        // UIText - render with alignment
        if (entity.hasComponent("UIText")) {
            var textComp = entity.getComponentByType("UIText");
            if (textComp != null) {
                renderTextElement(drawList, textComp, left, top, right, bottom);
            }
        }

        // Fallback - draw placeholder (skip for UIText)
        if (!contentRendered && !entity.hasComponent("UIText")) {
            int fillColor = getElementFillColor(entity);
            if (fillColor != 0) {
                drawList.addRectFilled(left, top, right, bottom, fillColor);
            }
        }
    }

    private boolean renderSprite(ImDrawList drawList, Object spriteObj, Object colorObj,
                                 float left, float top, float right, float bottom) {
        if (spriteObj == null) return false;

        Sprite sprite = null;

        if (spriteObj instanceof Sprite s) {
            sprite = s;
        } else if (spriteObj instanceof String spritePath && !spritePath.isEmpty()) {
            sprite = Assets.load(spritePath);
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
                        var texture = Assets.load(texturePath, com.pocket.rpg.rendering.Texture.class);
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
                        // Ignore - sprite will remain null
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

    private void renderTextElement(ImDrawList drawList, ComponentData textComp,
                                   float left, float top, float right, float bottom) {
        Map<String, Object> fields = textComp.getFields();

        String text = fields.get("text") != null ? fields.get("text").toString() : "";
        if (text.isEmpty()) {
            return;  // Don't render empty text
        }

        // Get color
        int color = parseColor(fields.get("color"));

        // Get horizontal alignment
        String hAlign = "LEFT";
        Object hAlignObj = fields.get("horizontalAlignment");
        if (hAlignObj != null) {
            hAlign = hAlignObj.toString().toUpperCase();
        }

        // Get vertical alignment
        String vAlign = "TOP";
        Object vAlignObj = fields.get("verticalAlignment");
        if (vAlignObj != null) {
            vAlign = vAlignObj.toString().toUpperCase();
        }

        // Get word wrap
        boolean wordWrap = false;
        Object wrapObj = fields.get("wordWrap");
        if (wrapObj instanceof Boolean b) {
            wordWrap = b;
        }

        // Try to get Font for accurate metrics
        com.pocket.rpg.ui.text.Font font = loadFontFromField(fields.get("font"));

        float boxWidth = right - left;
        float boxHeight = bottom - top;

        // Split text into lines (handle word wrap)
        String[] lines = splitTextIntoLines(text, font, boxWidth, wordWrap);

        // Calculate line metrics
        float lineHeight;
        float[] lineWidths = new float[lines.length];

        if (font != null) {
            lineHeight = font.getLineHeight();
            for (int i = 0; i < lines.length; i++) {
                lineWidths[i] = font.getStringWidth(lines[i]);
            }
        } else {
            ImVec2 textSize = new ImVec2();
            ImGui.calcTextSize(textSize, "Hg");  // Reference height
            lineHeight = textSize.y;
            for (int i = 0; i < lines.length; i++) {
                ImGui.calcTextSize(textSize, lines[i]);
                lineWidths[i] = textSize.x;
            }
        }

        float totalTextHeight = lines.length * lineHeight;

        // Calculate vertical start position
        float startY;
        switch (vAlign) {
            case "MIDDLE" -> startY = top + (boxHeight - totalTextHeight) / 2;
            case "BOTTOM" -> startY = bottom - totalTextHeight;
            default -> startY = top;  // TOP
        }

        // Render each line
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            float lineWidth = lineWidths[i];

            // Calculate horizontal position for this line
            float lineX;
            switch (hAlign) {
                case "CENTER" -> lineX = left + (boxWidth - lineWidth) / 2;
                case "RIGHT" -> lineX = right - lineWidth;
                default -> lineX = left;  // LEFT
            }

            float lineY = startY + i * lineHeight;

            drawList.addText(lineX, lineY, color, line);
        }
    }

    /**
     * Splits text into lines, optionally with word wrapping.
     */
    private String[] splitTextIntoLines(String text, com.pocket.rpg.ui.text.Font font,
                                        float maxWidth, boolean wordWrap) {
        // First split by explicit newlines
        String[] paragraphs = text.split("\n", -1);

        if (!wordWrap || maxWidth <= 0) {
            return paragraphs;
        }

        // Word wrap each paragraph
        java.util.List<String> wrappedLines = new java.util.ArrayList<>();

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
                    // First word on line
                    currentLine.append(word);
                    currentWidth = wordWidth;
                } else if (currentWidth + spaceWidth + wordWidth <= maxWidth) {
                    // Word fits on current line
                    currentLine.append(" ").append(word);
                    currentWidth += spaceWidth + wordWidth;
                } else {
                    // Word doesn't fit - start new line
                    wrappedLines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                    currentWidth = wordWidth;
                }
            }

            // Add remaining text
            if (currentLine.length() > 0) {
                wrappedLines.add(currentLine.toString());
            }
        }

        return wrappedLines.toArray(new String[0]);
    }

    /**
     * Gets text width using font if available, otherwise ImGui.
     */
    private float getTextWidth(String text, com.pocket.rpg.ui.text.Font font) {
        if (font != null) {
            return font.getStringWidth(text);
        }
        ImVec2 size = new ImVec2();
        ImGui.calcTextSize(size, text);
        return size.x;
    }

    /**
     * Attempts to load a Font from a field value (can be Font, String path, or Map).
     */
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
                // Font not loadable, fall back to ImGui
            }
        }

        return null;
    }

    private int getElementFillColor(EditorEntity entity) {
        if (entity.hasComponent("UIPanel")) {
            return ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.4f, 0.6f);
        } else if (entity.hasComponent("UIButton")) {
            return ImGui.colorConvertFloat4ToU32(0.3f, 0.5f, 0.3f, 0.6f);
        } else if (entity.hasComponent("UIImage")) {
            return ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.3f, 0.6f);
        } else if (entity.hasComponent("UIText")) {
            return 0; // Text doesn't need background
        } else {
            return ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 0.4f);
        }
    }

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

    private int getCanvasSortOrder(EditorEntity entity) {
        // Walk up to find canvas
        EditorEntity current = entity;
        while (current != null) {
            if (current.hasComponent("UICanvas")) {
                var canvas = current.getComponentByType("UICanvas");
                if (canvas != null) {
                    Object sortOrder = canvas.getFields().get("sortOrder");
                    if (sortOrder instanceof Number n) {
                        return n.intValue();
                    }
                }
                return 0;
            }
            current = current.getParent();
        }
        return 0;
    }

    private boolean isUIEntity(EditorEntity entity) {
        return entity.hasComponent("UICanvas") ||
                entity.hasComponent("UITransform") ||
                entity.hasComponent("UIPanel") ||
                entity.hasComponent("UIImage") ||
                entity.hasComponent("UIButton") ||
                entity.hasComponent("UIText");
    }

    private float getFloatFromMap(Map<?, ?> map, String key, float defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.floatValue();
        }
        return defaultValue;
    }

    private static class UIRenderEntry {
        EditorEntity entity;
        int sortOrder;

        UIRenderEntry(EditorEntity entity, int sortOrder) {
            this.entity = entity;
            this.sortOrder = sortOrder;
        }
    }
}