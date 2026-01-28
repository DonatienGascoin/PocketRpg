package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.rendering.batch.BatchRenderer;
import com.pocket.rpg.rendering.resources.Sprite;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Renders a single sprite through the BatchRenderer into an off-screen FBO,
 * respecting pivot points and world-unit scaling for WYSIWYG preview.
 * <p>
 * Usage: call {@link #renderSprite} during ImGui frame to draw a sprite
 * preview at a given screen position. The checker background is drawn via
 * ImDrawList, and the sprite is rendered via the pipeline into an FBO
 * then composited on top.
 */
public class SpritePreviewRenderer {

    private static final int CHECKER_SIZE = 8;

    private EditorFramebuffer framebuffer;
    private BatchRenderer batchRenderer;
    private boolean initialized = false;

    private int lastFboWidth = 0;
    private int lastFboHeight = 0;

    /**
     * Renders a sprite preview at the given screen area.
     * Draws checker background, then renders the sprite through the pipeline
     * with pivot support, and composites the result.
     *
     * @param drawList    The ImGui draw list to render into
     * @param sprite      The sprite to render (null draws only background + "No animation" text)
     * @param areaX       Screen X of the preview area
     * @param areaY       Screen Y of the preview area
     * @param areaWidth   Width of the preview area in pixels
     * @param areaHeight  Height of the preview area in pixels
     * @param maxRefWidth  Max sprite world width for stable scaling (0 = use sprite's own)
     * @param maxRefHeight Max sprite world height for stable scaling (0 = use sprite's own)
     */
    public void renderSprite(ImDrawList drawList, Sprite sprite,
                             float areaX, float areaY, float areaWidth, float areaHeight,
                             float maxRefWidth, float maxRefHeight) {
        // Draw checker background
        drawCheckerBackground(drawList, areaX, areaY, areaWidth, areaHeight);

        if (sprite == null || sprite.getTexture() == null) {
            // Draw centered "No animation" text
            String text = "No animation";
            ImVec2 textSize = new ImVec2();
            ImGui.calcTextSize(textSize, text);
            float textX = areaX + (areaWidth - textSize.x) / 2;
            float textY = areaY + (areaHeight - textSize.y) / 2;
            drawList.addText(textX, textY,
                    ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1f), text);
            return;
        }

        int fboW = Math.max(1, (int) areaWidth);
        int fboH = Math.max(1, (int) areaHeight);

        ensureInitialized(fboW, fboH);

        // Render sprite to FBO
        renderSpriteToFBO(sprite, fboW, fboH, maxRefWidth, maxRefHeight);

        // Composite FBO texture on top of checker (V-flipped for OpenGL)
        drawList.addImage(framebuffer.getTextureId(),
                areaX, areaY,
                areaX + areaWidth, areaY + areaHeight,
                0, 1, 1, 0);
    }

    /**
     * Cleans up GPU resources.
     */
    public void destroy() {
        if (framebuffer != null) {
            framebuffer.destroy();
            framebuffer = null;
        }
        if (batchRenderer != null) {
            batchRenderer.destroy();
            batchRenderer = null;
        }
        initialized = false;
    }

    // ========================================================================
    // INTERNALS
    // ========================================================================

    private void ensureInitialized(int width, int height) {
        if (!initialized) {
            RenderingConfig config = ConfigLoader.getConfig(ConfigLoader.ConfigType.RENDERING);
            batchRenderer = new BatchRenderer(config);
            batchRenderer.init(width, height);
            framebuffer = new EditorFramebuffer(width, height);
            framebuffer.init();
            lastFboWidth = width;
            lastFboHeight = height;
            initialized = true;
        } else if (width != lastFboWidth || height != lastFboHeight) {
            framebuffer.resize(width, height);
            lastFboWidth = width;
            lastFboHeight = height;
        }
    }

    private void renderSpriteToFBO(Sprite sprite, int fboW, int fboH,
                                   float maxRefW, float maxRefH) {
        float worldW = sprite.getWorldWidth();
        float worldH = sprite.getWorldHeight();
        float refW = maxRefW > 0 ? maxRefW : worldW;
        float refH = maxRefH > 0 ? maxRefH : worldH;

        // Calculate view extent in world units to fit the reference sprite
        float fboAspect = (float) fboW / fboH;
        float refAspect = refW / refH;

        float viewHalfW, viewHalfH;
        if (refAspect > fboAspect) {
            viewHalfW = refW * 0.6f;
            viewHalfH = viewHalfW / fboAspect;
        } else {
            viewHalfH = refH * 0.6f;
            viewHalfW = viewHalfH * fboAspect;
        }

        // Orthographic projection centered at (0,0), Y-down matching game convention
        Matrix4f projection = new Matrix4f().ortho(
                -viewHalfW, viewHalfW,
                -viewHalfH, viewHalfH,
                -1, 1
        );
        Matrix4f view = new Matrix4f().identity();

        // Save current GL state
        int[] prevViewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, prevViewport);
        int prevFbo = glGetInteger(GL_FRAMEBUFFER_BINDING);

        // Bind FBO and render
        framebuffer.bind();
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        framebuffer.clear(0, 0, 0, 0); // Transparent background

        batchRenderer.beginWithMatrices(projection, view, null);
        batchRenderer.getBatch().submit(
                sprite,
                0, 0,
                worldW, worldH,
                0,
                sprite.getPivotX(), sprite.getPivotY(),
                0,
                new Vector4f(1, 1, 1, 1)
        );
        batchRenderer.end();

        // Restore GL state
        glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);
        glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
    }

    private void drawCheckerBackground(ImDrawList drawList, float x, float y,
                                       float width, float height) {
        int colorA = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f);
        int colorB = ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1f);

        int cols = (int) Math.ceil(width / CHECKER_SIZE);
        int rows = (int) Math.ceil(height / CHECKER_SIZE);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                float rectX = x + col * CHECKER_SIZE;
                float rectY = y + row * CHECKER_SIZE;
                float rectW = Math.min(CHECKER_SIZE, x + width - rectX);
                float rectH = Math.min(CHECKER_SIZE, y + height - rectY);

                int color = ((row + col) % 2 == 0) ? colorA : colorB;
                drawList.addRectFilled(rectX, rectY, rectX + rectW, rectY + rectH, color);
            }
        }
    }
}
