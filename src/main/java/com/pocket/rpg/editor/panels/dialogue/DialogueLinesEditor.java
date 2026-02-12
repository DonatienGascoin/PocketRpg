package com.pocket.rpg.editor.panels.dialogue;

import com.pocket.rpg.dialogue.*;
import com.pocket.rpg.editor.core.MaterialIcons;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImString;

import static com.pocket.rpg.editor.panels.animation.AnimationTimelineContext.intToBytes;
import static com.pocket.rpg.editor.panels.animation.AnimationTimelineContext.bytesToInt;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Renders the lines section of the dialogue editor.
 * Owns line-specific state: expanded event toggles, drag-drop reorder.
 */
public class DialogueLinesEditor {

    private static final String LINE_DRAG_TYPE = "DIALOGUE_LINE";
    private static final int ALT_ROW_COLOR = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.12f);
    private static final float DROP_GAP_HEIGHT = 8f;
    private static final int REORDER_LINE_COLOR = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 1f, 1f);
    private static final int REORDER_NOOP_COLOR = ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 0.5f);
    private static final float ON_COMPLETE_WIDTH = 250f;

    // State
    private final List<Boolean> lineEventExpanded = new ArrayList<>();
    private int[] pendingLineMove = null;
    private boolean dragCancelled = false;

    // Callbacks / suppliers
    private final Runnable captureUndoState;
    private final Runnable markDirty;
    private final Runnable addLine;
    private final Supplier<Set<String>> validVariableNames;
    private final Supplier<Set<String>> validEventNames;
    private final Supplier<List<String>> customEventNames;
    private final Supplier<DialogueVariables> variablesAsset;

    public DialogueLinesEditor(
            Runnable captureUndoState,
            Runnable markDirty,
            Runnable addLine,
            Supplier<Set<String>> validVariableNames,
            Supplier<Set<String>> validEventNames,
            Supplier<List<String>> customEventNames,
            Supplier<DialogueVariables> variablesAsset
    ) {
        this.captureUndoState = captureUndoState;
        this.markDirty = markDirty;
        this.addLine = addLine;
        this.validVariableNames = validVariableNames;
        this.validEventNames = validEventNames;
        this.customEventNames = customEventNames;
        this.variablesAsset = variablesAsset;
    }

    /** Reset line-specific state (call on dialogue switch, undo/redo). */
    public void resetState() {
        lineEventExpanded.clear();
        pendingLineMove = null;
        dragCancelled = false;
    }

    public void render(Dialogue dialogue) {
        ImGui.text("Lines");
        ImGui.spacing();

        List<DialogueEntry> entries = dialogue.getEntries();

        // Count lines (entries that are DialogueLine, ignoring the potential ChoiceGroup at end)
        int lineCount = 0;
        for (DialogueEntry entry : entries) {
            if (entry instanceof DialogueLine) lineCount++;
        }

        // Ensure lineEventExpanded list is sized
        while (lineEventExpanded.size() < lineCount) {
            lineEventExpanded.add(false);
        }

        // Escape cancellation for drag-drop (flag-based, persists until mouse released)
        if (ImGui.getDragDropPayload() == null) {
            dragCancelled = false;
        } else if (ImGui.isKeyPressed(ImGuiKey.Escape, false)) {
            dragCancelled = true;
        }

        // Collect entry indices for lines (to map lineIndex <-> entryIndex)
        List<Integer> lineEntryIndices = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) instanceof DialogueLine) {
                lineEntryIndices.add(i);
            }
        }

        int lineIndex = 0;
        int entryIndexToRemove = -1;
        float reorderIndicatorY = -1;
        boolean reorderIsNoop = false;
        float reorderIndicatorX = -1;
        float reorderIndicatorWidth = -1;

        Set<String> varNames = validVariableNames.get();
        Set<String> evtNames = validEventNames.get();

        for (int i = 0; i < entries.size(); i++) {
            DialogueEntry entry = entries.get(i);
            if (!(entry instanceof DialogueLine line)) continue;

            ImGui.pushID("line_" + i);

            // Drop gap BEFORE this line
            float gapX = ImGui.getCursorScreenPosX();
            float gapWidth = ImGui.getContentRegionAvailX();
            int insertIndex = lineIndex;
            int[] gapResult = renderDropGap(i, insertIndex, lineEntryIndices, gapX, gapWidth);
            if (gapResult != null) {
                reorderIndicatorY = Float.intBitsToFloat(gapResult[0]);
                reorderIsNoop = gapResult[1] == 1;
                reorderIndicatorX = gapX;
                reorderIndicatorWidth = gapWidth;
            }

            // Alternating row background
            float rowStartY = ImGui.getCursorScreenPosY();
            float rowStartX = ImGui.getCursorScreenPosX();

            // Header row: drag handle + Line N  |  On Complete: [...]  |  [+ Var] [X]
            String headerLabel = MaterialIcons.DragIndicator + " Line " + (lineIndex + 1);
            ImVec2 headerSize = ImGui.calcTextSize(headerLabel);
            float cursorX = ImGui.getCursorScreenPosX();
            float cursorY = ImGui.getCursorScreenPosY();
            ImGui.invisibleButton("##dragHandle_" + i, headerSize.x, headerSize.y);
            if (ImGui.isItemHovered()) {
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            }
            ImGui.getWindowDrawList().addText(cursorX, cursorY,
                    ImGui.colorConvertFloat4ToU32(0.6f, 0.6f, 0.6f, 1f), headerLabel);

            // Drag source
            if (!dragCancelled && ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceAllowNullID)) {
                ImGui.setDragDropPayload(LINE_DRAG_TYPE, intToBytes(i));
                ImGui.text("Line " + (lineIndex + 1));
                ImGui.endDragDropSource();
            }

            // On Complete event — inline on the same row
            ImGui.sameLine();
            renderOnCompleteEvent(line, lineIndex);

            // Right-aligned buttons: [+ Var] [X]
            float deleteWidth = ImGui.calcTextSize(MaterialIcons.Close).x + ImGui.getStyle().getFramePaddingX() * 2;
            float varWidth = ImGui.calcTextSize("+ Var").x + ImGui.getStyle().getFramePaddingX() * 2;
            float spacing = ImGui.getStyle().getItemSpacingX();
            float rightEdge = ImGui.getContentRegionAvailX() + ImGui.getCursorPosX();

            ImGui.sameLine(rightEdge - deleteWidth - spacing - varWidth);
            if (ImGui.button("+ Var")) {
                ImGui.openPopup("##varPopup_" + i);
            }
            renderVariablePopup(line, i);

            ImGui.sameLine();

            // Red delete button (disabled if only one line)
            boolean canDelete = lineCount > 1;
            if (!canDelete) ImGui.beginDisabled();
            ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.2f, 0.2f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.7f, 0.3f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.8f, 0.2f, 0.2f, 1.0f);
            if (ImGui.button(MaterialIcons.Close + "##del")) {
                entryIndexToRemove = i;
            }
            ImGui.popStyleColor(3);
            if (!canDelete) ImGui.endDisabled();

            // Expanded event ref editor
            if (lineIndex < lineEventExpanded.size() && lineEventExpanded.get(lineIndex)) {
                ImGui.indent(20);
                renderDialogueEventRefEditor(line, lineIndex);
                DialogueValidation.renderEventRefValidationWarning(line.getOnCompleteEvent(), evtNames);
                ImGui.unindent(20);
                ImGui.spacing();
            }

            // Text input
            ImString lineText = new ImString(line.getText(), 1024);
            ImGui.setNextItemWidth(-1);
            if (ImGui.inputTextMultiline("##text", lineText, -1, 50,
                    ImGuiInputTextFlags.AllowTabInput)) {
                captureUndoState.run();
                line.setText(lineText.get());
                markDirty.run();
            }

            // Validation warnings
            DialogueValidation.renderLineValidationWarnings(line, varNames);

            float rowEndY = ImGui.getCursorScreenPosY();
            float rowWidth = ImGui.getContentRegionAvailX() + ImGui.getCursorScreenPosX() - rowStartX;

            // Alternating background (odd lines)
            if (lineIndex % 2 == 1) {
                ImGui.getWindowDrawList().addRectFilled(
                        rowStartX, rowStartY, rowStartX + rowWidth, rowEndY, ALT_ROW_COLOR);
            }

            ImGui.spacing();
            ImGui.popID();
            lineIndex++;
        }

        // Drop gap AFTER the last line
        if (!lineEntryIndices.isEmpty()) {
            float gapX = ImGui.getCursorScreenPosX();
            float gapWidth = ImGui.getContentRegionAvailX();
            int lastEntryIndex = lineEntryIndices.getLast();
            int[] gapResult = renderDropGap(lastEntryIndex + 1, lineEntryIndices.size(), lineEntryIndices, gapX, gapWidth);
            if (gapResult != null) {
                reorderIndicatorY = Float.intBitsToFloat(gapResult[0]);
                reorderIsNoop = gapResult[1] == 1;
                reorderIndicatorX = gapX;
                reorderIndicatorWidth = gapWidth;
            }
        }

        // Draw reorder indicator line on top
        if (reorderIndicatorY >= 0) {
            int color = reorderIsNoop ? REORDER_NOOP_COLOR : REORDER_LINE_COLOR;
            ImDrawList drawList = ImGui.getWindowDrawList();
            drawList.addLine(reorderIndicatorX, reorderIndicatorY,
                    reorderIndicatorX + reorderIndicatorWidth, reorderIndicatorY,
                    color, 3f);
            drawList.addTriangleFilled(
                    reorderIndicatorX, reorderIndicatorY - 5,
                    reorderIndicatorX, reorderIndicatorY + 5,
                    reorderIndicatorX + 8, reorderIndicatorY,
                    color);
        }

        // Process deferred operations
        if (pendingLineMove != null) {
            int from = pendingLineMove[0];
            int to = pendingLineMove[1];
            pendingLineMove = null;

            captureUndoState.run();
            DialogueEntry moved = entries.remove(from);
            entries.add(to, moved);

            if (from < lineEventExpanded.size()) {
                boolean expandedState = lineEventExpanded.remove(from);
                int insertAt = Math.min(to, lineEventExpanded.size());
                lineEventExpanded.add(insertAt, expandedState);
            }
            markDirty.run();
        }

        // Remove line if requested
        if (entryIndexToRemove >= 0) {
            captureUndoState.run();
            entries.remove(entryIndexToRemove);
            if (entryIndexToRemove < lineEventExpanded.size()) {
                lineEventExpanded.remove(entryIndexToRemove);
            }
            markDirty.run();
        }

        // Add line button
        if (ImGui.button(MaterialIcons.Add + " Add Line")) {
            addLine.run();
        }
    }

    private void renderVariablePopup(DialogueLine line, int entryIndex) {
        if (ImGui.beginPopup("##varPopup_" + entryIndex)) {
            DialogueVariables variables = variablesAsset.get();
            if (variables != null && !variables.getVariables().isEmpty()) {
                for (DialogueVariable var : variables.getVariables()) {
                    if (ImGui.selectable(var.getName())) {
                        captureUndoState.run();
                        line.setText(line.getText() + "[" + var.getName() + "]");
                        markDirty.run();
                    }
                }
            } else {
                ImGui.textDisabled("No variables defined");
            }
            ImGui.endPopup();
        }
    }

    private int[] renderDropGap(int gapId, int insertLineIndex, List<Integer> lineEntryIndices, float gapX, float gapWidth) {
        float gapY = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton("##dropGap_" + gapId, gapWidth, DROP_GAP_HEIGHT);

        if (dragCancelled) return null;
        if (!ImGui.beginDragDropTarget()) return null;

        int noRect = ImGuiDragDropFlags.AcceptNoDrawDefaultRect;
        int[] result = null;

        // Peek to show indicator
        byte[] peek = ImGui.acceptDragDropPayload(LINE_DRAG_TYPE, ImGuiDragDropFlags.AcceptPeekOnly | noRect);
        if (peek != null && peek.length == 4) {
            int sourceEntryIndex = bytesToInt(peek);
            int sourceLineIndex = lineEntryIndices.indexOf(sourceEntryIndex);
            if (sourceLineIndex >= 0) {
                boolean isNoop = sourceLineIndex == insertLineIndex || sourceLineIndex == insertLineIndex - 1;
                result = new int[]{Float.floatToIntBits(gapY + DROP_GAP_HEIGHT / 2), isNoop ? 1 : 0};
            }
        }

        // Accept drop
        byte[] payload = ImGui.acceptDragDropPayload(LINE_DRAG_TYPE, noRect);
        if (payload != null && payload.length == 4) {
            int sourceEntryIndex = bytesToInt(payload);
            int sourceLineIndex = lineEntryIndices.indexOf(sourceEntryIndex);
            if (sourceLineIndex >= 0) {
                boolean isNoop = sourceLineIndex == insertLineIndex || sourceLineIndex == insertLineIndex - 1;
                if (!isNoop) {
                    int targetEntryIndex = insertLineIndex < lineEntryIndices.size()
                            ? lineEntryIndices.get(insertLineIndex)
                            : lineEntryIndices.getLast() + 1;
                    if (sourceEntryIndex < targetEntryIndex) {
                        targetEntryIndex--;
                    }
                    pendingLineMove = new int[]{sourceEntryIndex, targetEntryIndex};
                }
            }
        }

        ImGui.endDragDropTarget();
        return result;
    }

    private void renderOnCompleteEvent(DialogueLine line, int lineIndex) {
        while (lineEventExpanded.size() <= lineIndex) {
            lineEventExpanded.add(false);
        }

        boolean expanded = lineEventExpanded.get(lineIndex);
        DialogueEventRef eventRef = line.getOnCompleteEvent();
        String summary = eventRef != null ? formatEventRef(eventRef) : "[none]";
        String arrow = expanded ? MaterialIcons.ExpandMore : MaterialIcons.ChevronRight;

        String label = arrow + " On Complete: " + summary + "##onComplete_" + lineIndex;
        if (ImGui.selectable(label, expanded, ImGuiSelectableFlags.None, ON_COMPLETE_WIDTH, 0)) {
            lineEventExpanded.set(lineIndex, !expanded);
        }
    }

    private void renderDialogueEventRefEditor(DialogueLine line, int lineIndex) {
        DialogueEventRef eventRef = line.getOnCompleteEvent();

        // Category dropdown
        DialogueEventRef.Category currentCategory = eventRef != null ? eventRef.getCategory() : null;
        String categoryLabel = currentCategory != null ? currentCategory.name() : "Select...";

        ImGui.setNextItemWidth(120);
        if (ImGui.beginCombo("##eventCategory_" + lineIndex, categoryLabel)) {
            if (ImGui.selectable("BUILT_IN", currentCategory == DialogueEventRef.Category.BUILT_IN)) {
                captureUndoState.run();
                line.setOnCompleteEvent(DialogueEventRef.builtIn(DialogueEvent.END_CONVERSATION));
                markDirty.run();
            }
            if (ImGui.selectable("CUSTOM", currentCategory == DialogueEventRef.Category.CUSTOM)) {
                captureUndoState.run();
                line.setOnCompleteEvent(DialogueEventRef.custom(""));
                markDirty.run();
            }
            ImGui.endCombo();
        }

        // Event selector
        if (eventRef != null) {
            ImGui.sameLine();
            if (eventRef.isBuiltIn()) {
                DialogueEvent current = eventRef.getBuiltInEvent();
                String builtInLabel = current != null ? current.name() : "Select...";
                ImGui.setNextItemWidth(160);
                if (ImGui.beginCombo("##builtInEvent_" + lineIndex, builtInLabel)) {
                    for (DialogueEvent event : DialogueEvent.values()) {
                        if (ImGui.selectable(event.name(), event == current)) {
                            captureUndoState.run();
                            eventRef.setBuiltInEvent(event);
                            markDirty.run();
                        }
                    }
                    ImGui.endCombo();
                }
            } else if (eventRef.isCustom()) {
                String currentCustom = eventRef.getCustomEvent() != null ? eventRef.getCustomEvent() : "";
                List<String> eventNames = customEventNames.get();

                ImGui.setNextItemWidth(160);
                if (ImGui.beginCombo("##customEvent_" + lineIndex, currentCustom.isEmpty() ? "Select..." : currentCustom)) {
                    for (String name : eventNames) {
                        if (ImGui.selectable(name, name.equals(currentCustom))) {
                            captureUndoState.run();
                            eventRef.setCustomEvent(name);
                            markDirty.run();
                        }
                    }
                    ImGui.endCombo();
                }
            }

            // Clear button
            ImGui.sameLine();
            if (ImGui.button(MaterialIcons.Close + "##clearEvent_" + lineIndex)) {
                captureUndoState.run();
                line.setOnCompleteEvent(null);
                markDirty.run();
            }
        }
    }

    private String formatEventRef(DialogueEventRef ref) {
        if (ref == null) return "[none]";
        if (ref.isBuiltIn()) {
            return ref.getBuiltInEvent() != null ? ref.getBuiltInEvent().name() : "BUILT_IN (none)";
        }
        if (ref.isCustom()) {
            return ref.getCustomEvent() != null && !ref.getCustomEvent().isEmpty()
                    ? ref.getCustomEvent() : "CUSTOM (none)";
        }
        return "[none]";
    }
}
