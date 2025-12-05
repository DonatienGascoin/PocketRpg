package com.pocket.rpg.glfw;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.ui.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * OpenGL implementation of UIRenderer.
 * Renders UI in game resolution with bottom-left origin.
 * Scales with pillarbox/letterbox to match game viewport.
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

    // Vertex data: position (2) + uv (2) = 4 floats per vertex, 4 vertices
    private final float[] vertices = new float[16];
    private final FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(16);

    private boolean initialized = false;

    @Override
    public void init(GameConfig config) {
        if (initialized) return;

        this.gameWidth = config.getGameWidth();
        this.gameHeight = config.getGameHeight();

        createShader();
        createBuffers();
        createWhiteTexture();

        // Initial projection with game resolution, bottom-left origin
        updateProjection();

        initialized = true;
        System.out.println("OpenGLUIRenderer initialized (game: " + gameWidth + "x" + gameHeight + ")");
    }

    @Override
    public void setViewportSize(int width, int height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
        // Projection doesn't change - we always use game resolution
        // The viewport/scissor will be set by the main renderer for pillarbox
    }

    private void updateProjection() {
        // Bottom-left origin: (0,0) at bottom-left, (gameWidth, gameHeight) at top-right
        projectionMatrix.identity().ortho(0, gameWidth, 0, gameHeight, -1, 1);
    }

    @Override
    public void render(List<UICanvas> canvases) {
        if (!initialized || canvases.isEmpty()) return;

        // Setup render state
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);

        glUseProgram(shaderProgram);
        glUniformMatrix4fv(uProjection, false, projectionMatrix.get(new float[16]));

        glBindVertexArray(vao);

        // Canvases are pre-sorted by Scene
        for (UICanvas canvas : canvases) {
            if (!canvas.isEnabled()) continue;
            if (canvas.getRenderMode() != UICanvas.RenderMode.SCREEN_SPACE_OVERLAY) continue;

            renderCanvasSubtree(canvas.getGameObject(), gameWidth, gameHeight);
        }

        glBindVertexArray(0);
        glUseProgram(0);
        glEnable(GL_DEPTH_TEST);
    }

    private void renderCanvasSubtree(GameObject root, float parentWidth, float parentHeight) {
        renderGameObjectUI(root, parentWidth, parentHeight);

        // Get this object's bounds for children
        float childParentWidth = parentWidth;
        float childParentHeight = parentHeight;

        UITransform transform = root.getComponent(UITransform.class);
        if (transform != null) {
            childParentWidth = transform.getWidth();
            childParentHeight = transform.getHeight();
        }

        for (GameObject child : root.getChildren()) {
            renderCanvasSubtree(child, childParentWidth, childParentHeight);
        }
    }

    private void renderGameObjectUI(GameObject go, float parentWidth, float parentHeight) {
        if (!go.isEnabled()) return;

        // Update UITransform with parent bounds
        UITransform transform = go.getComponent(UITransform.class);
        if (transform != null) {
            transform.setParentBounds(parentWidth, parentHeight);
        }

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
    // UIRendererBackend Implementation
    // =========================================

    @Override
    public void drawQuad(float x, float y, float width, float height, Vector4f color) {
        modelMatrix.identity().translate(x, y, 0);
        glUniformMatrix4fv(uModel, false, modelMatrix.get(new float[16]));

        glUniform4f(uColor, color.x, color.y, color.z, color.w);
        glUniform1i(uUseTexture, 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, whiteTexture);

        // Build quad from bottom-left corner
        buildQuadVertices(0, 0, width, height, 0, 0, 1, 1);
        uploadVertices();

        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    @Override
    public void drawSprite(float x, float y, float width, float height, Sprite sprite, Vector4f tint) {
        modelMatrix.identity().translate(x, y, 0);
        glUniformMatrix4fv(uModel, false, modelMatrix.get(new float[16]));

        glUniform4f(uColor, tint.x, tint.y, tint.z, tint.w);

        if (sprite != null && sprite.getTexture() != null) {
            glUniform1i(uUseTexture, 1);
            sprite.getTexture().bind(0);
            // Flip V coordinates for bottom-left origin
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
    }

    // =========================================
    // Internal Methods
    // =========================================

    private void buildQuadVertices(float x, float y, float w, float h,
                                   float u0, float v0, float u1, float v1) {
        // Bottom-left origin: build quad from (x,y) going right and up
        // Bottom-left
        vertices[0] = x;      vertices[1] = y;      vertices[2] = u0; vertices[3] = v0;
        // Bottom-right
        vertices[4] = x + w;  vertices[5] = y;      vertices[6] = u1; vertices[7] = v0;
        // Top-right
        vertices[8] = x + w;  vertices[9] = y + h;  vertices[10] = u1; vertices[11] = v1;
        // Top-left
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
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
        glDeleteTextures(whiteTexture);

        initialized = false;
        System.out.println("OpenGLUIRenderer destroyed");
    }
}