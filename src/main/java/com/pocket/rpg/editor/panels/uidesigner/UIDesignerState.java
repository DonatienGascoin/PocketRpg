package com.pocket.rpg.editor.panels.uidesigner;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.commands.UITransformDragCommand;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

import java.util.List;

/**
 * Shared state for the UI Designer panel components.
 * Contains camera, zoom, drag state, and configuration.
 */
@Getter
@Setter
public class UIDesignerState {

    // ========================================================================
    // VIEWPORT STATE
    // ========================================================================

    private float viewportX, viewportY;
    private float viewportWidth, viewportHeight;
    private boolean isHovered = false;
    private boolean isFocused = false;

    // ========================================================================
    // CAMERA & ZOOM
    // ========================================================================

    private float cameraX;
    private float cameraY;
    private float zoom = 1.0f;

    private static final float MIN_ZOOM = 0.1f;
    private static final float MAX_ZOOM = 5.0f;

    // ========================================================================
    // CANVAS DIMENSIONS
    // ========================================================================

    private final int canvasWidth;
    private final int canvasHeight;

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    public enum BackgroundMode { WORLD, GRAY }

    private BackgroundMode backgroundMode = BackgroundMode.GRAY;
    private boolean snapEnabled = false;
    private boolean showAnchorLines = false;

    public static final float SNAP_THRESHOLD = 8f;

    // ========================================================================
    // DRAG STATE
    // ========================================================================

    private boolean isDraggingCamera = false;
    private boolean isDraggingElement = false;
    private boolean isDraggingHandle = false;
    private boolean isDraggingAnchor = false;
    private boolean isDraggingPivot = false;

    private EditorGameObject draggedEntity = null;
    private float dragStartX, dragStartY;
    private float entityStartOffsetX, entityStartOffsetY;
    private float entityStartWidth, entityStartHeight;
    private float entityStartAnchorX, entityStartAnchorY;
    private float entityStartPivotX, entityStartPivotY;

    // ========================================================================
    // DRAG UNDO STATE
    // ========================================================================

    private Vector2f dragOldOffset;
    private float dragOldWidth;
    private float dragOldHeight;
    private Vector2f dragOldAnchor;
    private Vector2f dragOldPivot;
    private List<UITransformDragCommand.ChildTransformState> dragChildStates;

    // ========================================================================
    // RESIZE HANDLES
    // ========================================================================

    public enum ResizeHandle {
        TOP_LEFT, TOP, TOP_RIGHT,
        LEFT, RIGHT,
        BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
    }

    public static final float HANDLE_SIZE = 8f;
    public static final float HANDLE_HIT_SIZE = 12f;

    private ResizeHandle activeHandle = null;
    private ResizeHandle hoveredHandle = null;
    private EditorGameObject hoveredHandleEntity = null;

    // ========================================================================
    // EXTERNAL RESOURCES
    // ========================================================================

    private int sceneTextureId = 0;

    // ========================================================================
    // COLORS (pre-computed for performance)
    // ========================================================================

    public static final int COLOR_HANDLE = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f);
    public static final int COLOR_HANDLE_HOVERED = ImGui.colorConvertFloat4ToU32(1f, 0.8f, 0.2f, 1f);
    public static final int COLOR_HANDLE_BORDER = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f);
    public static final int COLOR_ANCHOR = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 1f);
    public static final int COLOR_PIVOT = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 1f, 1f);
    public static final int COLOR_SELECTION = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 1f, 1f);
    public static final int COLOR_SNAP_GUIDE = ImGui.colorConvertFloat4ToU32(1f, 0.5f, 0.2f, 0.8f);
    public static final int COLOR_CANVAS_BORDER = ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 1f);
    public static final int COLOR_CANVAS_LABEL = ImGui.colorConvertFloat4ToU32(0.6f, 0.6f, 0.6f, 1f);

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public UIDesignerState(GameConfig gameConfig) {
        this.canvasWidth = gameConfig.getGameWidth();
        this.canvasHeight = gameConfig.getGameHeight();

        // Initialize camera to canvas center - canvas will be centered in viewport
        this.cameraX = canvasWidth / 2f;
        this.cameraY = canvasHeight / 2f;
    }

    // ========================================================================
    // CAMERA METHODS
    // ========================================================================

    public void resetCamera() {
        // Center canvas in viewport
        cameraX = canvasWidth / 2f;
        cameraY = canvasHeight / 2f;
        zoom = calculateFitZoom();
    }

    public float calculateFitZoom() {
        if (viewportWidth <= 0 || viewportHeight <= 0) return 1.0f;

        float zoomX = (viewportWidth - 40) / canvasWidth;
        float zoomY = (viewportHeight - 40) / canvasHeight;
        return Math.min(zoomX, zoomY);
    }

    public void adjustZoom(float delta) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + delta));
    }

    public void pan(float deltaX, float deltaY) {
        cameraX -= deltaX / zoom;
        cameraY -= deltaY / zoom;
    }

    // ========================================================================
    // DRAG STATE METHODS
    // ========================================================================

    public boolean isAnyDragActive() {
        return isDraggingCamera || isDraggingElement || isDraggingHandle
                || isDraggingAnchor || isDraggingPivot;
    }

    public void clearDragState() {
        isDraggingCamera = false;
        isDraggingElement = false;
        isDraggingHandle = false;
        isDraggingAnchor = false;
        isDraggingPivot = false;
        draggedEntity = null;
        activeHandle = null;
        dragChildStates = null;
    }

    public void clearHoverState() {
        hoveredHandle = null;
        hoveredHandleEntity = null;
    }
}
