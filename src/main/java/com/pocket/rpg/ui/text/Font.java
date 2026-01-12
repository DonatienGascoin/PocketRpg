package com.pocket.rpg.ui.text;

import com.pocket.rpg.rendering.resources.Texture;
import lombok.Getter;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.system.MemoryStack;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * Loads a TrueType font and creates a texture atlas for rendering.
 * Each Font instance is rasterized at a specific pixel size.
 * <p>
 * Usage:
 * Font font = new Font("fonts/arial.ttf", 24);
 * Glyph g = font.getGlyph('A');
 * font.getAtlasTexture().bind(0);
 * // render using glyph UVs
 * font.destroy();
 * <p>
 * Character range: ASCII 32-126 by default (printable characters).
 * Extended Unicode support can be added via overloaded constructor.
 */
public class Font {

    private static final int FIRST_CHAR = 32;   // Space
    private static final int LAST_CHAR = 126;   // Tilde
    private static final int CHAR_COUNT = LAST_CHAR - FIRST_CHAR + 1;
    @Getter
    private final String path;
    private final int size;

    private int atlasTextureId;
    private int atlasWidth;
    private int atlasHeight;
    @Getter
    private transient Texture atlasTexture;

    private final Map<Integer, Glyph> glyphs = new HashMap<>();

    // Font metrics
    private int ascent;   // Distance from baseline to top of tallest glyph
    private int descent;  // Distance from baseline to bottom of lowest glyph (negative)
    private int lineGap;  // Recommended gap between lines
    private float scale;  // Scale factor for this font size

    /**
     * Loads a font from the classpath at the specified pixel size.
     *
     * @param resourcePath Path to TTF file (e.g., "fonts/arial.ttf")
     * @param pixelSize    Desired font size in pixels
     * @throws RuntimeException if font cannot be loaded
     */
    public Font(String resourcePath, int pixelSize) {
        this.path = resourcePath;
        this.size = pixelSize;

        ByteBuffer fontData = loadResource(resourcePath);
        createAtlas(fontData);
    }

    private ByteBuffer loadResource(String resourcePath) {
        File file = new File(resourcePath);

        try (InputStream is = new FileInputStream(file)) {
            if (is == null) {
                throw new RuntimeException("Font not found: " + resourcePath);
            }

            byte[] bytes = is.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            return buffer;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load font: " + resourcePath, e);
        }
    }

    private void createAtlas(ByteBuffer fontData) {
        // Initialize font info
        STBTTFontinfo fontInfo = STBTTFontinfo.create();
        if (!stbtt_InitFont(fontInfo, fontData)) {
            throw new RuntimeException("Failed to initialize font: " + path);
        }

        // Get font metrics
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ascentBuf = stack.mallocInt(1);
            IntBuffer descentBuf = stack.mallocInt(1);
            IntBuffer lineGapBuf = stack.mallocInt(1);

            stbtt_GetFontVMetrics(fontInfo, ascentBuf, descentBuf, lineGapBuf);

            scale = stbtt_ScaleForPixelHeight(fontInfo, size);
            ascent = Math.round(ascentBuf.get(0) * scale);
            descent = Math.round(descentBuf.get(0) * scale);
            lineGap = Math.round(lineGapBuf.get(0) * scale);
        }

        // Calculate atlas size (start small, grow if needed)
        atlasWidth = 256;
        atlasHeight = 256;

        // Packed character data
        STBTTPackedchar.Buffer packedChars = STBTTPackedchar.malloc(CHAR_COUNT);
        ByteBuffer atlasData = null;
        boolean success = false;

        // Try increasingly larger atlas sizes
        while (!success && atlasWidth <= 4096) {
            atlasData = BufferUtils.createByteBuffer(atlasWidth * atlasHeight);

            STBTTPackContext packContext = STBTTPackContext.malloc();
            stbtt_PackBegin(packContext, atlasData, atlasWidth, atlasHeight, 0, 1);

            // Enable oversampling for better quality
            stbtt_PackSetOversampling(packContext, 2, 2);

            success = stbtt_PackFontRange(packContext, fontData, 0, size,
                    FIRST_CHAR, packedChars);

            stbtt_PackEnd(packContext);
            packContext.free();

            if (!success) {
                atlasWidth *= 2;
                atlasHeight *= 2;
                packedChars.clear();
            }
        }

        if (!success) {
            packedChars.free();
            throw new RuntimeException("Failed to pack font atlas (font too large?): " + path);
        }

        // Create OpenGL texture
        atlasTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, atlasTextureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, atlasWidth, atlasHeight, 0,
                GL_RED, GL_UNSIGNED_BYTE, atlasData);

        // Use linear filtering for smoother text
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glBindTexture(GL_TEXTURE_2D, 0);

        // Extract glyph data
        for (int i = 0; i < CHAR_COUNT; i++) {
            STBTTPackedchar pc = packedChars.get(i);
            int codepoint = FIRST_CHAR + i;

            // Use xoff2/yoff2 for correct screen-space dimensions
            // (x1-x0 and y1-y0 are atlas pixels, which are scaled by oversampling)
            float width = pc.xoff2() - pc.xoff();
            float height = pc.yoff2() - pc.yoff();

            float u0 = (float) pc.x0() / atlasWidth;
            float v0 = (float) pc.y0() / atlasHeight;
            float u1 = (float) pc.x1() / atlasWidth;
            float v1 = (float) pc.y1() / atlasHeight;

            // STB uses xoff/yoff relative to cursor position
            // xoff = bearingX, yoff = offset from baseline to top of glyph
            float bearingX = pc.xoff();
            float bearingY = -pc.yoff();  // Convert to "up from baseline"
            float advance = pc.xadvance();

            Glyph glyph = new Glyph(codepoint, width, height,
                    bearingX, bearingY, advance,
                    u0, v0, u1, v1);
            glyphs.put(codepoint, glyph);
        }
        atlasTexture = Texture.wrap(atlasTextureId, atlasWidth, atlasHeight);
        packedChars.free();
    }

    // ======================================================================
    // PUBLIC API
    // ======================================================================

    /**
     * Gets the glyph for a character.
     * Returns null for characters not in the atlas.
     */
    public Glyph getGlyph(int codepoint) {
        return glyphs.get(codepoint);
    }

    /**
     * Gets the glyph for a character.
     */
    public Glyph getGlyph(char c) {
        return glyphs.get((int) c);
    }

    /**
     * Gets the atlas texture ID for binding.
     */
    public int getAtlasTextureId() {
        return atlasTextureId;
    }

    /**
     * Gets the font size in pixels.
     */
    public int getSize() {
        return size;
    }

    /**
     * Gets the ascent (baseline to top of tallest glyph).
     */
    public int getAscent() {
        return ascent;
    }

    /**
     * Gets the descent (baseline to bottom of lowest glyph, typically negative).
     */
    public int getDescent() {
        return descent;
    }

    /**
     * Gets the recommended line gap.
     */
    public int getLineGap() {
        return lineGap;
    }

    /**
     * Gets the line height (ascent - descent + lineGap).
     * Use this for multi-line text spacing.
     */
    public int getLineHeight() {
        return ascent - descent + lineGap;
    }

    /**
     * Calculates the width of a string in pixels.
     * Does not account for newlines.
     */
    public float getStringWidth(String text) {
        float width = 0;
        for (int i = 0; i < text.length(); i++) {
            Glyph g = getGlyph(text.charAt(i));
            if (g != null) {
                width += g.advance;
            }
        }
        return width;
    }

    /**
     * Checks if a character is supported by this font.
     */
    public boolean hasGlyph(int codepoint) {
        return glyphs.containsKey(codepoint);
    }

    /**
     * Gets atlas dimensions.
     */
    public int getAtlasWidth() {
        return atlasWidth;
    }

    public int getAtlasHeight() {
        return atlasHeight;
    }

    /**
     * Binds the atlas texture to a texture unit.
     */
    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, atlasTextureId);
    }

    /**
     * Releases font resources. Call when font is no longer needed.
     */
    public void destroy() {
        if (atlasTextureId != 0) {
            glDeleteTextures(atlasTextureId);
            atlasTextureId = 0;
            atlasTexture = null;
        }
        glyphs.clear();
        System.out.println("Font destroyed: " + path);
    }

    @Override
    public String toString() {
        return String.format("Font[%s @ %dpx, atlas=%dx%d]", path, size, atlasWidth, atlasHeight);
    }
}