package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.dialogue.DialogueEvents;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.loaders.DialogueEventsLoader;
import imgui.ImGui;
import imgui.type.ImString;

import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Inspector renderer for {@link DialogueEvents} assets.
 * <p>
 * Renders the event name list with add/remove/rename support,
 * undo/redo, and a save button.
 */
public class DialogueEventsInspectorRenderer implements AssetInspectorRenderer<DialogueEvents> {

    private static final int MAX_UNDO = 50;

    private final DialogueEventsLoader loader = new DialogueEventsLoader();
    private final ImString nameBuffer = new ImString(256);

    private String cachedPath;
    private boolean hasChanges = false;

    // Undo/redo stacks — snapshots of the full event list
    private final Deque<List<String>> undoStack = new ArrayDeque<>();
    private final Deque<List<String>> redoStack = new ArrayDeque<>();
    private DialogueEvents currentAsset;

    @Override
    public boolean render(DialogueEvents events, String assetPath, float maxPreviewSize) {
        if (!assetPath.equals(cachedPath)) {
            cachedPath = assetPath;
            hasChanges = false;
            undoStack.clear();
            redoStack.clear();
        }
        currentAsset = events;

        ImGui.text("Dialogue Events");
        ImGui.separator();

        // Undo/Redo buttons
        renderUndoRedo(events);
        ImGui.spacing();

        List<String> eventNames = events.getEvents();
        int removeIndex = -1;

        if (eventNames.isEmpty()) {
            ImGui.textDisabled("No events defined");
        }

        for (int i = 0; i < eventNames.size(); i++) {
            ImGui.pushID("event_" + i);

            // Event name input
            nameBuffer.set(eventNames.get(i) != null ? eventNames.get(i) : "");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 30);
            if (ImGui.inputText("##name", nameBuffer)) {
                captureUndo(events);
                eventNames.set(i, nameBuffer.get());
                hasChanges = true;
            }

            // Delete button
            ImGui.sameLine();
            EditorColors.pushDangerButton();
            if (ImGui.button(MaterialIcons.Close + "##remove")) {
                removeIndex = i;
            }
            EditorColors.popButtonColors();

            ImGui.popID();
        }

        // Remove deferred
        if (removeIndex >= 0) {
            captureUndo(events);
            eventNames.remove(removeIndex);
            hasChanges = true;
        }

        ImGui.spacing();

        // Add button
        if (ImGui.button(MaterialIcons.Add + " Add Event")) {
            captureUndo(events);
            eventNames.add("");
            hasChanges = true;
        }

        return hasChanges;
    }

    private void renderUndoRedo(DialogueEvents events) {
        boolean canUndo = !undoStack.isEmpty();
        boolean canRedo = !redoStack.isEmpty();

        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Undo + "##undoEvents")) {
            undo(events);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Undo");
        if (!canUndo) ImGui.endDisabled();

        ImGui.sameLine();

        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Redo + "##redoEvents")) {
            redo(events);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Redo");
        if (!canRedo) ImGui.endDisabled();
    }

    // ========================================================================
    // UNDO / REDO
    // ========================================================================

    private void captureUndo(DialogueEvents events) {
        undoStack.push(new ArrayList<>(events.getEvents()));
        redoStack.clear();
        if (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
    }

    @Override
    public void undo() {
        if (currentAsset != null) undo(currentAsset);
    }

    @Override
    public void redo() {
        if (currentAsset != null) redo(currentAsset);
    }

    private void undo(DialogueEvents events) {
        if (undoStack.isEmpty()) return;
        redoStack.push(new ArrayList<>(events.getEvents()));
        restore(events, undoStack.pop());
        hasChanges = true;
    }

    private void redo(DialogueEvents events) {
        if (redoStack.isEmpty()) return;
        undoStack.push(new ArrayList<>(events.getEvents()));
        restore(events, redoStack.pop());
        hasChanges = true;
    }

    private void restore(DialogueEvents events, List<String> snapshot) {
        events.getEvents().clear();
        events.getEvents().addAll(snapshot);
    }

    // ========================================================================
    // ASSET INSPECTOR RENDERER
    // ========================================================================

    @Override
    public boolean hasEditableProperties() {
        return true;
    }

    @Override
    public void save(DialogueEvents events, String assetPath) {
        try {
            String fullPath = Paths.get(Assets.getAssetRoot(), assetPath).toString();
            loader.save(events, fullPath);
            hasChanges = false;
        } catch (Exception e) {
            System.err.println("Failed to save dialogue events: " + e.getMessage());
        }
    }

    @Override
    public void onDeselect() {
        cachedPath = null;
        hasChanges = false;
        undoStack.clear();
        redoStack.clear();
        currentAsset = null;
    }

    @Override
    public boolean hasUnsavedChanges() {
        return hasChanges;
    }

    @Override
    public Class<DialogueEvents> getAssetType() {
        return DialogueEvents.class;
    }
}
