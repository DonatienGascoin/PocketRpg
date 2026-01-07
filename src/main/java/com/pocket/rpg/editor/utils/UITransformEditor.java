package com.pocket.rpg.editor.utils;

import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.UITransformDragCommand;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentData;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom editor for UITransform component.
 * <p>
 * Features:
 * - 9-button anchor preset grid
 * - 9-button pivot preset grid
 * - Offset drag fields
 * - Size drag fields with aspect lock option
 * - Cascading resize (children scale with parent)
 * - Undo/redo support
 */
public class UITransformEditor implements CustomComponentEditor {

    // Anchor/Pivot preset positions
    private static final float[][] PRESETS = {
            {0f, 0f},     // Top-Left
            {0.5f, 0f},   // Top-Center
            {1f, 0f},     // Top-Right
            {0f, 0.5f},   // Middle-Left
            {0.5f, 0.5f}, // Center
            {1f, 0.5f},   // Middle-Right
            {0f, 1f},     // Bottom-Left
            {0.5f, 1f},   // Bottom-Center
            {1f, 1f}      // Bottom-Right
    };

    // Preset labels (short)
    private static final String[] PRESET_LABELS = {
            "TL", "T", "TR",
            "L", "C", "R",
            "BL", "B", "BR"
    };

    // Preset tooltips
    private static final String[] PRESET_TOOLTIPS = {
            "Top-Left (0, 0)",
            "Top-Center (0.5, 0)",
            "Top-Right (1, 0)",
            "Middle-Left (0, 0.5)",
            "Center (0.5, 0.5)",
            "Middle-Right (1, 0.5)",
            "Bottom-Left (0, 1)",
            "Bottom-Center (0.5, 1)",
            "Bottom-Right (1, 1)"
    };

    private boolean lockAspectRatio = false;
    private float lastWidth = 0;
    private float lastHeight = 0;

    /**
     * When true, anchor and pivot grids are displayed side by side (compact).
     * When false, they are stacked vertically (default).
     * -- SETTER --
     *  Sets the layout mode.
     *
     *
     * -- GETTER --
     *  Returns whether compact layout is enabled.
     @param compact true for side-by-side anchor/pivot, false for stacked

     */
    @Getter
    @Setter private boolean compactLayout = false;

    public UITransformEditor(boolean compactLayout) {
        this.compactLayout = compactLayout;
    }

    // Track editing state for undo
    private boolean isEditingSize = false;
    private float editStartWidth;
    private float editStartHeight;
    private Vector2f editStartOffset;
    private List<ChildState> editStartChildStates;

    /**
     * Stores a child's transform state for undo.
     */
    private static class ChildState {
        final EditorEntity entity;
        final ComponentData transform;
        final Vector2f oldOffset;
        final float oldWidth;
        final float oldHeight;

        ChildState(EditorEntity entity, ComponentData transform) {
            this.entity = entity;
            this.transform = transform;
            Map<String, Object> fields = transform.getFields();
            this.oldOffset = new Vector2f(FieldEditors.getVector2f(fields, "offset"));
            this.oldWidth = FieldEditors.getFloat(fields, "width", 100);
            this.oldHeight = FieldEditors.getFloat(fields, "height", 100);
        }
    }

    @Override
    public boolean draw(ComponentData data, EditorEntity entity) {
        Map<String, Object> fields = data.getFields();
        boolean changed = false;

        Vector2f anchor = FieldEditors.getVector2f(fields, "anchor");
        Vector2f pivot = FieldEditors.getVector2f(fields, "pivot");

        if (compactLayout) {
            // Compact layout: Anchor and Pivot side by side
            float halfWidth = ImGui.getContentRegionAvailX() / 2 - 10;

            // Anchor (left side)
            ImGui.beginGroup();
            ImGui.text(FontAwesomeIcons.Anchor + " Anchor");
            ImGui.sameLine();
            ImGui.textDisabled(String.format("(%.1f,%.1f)", anchor.x, anchor.y));
            changed |= drawPresetGrid("anchor", fields, anchor, entity, data);
            ImGui.endGroup();

            ImGui.sameLine(halfWidth + 20);

            // Pivot (right side)
            ImGui.beginGroup();
            ImGui.text(FontAwesomeIcons.Crosshairs + " Pivot");
            ImGui.sameLine();
            ImGui.textDisabled(String.format("(%.1f,%.1f)", pivot.x, pivot.y));
            changed |= drawPresetGrid("pivot", fields, pivot, entity, data);
            ImGui.endGroup();
        } else {
            // Normal layout: Anchor and Pivot stacked
            // Section: Anchor
            ImGui.text(FontAwesomeIcons.Anchor + " Anchor");
            ImGui.sameLine(100);
            ImGui.textDisabled(String.format("(%.2f, %.2f)", anchor.x, anchor.y));
            changed |= drawPresetGrid("anchor", fields, anchor, entity, data);

            ImGui.spacing();

            // Section: Pivot
            ImGui.text(FontAwesomeIcons.Crosshairs + " Pivot");
            ImGui.sameLine(100);
            ImGui.textDisabled(String.format("(%.2f, %.2f)", pivot.x, pivot.y));
            changed |= drawPresetGrid("pivot", fields, pivot, entity, data);
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Section: Offset
        ImGui.text(FontAwesomeIcons.ArrowsAlt + " Offset");

        ImGui.sameLine();
        if (ImGui.smallButton("Reset##offset")) {
            Vector2f currentOffset = FieldEditors.getVector2f(fields, "offset");
            if (currentOffset.x != 0 || currentOffset.y != 0) {
                createMoveCommand(entity, data, currentOffset, new Vector2f(0, 0), fields);
                fields.put("offset", new Vector2f(0, 0));
                changed = true;
            }
        }

        // Track offset changes for undo
        Vector2f oldOffset = new Vector2f(FieldEditors.getVector2f(fields, "offset"));
        boolean offsetChanged = FieldEditors.drawVector2f("offset", fields, "offset", 1.0f);

        if (offsetChanged) {
            Vector2f newOffset = FieldEditors.getVector2f(fields, "offset");
            createMoveCommand(entity, data, oldOffset, newOffset, fields);
            changed = true;
        }



        ImGui.spacing();

        // Section: Size (with cascading resize)
        changed |= drawSizeSection(fields, entity, data);

        return changed;
    }

    /**
     * Draws the size section with cascading resize support.
     */
    private boolean drawSizeSection(Map<String, Object> fields, EditorEntity entity, ComponentData data) {
        boolean changed = false;

        ImGui.text(FontAwesomeIcons.ExpandAlt + " Size");

        // Lock aspect ratio toggle
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 25);
        if (ImGui.smallButton(lockAspectRatio ? FontAwesomeIcons.Lock : FontAwesomeIcons.LockOpen)) {
            lockAspectRatio = !lockAspectRatio;
            if (lockAspectRatio) {
                lastWidth = FieldEditors.getFloat(fields, "width", 100);
                lastHeight = FieldEditors.getFloat(fields, "height", 100);
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(lockAspectRatio ? "Aspect ratio locked" : "Lock aspect ratio");
        }

        // Width
        float width = FieldEditors.getFloat(fields, "width", 100);
        float height = FieldEditors.getFloat(fields, "height", 100);

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.5f - 30);
        float[] widthBuf = {width};

        // Start editing on activation
        if (ImGui.isItemActivated() || (!isEditingSize && ImGui.isItemActive())) {
            startSizeEdit(fields, entity);
        }

        if (ImGui.dragFloat("W", widthBuf, 1.0f, 1.0f, 10000f)) {
            if (!isEditingSize) {
                startSizeEdit(fields, entity);
            }

            float newWidth = Math.max(1, widthBuf[0]);
            float newHeight = height;

            if (lockAspectRatio && lastWidth > 0) {
                float ratio = newWidth / lastWidth;
                newHeight = lastHeight * ratio;
                lastWidth = newWidth;
                lastHeight = newHeight;
            }

            applySizeChange(fields, entity, editStartWidth, editStartHeight, newWidth, newHeight);
            changed = true;
        }

        // End editing on deactivation
        if (ImGui.isItemDeactivatedAfterEdit()) {
            commitSizeEdit(fields, entity, data);
        }

        ImGui.sameLine();

        // Height
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 20);
        float[] heightBuf = {FieldEditors.getFloat(fields, "height", 100)};

        if (ImGui.isItemActivated() || (!isEditingSize && ImGui.isItemActive())) {
            startSizeEdit(fields, entity);
        }

        if (ImGui.dragFloat("H", heightBuf, 1.0f, 1.0f, 10000f)) {
            if (!isEditingSize) {
                startSizeEdit(fields, entity);
            }

            float newHeight = Math.max(1, heightBuf[0]);
            float currentWidth = FieldEditors.getFloat(fields, "width", 100);
            float newWidth = currentWidth;

            if (lockAspectRatio && lastHeight > 0) {
                float ratio = newHeight / lastHeight;
                newWidth = lastWidth * ratio;
                lastWidth = newWidth;
                lastHeight = newHeight;
            }

            applySizeChange(fields, entity, editStartWidth, editStartHeight, newWidth, newHeight);
            changed = true;
        }

        if (ImGui.isItemDeactivatedAfterEdit()) {
            commitSizeEdit(fields, entity, data);
        }

        // Quick size presets
        ImGui.spacing();
        changed |= drawSizePreset(fields, entity, data, "32x32", 32, 32);
        ImGui.sameLine();
        changed |= drawSizePreset(fields, entity, data, "64x64", 64, 64);
        ImGui.sameLine();
        changed |= drawSizePreset(fields, entity, data, "128x128", 128, 128);

        // Texture size button (only if entity has a sprite)
        float[] textureDims = getSpriteTextureDimensions(entity);
        if (textureDims != null) {
            ImGui.sameLine();
            if (ImGui.smallButton(FontAwesomeIcons.Image + "##textureSize")) {
                float oldWidth = FieldEditors.getFloat(fields, "width", 100);
                float oldHeight = FieldEditors.getFloat(fields, "height", 100);

                if (oldWidth != textureDims[0] || oldHeight != textureDims[1]) {
                    startSizeEdit(fields, entity);
                    applySizeChange(fields, entity, oldWidth, oldHeight, textureDims[0], textureDims[1]);
                    commitSizeEdit(fields, entity, data);
                    changed = true;
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Match texture size (" + (int)textureDims[0] + "x" + (int)textureDims[1] + ")");
            }
        }

        // Match parent size button
        EditorEntity parentEntity = entity.getParent();
        if (parentEntity != null) {
            ComponentData parentTransform = parentEntity.getComponentByType("UITransform");
            if (parentTransform != null) {
                ImGui.sameLine();
                if (ImGui.smallButton(FontAwesomeIcons.Expand + "##matchParent")) {
                    float parentWidth = FieldEditors.getFloat(parentTransform.getFields(), "width", 100);
                    float parentHeight = FieldEditors.getFloat(parentTransform.getFields(), "height", 100);
                    float oldWidth = FieldEditors.getFloat(fields, "width", 100);
                    float oldHeight = FieldEditors.getFloat(fields, "height", 100);

                    if (oldWidth != parentWidth || oldHeight != parentHeight) {
                        startSizeEdit(fields, entity);
                        applySizeChange(fields, entity, oldWidth, oldHeight, parentWidth, parentHeight);
                        fields.put("offset", new Vector2f(0, 0));  // Center in parent
                        commitSizeEdit(fields, entity, data);
                        changed = true;
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Match parent size (" +
                            (int)FieldEditors.getFloat(parentTransform.getFields(), "width", 100) + "x" +
                            (int)FieldEditors.getFloat(parentTransform.getFields(), "height", 100) + ")");
                }
            }
        }

        return changed;
    }

    private boolean drawSizePreset(Map<String, Object> fields, EditorEntity entity, ComponentData data,
                                   String label, float targetWidth, float targetHeight) {
        if (ImGui.smallButton(label)) {
            float oldWidth = FieldEditors.getFloat(fields, "width", 100);
            float oldHeight = FieldEditors.getFloat(fields, "height", 100);

            if (oldWidth != targetWidth || oldHeight != targetHeight) {
                // Capture state for undo
                startSizeEdit(fields, entity);
                applySizeChange(fields, entity, oldWidth, oldHeight, targetWidth, targetHeight);
                commitSizeEdit(fields, entity, data);
                return true;
            }
        }
        return false;
    }

    /**
     * Starts tracking a size edit for undo.
     */
    private void startSizeEdit(Map<String, Object> fields, EditorEntity entity) {
        if (isEditingSize) return;

        isEditingSize = true;
        editStartWidth = FieldEditors.getFloat(fields, "width", 100);
        editStartHeight = FieldEditors.getFloat(fields, "height", 100);
        editStartOffset = new Vector2f(FieldEditors.getVector2f(fields, "offset"));

        // Capture child states
        editStartChildStates = new ArrayList<>();
        captureChildStates(entity, editStartChildStates);
    }

    /**
     * Recursively captures child transform states.
     */
    private void captureChildStates(EditorEntity parent, List<ChildState> states) {
        for (EditorEntity child : parent.getChildren()) {
            ComponentData childTransform = child.getComponentByType("UITransform");
            if (childTransform != null) {
                states.add(new ChildState(child, childTransform));
                captureChildStates(child, states);
            }
        }
    }

    /**
     * Applies a size change with cascading to children.
     */
    private void applySizeChange(Map<String, Object> fields, EditorEntity entity,
                                 float oldWidth, float oldHeight, float newWidth, float newHeight) {
        // Apply to parent
        fields.put("width", newWidth);
        fields.put("height", newHeight);

        // Calculate scale factors
        float scaleX = (oldWidth > 0) ? newWidth / oldWidth : 1f;
        float scaleY = (oldHeight > 0) ? newHeight / oldHeight : 1f;

        // Apply cascading to children (use original values from edit start)
        if (editStartChildStates != null) {
            for (ChildState state : editStartChildStates) {
                Map<String, Object> childFields = state.transform.getFields();

                // Scale relative to original values
                float scaledWidth = state.oldWidth * scaleX;
                float scaledHeight = state.oldHeight * scaleY;
                float scaledOffsetX = state.oldOffset.x * scaleX;
                float scaledOffsetY = state.oldOffset.y * scaleY;

                childFields.put("width", Math.max(1, scaledWidth));
                childFields.put("height", Math.max(1, scaledHeight));
                childFields.put("offset", new Vector2f(scaledOffsetX, scaledOffsetY));
            }
        }
    }

    /**
     * Commits the size edit and creates undo command.
     */
    private void commitSizeEdit(Map<String, Object> fields, EditorEntity entity, ComponentData data) {
        if (!isEditingSize) return;

        float newWidth = FieldEditors.getFloat(fields, "width", 100);
        float newHeight = FieldEditors.getFloat(fields, "height", 100);
        Vector2f newOffset = FieldEditors.getVector2f(fields, "offset");
        Vector2f anchor = FieldEditors.getVector2f(fields, "anchor");
        Vector2f pivot = FieldEditors.getVector2f(fields, "pivot");

        // Check if anything changed
        boolean hasChanges = (editStartWidth != newWidth || editStartHeight != newHeight);

        if (hasChanges) {
            // Create undo command
            UITransformDragCommand command = UITransformDragCommand.resize(
                    entity, data,
                    editStartOffset, editStartWidth, editStartHeight,
                    newOffset, newWidth, newHeight,
                    anchor, pivot
            );

            // Add child states
            if (editStartChildStates != null) {
                for (ChildState state : editStartChildStates) {
                    Map<String, Object> childFields = state.transform.getFields();
                    Vector2f childNewOffset = FieldEditors.getVector2f(childFields, "offset");
                    float childNewWidth = FieldEditors.getFloat(childFields, "width", 100);
                    float childNewHeight = FieldEditors.getFloat(childFields, "height", 100);

                    UITransformDragCommand.ChildTransformState childState =
                            new UITransformDragCommand.ChildTransformState(
                                    state.entity, state.transform,
                                    state.oldOffset, state.oldWidth, state.oldHeight
                            );
                    childState.setNewValues(childNewOffset, childNewWidth, childNewHeight);

                    command.addChildState(childState);
                }
            }

            // Revert to old values, then execute through UndoManager
            command.undo();
            UndoManager.getInstance().execute(command);
        }

        isEditingSize = false;
        editStartChildStates = null;
    }

    /**
     * Creates a move command for offset changes.
     */
    private void createMoveCommand(EditorEntity entity, ComponentData data,
                                   Vector2f oldOffset, Vector2f newOffset,
                                   Map<String, Object> fields) {
        float width = FieldEditors.getFloat(fields, "width", 100);
        float height = FieldEditors.getFloat(fields, "height", 100);
        Vector2f anchor = FieldEditors.getVector2f(fields, "anchor");
        Vector2f pivot = FieldEditors.getVector2f(fields, "pivot");

        UITransformDragCommand command = UITransformDragCommand.move(
                entity, data,
                oldOffset, newOffset,
                width, height, anchor, pivot
        );

        // Revert and execute through UndoManager
        fields.put("offset", new Vector2f(oldOffset));
        UndoManager.getInstance().execute(command);
    }

    /**
     * Draws a 3x3 preset grid for anchor or pivot.
     */
    private boolean drawPresetGrid(String fieldKey, Map<String, Object> fields, Vector2f current,
                                   EditorEntity entity, ComponentData data) {
        boolean changed = false;

        float buttonSize = 24;
        float spacing = 2;

        ImGui.pushID(fieldKey + "_grid");

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                float presetX = PRESETS[idx][0];
                float presetY = PRESETS[idx][1];

                boolean isSelected = Math.abs(current.x - presetX) < 0.01f &&
                        Math.abs(current.y - presetY) < 0.01f;

                if (col > 0) ImGui.sameLine(0, spacing);

                // Highlight selected
                if (isSelected) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.6f, 0.9f, 1.0f);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.4f, 0.7f, 1.0f, 1.0f);
                }

                ImGui.pushID(idx);
                if (ImGui.button(PRESET_LABELS[idx], buttonSize, buttonSize)) {
                    Vector2f oldValue = new Vector2f(current);
                    Vector2f newValue = new Vector2f(presetX, presetY);

                    if (!oldValue.equals(newValue)) {
                        createAnchorPivotCommand(entity, data, fieldKey, oldValue, newValue, fields);
                        fields.put(fieldKey, newValue);
                        changed = true;
                    }
                }
                ImGui.popID();

                if (isSelected) {
                    ImGui.popStyleColor(2);
                }

                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(PRESET_TOOLTIPS[idx]);
                }
            }
        }

        // Custom value input (collapsed)
        ImGui.sameLine(0, 10);
        ImGui.pushID("custom");
        if (ImGui.smallButton("...")) {
            ImGui.openPopup("custom_value");
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Enter custom value");
        }

        if (ImGui.beginPopup("custom_value")) {
            ImGui.text("Custom " + (fieldKey.equals("anchor") ? "Anchor" : "Pivot"));
            ImGui.separator();

            float[] xBuf = {current.x};
            float[] yBuf = {current.y};

            Vector2f oldValue = new Vector2f(current);

            ImGui.setNextItemWidth(80);
            if (ImGui.sliderFloat("X", xBuf, 0f, 1f)) {
                Vector2f newValue = new Vector2f(xBuf[0], current.y);
                createAnchorPivotCommand(entity, data, fieldKey, oldValue, newValue, fields);
                current.x = xBuf[0];
                fields.put(fieldKey, new Vector2f(current));
                changed = true;
            }

            ImGui.setNextItemWidth(80);
            if (ImGui.sliderFloat("Y", yBuf, 0f, 1f)) {
                Vector2f newValue = new Vector2f(current.x, yBuf[0]);
                createAnchorPivotCommand(entity, data, fieldKey, oldValue, newValue, fields);
                current.y = yBuf[0];
                fields.put(fieldKey, new Vector2f(current));
                changed = true;
            }

            ImGui.endPopup();
        }
        ImGui.popID();

        ImGui.popID();
        return changed;
    }

    /**
     * Creates an undo command for anchor or pivot changes.
     */
    private void createAnchorPivotCommand(EditorEntity entity, ComponentData data,
                                          String fieldKey, Vector2f oldValue, Vector2f newValue,
                                          Map<String, Object> fields) {
        Vector2f offset = FieldEditors.getVector2f(fields, "offset");
        float width = FieldEditors.getFloat(fields, "width", 100);
        float height = FieldEditors.getFloat(fields, "height", 100);
        Vector2f anchor = FieldEditors.getVector2f(fields, "anchor");
        Vector2f pivot = FieldEditors.getVector2f(fields, "pivot");

        UITransformDragCommand command;
        if (fieldKey.equals("anchor")) {
            command = UITransformDragCommand.anchor(
                    entity, data,
                    oldValue, offset,
                    newValue, offset,  // offset stays same for preset changes
                    width, height, pivot
            );
        } else {
            command = UITransformDragCommand.pivot(
                    entity, data,
                    oldValue, offset,
                    newValue, offset,
                    width, height, anchor
            );
        }

        // Execute through UndoManager
        UndoManager.getInstance().execute(command);
    }

    /**
     * Gets the texture dimensions from an entity's sprite (UIImage or UIButton).
     * @return float[2] with {width, height}, or null if no sprite found
     */
    private float[] getSpriteTextureDimensions(EditorEntity entity) {
        Object spriteObj = null;

        // Check UIImage
        ComponentData imageComp = entity.getComponentByType("UIImage");
        if (imageComp != null) {
            spriteObj = imageComp.getFields().get("sprite");
        }

        // Check UIButton
        if (spriteObj == null) {
            ComponentData buttonComp = entity.getComponentByType("UIButton");
            if (buttonComp != null) {
                spriteObj = buttonComp.getFields().get("sprite");
            }
        }

        if (spriteObj == null) return null;

        // Handle Sprite instance
        if (spriteObj instanceof com.pocket.rpg.rendering.Sprite sprite) {
            if (sprite.getTexture() != null) {
                return new float[] { sprite.getWidth(), sprite.getHeight() };
            }
        }

        // Handle Map (deserialized from JSON)
        if (spriteObj instanceof java.util.Map<?, ?> spriteMap) {
            String texturePath = getStringFromMap(spriteMap, "texturePath");
            if (texturePath == null || texturePath.isEmpty()) {
                texturePath = getStringFromMap(spriteMap, "name");
            }
            if (texturePath != null && !texturePath.isEmpty()) {
                try {
                    var sprite = Assets.load(texturePath,
                            Sprite.class);
                    if (sprite != null && sprite.getTexture() != null) {
                        // Use sprite dimensions (may be cropped from texture)
                        float w = getFloatFromMap(spriteMap, "width", sprite.getWidth());
                        float h = getFloatFromMap(spriteMap, "height", sprite.getHeight());
                        return new float[] { w, h };
                    }
                } catch (Exception e) {
                    // Try as texture
                    try {
                        var texture = Assets.load(texturePath,
                                com.pocket.rpg.rendering.Texture.class);
                        if (texture != null) {
                            return new float[] { texture.getWidth(), texture.getHeight() };
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        return null;
    }

    private String getStringFromMap(java.util.Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private float getFloatFromMap(java.util.Map<?, ?> map, String key, float defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.floatValue();
        }
        return defaultValue;
    }
}