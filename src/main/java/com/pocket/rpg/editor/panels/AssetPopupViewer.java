package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * A standalone floating window that hosts an {@link AssetEditorContent} instance
 * for viewing/editing an asset without navigating the main Asset Editor away
 * from its current asset.
 * <p>
 * Used for Dialogue Variables/Events viewers, and potentially other secondary
 * asset views in the future.
 */
public class AssetPopupViewer {

    private final AssetEditorContentRegistry contentRegistry;
    private final String windowId;
    private String assetPath;
    private Object loadedAsset;
    private AssetEditorContent content;
    private final Deque<EditorCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditorCommand> redoStack = new ArrayDeque<>();
    private boolean dirty;
    private boolean open;
    private boolean pendingFocus;
    private final ImBoolean windowOpen = new ImBoolean(true);
    private Consumer<String> statusCallback;

    // Unsaved changes guard for popup close
    private boolean showUnsavedPopup;
    private Runnable pendingCloseAction;

    private final AssetEditorShell shell = new AssetEditorShell() {
        @Override public void markDirty() { dirty = true; }
        @Override public boolean isDirty() { return dirty; }
        @Override public String getEditingPath() { return assetPath; }
        @Override public Deque<EditorCommand> getUndoStack() { return undoStack; }
        @Override public Deque<EditorCommand> getRedoStack() { return redoStack; }
        @Override public void showStatus(String msg) { if (statusCallback != null) statusCallback.accept(msg); }
        @Override public void requestSidebarRefresh() { /* no sidebar */ }
        @Override public void selectAssetByPath(String path) { /* no-op in popup */ }
        @Override public void clearEditingAsset() { close(); }
        @Override public String createAsset(String name, Object def) { return null; }
        @Override public void requestDirtyGuard(Runnable after) {
            if (!dirty) { after.run(); return; }
            pendingCloseAction = after;
            showUnsavedPopup = true;
        }
        @Override public EditorSelectionManager getSelectionManager() { return null; }
    };

    public AssetPopupViewer(AssetEditorContentRegistry contentRegistry, String windowId) {
        this.contentRegistry = contentRegistry;
        this.windowId = windowId;
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    public void open(String path, Class<?> type) {
        loadedAsset = Assets.load(path, type);
        if (loadedAsset == null) return;

        assetPath = path;

        if (content != null) {
            content.onAssetUnloaded();
            content.destroy();
        }
        content = contentRegistry.createContent(type);
        if (content != null) {
            content.initialize();
            content.onAssetLoaded(path, loadedAsset, shell);
        }

        dirty = false;
        undoStack.clear();
        redoStack.clear();
        open = true;
        windowOpen.set(true);
        pendingFocus = true;
    }

    public void render() {
        if (!open) return;

        String displayName = extractFilename(assetPath);
        String title = displayName + (dirty ? " *" : "")
                + "###popupViewer_" + windowId;

        ImGui.setNextWindowSize(500, 400, ImGuiCond.FirstUseEver);
        if (pendingFocus) {
            ImGui.setNextWindowFocus();
            pendingFocus = false;
        }

        if (ImGui.begin(title, windowOpen,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse)) {

            renderMiniToolbar(displayName);
            ImGui.separator();

            if (content != null) {
                UndoManager um = UndoManager.getInstance();
                um.pushTarget(undoStack, redoStack);
                try {
                    content.render();
                } finally {
                    um.popTarget();
                }
            }
        }

        if (content != null) content.renderPopups();
        renderUnsavedChangesPopup();

        ImGui.end();

        // Handle window close via X button
        if (!windowOpen.get()) {
            if (dirty) {
                windowOpen.set(true);
                pendingCloseAction = this::close;
                showUnsavedPopup = true;
            } else {
                close();
            }
        }
    }

    private void renderMiniToolbar(String displayName) {
        if (dirty) {
            EditorColors.textColored(EditorColors.WARNING, displayName + " *");
        } else {
            ImGui.text(displayName);
        }

        // Right-aligned: Undo, Redo, Save
        float spacing = ImGui.getStyle().getItemSpacingX();
        float framePadX = ImGui.getStyle().getFramePaddingX();
        float undoW = ImGui.calcTextSize(MaterialIcons.Undo).x + framePadX * 2;
        float redoW = ImGui.calcTextSize(MaterialIcons.Redo).x + framePadX * 2;
        float saveW = ImGui.calcTextSize(MaterialIcons.Save).x + framePadX * 2;
        float rightWidth = undoW + spacing + redoW + spacing + saveW;
        float rightEdge = ImGui.getCursorPosX() + ImGui.getContentRegionAvailX();
        ImGui.sameLine(rightEdge - rightWidth);

        // Undo
        boolean canUndo = !undoStack.isEmpty();
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Undo + "##popupUndo_" + windowId)) {
            undo();
        }
        if (!canUndo) ImGui.endDisabled();

        ImGui.sameLine();

        // Redo
        boolean canRedo = !redoStack.isEmpty();
        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Redo + "##popupRedo_" + windowId)) {
            redo();
        }
        if (!canRedo) ImGui.endDisabled();

        ImGui.sameLine();

        // Save
        boolean canSave = dirty;
        if (canSave) {
            EditorColors.pushWarningButton();
        } else {
            ImGui.beginDisabled();
        }
        if (ImGui.button(MaterialIcons.Save + "##popupSave_" + windowId)) {
            save();
        }
        if (canSave) {
            EditorColors.popButtonColors();
        } else {
            ImGui.endDisabled();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Save changes");
        }
    }

    private void renderUnsavedChangesPopup() {
        if (showUnsavedPopup) {
            ImGui.openPopup("Unsaved Changes##popupViewer_" + windowId);
            showUnsavedPopup = false;
        }

        if (ImGui.beginPopupModal("Unsaved Changes##popupViewer_" + windowId,
                new ImBoolean(true), ImGuiWindowFlags.AlwaysAutoResize)) {
            String name = assetPath != null ? extractFilename(assetPath) : "current asset";
            ImGui.text("You have unsaved changes to \"" + name + "\".");
            ImGui.text("What would you like to do?");
            ImGui.spacing();

            if (ImGui.button("Save & Continue", 130, 0)) {
                save();
                ImGui.closeCurrentPopup();
                if (!dirty && pendingCloseAction != null) {
                    Runnable action = pendingCloseAction;
                    pendingCloseAction = null;
                    action.run();
                }
            }
            ImGui.sameLine();
            if (ImGui.button("Discard & Continue", 140, 0)) {
                if (assetPath != null) Assets.reload(assetPath);
                dirty = false;
                ImGui.closeCurrentPopup();
                if (pendingCloseAction != null) {
                    Runnable action = pendingCloseAction;
                    pendingCloseAction = null;
                    action.run();
                }
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 130, 0)) {
                pendingCloseAction = null;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void save() {
        if (loadedAsset == null || assetPath == null || !dirty) return;
        try {
            if (content != null && content.hasCustomSave()) {
                content.customSave(assetPath);
            } else {
                Assets.persist(loadedAsset, assetPath);
            }
            dirty = false;
            shell.showStatus("Saved: " + extractFilename(assetPath));
        } catch (Exception e) {
            System.err.println("[AssetPopupViewer] Failed to save: " + e.getMessage());
            shell.showStatus("Error saving: " + e.getMessage());
        }
    }

    private void undo() {
        UndoManager um = UndoManager.getInstance();
        um.pushTarget(undoStack, redoStack);
        try { um.undo(); } finally { um.popTarget(); }
        if (content != null) content.onAfterUndoRedo();
    }

    private void redo() {
        UndoManager um = UndoManager.getInstance();
        um.pushTarget(undoStack, redoStack);
        try { um.redo(); } finally { um.popTarget(); }
        if (content != null) content.onAfterUndoRedo();
    }

    public void close() {
        if (content != null) {
            content.onAssetUnloaded();
            content.destroy();
            content = null;
        }
        open = false;
        loadedAsset = null;
        assetPath = null;
    }

    public boolean isOpen() {
        return open;
    }

    public String getAssetPath() {
        return assetPath;
    }

    public void requestFocus() {
        pendingFocus = true;
    }

    private String extractFilename(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
