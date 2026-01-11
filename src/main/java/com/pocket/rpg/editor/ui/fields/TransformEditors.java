package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Transform;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.MoveEntityCommand;
import com.pocket.rpg.editor.undo.commands.RotateEntityCommand;
import com.pocket.rpg.editor.undo.commands.ScaleEntityCommand;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.joml.Vector3f;

/**
 * Specialized field editors for Transform component.
 * <p>
 * Handles all complexity internally:
 * - Override detection and styling
 * - Reset button with proper buffer updates
 * - Undo capture/push (on deactivation, not every frame)
 * - XYZ axis coloring
 * <p>
 * Usage in custom editor:
 * <pre>
 * public boolean draw(Component component, EditorGameObject entity) {
 *     boolean changed = false;
 *     changed |= TransformEditors.drawPosition("Position", entity);
 *     changed |= TransformEditors.drawScale("Scale", entity);
 *     changed |= TransformEditors.drawRotation("Rotation", entity);
 *     return changed;
 * }
 * </pre>
 */
public final class TransformEditors {

    private static final String TRANSFORM_TYPE = Transform.class.getName();

    // Buffers
    private static final float[] posX = new float[1];
    private static final float[] posY = new float[1];
    private static final float[] rotZ = new float[1];
    private static final float[] scaleX = new float[1];
    private static final float[] scaleY = new float[1];

    // Undo capture state
    private static Vector3f dragStartPosition = null;
    private static Vector3f dragStartRotation = null;
    private static Vector3f dragStartScale = null;

    // Axis colors
    private static final float[] AXIS_X_COLOR = {0.85f, 0.3f, 0.3f, 1.0f};
    private static final float[] AXIS_Y_COLOR = {0.3f, 0.85f, 0.3f, 1.0f};
    private static final float[] AXIS_Z_COLOR = {0.3f, 0.5f, 0.95f, 1.0f};
    private static final int OVERRIDE_COLOR = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.2f, 1.0f);

    private TransformEditors() {}

    // ========================================================================
    // POSITION
    // ========================================================================

    /**
     * Draws position editor with XY fields, override styling, reset button, and undo.
     */
    public static boolean drawPosition(String label, EditorGameObject entity) {
        Vector3f pos = entity.getPosition();
        posX[0] = pos.x;
        posY[0] = pos.y;

        boolean isPrefab = entity.isPrefabInstance();
        boolean isOverridden = isPrefab && entity.isFieldOverridden(TRANSFORM_TYPE, "localPosition");

        final boolean[] changed = {false};
        final boolean[] deactivated = {false};

        FieldEditorUtils.inspectorRow(label, () -> {
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
                if (ImGui.smallButton(FontAwesomeIcons.Undo + "##pos")) {
                    Vector3f oldValue = new Vector3f(entity.getPosition());
                    Object defaultObj = entity.getFieldDefault(TRANSFORM_TYPE, "localPosition");
                    Vector3f defaultValue = defaultObj instanceof Vector3f v ? v : new Vector3f();

                    entity.setPosition(defaultValue.x, defaultValue.y, defaultValue.z);
                    entity.resetFieldToDefault(TRANSFORM_TYPE, "localPosition");

                    // Update buffers
                    posX[0] = defaultValue.x;
                    posY[0] = defaultValue.y;

                    UndoManager.getInstance().push(new MoveEntityCommand(entity, oldValue, defaultValue));
                    changed[0] = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Reset to prefab default");
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
                UndoManager.getInstance().push(new MoveEntityCommand(entity, dragStartPosition, newPos));
            }
            dragStartPosition = null;
        }

        return changed[0];
    }

    private static void capturePositionStart(Vector3f current) {
        if (dragStartPosition == null) {
            dragStartPosition = new Vector3f(current);
        }
    }

    // ========================================================================
    // ROTATION
    // ========================================================================

    /**
     * Draws rotation editor with Z field (2D), override styling, reset button, and undo.
     */
    public static boolean drawRotation(String label, EditorGameObject entity) {
        Vector3f rot = entity.getRotation();
        rotZ[0] = rot.z;

        boolean isPrefab = entity.isPrefabInstance();
        boolean isOverridden = isPrefab && entity.isFieldOverridden(TRANSFORM_TYPE, "localRotation");

        final boolean[] changed = {false};
        final boolean[] deactivated = {false};

        FieldEditorUtils.inspectorRow(label, () -> {
            ImGui.pushID("Rotation");

            if (isOverridden) {
                ImGui.pushStyleColor(ImGuiCol.Text, OVERRIDE_COLOR);
            }

            float fieldWidth = calcFieldWidth(1, isOverridden);

            // Offset to align with other fields
            ImGui.setCursorPosX(ImGui.getCursorPosX() + ImGui.calcTextSizeX("Z") + 2 * ImGui.getStyle().getItemInnerSpacingX());
            ImGui.setNextItemWidth(fieldWidth);
            if (ImGui.dragFloat("##Z", rotZ, 0.5f)) changed[0] = true;
            if (ImGui.isItemActivated()) captureRotationStart(rot);
            if (ImGui.isItemDeactivatedAfterEdit()) deactivated[0] = true;

            if (isOverridden) {
                ImGui.popStyleColor();
                ImGui.sameLine();
                if (ImGui.smallButton(FontAwesomeIcons.Undo + "##rot")) {
                    Vector3f oldValue = new Vector3f(entity.getRotation());
                    Object defaultObj = entity.getFieldDefault(TRANSFORM_TYPE, "localRotation");
                    Vector3f defaultValue = defaultObj instanceof Vector3f v ? v : new Vector3f();

                    entity.setRotation(defaultValue);
                    entity.resetFieldToDefault(TRANSFORM_TYPE, "localRotation");

                    rotZ[0] = defaultValue.z;

                    UndoManager.getInstance().push(new RotateEntityCommand(entity, oldValue, defaultValue));
                    changed[0] = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Reset to prefab default");
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
                UndoManager.getInstance().push(new RotateEntityCommand(entity, dragStartRotation, newRot));
            }
            dragStartRotation = null;
        }

        return changed[0];
    }

    private static void captureRotationStart(Vector3f current) {
        if (dragStartRotation == null) {
            dragStartRotation = new Vector3f(current);
        }
    }

    // ========================================================================
    // SCALE
    // ========================================================================

    /**
     * Draws scale editor with XY fields, override styling, reset button, and undo.
     */
    public static boolean drawScale(String label, EditorGameObject entity) {
        Vector3f scale = entity.getScale();
        scaleX[0] = scale.x;
        scaleY[0] = scale.y;

        boolean isPrefab = entity.isPrefabInstance();
        boolean isOverridden = isPrefab && entity.isFieldOverridden(TRANSFORM_TYPE, "localScale");

        final boolean[] changed = {false};
        final boolean[] deactivated = {false};

        FieldEditorUtils.inspectorRow(label, () -> {
            ImGui.pushID("Scale");

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
                if (ImGui.smallButton(FontAwesomeIcons.Undo + "##scale")) {
                    Vector3f oldValue = new Vector3f(entity.getScale());
                    Object defaultObj = entity.getFieldDefault(TRANSFORM_TYPE, "localScale");
                    Vector3f defaultValue = defaultObj instanceof Vector3f v ? v : new Vector3f(1, 1, 1);

                    entity.setScale(defaultValue);
                    entity.resetFieldToDefault(TRANSFORM_TYPE, "localScale");

                    scaleX[0] = defaultValue.x;
                    scaleY[0] = defaultValue.y;

                    UndoManager.getInstance().push(new ScaleEntityCommand(entity, oldValue, defaultValue));
                    changed[0] = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Reset to prefab default");
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
                UndoManager.getInstance().push(new ScaleEntityCommand(entity, dragStartScale, newScale));
            }
            dragStartScale = null;
        }

        return changed[0];
    }

    private static void captureScaleStart(Vector3f current) {
        if (dragStartScale == null) {
            dragStartScale = new Vector3f(current);
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private static void axisLabel(String label, float[] color) {
        ImGui.textColored(color[0], color[1], color[2], color[3], label);
        ImGui.sameLine();
    }

    private static float calcFieldWidth(int axisCount, boolean hasResetButton) {
        float avail = ImGui.getContentRegionAvailX();
        float labelWidth = ImGui.calcTextSize("X").x + ImGui.getStyle().getItemInnerSpacingX();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float resetWidth = hasResetButton ? (RESET_BUTTON_WIDTH + spacing) : 0;

        float used = axisCount * (labelWidth + spacing) + resetWidth;
        return (avail - used) / axisCount;
    }

    private static final float RESET_BUTTON_WIDTH = 25f;
}
