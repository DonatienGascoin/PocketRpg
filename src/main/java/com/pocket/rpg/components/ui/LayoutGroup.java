package com.pocket.rpg.components.ui;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.components.ComponentMeta;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for UI layout groups that automatically arrange children.
 * <p>
 * Layout groups are applied by the renderer before children are traversed,
 * ensuring positions are set before any child rendering occurs.
 * <p>
 * Subclasses implement {@link #applyLayout()} to position children according
 * to their specific layout strategy (horizontal, vertical, grid).
 */
@ComponentMeta(category = "UI")
public abstract class LayoutGroup extends Component implements UITransformDriver {

    // ========================================================================
    // ENUMS
    // ========================================================================

    public enum ChildHorizontalAlignment {
        LEFT, CENTER, RIGHT
    }

    public enum ChildVerticalAlignment {
        TOP, MIDDLE, BOTTOM
    }

    /** How the layout group enforces child size on a given axis. */
    public enum ChildSizeMode {
        /** No enforcement — children use their own UITransform size. */
        NONE,
        /** All children get exactly {@code childWidth}/{@code childHeight} pixels. */
        FIXED,
        /** All children get a percentage of the content area. */
        PERCENT
    }

    // ========================================================================
    // COMMON PROPERTIES
    // ========================================================================

    @Getter @Setter
    protected float paddingLeft = 0;

    @Getter @Setter
    protected float paddingRight = 0;

    @Getter @Setter
    protected float paddingTop = 0;

    @Getter @Setter
    protected float paddingBottom = 0;

    @Getter @Setter
    protected float spacing = 0;

    /** Total height of laid-out content including all padding. Computed during applyLayout. */
    @Getter
    protected transient float contentExtentHeight = 0;

    @Getter @Setter
    protected boolean childForceExpandWidth = false;

    @Getter @Setter
    protected boolean childForceExpandHeight = false;

    @Getter @Setter
    protected ChildSizeMode childWidthMode = ChildSizeMode.NONE;

    @Getter @Setter
    protected float childWidth = 0;

    @Getter @Setter
    protected ChildSizeMode childHeightMode = ChildSizeMode.NONE;

    @Getter @Setter
    protected float childHeight = 0;

    // ========================================================================
    // LAYOUT APPLICATION
    // ========================================================================

    /**
     * Applies the layout to all children. Called by the renderer each frame
     * before children are traversed and rendered.
     * <p>
     * Implementations should set anchor and offset on each child's UITransform
     * to position them according to the layout strategy.
     */
    public abstract void applyLayout();

    // ========================================================================
    // UITransformDriver
    // ========================================================================

    /**
     * Whether this layout group will drive child width.
     * Subclasses can override (e.g. grid always drives both axes).
     */
    public boolean willDriveChildWidth() {
        return childForceExpandWidth || childWidthMode != ChildSizeMode.NONE;
    }

    /**
     * Whether this layout group will drive child height.
     * Subclasses can override (e.g. grid always drives both axes).
     */
    public boolean willDriveChildHeight() {
        return childForceExpandHeight || childHeightMode != ChildSizeMode.NONE;
    }

    @Override
    public TransformDriverInfo getChildDriverInfo(GameObject child) {
        return new TransformDriverInfo(false, willDriveChildWidth(), willDriveChildHeight(), true, "parent layout");
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /**
     * Gets the UITransform on this layout group's GameObject.
     *
     * @return UITransform or null if not present
     */
    protected UITransform getOwnTransform() {
        if (gameObject == null) return null;
        return gameObject.getComponent(UITransform.class);
    }

    /**
     * Returns enabled children that have a UITransform component.
     * Skips disabled GameObjects and children without UITransform.
     *
     * @return List of layout-eligible children (never null)
     */
    protected List<GameObject> getLayoutChildren() {
        List<GameObject> result = new ArrayList<>();
        if (gameObject == null) return result;

        for (GameObject child : gameObject.getChildren()) {
            if (child.isEnabled() && child.getComponent(UITransform.class) != null) {
                result.add(child);
            }
        }
        return result;
    }
}
