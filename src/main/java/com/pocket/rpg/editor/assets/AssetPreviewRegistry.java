package com.pocket.rpg.editor.assets;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.ui.text.Font;
import imgui.ImGui;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for asset preview and inspector renderers.
 * <p>
 * Maps asset types to their {@link AssetPreviewRenderer}. Most assets use the default
 * {@link SpriteBasedPreviewRenderer}, while special cases have custom renderers.
 * <p>
 * Usage:
 * <pre>
 * // Compact thumbnail (picker popup, browser grid)
 * AssetPreviewRegistry.renderPreview(asset, 180f);
 *
 * // Detailed inspector (inspector panel)
 * AssetPreviewRegistry.renderInspector(asset, assetPath, 200f);
 * </pre>
 */
public final class AssetPreviewRegistry {

    private static final Map<Class<?>, AssetPreviewRenderer<?>> renderers = new HashMap<>();
    private static final SpriteBasedPreviewRenderer defaultRenderer = new SpriteBasedPreviewRenderer();

    static {
        register(Font.class, new FontPreviewRenderer());
        register(AudioClip.class, new AudioClipPreviewRenderer());
        register(Sprite.class, new SpritePreviewRenderer());
        register(Animation.class, new AnimationPreviewRenderer());
    }

    private AssetPreviewRegistry() {
        // Utility class
    }

    /**
     * Registers a custom renderer for an asset type.
     *
     * @param type     Asset class
     * @param renderer Renderer for that type
     * @param <T>      Asset type
     */
    public static <T> void register(Class<T> type, AssetPreviewRenderer<T> renderer) {
        renderers.put(type, renderer);
    }

    /**
     * Renders a compact preview/thumbnail of the asset.
     *
     * @param asset   The asset to preview
     * @param maxSize Maximum preview size
     */
    @SuppressWarnings("unchecked")
    public static void renderPreview(Object asset, float maxSize) {
        if (asset == null) {
            ImGui.textDisabled("No asset selected");
            return;
        }

        AssetPreviewRenderer renderer = findRenderer(asset.getClass());
        renderer.renderPreview(asset, maxSize);
    }

    /**
     * Renders a detailed inspector view of the asset.
     * <p>
     * Falls back to {@link #renderPreview} for types without a custom inspector.
     *
     * @param asset     The asset to inspect
     * @param assetPath Path to the asset (for metadata loading)
     * @param maxSize   Maximum preview size
     */
    @SuppressWarnings("unchecked")
    public static void renderInspector(Object asset, String assetPath, float maxSize) {
        if (asset == null) {
            ImGui.textDisabled("No asset selected");
            return;
        }

        AssetPreviewRenderer renderer = findRenderer(asset.getClass());
        renderer.renderInspector(asset, assetPath, maxSize);
    }

    /**
     * Checks if a custom renderer is registered for a type.
     *
     * @param type Asset class
     * @return true if a custom renderer exists
     */
    public static boolean hasCustomRenderer(Class<?> type) {
        if (renderers.containsKey(type)) {
            return true;
        }
        for (Class<?> registered : renderers.keySet()) {
            if (registered.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }

    private static AssetPreviewRenderer<?> findRenderer(Class<?> assetClass) {
        // Look for exact type match
        AssetPreviewRenderer<?> renderer = renderers.get(assetClass);

        // Check superclass/interface matches
        if (renderer == null) {
            for (Map.Entry<Class<?>, AssetPreviewRenderer<?>> entry : renderers.entrySet()) {
                if (entry.getKey().isInstance(assetClass)) {
                    renderer = entry.getValue();
                    break;
                }
            }
        }

        return renderer != null ? renderer : defaultRenderer;
    }
}
