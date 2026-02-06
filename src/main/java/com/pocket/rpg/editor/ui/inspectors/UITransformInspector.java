package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.LayoutGroup;
import com.pocket.rpg.components.ui.UIGridLayoutGroup;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.ui.layout.EditorFields;
import com.pocket.rpg.editor.ui.layout.EditorLayout;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.CompoundCommand;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.editor.undo.commands.UITransformDragCommand;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiHoveredFlags;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
@InspectorFor(UITransform.class)
public class UITransformInspector extends CustomComponentInspector<UITransform> {

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

    // Preset labels (icons)
    private static final String[] PRESET_LABELS = {
            MaterialIcons.NorthWest, MaterialIcons.North, MaterialIcons.NorthEast,
            MaterialIcons.West, MaterialIcons.Adjust, MaterialIcons.East,
            MaterialIcons.SouthWest, MaterialIcons.South, MaterialIcons.SouthEast
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
    private static final float FIELD_POSITION = 150f;

    private boolean lockAspectRatio = false;
    private float lastWidth = 0;
    private float lastHeight = 0;

    @Getter
    @Setter
    private boolean compactLayout = true;

    /**
     * Creates a UITransformInspector with compact layout enabled (default).
     */
    public UITransformInspector() {
        // Default compact layout
    }

    /**
     * Creates a UITransformInspector with specified layout mode.
     *
     * @param compactLayout true for compact layout
     */
    public UITransformInspector(boolean compactLayout) {
        this.compactLayout = compactLayout;
    }

    // Track editing state for percent undo
    private final java.util.HashMap<String, Float> percentUndoStart = new java.util.HashMap<>();
    private boolean isEditingSize = false;
    private float editStartWidth;
    private float editStartHeight;
    private Vector2f editStartOffset;
    private List<ChildState> editStartChildStates;

    // Accent color for "Match Parent" buttons when ON (red)
    private static final float[] ACCENT_COLOR = {0.9f, 0.2f, 0.2f, 1.0f};
    private static final float[] ACCENT_HOVER = {1.0f, 0.3f, 0.3f, 1.0f};
    private static final float[] ACCENT_ACTIVE = {0.8f, 0.1f, 0.1f, 1.0f};

    /**
     * Stores a child's transform state for undo.
     */
    private static class ChildState {
        final EditorGameObject entity;
        final Component transform;
        final Vector2f oldOffset;
        final float oldWidth;
        final float oldHeight;

        ChildState(EditorGameObject entity, Component transform) {
            this.entity = entity;
            this.transform = transform;
            this.oldOffset = transform instanceof UITransform ut
                    ? new Vector2f(ut.getOffset())
                    : new Vector2f();
            this.oldWidth = FieldEditors.getFloat(transform, "width", 100);
            this.oldHeight = FieldEditors.getFloat(transform, "height", 100);
        }
    }

    @Override
    public boolean draw() {
        boolean changed = false;

        Vector2f anchor = FieldEditors.getVector2f(component, "anchor");
        Vector2f pivot = FieldEditors.getVector2f(component, "pivot");
        boolean isMatchingParentSize = component.isMatchingParent();

        // Detect parent layout group
        LayoutGroup parentLayout = getParentLayoutGroup();
        boolean hasParentLayout = parentLayout != null;

        // Always use side-by-side layout for anchor/pivot
        float halfWidth = ImGui.getContentRegionAvailX() / 2 - 10;

        // Anchor (left side)
        ImGui.beginGroup();
        ImGui.text(MaterialIcons.Anchor + " Anchor (" + anchor.x + " / " + anchor.y + ")");
        if (isMatchingParentSize) {
            ImGui.textDisabled("Ignored (%% sizing)");
        } else if (hasParentLayout) {
            ImGui.textDisabled("Managed by parent layout");
        } else {
            changed |= drawPresetGrid("anchor", component, anchor, entity);
        }
        ImGui.endGroup();

        ImGui.sameLine(halfWidth + 20);

        // Pivot (right side)
        ImGui.beginGroup();
        ImGui.text(MaterialIcons.CenterFocusWeak + " Pivot (" + pivot.x + " / " + pivot.y + ")");
        if (isMatchingParentSize) {
            ImGui.textDisabled("Ignored (%% sizing)");
        } else if (hasParentLayout) {
            ImGui.textDisabled("Managed by parent layout");
        } else {
            changed |= drawPresetGrid("pivot", component, pivot, entity);
        }
        ImGui.endGroup();

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Section: Offset
        // component is already typed as UITransform (via bind)

        // Check if we have a parent with UITransform
        EditorGameObject parentEntity = entity != null ? entity.getParent() : null;
        boolean hasParentUITransform = parentEntity != null &&
                parentEntity.getComponent(UITransform.class) != null;

        ImGui.text(MaterialIcons.OpenWith + " Offset");

        if (component.isFillingParent()) {
            ImGui.sameLine();
            ImGui.textDisabled("Ignored (filling parent)");
        } else if (hasParentLayout) {
            ImGui.sameLine();
            ImGui.textDisabled("Managed by parent layout");
        } else {
            ImGui.sameLine();
            if (ImGui.smallButton("R##offset")) {
                Vector2f currentOffset = component.getOffset();
                if (currentOffset.x != 0 || currentOffset.y != 0) {
                    // Push undo for reset
                    final UITransform t = this.component;
                    Vector2f oldOffset = new Vector2f(currentOffset);
                    t.setOffset(0, 0);
                    UndoManager.getInstance().push(
                            new SetterUndoCommand<>(
                                    v -> t.setOffset(v.x, v.y),
                                    oldOffset, new Vector2f(0, 0), "Reset Offset"
                            )
                    );
                    changed = true;
                }
            }

            // Capture component reference for undo lambdas
            final UITransform t = this.component;

            // X and Y on same line with automatic undo support
            ImGui.sameLine();
            ImGui.setCursorPosX(FIELD_POSITION); // Fixed position for size fields
            EditorLayout.beginHorizontal(2);
            changed |= EditorFields.floatField("X", "uiTransform.offset.x",
                    () -> t.getOffset().x,
                    v -> t.setOffset((float) v, t.getOffset().y),
                    1.0f);
            changed |= EditorFields.floatField("Y", "uiTransform.offset.y",
                    () -> t.getOffset().y,
                    v -> t.setOffset(t.getOffset().x, (float) v),
                    1.0f);
            EditorLayout.endHorizontal();
        }


        // Section: Size (with cascading resize)
        boolean layoutControlsWidth = hasParentLayout && isWidthControlledByLayout(parentLayout);
        boolean layoutControlsHeight = hasParentLayout && isHeightControlledByLayout(parentLayout);
        changed |= drawSizeSection(hasParentUITransform, hasParentLayout, layoutControlsWidth, layoutControlsHeight);

//        ImGui.spacing();
//        ImGui.separator();
//        ImGui.spacing();

        // Section: Rotation and Scale (inherited from Transform)
        changed |= drawRotationScaleSection(hasParentUITransform);

        ImGui.spacing();

        // Master "MATCH PARENT" toggle button (only if parent has UITransform)
        if (hasParentUITransform) {
            if (hasParentLayout) ImGui.beginDisabled();

            boolean anyMatchParent = component.isMatchingParent() ||
                    component.isMatchParentRotation() ||
                    component.isMatchParentScale();
            boolean allMatchParent = component.isFillingParent() &&
                    component.isMatchParentRotation() &&
                    component.isMatchParentScale();

            float buttonWidth = ImGui.getContentRegionAvailX();
            changed |= drawMasterMatchParentButton(component, anyMatchParent, allMatchParent, buttonWidth);
            if (hasParentLayout && ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
                ImGui.setTooltip("Managed by parent layout");
            }

            if (hasParentLayout) ImGui.endDisabled();
            ImGui.spacing();
        }

        return changed;
    }

    /**
     * Draws the master "MATCH PARENT" toggle button.
     * When clicked, toggles all match parent options together.
     *
     * @param anyMatchParent true if any match parent option is enabled (used for toggle logic)
     * @param allMatchParent true if ALL match parent options are enabled (used for red styling)
     */
    private boolean drawMasterMatchParentButton(UITransform component, boolean anyMatchParent, boolean allMatchParent, float buttonWidth) {
        boolean changed = false;

        // Apply red accent color only if ALL match parent options are active
        if (allMatchParent) {
            ImGui.pushStyleColor(ImGuiCol.Button, ACCENT_COLOR[0], ACCENT_COLOR[1], ACCENT_COLOR[2], ACCENT_COLOR[3]);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ACCENT_HOVER[0], ACCENT_HOVER[1], ACCENT_HOVER[2], ACCENT_HOVER[3]);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, ACCENT_ACTIVE[0], ACCENT_ACTIVE[1], ACCENT_ACTIVE[2], ACCENT_ACTIVE[3]);
        }

        if (ImGui.button(MaterialIcons.Link + (allMatchParent ? " MATCHING PARENT" : " MATCH PARENT"), buttonWidth, 0)) {
            // Capture old values
            UITransform.SizeMode oldWidthMode = component.getWidthMode();
            UITransform.SizeMode oldHeightMode = component.getHeightMode();
            float oldWidthPercent = component.getWidthPercent();
            float oldHeightPercent = component.getHeightPercent();
            boolean oldRotation = component.isMatchParentRotation();
            boolean oldScale = component.isMatchParentScale();

            // Toggle all match parent options
            boolean newState = !anyMatchParent;

            if (newState) {
                component.setMatchParent();
            } else {
                component.clearMatchParent();
            }
            component.setMatchParentRotation(newState);
            component.setMatchParentScale(newState);

            UITransform.SizeMode newWidthMode = component.getWidthMode();
            UITransform.SizeMode newHeightMode = component.getHeightMode();
            float newWidthPercent = component.getWidthPercent();
            float newHeightPercent = component.getHeightPercent();

            UndoManager.getInstance().push(
                    new CompoundCommand("Toggle Match Parent",
                            new SetterUndoCommand<>(component::setWidthMode, oldWidthMode, newWidthMode, "Change Width Mode"),
                            new SetterUndoCommand<>(component::setHeightMode, oldHeightMode, newHeightMode, "Change Height Mode"),
                            new SetterUndoCommand<>(component::setWidthPercent, oldWidthPercent, newWidthPercent, "Change Width Percent"),
                            new SetterUndoCommand<>(component::setHeightPercent, oldHeightPercent, newHeightPercent, "Change Height Percent"),
                            new SetterUndoCommand<>(component::setMatchParentRotation, oldRotation, newState, "Change Match Rotation"),
                            new SetterUndoCommand<>(component::setMatchParentScale, oldScale, newState, "Change Match Scale")
                    )
            );
            changed = true;
        }

        if (allMatchParent) {
            ImGui.popStyleColor(3);
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(anyMatchParent ?
                    "Click to disable all match parent options" :
                    "Click to enable all match parent options (size, rotation, scale)");
        }

        return changed;
    }

    /**
     * Draws a small [M] match parent toggle button with accent color when active.
     * @return true if the value changed
     */
    private boolean drawMatchParentToggle(String id, boolean isActive, Runnable onToggle) {
        boolean changed = false;

        if (isActive) {
            ImGui.pushStyleColor(ImGuiCol.Button, ACCENT_COLOR[0], ACCENT_COLOR[1], ACCENT_COLOR[2], ACCENT_COLOR[3]);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ACCENT_HOVER[0], ACCENT_HOVER[1], ACCENT_HOVER[2], ACCENT_HOVER[3]);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, ACCENT_ACTIVE[0], ACCENT_ACTIVE[1], ACCENT_ACTIVE[2], ACCENT_ACTIVE[3]);
        }

        if (ImGui.smallButton("M##" + id)) {
            onToggle.run();
            changed = true;
        }

        if (isActive) {
            ImGui.popStyleColor(3);
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(isActive ? "Match Parent: ON" : "Match Parent: OFF");
        }

        return changed;
    }

    /**
     * Draws the size section with per-axis px/% toggles and cascading resize support.
     */
    private boolean drawSizeSection(boolean hasParentUITransform, boolean hasParentLayout,
                                     boolean layoutControlsWidth, boolean layoutControlsHeight) {
        boolean changed = false;
        boolean layoutControlsBothAxes = layoutControlsWidth && layoutControlsHeight;

        ImGui.text(MaterialIcons.FitScreen + " Size");

        // Disable size buttons when parent has a layout group
        if (hasParentLayout) ImGui.beginDisabled();

        // Lock aspect ratio toggle
        ImGui.sameLine();
        if (ImGui.smallButton(lockAspectRatio ? MaterialIcons.Lock : MaterialIcons.LockOpen)) {
            lockAspectRatio = !lockAspectRatio;
            if (lockAspectRatio) {
                lastWidth = FieldEditors.getFloat(component, "width", 100);
                lastHeight = FieldEditors.getFloat(component, "height", 100);
            }
        }
        if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            if (hasParentLayout) {
                ImGui.setTooltip("Size managed by parent layout");
            } else {
                ImGui.setTooltip(lockAspectRatio ? "Aspect ratio locked" : "Lock aspect ratio");
            }
        }

        if (hasParentLayout) ImGui.endDisabled();

        // If layout controls both axes, show info text instead of editable fields
        if (layoutControlsBothAxes) {
            ImGui.sameLine();
            ImGui.textDisabled("Parent layout (" +
                    (int) component.getEffectiveWidth() + "x" +
                    (int) component.getEffectiveHeight() + ")");
            return changed;
        }

        float width = FieldEditors.getFloat(component, "width", 100);
        float height = FieldEditors.getFloat(component, "height", 100);

        // Texture size button (only if entity has a sprite) — on header line
        float[] textureDims = getSpriteTextureDimensions();
        if (textureDims != null) {
            ImGui.sameLine();
            if (ImGui.smallButton(MaterialIcons.Image + "##textureSize")) {
                float oldWidth = FieldEditors.getFloat(component, "width", 100);
                float oldHeight = FieldEditors.getFloat(component, "height", 100);

                if (oldWidth != textureDims[0] || oldHeight != textureDims[1]) {
                    startSizeEdit();
                    applySizeChange(oldWidth, oldHeight, textureDims[0], textureDims[1]);
                    commitSizeEdit();
                    changed = true;
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Match texture size (" + (int) textureDims[0] + "x" + (int) textureDims[1] + ")");
            }
        }

        // Match parent size button — on header line
        if (hasParentUITransform) {
            EditorGameObject parentEntity = entity.getParent();
            Component parentTransform = parentEntity.getComponent(UITransform.class);
            if (parentTransform != null) {
                ImGui.sameLine();
                if (ImGui.smallButton(MaterialIcons.OpenInFull + "##matchParent")) {
                    // Capture component reference for undo lambdas (survives inspector unbind)
                    final UITransform t = this.component;

                    float parentWidth = FieldEditors.getFloat(parentTransform, "width", 100);
                    float parentHeight = FieldEditors.getFloat(parentTransform, "height", 100);
                    float oldWidth = FieldEditors.getFloat(t, "width", 100);
                    float oldHeight = FieldEditors.getFloat(t, "height", 100);

                    // Capture old anchor/pivot before changes
                    Vector2f oldAnchor = new Vector2f(t.getAnchor());
                    Vector2f oldPivot = new Vector2f(t.getPivot());

                    // Size change with undo support
                    startSizeEdit();
                    applySizeChange(oldWidth, oldHeight, parentWidth, parentHeight);
                    t.setOffset(0, 0);
                    commitSizeEdit();

                    // Set anchor and pivot to top-left with undo
                    Vector2f newAnchor = new Vector2f(0, 0);
                    Vector2f newPivot = new Vector2f(0, 0);
                    t.setAnchor(0, 0);
                    t.setPivot(0, 0);

                    if (!oldAnchor.equals(newAnchor)) {
                        UndoManager.getInstance().push(
                                new SetterUndoCommand<>(
                                        v -> t.setAnchor(v.x, v.y),
                                        oldAnchor, newAnchor, "Set Anchor"
                                )
                        );
                    }
                    if (!oldPivot.equals(newPivot)) {
                        UndoManager.getInstance().push(
                                new SetterUndoCommand<>(
                                        v -> t.setPivot(v.x, v.y),
                                        oldPivot, newPivot, "Set Pivot"
                                )
                        );
                    }
                    changed = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Match parent size (" +
                            (int) FieldEditors.getFloat(parentTransform, "width", 100) + "x" +
                            (int) FieldEditors.getFloat(parentTransform, "height", 100) + ")");
                }
            }
        }

        // W/H fields aligned at FIELD_POSITION (same line as header)
        ImGui.sameLine();
        ImGui.setCursorPosX(FIELD_POSITION);
        EditorLayout.beginHorizontal(2);

        // ---- Width axis ----
        if (layoutControlsWidth) {
            EditorLayout.beforeWidget();
            ImGui.textDisabled("W: " + (int) component.getEffectiveWidth() + " (layout)");
        } else {
            changed |= drawAxisField("W", "sizeW", true, width, height, hasParentUITransform);
        }

        // ---- Height axis ----
        if (layoutControlsHeight) {
            EditorLayout.beforeWidget();
            ImGui.textDisabled("H: " + (int) component.getEffectiveHeight() + " (layout)");
        } else {
            changed |= drawAxisField("H", "sizeH", false, width, height, hasParentUITransform);
        }

        EditorLayout.endHorizontal();

        return changed;
    }

    /**
     * Draws a single axis field (W or H) with a px/% toggle button and the appropriate drag field.
     * Layout: "W" [px] [____drag____]  — matches the Offset "X [____drag____]" pattern.
     *
     * @param label           "W" or "H"
     * @param id              ImGui ID suffix
     * @param isWidth         true for width axis, false for height axis
     * @param currentWidth    current pixel width
     * @param currentHeight   current pixel height
     * @param hasParentUITransform whether a parent UITransform exists
     * @return true if value changed
     */
    private boolean drawAxisField(String label, String id, boolean isWidth,
                                   float currentWidth, float currentHeight,
                                   boolean hasParentUITransform) {
        boolean changed = false;

        UITransform.SizeMode mode = isWidth ? component.getWidthMode() : component.getHeightMode();
        boolean isPercent = mode == UITransform.SizeMode.PERCENT;

        // Position via layout system (handles sameLine for 2nd widget)
        EditorLayout.beforeWidget();

        // "W" or "H" text label
        ImGui.text(label);
        ImGui.sameLine();

        // Per-axis mode toggle button: "px" or "%"
        boolean wasPercent = isPercent;
        if (wasPercent) {
            ImGui.pushStyleColor(ImGuiCol.Button, ACCENT_COLOR[0], ACCENT_COLOR[1], ACCENT_COLOR[2], ACCENT_COLOR[3]);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ACCENT_HOVER[0], ACCENT_HOVER[1], ACCENT_HOVER[2], ACCENT_HOVER[3]);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, ACCENT_ACTIVE[0], ACCENT_ACTIVE[1], ACCENT_ACTIVE[2], ACCENT_ACTIVE[3]);
        }

        String modeLabel = wasPercent ? "%%" : "px";
        if (ImGui.smallButton(modeLabel + "##" + id)) {
            if (hasParentUITransform) {
                changed |= toggleSizeMode(isWidth);
            }
        }

        if (wasPercent) {
            ImGui.popStyleColor(3);
        }

        if (ImGui.isItemHovered()) {
            if (!hasParentUITransform) {
                ImGui.setTooltip("No parent UITransform for percentage sizing");
            } else {
                ImGui.setTooltip(isPercent ? "Switch to pixel size" : "Switch to percentage of parent");
            }
        }

        // Re-read mode after potential toggle
        mode = isWidth ? component.getWidthMode() : component.getHeightMode();
        isPercent = mode == UITransform.SizeMode.PERCENT;

        // Drag field fills remaining width in this half
        ImGui.sameLine();
        float usedWidth = ImGui.calcTextSize(label).x + ImGui.calcTextSize(modeLabel).x + 16f; // label + button + padding
        ImGui.setNextItemWidth(EditorLayout.calculateWidgetWidth(usedWidth));

        if (isPercent) {
            // PERCENT mode: drag the percentage value
            float percent = isWidth ? component.getWidthPercent() : component.getHeightPercent();
            float[] buf = {percent};

            if (ImGui.dragFloat("##" + id, buf, 0.5f, 0f, 200f, "%.1f%%%%")) {
                float newPercent = Math.max(0, buf[0]);
                if (isWidth) {
                    component.setWidthPercent(newPercent);
                } else {
                    component.setHeightPercent(newPercent);
                }
                if (lockAspectRatio) {
                    float ratio = (percent > 0) ? newPercent / percent : 1f;
                    if (isWidth) {
                        component.setHeightPercent(component.getHeightPercent() * ratio);
                    } else {
                        component.setWidthPercent(component.getWidthPercent() * ratio);
                    }
                }
                component.markDirty();
                changed = true;
            }

            // Undo for percent drag
            handlePercentUndo(id, isWidth);

            if (ImGui.isItemHovered()) {
                float resolvedPx = isWidth ? component.getEffectiveWidth() : component.getEffectiveHeight();
                ImGui.setTooltip("Resolved: " + (int) resolvedPx + " px");
            }
        } else {
            // FIXED mode: drag the pixel value (original behavior)
            float pixelValue = isWidth ? currentWidth : currentHeight;
            float[] buf = {pixelValue};

            if (ImGui.dragFloat("##" + id, buf, 1.0f, 1.0f, 10000f)) {
                if (!isEditingSize) {
                    startSizeEdit();
                }

                float newValue = Math.max(1, buf[0]);
                float newWidth = isWidth ? newValue : currentWidth;
                float newHeight = isWidth ? currentHeight : newValue;

                if (lockAspectRatio) {
                    if (isWidth && lastWidth > 0) {
                        float ratio = newValue / lastWidth;
                        newHeight = lastHeight * ratio;
                        lastWidth = newValue;
                        lastHeight = newHeight;
                    } else if (!isWidth && lastHeight > 0) {
                        float ratio = newValue / lastHeight;
                        newWidth = lastWidth * ratio;
                        lastWidth = newWidth;
                        lastHeight = newValue;
                    }
                }

                applySizeChange(editStartWidth, editStartHeight, newWidth, newHeight);
                changed = true;
            }

            if (ImGui.isItemActivated()) {
                startSizeEdit();
            }
            if (ImGui.isItemDeactivatedAfterEdit()) {
                commitSizeEdit();
            }
        }

        return changed;
    }

    /**
     * Toggles an axis between FIXED and PERCENT mode with undo support.
     * When switching to PERCENT: auto-calculates initial percent from current pixel size.
     * When switching to FIXED: stores current effective pixel size as the new fixed value.
     */
    private boolean toggleSizeMode(boolean isWidth) {
        // Capture component reference for undo lambdas (survives inspector unbind)
        final UITransform t = this.component;

        UITransform.SizeMode oldMode = isWidth ? t.getWidthMode() : t.getHeightMode();
        float oldPercent = isWidth ? t.getWidthPercent() : t.getHeightPercent();
        float oldPixelSize = isWidth ? t.getWidth() : t.getHeight();

        if (oldMode == UITransform.SizeMode.FIXED) {
            // Switching to PERCENT: calculate what percent the current pixel size is
            float parentSize = isWidth ? t.getParentWidth() : t.getParentHeight();
            float newPercent = (parentSize > 0) ? (oldPixelSize / parentSize) * 100f : 100f;

            if (isWidth) {
                t.setWidthMode(UITransform.SizeMode.PERCENT);
                t.setWidthPercent(newPercent);
            } else {
                t.setHeightMode(UITransform.SizeMode.PERCENT);
                t.setHeightPercent(newPercent);
            }

            UITransform.SizeMode newMode = UITransform.SizeMode.PERCENT;
            UndoManager.getInstance().push(
                    new CompoundCommand("Toggle " + (isWidth ? "Width" : "Height") + " to %",
                            new SetterUndoCommand<>(
                                    isWidth ? t::setWidthMode : t::setHeightMode,
                                    oldMode, newMode, "Change Size Mode"),
                            new SetterUndoCommand<>(
                                    isWidth ? t::setWidthPercent : t::setHeightPercent,
                                    oldPercent, newPercent, "Change Percent")
                    )
            );
        } else {
            // Switching to FIXED: store current effective pixel size
            float effectiveSize = isWidth ? t.getEffectiveWidth() : t.getEffectiveHeight();

            if (isWidth) {
                t.setWidthMode(UITransform.SizeMode.FIXED);
                t.setWidth(effectiveSize);
            } else {
                t.setHeightMode(UITransform.SizeMode.FIXED);
                t.setHeight(effectiveSize);
            }

            UITransform.SizeMode newMode = UITransform.SizeMode.FIXED;
            Consumer<Float> pixelSetter = isWidth
                    ? v -> t.setWidth(v)
                    : v -> t.setHeight(v);
            UndoManager.getInstance().push(
                    new CompoundCommand("Toggle " + (isWidth ? "Width" : "Height") + " to px",
                            new SetterUndoCommand<>(
                                    isWidth ? t::setWidthMode : t::setHeightMode,
                                    oldMode, newMode, "Change Size Mode"),
                            new SetterUndoCommand<>(
                                    pixelSetter,
                                    oldPixelSize, effectiveSize, "Change Pixel Size")
                    )
            );
        }

        t.markDirty();
        return true;
    }

    /**
     * Handles undo for percent drag fields.
     */
    private void handlePercentUndo(String id, boolean isWidth) {
        // Capture component reference for undo lambdas (survives inspector unbind)
        final UITransform t = this.component;
        String undoKey = "percent_" + id;
        if (ImGui.isItemActivated()) {
            percentUndoStart.put(undoKey, isWidth ? t.getWidthPercent() : t.getHeightPercent());
        }
        if (ImGui.isItemDeactivatedAfterEdit() && percentUndoStart.containsKey(undoKey)) {
            float startValue = percentUndoStart.remove(undoKey);
            float endValue = isWidth ? t.getWidthPercent() : t.getHeightPercent();
            if (startValue != endValue) {
                UndoManager.getInstance().push(
                        new SetterUndoCommand<>(
                                isWidth ? t::setWidthPercent : t::setHeightPercent,
                                startValue, endValue,
                                "Change " + (isWidth ? "Width" : "Height") + " %%"
                        )
                );
            }
        }
    }

    /**
     * Draws the rotation and scale section for UITransform.
     * Uses FieldEditors for automatic undo support.
     */
    private boolean drawRotationScaleSection(boolean hasParentUITransform) {
        boolean changed = false;

        // Capture component reference for undo lambdas
        // This ensures undo works even if inspector is later unbound
        final UITransform t = this.component;

        // Rotation (Z only for 2D)
        ImGui.text(MaterialIcons.Sync + " Rotation");

        // Match Parent toggle [M] (only if parent has UITransform)
        if (hasParentUITransform) {
            ImGui.sameLine();
            changed |= drawMatchParentToggle("##rotation", t.isMatchParentRotation(), () -> {
                boolean oldValue = t.isMatchParentRotation();
                boolean newValue = !oldValue;
                t.setMatchParentRotation(newValue);
                UndoManager.getInstance().push(
                        new SetterUndoCommand<>(t::setMatchParentRotation, oldValue, newValue, "Toggle Match Parent Rotation")
                );
            });
        }

        boolean matchingRotation = t.isMatchParentRotation();

        if (matchingRotation) {
            // Show parent value as disabled text
            ImGui.sameLine();
            UITransform parentTransform = getParentUITransform();
            float parentRotation = parentTransform != null ? parentTransform.getLocalRotation2D() : 0f;
            ImGui.textDisabled("Matching parent (" + String.format("%.1f", parentRotation) + "°)");
        } else {
            // Show reset button and editable field
            ImGui.sameLine();
            if (ImGui.smallButton("R##rotation")) {
                float oldRotation = t.getLocalRotation2D();
                if (oldRotation != 0) {
                    t.setRotation2D(0);
                    UndoManager.getInstance().push(
                            new SetterUndoCommand<>(
                                    t::setRotation2D, oldRotation, 0f, "Reset Rotation"
                            )
                    );
                    changed = true;
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Reset rotation to 0");
            }
            ImGui.sameLine();
            ImGui.setCursorPosX(ImGui.getCursorPosX() + 21); // Fixed position for size fields
            ImGui.setNextItemWidth(-1); // Use remaining width, does not work
            changed |= FieldEditors.drawFloat("##Rotation", "uiTransform.rotation",
                    t::getLocalRotation2D,
                    v -> t.setRotation2D((float) v),
                    0.5f, -360f, 360f, "%.1f°");
        }

        ImGui.spacing();

        // Scale (X, Y)
        ImGui.text(MaterialIcons.OpenInFull + " Scale");

        // Match Parent toggle [M] (only if parent has UITransform)
        if (hasParentUITransform) {
            ImGui.sameLine();
            changed |= drawMatchParentToggle("scale", t.isMatchParentScale(), () -> {
                boolean oldValue = t.isMatchParentScale();
                boolean newValue = !oldValue;
                t.setMatchParentScale(newValue);
                UndoManager.getInstance().push(
                        new SetterUndoCommand<>(t::setMatchParentScale, oldValue, newValue, "Toggle Match Parent Scale")
                );
            });
        }

        boolean matchingScale = t.isMatchParentScale();

        if (matchingScale) {
            // Show parent value as disabled text
            ImGui.sameLine();
            UITransform parentTransform = getParentUITransform();
            Vector2f parentScale = parentTransform != null ? parentTransform.getLocalScale2D() : new Vector2f(1, 1);
            ImGui.textDisabled("Matching parent (" +
                    String.format("%.2f", parentScale.x) + ", " +
                    String.format("%.2f", parentScale.y) + ")");
        } else {
            // Show reset button and editable fields
            ImGui.sameLine();
            if (ImGui.smallButton("R##scale")) {
                Vector2f oldScale = t.getLocalScale2D();
                if (oldScale.x != 1 || oldScale.y != 1) {
                    t.setScale2D(1, 1);
                    UndoManager.getInstance().push(
                            new SetterUndoCommand<>(
                                    v -> t.setScale2D(v.x, v.y),
                                    new Vector2f(oldScale), new Vector2f(1, 1), "Reset Scale"
                            )
                    );
                    changed = true;
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Reset scale to 1");
            }

            // Uniform scale button
            ImGui.sameLine();
            if (ImGui.smallButton(MaterialIcons.Link + "##uniformScale")) {
                Vector2f scale = t.getLocalScale2D();
                Vector2f oldScale = new Vector2f(scale);
                float uniform = (scale.x + scale.y) / 2f;
                if (oldScale.x != uniform || oldScale.y != uniform) {
                    t.setScale2D(uniform, uniform);
                    UndoManager.getInstance().push(
                            new SetterUndoCommand<>(
                                    v -> t.setScale2D(v.x, v.y),
                                    oldScale, new Vector2f(uniform, uniform), "Uniform Scale"
                            )
                    );
                    changed = true;
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Make scale uniform");
            }
            // X and Y on same line with automatic undo support
            ImGui.sameLine();
            ImGui.setCursorPosX(FIELD_POSITION); // Fixed position for size fields
            EditorLayout.beginHorizontal(2);
            changed |= EditorFields.floatField("X", "uiTransform.scale.x",
                    () -> t.getLocalScale2D().x,
                    v -> t.setScale2D((float) v, t.getLocalScale2D().y),
                    0.01f, 0.01f, 10f, "%.2f");
            changed |= EditorFields.floatField("Y", "uiTransform.scale.y",
                    () -> t.getLocalScale2D().y,
                    v -> t.setScale2D(t.getLocalScale2D().x, (float) v),
                    0.01f, 0.01f, 10f, "%.2f");
            EditorLayout.endHorizontal();

        }

        return changed;
    }

    private boolean drawSizePreset(String label, float targetWidth, float targetHeight) {
        if (ImGui.smallButton(label)) {
            float oldWidth = FieldEditors.getFloat(component, "width", 100);
            float oldHeight = FieldEditors.getFloat(component, "height", 100);

            if (oldWidth != targetWidth || oldHeight != targetHeight) {
                startSizeEdit();
                applySizeChange(oldWidth, oldHeight, targetWidth, targetHeight);
                commitSizeEdit();
                return true;
            }
        }
        return false;
    }

    /**
     * Starts tracking a size edit for undo.
     */
    private void startSizeEdit() {
        if (isEditingSize) return;

        isEditingSize = true;
        editStartWidth = FieldEditors.getFloat(component, "width", 100);
        editStartHeight = FieldEditors.getFloat(component, "height", 100);
        editStartOffset = new Vector2f(component.getOffset());

        // Capture child states (editor mode only — entity is null in play mode)
        editStartChildStates = new ArrayList<>();
        if (entity != null) {
            captureChildStates(entity, editStartChildStates);
        }
    }

    /**
     * Recursively captures child transform states.
     */
    private void captureChildStates(EditorGameObject parent, List<ChildState> states) {
        for (EditorGameObject child : parent.getChildren()) {
            Component childTransform = child.getComponent(UITransform.class);
            if (childTransform != null) {
                states.add(new ChildState(child, childTransform));
                captureChildStates(child, states);
            }
        }
    }

    /**
     * Applies a size change with cascading to children.
     */
    private void applySizeChange(float oldWidth, float oldHeight, float newWidth, float newHeight) {
        // Apply to parent
        ComponentReflectionUtils.setFieldValue(component, "width", newWidth);
        ComponentReflectionUtils.setFieldValue(component, "height", newHeight);

        // Calculate scale factors
        float scaleX = (oldWidth > 0) ? newWidth / oldWidth : 1f;
        float scaleY = (oldHeight > 0) ? newHeight / oldHeight : 1f;

        // Apply cascading to children (use original values from edit start)
        if (editStartChildStates != null) {
            for (ChildState state : editStartChildStates) {
                // Scale relative to original values
                float scaledWidth = state.oldWidth * scaleX;
                float scaledHeight = state.oldHeight * scaleY;
                float scaledOffsetX = state.oldOffset.x * scaleX;
                float scaledOffsetY = state.oldOffset.y * scaleY;

                ComponentReflectionUtils.setFieldValue(state.transform, "width", Math.max(1, scaledWidth));
                ComponentReflectionUtils.setFieldValue(state.transform, "height", Math.max(1, scaledHeight));
                ComponentReflectionUtils.setFieldValue(state.transform, "offset", new Vector2f(scaledOffsetX, scaledOffsetY));
            }
        }
    }

    /**
     * Commits the size edit and creates undo command.
     */
    private void commitSizeEdit() {
        if (!isEditingSize) return;

        float newWidth = FieldEditors.getFloat(component, "width", 100);
        float newHeight = FieldEditors.getFloat(component, "height", 100);
        Vector2f newOffset = new Vector2f(component.getOffset());
        Vector2f anchor = new Vector2f(component.getAnchor());
        Vector2f pivot = new Vector2f(component.getPivot());

        // Check if anything changed
        boolean hasChanges = (editStartWidth != newWidth || editStartHeight != newHeight);

        if (hasChanges && entity != null) {
            // Create undo command (editor mode only — entity is null in play mode)
            UITransformDragCommand command = UITransformDragCommand.resize(
                    entity, component,
                    editStartOffset, editStartWidth, editStartHeight,
                    newOffset, newWidth, newHeight,
                    anchor, pivot
            );

            // Add child states
            if (editStartChildStates != null) {
                for (ChildState state : editStartChildStates) {
                    Vector2f childNewOffset = state.transform instanceof UITransform childUt
                            ? new Vector2f(childUt.getOffset())
                            : new Vector2f();
                    float childNewWidth = FieldEditors.getFloat(state.transform, "width", 100);
                    float childNewHeight = FieldEditors.getFloat(state.transform, "height", 100);

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
     * Draws a 3x3 preset grid for anchor or pivot.
     */
    private boolean drawPresetGrid(String fieldKey, Component component, Vector2f current,
                                   EditorGameObject entity) {
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
                        createAnchorPivotCommand( fieldKey, oldValue, newValue);
                        ComponentReflectionUtils.setFieldValue(component, fieldKey, newValue);
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
                createAnchorPivotCommand( fieldKey, oldValue, newValue);
                current.x = xBuf[0];
                ComponentReflectionUtils.setFieldValue(component, fieldKey, new Vector2f(current));
                changed = true;
            }

            ImGui.setNextItemWidth(80);
            if (ImGui.sliderFloat("Y", yBuf, 0f, 1f)) {
                Vector2f newValue = new Vector2f(current.x, yBuf[0]);
                createAnchorPivotCommand( fieldKey, oldValue, newValue);
                current.y = yBuf[0];
                ComponentReflectionUtils.setFieldValue(component, fieldKey, new Vector2f(current));
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
    private void createAnchorPivotCommand(String fieldKey, Vector2f oldValue, Vector2f newValue) {
        // Skip undo in play mode (entity is null)
        if (entity == null) return;

        Vector2f offset = new Vector2f(component.getOffset());
        float width = FieldEditors.getFloat(component, "width", 100);
        float height = FieldEditors.getFloat(component, "height", 100);
        Vector2f anchor = new Vector2f(component.getAnchor());
        Vector2f pivot = new Vector2f(component.getPivot());

        UITransformDragCommand command;
        if (fieldKey.equals("anchor")) {
            command = UITransformDragCommand.anchor(
                    entity, component,
                    oldValue, offset,
                    newValue, offset,
                    width, height, pivot
            );
        } else {
            command = UITransformDragCommand.pivot(
                    entity, component,
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
     *
     * @return float[2] with {width, height}, or null if no sprite found
     */
    private float[] getSpriteTextureDimensions() {
        if (entity == null) return null;
        Object spriteObj = null;

        // Check UIImage
        Component imageComp = entity.getComponentByType("com.pocket.rpg.components.ui.UIImage");
        if (imageComp != null) {
            spriteObj = ComponentReflectionUtils.getFieldValue(imageComp, "sprite");
        }

        // Check UIButton
        if (spriteObj == null) {
            Component buttonComp = entity.getComponentByType("com.pocket.rpg.components.ui.UIButton");
            if (buttonComp != null) {
                spriteObj = ComponentReflectionUtils.getFieldValue(buttonComp, "sprite");
            }
        }

        if (spriteObj == null) return null;

        // Handle Sprite instance
        if (spriteObj instanceof Sprite sprite) {
            if (sprite.getTexture() != null) {
                return new float[]{sprite.getWidth(), sprite.getHeight()};
            }
        }

        return null;
    }

    /**
     * Gets the parent's UITransform component if it exists.
     *
     * @return The parent's UITransform, or null if no parent or parent has no UITransform
     */
    private UITransform getParentUITransform() {
        if (entity == null) return null;
        EditorGameObject parentEntity = entity.getParent();
        if (parentEntity == null) {
            return null;
        }
        Component parentComp = parentEntity.getComponent(UITransform.class);
        return parentComp instanceof UITransform ? (UITransform) parentComp : null;
    }

    /**
     * Checks if the parent entity has a LayoutGroup component.
     *
     * @return the parent's LayoutGroup, or null if none
     */
    private LayoutGroup getParentLayoutGroup() {
        if (entity == null) return null;
        EditorGameObject parentEntity = entity.getParent();
        if (parentEntity == null) return null;
        for (Component comp : parentEntity.getComponents()) {
            if (comp instanceof LayoutGroup lg) return lg;
        }
        return null;
    }

    private boolean isWidthControlledByLayout(LayoutGroup layout) {
        if (layout instanceof UIGridLayoutGroup) return true;
        return layout.isChildForceExpandWidth();
    }

    private boolean isHeightControlledByLayout(LayoutGroup layout) {
        if (layout instanceof UIGridLayoutGroup) return true;
        return layout.isChildForceExpandHeight();
    }
}
