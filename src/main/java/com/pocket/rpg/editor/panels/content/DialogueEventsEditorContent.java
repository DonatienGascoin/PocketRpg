package com.pocket.rpg.editor.panels.content;

import com.pocket.rpg.dialogue.DialogueEvents;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.AssetEditorContent;
import com.pocket.rpg.editor.panels.AssetEditorShell;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SnapshotCommand;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor content for DialogueEvents assets (.dialogue-events.json).
 * <p>
 * Two-column layout: left (event list with add/delete), right (rename + info).
 */
@EditorContentFor(DialogueEvents.class)
public class DialogueEventsEditorContent implements AssetEditorContent {

    private DialogueEvents editingEvents;
    private AssetEditorShell shell;

    // Selection
    private int selectedIdx = -1;

    // Search
    private final ImString searchFilter = new ImString();

    // Name editing (deferred rename)
    private final ImString nameBuffer = new ImString(256);
    private boolean nameActive = false;

    // Delete confirmation
    private boolean showDeleteConfirmPopup = false;

    @Override
    public void onAssetLoaded(String path, Object asset, AssetEditorShell shell) {
        this.editingEvents = (DialogueEvents) asset;
        this.shell = shell;
        this.selectedIdx = -1;
        this.searchFilter.set("");
    }

    @Override
    public void onAssetUnloaded() {
        editingEvents = null;
        selectedIdx = -1;
    }

    @Override
    public void onAfterUndoRedo() {
        if (editingEvents == null) return;
        List<String> events = editingEvents.getEvents();
        if (selectedIdx >= events.size()) {
            selectedIdx = events.isEmpty() ? -1 : events.size() - 1;
        }
    }

    @Override
    public Class<?> getAssetClass() {
        return DialogueEvents.class;
    }

    // ========================================================================
    // RENDER
    // ========================================================================

    @Override
    public void render() {
        if (editingEvents == null) return;

        // Info banner
        renderInfoBanner();

        ImGui.spacing();

        float totalWidth = ImGui.getContentRegionAvailX();
        float leftColumnWidth = Math.max(200, totalWidth * 0.35f);

        // Left column: event list
        if (ImGui.beginChild("##eventList", leftColumnWidth, -1, true)) {
            renderEventList();
        }
        ImGui.endChild();

        ImGui.sameLine();

        // Right column: event editor
        if (ImGui.beginChild("##eventEditor", 0, -1, true)) {
            renderEventEditor();
        }
        ImGui.endChild();
    }

    private void renderInfoBanner() {
        EditorColors.textColored(EditorColors.INFO, MaterialIcons.Info);
        ImGui.sameLine();
        ImGui.textWrapped("Events are one-time flags. Fire them at the end of a conversation, " +
                "then check them in Conditional Dialogues to branch NPC behavior.");
    }

    // ========================================================================
    // LEFT COLUMN — EVENT LIST
    // ========================================================================

    private void renderEventList() {
        // Search box
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##search", "Search...", searchFilter);

        ImGui.spacing();

        // Add / Delete buttons
        if (ImGui.button(MaterialIcons.Add + " New Event")) {
            addNewEvent();
        }

        if (selectedIdx >= 0) {
            ImGui.sameLine();
            EditorColors.pushDangerButton();
            if (ImGui.button(MaterialIcons.Delete + " Delete")) {
                showDeleteConfirmPopup = true;
            }
            EditorColors.popButtonColors();
        }

        ImGui.separator();

        // Event list
        String filter = searchFilter.get().toLowerCase();
        List<String> events = editingEvents.getEvents();

        if (ImGui.beginChild("##eventListScroll")) {
            for (int i = 0; i < events.size(); i++) {
                String event = events.get(i);

                if (!filter.isEmpty() && !event.toLowerCase().contains(filter)) {
                    continue;
                }

                boolean isSelected = (i == selectedIdx);
                if (ImGui.selectable(event, isSelected)) {
                    selectedIdx = i;
                }
            }
        }
        ImGui.endChild();
    }

    // ========================================================================
    // RIGHT COLUMN — EVENT EDITOR
    // ========================================================================

    private void renderEventEditor() {
        if (selectedIdx < 0 || selectedIdx >= editingEvents.getEvents().size()) {
            ImGui.textDisabled("Select an event to edit");
            return;
        }

        String currentName = editingEvents.getEvents().get(selectedIdx);

        ImGui.text("Event Name");
        ImGui.spacing();

        // Name field — deferred rename on deactivation
        if (!nameActive) {
            nameBuffer.set(currentName);
        }
        ImGui.setNextItemWidth(-1);
        ImGui.inputText("##eventName", nameBuffer, ImGuiInputTextFlags.CallbackCharFilter);
        nameActive = ImGui.isItemActive();
        if (ImGui.isItemDeactivatedAfterEdit()) {
            String newName = sanitizeEventName(nameBuffer.get().trim());
            if (!newName.isEmpty() && !newName.equals(currentName) && !editingEvents.getEvents().contains(newName)) {
                int idx = selectedIdx;
                captureUndo("Rename Event", () -> editingEvents.getEvents().set(idx, newName));
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Usage hint
        ImGui.textDisabled("How to use this event:");
        ImGui.spacing();
        ImGui.bulletText("Dialogue Editor: set as 'On Complete' action");
        ImGui.bulletText("DialogueInteractable: check in Conditional Dialogues");
        ImGui.bulletText("DialogueEventListener: react when event fires");
    }

    // ========================================================================
    // POPUPS
    // ========================================================================

    @Override
    public void renderPopups() {
        if (showDeleteConfirmPopup) {
            ImGui.openPopup("Delete Event##evtConfirm");
            showDeleteConfirmPopup = false;
        }

        if (ImGui.beginPopupModal("Delete Event##evtConfirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            String name = (selectedIdx >= 0 && selectedIdx < editingEvents.getEvents().size())
                    ? editingEvents.getEvents().get(selectedIdx) : "?";
            ImGui.text("Delete event \"" + name + "\"?");
            ImGui.textDisabled("Any references to this event will become stale.");
            ImGui.spacing();

            EditorColors.pushDangerButton();
            if (ImGui.button("Delete", 120, 0)) {
                performDelete();
                ImGui.closeCurrentPopup();
            }
            EditorColors.popButtonColors();

            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void performDelete() {
        if (selectedIdx < 0 || selectedIdx >= editingEvents.getEvents().size()) return;

        int idx = selectedIdx;
        captureUndo("Delete Event", () -> editingEvents.getEvents().remove(idx));

        // Adjust selection
        if (selectedIdx >= editingEvents.getEvents().size()) {
            selectedIdx = editingEvents.getEvents().isEmpty() ? -1 : editingEvents.getEvents().size() - 1;
        }
    }

    // ========================================================================
    // UNDO SUPPORT
    // ========================================================================

    private static List<String> snapshot(DialogueEvents events) {
        return new ArrayList<>(events.getEvents());
    }

    private static void restore(DialogueEvents target, Object snapshotObj) {
        @SuppressWarnings("unchecked")
        List<String> snapshot = (List<String>) snapshotObj;
        target.getEvents().clear();
        target.getEvents().addAll(snapshot);
    }

    private void captureUndo(String description, Runnable mutation) {
        if (editingEvents == null) return;
        UndoManager.getInstance().push(SnapshotCommand.capture(
                editingEvents,
                DialogueEventsEditorContent::snapshot,
                DialogueEventsEditorContent::restore,
                mutation,
                description
        ));
        shell.markDirty();
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void addNewEvent() {
        String name = generateUniqueName();
        captureUndo("Add Event", () -> editingEvents.getEvents().add(name));
        selectedIdx = editingEvents.getEvents().size() - 1;
    }

    private String generateUniqueName() {
        int counter = 1;
        while (editingEvents.getEvents().contains("NEW_EVENT_" + counter)) {
            counter++;
        }
        return "NEW_EVENT_" + counter;
    }

    private String sanitizeEventName(String input) {
        // Uppercase, replace spaces/hyphens with underscores, strip invalid chars
        return input.toUpperCase()
                .replace(' ', '_')
                .replace('-', '_')
                .replaceAll("[^A-Z0-9_]", "");
    }
}
