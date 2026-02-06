package com.pocket.rpg.components.ui;

import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.rendering.ui.UIRendererBackend;
import com.pocket.rpg.serialization.Required;
import com.pocket.rpg.ui.text.Font;
import com.pocket.rpg.ui.text.FontCache;
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
 * UIText text = new UIText("fonts/zelda.ttf", 24, "Hello World");
 * text.setHorizontalAlignment(HorizontalAlignment.CENTER);
 * text.setVerticalAlignment(VerticalAlignment.MIDDLE);
 * text.setAutoFit(true);  // Scale to fit UITransform bounds
 * textObj.addComponent(text);
 * }</pre>
 */
public class UIText extends UIComponent {

    /**
     * Path to the font file (e.g., "fonts/zelda.ttf").
     */
    @Required
    @Getter
    @Setter
    private String fontPath;

    /**
     * Font size in pixels.
     */
    @Getter
    @Setter
    private int fontSize = 20;

    /**
     * Cached Font instance (not serialized).
     */
    private transient Font cachedFont;
    private transient String cachedFontKey;

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
     * When true, automatically finds the largest font size that fits within UITransform bounds.
     * This is Unity-style Best Fit - it changes the font size, not scaling.
     */
    @Getter
    @Setter
    private boolean autoFit = false;

    /**
     * Minimum font size when auto-fit is enabled (default 8).
     */
    @Getter
    @Setter
    private int minFontSize = 8;

    /**
     * Maximum font size when auto-fit is enabled (default 72).
     */
    @Getter
    @Setter
    private int maxFontSize = 72;

    /**
     * The computed best-fit font size. Used internally when autoFit is enabled.
     */
    private transient int computedFontSize = -1;

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

    // ==================== Layout Cache (transient - not serialized) ====================

    private transient String[] lines;
    private transient float[] lineWidths;
    private transient float naturalWidth;     // Width at current font size
    private transient float naturalHeight;    // Height at current font size
    private transient boolean layoutDirty = true;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public UIText() {
    }

    public UIText(String fontPath, int fontSize) {
        this.fontPath = fontPath;
        this.fontSize = fontSize;
    }

    public UIText(String fontPath, int fontSize, String text) {
        this.fontPath = fontPath;
        this.fontSize = fontSize;
        setText(text);
    }

    // ========================================================================
    // FONT
    // ========================================================================

    /**
     * Gets the Font instance for rendering.
     * Uses FontCache to reuse Font instances across components.
     *
     * @return Font instance, or null if fontPath is not set
     */
    public Font getFont() {
        if (fontPath == null || fontPath.isEmpty()) {
            return null;
        }
        String key = fontPath + "@" + fontSize;
        if (cachedFont == null || !key.equals(cachedFontKey)) {
            cachedFont = FontCache.get(fontPath, fontSize);
            cachedFontKey = key;
        }
        return cachedFont;
    }

    /**
     * Sets the font by path and size.
     *
     * @param fontPath Path to font file
     * @param fontSize Size in pixels
     */
    public void setFont(String fontPath, int fontSize) {
        this.fontPath = fontPath;
        this.fontSize = fontSize;
        this.cachedFont = null;
        this.cachedFontKey = null;
        this.layoutDirty = true;
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
    public void render(com.pocket.rpg.rendering.ui.UIRendererBackend backend) {
        if (getFont() == null || text.isEmpty()) {
            return;
        }

        UITransform transform = getUITransform();
        if (transform == null) {
            return;
        }

        // Use matrix-based methods for correct hierarchy handling
        Vector2f pivotWorld = transform.getWorldPivotPosition2D();
        Vector2f worldScale = transform.getComputedWorldScale2D();
        float boxWidth = transform.getEffectiveWidth() * worldScale.x;
        float boxHeight = transform.getEffectiveHeight() * worldScale.y;
        float rotation = transform.getComputedWorldRotation2D();
        Vector2f pivot = transform.getEffectivePivot();  // Use effective pivot for MATCH_PARENT

        // Calculate top-left position from pivot
        float boxX = pivotWorld.x - pivot.x * boxWidth;
        float boxY = pivotWorld.y - pivot.y * boxHeight;
        float pivotX = pivotWorld.x;
        float pivotY = pivotWorld.y;

        renderInternal(backend, boxX, boxY, boxWidth, boxHeight, rotation, pivotX, pivotY);
    }

    /**
     * Renders text with explicit position, size, and rotation parameters.
     * Use this in editor context where transform hierarchy may not be set up.
     *
     * @param backend  Rendering backend
     * @param x        X position (screen-space)
     * @param y        Y position (screen-space)
     * @param width    Box width
     * @param height   Box height
     * @param rotation Rotation in degrees
     * @param pivotX   Pivot X in screen coordinates
     * @param pivotY   Pivot Y in screen coordinates
     */
    public void render(com.pocket.rpg.rendering.ui.UIRendererBackend backend,
                       float x, float y, float width, float height,
                       float rotation, float pivotX, float pivotY) {
        if (getFont() == null || text.isEmpty()) return;

        renderInternal(backend, x, y, width, height, rotation, pivotX, pivotY);
    }

    /**
     * Internal rendering implementation shared by both render methods.
     */
    private void renderInternal(com.pocket.rpg.rendering.ui.UIRendererBackend backend,
                                float boxX, float boxY, float boxWidth, float boxHeight,
                                float rotation, float pivotX, float pivotY) {
        // Get the font to render with (may recalculate if auto-fit)
        Font renderFont = getRenderFont(boxWidth, boxHeight);
        if (renderFont == null) return;

        // Recalculate layout if needed
        if (layoutDirty) {
            calculateLayout(boxWidth, renderFont);
        }

        // Calculate text position for alignment
        float textStartX = calculateHorizontalStart(boxX, boxWidth, naturalWidth);
        float textStartY = calculateVerticalStart(boxY, boxHeight, naturalHeight);

        // Use the transform's pivot point for rotation (passed in from render())
        // This ensures MATCH_PARENT children rotate around the same pivot as their parent
        float effectivePivotX = pivotX;
        float effectivePivotY = pivotY;

        // Setup OpenGL state for font atlas (single channel)
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Bind font atlas and begin batch
        Texture atlasTexture = renderFont.getAtlasTexture();
        if (atlasTexture == null) return;
        backend.beginBatch(atlasTexture);

        // Render shadow first (if enabled)
        if (shadow) {
            renderTextPass(backend, renderFont, boxX + shadowOffset.x, boxY + shadowOffset.y,
                    boxWidth, boxHeight, shadowColor, rotation, effectivePivotX, effectivePivotY);
        }

        // Render main text
        renderTextPass(backend, renderFont, boxX, boxY, boxWidth, boxHeight, color, rotation, effectivePivotX, effectivePivotY);

        backend.endBatch();
    }

    /**
     * Gets the font to use for rendering.
     * When autoFit is enabled, finds the largest font size that fits.
     */
    private Font getRenderFont(float boxWidth, float boxHeight) {
        if (!autoFit) {
            return getFont();
        }

        // Calculate best fit font size
        computedFontSize = calculateBestFitFontSize(boxWidth, boxHeight);
        return FontCache.get(fontPath, computedFontSize);
    }

    /**
     * Binary search to find the largest font size that fits within bounds.
     */
    private int calculateBestFitFontSize(float boxWidth, float boxHeight) {
        if (fontPath == null || fontPath.isEmpty() || text.isEmpty()) {
            return fontSize;
        }

        int low = minFontSize;
        int high = Math.min(maxFontSize, fontSize);  // Don't exceed specified fontSize
        int bestSize = low;

        while (low <= high) {
            int mid = (low + high) / 2;
            Font testFont = FontCache.get(fontPath, mid);
            if (testFont == null) break;

            // Calculate text dimensions at this font size
            float testWidth = calculateTextWidth(testFont);
            float testHeight = calculateTextHeight(testFont);

            if (testWidth <= boxWidth && testHeight <= boxHeight) {
                bestSize = mid;
                low = mid + 1;  // Try larger
            } else {
                high = mid - 1;  // Try smaller
            }
        }

        return bestSize;
    }

    /**
     * Calculates text width for a given font.
     */
    private float calculateTextWidth(Font testFont) {
        float maxWidth = 0;
        String[] testLines = text.split("\n", -1);
        for (String line : testLines) {
            maxWidth = Math.max(maxWidth, testFont.getStringWidth(line));
        }
        return maxWidth;
    }

    /**
     * Calculates text height for a given font.
     */
    private float calculateTextHeight(Font testFont) {
        String[] testLines = text.split("\n", -1);
        int singleLineVisualHeight = testFont.getAscent() - testFont.getDescent();
        if (testLines.length == 1) {
            return singleLineVisualHeight;
        }
        return (testLines.length - 1) * testFont.getLineHeight() + singleLineVisualHeight;
    }

    /**
     * Renders a single pass of text (used for both shadow and main text).
     * Supports rotation around the pivot point.
     */
    private void renderTextPass(UIRendererBackend backend, Font renderFont, float baseX, float baseY,
                                float boxWidth, float boxHeight, Vector4f textColor,
                                float rotation, float pivotX, float pivotY) {

        float startY = calculateVerticalStart(baseY, boxHeight, naturalHeight);

        float lineY = startY;
        float lineHeight = renderFont.getLineHeight();
        float ascent = renderFont.getAscent();

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            float lineWidth = lineWidths[lineIndex];

            // Calculate horizontal starting position for this line
            float lineX = calculateHorizontalStart(baseX, boxWidth, lineWidth);

            // Render glyphs
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

                    // Use rotation-aware batchSprite to rotate both position AND glyph quad
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

            // Move to next line
            lineY += lineHeight;
        }
    }

    private void calculateLayout(float maxWidth, Font renderFont) {
        if (text.isEmpty() || renderFont == null) {
            lines = new String[0];
            lineWidths = new float[0];
            naturalWidth = 0;
            naturalHeight = 0;
            layoutDirty = false;
            return;
        }

        if (wordWrap && maxWidth > 0) {
            calculateWrappedLayout(maxWidth, renderFont);
        } else {
            calculateSimpleLayout(renderFont);
        }

        layoutDirty = false;
    }

    private void calculateSimpleLayout(Font renderFont) {
        // Split by newlines only
        String[] rawLines = text.split("\n", -1);
        lines = rawLines;
        lineWidths = new float[lines.length];

        naturalWidth = 0;
        for (int i = 0; i < lines.length; i++) {
            lineWidths[i] = renderFont.getStringWidth(lines[i]);
            naturalWidth = Math.max(naturalWidth, lineWidths[i]);
        }

        calculateNaturalHeight(renderFont);
    }

    private void calculateWrappedLayout(float maxWidth, Font renderFont) {
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
            float spaceWidth = renderFont.getGlyph(' ') != null ? renderFont.getGlyph(' ').advance : 0;

            for (String word : words) {
                float wordWidth = renderFont.getStringWidth(word);

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

        calculateNaturalHeight(renderFont);
    }

    /**
     * Calculate natural height at current font size.
     * Visual height = ascent + |descent| for single line
     * Multi-line includes lineHeight spacing between lines.
     */
    private void calculateNaturalHeight(Font renderFont) {
        if (lines.length == 0) {
            naturalHeight = 0;
            return;
        }

        // Single line visual height = ascent + |descent|
        int singleLineVisualHeight = renderFont.getAscent() - renderFont.getDescent();

        if (lines.length == 1) {
            naturalHeight = singleLineVisualHeight;
        } else {
            // Multiple lines: (n-1) * lineHeight + last line visual height
            naturalHeight = (lines.length - 1) * renderFont.getLineHeight() + singleLineVisualHeight;
        }
    }

    /**
     * Calculates horizontal start position based on alignment.
     * Text is aligned within the bounding box (Unity-style).
     */
    private float calculateHorizontalStart(float boxX, float boxWidth, float lineWidth) {
        return switch (horizontalAlignment) {
            case LEFT -> boxX;
            case CENTER -> boxX + (boxWidth - lineWidth) / 2;
            case RIGHT -> boxX + boxWidth - lineWidth;
        };
    }

    /**
     * Calculates vertical start position based on alignment.
     * Text is aligned within the bounding box (Unity-style).
     */
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
     * Gets the natural width of the text at the current font size.
     */
    public float getNaturalWidth() {
        Font font = getFont();
        if (layoutDirty && font != null) {
            calculateLayout(getUITransform() != null ? getUITransform().getWidth() : 0, font);
        }
        return naturalWidth;
    }

    /**
     * Gets the natural height of the text at the current font size.
     */
    public float getNaturalHeight() {
        Font font = getFont();
        if (layoutDirty && font != null) {
            calculateLayout(getUITransform() != null ? getUITransform().getWidth() : 0, font);
        }
        return naturalHeight;
    }

    /**
     * Gets the current computed font size (after auto-fit).
     * Returns the specified fontSize if auto-fit is disabled.
     */
    public int getComputedFontSize() {
        return computedFontSize > 0 ? computedFontSize : fontSize;
    }

    /**
     * Gets the number of lines after layout.
     */
    public int getLineCount() {
        Font font = getFont();
        if (layoutDirty && font != null) {
            calculateLayout(getUITransform() != null ? getUITransform().getWidth() : 0, font);
        }
        return lines != null ? lines.length : 0;
    }

    public void setAlpha(float alpha) {
        color.w = alpha;
    }
}