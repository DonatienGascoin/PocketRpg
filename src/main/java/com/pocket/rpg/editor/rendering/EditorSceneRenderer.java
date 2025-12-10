package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.rendering.renderers.BatchRenderer;
import com.pocket.rpg.rendering.Renderable;
import com.pocket.rpg.serialization.GameObjectData;
import com.pocket.rpg.serialization.SceneData;
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
 * - Rendering SceneData (editor's data model) to the framebuffer
 * - Sorting renderables by zIndex
 * - Grid overlay rendering
 * - Selection highlight rendering (future)
 * 
 * Uses the existing BatchRenderer for actual sprite/tilemap rendering.
 */
public class EditorSceneRenderer {
    
    private final EditorFramebuffer framebuffer;
    private final RenderingConfig renderingConfig;
    
    private BatchRenderer batchRenderer;
    private boolean initialized = false;
    
    // Grid settings
    private boolean showGrid = true;
    private float gridSize = 1.0f; // World units per grid cell
    private Vector4f gridColor = new Vector4f(0.3f, 0.3f, 0.3f, 0.5f);
    private Vector4f gridMajorColor = new Vector4f(0.4f, 0.4f, 0.4f, 0.7f);
    private int gridMajorInterval = 10; // Major line every N cells

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
        
        // Initialize batch renderer
        batchRenderer = new BatchRenderer();
        batchRenderer.init();
        
        initialized = true;
        System.out.println("EditorSceneRenderer initialized");
    }

    /**
     * Renders the scene to the framebuffer.
     * 
     * @param sceneData Scene data to render
     * @param camera Editor camera for view/projection matrices
     */
    public void render(SceneData sceneData, EditorCamera camera) {
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
        
        // Render grid (behind everything)
        if (showGrid) {
            renderGrid(camera);
        }
        
        // Render scene content
        if (sceneData != null) {
            renderSceneContent(sceneData, projectionMatrix, viewMatrix);
        }
        
        // Unbind framebuffer
        framebuffer.unbind();
    }

    /**
     * Renders scene GameObjects.
     */
    private void renderSceneContent(SceneData sceneData, Matrix4f projection, Matrix4f view) {
        // Collect all renderables from the scene hierarchy
        List<RenderableEntry> renderables = new ArrayList<>();
        
        for (GameObjectData goData : sceneData.getGameObjects()) {
            collectRenderables(goData, renderables);
        }
        
        // Sort by zIndex
        renderables.sort(Comparator.comparingInt(r -> r.zIndex));
        
        // Begin batch
        batchRenderer.begin(projection, view);
        
        // Render each item
        for (RenderableEntry entry : renderables) {
            renderEntry(entry);
        }
        
        // End batch
        batchRenderer.end();
    }

    /**
     * Recursively collects renderables from GameObjectData hierarchy.
     */
    private void collectRenderables(GameObjectData goData, List<RenderableEntry> renderables) {
        // Check for SpriteRenderer
        SpriteRenderer spriteRenderer = goData.getComponent(SpriteRenderer.class);
        if (spriteRenderer != null && spriteRenderer.getSprite() != null) {
            renderables.add(new RenderableEntry(
                RenderableType.SPRITE,
                spriteRenderer,
                goData,
                spriteRenderer.getZIndex()
            ));
        }
        
        // Check for TilemapRenderer
        TilemapRenderer tilemapRenderer = goData.getComponent(TilemapRenderer.class);
        if (tilemapRenderer != null) {
            renderables.add(new RenderableEntry(
                RenderableType.TILEMAP,
                tilemapRenderer,
                goData,
                tilemapRenderer.getZIndex()
            ));
        }
        
        // Recurse into children
        for (GameObjectData child : goData.getChildren()) {
            collectRenderables(child, renderables);
        }
    }

    /**
     * Renders a single renderable entry.
     */
    private void renderEntry(RenderableEntry entry) {
        switch (entry.type) {
            case SPRITE:
                renderSprite((SpriteRenderer) entry.renderable, entry.gameObject);
                break;
            case TILEMAP:
                renderTilemap((TilemapRenderer) entry.renderable, entry.gameObject);
                break;
        }
    }

    /**
     * Renders a sprite.
     */
    private void renderSprite(SpriteRenderer spriteRenderer, GameObjectData goData) {
        // Get transform from components
        com.pocket.rpg.components.Transform transform = goData.getComponent(com.pocket.rpg.components.Transform.class);
        if (transform == null) return;
        
        // Submit to batch
        batchRenderer.drawSprite(
            spriteRenderer.getSprite(),
            transform.getPosition().x,
            transform.getPosition().y,
            transform.getScale().x,
            transform.getScale().y,
            transform.getRotation().z,
            spriteRenderer.getOriginX(),
            spriteRenderer.getOriginY()
        );
    }

    /**
     * Renders a tilemap.
     */
    private void renderTilemap(TilemapRenderer tilemapRenderer, GameObjectData goData) {
        // TODO: Implement tilemap rendering
        // This requires iterating chunks, checking visibility, and submitting tiles
        // For now, tilemap rendering is deferred to Phase 3
    }

    /**
     * Renders the editor grid overlay.
     */
    private void renderGrid(EditorCamera camera) {
        // TODO: Implement grid rendering
        // This requires a simple line shader or primitive drawing
        // For now, grid is rendered in SceneViewport using ImGui draw lists
    }

    // ========================================================================
    // SETTINGS
    // ========================================================================

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setGridSize(float gridSize) {
        this.gridSize = gridSize;
    }

    public float getGridSize() {
        return gridSize;
    }

    public void setGridColor(Vector4f color) {
        this.gridColor = color;
    }

    public void setGridMajorColor(Vector4f color) {
        this.gridMajorColor = color;
    }

    public void setGridMajorInterval(int interval) {
        this.gridMajorInterval = interval;
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

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

    // ========================================================================
    // HELPER CLASSES
    // ========================================================================

    private enum RenderableType {
        SPRITE,
        TILEMAP
    }

    private static class RenderableEntry {
        final RenderableType type;
        final Object renderable;
        final GameObjectData gameObject;
        final int zIndex;

        RenderableEntry(RenderableType type, Object renderable, GameObjectData gameObject, int zIndex) {
            this.type = type;
            this.renderable = renderable;
            this.gameObject = gameObject;
            this.zIndex = zIndex;
        }
    }
}
