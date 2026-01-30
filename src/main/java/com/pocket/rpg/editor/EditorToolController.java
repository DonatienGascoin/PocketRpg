package com.pocket.rpg.editor;

import com.pocket.rpg.components.interaction.CameraBoundsZone;
import com.pocket.rpg.editor.events.CollisionTypePickedEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.StatusMessageEvent;
import com.pocket.rpg.editor.events.TilesPickedEvent;
import com.pocket.rpg.editor.events.ToggleBoundsZoneToolEvent;
import com.pocket.rpg.editor.panels.CollisionPanel;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.events.SelectionChangedEvent;
import com.pocket.rpg.editor.tools.BoundsZoneTool;
import com.pocket.rpg.editor.tools.CameraTool;
import com.pocket.rpg.editor.tools.CollisionBrushTool;
import com.pocket.rpg.editor.tools.CollisionEraserTool;
import com.pocket.rpg.editor.tools.CollisionFillTool;
import com.pocket.rpg.editor.tools.CollisionRectangleTool;
import com.pocket.rpg.editor.tools.CollisionPickerTool;
import com.pocket.rpg.editor.tools.EditorTool;
import com.pocket.rpg.editor.tools.MoveTool;
import com.pocket.rpg.editor.tools.RotateTool;
import com.pocket.rpg.editor.tools.ScaleTool;
import com.pocket.rpg.editor.tools.SelectionTool;
import com.pocket.rpg.editor.tools.TileBrushTool;
import com.pocket.rpg.editor.tools.TileEraserTool;
import com.pocket.rpg.editor.tools.TileFillTool;
import com.pocket.rpg.editor.tools.TileRectangleTool;
import com.pocket.rpg.editor.tools.TilePickerTool;
import com.pocket.rpg.editor.tools.ToolManager;
import lombok.Getter;
import lombok.Setter;

/**
 * Manages editor tools: creation and registration.
 * <p>
 * Handles tool registration and callback setup.
 * Tool UI is rendered by SceneViewToolbar.
 * Keyboard shortcuts are handled by the centralized shortcut system.
 *
 * @see com.pocket.rpg.editor.shortcut.ShortcutRegistry
 */
public class EditorToolController {

    private final EditorContext context;

    // Tilemap tools
    @Getter private TileBrushTool brushTool;
    @Getter private TileEraserTool eraserTool;
    @Getter private TileFillTool fillTool;
    @Getter private TileRectangleTool rectangleTool;
    @Getter private TilePickerTool pickerTool;

    // Collision tools
    @Getter private CollisionBrushTool collisionBrushTool;
    @Getter private CollisionEraserTool collisionEraserTool;
    @Getter private CollisionFillTool collisionFillTool;
    @Getter private CollisionRectangleTool collisionRectangleTool;
    @Getter private CollisionPickerTool collisionPickerTool;

    // Entity tools
    @Getter private SelectionTool selectionTool;

    // Transform tools
    @Getter private MoveTool moveTool;
    @Getter private RotateTool rotateTool;
    @Getter private ScaleTool scaleTool;

    // Camera tool
    @Getter private CameraTool cameraTool;

    // Bounds zone tool
    @Getter private BoundsZoneTool boundsZoneTool;

    // Tool to restore when camera is deselected
    private EditorTool previousTool;

    // Tool to restore when bounds zone entity is deselected
    private EditorTool previousToolBeforeBoundsZone;

    // Reference to collision panel for type display
    @Setter private CollisionPanel collisionPanel;

    public EditorToolController(EditorContext context) {
        this.context = context;
    }

    /**
     * Creates and registers all tools.
     */
    public void createTools() {
        EditorScene scene = context.getCurrentScene();
        ToolManager toolManager = context.getToolManager();

        // Tilemap tools
        brushTool = new TileBrushTool(scene);
        toolManager.registerTool(brushTool);

        eraserTool = new TileEraserTool(scene);
        toolManager.registerTool(eraserTool);

        fillTool = new TileFillTool(scene);
        toolManager.registerTool(fillTool);

        rectangleTool = new TileRectangleTool(scene);
        toolManager.registerTool(rectangleTool);

        pickerTool = new TilePickerTool(scene);
        toolManager.registerTool(pickerTool);

        // Collision tools
        collisionBrushTool = new CollisionBrushTool(scene);
        toolManager.registerTool(collisionBrushTool);

        collisionEraserTool = new CollisionEraserTool(scene);
        toolManager.registerTool(collisionEraserTool);

        collisionFillTool = new CollisionFillTool(scene);
        toolManager.registerTool(collisionFillTool);

        collisionRectangleTool = new CollisionRectangleTool(scene);
        toolManager.registerTool(collisionRectangleTool);

        collisionPickerTool = new CollisionPickerTool(scene);
        toolManager.registerTool(collisionPickerTool);

        // Entity tools
        selectionTool = new SelectionTool();
        selectionTool.setScene(scene);
        selectionTool.setCamera(context.getCamera());
        selectionTool.setSelectionManager(context.getSelectionManager());
        toolManager.registerTool(selectionTool);

        // Transform tools
        moveTool = new MoveTool();
        moveTool.setScene(scene);
        moveTool.setCamera(context.getCamera());
        moveTool.setSelectionManager(context.getSelectionManager());
        toolManager.registerTool(moveTool);

        rotateTool = new RotateTool();
        rotateTool.setScene(scene);
        rotateTool.setCamera(context.getCamera());
        rotateTool.setSelectionManager(context.getSelectionManager());
        toolManager.registerTool(rotateTool);

        scaleTool = new ScaleTool();
        scaleTool.setScene(scene);
        scaleTool.setCamera(context.getCamera());
        scaleTool.setSelectionManager(context.getSelectionManager());
        toolManager.registerTool(scaleTool);

        // Camera tool
        cameraTool = new CameraTool();
        cameraTool.setScene(scene);
        cameraTool.setCamera(context.getCamera());
        cameraTool.setSelectionManager(context.getSelectionManager());
        toolManager.registerTool(cameraTool);

        // Bounds zone tool
        boundsZoneTool = new BoundsZoneTool();
        boundsZoneTool.setScene(scene);
        boundsZoneTool.setCamera(context.getCamera());
        boundsZoneTool.setSelectionManager(context.getSelectionManager());
        toolManager.registerTool(boundsZoneTool);

        // Setup callbacks
        setupCallbacks();
    }

    /**
     * Updates tool scene references when scene changes.
     */
    public void updateSceneReferences(EditorScene scene) {
        // Tilemap tools
        brushTool.setScene(scene);
        eraserTool.setScene(scene);
        fillTool.setScene(scene);
        rectangleTool.setScene(scene);
        pickerTool.setScene(scene);

        // Collision tools
        collisionBrushTool.setScene(scene);
        collisionEraserTool.setScene(scene);
        collisionFillTool.setScene(scene);
        collisionRectangleTool.setScene(scene);
        collisionPickerTool.setScene(scene);

        // Entity tools
        selectionTool.setScene(scene);

        // Transform tools
        moveTool.setScene(scene);
        rotateTool.setScene(scene);
        scaleTool.setScene(scene);

        // Camera tool
        cameraTool.setScene(scene);

        // Bounds zone tool
        boundsZoneTool.setScene(scene);

        // Sync z-levels
        syncCollisionZLevels();
    }

    /**
     * Synchronizes Z-level from scene to all collision tools.
     */
    public void syncCollisionZLevels() {
        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        int z = scene.getCollisionZLevel();
        collisionBrushTool.setZLevel(z);
        collisionEraserTool.setZLevel(z);
        collisionFillTool.setZLevel(z);
        collisionRectangleTool.setZLevel(z);
        collisionPickerTool.setZLevel(z);
        selectionTool.setCollisionZLevel(z);
    }

    private void setupCallbacks() {
        ToolManager toolManager = context.getToolManager();

        // Subscribe to tile picker events
        EditorEventBus.get().subscribe(TilesPickedEvent.class, event -> {
            brushTool.setSelection(event.selection());
            fillTool.setSelection(event.selection());
            rectangleTool.setSelection(event.selection());
            toolManager.setActiveTool(brushTool);
            showMessage("Picked tiles - switched to Brush");
        });

        // Subscribe to collision picker events
        EditorEventBus.get().subscribe(CollisionTypePickedEvent.class, event -> {
            if (collisionPanel != null) {
                collisionPanel.setSelectedType(event.collisionType());
            }
            toolManager.setActiveTool(collisionBrushTool);
            showMessage("Picked collision: " + event.collisionType().getDisplayName() + " - switched to Brush");
        });

        // Switch to transform tool when entity is selected via SelectionTool
        // Skip if BoundsZoneTool was just activated by the SelectionChangedEvent
        selectionTool.setOnSwitchToTransformTool(toolName -> {
            if (toolManager.getActiveTool() != boundsZoneTool) {
                toolManager.setActiveTool(toolName);
            }
        });

        // Toggle bounds zone tool from inspector button
        EditorEventBus.get().subscribe(ToggleBoundsZoneToolEvent.class, event -> {
            if (event.activate()) {
                previousToolBeforeBoundsZone = toolManager.getActiveTool();
                toolManager.setActiveTool(boundsZoneTool);
            } else {
                if (previousToolBeforeBoundsZone != null && previousToolBeforeBoundsZone != boundsZoneTool) {
                    toolManager.setActiveTool(previousToolBeforeBoundsZone);
                } else {
                    toolManager.setActiveTool(selectionTool);
                }
                previousToolBeforeBoundsZone = null;
            }
        });

        // Auto-activate camera tool when camera is selected
        EditorEventBus.get().subscribe(SelectionChangedEvent.class, event -> {
            if (event.selectionType() == EditorSelectionManager.SelectionType.CAMERA) {
                previousTool = toolManager.getActiveTool();
                toolManager.setActiveTool(cameraTool);
            } else if (event.previousType() == EditorSelectionManager.SelectionType.CAMERA) {
                if (previousTool != null && previousTool != cameraTool) {
                    toolManager.setActiveTool(previousTool);
                } else {
                    toolManager.setActiveTool(selectionTool);
                }
                previousTool = null;
            }

            // Auto-activate bounds zone tool when a CameraBoundsZone entity is selected
            if (event.selectionType() == EditorSelectionManager.SelectionType.ENTITY) {
                EditorSelectionManager sm = context.getSelectionManager();
                if (sm != null && sm.hasEntitySelection()) {
                    EditorGameObject selected = sm.getFirstSelectedEntity();
                    if (selected != null && selected.getComponent(CameraBoundsZone.class) != null) {
                        previousToolBeforeBoundsZone = toolManager.getActiveTool();
                        toolManager.setActiveTool(boundsZoneTool);
                    } else if (toolManager.getActiveTool() == boundsZoneTool) {
                        // Deselected a bounds zone entity, restore previous tool
                        if (previousToolBeforeBoundsZone != null && previousToolBeforeBoundsZone != boundsZoneTool) {
                            toolManager.setActiveTool(previousToolBeforeBoundsZone);
                        } else {
                            toolManager.setActiveTool(selectionTool);
                        }
                        previousToolBeforeBoundsZone = null;
                    }
                }
            } else if (event.previousType() == EditorSelectionManager.SelectionType.ENTITY
                    && toolManager.getActiveTool() == boundsZoneTool) {
                // Selection changed away from entity, restore previous tool
                if (previousToolBeforeBoundsZone != null && previousToolBeforeBoundsZone != boundsZoneTool) {
                    toolManager.setActiveTool(previousToolBeforeBoundsZone);
                } else {
                    toolManager.setActiveTool(selectionTool);
                }
                previousToolBeforeBoundsZone = null;
            }
        });
    }

    private void showMessage(String message) {
        EditorEventBus.get().publish(new StatusMessageEvent(message));
    }
}