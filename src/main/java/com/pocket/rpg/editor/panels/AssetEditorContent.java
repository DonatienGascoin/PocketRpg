package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.shortcut.KeyboardLayout;
import com.pocket.rpg.editor.shortcut.ShortcutAction;

import java.util.List;

/**
 * Pluggable content interface for the unified AssetEditorPanel.
 * Each asset type provides its own content implementation; the shell
 * (AssetEditorPanel) owns all shared infrastructure (toolbar, undo stacks,
 * dirty tracking, shortcuts, hamburger sidebar).
 * <p>
 * Content implementations own their full internal layout (columns, tabs, etc.).
 */
public interface AssetEditorContent {

    /**
     * Renders the main content area. Called every frame when an asset is loaded.
     * UndoManager is already redirected to the panel's stacks before this call.
     */
    void render();

    /**
     * Called when a new asset is loaded into the editor.
     *
     * @param path  Asset path (relative to asset root)
     * @param asset The loaded asset object
     * @param shell Shell API for callbacks (markDirty, showStatus, etc.)
     */
    void onAssetLoaded(String path, Object asset, AssetEditorShell shell);

    /**
     * Called when the current asset is unloaded (e.g., switching to a different type).
     */
    void onAssetUnloaded();

    /**
     * Whether this content handles saving itself (instead of default Assets.persist).
     */
    default boolean hasCustomSave() {
        return false;
    }

    /**
     * Custom save implementation. Only called if {@link #hasCustomSave()} returns true.
     *
     * @param path Asset path to save to
     */
    default void customSave(String path) {
    }

    /**
     * Renders extra toolbar buttons after the standard Save/Undo/Redo buttons.
     * Called every frame when an asset is loaded.
     */
    default void renderToolbarExtras() {
    }

    /**
     * Renders any popups owned by this content (e.g., confirm dialogs).
     * Called every frame outside the main content child window.
     */
    default void renderPopups() {
    }

    /**
     * Provides extra shortcuts specific to this content type.
     * These are merged with the shell's standard shortcuts.
     *
     * @param layout Keyboard layout (affects binding choices)
     * @return Extra shortcut actions, or empty list
     */
    default List<ShortcutAction> provideExtraShortcuts(KeyboardLayout layout) {
        return List.of();
    }

    /**
     * Returns an annotation string for an asset path in the sidebar list.
     * Used for validation warnings, status icons, etc.
     *
     * @param path Asset path to annotate
     * @return Annotation string (e.g., warning icon), or null for none
     */
    default String getAssetAnnotation(String path) {
        return null;
    }

    /**
     * Called after an undo or redo operation completes on the panel's stacks.
     * Use to re-resolve stale object references (e.g., selected items that were
     * replaced by snapshot restoration).
     */
    default void onAfterUndoRedo() {
    }

    /**
     * Called when the user presses the panel-level "New" shortcut (Ctrl+N).
     * Override to open a new-asset dialog for this content type.
     */
    default void onNewRequested() {
    }

    /**
     * Called once when the content is first created. Use for one-time setup.
     */
    default void initialize() {
    }

    /**
     * Called when the content is being destroyed. Use for cleanup.
     */
    default void destroy() {
    }

    /** Returns creation metadata, or null if this content doesn't support creating new assets. */
    default AssetCreationInfo getCreationInfo() { return null; }

    /**
     * Returns the asset class this content handles.
     * Used by the registry to match asset types to content implementations.
     */
    Class<?> getAssetClass();
}
