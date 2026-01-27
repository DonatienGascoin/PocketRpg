package com.pocket.rpg.animation;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores layout data for the animator node graph editor.
 * <p>
 * This data is saved separately from the AnimatorController to allow
 * different users to have different node arrangements while sharing
 * the same controller definition.
 */
public class AnimatorLayoutData {

    /**
     * Canvas pan position X.
     */
    private float viewPanX = 0.0f;

    /**
     * Canvas pan position Y.
     */
    private float viewPanY = 0.0f;

    /**
     * Canvas zoom level.
     */
    private float viewZoom = 1.0f;

    /**
     * Node positions by state name.
     */
    private Map<String, NodeLayout> nodeLayouts = new HashMap<>();

    // ========================================================================
    // ACCESSORS
    // ========================================================================

    public float getViewPanX() {
        return viewPanX;
    }

    public void setViewPanX(float viewPanX) {
        this.viewPanX = viewPanX;
    }

    public float getViewPanY() {
        return viewPanY;
    }

    public void setViewPanY(float viewPanY) {
        this.viewPanY = viewPanY;
    }

    public float getViewZoom() {
        return viewZoom;
    }

    public void setViewZoom(float viewZoom) {
        this.viewZoom = viewZoom;
    }

    public Map<String, NodeLayout> getNodeLayouts() {
        return nodeLayouts;
    }

    public void setNodeLayouts(Map<String, NodeLayout> nodeLayouts) {
        this.nodeLayouts = nodeLayouts;
    }

    // ========================================================================
    // NODE LAYOUT OPERATIONS
    // ========================================================================

    /**
     * Gets or creates a node layout for the given state name.
     */
    public NodeLayout getOrCreateNodeLayout(String stateName) {
        return nodeLayouts.computeIfAbsent(stateName, k -> new NodeLayout());
    }

    /**
     * Gets the node layout for the given state name, or null if not found.
     */
    public NodeLayout getNodeLayout(String stateName) {
        return nodeLayouts.get(stateName);
    }

    /**
     * Sets the node layout for the given state name.
     */
    public void setNodeLayout(String stateName, float x, float y) {
        NodeLayout layout = getOrCreateNodeLayout(stateName);
        layout.setX(x);
        layout.setY(y);
    }

    /**
     * Removes the node layout for the given state name.
     */
    public void removeNodeLayout(String stateName) {
        nodeLayouts.remove(stateName);
    }

    /**
     * Checks if a layout exists for the given state name.
     */
    public boolean hasNodeLayout(String stateName) {
        return nodeLayouts.containsKey(stateName);
    }

    // ========================================================================
    // INNER CLASS
    // ========================================================================

    /**
     * Layout data for a single node.
     */
    public static class NodeLayout {
        private float x = 0.0f;
        private float y = 0.0f;
        private boolean collapsed = false;

        public NodeLayout() {}

        public NodeLayout(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public float getX() {
            return x;
        }

        public void setX(float x) {
            this.x = x;
        }

        public float getY() {
            return y;
        }

        public void setY(float y) {
            this.y = y;
        }

        public boolean isCollapsed() {
            return collapsed;
        }

        public void setCollapsed(boolean collapsed) {
            this.collapsed = collapsed;
        }
    }
}
