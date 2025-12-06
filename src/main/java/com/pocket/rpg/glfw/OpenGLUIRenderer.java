package com.pocket.rpg.glfw;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.ui.*;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * OpenGL implementation of UIRenderer.
 * Renders UI in game resolution with TOP-LEFT origin (matching Camera).
 * Scales with pillarbox/letterbox to match game viewport.
 *
 * Coordinate system:
 * - Origin (0,0) at TOP-LEFT
 * - Positive X = right
 * - Positive Y = down
 *
 * Supports:
 * - Immediate mode rendering (drawQuad, drawSprite)
 * - Batched rendering for text/particles (beginBatch, batchSprite, endBatch)
 * - Single-channel font atlas textures (alpha from red channel)
 * - Hierarchical positioning (children inherit parent's screen position)
 */
public class OpenGLUIRenderer implements UIRenderer, UIRendererBackend {

    private int vao, vbo, ebo;
    private int shaderProgram;
    private int whiteTexture;

    // Game resolution (fixed, from config)
    private int gameWidth;
    private int gameHeight;

    // Viewport size (changes on window resize)
    private int viewportWidth;
    private int viewportHeight;

    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f modelMatrix = new Matrix4f();

    // Shader uniform locations
    private int uProjection;
    private int uModel;
    private int uColor;
    private int uTexture;
    private int uUseTexture;

    // Immediate mode vertex data: position (2) + uv (2) = 4 floats per vertex, 4 vertices
    private final float[] vertices = new float[16];
    private final FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(16);

    // ========================================
    // BATCHING
    // ========================================

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
    private boolean batchIsText = false;  // True when rendering font atlas (single-channel)
    private boolean batching = false;

    private boolean initialized = false;

    @Override
    public void init(GameConfig config) {
        if (initialized) return;

        this.gameWidth = config.getGameWidth();
        this.gameHeight = config.getGameHeight();

        createShader();
        createBuffers();
        createWhiteTexture();
        createBatchResources();

        updateProjection();

        initialized = true;
        System.out.println("OpenGLUIRenderer initialized (game: " + gameWidth + "x" + gameHeight + ", Y=0 at top)");
    }

    @Override
    public void setViewportSize(int width, int height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
    }

    private void updateProjection() {
        // TOP-LEFT origin: (0,0) at top-left, (gameWidth, gameHeight) at bottom-right
        projectionMatrix.identity().ortho(0, gameWidth, gameHeight, 0, -1, 1);
    }

    @Override
    public void render(List<UICanvas> canvases) {
        if (!initialized || canvases.isEmpty()) return;

        // Setup render state
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);

        // Canvases are pre-sorted by Scene
        for (UICanvas canvas : canvases) {
            if (!canvas.isEnabled()) continue;
            if (canvas.getRenderMode() != UICanvas.RenderMode.SCREEN_SPACE_OVERLAY) continue;

            // Canvas root starts at (0,0) with full game resolution as bounds
            renderCanvasSubtree(canvas.getGameObject(), 0, 0, gameWidth, gameHeight);
        }

        glEnable(GL_DEPTH_TEST);
    }

    /**
     * Recursively renders a UI subtree.
     *
     * @param root         The GameObject to render
     * @param parentX      Parent's absolute X position on screen
     * @param parentY      Parent's absolute Y position on screen
     * @param parentWidth  Parent's width in pixels
     * @param parentHeight Parent's height in pixels
     */
    private void renderCanvasSubtree(GameObject root, float parentX, float parentY,
                                     float parentWidth, float parentHeight) {
        if (!root.isEnabled()) return;

        // Update UITransform with parent bounds AND position
        UITransform transform = root.getComponent(UITransform.class);
        if (transform != null) {
            transform.setParentBounds(parentX, parentY, parentWidth, parentHeight);
        }

        // Render this object's UI components
        renderGameObjectUI(root);

        // Calculate this object's bounds for children
        float childParentX = parentX;
        float childParentY = parentY;
        float childParentWidth = parentWidth;
        float childParentHeight = parentHeight;

        if (transform != null) {
            // Children use this object's screen position and size as their parent bounds
            Vector2f screenPos = transform.getScreenPosition();
            childParentX = screenPos.x;
            childParentY = screenPos.y;
            childParentWidth = transform.getWidth();
            childParentHeight = transform.getHeight();
        }

        // Render children
        for (GameObject child : root.getChildren()) {
            renderCanvasSubtree(child, childParentX, childParentY, childParentWidth, childParentHeight);
        }
    }

    private void renderGameObjectUI(GameObject go) {
        // Get all UI components and render them
        for (var component : go.getAllComponents()) {
            if (component instanceof UIComponent uiComp && uiComp.isEnabled()) {
                // Skip UICanvas itself - it's just a marker
                if (!(uiComp instanceof UICanvas)) {
                    uiComp.render(this);
                }
            }
        }
    }

    // =========================================
    // IMMEDIATE MODE - UIRendererBackend Implementation
    // =========================================

    @Override
    public void drawQuad(float x, float y, float width, float height, Vector4f color) {
        // Self-contained: bind our own shader and VAO
        glUseProgram(shaderProgram);
        glBindVertexArray(vao);

        glUniformMatrix4fv(uProjection, false, projectionMatrix.get(new float[16]));

        modelMatrix.identity().translate(x, y, 0);
        glUniformMatrix4fv(uModel, false, modelMatrix.get(new float[16]));

        glUniform4f(uColor, color.x, color.y, color.z, color.w);
        glUniform1i(uUseTexture, 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, whiteTexture);

        // Build quad from top-left corner (Y increases downward)
        buildQuadVertices(0, 0, width, height, 0, 0, 1, 1);
        uploadVertices();

        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

        // Clean up
        glBindVertexArray(0);
        glUseProgram(0);
    }

    @Override
    public void drawSprite(float x, float y, float width, float height, Sprite sprite, Vector4f tint) {
        // Self-contained: bind our own shader and VAO
        glUseProgram(shaderProgram);
        glBindVertexArray(vao);

        glUniformMatrix4fv(uProjection, false, projectionMatrix.get(new float[16]));

        modelMatrix.identity().translate(x, y, 0);
        glUniformMatrix4fv(uModel, false, modelMatrix.get(new float[16]));

        glUniform4f(uColor, tint.x, tint.y, tint.z, tint.w);

        if (sprite != null && sprite.getTexture() != null) {
            glUniform1i(uUseTexture, 1);
            sprite.getTexture().bind(0);
            // UV coordinates: v0 = top, v1 = bottom
            buildQuadVertices(0, 0, width, height,
                    sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
        } else {
            glUniform1i(uUseTexture, 0);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, whiteTexture);
            buildQuadVertices(0, 0, width, height, 0, 0, 1, 1);
        }

        uploadVertices();
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

        // Clean up
        glBindVertexArray(0);
        glUseProgram(0);
    }

    // =========================================
    // BATCHED MODE - UIRendererBackend Implementation
    // =========================================

    @Override
    public void beginBatch(Texture texture) {
        if (batching) {
            System.err.println("WARNING: beginBatch() called while already batching. Flushing previous batch.");
            endBatch();
        }

        currentBatchTexture = texture;
        // If texture is null, we're in text mode (font atlas manually bound by UIText)
        batchIsText = (texture == null);
        batchSpriteCount = 0;
        batching = true;
    }

    @Override
    public void batchSprite(float x, float y, float width, float height,
                            float u0, float v0, float u1, float v1, Vector4f tint) {
        if (!batching) {
            System.err.println("WARNING: batchSprite() called without beginBatch()");
            return;
        }

        if (batchSpriteCount >= MAX_BATCH_SPRITES) {
            // Flush and continue
            flushBatch();
        }

        int offset = batchSpriteCount * VERTICES_PER_SPRITE * FLOATS_PER_VERTEX;

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
    public void endBatch() {
        if (!batching) return;
        flushBatch();
        batching = false;
        currentBatchTexture = null;
        batchIsText = false;
    }

    private void flushBatch() {
        if (batchSpriteCount == 0) return;

        // Upload vertex data
        batchVertexBuffer.clear();
        batchVertexBuffer.put(batchVertices, 0, batchSpriteCount * VERTICES_PER_SPRITE * FLOATS_PER_VERTEX);
        batchVertexBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, batchVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, batchVertexBuffer);

        // Self-contained: bind batch shader and VAO
        glUseProgram(batchShaderProgram);
        glBindVertexArray(batchVao);

        glUniformMatrix4fv(batchUProjection, false, projectionMatrix.get(new float[16]));
        glUniform1i(batchUIsText, batchIsText ? 1 : 0);

        // Bind texture (font atlas already bound by UIText for text mode)
        if (currentBatchTexture != null) {
            currentBatchTexture.bind(0);
        } else if (!batchIsText) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, whiteTexture);
        }
        // For text mode, texture is already bound by UIText.font.bind()

        // Draw
        glDrawElements(GL_TRIANGLES, batchSpriteCount * INDICES_PER_SPRITE, GL_UNSIGNED_INT, 0);

        // Clean up
        glBindVertexArray(0);
        glUseProgram(0);

        // Reset for next batch
        batchSpriteCount = 0;
    }

    @Override
    public int getMaxBatchSize() {
        return MAX_BATCH_SPRITES;
    }

    // =========================================
    // Internal Methods
    // =========================================

    private void buildQuadVertices(float x, float y, float w, float h,
                                   float u0, float v0, float u1, float v1) {
        // Top-left origin: build quad from (x,y) going right and down
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

    private void createBuffers() {
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 16 * Float.BYTES, GL_DYNAMIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        int[] indices = { 0, 1, 2, 2, 3, 0 };
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glBindVertexArray(0);
    }

    private void createBatchResources() {
        // Create VAO for batching
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

        // Create batch shader
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
        // Batch shader supports both regular RGBA textures and single-channel font atlases
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
            uniform int uIsText;  // 1 = font atlas (use red channel as alpha)
            
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
            throw new RuntimeException("UI " + type + " shader compilation failed:\n" + log);
        }
    }

    private void checkProgramError(int program) {
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            throw new RuntimeException("UI shader program linking failed:\n" + log);
        }
    }

    @Override
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
        System.out.println("OpenGLUIRenderer destroyed");
    }
}