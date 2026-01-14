package com.pocket.rpg.editor.panels.uidesigner;

import com.pocket.rpg.components.ui.*;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import com.pocket.rpg.editor.rendering.UIRenderingBackend;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.ui.text.HorizontalAlignment;
import com.pocket.rpg.ui.text.VerticalAlignment;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Renders UI elements for the UI Designer panel.
 * Uses direct typed component access (no reflection).
 */
public class UIDesignerRenderer {

    private final UIDesignerState state;
    private final UIDesignerCoordinates coords;
    private final RenderingConfig renderingConfig;

    private UIRenderingBackend uiBackend;
    private boolean initialized = false;

    @Setter
    private int sceneTextureId = 0;

    // Default fill colors for elements without visual content
    private static final Vector4f FILL_PANEL = new Vector4f(0.3f, 0.3f, 0.4f, 0.6f);
    private static final Vector4f FILL_BUTTON = new Vector4f(0.3f, 0.5f, 0.3f, 0.6f);
    private static final Vector4f FILL_IMAGE = new Vector4f(0.4f, 0.4f, 0.3f, 0.6f);
    private static final Vector4f FILL_TEXT = new Vector4f(0.4f, 0.3f, 0.4f, 0.6f);
    private static final Vector4f FILL_DEFAULT = new Vector4f(0.3f, 0.3f, 0.3f, 0.4f);

    public UIDesignerRenderer(UIDesignerState state, UIDesignerCoordinates coords, RenderingConfig renderingConfig) {
        this.state = state;
        this.coords = coords;
        this.renderingConfig = renderingConfig;
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    private void init() {
        if (initialized) return;

        uiBackend = new UIRenderingBackend(renderingConfig);
        uiBackend.init(state.getCanvasWidth(), state.getCanvasHeight());
        initialized = true;
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    /**
     * Renders all UI elements to a texture.
     */
    public void renderToTexture(EditorScene scene) {
        if (!initialized) {
            init();
        }
        if (scene == null) return;

        int canvasWidth = state.getCanvasWidth();
        int canvasHeight = state.getCanvasHeight();

        uiBackend.begin(canvasWidth, canvasHeight);

        for (EditorGameObject entity : scene.getEntities()) {
            if (!coords.isUIEntity(entity)) continue;
            if (entity.hasComponent(UICanvas.class)) continue;
            renderUIElement(entity);
        }

        uiBackend.end();
    }

    /**
     * Renders a single UI element using direct typed component access.
     */
    private void renderUIElement(EditorGameObject entity) {
        float[] bounds = coords.calculateElementBounds(entity);
        if (bounds == null) return;

        float x = bounds[0];
        float y = bounds[1];
        float width = bounds[2];
        float height = bounds[3];
        float zIndex = entity.getOrder();

        // Get rotation and pivot from UITransform
        UITransform transform = entity.getComponent(UITransform.class);
        float rotation = 0;
        float originX = 0;
        float originY = 0;

        if (transform != null) {
            rotation = transform.getRotation2D();
            Vector2f pivot = transform.getPivot();
            // Origin is normalized 0-1 (SpriteBatch expects normalized values)
            // SpriteBatch uses Y-up (0=bottom), but UI uses Y-down (0=top), so flip Y
            originX = pivot.x;
            originY = 1.0f - pivot.y;
        }

        boolean contentRendered = false;

        // Try UIImage first
        UIImage image = entity.getComponent(UIImage.class);
        if (image != null) {
            Sprite sprite = image.getSprite();
            if (sprite != null) {
                Vector4f color = image.getColor();
                if (rotation != 0) {
                    uiBackend.drawSprite(sprite, x, y, width, height, rotation, originX, originY, zIndex, color);
                } else {
                    uiBackend.drawSprite(sprite, x, y, width, height, zIndex, color);
                }
                contentRendered = true;
            }
        }

        // Try UIButton
        if (!contentRendered) {
            UIButton button = entity.getComponent(UIButton.class);
            if (button != null) {
                Sprite sprite = button.getSprite();
                if (sprite != null) {
                    Vector4f color = button.getColor();
                    if (rotation != 0) {
                        uiBackend.drawSprite(sprite, x, y, width, height, rotation, originX, originY, zIndex, color);
                    } else {
                        uiBackend.drawSprite(sprite, x, y, width, height, zIndex, color);
                    }
                    contentRendered = true;
                } else {
                    Vector4f color = button.getColor();
                    if (color != null && color.w > 0) {
                        if (rotation != 0) {
                            uiBackend.drawRect(x, y, width, height, rotation, originX, originY, color, zIndex);
                        } else {
                            uiBackend.drawRect(x, y, width, height, color, zIndex);
                        }
                        contentRendered = true;
                    }
                }
            }
        }

        // Try UIPanel
        if (!contentRendered) {
            UIPanel panel = entity.getComponent(UIPanel.class);
            if (panel != null) {
                Vector4f color = panel.getColor();
                if (color != null && color.w > 0) {
                    if (rotation != 0) {
                        uiBackend.drawRect(x, y, width, height, rotation, originX, originY, color, zIndex);
                    } else {
                        uiBackend.drawRect(x, y, width, height, color, zIndex);
                    }
                    contentRendered = true;
                }
            }
        }

        // Fallback: draw placeholder for elements without visual content (except text)
        if (!contentRendered && !entity.hasComponent(UIText.class)) {
            Vector4f fillColor = getDefaultFillColor(entity);
            if (fillColor.w > 0) {
                if (rotation != 0) {
                    uiBackend.drawRect(x, y, width, height, rotation, originX, originY, fillColor, zIndex);
                } else {
                    uiBackend.drawRect(x, y, width, height, fillColor, zIndex);
                }
            }
        }
    }

    private Vector4f getDefaultFillColor(EditorGameObject entity) {
        if (entity.hasComponent(UIPanel.class)) return FILL_PANEL;
        if (entity.hasComponent(UIButton.class)) return FILL_BUTTON;
        if (entity.hasComponent(UIImage.class)) return FILL_IMAGE;
        if (entity.hasComponent(UIText.class)) return FILL_TEXT;
        return FILL_DEFAULT;
    }

    // ========================================================================
    // DISPLAY
    // ========================================================================

    /**
     * Displays the world background texture (if enabled).
     */
    public void drawWorldBackground(ImDrawList drawList) {
        if (state.getBackgroundMode() != UIDesignerState.BackgroundMode.WORLD) return;
        if (sceneTextureId == 0) return;

        float[] canvasBounds = coords.getCanvasScreenBounds();
        float left = state.getViewportX() + canvasBounds[0];
        float top = state.getViewportY() + canvasBounds[1];
        float right = state.getViewportX() + canvasBounds[2];
        float bottom = state.getViewportY() + canvasBounds[3];

        // Draw with flipped V coordinates (OpenGL texture origin is bottom-left)
        drawList.addImage(sceneTextureId, left, top, right, bottom, 0, 1, 1, 0);
    }

    /**
     * Displays the rendered UI texture in the viewport.
     */
    public void displayUITexture(ImDrawList drawList) {
        if (uiBackend == null || uiBackend.getOutputTexture() == 0) return;

        float[] canvasBounds = coords.getCanvasScreenBounds();
        float left = state.getViewportX() + canvasBounds[0];
        float top = state.getViewportY() + canvasBounds[1];
        float right = state.getViewportX() + canvasBounds[2];
        float bottom = state.getViewportY() + canvasBounds[3];

        // Draw with flipped V coordinates (OpenGL texture origin is bottom-left)
        drawList.addImage(
                uiBackend.getOutputTexture(),
                left, top, right, bottom,
                0, 1, 1, 0  // Flipped V: was 0,0,1,1
        );
    }

    /**
     * Draws text elements directly to ImGui (text uses ImGui font rendering).
     */
    public void drawTextElements(ImDrawList drawList, EditorScene scene) {
        if (scene == null) return;

        for (EditorGameObject entity : scene.getEntities()) {
            UIText textComp = entity.getComponent(UIText.class);
            if (textComp == null) continue;

            float[] bounds = coords.calculateElementBounds(entity);
            if (bounds == null) continue;

            Vector2f screenPos = coords.canvasToScreen(bounds[0], bounds[1]);
            Vector2f screenEnd = coords.canvasToScreen(bounds[0] + bounds[2], bounds[1] + bounds[3]);

            float left = state.getViewportX() + screenPos.x;
            float top = state.getViewportY() + screenPos.y;
            float right = state.getViewportX() + screenEnd.x;
            float bottom = state.getViewportY() + screenEnd.y;

            renderTextElement(drawList, textComp, left, top, right, bottom);
        }
    }

    private void renderTextElement(ImDrawList drawList, UIText textComp,
                                   float left, float top, float right, float bottom) {
        String text = textComp.getText();
        if (text == null || text.isEmpty()) return;

        Vector4f colorVec = textComp.getColor();
        int color = ImGui.colorConvertFloat4ToU32(colorVec.x, colorVec.y, colorVec.z, colorVec.w);

        float width = right - left;
        float height = bottom - top;

        // Calculate text position based on alignment
        float textX = left;
        float textY = top;

        // Simple text rendering (for more complex text, use the full text layout system)
        float fontSize = 14f * state.getZoom();
        if (fontSize < 6) return; // Too small to render

        // Apply horizontal alignment
        float textWidth = ImGui.calcTextSize(text).x;
        HorizontalAlignment hAlign = textComp.getHorizontalAlignment();
        if (hAlign == HorizontalAlignment.CENTER) {
            textX = left + (width - textWidth) / 2;
        } else if (hAlign == HorizontalAlignment.RIGHT) {
            textX = right - textWidth;
        }

        // Apply vertical alignment
        float lineHeight = ImGui.getTextLineHeight();
        VerticalAlignment vAlign = textComp.getVerticalAlignment();
        if (vAlign == VerticalAlignment.MIDDLE) {
            textY = top + (height - lineHeight) / 2;
        } else if (vAlign == VerticalAlignment.BOTTOM) {
            textY = bottom - lineHeight;
        }

        // Clip to bounds
        drawList.pushClipRect(left, top, right, bottom, true);
        drawList.addText(textX, textY, color, text);
        drawList.popClipRect();
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    public void destroy() {
        if (uiBackend != null) {
            uiBackend.destroy();
            uiBackend = null;
        }
        initialized = false;
    }

    public int getOutputTexture() {
        return uiBackend != null ? uiBackend.getOutputTexture() : 0;
    }
}
