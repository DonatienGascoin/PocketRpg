package com.pocket.rpg.editor.utils;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.MoveEntityCommand;
import com.pocket.rpg.editor.undo.commands.RotateEntityCommand;
import com.pocket.rpg.editor.undo.commands.ScaleEntityCommand;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.joml.Vector3f;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Custom editor for Transform component.
 * Supports prefab override visualization and reset functionality.
 */
public class TransformEditor implements CustomComponentEditor {

    private static final String TRANSFORM_TYPE = "com.pocket.rpg.components.Transform";

    private final float[] posX = new float[1];
    private final float[] posY = new float[1];
    private final float[] posZ = new float[1];
    private final float[] rotX = new float[1];
    private final float[] rotY = new float[1];
    private final float[] rotZ = new float[1];
    private final float[] scaleX = new float[1];
    private final float[] scaleY = new float[1];

    // Drag start values for undo
    private EditorGameObject draggingEntity = null;
    private Vector3f dragStartPosition = null;
    private Vector3f dragStartRotation = null;
    private Vector3f dragStartScale = null;

    // Axis colors
    private static final float[] AXIS_X_COLOR = {0.85f, 0.3f, 0.3f, 1.0f};
    private static final float[] AXIS_Y_COLOR = {0.3f, 0.85f, 0.3f, 1.0f};
    private static final float[] AXIS_Z_COLOR = {0.3f, 0.5f, 0.95f, 1.0f};
    private static final int OVERRIDE_COLOR = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.2f, 1.0f);

    @Override
    public boolean draw(Component component, EditorGameObject entity) {
        AtomicBoolean changed = new AtomicBoolean(false);
        boolean isPrefab = entity.isPrefabInstance();

        // Position
        changed.set(drawPosition(entity, isPrefab) || changed.get());

        // Rotation
        changed.set(drawRotation(entity, isPrefab) || changed.get());

        // Scale
        changed.set(drawScale(entity, isPrefab) || changed.get());

        return changed.get();
    }

    // ========================================================================
    // POSITION
    // ========================================================================

    private boolean drawPosition(EditorGameObject entity, boolean isPrefab) {
        Vector3f pos = entity.getPosition();
        posX[0] = pos.x;
        posY[0] = pos.y;
        posZ[0] = pos.z;

        boolean isOverridden = isPrefab && entity.isFieldOverridden(TRANSFORM_TYPE, "position");
        boolean changed = false;

        String label = isOverridden ? "Position *" : "Position";

        FieldEditors.inspectorRow(label, () -> {
            ImGui.pushID("Position");

            if (isOverridden) {
                ImGui.pushStyleColor(ImGuiCol.Text, OVERRIDE_COLOR);
            }

            float fieldWidth = calcFieldWidth(3, isOverridden);

            // X
            axisLabel("X", AXIS_X_COLOR);
            ImGui.setNextItemWidth(fieldWidth);
            boolean changedX = ImGui.dragFloat("##X", posX, 0.1f);
            ImGui.sameLine();

            // Y
            axisLabel("Y", AXIS_Y_COLOR);
            ImGui.setNextItemWidth(fieldWidth);
            boolean changedY = ImGui.dragFloat("##Y", posY, 0.1f);
            ImGui.sameLine();

            // Z (depth)
            axisLabel("Z", AXIS_Z_COLOR);
            ImGui.setNextItemWidth(fieldWidth);
            boolean changedZ = ImGui.dragFloat("##Z", posZ, 0.1f);

            if (isOverridden) {
                ImGui.popStyleColor();
                ImGui.sameLine();
                if (ImGui.smallButton("Reset##pos")) {
                    entity.resetFieldToDefault(TRANSFORM_TYPE, "position");
                }
            }

            if (changedX || changedY || changedZ) {
                entity.setPosition(posX[0], posY[0], posZ[0]);
            }

            // Undo tracking
            handlePositionUndo(entity, pos);

            ImGui.popID();
        });

        return changed || !pos.equals(entity.getPosition());
    }

    private void handlePositionUndo(EditorGameObject entity, Vector3f originalPos) {
        if (ImGui.isItemActivated()) {
            draggingEntity = entity;
            dragStartPosition = new Vector3f(originalPos);
        }

        if (ImGui.isItemDeactivatedAfterEdit() && draggingEntity == entity && dragStartPosition != null) {
            Vector3f newPos = entity.getPosition();
            if (!newPos.equals(dragStartPosition)) {
                UndoManager.getInstance().execute(
                        new MoveEntityCommand(entity, dragStartPosition, newPos)
                );
            }
            draggingEntity = null;
            dragStartPosition = null;
        }
    }

    // ========================================================================
    // ROTATION
    // ========================================================================

    private boolean drawRotation(EditorGameObject entity, boolean isPrefab) {
        Vector3f rot = entity.getRotation();
        rotX[0] = rot.x;
        rotY[0] = rot.y;
        rotZ[0] = rot.z;

        boolean isOverridden = isPrefab && entity.isFieldOverridden(TRANSFORM_TYPE, "rotation");
        boolean changed = false;

        String label = isOverridden ? "Rotation *" : "Rotation";

        FieldEditors.inspectorRow(label, () -> {
            ImGui.pushID("Rotation");

            if (isOverridden) {
                ImGui.pushStyleColor(ImGuiCol.Text, OVERRIDE_COLOR);
            }

            float fieldWidth = calcFieldWidth(3, isOverridden);

            // X
            axisLabel("X", AXIS_X_COLOR);
            ImGui.setNextItemWidth(fieldWidth);
            boolean changedX = ImGui.dragFloat("##X", rotX, 0.5f);
            ImGui.sameLine();

            // Y
            axisLabel("Y", AXIS_Y_COLOR);
            ImGui.setNextItemWidth(fieldWidth);
            boolean changedY = ImGui.dragFloat("##Y", rotY, 0.5f);
            ImGui.sameLine();

            // Z
            axisLabel("Z", AXIS_Z_COLOR);
            ImGui.setNextItemWidth(fieldWidth);
            boolean changedZ = ImGui.dragFloat("##Z", rotZ, 0.5f);

            if (isOverridden) {
                ImGui.popStyleColor();
                ImGui.sameLine();
                if (ImGui.smallButton("Reset##rot")) {
                    entity.resetFieldToDefault(TRANSFORM_TYPE, "rotation");
                }
            }

            if (changedX || changedY || changedZ) {
                entity.setRotation(new Vector3f(rotX[0], rotY[0], rotZ[0]));
            }

            // Undo tracking
            handleRotationUndo(entity, rot);

            ImGui.popID();
        });

        return changed || !rot.equals(entity.getRotation());
    }

    private void handleRotationUndo(EditorGameObject entity, Vector3f originalRot) {
        if (ImGui.isItemActivated()) {
            dragStartRotation = new Vector3f(originalRot);
        }

        if (ImGui.isItemDeactivatedAfterEdit() && dragStartRotation != null) {
            Vector3f newRot = entity.getRotation();
            if (!newRot.equals(dragStartRotation)) {
                UndoManager.getInstance().execute(
                        new RotateEntityCommand(entity, dragStartRotation, newRot)
                );
            }
            dragStartRotation = null;
        }
    }

    // ========================================================================
    // SCALE
    // ========================================================================

    private boolean drawScale(EditorGameObject entity, boolean isPrefab) {
        Vector3f scale = entity.getScale();
        scaleX[0] = scale.x;
        scaleY[0] = scale.y;

        boolean isOverridden = isPrefab && entity.isFieldOverridden(TRANSFORM_TYPE, "scale");
        boolean changed = false;

        String label = isOverridden ? "Scale *" : "Scale";

        FieldEditors.inspectorRow(label, () -> {
            ImGui.pushID("Scale");

            if (isOverridden) {
                ImGui.pushStyleColor(ImGuiCol.Text, OVERRIDE_COLOR);
            }

            float fieldWidth = calcFieldWidth(2, isOverridden);

            // X
            axisLabel("X", AXIS_X_COLOR);
            ImGui.setNextItemWidth(fieldWidth);
            boolean changedX = ImGui.dragFloat("##X", scaleX, 0.01f);
            ImGui.sameLine();

            // Y
            axisLabel("Y", AXIS_Y_COLOR);
            ImGui.setNextItemWidth(fieldWidth);
            boolean changedY = ImGui.dragFloat("##Y", scaleY, 0.01f);

            if (isOverridden) {
                ImGui.popStyleColor();
                ImGui.sameLine();
                if (ImGui.smallButton("Reset##scale")) {
                    entity.resetFieldToDefault(TRANSFORM_TYPE, "scale");
                }
            }

            if (changedX || changedY) {
                entity.setScale(scaleX[0], scaleY[0]);
            }

            // Undo tracking
            handleScaleUndo(entity, scale);

            ImGui.popID();
        });

        return changed || !scale.equals(entity.getScale());
    }

    private void handleScaleUndo(EditorGameObject entity, Vector3f originalScale) {
        if (ImGui.isItemActivated()) {
            dragStartScale = new Vector3f(originalScale);
        }

        if (ImGui.isItemDeactivatedAfterEdit() && dragStartScale != null) {
            Vector3f newScale = entity.getScale();
            if (!newScale.equals(dragStartScale)) {
                UndoManager.getInstance().execute(
                        new ScaleEntityCommand(entity, dragStartScale, newScale)
                );
            }
            dragStartScale = null;
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void axisLabel(String label, float[] color) {
        ImGui.textColored(color[0], color[1], color[2], color[3], label);
        ImGui.sameLine();
    }

    private float calcFieldWidth(int axisCount, boolean hasResetButton) {
        float avail = ImGui.getContentRegionAvailX();
        float labelWidth = ImGui.calcTextSize("X").x + ImGui.getStyle().getItemInnerSpacingX();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float resetWidth = hasResetButton ? (ImGui.calcTextSize("Reset").x + spacing * 2 + 8) : 0;

        float used = axisCount * (labelWidth + spacing) + resetWidth;
        return (avail - used) / axisCount;
    }
}