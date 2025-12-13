package com.pocket.rpg.rendering.renderers;

import static org.lwjgl.opengl.GL33.GL_FLOAT;
import static org.lwjgl.opengl.GL33.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL33.glVertexAttribPointer;

/**
 * Defines the vertex layout for sprite rendering.
 * Makes it easy to add new attributes and ensures consistency.
 */
public class VertexLayout {

    // ==================== Attribute Locations ====================
    public static final int ATTRIB_POSITION = 0;
    public static final int ATTRIB_TEXCOORD = 1;
    public static final int ATTRIB_COLOR = 2;    // Future: per-vertex color

    // ==================== Component Counts ====================
    public static final int POSITION_COMPONENTS = 2;  // x, y
    public static final int TEXCOORD_COMPONENTS = 2;  // u, v
    public static final int COLOR_COMPONENTS = 4;     // r, g, b, a

    // ==================== Sizes in Bytes ====================
    public static final int POSITION_SIZE = POSITION_COMPONENTS * Float.BYTES;
    public static final int TEXCOORD_SIZE = TEXCOORD_COMPONENTS * Float.BYTES;
    public static final int COLOR_SIZE = COLOR_COMPONENTS * Float.BYTES;

    // ==================== Offsets in Bytes ====================
    public static final int POSITION_OFFSET = 0;
    public static final int TEXCOORD_OFFSET = POSITION_OFFSET + POSITION_SIZE;
    public static final int COLOR_OFFSET = TEXCOORD_OFFSET + TEXCOORD_SIZE;

    // ==================== Stride (Total Size) ====================
    // With color: position(2) + texcoord(2) + color(4) = 8 floats
    public static final int FLOATS_PER_VERTEX = POSITION_COMPONENTS + TEXCOORD_COMPONENTS + COLOR_COMPONENTS;
    public static final int STRIDE = FLOATS_PER_VERTEX * Float.BYTES;

    // ==================== Derived Constants ====================
    public static final int VERTICES_PER_SPRITE = 6;  // 2 triangles
    public static final int FLOATS_PER_SPRITE = VERTICES_PER_SPRITE * FLOATS_PER_VERTEX;
    public static final int BYTES_PER_SPRITE = FLOATS_PER_SPRITE * Float.BYTES;

    /**
     * Sets up vertex attributes for a VAO.
     * Call this after binding the VAO and VBO.
     */
    public static void setupVertexAttributes() {
        // Position attribute
        glEnableVertexAttribArray(ATTRIB_POSITION);
        glVertexAttribPointer(
                ATTRIB_POSITION,
                POSITION_COMPONENTS,
                GL_FLOAT,
                false,
                STRIDE,
                POSITION_OFFSET
        );

        // TexCoord attribute
        glEnableVertexAttribArray(ATTRIB_TEXCOORD);
        glVertexAttribPointer(
                ATTRIB_TEXCOORD,
                TEXCOORD_COMPONENTS,
                GL_FLOAT,
                false,
                STRIDE,
                TEXCOORD_OFFSET
        );

        // Color attribute (commented out for now)
        glEnableVertexAttribArray(ATTRIB_COLOR);
        glVertexAttribPointer(ATTRIB_COLOR,
                COLOR_COMPONENTS,
                GL_FLOAT,
                false,
                STRIDE,
                COLOR_OFFSET);
    }

    /**
     * Returns a human-readable description of the vertex layout.
     */
    public static String describe() {
        return String.format(
                "Vertex Layout:%n" +
                        "  Position: location=%d, components=%d, offset=%d bytes%n" +
                        "  TexCoord: location=%d, components=%d, offset=%d bytes%n" +
                        "  Color: location=%d, components=%d, offset=%d bytes%n" +
                        "  Stride: %d bytes (%d floats)%n" +
                        "  Sprite: %d vertices, %d floats, %d bytes",
                ATTRIB_POSITION, POSITION_COMPONENTS, POSITION_OFFSET,
                ATTRIB_TEXCOORD, TEXCOORD_COMPONENTS, TEXCOORD_OFFSET,
                ATTRIB_COLOR, COLOR_COMPONENTS, COLOR_OFFSET,
                STRIDE, FLOATS_PER_VERTEX,
                VERTICES_PER_SPRITE, FLOATS_PER_SPRITE, BYTES_PER_SPRITE
        );
    }
}