package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.LayoutGroup;
import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UIGridLayoutGroup;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyItem;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.ui.fields.FieldEditorUtils;
import com.pocket.rpg.editor.ui.fields.FieldUndoTracker;
import com.pocket.rpg.editor.ui.fields.PrimitiveEditors;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.CompoundCommand;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.editor.undo.commands.UITransformDragCommand;
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

    private boolean isEditingSize = false;
    private float editStartWidth;
    private float editStartHeight;
    private Vector2f editStartOffset;
    private List<ChildState> editStartChildStates;

    /**
     * Stores a child's transform state for undo.
     */
    private static class ChildState {
        // May be a RuntimeGameObjectAdapter in play mode; only used for undo
        // when it is an EditorGameObject.
        final HierarchyItem entity;
        final Component transform;
        final Vector2f oldOffset;
        final float oldWidth;
        final float oldHeight;

        ChildState(HierarchyItem entity, Component transform) {
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
        // Canvas-owned UITransform: read-only display with game resolution
        if (entity.getComponent(UICanvas.class) != null) {
            GameConfig gameConfig = ConfigLoader.getConfig(ConfigLoader.ConfigType.GAME);
            ImGui.beginDisabled();
            ImGui.text(String.format("Managed by UICanvas  (%d x %d)",
                    gameConfig.getGameWidth(), gameConfig.getGameHeight()));
            ImGui.endDisabled();
            return false;
        }

        boolean changed = false;

        Vector2f anchor = FieldEditors.getVector2f(component, "anchor");
        Vector2f pivot = FieldEditors.getVector2f(component, "pivot");
        boolean isMatchingParentSize = component.isFillingParent();

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
        HierarchyItem parentEntity = entity.getHierarchyParent();
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
            ImGui.setCursorPosX(FIELD_POSITION);
            float flexWidth = calculateFlexWidth(2);
            changed |= PrimitiveEditors.drawFloatInline("X", "uiTransform.offset.x",
                    () -> t.getOffset().x,
                    v -> t.setOffset((float) v, t.getOffset().y),
                    1.0f, flexWidth);
            ImGui.sameLine();
            changed |= PrimitiveEditors.drawFloatInline("Y", "uiTransform.offset.y",
                    () -> t.getOffset().y,
                    v -> t.setOffset(t.getOffset().x, (float) v),
                    1.0f, flexWidth);
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

        // Master "MATCH PARENT" toggle button (always visible; disabled if no parent UITransform)
        if (!hasParentUITransform) ImGui.beginDisabled();
        if (hasParentLayout) ImGui.beginDisabled();

        boolean anyMatchParent = component.isMatchingParent() ||
                component.isMatchParentRotation() ||
                component.isMatchParentScale();
        boolean allMatchParent = component.isFillingParent() &&
                component.isMatchParentRotation() &&
                component.isMatchParentScale();

        float buttonWidth = ImGui.getContentRegionAvailX();
        changed |= drawMasterMatchParentButton(component, anyMatchParent, allMatchParent, buttonWidth);

        if (hasParentLayout) ImGui.endDisabled();
        if (!hasParentUITransform) ImGui.endDisabled();
        if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            if (!hasParentUITransform) {
                ImGui.setTooltip("No parent UITransform");
            } else if (hasParentLayout) {
                ImGui.setTooltip("Managed by parent layout");
            }
        }
        ImGui.spacing();

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

        String label = MaterialIcons.Link + (allMatchParent ? " MATCHING PARENT" : " MATCH PARENT");
        if (FieldEditorUtils.accentButton(allMatchParent, label, buttonWidth)) {
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

        if (FieldEditorUtils.accentButton(isActive, "M##" + id)) {
            onToggle.run();
            changed = true;
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

        // [M] toggle: set both axes to 100% of parent (always visible; disabled if no parent)
        if (!hasParentUITransform) ImGui.beginDisabled();
        if (hasParentLayout) ImGui.beginDisabled();

        boolean isBothPercent100 =
                component.getWidthMode() == UITransform.SizeMode.PERCENT
                && component.getHeightMode() == UITransform.SizeMode.PERCENT
                && component.getWidthPercent() == 100f
                && component.getHeightPercent() == 100f;

        ImGui.sameLine();
        if (FieldEditorUtils.accentButton(isBothPercent100, "M##sizeMatchParent")) {
            changed |= toggleMatchParentSize(isBothPercent100);
        }

        if (hasParentLayout) ImGui.endDisabled();
        if (!hasParentUITransform) ImGui.endDisabled();
        if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            if (!hasParentUITransform) {
                ImGui.setTooltip("No parent UITransform");
            } else if (hasParentLayout) {
                ImGui.setTooltip("Size managed by parent layout");
            } else {
                ImGui.setTooltip(isBothPercent100
                        ? "Match Parent Size: ON (click to switch to pixel)"
                        : "Set both axes to 100% of parent");
            }
        }

        // Match parent size button (pixel-based) — on header line
        // Always visible; disabled if no parent UITransform
        HierarchyItem matchParent = entity.getHierarchyParent();
        Component parentTransform = hasParentUITransform && matchParent != null
                ? matchParent.getComponent(UITransform.class) : null;
        ImGui.sameLine();
        if (!hasParentUITransform) ImGui.beginDisabled();
        if (ImGui.smallButton(MaterialIcons.OpenInFull + "##matchParent")) {
            if (parentTransform != null) {
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
        }
        if (!hasParentUITransform) ImGui.endDisabled();
        if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            if (!hasParentUITransform) {
                ImGui.setTooltip("No parent UITransform");
            } else if (parentTransform != null) {
                ImGui.setTooltip("Match parent size (" +
                        (int) FieldEditors.getFloat(parentTransform, "width", 100) + "x" +
                        (int) FieldEditors.getFloat(parentTransform, "height", 100) + ")");
            }
        }

        // W/H fields aligned at FIELD_POSITION (same line as header)
        ImGui.sameLine();
        ImGui.setCursorPosX(FIELD_POSITION);

        // Calculate drag field width for each axis (label + px/% button + drag field)
        float usedPerField = ImGui.calcTextSize("W").x + ImGui.calcTextSize("px").x + 16f;
        float sizeFieldWidth = calculateFlexWidth(2, usedPerField);

        // ---- Width axis ----
        if (layoutControlsWidth) {
            ImGui.textDisabled("W: " + (int) component.getEffectiveWidth() + " (layout)");
        } else {
            changed |= drawAxisField("W", "sizeW", true, width, height, hasParentUITransform, sizeFieldWidth);
        }

        // ---- Height axis ----
        ImGui.sameLine();
        if (layoutControlsHeight) {
            ImGui.textDisabled("H: " + (int) component.getEffectiveHeight() + " (layout)");
        } else {
            changed |= drawAxisField("H", "sizeH", false, width, height, hasParentUITransform, sizeFieldWidth);
        }

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
     * @param fieldWidth      width for the drag field
     * @return true if value changed
     */
    private boolean drawAxisField(String label, String id, boolean isWidth,
                                   float currentWidth, float currentHeight,
                                   boolean hasParentUITransform, float fieldWidth) {
        boolean changed = false;

        UITransform.SizeMode mode = isWidth ? component.getWidthMode() : component.getHeightMode();
        boolean isPercent = mode == UITransform.SizeMode.PERCENT;

        // "W" or "H" text label
        ImGui.text(label);
        ImGui.sameLine();

        // Per-axis mode toggle button: "px" or "%"
        String modeLabel = isPercent ? "%" : "px";
        if (FieldEditorUtils.accentButton(isPercent, modeLabel + "##" + id)) {
            if (hasParentUITransform) {
                changed |= toggleSizeMode(isWidth);
            }
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
        ImGui.setNextItemWidth(fieldWidth);

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
            final UITransform t = this.component;
            float currentPercent = isWidth ? t.getWidthPercent() : t.getHeightPercent();
            Consumer<Float> percentSetter = isWidth ? t::setWidthPercent : t::setHeightPercent;
            FieldUndoTracker.track(
                    "percent_" + id,
                    currentPercent,
                    percentSetter,
                    "Change " + (isWidth ? "Width" : "Height") + " %%"
            );

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
     * Toggles both axes between 100% parent match and FIXED mode.
     * When enabling: sets both axes to PERCENT mode at 100%.
     * When disabling: switches back to FIXED mode, keeping current effective pixel sizes.
     */
    private boolean toggleMatchParentSize(boolean isCurrentlyMatching) {
        final UITransform t = this.component;

        UITransform.SizeMode oldWidthMode = t.getWidthMode();
        UITransform.SizeMode oldHeightMode = t.getHeightMode();
        float oldWidthPercent = t.getWidthPercent();
        float oldHeightPercent = t.getHeightPercent();
        float oldWidth = t.getWidth();
        float oldHeight = t.getHeight();

        if (isCurrentlyMatching) {
            // Switch back to FIXED: keep current effective pixel sizes
            float effectiveWidth = t.getEffectiveWidth();
            float effectiveHeight = t.getEffectiveHeight();

            t.setWidthMode(UITransform.SizeMode.FIXED);
            t.setHeightMode(UITransform.SizeMode.FIXED);
            t.setWidth(effectiveWidth);
            t.setHeight(effectiveHeight);

            UndoManager.getInstance().push(
                    new CompoundCommand("Disable Match Parent Size",
                            new SetterUndoCommand<>(t::setWidthMode, oldWidthMode, UITransform.SizeMode.FIXED, "Width Mode"),
                            new SetterUndoCommand<>(t::setHeightMode, oldHeightMode, UITransform.SizeMode.FIXED, "Height Mode"),
                            new SetterUndoCommand<>(v -> t.setWidth(v), oldWidth, effectiveWidth, "Width"),
                            new SetterUndoCommand<>(v -> t.setHeight(v), oldHeight, effectiveHeight, "Height")
                    )
            );
        } else {
            // Enable: set both axes to PERCENT mode at 100%
            t.setWidthMode(UITransform.SizeMode.PERCENT);
            t.setHeightMode(UITransform.SizeMode.PERCENT);
            t.setWidthPercent(100f);
            t.setHeightPercent(100f);

            UndoManager.getInstance().push(
                    new CompoundCommand("Match Parent Size 100%",
                            new SetterUndoCommand<>(t::setWidthMode, oldWidthMode, UITransform.SizeMode.PERCENT, "Width Mode"),
                            new SetterUndoCommand<>(t::setHeightMode, oldHeightMode, UITransform.SizeMode.PERCENT, "Height Mode"),
                            new SetterUndoCommand<>(t::setWidthPercent, oldWidthPercent, 100f, "Width Percent"),
                            new SetterUndoCommand<>(t::setHeightPercent, oldHeightPercent, 100f, "Height Percent")
                    )
            );
        }

        t.markDirty();
        return true;
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

        // Match Parent toggle [M] (always visible; disabled if no parent UITransform)
        ImGui.sameLine();
        if (!hasParentUITransform) ImGui.beginDisabled();
        changed |= drawMatchParentToggle("##rotation", t.isMatchParentRotation(), () -> {
            boolean oldValue = t.isMatchParentRotation();
            boolean newValue = !oldValue;
            t.setMatchParentRotation(newValue);
            UndoManager.getInstance().push(
                    new SetterUndoCommand<>(t::setMatchParentRotation, oldValue, newValue, "Toggle Match Parent Rotation")
            );
        });
        if (!hasParentUITransform) ImGui.endDisabled();
        if (!hasParentUITransform && ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip("No parent UITransform");
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
            ImGui.setCursorPosX(ImGui.getCursorPosX() + 21); // Fixed position for size field
            ImGui.setNextItemWidth(-1);
            float rotValue = t.getLocalRotation2D();
            float[] rotBuf = {rotValue};
            if (ImGui.dragFloat("##rotation_drag", rotBuf, 0.5f, -360f, 360f, "%.1f°")) {
                t.setRotation2D(rotBuf[0]);
                changed = true;
            }
            FieldUndoTracker.track(
                    FieldUndoTracker.undoKey(t, "rotation"),
                    rotValue,
                    t::setRotation2D,
                    "Change Rotation"
            );
        }

        ImGui.spacing();

        // Scale (X, Y)
        ImGui.text(MaterialIcons.OpenInFull + " Scale");

        // Match Parent toggle [M] (always visible; disabled if no parent UITransform)
        ImGui.sameLine();
        if (!hasParentUITransform) ImGui.beginDisabled();
        changed |= drawMatchParentToggle("scale", t.isMatchParentScale(), () -> {
            boolean oldValue = t.isMatchParentScale();
            boolean newValue = !oldValue;
            t.setMatchParentScale(newValue);
            UndoManager.getInstance().push(
                    new SetterUndoCommand<>(t::setMatchParentScale, oldValue, newValue, "Toggle Match Parent Scale")
            );
        });
        if (!hasParentUITransform) ImGui.endDisabled();
        if (!hasParentUITransform && ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip("No parent UITransform");
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
            ImGui.setCursorPosX(FIELD_POSITION);
            float scaleFlexWidth = calculateFlexWidth(2);
            changed |= PrimitiveEditors.drawFloatInline("X", "uiTransform.scale.x",
                    () -> t.getLocalScale2D().x,
                    v -> t.setScale2D((float) v, t.getLocalScale2D().y),
                    0.01f, 0.01f, 10f, "%.2f", scaleFlexWidth);
            ImGui.sameLine();
            changed |= PrimitiveEditors.drawFloatInline("Y", "uiTransform.scale.y",
                    () -> t.getLocalScale2D().y,
                    v -> t.setScale2D(t.getLocalScale2D().x, (float) v),
                    0.01f, 0.01f, 10f, "%.2f", scaleFlexWidth);

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

        // Capture child states for cascading resize
        editStartChildStates = new ArrayList<>();
        captureChildStates(entity, editStartChildStates);
    }

    /**
     * Recursively captures child transform states.
     */
    private void captureChildStates(HierarchyItem parent, List<ChildState> states) {
        for (HierarchyItem child : parent.getHierarchyChildren()) {
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

        if (hasChanges && editorEntity() != null) {
            // Create undo command
            UITransformDragCommand command = UITransformDragCommand.resize(
                    editorEntity(), component,
                    editStartOffset, editStartWidth, editStartHeight,
                    newOffset, newWidth, newHeight,
                    anchor, pivot
            );

            // Add child states (only EditorGameObject children get undo tracking)
            if (editStartChildStates != null) {
                for (ChildState state : editStartChildStates) {
                    if (!(state.entity instanceof EditorGameObject childEgo)) continue;

                    Vector2f childNewOffset = state.transform instanceof UITransform childUt
                            ? new Vector2f(childUt.getOffset())
                            : new Vector2f();
                    float childNewWidth = FieldEditors.getFloat(state.transform, "width", 100);
                    float childNewHeight = FieldEditors.getFloat(state.transform, "height", 100);

                    UITransformDragCommand.ChildTransformState childState =
                            new UITransformDragCommand.ChildTransformState(
                                    childEgo, state.transform,
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
                                   HierarchyItem entity) {
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
        if (editorEntity() == null) return;

        Vector2f offset = new Vector2f(component.getOffset());
        float width = FieldEditors.getFloat(component, "width", 100);
        float height = FieldEditors.getFloat(component, "height", 100);
        Vector2f anchor = new Vector2f(component.getAnchor());
        Vector2f pivot = new Vector2f(component.getPivot());

        UITransformDragCommand command;
        if (fieldKey.equals("anchor")) {
            command = UITransformDragCommand.anchor(
                    editorEntity(), component,
                    oldValue, offset,
                    newValue, offset,
                    width, height, pivot
            );
        } else {
            command = UITransformDragCommand.pivot(
                    editorEntity(), component,
                    oldValue, offset,
                    newValue, offset,
                    width, height, anchor
            );
        }

        // Execute through UndoManager
        UndoManager.getInstance().execute(command);
    }

    /**
     * Gets the parent's UITransform component if it exists.
     *
     * @return The parent's UITransform, or null if no parent or parent has no UITransform
     */
    private UITransform getParentUITransform() {
        HierarchyItem parent = entity.getHierarchyParent();
        return parent != null ? parent.getComponent(UITransform.class) : null;
    }

    /**
     * Checks if the parent entity has a LayoutGroup component.
     *
     * @return the parent's LayoutGroup, or null if none
     */
    private LayoutGroup getParentLayoutGroup() {
        HierarchyItem parentEntity = entity.getHierarchyParent();
        if (parentEntity == null) return null;
        for (Component comp : parentEntity.getAllComponents()) {
            if (comp instanceof LayoutGroup lg) return lg;
        }
        return null;
    }

    /**
     * Calculates width per drag field for a horizontal N-field layout.
     * Uses a single-char label width (suitable for "X [___] Y [___]" patterns).
     */
    private float calculateFlexWidth(int fieldCount) {
        return calculateFlexWidth(fieldCount, ImGui.calcTextSize("X").x);
    }

    /**
     * Calculates width per drag field for a horizontal N-field layout.
     *
     * @param fieldCount        Number of fields in the row
     * @param perFieldLabelWidth Width consumed by label + extras per field
     */
    private float calculateFlexWidth(int fieldCount, float perFieldLabelWidth) {
        float available = ImGui.getContentRegionAvailX();
        float totalLabels = perFieldLabelWidth * fieldCount;
        float spacing = 8f * (fieldCount - 1);
        float padding = 4f * fieldCount;
        float margin = 8f;
        return (available - totalLabels - spacing - padding - margin) / fieldCount;
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
