package com.pocket.rpg.rendering;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.utils.DirtyReference;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * A 2D sprite renderer that renders SpriteRenderer components.
 * Handles transformations, textures, and camera operations.
 * Uses orthographic projection for 2D rendering.
 */
public class Renderer {

    private int viewportWidth = 800;
    private int viewportHeight = 600;

    // Camera viewport bounds (world space)
    private float cameraLeft = 0;
    private float cameraRight = 800;
    private float cameraTop = 0;
    private float cameraBottom = 600;

    // Debug statistics
    private int totalSprites = 0;
    private int culledSprites = 0;
    private int renderedSprites = 0;

    private Shader shader;
    private int quadVAO;
    private int quadVBO;


    private DirtyReference<Matrix4f> projectionMatrixRef;
    private DirtyReference<Matrix4f> viewMatrixRef;
    private Matrix4f modelMatrix;

    // For dynamic UV updates
    private FloatBuffer vertexBuffer;

    // Default clear color when no camera is provided
    private static final Vector4f DEFAULT_CLEAR_COLOR = new Vector4f(0.1f, 0.1f, 0.15f, 1.0f);

    /**
     * Initializes the renderer with the specified viewport dimensions.
     *
     * @param viewportWidth  Viewport width
     * @param viewportHeight Viewport height
     */
    public void init(int viewportWidth, int viewportHeight) {
        // Create shader program
        shader = new Shader("assets/shaders/sprite.glsl");
        shader.compileAndLink();

        // Allocate vertex buffer for UV updates
        vertexBuffer = MemoryUtil.memAllocFloat(24); // 6 vertices * 4 floats (pos + uv)

        // Create quad mesh
        createQuadMesh();

        // Initialize matrices
        projectionMatrixRef = new DirtyReference<>(new Matrix4f(),
                matrix4f -> shader.uploadMat4f("projection", matrix4f));
        viewMatrixRef = new DirtyReference<>(new Matrix4f(),
                matrix4f -> shader.uploadMat4f("view", matrix4f));
        modelMatrix = new Matrix4f();

        // Set up orthographic projection (origin at top-left, Y-down)
        setProjection(viewportWidth, viewportHeight);

        // Enable blending for transparent sprites
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Updates camera bounds when view matrix changes.
     * Call this in beginWithCamera() after setting view matrix.
     */
    private void updateCameraBounds(Camera camera) {
        if (camera == null) {
            // No camera - use viewport size
            cameraLeft = 0;
            cameraRight = viewportWidth;
            cameraTop = 0;
            cameraBottom = viewportHeight;
        } else {
            // Get camera position
            Transform camTransform = camera.getGameObject().getTransform();
            Vector3f camPos = camTransform.getPosition();

            // Calculate world-space bounds
            // For orthographic camera with screen-space coordinates
            cameraLeft = camPos.x;
            cameraRight = camPos.x + viewportWidth;
            cameraTop = camPos.y;
            cameraBottom = camPos.y + viewportHeight;
        }
    }

    /**
     * Tests if a sprite is visible in the camera frustum.
     * Uses simple AABB (bounding box) intersection test.
     */
    private boolean isVisible(SpriteRenderer spriteRenderer) {
        if (spriteRenderer == null || spriteRenderer.getSprite() == null) {
            return false;
        }

        Sprite sprite = spriteRenderer.getSprite();
        Transform transform = spriteRenderer.getGameObject().getTransform();
        Vector3f pos = transform.getPosition();
        Vector3f scale = transform.getScale();

        // Calculate sprite bounds in world space
        float spriteWidth = sprite.getWidth() * scale.x;
        float spriteHeight = sprite.getHeight() * scale.y;

        // Account for origin (rotation/scale pivot point)
        float originOffsetX = spriteWidth * spriteRenderer.getOriginX();
        float originOffsetY = spriteHeight * spriteRenderer.getOriginY();

        // Calculate AABB (axis-aligned bounding box)
        // Note: This doesn't account for rotation - conservative culling
        float spriteLeft = pos.x - originOffsetX;
        float spriteRight = pos.x + (spriteWidth - originOffsetX);
        float spriteTop = pos.y - originOffsetY;
        float spriteBottom = pos.y + (spriteHeight - originOffsetY);

        // Add padding for rotation (conservative approach)
        float diagonal = (float) Math.sqrt(spriteWidth * spriteWidth + spriteHeight * spriteHeight);
        float padding = (diagonal - Math.max(spriteWidth, spriteHeight)) / 2;

        spriteLeft -= padding;
        spriteRight += padding;
        spriteTop -= padding;
        spriteBottom += padding;

        // AABB intersection test
        boolean intersects = !(spriteRight < cameraLeft ||
                spriteLeft > cameraRight ||
                spriteBottom < cameraTop ||
                spriteTop > cameraBottom);

        return intersects;
    }

    /**
     * Optimized version without rotation padding (faster, less accurate).
     */
    private boolean isVisibleSimple(SpriteRenderer spriteRenderer) {
        if (spriteRenderer == null || spriteRenderer.getSprite() == null) {
            return false;
        }

        Sprite sprite = spriteRenderer.getSprite();
        Transform transform = spriteRenderer.getGameObject().getTransform();
        Vector3f pos = transform.getPosition();
        Vector3f scale = transform.getScale();

        float spriteWidth = sprite.getWidth() * scale.x;
        float spriteHeight = sprite.getHeight() * scale.y;

        float originOffsetX = spriteWidth * spriteRenderer.getOriginX();
        float originOffsetY = spriteHeight * spriteRenderer.getOriginY();

        float spriteLeft = pos.x - originOffsetX;
        float spriteRight = pos.x + (spriteWidth - originOffsetX);
        float spriteTop = pos.y - originOffsetY;
        float spriteBottom = pos.y + (spriteHeight - originOffsetY);

        // Simple AABB test
        return !(spriteRight < cameraLeft ||
                spriteLeft > cameraRight ||
                spriteBottom < cameraTop ||
                spriteTop > cameraBottom);
    }

    /**
     * Projection matrix only changes when window is resized
     * Sets up the orthographic projection matrix.
     *
     * @param width  Viewport width
     * @param height Viewport height
     */
    public void setProjection(int width, int height) {
        // Orthographic projection: (0, 0) at top-left, (width, height) at bottom-right
        projectionMatrixRef.set(projectionMatrixRef.getValue().identity().ortho(0, width, height, 0, -1, 1));
    }

    /**
     * Begins a rendering batch with camera support.
     * Handles OpenGL clear operations and applies camera matrices.
     *
     * @param camera The active camera (can be null for default behavior)
     */
    public void beginWithCamera(Camera camera) {
        // Apply camera clear color (or default)
        Vector4f clearColor = camera != null ? camera.getClearColor() : DEFAULT_CLEAR_COLOR;
        glClearColor(clearColor.x, clearColor.y, clearColor.z, clearColor.w);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Update camera bounds for culling
        updateCameraBounds(camera);

        // Apply camera view matrix
        if (camera != null) {
            setViewMatrix(camera.getViewMatrix());
        } else {
            resetView();
        }

        // Continue with normal begin
        begin();
    }

    /**
     * Begins a rendering batch.
     * Call this before rendering sprites.
     */
    public void begin() {
        // Reset stats each frame
        totalSprites = 0;
        culledSprites = 0;
        renderedSprites = 0;
        shader.use();

        // Upload matrices
        projectionMatrixRef.applyIfDirty();
        viewMatrixRef.applyIfDirty();

        // Set texture sampler
        shader.uploadInt("textureSampler", 0);
    }

    /**
     * Renders a SpriteRenderer component.
     * Reads Transform from the GameObject and Sprite from the component.
     *
     * @param spriteRenderer The SpriteRenderer component to render
     */
    public void drawSpriteRenderer(SpriteRenderer spriteRenderer) {
        if (spriteRenderer == null || spriteRenderer.getSprite() == null) {
            return;
        }

        totalSprites++;

        // Early exit if not visible
        if (!isVisible(spriteRenderer)) {
            culledSprites++;
            return; // Skip rendering entirely!
        }

        renderedSprites++;


        Sprite sprite = spriteRenderer.getSprite();
        Transform transform = spriteRenderer.getGameObject().getTransform();

        if (transform == null) {
            return;
        }

        // Update quad UVs for this sprite
        updateQuadUVs(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());

        // Bind texture
        sprite.getTexture().bind(0);

        // Build model matrix from Transform and Sprite
        buildModelMatrix(sprite, transform, spriteRenderer);

        // Upload model matrix
        shader.uploadMat4f("model", modelMatrix);

        // Draw quad
        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        // Unbind texture
        Texture.unbind(0);
    }

    /**
     * Builds the model matrix from Transform, Sprite, and SpriteRenderer.
     * Applies: Translation -> Rotation (around origin) -> Scale
     *
     * @param sprite         The sprite (for size)
     * @param transform      The transform (for position, rotation, scale)
     * @param spriteRenderer The sprite renderer (for origin/pivot)
     */
    private void buildModelMatrix(Sprite sprite, Transform transform, SpriteRenderer spriteRenderer) {
        Vector3f pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        Vector3f scale = transform.getScale();

        // Calculate final size (sprite base size * transform scale)
        float finalWidth = sprite.getWidth() * scale.x;
        float finalHeight = sprite.getHeight() * scale.y;

        // Calculate origin offset in pixels
        float originX = finalWidth * spriteRenderer.getOriginX();
        float originY = finalHeight * spriteRenderer.getOriginY();

        // Build model matrix (TRS: Translate, Rotate, Scale)
        modelMatrix.identity();

        // 1. Translate to position
        modelMatrix.translate(pos.x, pos.y, pos.z);

        // 2. Rotate around origin (if rotation is not zero)
        if (rot.z != 0) {
            modelMatrix.translate(originX, originY, 0);
            modelMatrix.rotateZ((float) Math.toRadians(rot.z));
            modelMatrix.translate(-originX, -originY, 0);
        }

        // 3. Scale to final size
        modelMatrix.scale(finalWidth, finalHeight, 1);
    }

    /**
     * Ends the rendering batch.
     * Call this after rendering all sprites.
     */
    public void end() {
        printStats();
        shader.detach();
    }

    private void printStats() {
//        System.out.printf("Sprites: %d total, %d rendered, %d culled (%.1f%% culled)%n",
//                totalSprites, renderedSprites, culledSprites,
//                (culledSprites / (float) totalSprites) * 100);
    }

    /**
     * Creates a unit quad mesh (0,0 to 1,1) with texture coordinates.
     * Uses GL_DYNAMIC_DRAW since UVs are updated frequently.
     */
    private void createQuadMesh() {
        quadVAO = glGenVertexArrays();
        quadVBO = glGenBuffers();

        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);

        // IMPORTANT: Use GL_DYNAMIC_DRAW for frequently updated data
        glBufferData(GL_ARRAY_BUFFER, 24 * Float.BYTES, GL_DYNAMIC_DRAW);

        // Position attribute (location 0)
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);

        // Texture coordinate attribute (location 1)
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

        glBindVertexArray(0);

        // Initialize with default UVs (0,0 to 1,1)
        updateQuadUVs(0, 0, 1, 1);
    }

    /**
     * Updates the quad's UV coordinates for sprite sheet support.
     *
     * @param u0 Left U coordinate
     * @param v0 Top V coordinate
     * @param u1 Right U coordinate
     * @param v1 Bottom V coordinate
     */
    private void updateQuadUVs(float u0, float v0, float u1, float v1) {
        // Clear and refill buffer
        vertexBuffer.clear();

        // Triangle 1
        vertexBuffer.put(0.0f).put(0.0f).put(u0).put(v1);  // Top-left (flipped V)
        vertexBuffer.put(0.0f).put(1.0f).put(u0).put(v0);  // Bottom-left (flipped V)
        vertexBuffer.put(1.0f).put(1.0f).put(u1).put(v0);  // Bottom-right (flipped V)

        // Triangle 2
        vertexBuffer.put(0.0f).put(0.0f).put(u0).put(v1);  // Top-left (flipped V)
        vertexBuffer.put(1.0f).put(1.0f).put(u1).put(v0);  // Bottom-right (flipped V)
        vertexBuffer.put(1.0f).put(0.0f).put(u1).put(v1);  // Top-right (flipped V)

        vertexBuffer.flip();

        // Upload to GPU
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * View matrix only changes when camera moves
     * Sets the view matrix for camera transforms.
     *
     * @param viewMatrix The view matrix
     */
    public void setViewMatrix(Matrix4f viewMatrix) {
        viewMatrixRef.set(viewMatrix);
    }

    /**
     * Resets the view matrix to identity.
     */
    public void resetView() {
        viewMatrixRef.set(viewMatrixRef.getValue().identity());
    }

    /**
     * Cleans up OpenGL resources.
     */
    public void destroy() {
        if (shader != null) {
            shader.delete();
        }
        if (quadVAO != 0) {
            glDeleteVertexArrays(quadVAO);
        }
        if (quadVBO != 0) {
            glDeleteBuffers(quadVBO);
        }
        if (vertexBuffer != null) {
            MemoryUtil.memFree(vertexBuffer);
        }
    }
}