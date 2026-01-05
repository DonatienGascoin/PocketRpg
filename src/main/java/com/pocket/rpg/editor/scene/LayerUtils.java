package com.pocket.rpg.editor.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utility for working with tilemap layers in EditorScene.
 * Provides common operations like sorting by z-index.
 */
public final class LayerUtils {

    private LayerUtils() {
        // Utility class
    }

    /**
     * Gets layer indices sorted by z-index (ascending).
     * Lower z-index renders first (background).
     *
     * @param scene The editor scene
     * @return List of layer indices sorted by z-index
     */
    public static List<Integer> getLayerIndicesSortedByZIndex(EditorScene scene) {
        List<int[]> layerOrder = new ArrayList<>();

        int layerCount = scene.getLayerCount();
        for (int i = 0; i < layerCount; i++) {
            TilemapLayer layer = scene.getLayer(i);
            if (layer != null) {
                layerOrder.add(new int[]{i, layer.getZIndex()});
            }
        }

        layerOrder.sort(Comparator.comparingInt(a -> a[1]));

        List<Integer> result = new ArrayList<>(layerOrder.size());
        for (int[] pair : layerOrder) {
            result.add(pair[0]);
        }
        return result;
    }

    /**
     * Gets visible layer indices sorted by z-index.
     * Uses layer's direct visibility flag (not visibility mode).
     *
     * @param scene The editor scene
     * @return List of visible layer indices sorted by z-index
     */
    public static List<Integer> getVisibleLayerIndicesSortedByZIndex(EditorScene scene) {
        List<int[]> layerOrder = new ArrayList<>();

        int layerCount = scene.getLayerCount();
        for (int i = 0; i < layerCount; i++) {
            TilemapLayer layer = scene.getLayer(i);
            if (layer != null && layer.isVisible()) {
                layerOrder.add(new int[]{i, layer.getZIndex()});
            }
        }

        layerOrder.sort(Comparator.comparingInt(a -> a[1]));

        List<Integer> result = new ArrayList<>(layerOrder.size());
        for (int[] pair : layerOrder) {
            result.add(pair[0]);
        }
        return result;
    }

    /**
     * Data class for layer rendering info.
     */
    public record LayerRenderInfo(int index, TilemapLayer layer, float opacity) {}

    /**
     * Gets layers ready for rendering with opacity based on visibility mode.
     * Used by EditorSceneRenderer which respects visibility mode.
     *
     * @param scene The editor scene
     * @return List of layer render info sorted by z-index
     */
    public static List<LayerRenderInfo> getLayersForEditorRendering(EditorScene scene) {
        List<LayerRenderInfo> result = new ArrayList<>();

        for (int index : getLayerIndicesSortedByZIndex(scene)) {
            if (!scene.isLayerVisible(index)) {
                continue;
            }

            TilemapLayer layer = scene.getLayer(index);
            if (layer == null) continue;

            float opacity = scene.getLayerOpacity(index);
            result.add(new LayerRenderInfo(index, layer, opacity));
        }

        return result;
    }

    /**
     * Gets visible layers for preview rendering.
     * Used by GamePreviewRenderer which ignores visibility mode.
     *
     * @param scene The editor scene
     * @return List of layer render info (all with opacity 1.0)
     */
    public static List<LayerRenderInfo> getLayersForPreviewRendering(EditorScene scene) {
        List<LayerRenderInfo> result = new ArrayList<>();

        for (int index : getVisibleLayerIndicesSortedByZIndex(scene)) {
            TilemapLayer layer = scene.getLayer(index);
            if (layer == null) continue;

            result.add(new LayerRenderInfo(index, layer, 1.0f));
        }

        return result;
    }
}
