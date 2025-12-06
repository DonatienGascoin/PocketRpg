package com.pocket.rpg.ui.text;

import com.pocket.rpg.ui.UIComponent;
import com.pocket.rpg.ui.UIRendererBackend;
import com.pocket.rpg.ui.UITransform;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL11.*;

/**
 * UI component for rendering text.
 * Requires UITransform on the same GameObject.
 * <p>
 * Features:
 * - Single-line and multi-line text
 * - Horizontal alignment (LEFT, CENTER, RIGHT)
 * - Vertical alignment (TOP, MIDDLE, BOTTOM)
 * - Word wrapping (optional, based on UITransform width)
 * - Color tinting
 * <p>
 * Usage:
 * GameObject textObj = new GameObject("Label");
 * UITransform transform = new UITransform(200, 50);
 * transform.setAnchor(AnchorPreset.TOP_CENTER);
 * textObj.addComponent(transform);
 * <p>
 * UIText text = new UIText(font);
 * text.setText("Hello World");
 * text.setHorizontalAlignment(HorizontalAlignment.CENTER);
 * textObj.addComponent(text);
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

    // Cached layout data
    private String[] lines;
    private float[] lineWidths;
    private float totalHeight;
    private boolean layoutDirty = true;

    public UIText() {
    }

    public UIText(Font font) {
        this.font = font;
    }

    public UIText(Font font, String text) {
        this.font = font;
        setText(text);
    }

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

    @Override
    public void render(UIRendererBackend backend) {
        if (font == null || text.isEmpty()) return;

        UITransform transform = getUITransform();
        if (transform == null) return;

        // Recalculate layout if needed
        if (layoutDirty) {
            calculateLayout(transform.getWidth());
        }

        Vector2f pos = transform.getScreenPosition();
        float boxWidth = transform.getWidth();
        float boxHeight = transform.getHeight();

        // Calculate vertical starting position
        float startY = calculateVerticalStart(pos.y, boxHeight);

        // Setup OpenGL state for font atlas (single channel)
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Bind font atlas and begin batch
        font.bind(0);
        backend.beginBatch(null);  // We manually bound the texture

        // Render each line
        float lineY = startY;
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            float lineWidth = lineWidths[lineIndex];

            // Calculate horizontal starting position for this line
            float lineX = calculateHorizontalStart(pos.x, boxWidth, lineWidth);

            // Render glyphs
            float cursorX = lineX;
            float baseline = lineY + font.getAscent();

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                Glyph glyph = font.getGlyph(c);

                if (glyph != null && !glyph.isWhitespace()) {
                    float glyphX = cursorX + glyph.bearingX;
                    float glyphY = baseline - glyph.bearingY;

                    backend.batchSprite(
                            glyphX, glyphY,
                            glyph.width, glyph.height,
                            glyph.u0, glyph.v0, glyph.u1, glyph.v1,
                            color
                    );
                }

                if (glyph != null) {
                    cursorX += glyph.advance;
                }
            }

            lineY += font.getLineHeight();
        }

        backend.endBatch();
    }

    private void calculateLayout(float maxWidth) {
        if (text.isEmpty()) {
            lines = new String[0];
            lineWidths = new float[0];
            totalHeight = 0;
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

        for (int i = 0; i < lines.length; i++) {
            lineWidths[i] = font.getStringWidth(lines[i]);
        }

        totalHeight = lines.length * font.getLineHeight();
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
        for (int i = 0; i < widths.size(); i++) {
            lineWidths[i] = widths.get(i);
        }

        totalHeight = lines.length * font.getLineHeight();
    }

    private float calculateHorizontalStart(float boxX, float boxWidth, float lineWidth) {
        return switch (horizontalAlignment) {
            case LEFT -> boxX;
            case CENTER -> boxX + (boxWidth - lineWidth) / 2;
            case RIGHT -> boxX + boxWidth - lineWidth;
        };
    }

    private float calculateVerticalStart(float boxY, float boxHeight) {
        return switch (verticalAlignment) {
            case TOP -> boxY;
            case MIDDLE -> boxY + (boxHeight - totalHeight) / 2;
            case BOTTOM -> boxY + boxHeight - totalHeight;
        };
    }

    /**
     * Marks layout as needing recalculation.
     * Call after changing font or alignment.
     */
    public void markLayoutDirty() {
        layoutDirty = true;
    }

    /**
     * Gets the calculated height of the text.
     */
    public float getTextHeight() {
        if (layoutDirty && font != null) {
            calculateLayout(getTransform() != null ? getUITransform().getWidth() : 0);
        }
        return totalHeight;
    }

    /**
     * Gets the number of lines after layout.
     */
    public int getLineCount() {
        if (layoutDirty && font != null) {
            calculateLayout(getTransform() != null ? getUITransform().getWidth() : 0);
        }
        return lines != null ? lines.length : 0;
    }
}