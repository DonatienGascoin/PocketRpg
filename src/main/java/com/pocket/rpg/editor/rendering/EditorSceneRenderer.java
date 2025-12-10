package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.rendering.Renderable;
import com.pocket.rpg.rendering.renderers.BatchRenderer;
import com.pocket.rpg.scenes.Scene;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Renders scene content to the editor framebuffer.
 * 
 * Handles:
 * - Rendering a live Scene to the framebuffer
 * - Sorting renderables by zIndex
 * - Grid overlay rendering (delegated to SceneViewport)
 * 
 * Uses the existing BatchRenderer for actual sprite/tilemap rendering.
 * 
 * Note: The editor maintains a live Scene instance for rendering purposes.
 * Edits are made to SceneData (source of truth) and synced to the live Scene.
 */
public class EditorSceneRenderer {
    
    private final EditorFramebuffer framebuffer;
    private final RenderingConfig renderingConfig;
    
    private BatchRenderer batchRenderer;
    private boolean initialized = false;

    /**
     * Creates an EditorSceneRenderer.
     * 
     * @param framebuffer Framebuffer to render to
     * @param renderingConfig Rendering configuration
     */
    public EditorSceneRenderer(EditorFramebuffer framebuffer, RenderingConfig renderingConfig) {
        this.framebuffer = framebuffer;
        this.renderingConfig = renderingConfig;
    }

    /**
     * Initializes rendering resources.
     */
    public void init() {
        if (initialized) return;
        
        // Initialize batch renderer with config
        batchRenderer = new BatchRenderer(renderingConfig);
        batchRenderer.init(framebuffer.getWidth(), framebuffer.getHeight());
        
        initialized = true;
        System.out.println("EditorSceneRenderer initialized");
    }

    /**
     * Renders the live scene to the framebuffer.
     * 
     * @param scene Live Scene instance to render (can be null for empty view)
     * @param camera Editor camera for view/projection matrices
     */
    public void render(Scene scene, EditorCamera camera) {
        if (!initialized) {
            init();
        }
        
        // Bind framebuffer
        framebuffer.bind();
        
        // Clear with background color
        Vector4f clearColor = renderingConfig != null ? 
            renderingConfig.getClearColor() : new Vector4f(0.1f, 0.1f, 0.1f, 1.0f);
        framebuffer.clear(clearColor.x, clearColor.y, clearColor.z, clearColor.w);
        
        // Enable blending for sprites
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Get camera matrices
        Matrix4f projectionMatrix = camera.getProjectionMatrix();
        Matrix4f viewMatrix = camera.getViewMatrix();
        
        // Render scene content
        if (scene != null) {
            renderSceneContent(scene, projectionMatrix, viewMatrix, camera);
        }
        
        // Unbind framebuffer
        framebuffer.unbind();
    }

    /**
     * Renders scene GameObjects using the live Scene's renderable cache.
     */
    private void renderSceneContent(Scene scene, Matrix4f projection, Matrix4f view, EditorCamera camera) {
        // Get renderables from scene (already sorted by zIndex)
        List<Renderable> renderables = scene.getRenderers();
        
        // Begin batch with camera matrices
        batchRenderer.beginWithMatrices(projection, view, null);
        
        // Render each renderable
        for (Renderable renderable : renderables) {
            if (!renderable.isRenderVisible()) continue;
            
            if (renderable instanceof SpriteRenderer spriteRenderer) {
                batchRenderer.drawSpriteRenderer(spriteRenderer);
            } else if (renderable instanceof TilemapRenderer tilemapRenderer) {
                renderTilemap(tilemapRenderer, camera);
            }
        }
        
        // End batch
        batchRenderer.end();
    }

    /**
     * Renders a tilemap with frustum culling.
     */
    private void renderTilemap(TilemapRenderer tilemap, EditorCamera camera) {
        // Get visible bounds
        float[] bounds = camera.getWorldBounds();
        float left = bounds[0];
        float bottom = bounds[1];
        float right = bounds[2];
        float top = bounds[3];
        
        // Calculate visible chunks
        float tileSize = tilemap.getTileSize();
        int chunkSize = TilemapRenderer.TileChunk.CHUNK_SIZE;
        float chunkWorldSize = chunkSize * tileSize;
        
        int startCX = (int) Math.floor(left / chunkWorldSize);
        int startCY = (int) Math.floor(bottom / chunkWorldSize);
        int endCX = (int) Math.ceil(right / chunkWorldSize);
        int endCY = (int) Math.ceil(top / chunkWorldSize);
        
        // Collect visible chunks
        List<long[]> visibleChunks = new ArrayList<>();
        for (int cy = startCY; cy <= endCY; cy++) {
            for (int cx = startCX; cx <= endCX; cx++) {
                if (tilemap.hasChunk(cx, cy)) {
                    visibleChunks.add(new long[]{cx, cy});
                }
            }
        }
        
        // Render visible chunks
        if (!visibleChunks.isEmpty()) {
            batchRenderer.drawTilemap(tilemap, visibleChunks);
        }
    }

    /**
     * Called when framebuffer is resized.
     */
    public void onResize(int width, int height) {
        if (batchRenderer != null && width > 0 && height > 0) {
            batchRenderer.setProjection(width, height);
        }
    }

    /**
     * Destroys rendering resources.
     */
    public void destroy() {
        if (batchRenderer != null) {
            batchRenderer.destroy();
            batchRenderer = null;
        }
        initialized = false;
    }
}
