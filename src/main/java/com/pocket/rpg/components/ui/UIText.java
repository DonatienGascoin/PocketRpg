package com.pocket.rpg.components.ui;

import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.ui.UIRendererBackend;
import com.pocket.rpg.ui.text.Font;
import com.pocket.rpg.ui.text.Glyph;
import com.pocket.rpg.ui.text.HorizontalAlignment;
import com.pocket.rpg.ui.text.VerticalAlignment;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL11.*;

/**
 * UI component for rendering text.
 * Requires UITransform on the same GameObject.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Single-line and multi-line text</li>
 *   <li>Horizontal alignment (LEFT, CENTER, RIGHT)</li>
 *   <li>Vertical alignment (TOP, MIDDLE, BOTTOM)</li>
 *   <li>Word wrapping (optional)</li>
 *   <li>Auto-fit scaling to UITransform bounds</li>
 *   <li>Drop shadow effect</li>
 *   <li>Color tinting</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * GameObject textObj = new GameObject("Label");
 * UITransform transform = new UITransform(200, 50);  // Width x Height
 * textObj.addComponent(transform);
 *
 * UIText text = new UIText(font, "Hello World");
 * text.setHorizontalAlignment(HorizontalAlignment.CENTER);
 * text.setVerticalAlignment(VerticalAlignment.MIDDLE);
 * text.setAutoFit(true);  // Scale to fit UITransform bounds
 * textObj.addComponent(text);
 * }</pre>
 */
public class UIText extends UIComponent {

    @Getter
    @Setter
    private Font font;

    @Getter
    private String text = "";

    @Getter
    @Setter
    private Vector4f color = new Vector4f(1, 1, 1, 1);  // White

    @Getter
    @Setter
    private HorizontalAlignment horizontalAlignment = HorizontalAlignment.LEFT;

    @Getter
    @Setter
    private VerticalAlignment verticalAlignment = VerticalAlignment.TOP;

    @Getter
    @Setter
    private boolean wordWrap = false;

    // ==================== Auto-fit ====================

    /**
     * When true, text scales to fit within UITransform bounds.
     */
    @Getter
    @Setter
    private boolean autoFit = false;

    /**
     * Minimum scale factor (default 0.5 = don't shrink below 50%)
     */
    @Getter
    @Setter
    private float minScale = 0.5f;

    /**
     * Maximum scale factor (default 1.0 = don't grow beyond 100%)
     */
    @Getter
    @Setter
    private float maxScale = 1.0f;

    /**
     * When true, scales uniformly. When false, can stretch to fill.
     */
    @Getter
    @Setter
    private boolean maintainAspectRatio = true;

    // ==================== Shadow ====================

    /**
     * When true, renders a drop shadow behind text
     */
    @Getter
    @Setter
    private boolean shadow = false;

    /**
     * Shadow color (default: semi-transparent black)
     */
    @Getter
    private Vector4f shadowColor = new Vector4f(0, 0, 0, 0.5f);

    /**
     * Shadow offset in pixels (default: 2, 2)
     */
    @Getter
    private Vector2f shadowOffset = new Vector2f(2, 2);

    // ==================== Layout Cache ====================

    private String[] lines;
    private float[] lineWidths;
    private float naturalWidth;     // Width at scale 1.0
    private float naturalHeight;    // Height at scale 1.0
    private float computedScaleX = 1.0f;
    private float computedScaleY = 1.0f;
    private boolean layoutDirty = true;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public UIText() {
    }

    public UIText(Font font) {
        this.font = font;
    }

    public UIText(Font font, String text) {
        this.font = font;
        setText(text);
    }

    // ========================================================================
    // TEXT
    // ========================================================================

    /**
     * Sets the text to display. Marks layout as needing recalculation.
     */
    public void setText(String text) {
        if (text == null) text = "";
        if (!this.text.equals(text)) {
            this.text = text;
            layoutDirty = true;
        }
    }

    // ========================================================================
    // COLOR
    // ========================================================================

    /**
     * Sets color from RGBA components (0-1 range).
     */
    public void setColor(float r, float g, float b, float a) {
        color.set(r, g, b, a);
    }

    /**
     * Sets color from RGB (alpha = 1).
     */
    public void setColor(float r, float g, float b) {
        setColor(r, g, b, 1);
    }

    // ========================================================================
    // SHADOW
    // ========================================================================

    /**
     * Sets shadow color from RGBA components (0-1 range).
     */
    public void setShadowColor(float r, float g, float b, float a) {
        shadowColor.set(r, g, b, a);
    }

    /**
     * Sets shadow color.
     */
    public void setShadowColor(Vector4f color) {
        shadowColor.set(color);
    }

    /**
     * Sets shadow offset in pixels.
     */
    public void setShadowOffset(float x, float y) {
        shadowOffset.set(x, y);
    }

    /**
     * Sets shadow offset.
     */
    public void setShadowOffset(Vector2f offset) {
        shadowOffset.set(offset);
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    @Override
    public void render(UIRendererBackend backend) {
        if (font == null || text.isEmpty()) return;

        UITransform transform = getUITransform();
        if (transform == null) return;

        // Get bounds from UITransform
        Vector2f pos = transform.getScreenPosition();
        float boxX = pos.x;
        float boxY = pos.y;
        float boxWidth = transform.getWidth();
        float boxHeight = transform.getHeight();

        // Recalculate layout if needed
        if (layoutDirty) {
            calculateLayout(boxWidth);
        }

        // Calculate auto-fit scale
        if (autoFit) {
            calculateAutoFitScale(boxWidth, boxHeight);
        } else {
            computedScaleX = 1.0f;
            computedScaleY = 1.0f;
        }

        // Setup OpenGL state for font atlas (single channel)
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Bind font atlas and begin batch
        Texture atlasTexture = font.getAtlasTexture();
        if (atlasTexture == null) return;
        backend.beginBatch(atlasTexture);

        // Render shadow first (if enabled)
        if (shadow) {
            renderTextPass(backend, boxX + shadowOffset.x, boxY + shadowOffset.y,
                    boxWidth, boxHeight, shadowColor);
        }

        // Render main text
        renderTextPass(backend, boxX, boxY, boxWidth, boxHeight, color);

        backend.endBatch();
    }

    /**
     * Renders a single pass of text (used for both shadow and main text).
     */
    private void renderTextPass(UIRendererBackend backend, float baseX, float baseY,
                                float boxWidth, float boxHeight, Vector4f textColor) {

        float scaledHeight = naturalHeight * computedScaleY;
        float startY = calculateVerticalStart(baseY, boxHeight, scaledHeight);

        float lineY = startY;
        float scaledLineHeight = font.getLineHeight() * computedScaleY;
        float scaledAscent = font.getAscent() * computedScaleY;

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            float scaledLineWidth = lineWidths[lineIndex] * computedScaleX;

            // Calculate horizontal starting position for this line
            float lineX = calculateHorizontalStart(baseX, boxWidth, scaledLineWidth);

            // Render glyphs
            float cursorX = lineX;
            float baseline = lineY + scaledAscent;

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                Glyph glyph = font.getGlyph(c);

                if (glyph != null && !glyph.isWhitespace()) {
                    float glyphX = cursorX + glyph.bearingX * computedScaleX;
                    float glyphY = baseline - glyph.bearingY * computedScaleY;
                    float glyphW = glyph.width * computedScaleX;
                    float glyphH = glyph.height * computedScaleY;

                    backend.batchSprite(
                            glyphX, glyphY,
                            glyphW, glyphH,
                            glyph.u0, glyph.v0, glyph.u1, glyph.v1,
                            textColor
                    );
                }

                if (glyph != null) {
                    cursorX += glyph.advance * computedScaleX;
                }
            }

            // Move to next line
            lineY += scaledLineHeight;
        }
    }

    /**
     * Calculates the scale factors for auto-fit mode.
     */
    private void calculateAutoFitScale(float boxWidth, float boxHeight) {
        if (naturalWidth <= 0 || naturalHeight <= 0) {
            computedScaleX = 1.0f;
            computedScaleY = 1.0f;
            return;
        }

        float scaleX = boxWidth / naturalWidth;
        float scaleY = boxHeight / naturalHeight;

        if (maintainAspectRatio) {
            // Use uniform scale (smallest to fit both dimensions)
            float uniformScale = Math.min(scaleX, scaleY);
            uniformScale = clampScale(uniformScale);
            computedScaleX = uniformScale;
            computedScaleY = uniformScale;
        } else {
            // Independent scaling (stretch to fill)
            computedScaleX = clampScale(scaleX);
            computedScaleY = clampScale(scaleY);
        }
    }

    /**
     * Clamps scale to min/max range.
     */
    private float clampScale(float scale) {
        return Math.max(minScale, Math.min(maxScale, scale));
    }

    private void calculateLayout(float maxWidth) {
        if (text.isEmpty()) {
            lines = new String[0];
            lineWidths = new float[0];
            naturalWidth = 0;
            naturalHeight = 0;
            layoutDirty = false;
            return;
        }

        if (wordWrap && maxWidth > 0) {
            calculateWrappedLayout(maxWidth);
        } else {
            calculateSimpleLayout();
        }

        layoutDirty = false;
    }

    private void calculateSimpleLayout() {
        // Split by newlines only
        String[] rawLines = text.split("\n", -1);
        lines = rawLines;
        lineWidths = new float[lines.length];

        naturalWidth = 0;
        for (int i = 0; i < lines.length; i++) {
            lineWidths[i] = font.getStringWidth(lines[i]);
            naturalWidth = Math.max(naturalWidth, lineWidths[i]);
        }

        calculateNaturalHeight();
    }

    private void calculateWrappedLayout(float maxWidth) {
        java.util.List<String> wrappedLines = new java.util.ArrayList<>();
        java.util.List<Float> widths = new java.util.ArrayList<>();

        String[] paragraphs = text.split("\n", -1);

        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                wrappedLines.add("");
                widths.add(0f);
                continue;
            }

            String[] words = paragraph.split(" ");
            StringBuilder currentLine = new StringBuilder();
            float currentWidth = 0;
            float spaceWidth = font.getGlyph(' ') != null ? font.getGlyph(' ').advance : 0;

            for (String word : words) {
                float wordWidth = font.getStringWidth(word);

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
                    widths.add(currentWidth);
                    currentLine = new StringBuilder(word);
                    currentWidth = wordWidth;
                }
            }

            // Add remaining text
            if (currentLine.length() > 0) {
                wrappedLines.add(currentLine.toString());
                widths.add(currentWidth);
            }
        }

        lines = wrappedLines.toArray(new String[0]);
        lineWidths = new float[widths.size()];

        naturalWidth = 0;
        for (int i = 0; i < widths.size(); i++) {
            lineWidths[i] = widths.get(i);
            naturalWidth = Math.max(naturalWidth, lineWidths[i]);
        }

        calculateNaturalHeight();
    }

    /**
     * Calculate natural height at scale 1.0.
     * Visual height = ascent + |descent| for single line
     * Multi-line includes lineHeight spacing between lines.
     */
    private void calculateNaturalHeight() {
        if (lines.length == 0) {
            naturalHeight = 0;
            return;
        }

        // Single line visual height = ascent + |descent|
        int singleLineVisualHeight = font.getAscent() - font.getDescent();

        if (lines.length == 1) {
            naturalHeight = singleLineVisualHeight;
        } else {
            // Multiple lines: (n-1) * lineHeight + last line visual height
            naturalHeight = (lines.length - 1) * font.getLineHeight() + singleLineVisualHeight;
        }
    }

    private float calculateHorizontalStart(float boxX, float boxWidth, float lineWidth) {
        return switch (horizontalAlignment) {
            case LEFT -> boxX;
            case CENTER -> boxX + (boxWidth - lineWidth) / 2;
            case RIGHT -> boxX + boxWidth - lineWidth;
        };
    }

    private float calculateVerticalStart(float boxY, float boxHeight, float textHeight) {
        return switch (verticalAlignment) {
            case TOP -> boxY;
            case MIDDLE -> boxY + (boxHeight - textHeight) / 2;
            case BOTTOM -> boxY + boxHeight - textHeight;
        };
    }

    // ========================================================================
    // LAYOUT INFO
    // ========================================================================

    /**
     * Marks layout as needing recalculation.
     * Call after changing font or when bounds change.
     */
    public void markLayoutDirty() {
        layoutDirty = true;
    }

    /**
     * Gets the natural width of the text (at scale 1.0).
     */
    public float getNaturalWidth() {
        if (layoutDirty && font != null) {
            calculateLayout(getUITransform() != null ? getUITransform().getWidth() : 0);
        }
        return naturalWidth;
    }

    /**
     * Gets the natural height of the text (at scale 1.0).
     */
    public float getNaturalHeight() {
        if (layoutDirty && font != null) {
            calculateLayout(getUITransform() != null ? getUITransform().getWidth() : 0);
        }
        return naturalHeight;
    }

    /**
     * Gets the current computed scale (after auto-fit).
     * Returns 1.0 if auto-fit is disabled.
     */
    public float getComputedScale() {
        return Math.min(computedScaleX, computedScaleY);
    }

    /**
     * Gets the number of lines after layout.
     */
    public int getLineCount() {
        if (layoutDirty && font != null) {
            calculateLayout(getUITransform() != null ? getUITransform().getWidth() : 0);
        }
        return lines != null ? lines.length : 0;
    }
}