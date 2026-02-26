package com.pocket.rpg.editor.events;

/**
 * Generic event requesting to open an asset in the Asset Editor panel.
 * <p>
 * Subscribed by {@code EditorUIController} to call
 * {@code assetEditorPanel.selectAssetByPath(path)}.
 *
 * @param assetPath  The asset path to open
 * @param subItemId  Optional sub-item to select within the editor (e.g. a trainer ID)
 */
public record OpenAssetEditorEvent(String assetPath, String subItemId) implements EditorEvent {

    public OpenAssetEditorEvent(String assetPath) {
        this(assetPath, null);
    }
}
