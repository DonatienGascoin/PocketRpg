package com.pocket.rpg.rendering.ui;

import com.pocket.rpg.components.ui.UIComponent;
import com.pocket.rpg.components.ui.UIImage;
import com.pocket.rpg.components.ui.UIPanel;
import com.pocket.rpg.components.ui.UIText;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.components.ui.UIVisual;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.ui.text.Font;
import com.pocket.rpg.ui.text.Glyph;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Dispatches UI component rendering to the appropriate rendering method.
 * Owns the type-specific rendering knowledge (panel, image, text) while
 * UIRenderer owns the low-level GPU resources and backend implementation.
 */
public class UIRenderDispatcher {

    /**
     * Renders a UIVisual component by dispatching to the correct type-specific method.
     */
    public void render(UIVisual visual, UIRendererBackend backend) {
        if (visual instanceof UIPanel panel) {
            renderPanel(panel, backend);
        } else if (visual instanceof UIImage image) {
            renderImage(image, backend);
        } else if (visual instanceof UIText text) {
            renderText(text, backend);
        }
    }

    // ========================================================================
    // PANEL
    // ========================================================================

    public void renderPanel(UIPanel panel, UIRendererBackend backend) {
        UIComponent.RenderBounds bounds = panel.computeRenderBounds();
        if (bounds == null) return;

        backend.drawQuad(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                bounds.rotation(), bounds.pivotX(), bounds.pivotY(), panel.getColor());
    }

    // ========================================================================
    // IMAGE
    // ========================================================================

    public void renderImage(UIImage image, UIRendererBackend backend) {
        UIComponent.RenderBounds bounds = image.computeRenderBounds();
        if (bounds == null) return;

        switch (image.getImageType()) {
            case SIMPLE -> renderImageSimple(image, bounds, backend);
            case SLICED -> renderImageSliced(image, bounds, backend);
            case TILED -> renderImageTiled(image, bounds, backend);
            case FILLED -> renderImageFilled(image, bounds, backend);
        }
    }

    private void renderImageSimple(UIImage image, UIComponent.RenderBounds bounds, UIRendererBackend backend) {
        UIComponent.RenderBounds adjusted = image.isPreserveAspectRatio() ? fitToAspectRatio(image, bounds) : bounds;
        backend.drawSprite(adjusted.x(), adjusted.y(), adjusted.width(), adjusted.height(),
                adjusted.rotation(), adjusted.pivotX(), adjusted.pivotY(), image.getSprite(), image.getColor());
    }

    private void renderImageSliced(UIImage image, UIComponent.RenderBounds bounds, UIRendererBackend backend) {
        if (image.getSprite() == null || !image.getSprite().hasNineSlice()) {
            renderImageSimple(image, bounds, backend);
            return;
        }

        backend.drawNineSlice(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                bounds.rotation(), bounds.pivotX(), bounds.pivotY(),
                image.getSprite(), image.getColor(), image.isFillCenter());
    }

    private void renderImageTiled(UIImage image, UIComponent.RenderBounds bounds, UIRendererBackend backend) {
        if (image.getSprite() == null) {
            renderImageSimple(image, bounds, backend);
            return;
        }

        backend.drawTiled(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                bounds.rotation(), bounds.pivotX(), bounds.pivotY(),
                image.getSprite(), image.getColor(), image.getPixelsPerUnit());
    }

    private void renderImageFilled(UIImage image, UIComponent.RenderBounds bounds, UIRendererBackend backend) {
        if (image.getSprite() == null || image.getFillAmount() <= 0) {
            return;
        }

        if (image.getFillAmount() >= 1.0f && image.getFillMethod() != FillMethod.RADIAL_90
                && image.getFillMethod() != FillMethod.RADIAL_180) {
            renderImageSimple(image, bounds, backend);
            return;
        }

        backend.drawFilled(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                bounds.rotation(), bounds.pivotX(), bounds.pivotY(),
                image.getSprite(), image.getColor(), image.getFillMethod(), image.getEffectiveFillOrigin(),
                image.getFillAmount(), image.isFillClockwise());
    }

    /**
     * Fits render bounds to the sprite's aspect ratio.
     */
    UIComponent.RenderBounds fitToAspectRatio(UIImage image, UIComponent.RenderBounds bounds) {
        Sprite sprite = image.getSprite();
        if (sprite == null || sprite.getWidth() <= 0 || sprite.getHeight() <= 0
                || bounds.width() <= 0 || bounds.height() <= 0) {
            return bounds;
        }

        float spriteAspect = sprite.getWidth() / sprite.getHeight();
        float boundsAspect = bounds.width() / bounds.height();

        float newW, newH;
        if (spriteAspect > boundsAspect) {
            newW = bounds.width();
            newH = bounds.width() / spriteAspect;
        } else {
            newH = bounds.height();
            newW = bounds.height() * spriteAspect;
        }

        float offsetX = (bounds.width() - newW) * bounds.pivotX();
        float offsetY = (bounds.height() - newH) * bounds.pivotY();

        return new UIComponent.RenderBounds(
                bounds.x() + offsetX, bounds.y() + offsetY,
                newW, newH,
                bounds.rotation(), bounds.pivotX(), bounds.pivotY()
        );
    }

    // ========================================================================
    // TEXT
    // ========================================================================

    public void renderText(UIText text, UIRendererBackend backend) {
        if (text.getFont() == null || text.getText().isEmpty()) return;

        UITransform transform = text.getUITransform();
        if (transform == null) return;

        Vector2f pivotWorld = transform.getWorldPivotPosition2D();
        Vector2f worldScale = transform.getComputedWorldScale2D();
        float boxWidth = transform.getEffectiveWidth() * worldScale.x;
        float boxHeight = transform.getEffectiveHeight() * worldScale.y;
        float rotation = transform.getComputedWorldRotation2D();
        Vector2f pivot = transform.getEffectivePivot();

        float boxX = pivotWorld.x - pivot.x * boxWidth;
        float boxY = pivotWorld.y - pivot.y * boxHeight;
        float pivotX = pivotWorld.x;
        float pivotY = pivotWorld.y;

        renderTextInternal(text, backend, boxX, boxY, boxWidth, boxHeight, rotation, pivotX, pivotY);
    }

    /**
     * Renders a UIText component with explicit position parameters.
     * Used for editor rendering where transform hierarchy may not be set up.
     */
    public void renderText(UIText text, UIRendererBackend backend,
                           float x, float y, float width, float height,
                           float rotation, float pivotX, float pivotY) {
        if (text.getFont() == null || text.getText().isEmpty()) return;
        renderTextInternal(text, backend, x, y, width, height, rotation, pivotX, pivotY);
    }

    private void renderTextInternal(UIText text, UIRendererBackend backend,
                                    float boxX, float boxY, float boxWidth, float boxHeight,
                                    float rotation, float pivotX, float pivotY) {
        Font renderFont = text.getRenderFont(boxWidth, boxHeight);
        if (renderFont == null) return;

        text.ensureLayout(boxWidth, renderFont);

        var atlasTexture = renderFont.getAtlasTexture();
        if (atlasTexture == null) return;
        backend.beginBatch(atlasTexture);

        // Render shadow first (if enabled)
        if (text.isShadow()) {
            renderTextPass(text, renderFont, backend,
                    boxX + text.getShadowOffset().x, boxY + text.getShadowOffset().y,
                    boxWidth, boxHeight, text.getShadowColor(), rotation, pivotX, pivotY);
        }

        // Render main text
        renderTextPass(text, renderFont, backend, boxX, boxY, boxWidth, boxHeight,
                text.getColor(), rotation, pivotX, pivotY);

        backend.endBatch();
    }

    private void renderTextPass(UIText text, Font renderFont, UIRendererBackend backend,
                                float baseX, float baseY, float boxWidth, float boxHeight,
                                Vector4f textColor, float rotation, float pivotX, float pivotY) {
        String[] lines = text.getLines();
        float[] lineWidths = text.getLineWidths();
        if (lines == null || lines.length == 0) return;

        float startY = text.calculateVerticalStart(baseY, boxHeight, text.getNaturalHeight());
        float lineY = startY;
        float lineHeight = renderFont.getLineHeight();
        float ascent = renderFont.getAscent();

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            float lineWidth = lineWidths[lineIndex];

            float lineX = text.calculateHorizontalStart(baseX, boxWidth, lineWidth);
            float cursorX = lineX;
            float baseline = lineY + ascent;

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                Glyph glyph = renderFont.getGlyph(c);

                if (glyph != null && !glyph.isWhitespace()) {
                    float glyphX = cursorX + glyph.bearingX;
                    float glyphY = baseline - glyph.bearingY;
                    float glyphW = glyph.width;
                    float glyphH = glyph.height;

                    backend.batchSprite(
                            glyphX, glyphY,
                            glyphW, glyphH,
                            glyph.u0, glyph.v0, glyph.u1, glyph.v1,
                            rotation, pivotX, pivotY,
                            textColor
                    );
                }

                if (glyph != null) {
                    cursorX += glyph.advance;
                }
            }

            lineY += lineHeight;
        }
    }
}
