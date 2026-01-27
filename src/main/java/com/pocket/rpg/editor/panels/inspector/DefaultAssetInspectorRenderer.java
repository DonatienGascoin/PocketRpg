package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.editor.assets.AssetPreviewRegistry;

/**
 * Default inspector renderer that delegates to {@link AssetPreviewRegistry}.
 * <p>
 * Used for asset types that don't have a custom inspector. Provides preview-only
 * functionality with no editable properties.
 */
public class DefaultAssetInspectorRenderer implements AssetInspectorRenderer<Object> {

    @Override
    public boolean render(Object asset, String assetPath, float maxPreviewSize) {
        // Delegate to the existing preview registry
        AssetPreviewRegistry.render(asset, maxPreviewSize);

        // No editable properties in default renderer
        return false;
    }

    @Override
    public boolean hasEditableProperties() {
        return false;
    }

    @Override
    public Class<Object> getAssetType() {
        return Object.class;
    }
}
