package com.pocket.rpg.rendering.ui;

import com.pocket.rpg.components.ui.LayoutGroup;
import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UIComponent;
import com.pocket.rpg.components.ui.UIImage;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.rendering.resources.NineSlice;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Unified UI renderer for screen-space canvases.
 * <p>
 * Implements {@link UIRendererBackend} (for UIComponent rendering).
 * <p>
 * Coordinate system:
 * <ul>
 *   <li>Origin (0,0) at TOP-LEFT</li>
 *   <li>Positive X = right</li>
 *   <li>Positive Y = down</li>
 * </ul>
 * <p>
 * Supports:
 * <ul>
 *   <li>Immediate mode rendering (drawQuad, drawSprite) for panels, images, buttons</li>
 *   <li>Batched rendering (beginBatch, batchSprite, endBatch) for text</li>
 *   <li>Single-channel font atlas textures (alpha from red channel)</li>
 *   <li>Hierarchical positioning (children inherit parent's screen position)</li>
 * </ul>
 * <p>
 * <b>UNIFIED RENDERER NOTE:</b>
 * This class replaces {@code OpenGLUIRenderer} which was a duplicate implementation.
 * Use this class for all UI rendering in both standalone game and editor contexts.
 */
public class UIRenderer implements UIRendererBackend {

    // Optional - set via constructor for RenderPipeline usage
    private ViewportConfig viewportConfig;

    // Game resolution (fixed)
    private int gameWidth;
    private int gameHeight;

    // ========================================================================
    // IMMEDIATE MODE RESOURCES
    // ========================================================================

    private int vao, vbo, ebo;
    private int shaderProgram;
    private int whiteTexture;

    // Uniform locations
    private int uProjection;
    private int uModel;
    private int uColor;
    private int uTexture;
    private int uUseTexture;

    // Vertex data: position (2) + uv (2) = 4 floats per vertex, 4 vertices
    private final float[] vertices = new float[16];
    private final FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(16);

    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f modelMatrix = new Matrix4f();

    // ========================================================================
    // BATCH MODE RESOURCES
    // ========================================================================

    private static final int MAX_BATCH_SPRITES = 1000;
    private static final int FLOATS_PER_VERTEX = 8;  // pos(2) + uv(2) + color(4)
    private static final int VERTICES_PER_SPRITE = 4;
    private static final int INDICES_PER_SPRITE = 6;

    private int batchVao, batchVbo, batchEbo;
    private int batchShaderProgram;
    private int batchUProjection, batchUTexture, batchUIsText;

    private final float[] batchVertices = new float[MAX_BATCH_SPRITES * VERTICES_PER_SPRITE * FLOATS_PER_VERTEX];
    private final FloatBuffer batchVertexBuffer = BufferUtils.createFloatBuffer(batchVertices.length);
    private int batchSpriteCount = 0;
    private Texture currentBatchTexture = null;
    private boolean batchIsText = false;
    private boolean batching = false;

    private boolean initialized = false;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    /**
     * No-arg constructor for factory pattern.
     * Must call {@link #init(GameConfig)} before use.
     */
    public UIRenderer() {
    }

    /**
     * Constructor with ViewportConfig for RenderPipeline usage.
     * Call {@link #init()} after construction.
     *
     * @param viewportConfig Viewport configuration
     */
    public UIRenderer(ViewportConfig viewportConfig) {
        this.viewportConfig = viewportConfig;
    }

    // ========================================================================
    // INITIALIZATION - Interface Implementation
    // ========================================================================

    /**
     * Initialize with GameConfig (com.pocket.rpg.ui.UIRenderer interface).
     * Used by GameEngine when created via PlatformFactory.
     *
     * @param config Game configuration containing resolution
     */
    public void init(GameConfig config) {
        if (initialized) return;

        this.gameWidth = config.getGameWidth();
        this.gameHeight = config.getGameHeight();

        initResources();
    }

    /**
     * Initialize using ViewportConfig set in constructor.
     * Used by RenderPipeline.
     */
    public void init() {
        if (initialized) return;

        if (viewportConfig != null) {
            this.gameWidth = viewportConfig.getGameWidth();
            this.gameHeight = viewportConfig.getGameHeight();
        } else {
            throw new IllegalStateException("UIRenderer: No ViewportConfig set. Use init(GameConfig) or set ViewportConfig in constructor.");
        }

        initResources();
    }

    /**
     * Shared initialization logic.
     */
    private void initResources() {
        createShader();
        createBuffers();
        createWhiteTexture();
        createBatchResources();
        updateProjection();

        initialized = true;
    }

    /**
     * Update viewport size (com.pocket.rpg.ui.UIRenderer interface).
     * Currently unused but required by interface for pillarbox/letterbox offset calculation.
     *
     * @param width  Window width
     * @param height Window height
     */
    public void setViewportSize(int width, int height) {
        // Currently unused - UI renders in game resolution
        // Could be used for calculating pillarbox/letterbox offset if needed
    }

    private void updateProjection() {
        // Top-left origin: (0,0) at top-left, (gameWidth, gameHeight) at bottom-right
        projectionMatrix.identity().ortho(0, gameWidth, gameHeight, 0, -1, 1);
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Renders all UI canvases.
     * Canvases are rendered in order (assumes pre-sorted by sortOrder).
     *
     * @param canvases List of canvases to render
     */
    public void render(List<UICanvas> canvases) {
        if (!initialized || canvases == null || canvases.isEmpty()) {
            return;
        }

        // Setup render state
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);

        for (UICanvas canvas : canvases) {
            if (!canvas.isEnabled()) {
                continue;
            }
            if (canvas.getRenderMode() != UICanvas.RenderMode.SCREEN_SPACE_OVERLAY) {
                continue;
            }

            // Canvas root starts at (0,0) with full game resolution
            GameObject root = canvas.getGameObject();
            if (root == null) continue; // Skip canvases without a runtime GameObject (e.g. editor preview)
            renderCanvasSubtree(root, 0, 0, gameWidth, gameHeight);
        }

        glEnable(GL_DEPTH_TEST);
    }

    /**
     * Processes UI input.
     * Should be called before game update to consume mouse input over UI.
     */
    public void processInput() {
        // TODO: Implement input consumption
        // Set Input.isMouseConsumed() if mouse is over interactive UI element
    }

    // ========================================================================
    // CANVAS HIERARCHY TRAVERSAL
    // ========================================================================

    private void renderCanvasSubtree(GameObject root, float parentX, float parentY,
                                     float parentWidth, float parentHeight) {
        if (!root.isEnabled()) {
            return;
        }

        // Update UITransform with screen bounds
        // ALL UITransforms need screen bounds set for proper position calculations
        UITransform transform = root.getComponent(UITransform.class);
        if (transform != null) {
            transform.setScreenBounds(gameWidth, gameHeight);
            // Force position recalculation
            transform.markDirty();
        }

        // Render this object's UI components
        renderGameObjectUI(root);

        // Calculate bounds for children
        float childParentX = parentX;
        float childParentY = parentY;
        float childParentWidth = parentWidth;
        float childParentHeight = parentHeight;

        if (transform != null) {
            Vector2f screenPos = transform.getScreenPosition();
            childParentX = screenPos.x;
            childParentY = screenPos.y;
            childParentWidth = transform.getEffectiveWidth();  // Use effective for match parent
            childParentHeight = transform.getEffectiveHeight();
        }

        // Apply layout group before rendering children
        LayoutGroup layoutGroup = root.getComponent(LayoutGroup.class);
        if (layoutGroup != null && layoutGroup.isEnabled()) {
            layoutGroup.applyLayout();
        }

        // Render children
        for (GameObject child : root.getChildren()) {
            renderCanvasSubtree(child, childParentX, childParentY, childParentWidth, childParentHeight);
        }
    }

    private void renderGameObjectUI(GameObject go) {
        for (var component : go.getAllComponents()) {
            if (component instanceof UIComponent uiComp) {
                // Skip UICanvas - it's just a container marker
                if (!(uiComp instanceof UICanvas) && uiComp.isEnabled()) {
                    uiComp.render(this);
                }
            }
        }
    }

    // ========================================================================
    // IMMEDIATE MODE - UIRendererBackend Implementation
    // ========================================================================

    @Override
    public void drawQuad(float x, float y, float width, float height, Vector4f color) {
        drawQuad(x, y, width, height, 0, 0, 0, color);
    }

    @Override
    public void drawQuad(float x, float y, float width, float height,
                         float rotation, float originX, float originY, Vector4f color) {
        glUseProgram(shaderProgram);
        glBindVertexArray(vao);

        glUniformMatrix4fv(uProjection, false, projectionMatrix.get(new float[16]));

        // Apply rotation around origin
        if (rotation != 0) {
            float pivotX = x + originX * width;
            float pivotY = y + originY * height;
            modelMatrix.identity()
                    .translate(pivotX, pivotY, 0)
                    .rotateZ((float) Math.toRadians(-rotation))  // Negative for clockwise
                    .translate(-pivotX + x, -pivotY + y, 0);
        } else {
            modelMatrix.identity().translate(x, y, 0);
        }
        glUniformMatrix4fv(uModel, false, modelMatrix.get(new float[16]));

        glUniform4f(uColor, color.x, color.y, color.z, color.w);
        glUniform1i(uUseTexture, 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, whiteTexture);

        buildQuadVertices(0, 0, width, height, 0, 0, 1, 1);
        uploadVertices();

        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

        glBindVertexArray(0);
        glUseProgram(0);
    }

    @Override
    public void drawSprite(float x, float y, float width, float height, Sprite sprite, Vector4f tint) {
        drawSprite(x, y, width, height, 0, 0, 0, sprite, tint);
    }

    @Override
    public void drawSprite(float x, float y, float width, float height,
                           float rotation, float originX, float originY,
                           Sprite sprite, Vector4f tint) {
        glUseProgram(shaderProgram);
        glBindVertexArray(vao);

        glUniformMatrix4fv(uProjection, false, projectionMatrix.get(new float[16]));

        // Apply rotation around origin
        if (rotation != 0) {
            float pivotX = x + originX * width;
            float pivotY = y + originY * height;
            modelMatrix.identity()
                    .translate(pivotX, pivotY, 0)
                    .rotateZ((float) Math.toRadians(-rotation))  // Negative for clockwise
                    .translate(-pivotX + x, -pivotY + y, 0);
        } else {
            modelMatrix.identity().translate(x, y, 0);
        }
        glUniformMatrix4fv(uModel, false, modelMatrix.get(new float[16]));

        glUniform4f(uColor, tint.x, tint.y, tint.z, tint.w);

        if (sprite != null && sprite.getTexture() != null) {
            glUniform1i(uUseTexture, 1);
            sprite.getTexture().bind(0);
            // Flip V coordinates for sprites: textures are loaded with Y-flip,
            // so v0 (image top) maps to V=1 and v1 (image bottom) maps to V=0.
            // Swap v0/v1 so screen top shows image top.
            buildQuadVertices(0, 0, width, height,
                    sprite.getU0(), sprite.getV1(), sprite.getU1(), sprite.getV0());
        } else {
            glUniform1i(uUseTexture, 0);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, whiteTexture);
            buildQuadVertices(0, 0, width, height, 0, 0, 1, 1);
        }

        uploadVertices();
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

        glBindVertexArray(0);
        glUseProgram(0);
    }

    // ========================================================================
    // NINE-SLICE MODE - UIRendererBackend Implementation
    // ========================================================================

    @Override
    public void drawNineSlice(float x, float y, float width, float height,
                               float rotation, float originX, float originY,
                               Sprite sprite, Vector4f tint, boolean fillCenter) {
        if (sprite == null || !sprite.hasNineSlice()) {
            // Fallback to simple sprite draw
            drawSprite(x, y, width, height, rotation, originX, originY, sprite, tint);
            return;
        }

        NineSlice nineSlice = sprite.createNineSlice();

        // Get border sizes in pixels
        float left = nineSlice.getLeftBorder();
        float right = nineSlice.getRightBorder();
        float top = nineSlice.getTopBorder();
        float bottom = nineSlice.getBottomBorder();

        // Scale borders proportionally if size is smaller than minimum (Unity behavior)
        float minWidth = left + right;
        float minHeight = top + bottom;
        if (width < minWidth && minWidth > 0) {
            float scale = width / minWidth;
            left *= scale;
            right *= scale;
        }
        if (height < minHeight && minHeight > 0) {
            float scale = height / minHeight;
            top *= scale;
            bottom *= scale;
        }

        // Calculate center region size (may be 0 or negative if borders fill/exceed the space)
        float centerWidth = Math.max(0, width - left - right);
        float centerHeight = Math.max(0, height - top - bottom);

        glUseProgram(shaderProgram);
        glBindVertexArray(vao);
        glUniformMatrix4fv(uProjection, false, projectionMatrix.get(new float[16]));
        glUniform4f(uColor, tint.x, tint.y, tint.z, tint.w);
        glUniform1i(uUseTexture, 1);
        sprite.getTexture().bind(0);

        // Setup rotation if needed
        float pivotX = x + originX * width;
        float pivotY = y + originY * height;

        // Draw all 9 regions
        // NineSlice UV regions are now correctly mapped for Y-flipped textures:
        // TOP_* = visual top (high V), BOTTOM_* = visual bottom (low V)
        // Use regions in natural order with their corresponding border sizes

        // Screen top row (visual top of image)
        drawNineSliceRegion(x, y, left, top, nineSlice.getRegionUV(NineSlice.TOP_LEFT),
                rotation, pivotX, pivotY);
        drawNineSliceRegion(x + left, y, centerWidth, top, nineSlice.getRegionUV(NineSlice.TOP_CENTER),
                rotation, pivotX, pivotY);
        drawNineSliceRegion(x + left + centerWidth, y, right, top, nineSlice.getRegionUV(NineSlice.TOP_RIGHT),
                rotation, pivotX, pivotY);

        // Middle row
        drawNineSliceRegion(x, y + top, left, centerHeight, nineSlice.getRegionUV(NineSlice.MIDDLE_LEFT),
                rotation, pivotX, pivotY);
        if (fillCenter) {
            drawNineSliceRegion(x + left, y + top, centerWidth, centerHeight,
                    nineSlice.getRegionUV(NineSlice.MIDDLE_CENTER), rotation, pivotX, pivotY);
        }
        drawNineSliceRegion(x + left + centerWidth, y + top, right, centerHeight,
                nineSlice.getRegionUV(NineSlice.MIDDLE_RIGHT), rotation, pivotX, pivotY);

        // Screen bottom row (visual bottom of image)
        drawNineSliceRegion(x, y + top + centerHeight, left, bottom, nineSlice.getRegionUV(NineSlice.BOTTOM_LEFT),
                rotation, pivotX, pivotY);
        drawNineSliceRegion(x + left, y + top + centerHeight, centerWidth, bottom,
                nineSlice.getRegionUV(NineSlice.BOTTOM_CENTER), rotation, pivotX, pivotY);
        drawNineSliceRegion(x + left + centerWidth, y + top + centerHeight, right, bottom,
                nineSlice.getRegionUV(NineSlice.BOTTOM_RIGHT), rotation, pivotX, pivotY);

        glBindVertexArray(0);
        glUseProgram(0);
    }

    /**
     * Draws a single region of a 9-slice sprite.
     */
    private void drawNineSliceRegion(float x, float y, float width, float height,
                                      float[] uv, float rotation, float pivotX, float pivotY) {
        if (width <= 0 || height <= 0) return;

        // Apply rotation around pivot
        if (rotation != 0) {
            modelMatrix.identity()
                    .translate(pivotX, pivotY, 0)
                    .rotateZ((float) Math.toRadians(-rotation))
                    .translate(-pivotX, -pivotY, 0);
        } else {
            modelMatrix.identity();
        }
        glUniformMatrix4fv(uModel, false, modelMatrix.get(new float[16]));

        // Swap V for sprite (textures are loaded with Y-flip)
        // uv = [u0, v0, u1, v1] -> pass as [u0, v1, u1, v0]
        buildQuadVertices(x, y, width, height, uv[0], uv[3], uv[2], uv[1]);
        uploadVertices();
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    // ========================================================================
    // TILED MODE - UIRendererBackend Implementation
    // ========================================================================

    @Override
    public void drawTiled(float x, float y, float width, float height,
                          float rotation, float originX, float originY,
                          Sprite sprite, Vector4f tint, float pixelsPerUnit) {
        if (sprite == null) {
            drawQuad(x, y, width, height, rotation, originX, originY, tint);
            return;
        }

        glUseProgram(shaderProgram);
        glBindVertexArray(vao);
        glUniformMatrix4fv(uProjection, false, projectionMatrix.get(new float[16]));
        glUniform4f(uColor, tint.x, tint.y, tint.z, tint.w);
        glUniform1i(uUseTexture, 1);
        sprite.getTexture().bind(0);

        // Calculate tile size based on sprite dimensions and pixels per unit
        float tileWidth = sprite.getWidth() * (pixelsPerUnit / 100f);
        float tileHeight = sprite.getHeight() * (pixelsPerUnit / 100f);

        if (tileWidth <= 0) tileWidth = sprite.getWidth();
        if (tileHeight <= 0) tileHeight = sprite.getHeight();

        // Setup rotation
        float pivotX = x + originX * width;
        float pivotY = y + originY * height;

        if (rotation != 0) {
            modelMatrix.identity()
                    .translate(pivotX, pivotY, 0)
                    .rotateZ((float) Math.toRadians(-rotation))
                    .translate(-pivotX, -pivotY, 0);
        } else {
            modelMatrix.identity();
        }
        glUniformMatrix4fv(uModel, false, modelMatrix.get(new float[16]));

        // Draw tiles - swap V for sprite (textures are loaded with Y-flip)
        float u0 = sprite.getU0(), v0 = sprite.getV1();  // Swapped
        float u1 = sprite.getU1(), v1 = sprite.getV0();  // Swapped

        for (float ty = y; ty < y + height; ty += tileHeight) {
            for (float tx = x; tx < x + width; tx += tileWidth) {
                // Calculate actual tile dimensions (may be clipped at edges)
                float tw = Math.min(tileWidth, x + width - tx);
                float th = Math.min(tileHeight, y + height - ty);

                // Adjust UVs for partial tiles
                float tu1 = u0 + (u1 - u0) * (tw / tileWidth);
                float tv1 = v0 + (v1 - v0) * (th / tileHeight);

                buildQuadVertices(tx, ty, tw, th, u0, v0, tu1, tv1);
                uploadVertices();
                glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
            }
        }

        glBindVertexArray(0);
        glUseProgram(0);
    }

    // ========================================================================
    // FILLED MODE - UIRendererBackend Implementation
    // ========================================================================

    @Override
    public void drawFilled(float x, float y, float width, float height,
                           float rotation, float originX, float originY,
                           Sprite sprite, Vector4f tint,
                           UIImage.FillMethod fillMethod, UIImage.FillOrigin fillOrigin,
                           float fillAmount, boolean clockwise) {
        if (sprite == null || fillAmount <= 0) return;

        // Clamp fill amount
        fillAmount = Math.min(fillAmount, 1.0f);

        // For horizontal, vertical, and radial 360, full fill = full sprite
        // For radial 90/180, full fill only shows that portion of the sprite
        if (fillAmount >= 1.0f && fillMethod != UIImage.FillMethod.RADIAL_90
                && fillMethod != UIImage.FillMethod.RADIAL_180) {
            drawSprite(x, y, width, height, rotation, originX, originY, sprite, tint);
            return;
        }

        switch (fillMethod) {
            case HORIZONTAL -> drawFilledHorizontal(x, y, width, height, rotation, originX, originY,
                    sprite, tint, fillOrigin, fillAmount);
            case VERTICAL -> drawFilledVertical(x, y, width, height, rotation, originX, originY,
                    sprite, tint, fillOrigin, fillAmount);
            case RADIAL_90, RADIAL_180, RADIAL_360 ->
                    drawFilledRadial(x, y, width, height, rotation, originX, originY,
                            sprite, tint, fillMethod, fillOrigin, fillAmount, clockwise);
        }
    }

    private void drawFilledHorizontal(float x, float y, float width, float height,
                                       float rotation, float originX, float originY,
                                       Sprite sprite, Vector4f tint,
                                       UIImage.FillOrigin fillOrigin, float fillAmount) {
        float u0 = sprite.getU0(), v0 = sprite.getV0();
        float u1 = sprite.getU1(), v1 = sprite.getV1();

        float fillWidth = width * fillAmount;
        float drawX = x;
        float drawU0 = u0, drawU1 = u1;

        if (fillOrigin == UIImage.FillOrigin.LEFT) {
            // Fill from left
            drawU1 = u0 + (u1 - u0) * fillAmount;
        } else {
            // Fill from right
            drawX = x + width - fillWidth;
            drawU0 = u1 - (u1 - u0) * fillAmount;
        }

        glUseProgram(shaderProgram);
        glBindVertexArray(vao);
        glUniformMatrix4fv(uProjection, false, projectionMatrix.get(new float[16]));

        setupFilledRotation(x, y, width, height, rotation, originX, originY);

        glUniform4f(uColor, tint.x, tint.y, tint.z, tint.w);
        glUniform1i(uUseTexture, 1);
        sprite.getTexture().bind(0);

        // Swap V for sprite (textures are loaded with Y-flip)
        buildQuadVertices(drawX, y, fillWidth, height, drawU0, v1, drawU1, v0);
        uploadVertices();
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

        glBindVertexArray(0);
        glUseProgram(0);
    }

    private void drawFilledVertical(float x, float y, float width, float height,
                                     float rotation, float originX, float originY,
                                     Sprite sprite, Vector4f tint,
                                     UIImage.FillOrigin fillOrigin, float fillAmount) {
        float u0 = sprite.getU0(), v0 = sprite.getV0();
        float u1 = sprite.getU1(), v1 = sprite.getV1();

        float fillHeight = height * fillAmount;
        float drawY = y;
        float drawV0 = v0, drawV1 = v1;

        if (fillOrigin == UIImage.FillOrigin.TOP) {
            // Fill from top (Y-down coordinate system)
            drawV1 = v0 + (v1 - v0) * fillAmount;
        } else {
            // Fill from bottom
            drawY = y + height - fillHeight;
            drawV0 = v1 - (v1 - v0) * fillAmount;
        }

        glUseProgram(shaderProgram);
        glBindVertexArray(vao);
        glUniformMatrix4fv(uProjection, false, projectionMatrix.get(new float[16]));

        setupFilledRotation(x, y, width, height, rotation, originX, originY);

        glUniform4f(uColor, tint.x, tint.y, tint.z, tint.w);
        glUniform1i(uUseTexture, 1);
        sprite.getTexture().bind(0);

        // Swap V for sprite (textures are loaded with Y-flip)
        buildQuadVertices(x, drawY, width, fillHeight, u0, drawV1, u1, drawV0);
        uploadVertices();
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

        glBindVertexArray(0);
        glUseProgram(0);
    }

    private void drawFilledRadial(float x, float y, float width, float height,
                                   float rotation, float originX, float originY,
                                   Sprite sprite, Vector4f tint,
                                   UIImage.FillMethod fillMethod, UIImage.FillOrigin fillOrigin,
                                   float fillAmount, boolean clockwise) {
        // For radial fills, we render triangle fan segments from center
        // Screen coordinates: 0° = right, 90° = down, 180° = left, 270° = up

        float maxAngle = switch (fillMethod) {
            case RADIAL_90 -> 90f;
            case RADIAL_180 -> 180f;
            case RADIAL_360 -> 360f;
            default -> 360f;
        };

        float sweepAngle = maxAngle * fillAmount;

        // Calculate center
        float centerX = x + width / 2;
        float centerY = y + height / 2;

        // Start angle depends on origin - use cardinal directions
        // For RADIAL_90, the corner origins define which quadrant to fill
        // For RADIAL_180/360, we use the edge that the corner is adjacent to
        float startAngle = switch (fillOrigin) {
            case BOTTOM_LEFT -> clockwise ? 90f : 180f;   // Start from bottom or left edge
            case TOP_LEFT -> clockwise ? 180f : 270f;     // Start from left or top edge
            case TOP_RIGHT -> clockwise ? 270f : 0f;      // Start from top or right edge
            case BOTTOM_RIGHT -> clockwise ? 0f : 90f;    // Start from right or bottom edge
            default -> 0f;
        };

        // Apply clockwise/counter-clockwise direction
        float actualSweep = clockwise ? sweepAngle : -sweepAngle;

        glUseProgram(shaderProgram);
        glBindVertexArray(vao);
        glUniformMatrix4fv(uProjection, false, projectionMatrix.get(new float[16]));

        setupFilledRotation(x, y, width, height, rotation, originX, originY);

        glUniform4f(uColor, tint.x, tint.y, tint.z, tint.w);
        glUniform1i(uUseTexture, 1);
        sprite.getTexture().bind(0);

        // Render radial fill as triangle fan segments
        // Swap V coordinates for sprites (textures are loaded with Y-flip)
        float u0 = sprite.getU0(), v0 = sprite.getV1();  // Swapped: use V1 as "top"
        float u1 = sprite.getU1(), v1 = sprite.getV0();  // Swapped: use V0 as "bottom"
        float uCenter = (u0 + u1) / 2, vCenter = (v0 + v1) / 2;

        // Number of segments for smooth arc (at least 1 segment per 5 degrees)
        int segments = (int) Math.max(8, Math.abs(actualSweep) / 5);
        float angleStep = actualSweep / segments;

        // Draw the arc segments
        for (int i = 0; i < segments; i++) {
            float angle1 = startAngle + angleStep * i;
            float angle2 = startAngle + angleStep * (i + 1);

            // Convert to radians
            float rad1 = (float) Math.toRadians(angle1);
            float rad2 = (float) Math.toRadians(angle2);

            // Calculate edge points (extend to quad boundary)
            float[] p1 = getRadialEdgePoint(centerX, centerY, width, height, rad1);
            float[] p2 = getRadialEdgePoint(centerX, centerY, width, height, rad2);

            // Calculate UVs for edge points
            float[] uv1 = getRadialUV(p1[0], p1[1], x, y, width, height, u0, v0, u1, v1);
            float[] uv2 = getRadialUV(p2[0], p2[1], x, y, width, height, u0, v0, u1, v1);

            // Draw triangle: center -> edge1 -> edge2
            drawRadialTriangle(centerX, centerY, uCenter, vCenter,
                    p1[0], p1[1], uv1[0], uv1[1],
                    p2[0], p2[1], uv2[0], uv2[1]);
        }

        glBindVertexArray(0);
        glUseProgram(0);
    }

    private void setupFilledRotation(float x, float y, float width, float height,
                                      float rotation, float originX, float originY) {
        if (rotation != 0) {
            float pivotX = x + originX * width;
            float pivotY = y + originY * height;
            modelMatrix.identity()
                    .translate(pivotX, pivotY, 0)
                    .rotateZ((float) Math.toRadians(-rotation))
                    .translate(-pivotX, -pivotY, 0);
        } else {
            modelMatrix.identity();
        }
        glUniformMatrix4fv(uModel, false, modelMatrix.get(new float[16]));
    }

    /**
     * Gets the point where a ray from center at given angle intersects the quad boundary.
     */
    private float[] getRadialEdgePoint(float cx, float cy, float width, float height, float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        // Half dimensions
        float hw = width / 2;
        float hh = height / 2;

        // Find intersection with quad edges
        // We need to find the t where ray hits the boundary
        float t = Float.MAX_VALUE;

        // Check right edge (x = cx + hw)
        if (cos > 0.0001f) {
            float tRight = hw / cos;
            if (tRight > 0 && Math.abs(sin * tRight) <= hh) t = Math.min(t, tRight);
        }
        // Check left edge (x = cx - hw)
        if (cos < -0.0001f) {
            float tLeft = -hw / cos;
            if (tLeft > 0 && Math.abs(sin * tLeft) <= hh) t = Math.min(t, tLeft);
        }
        // Check bottom edge (y = cy + hh)
        if (sin > 0.0001f) {
            float tBottom = hh / sin;
            if (tBottom > 0 && Math.abs(cos * tBottom) <= hw) t = Math.min(t, tBottom);
        }
        // Check top edge (y = cy - hh)
        if (sin < -0.0001f) {
            float tTop = -hh / sin;
            if (tTop > 0 && Math.abs(cos * tTop) <= hw) t = Math.min(t, tTop);
        }

        return new float[]{cx + cos * t, cy + sin * t};
    }

    /**
     * Calculates UV coordinates for a point within the quad.
     * For sprites with Y-flipped textures, v0/v1 should be pre-swapped by caller.
     */
    private float[] getRadialUV(float px, float py, float x, float y, float width, float height,
                                 float u0, float v0, float u1, float v1) {
        float u = u0 + (u1 - u0) * ((px - x) / width);
        float v = v0 + (v1 - v0) * ((py - y) / height);
        return new float[]{u, v};
    }

    /**
     * Draws a single triangle for radial fill.
     */
    private void drawRadialTriangle(float x0, float y0, float u0, float v0,
                                     float x1, float y1, float u1, float v1,
                                     float x2, float y2, float u2, float v2) {
        // Build triangle vertices (reusing the quad vertex array, only using first 3 vertices)
        vertices[0] = x0;  vertices[1] = y0;  vertices[2] = u0;  vertices[3] = v0;
        vertices[4] = x1;  vertices[5] = y1;  vertices[6] = u1;  vertices[7] = v1;
        vertices[8] = x2;  vertices[9] = y2;  vertices[10] = u2; vertices[11] = v2;

        vertexBuffer.clear();
        vertexBuffer.put(vertices, 0, 12);
        vertexBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);

        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    // ========================================================================
    // BATCHED MODE - UIRendererBackend Implementation
    // ========================================================================

    @Override
    public void beginBatch(Texture texture) {
        if (batching) {
            System.err.println("[UIRenderer] WARNING: beginBatch called while batching. Flushing.");
            endBatch();
        }

        currentBatchTexture = texture;
        batchIsText = (texture != null);  // Text mode uses font atlas texture
        batchSpriteCount = 0;
        batching = true;
    }

    @Override
    public void batchSprite(float x, float y, float width, float height,
                            float u0, float v0, float u1, float v1, Vector4f tint) {
        if (!batching) {
            System.err.println("[UIRenderer] WARNING: batchSprite called without beginBatch");
            return;
        }

        if (batchSpriteCount >= MAX_BATCH_SPRITES) {
            flushBatch();
        }

        int offset = batchSpriteCount * VERTICES_PER_SPRITE * FLOATS_PER_VERTEX;

        // NOTE: batchSprite uses raw UV coordinates as-is (no V flip)
        // This is used by UIText with font atlas UVs which don't need flipping
        // (Font atlases are uploaded without Y-flip, so V=0 = top of atlas)

        // Top-left vertex
        batchVertices[offset + 0] = x;
        batchVertices[offset + 1] = y;
        batchVertices[offset + 2] = u0;
        batchVertices[offset + 3] = v0;
        batchVertices[offset + 4] = tint.x;
        batchVertices[offset + 5] = tint.y;
        batchVertices[offset + 6] = tint.z;
        batchVertices[offset + 7] = tint.w;

        // Top-right vertex
        batchVertices[offset + 8] = x + width;
        batchVertices[offset + 9] = y;
        batchVertices[offset + 10] = u1;
        batchVertices[offset + 11] = v0;
        batchVertices[offset + 12] = tint.x;
        batchVertices[offset + 13] = tint.y;
        batchVertices[offset + 14] = tint.z;
        batchVertices[offset + 15] = tint.w;

        // Bottom-right vertex
        batchVertices[offset + 16] = x + width;
        batchVertices[offset + 17] = y + height;
        batchVertices[offset + 18] = u1;
        batchVertices[offset + 19] = v1;
        batchVertices[offset + 20] = tint.x;
        batchVertices[offset + 21] = tint.y;
        batchVertices[offset + 22] = tint.z;
        batchVertices[offset + 23] = tint.w;

        // Bottom-left vertex
        batchVertices[offset + 24] = x;
        batchVertices[offset + 25] = y + height;
        batchVertices[offset + 26] = u0;
        batchVertices[offset + 27] = v1;
        batchVertices[offset + 28] = tint.x;
        batchVertices[offset + 29] = tint.y;
        batchVertices[offset + 30] = tint.z;
        batchVertices[offset + 31] = tint.w;

        batchSpriteCount++;
    }

    @Override
    public void batchSprite(float x, float y, float width, float height,
                            float u0, float v0, float u1, float v1,
                            float rotation, float pivotX, float pivotY, Vector4f tint) {
        // If no rotation, use the simpler method
        if (rotation == 0) {
            batchSprite(x, y, width, height, u0, v0, u1, v1, tint);
            return;
        }

        if (!batching) {
            System.err.println("[UIRenderer] WARNING: batchSprite called without beginBatch");
            return;
        }

        if (batchSpriteCount >= MAX_BATCH_SPRITES) {
            flushBatch();
        }

        // Calculate the 4 corners before rotation
        float x0 = x, y0 = y;                    // Top-left
        float x1 = x + width, y1 = y;            // Top-right
        float x2 = x + width, y2 = y + height;   // Bottom-right
        float x3 = x, y3 = y + height;           // Bottom-left

        // Precompute rotation values
        float cos = (float) Math.cos(Math.toRadians(-rotation));
        float sin = (float) Math.sin(Math.toRadians(-rotation));

        // Rotate each corner around pivot
        float rx0 = rotateX(x0, y0, pivotX, pivotY, cos, sin);
        float ry0 = rotateY(x0, y0, pivotX, pivotY, cos, sin);
        float rx1 = rotateX(x1, y1, pivotX, pivotY, cos, sin);
        float ry1 = rotateY(x1, y1, pivotX, pivotY, cos, sin);
        float rx2 = rotateX(x2, y2, pivotX, pivotY, cos, sin);
        float ry2 = rotateY(x2, y2, pivotX, pivotY, cos, sin);
        float rx3 = rotateX(x3, y3, pivotX, pivotY, cos, sin);
        float ry3 = rotateY(x3, y3, pivotX, pivotY, cos, sin);

        int offset = batchSpriteCount * VERTICES_PER_SPRITE * FLOATS_PER_VERTEX;

        // NOTE: batchSprite uses raw UV coordinates as-is (no V flip)
        // This is used by UIText with font atlas UVs which don't need flipping

        // Top-left vertex (rotated)
        batchVertices[offset + 0] = rx0;
        batchVertices[offset + 1] = ry0;
        batchVertices[offset + 2] = u0;
        batchVertices[offset + 3] = v0;
        batchVertices[offset + 4] = tint.x;
        batchVertices[offset + 5] = tint.y;
        batchVertices[offset + 6] = tint.z;
        batchVertices[offset + 7] = tint.w;

        // Top-right vertex (rotated)
        batchVertices[offset + 8] = rx1;
        batchVertices[offset + 9] = ry1;
        batchVertices[offset + 10] = u1;
        batchVertices[offset + 11] = v0;
        batchVertices[offset + 12] = tint.x;
        batchVertices[offset + 13] = tint.y;
        batchVertices[offset + 14] = tint.z;
        batchVertices[offset + 15] = tint.w;

        // Bottom-right vertex (rotated)
        batchVertices[offset + 16] = rx2;
        batchVertices[offset + 17] = ry2;
        batchVertices[offset + 18] = u1;
        batchVertices[offset + 19] = v1;
        batchVertices[offset + 20] = tint.x;
        batchVertices[offset + 21] = tint.y;
        batchVertices[offset + 22] = tint.z;
        batchVertices[offset + 23] = tint.w;

        // Bottom-left vertex (rotated)
        batchVertices[offset + 24] = rx3;
        batchVertices[offset + 25] = ry3;
        batchVertices[offset + 26] = u0;
        batchVertices[offset + 27] = v1;
        batchVertices[offset + 28] = tint.x;
        batchVertices[offset + 29] = tint.y;
        batchVertices[offset + 30] = tint.z;
        batchVertices[offset + 31] = tint.w;

        batchSpriteCount++;
    }

    /**
     * Rotates X coordinate around a pivot point.
     */
    private float rotateX(float x, float y, float px, float py, float cos, float sin) {
        float dx = x - px;
        float dy = y - py;
        return px + dx * cos - dy * sin;
    }

    /**
     * Rotates Y coordinate around a pivot point.
     */
    private float rotateY(float x, float y, float px, float py, float cos, float sin) {
        float dx = x - px;
        float dy = y - py;
        return py + dx * sin + dy * cos;
    }

    @Override
    public void endBatch() {
        if (!batching) return;
        flushBatch();
        batching = false;
        currentBatchTexture = null;
        batchIsText = false;
    }

    @Override
    public int getMaxBatchSize() {
        return MAX_BATCH_SPRITES;
    }

    private void flushBatch() {
        if (batchSpriteCount == 0) return;

        // Upload vertex data
        batchVertexBuffer.clear();
        batchVertexBuffer.put(batchVertices, 0, batchSpriteCount * VERTICES_PER_SPRITE * FLOATS_PER_VERTEX);
        batchVertexBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, batchVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, batchVertexBuffer);

        // Bind shader and VAO
        glUseProgram(batchShaderProgram);
        glBindVertexArray(batchVao);

        glUniformMatrix4fv(batchUProjection, false, projectionMatrix.get(new float[16]));
        glUniform1i(batchUIsText, batchIsText ? 1 : 0);

        // Bind texture
        if (currentBatchTexture != null) {
            currentBatchTexture.bind(0);
        } else if (!batchIsText) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, whiteTexture);
        }
        // For text mode, texture is already bound by UIText.font.bind()

        // Draw
        glDrawElements(GL_TRIANGLES, batchSpriteCount * INDICES_PER_SPRITE, GL_UNSIGNED_INT, 0);

        glBindVertexArray(0);
        glUseProgram(0);

        batchSpriteCount = 0;
    }

    // ========================================================================
    // INTERNAL HELPERS
    // ========================================================================

    private void buildQuadVertices(float x, float y, float w, float h,
                                   float u0, float v0, float u1, float v1) {
        // Standard UV mapping: v0 at screen top, v1 at screen bottom
        // Top-left
        vertices[0] = x;      vertices[1] = y;      vertices[2] = u0; vertices[3] = v0;
        // Top-right
        vertices[4] = x + w;  vertices[5] = y;      vertices[6] = u1; vertices[7] = v0;
        // Bottom-right
        vertices[8] = x + w;  vertices[9] = y + h;  vertices[10] = u1; vertices[11] = v1;
        // Bottom-left
        vertices[12] = x;     vertices[13] = y + h; vertices[14] = u0; vertices[15] = v1;
    }

    private void uploadVertices() {
        vertexBuffer.clear();
        vertexBuffer.put(vertices);
        vertexBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);
    }

    // ========================================================================
    // RESOURCE CREATION
    // ========================================================================

    private void createBuffers() {
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 16 * Float.BYTES, GL_DYNAMIC_DRAW);

        // Position attribute (location 0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        // UV attribute (location 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        // Index buffer
        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        int[] indices = { 0, 1, 2, 2, 3, 0 };
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glBindVertexArray(0);
    }

    private void createBatchResources() {
        batchVao = glGenVertexArrays();
        glBindVertexArray(batchVao);

        // Vertex buffer (dynamic)
        batchVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, batchVbo);
        glBufferData(GL_ARRAY_BUFFER, (long) batchVertices.length * Float.BYTES, GL_DYNAMIC_DRAW);

        // Position (2 floats)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        // UV (2 floats)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        // Color (4 floats)
        glVertexAttribPointer(2, 4, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 4 * Float.BYTES);
        glEnableVertexAttribArray(2);

        // Index buffer (static)
        batchEbo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, batchEbo);

        int[] batchIndices = new int[MAX_BATCH_SPRITES * INDICES_PER_SPRITE];
        for (int i = 0; i < MAX_BATCH_SPRITES; i++) {
            int vertexOffset = i * 4;
            int indexOffset = i * 6;
            batchIndices[indexOffset + 0] = vertexOffset + 0;
            batchIndices[indexOffset + 1] = vertexOffset + 1;
            batchIndices[indexOffset + 2] = vertexOffset + 2;
            batchIndices[indexOffset + 3] = vertexOffset + 2;
            batchIndices[indexOffset + 4] = vertexOffset + 3;
            batchIndices[indexOffset + 5] = vertexOffset + 0;
        }
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, batchIndices, GL_STATIC_DRAW);

        glBindVertexArray(0);

        createBatchShader();
    }

    private void createWhiteTexture() {
        whiteTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, whiteTexture);

        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255);
        pixel.flip();

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void createShader() {
        String vertexSource = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec2 aUV;
            
            uniform mat4 uProjection;
            uniform mat4 uModel;
            
            out vec2 vUV;
            
            void main() {
                gl_Position = uProjection * uModel * vec4(aPos, 0.0, 1.0);
                vUV = aUV;
            }
            """;

        String fragmentSource = """
            #version 330 core
            in vec2 vUV;
            
            uniform sampler2D uTexture;
            uniform vec4 uColor;
            uniform int uUseTexture;
            
            out vec4 FragColor;
            
            void main() {
                vec4 texColor = texture(uTexture, vUV);
                if (uUseTexture == 1) {
                    FragColor = texColor * uColor;
                } else {
                    FragColor = uColor;
                }
            }
            """;

        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vertexSource);
        glCompileShader(vertexShader);
        checkShaderError(vertexShader, "VERTEX");

        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fragmentSource);
        glCompileShader(fragmentShader);
        checkShaderError(fragmentShader, "FRAGMENT");

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vertexShader);
        glAttachShader(shaderProgram, fragmentShader);
        glLinkProgram(shaderProgram);
        checkProgramError(shaderProgram);

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        uProjection = glGetUniformLocation(shaderProgram, "uProjection");
        uModel = glGetUniformLocation(shaderProgram, "uModel");
        uColor = glGetUniformLocation(shaderProgram, "uColor");
        uTexture = glGetUniformLocation(shaderProgram, "uTexture");
        uUseTexture = glGetUniformLocation(shaderProgram, "uUseTexture");

        glUseProgram(shaderProgram);
        glUniform1i(uTexture, 0);
        glUseProgram(0);
    }

    private void createBatchShader() {
        String vertexSource = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec2 aUV;
            layout (location = 2) in vec4 aColor;
            
            uniform mat4 uProjection;
            
            out vec2 vUV;
            out vec4 vColor;
            
            void main() {
                gl_Position = uProjection * vec4(aPos, 0.0, 1.0);
                vUV = aUV;
                vColor = aColor;
            }
            """;

        String fragmentSource = """
            #version 330 core
            in vec2 vUV;
            in vec4 vColor;
            
            uniform sampler2D uTexture;
            uniform int uIsText;  // 1 = font atlas (red channel as alpha)
            
            out vec4 FragColor;
            
            void main() {
                vec4 texColor = texture(uTexture, vUV);
                
                if (uIsText == 1) {
                    // Font atlas: red channel is coverage/alpha
                    float alpha = texColor.r * vColor.a;
                    FragColor = vec4(vColor.rgb, alpha);
                } else {
                    // Regular RGBA texture
                    FragColor = texColor * vColor;
                }
            }
            """;

        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vertexSource);
        glCompileShader(vertexShader);
        checkShaderError(vertexShader, "BATCH_VERTEX");

        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fragmentSource);
        glCompileShader(fragmentShader);
        checkShaderError(fragmentShader, "BATCH_FRAGMENT");

        batchShaderProgram = glCreateProgram();
        glAttachShader(batchShaderProgram, vertexShader);
        glAttachShader(batchShaderProgram, fragmentShader);
        glLinkProgram(batchShaderProgram);
        checkProgramError(batchShaderProgram);

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        batchUProjection = glGetUniformLocation(batchShaderProgram, "uProjection");
        batchUTexture = glGetUniformLocation(batchShaderProgram, "uTexture");
        batchUIsText = glGetUniformLocation(batchShaderProgram, "uIsText");

        glUseProgram(batchShaderProgram);
        glUniform1i(batchUTexture, 0);
        glUniform1i(batchUIsText, 0);
        glUseProgram(0);
    }

    private void checkShaderError(int shader, String type) {
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            throw new RuntimeException("[UIRenderer] " + type + " shader compilation failed:\n" + log);
        }
    }

    private void checkProgramError(int program) {
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            throw new RuntimeException("[UIRenderer] Shader program linking failed:\n" + log);
        }
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    public boolean isInitialized() {
        return initialized;
    }

    public void destroy() {
        if (!initialized) return;

        glDeleteProgram(shaderProgram);
        glDeleteProgram(batchShaderProgram);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteBuffers(batchVbo);
        glDeleteBuffers(batchEbo);
        glDeleteVertexArrays(vao);
        glDeleteVertexArrays(batchVao);
        glDeleteTextures(whiteTexture);

        initialized = false;
    }
}
