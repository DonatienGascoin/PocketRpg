package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.dialogue.DialogueEventListener;
import com.pocket.rpg.components.dialogue.DialogueReaction;
import com.pocket.rpg.dialogue.DialogueEvents;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.ui.fields.FieldEditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.Collections;
import java.util.List;

/**
 * Custom inspector for {@link DialogueEventListener}.
 * <p>
 * Shows an event name dropdown populated from the global {@link DialogueEvents} asset,
 * with a stale-event warning for unknown names, and a reaction enum dropdown.
 */
@InspectorFor(DialogueEventListener.class)
public class DialogueEventListenerInspector extends CustomComponentInspector<DialogueEventListener> {

    private static final String EVENTS_ASSET_PATH = "dialogues/events.dialogue-events.json";

    @Override
    public boolean draw() {
        boolean changed = false;

        // --- Event Name (with @Required highlight) ---
        changed |= drawEventName();

        ImGui.spacing();

        // --- Reaction ---
        changed |= FieldEditors.drawEnum("Reaction", component, "reaction", DialogueReaction.class);

        return changed;
    }

    private boolean drawEventName() {
        boolean changed = false;
        List<String> eventNames = loadEventNames();

        boolean highlighted = FieldEditorContext.beginRequiredRowHighlight("eventName");
        try {
            String current = component.getEventName() != null ? component.getEventName() : "";
            String displayLabel = current.isEmpty() ? "Select..." : current;

            final boolean[] comboChanged = {false};
            FieldEditors.inspectorRow("Event Name", () -> {
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                if (ImGui.beginCombo("##eventName", displayLabel)) {
                    for (String name : eventNames) {
                        boolean isSelected = name.equals(current);
                        if (ImGui.selectable(name, isSelected)) {
                            component.setEventName(name);
                            markSceneDirty();
                            comboChanged[0] = true;
                        }
                    }
                    ImGui.endCombo();
                }
            });
            changed |= comboChanged[0];

            // Stale event warning: event name set but not in the asset list
            if (!current.isEmpty() && !eventNames.contains(current)) {
                ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.WARNING[0], EditorColors.WARNING[1], EditorColors.WARNING[2], EditorColors.WARNING[3]);
                ImGui.textWrapped("\u26A0 Unknown event: " + current);
                ImGui.popStyleColor();
            }
        } finally {
            FieldEditorContext.endRequiredRowHighlight(highlighted);
        }

        return changed;
    }

    private List<String> loadEventNames() {
        try {
            DialogueEvents events = Assets.load(EVENTS_ASSET_PATH, DialogueEvents.class);
            if (events != null && events.getEvents() != null) {
                return events.getEvents();
            }
        } catch (Exception ignored) {
            // Asset may not exist yet
        }
        return Collections.emptyList();
    }

    private void markSceneDirty() {
        if (FieldEditorContext.getCurrentScene() != null) {
            FieldEditorContext.getCurrentScene().markDirty();
        }
    }
}
