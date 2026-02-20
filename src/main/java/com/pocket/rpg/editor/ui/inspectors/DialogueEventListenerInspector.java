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
        List<String> eventNames = loadEventNames();
        String id = String.valueOf(System.identityHashCode(component));

        boolean highlighted = FieldEditorContext.beginRequiredRowHighlight("eventName");
        try {
            boolean changed = FieldEditors.drawStringCombo("Event Name", "event.name." + id,
                    component::getEventName, component::setEventName, eventNames);

            // Stale event warning: event name set but not in the asset list
            String current = component.getEventName();
            if (current != null && !current.isEmpty() && !eventNames.contains(current)) {
                ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.WARNING[0], EditorColors.WARNING[1], EditorColors.WARNING[2], EditorColors.WARNING[3]);
                ImGui.textWrapped("\u26A0 Unknown event: " + current);
                ImGui.popStyleColor();
            }

            return changed;
        } finally {
            FieldEditorContext.endRequiredRowHighlight(highlighted);
        }
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
}
