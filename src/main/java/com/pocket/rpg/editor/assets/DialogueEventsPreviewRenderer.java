package com.pocket.rpg.editor.assets;

import com.pocket.rpg.dialogue.DialogueEvents;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import imgui.ImGui;

import java.util.List;

/**
 * Preview renderer for DialogueEvents assets.
 * Shows event count and a compact list of event names.
 */
public class DialogueEventsPreviewRenderer implements AssetPreviewRenderer<DialogueEvents> {

    @Override
    public void renderPreview(DialogueEvents events, float maxSize) {
        if (events == null) {
            ImGui.textDisabled("No events asset");
            return;
        }

        EditorColors.textColored(EditorColors.INFO, MaterialIcons.Bolt + " Dialogue Events");

        ImGui.spacing();
        ImGui.textWrapped("One-time flags that fire when a conversation completes.");

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        List<String> eventList = events.getEvents();
        int count = eventList != null ? eventList.size() : 0;
        ImGui.text(count + (count == 1 ? " event" : " events") + " defined");

        ImGui.spacing();

        if (eventList != null && !eventList.isEmpty()) {
            for (String event : eventList) {
                ImGui.bulletText(event);
            }
        } else {
            ImGui.textDisabled("(none)");
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        ImGui.textDisabled("Open in editor to add/remove.");
    }

    @Override
    public Class<DialogueEvents> getAssetType() {
        return DialogueEvents.class;
    }
}
