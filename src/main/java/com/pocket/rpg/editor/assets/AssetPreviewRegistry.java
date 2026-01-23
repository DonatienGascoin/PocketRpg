package com.pocket.rpg.editor.assets;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.ui.text.Font;
import imgui.ImGui;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for asset preview renderers.
 * <p>
 * Maps asset types to their preview renderers. Most assets use the default
 * {@link SpriteBasedPreviewRenderer}, while special cases have custom renderers.
 * <p>
 * Usage:
 * <pre>
 * Object asset = Assets.load(path, type);
 * AssetPreviewRegistry.render(asset, 180f);
 * </pre>
 * <p>
 * Custom renderers can be registered:
 * <pre>
 * AssetPreviewRegistry.register(MyAsset.class, new MyAssetPreviewRenderer());
 * </pre>
 */
public final class AssetPreviewRegistry {

    private static final Map<Class<?>, AssetPreviewRenderer<?>> renderers = new HashMap<>();
    private static final SpriteBasedPreviewRenderer defaultRenderer = new SpriteBasedPreviewRenderer();

    static {
        // Register custom renderers for special cases
        register(Font.class, new FontPreviewRenderer());
        register(AudioClip.class, new AudioClipPreviewRenderer());
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
     * Renders a preview of the asset.
     * <p>
     * Uses a custom renderer if registered, otherwise falls back to default
     * sprite-based rendering.
     *
     * @param asset   The asset to preview
     * @param maxSize Maximum preview size
     */
    @SuppressWarnings("unchecked")
    public static void render(Object asset, float maxSize) {
        if (asset == null) {
            ImGui.textDisabled("No asset selected");
            return;
        }

        // Look for exact type match
        AssetPreviewRenderer renderer = renderers.get(asset.getClass());

        // Check superclass/interface matches
        if (renderer == null) {
            for (Map.Entry<Class<?>, AssetPreviewRenderer<?>> entry : renderers.entrySet()) {
                if (entry.getKey().isInstance(asset)) {
                    renderer = entry.getValue();
                    break;
                }
            }
        }

        // Fallback to default
        if (renderer == null) {
            renderer = defaultRenderer;
        }

        renderer.render(asset, maxSize);
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
}
