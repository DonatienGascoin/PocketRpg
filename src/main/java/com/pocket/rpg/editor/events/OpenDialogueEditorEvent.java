package com.pocket.rpg.editor.events;

/**
 * Event requesting to open the Dialogue Editor for a specific dialogue.
 *
 * @param dialoguePath The path to the dialogue to edit, or null to just open the panel
 */
public record OpenDialogueEditorEvent(String dialoguePath) implements EditorEvent {
}
