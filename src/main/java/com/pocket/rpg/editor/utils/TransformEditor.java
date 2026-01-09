package com.pocket.rpg.editor.utils;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.MoveEntityCommand;
import com.pocket.rpg.editor.undo.commands.RotateEntityCommand;
import com.pocket.rpg.editor.undo.commands.ScaleEntityCommand;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.joml.Vector3f;

import java.util.List;

/**
 * Custom editor for Transform component.
 * Supports prefab override visualization and reset functionality.
 */
public class TransformEditor implements CustomComponentEditor {

    private static final String TRANSFORM_TYPE = "com.pocket.rpg.components.Transform";

    private final float[] posX = new float[1];
    private final float[] posY = new float[1];

    private final float[] rotZ = new float[1];

    private final float[] scaleX = new float[1];
    private final float[] scaleY = new float[1];

    // Drag start values for undo
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
        boolean changed = false;

        changed |= drawPosition(entity, entity.isPrefabInstance());
        changed |= drawScale(entity, entity.isPrefabInstance());
        changed |= drawRotation(entity, entity.isPrefabInstance());

        return changed;
    }

    // ========================================================================
    // POSITION
    // ========================================================================

    private boolean drawPosition(EditorGameObject entity, boolean isPrefab) {
        Vector3f pos = entity.getPosition();
        posX[0] = pos.x;
        posY[0] = pos.y;

        boolean isOverridden = isPrefab && entity.isFieldOverridden(TRANSFORM_TYPE, "localPosition");
        String label = isOverridden ? "Position *" : "Position";

        final boolean[] changed = {false};
        final boolean[] deactivated = {false};

        FieldEditors.inspectorRow(label, () -> {
            ImGui.pushID("Position");

            if (isOverridden) {
                ImGui.pushStyleColor(ImGuiCol.Text, OVERRIDE_COLOR);
            }

            float fieldWidth = calcFieldWidth(2, isOverridden);

            // X
            axisLabel("X", AXIS_X_COLOR);
            ImGui.setNextItemWidth(fieldWidth);
            if (ImGui.dragFloat("##X", posX, 0.1f)) changed[0] = true;
            if (ImGui.isItemActivated()) capturePositionStart(pos);
            if (ImGui.isItemDeactivatedAfterEdit()) deactivated[0] = true;
            ImGui.sameLine();

            // Y
            axisLabel("Y", AXIS_Y_COLOR);
            ImGui.setNextItemWidth(fieldWidth);
            if (ImGui.dragFloat("##Y", posY, 0.1f)) changed[0] = true;
            if (ImGui.isItemActivated()) capturePositionStart(pos);
            if (ImGui.isItemDeactivatedAfterEdit()) deactivated[0] = true;

            if (isOverridden) {
                ImGui.popStyleColor();
                ImGui.sameLine();
                if (ImGui.smallButton("Reset##pos")) {
                    Vector3f oldValue = new Vector3f(entity.getPosition());
                    Object defaultObj = entity.getFieldDefault(TRANSFORM_TYPE, "localPosition");
                    Vector3f defaultValue = defaultObj instanceof Vector3f v ? v : new Vector3f();

                    entity.setPosition(defaultValue.x, defaultValue.y, defaultValue.z);
                    entity.resetFieldToDefault(TRANSFORM_TYPE, "localPosition");

                    // Update buffers so the code below doesn't overwrite
                    posX[0] = defaultValue.x;
                    posY[0] = defaultValue.y;

                    UndoManager.getInstance().push(
                            new MoveEntityCommand(entity, oldValue, defaultValue)
                    );
                    changed[0] = true;
                }
            }

            ImGui.popID();
        });

        // Apply change directly during drag/type
        if (changed[0]) {
            entity.setPosition(posX[0], posY[0], pos.z);
        }

        // Commit undo on release
        if (deactivated[0] && dragStartPosition != null) {
            Vector3f newPos = new Vector3f(posX[0], posY[0], pos.z);
            if (!newPos.equals(dragStartPosition)) {
                UndoManager.getInstance().push(
                        new MoveEntityCommand(entity, dragStartPosition, newPos)
                );
            }
            dragStartPosition = null;
        }

        return changed[0];
    }

    private void capturePositionStart(Vector3f current) {
        if (dragStartPosition == null) {
            dragStartPosition = new Vector3f(current);
        }
    }

    // ========================================================================
    // ROTATION
    // ========================================================================

    private boolean drawRotation(EditorGameObject entity, boolean isPrefab) {
        Vector3f rot = entity.getRotation();
        // Commented out as only Z rotation is used in 2D
        // rotX[0] = rot.x;
        // rotY[0] = rot.y;
        rotZ[0] = rot.z;

        boolean isOverridden = isPrefab && entity.isFieldOverridden(TRANSFORM_TYPE, "localRotation");
        String label = isOverridden ? "Rotation *" : "Rotation";

        final boolean[] changed = {false};
        final boolean[] deactivated = {false};

        FieldEditors.inspectorRow(label, () -> {
            ImGui.pushID("Rotation");

            if (isOverridden) {
                ImGui.pushStyleColor(ImGuiCol.Text, OVERRIDE_COLOR);
            }

            float fieldWidth = calcFieldWidth(1, isOverridden);

            /*
           // Commented out as only Z rotation is used in 2D
           // X
            axisLabel("X", AXIS_X_COLOR);
            ImGui.setNextItemWidth(fieldWidth);
            if (ImGui.dragFloat("##X", rotX, 0.5f)) changed[0] = true;
            if (ImGui.isItemActivated()) captureRotationStart(rot);
            if (ImGui.isItemDeactivatedAfterEdit()) deactivated[0] = true;
            ImGui.sameLine();

            // Y
            axisLabel("Y", AXIS_Y_COLOR);
            ImGui.setNextItemWidth(fieldWidth);
            if (ImGui.dragFloat("##Y", rotY, 0.5f)) changed[0] = true;
            if (ImGui.isItemActivated()) captureRotationStart(rot);
            if (ImGui.isItemDeactivatedAfterEdit()) deactivated[0] = true;
            ImGui.sameLine();
            */

            // Z
//            axisLabel("  ", AXIS_Z_COLOR);
            ImGui.setCursorPosX(ImGui.getCursorPosX() + ImGui.calcTextSizeX("Z") + 2 * ImGui.getStyle().getItemInnerSpacingX());
            ImGui.setNextItemWidth(fieldWidth);
            if (ImGui.dragFloat("##Z", rotZ, 0.5f)) changed[0] = true;
            if (ImGui.isItemActivated()) captureRotationStart(rot);
            if (ImGui.isItemDeactivatedAfterEdit()) deactivated[0] = true;

            if (isOverridden) {
                ImGui.popStyleColor();
                ImGui.sameLine();
                if (ImGui.smallButton("Reset##rot")) {
                    entity.resetFieldToDefault(TRANSFORM_TYPE, "localRotation");
                    changed[0] = true;
                }
            }

            ImGui.popID();
        });

        // Apply change directly
        if (changed[0]) {
            entity.setRotation(new Vector3f(rot.x, rot.y, rotZ[0]));
        }

        // Commit undo on release
        if (deactivated[0] && dragStartRotation != null) {
            Vector3f newRot = new Vector3f(rot.x, rot.y, rotZ[0]);
            if (!newRot.equals(dragStartRotation)) {
                UndoManager.getInstance().push(
                        new RotateEntityCommand(entity, dragStartRotation, newRot)
                );
            }
            dragStartRotation = null;
        }

        return changed[0];
    }

    private void captureRotationStart(Vector3f current) {
        if (dragStartRotation == null) {
            dragStartRotation = new Vector3f(current);
        }
    }

    // ========================================================================
    // SCALE
    // ========================================================================

    private boolean drawScale(EditorGameObject entity, boolean isPrefab) {
        Vector3f scale = entity.getScale();
        scaleX[0] = scale.x;
        scaleY[0] = scale.y;

        boolean isOverridden = isPrefab && entity.isFieldOverridden(TRANSFORM_TYPE, "localScale");
        String label = isOverridden ? "Scale *" : "Scale";

        final boolean[] changed = {false};
        final boolean[] deactivated = {false};

        FieldEditors.inspectorRow(label, () -> {
            ImGui.pushID("localScale");

            if (isOverridden) {
                ImGui.pushStyleColor(ImGuiCol.Text, OVERRIDE_COLOR);
            }

            float fieldWidth = calcFieldWidth(2, isOverridden);

            // X
            axisLabel("X", AXIS_X_COLOR);
            ImGui.setNextItemWidth(fieldWidth);
            if (ImGui.dragFloat("##X", scaleX, 0.01f)) changed[0] = true;
            if (ImGui.isItemActivated()) captureScaleStart(scale);
            if (ImGui.isItemDeactivatedAfterEdit()) deactivated[0] = true;
            ImGui.sameLine();

            // Y
            axisLabel("Y", AXIS_Y_COLOR);
            ImGui.setNextItemWidth(fieldWidth);
            if (ImGui.dragFloat("##Y", scaleY, 0.01f)) changed[0] = true;
            if (ImGui.isItemActivated()) captureScaleStart(scale);
            if (ImGui.isItemDeactivatedAfterEdit()) deactivated[0] = true;

            if (isOverridden) {
                ImGui.popStyleColor();
                ImGui.sameLine();
                if (ImGui.smallButton("Reset##scale")) {
                    entity.resetFieldToDefault(TRANSFORM_TYPE, "localScale");
                    changed[0] = true;
                }
            }

            ImGui.popID();
        });

        // Apply change directly
        if (changed[0]) {
            entity.setScale(scaleX[0], scaleY[0]);
        }

        // Commit undo on release
        if (deactivated[0] && dragStartScale != null) {
            Vector3f newScale = new Vector3f(scaleX[0], scaleY[0], scale.z);
            if (!newScale.equals(dragStartScale)) {
                UndoManager.getInstance().push(
                        new ScaleEntityCommand(entity, dragStartScale, newScale)
                );
            }
            dragStartScale = null;
        }

        return changed[0];
    }

    private void captureScaleStart(Vector3f current) {
        if (dragStartScale == null) {
            dragStartScale = new Vector3f(current);
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