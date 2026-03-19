package com.pocket.rpg.editor.panels;

import com.pocket.rpg.components.ui.*;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.PrefabEditController;
import com.pocket.rpg.editor.camera.PreviewCamera;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.uidesigner.*;
import com.pocket.rpg.rendering.targets.Framebuffer;
import com.pocket.rpg.editor.rendering.PickingPass;
import com.pocket.rpg.editor.rendering.PreviewCameraAdapter;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.editor.tools.ToolManager;
import com.pocket.rpg.rendering.batch.SpriteBatch;
import com.pocket.rpg.rendering.core.Renderable;
import com.pocket.rpg.rendering.pipeline.RenderParams;
import com.pocket.rpg.rendering.pipeline.RenderPipeline;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.targets.FramebufferTarget;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UI Designer panel - visual editor for UI elements.
 * <p>
 * Refactored to delegate to focused sub-components:
 * <ul>
 *   <li>{@link UIDesignerState} - Shared state (camera, zoom, drag state)</li>
 *   <li>{@link UIDesignerCoordinates} - Coordinate conversion, bounds calculation</li>
 *   <li>{@link UIDesignerGizmoDrawer} - Selection handles, anchor/pivot visualization</li>
 *   <li>{@link UIDesignerInputHandler} - Mouse/keyboard input handling</li>
 *   <li>{@link UIDesignerRenderer} - UI element rendering</li>
 * </ul>
 */
public class UIDesignerPanel {

    private final EditorContext context;
    private final GameConfig gameConfig;
    private final RenderingConfig renderingConfig;

    // Sub-components
    private final UIDesignerState state;
    private final UIDesignerCoordinates coords;
    private final UIDesignerGizmoDrawer gizmoDrawer;
    private final UIDesignerInputHandler inputHandler;
    private final UIDesignerRenderer renderer;

    // Unified rendering via RenderPipeline
    private RenderPipeline pipeline;
    private Framebuffer framebuffer;
    private ViewportConfig viewportConfig;
    private boolean pipelineInitialized = false;

    // Scene background rendering (for WORLD mode)
    private Framebuffer sceneFramebuffer;
    private PreviewCamera previewCamera;
    private PreviewCameraAdapter cameraAdapter;

    // GPU picking for pixel-accurate UI selection
    private PickingPass pickingPass;

    // Clear color for UI rendering (transparent)
    private static final Vector4f CLEAR_COLOR = new Vector4f(0f, 0f, 0f, 0f);

    @Setter
    private ToolManager toolManager;

    @Setter
    private PrefabEditController prefabEditController;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public UIDesignerPanel(EditorContext context) {
        this.context = context;
        this.gameConfig = context.getGameConfig();
        this.renderingConfig = context.getRenderingConfig();

        // Create sub-components
        this.state = new UIDesignerState(gameConfig);
        this.state.setShowElementBounds(context.getConfig().isShowElementBounds());
        this.coords = new UIDesignerCoordinates(state);
        this.gizmoDrawer = new UIDesignerGizmoDrawer(state, coords);
        this.inputHandler = new UIDesignerInputHandler(state, coords, context);
        this.inputHandler.setGpuPicker(this::pickEntityAtCanvasPosition);
        this.renderer = new UIDesignerRenderer(state, coords, renderingConfig);
    }

    /**
     * Initializes the RenderPipeline for UI rendering.
     * Called lazily on first render.
     */
    private void initPipeline() {
        if (pipelineInitialized) return;

        int width = state.getCanvasWidth();
        int height = state.getCanvasHeight();

        // Create framebuffer for UI rendering
        framebuffer = new Framebuffer(width, height);
        framebuffer.init();

        // Create framebuffer for scene background (same game-native dimensions)
        sceneFramebuffer = new Framebuffer(width, height);
        sceneFramebuffer.init();

        // Create viewport config matching canvas dimensions
        viewportConfig = new ViewportConfig(width, height, width, height);

        // Create preview camera for game-perspective scene rendering
        previewCamera = new PreviewCamera(viewportConfig);
        cameraAdapter = new PreviewCameraAdapter(previewCamera);

        // Create pipeline (no post-processing for UI)
        pipeline = new RenderPipeline(viewportConfig, renderingConfig);
        pipeline.init();

        // Initialize GPU picking
        pickingPass = new PickingPass(renderingConfig);
        pickingPass.init(width, height);

        pipelineInitialized = true;
        System.out.println("[UIDesignerPanel] Pipeline initialized (" + width + "x" + height + ")");
    }

    // ========================================================================
    // MAIN RENDER
    // ========================================================================

    public void render() {
        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        boolean visible = ImGui.begin("UI Designer", flags);

        if (!visible) {
            ImGui.end();
            return;
        }

        state.setFocused(ImGui.isWindowFocused());
        renderToolbar();
        ImGui.separator();
        renderViewport();
        ImGui.end();
    }

    // ========================================================================
    // TOOLBAR
    // ========================================================================

    private void renderToolbar() {
        ImGui.text("Background:");
        ImGui.sameLine();

        if (ImGui.radioButton("Gray", state.getBackgroundMode() == UIDesignerState.BackgroundMode.GRAY)) {
            state.setBackgroundMode(UIDesignerState.BackgroundMode.GRAY);
        }
        ImGui.sameLine();
        boolean inPrefabEditMode = prefabEditController != null && prefabEditController.isActive();
        if (inPrefabEditMode) {
            ImGui.beginDisabled();
        }
        if (ImGui.radioButton("World", state.getBackgroundMode() == UIDesignerState.BackgroundMode.WORLD)) {
            state.setBackgroundMode(UIDesignerState.BackgroundMode.WORLD);
        }
        if (inPrefabEditMode) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        // Snap button with icon and green highlight when active
        boolean snapEnabled = state.isSnapEnabled();
        if (snapEnabled) {
            EditorColors.pushSuccessButton();
        }
        if (ImGui.button(MaterialIcons.Anchor + " Snap")) {
            state.setSnapEnabled(!snapEnabled);
        }
        if (snapEnabled) {
            EditorColors.popButtonColors();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Toggle snap to edges");
        }

        ImGui.sameLine();

        // Anchor Lines button with icon and green highlight when active
        boolean showAnchorLines = state.isShowAnchorLines();
        if (showAnchorLines) {
            EditorColors.pushSuccessButton();
        }
        if (ImGui.button(MaterialIcons.AccountTree + " Anchors")) {
            state.setShowAnchorLines(!showAnchorLines);
        }
        if (showAnchorLines) {
            EditorColors.popButtonColors();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Toggle anchor lines visibility");
        }

        ImGui.sameLine();

        // Element Bounds button with icon and green highlight when active
        boolean showBounds = state.isShowElementBounds();
        if (showBounds) {
            EditorColors.pushSuccessButton();
        }
        if (ImGui.button(MaterialIcons.BorderAll + " Bounds")) {
            state.setShowElementBounds(!showBounds);
            context.getConfig().setShowElementBounds(!showBounds);
            context.getConfig().save();
        }
        if (showBounds) {
            EditorColors.popButtonColors();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Toggle bounds visibility");
        }

        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        if (ImGui.button(MaterialIcons.CenterFocusWeak + " Reset")) {
            state.resetCamera();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Reset view to fit canvas");
        }

        ImGui.sameLine();
        ImGui.text(String.format("Zoom: %.0f%%", state.getZoom() * 100));
    }

    private EditorScene getEffectiveScene() {
        if (prefabEditController != null && prefabEditController.isActive()) {
            return prefabEditController.getWorkingScene();
        }
        return context.getCurrentScene();
    }

    // ========================================================================
    // VIEWPORT
    // ========================================================================

    private void renderViewport() {
        // Initialize pipeline on first render
        if (!pipelineInitialized) {
            initPipeline();
        }

        // Get viewport bounds - start from current cursor position (after toolbar)
        ImVec2 cursorPos = ImGui.getCursorScreenPos();
        ImVec2 contentMax = ImGui.getWindowContentRegionMax();
        ImVec2 windowPos = ImGui.getWindowPos();
        ImVec2 contentMin = ImGui.getWindowContentRegionMin();

        float viewportX = cursorPos.x;
        float viewportY = cursorPos.y;
        float viewportWidth = contentMax.x - contentMin.x;
        float viewportHeight = (windowPos.y + contentMax.y) - cursorPos.y;

        // Update state
        state.setViewportX(viewportX);
        state.setViewportY(viewportY);
        state.setViewportWidth(viewportWidth);
        state.setViewportHeight(viewportHeight);

        // Check if hovered
        ImVec2 mousePos = ImGui.getMousePos();
        boolean isHovered = mousePos.x >= viewportX && mousePos.x <= viewportX + viewportWidth &&
                mousePos.y >= viewportY && mousePos.y <= viewportY + viewportHeight;
        state.setHovered(isHovered);

        // Get effective scene (prefab working scene when in prefab edit mode)
        EditorScene scene = getEffectiveScene();
        boolean inPrefabEdit = prefabEditController != null && prefabEditController.isActive();

        // Render scene background to texture (for WORLD mode, skip in prefab edit)
        if (state.getBackgroundMode() == UIDesignerState.BackgroundMode.WORLD && !inPrefabEdit) {
            renderSceneToTexture(scene);
            renderer.setSceneTextureId(sceneFramebuffer != null ? sceneFramebuffer.getTextureId() : 0);
        }

        // Render UI elements to texture using unified RenderPipeline
        renderUIToTexture(scene);

        // Render picking pass for pixel-accurate selection
        renderPickingPass(scene);

        // Get draw list
        ImDrawList drawList = ImGui.getWindowDrawList();

        // Clip all drawing to viewport area (prevents drawing over toolbar)
        drawList.pushClipRect(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight, true);

        // Draw gray background
        drawList.addRectFilled(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight,
                ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1.0f));

        // Draw canvas bounds
        gizmoDrawer.drawCanvasBounds(drawList);

        // Draw world background (if enabled)
        renderer.drawWorldBackground(drawList);

        // Display rendered UI texture
        displayUITexture(drawList);

        // Always draw selected item bounds; conditionally draw all others
        gizmoDrawer.drawSelectionBorders(drawList, scene, true);  // selected only
        if (state.isShowElementBounds()) {
            gizmoDrawer.drawSelectionBorders(drawList, scene, false);  // all (includes selected, no-op overlap)
            gizmoDrawer.drawTrackPadding(drawList, scene);
        }
        // Layout padding gizmos always shown for selected entities (independent of bounds toggle)
        gizmoDrawer.drawLayoutPadding(drawList, scene);

        // Draw selection gizmos (handles, anchor, pivot)
        gizmoDrawer.drawSelectionGizmos(drawList, scene);

        // Draw snap guides
        gizmoDrawer.drawSnapGuides(drawList);

        // Pop clip rect (matches pushClipRect at start of viewport rendering)
        drawList.popClipRect();

        // Handle input
        if (isHovered || state.isAnyDragActive()) {
            inputHandler.handleInput();
        }
    }

    /**
     * Renders UI elements to texture using unified RenderPipeline.
     * This uses the same rendering path as GameViewPanel and runtime.
     */
    private void renderUIToTexture(EditorScene scene) {
        if (pipeline == null || framebuffer == null) return;

        // Get UICanvases from scene entities
        List<UICanvas> uiCanvases = collectUICanvases(scene);

        // Create render target
        FramebufferTarget target = new FramebufferTarget(framebuffer);

        // Build params - UI only (no scene, no post-fx)
        RenderParams params = RenderParams.builder()
                .renderables(List.of())
                .camera(null)  // UI doesn't need camera
                .clearColor(CLEAR_COLOR)
                .renderScene(false)
                .renderUI(!uiCanvases.isEmpty())
                .uiCanvases(uiCanvases)
                .renderPostFx(false)
                .renderOverlay(false)
                .build();

        // Execute pipeline
        pipeline.execute(target, params);
    }

    /**
     * Renders the scene to a texture for the WORLD background.
     * Uses the game camera perspective at native resolution.
     */
    private void renderSceneToTexture(EditorScene scene) {
        if (pipeline == null || sceneFramebuffer == null || scene == null) return;

        // Apply scene camera settings (position from scene, ortho size from rendering config)
        SceneCameraSettings settings = scene.getCameraSettings();
        float orthoSize = renderingConfig.getDefaultOrthographicSize(gameConfig.getGameHeight());
        previewCamera.applySceneSettings(settings.getPosition(), orthoSize);

        // Get renderables (tilemaps + entities)
        List<Renderable> renderables = scene.getRenderables();

        // Create render target
        FramebufferTarget target = new FramebufferTarget(sceneFramebuffer);

        // Build params - scene only (no UI, no post-fx)
        RenderParams params = RenderParams.builder()
                .renderables(renderables)
                .camera(cameraAdapter)
                .clearColor(renderingConfig.getClearColor())
                .renderScene(true)
                .renderUI(false)
                .renderPostFx(false)
                .renderOverlay(false)
                .build();

        // Execute pipeline
        pipeline.execute(target, params);
    }

    // ========================================================================
    // GPU PICKING
    // ========================================================================

    /**
     * Renders entity IDs to the picking framebuffer for pixel-accurate UI selection.
     * Traverses the UI hierarchy and renders each visual element with its entity ID
     * encoded as color. The picking shader discards transparent pixels, so clicking
     * on a transparent area of a top image will select the visible element behind it.
     */
    private void renderPickingPass(EditorScene scene) {
        if (pickingPass == null || !pickingPass.isInitialized() || scene == null) return;

        // Use low threshold so semi-transparent UI elements (e.g. faded slots) are pickable
        pickingPass.setAlphaThreshold(0.01f);

        // Build UITransform -> EditorGameObject lookup (shared component instances)
        Map<UITransform, EditorGameObject> transformToEntity = new HashMap<>();
        for (EditorGameObject entity : scene.getEntities()) {
            UITransform t = entity.getComponent(UITransform.class);
            if (t != null) transformToEntity.put(t, entity);
        }

        // Use same ortho projection as UIRenderer: top-left origin
        int width = state.getCanvasWidth();
        int height = state.getCanvasHeight();
        Matrix4f projection = new Matrix4f().ortho(0, width, height, 0, -1, 1);
        Matrix4f view = new Matrix4f(); // Identity — screen-space UI

        pickingPass.execute(projection, view, (batch, entityIdMap) -> {
            // Traverse UI hierarchy in render order (same as UIRenderer)
            // Use incrementing z-index to preserve paint order (SpriteBatch sorts by z-index)
            float[] zCounter = {0f};
            List<UICanvas> canvases = collectUICanvases(scene);
            for (UICanvas canvas : canvases) {
                var root = canvas.getGameObject();
                if (root == null) continue;
                canvas.updateScreenSize(width, height);
                submitPickingHierarchy(root, batch, entityIdMap, transformToEntity, zCounter);
            }
        });
    }

    /**
     * Recursively submits UI elements to the picking batch in hierarchy order.
     */
    private void submitPickingHierarchy(GameObject go,
                                         SpriteBatch batch,
                                         Map<Integer, EditorGameObject> entityIdMap,
                                         Map<UITransform, EditorGameObject> transformToEntity,
                                         float[] zCounter) {
        if (!go.isEnabled()) return;

        UITransform transform = go.getComponent(UITransform.class);
        if (transform != null) {
            transform.setScreenBounds(state.getCanvasWidth(), state.getCanvasHeight());
            transform.markDirty();
        }

        // Find the EditorGameObject via shared UITransform instance
        EditorGameObject editorGo = transform != null ? transformToEntity.get(transform) : null;

        // Submit visual components for picking
        if (editorGo != null) {
            Sprite sprite = null;
            boolean isSolidQuad = false;

            UIImage image = go.getComponent(UIImage.class);
            UIButton button = go.getComponent(UIButton.class);
            UIPanel panel = go.getComponent(UIPanel.class);
            UIText text = go.getComponent(UIText.class);

            if (button != null && button.isEnabled()) {
                sprite = button.getSprite();
                if (sprite == null) isSolidQuad = true;
            } else if (image != null && image.isEnabled()) {
                sprite = image.getSprite();
            } else if (panel != null && panel.isEnabled()) {
                isSolidQuad = true;
            } else if (text != null && text.isEnabled()) {
                // Text renders glyphs — use solid quad for its bounding box
                isSolidQuad = true;
            }

            if (sprite != null || isSolidQuad) {
                Vector4f idColor = PickingPass.registerEntity(entityIdMap, editorGo);

                Vector2f pivotWorld = transform.getWorldPivotPosition2D();
                Vector2f scale = transform.getComputedWorldScale2D();
                float w = transform.getEffectiveWidth() * scale.x;
                float h = transform.getEffectiveHeight() * scale.y;
                float rotation = transform.getComputedWorldRotation2D();
                Vector2f pivot = transform.getEffectivePivot();

                // SpriteBatch expects (x,y) = pivot/origin position, not top-left
                // It computes corners as: left = x - originX*w, top = y - originY*h
                Sprite pickSprite = sprite != null ? sprite : pickingPass.getWhiteSprite();
                batch.submit(pickSprite, pivotWorld.x, pivotWorld.y, w, h,
                        rotation, pivot.x, pivot.y, zCounter[0]++, idColor);
            }
        }

        // Recurse into children
        for (var child : go.getChildren()) {
            submitPickingHierarchy(child, batch, entityIdMap, transformToEntity, zCounter);
        }
    }

    /**
     * Picks the entity at the given canvas coordinates using the GPU picking buffer.
     * Returns null if no entity at that position or picking is not available.
     */
    public EditorGameObject pickEntityAtCanvasPosition(float canvasX, float canvasY) {
        if (pickingPass == null || !pickingPass.isInitialized()) return null;

        int pixelX = Math.round(canvasX);
        // OpenGL readPixels uses bottom-left origin, so flip Y
        int pixelY = state.getCanvasHeight() - Math.round(canvasY);

        if (pixelX < 0 || pixelX >= pickingPass.getWidth() ||
            pixelY < 0 || pixelY >= pickingPass.getHeight()) {
            return null;
        }

        return pickingPass.readEntityAt(pixelX, pixelY);
    }

    /**
     * Displays the rendered UI texture in the viewport.
     */
    private void displayUITexture(ImDrawList drawList) {
        if (framebuffer == null || framebuffer.getTextureId() == 0) return;

        float[] canvasBounds = coords.getCanvasScreenBounds();
        float left = state.getViewportX() + canvasBounds[0];
        float top = state.getViewportY() + canvasBounds[1];
        float right = state.getViewportX() + canvasBounds[2];
        float bottom = state.getViewportY() + canvasBounds[3];

        // Draw with flipped V coordinates (OpenGL texture origin is bottom-left)
        drawList.addImage(
                framebuffer.getTextureId(),
                left, top, right, bottom,
                0, 1, 1, 0  // Flipped V
        );
    }

    // ========================================================================
    // PUBLIC ACCESSORS
    // ========================================================================

    public float getViewportX() {
        return state.getViewportX();
    }

    public float getViewportY() {
        return state.getViewportY();
    }

    public float getViewportWidth() {
        return state.getViewportWidth();
    }

    public float getViewportHeight() {
        return state.getViewportHeight();
    }

    public boolean isHovered() {
        return state.isHovered();
    }

    public boolean isFocused() {
        return state.isFocused();
    }

    public void resetCamera() {
        state.resetCamera();
    }

    // ========================================================================
    // UI CANVAS COLLECTION
    // ========================================================================

    /**
     * Collects root UICanvas components from the scene.
     * Since EditorGameObject extends GameObject, the UICanvas components
     * already have proper parent-child hierarchy via GameObject.
     */
    private List<UICanvas> collectUICanvases(EditorScene scene) {
        if (scene == null) return List.of();

        List<UICanvas> canvases = new java.util.ArrayList<>();
        for (EditorGameObject entity : scene.getEntities()) {
            if (!entity.isEnabled()) continue;
            UICanvas canvas = entity.getComponent(UICanvas.class);
            if (canvas != null && entity.getParent() == null) {
                canvases.add(canvas);
            }
        }
        canvases.sort(java.util.Comparator.comparingInt(UICanvas::getSortOrder));
        return canvases;
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    public void destroy() {
        renderer.destroy();

        if (pipeline != null) {
            pipeline.destroy();
            pipeline = null;
        }

        if (framebuffer != null) {
            framebuffer.destroy();
            framebuffer = null;
        }

        if (sceneFramebuffer != null) {
            sceneFramebuffer.destroy();
            sceneFramebuffer = null;
        }

        if (pickingPass != null) {
            pickingPass.destroy();
            pickingPass = null;
        }

        pipelineInitialized = false;
    }
}
