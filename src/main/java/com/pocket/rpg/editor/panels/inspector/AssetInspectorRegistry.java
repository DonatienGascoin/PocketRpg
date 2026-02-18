package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.dialogue.DialogueEvents;
import com.pocket.rpg.dialogue.DialogueVariables;
import com.pocket.rpg.rendering.resources.Sprite;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for asset inspector renderers.
 * <p>
 * Maps asset types to their inspector renderers. Assets without a specific
 * inspector use the {@link DefaultAssetInspectorRenderer} which provides
 * preview-only functionality via {@link com.pocket.rpg.editor.assets.AssetPreviewRegistry}.
 * <p>
 * Usage:
 * <pre>
 * Object asset = Assets.load(path, type);
 * boolean hasChanges = AssetInspectorRegistry.render(asset, path, 180f);
 * if (hasChanges && userClickedSave) {
 *     AssetInspectorRegistry.save(asset, path);
 * }
 * </pre>
 */
public final class AssetInspectorRegistry {

    private static final Map<Class<?>, AssetInspectorRenderer<?>> inspectors = new HashMap<>();
    private static final DefaultAssetInspectorRenderer defaultInspector = new DefaultAssetInspectorRenderer();

    // Track current inspector for onDeselect callback
    private static AssetInspectorRenderer<?> currentInspector = null;
    private static Class<?> currentAssetType = null;

    static {
        // Register type-specific inspectors
        register(Sprite.class, new SpriteInspectorRenderer());
        register(Animation.class, new AnimationInspectorRenderer());
        register(DialogueVariables.class, new DialogueVariablesInspectorRenderer());
        register(DialogueEvents.class, new DialogueEventsInspectorRenderer());
    }

    private AssetInspectorRegistry() {
        // Utility class
    }

    /**
     * Registers an inspector for an asset type.
     *
     * @param type Asset class
     * @param inspector Inspector for that type
     * @param <T> Asset type
     */
    public static <T> void register(Class<T> type, AssetInspectorRenderer<T> inspector) {
        inspectors.put(type, inspector);
    }

    /**
     * Renders the inspector for an asset.
     *
     * @param asset The asset to inspect
     * @param assetPath Path to the asset
     * @param maxPreviewSize Maximum preview size
     * @return true if there are unsaved changes
     */
    @SuppressWarnings("unchecked")
    public static boolean render(Object asset, String assetPath, float maxPreviewSize) {
        if (asset == null) {
            return false;
        }

        AssetInspectorRenderer inspector = getInspector(asset.getClass());

        // Track inspector changes for onDeselect callback
        if (inspector != currentInspector || asset.getClass() != currentAssetType) {
            if (currentInspector != null) {
                currentInspector.onDeselect();
            }
            currentInspector = inspector;
            currentAssetType = asset.getClass();
        }

        return inspector.render(asset, assetPath, maxPreviewSize);
    }

    /**
     * Saves pending changes for an asset.
     *
     * @param asset The asset
     * @param assetPath Path to save to
     */
    @SuppressWarnings("unchecked")
    public static void save(Object asset, String assetPath) {
        if (asset == null) return;

        AssetInspectorRenderer inspector = getInspector(asset.getClass());
        inspector.save(asset, assetPath);
    }

    /**
     * Checks if an asset type has editable properties.
     *
     * @param type Asset class
     * @return true if the inspector supports editing
     */
    public static boolean hasEditableProperties(Class<?> type) {
        if (type == null) return false;
        return getInspector(type).hasEditableProperties();
    }

    /**
     * Notifies that the inspector is being cleared (no asset selected).
     */
    public static void notifyDeselect() {
        if (currentInspector != null) {
            currentInspector.onDeselect();
            currentInspector = null;
            currentAssetType = null;
        }
    }

    /**
     * Checks if the current inspector has unsaved changes.
     *
     * @return true if there are unsaved changes
     */
    public static boolean hasUnsavedChanges() {
        return currentInspector != null && currentInspector.hasUnsavedChanges();
    }

    /**
     * Delegates undo to the current inspector.
     */
    public static void undo() {
        if (currentInspector != null) {
            currentInspector.undo();
        }
    }

    /**
     * Delegates redo to the current inspector.
     */
    public static void redo() {
        if (currentInspector != null) {
            currentInspector.redo();
        }
    }

    /**
     * Gets the inspector for an asset type.
     */
    private static AssetInspectorRenderer<?> getInspector(Class<?> type) {
        // Look for exact type match
        AssetInspectorRenderer<?> inspector = inspectors.get(type);

        // Check superclass/interface matches
        if (inspector == null) {
            for (Map.Entry<Class<?>, AssetInspectorRenderer<?>> entry : inspectors.entrySet()) {
                if (entry.getKey().isAssignableFrom(type)) {
                    inspector = entry.getValue();
                    break;
                }
            }
        }

        // Fallback to default
        return inspector != null ? inspector : defaultInspector;
    }
}
