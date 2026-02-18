package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.LogBuffer;
import com.pocket.rpg.logging.LogEntry;
import com.pocket.rpg.logging.LogLevel;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * Unity-style console panel for viewing log messages.
 * Features:
 * - Level filtering (TRACE/DEBUG/INFO/WARN/ERROR)
 * - Text search filtering
 * - Collapse repeated messages
 * - Auto-scroll to latest
 * - Detail pane for selected entry
 * - Copy to clipboard
 */
public class ConsolePanel extends EditorPanel {

    private static final String PANEL_ID = "console";

    // Filter state
    private final ImString searchFilter = new ImString(256);
    private final ImBoolean showTrace = new ImBoolean(false);
    private final ImBoolean showDebug = new ImBoolean(true);
    private final ImBoolean showInfo = new ImBoolean(true);
    private final ImBoolean showWarn = new ImBoolean(true);
    private final ImBoolean showError = new ImBoolean(true);

    // Options
    private final ImBoolean autoScroll = new ImBoolean(true);
    private final ImBoolean showTimestamps = new ImBoolean(false);
    private final ImBoolean showLoggerName = new ImBoolean(false);
    private final ImBoolean wordWrap = new ImBoolean(false);
    private final ImBoolean collapseEnabled = new ImBoolean(true);

    // Selection
    private LogEntry selectedEntry = null;
    private boolean scrollToBottom = false;

    public ConsolePanel() {
        super(PANEL_ID, true);
    }

    @Override
    public void render() {
        if (!isOpen()) return;

        int windowFlags = ImGuiWindowFlags.MenuBar;
        boolean visible = ImGui.begin(MaterialIcons.Terminal + " Console###Console", windowFlags);
        setContentVisible(visible);

        if (visible) {
            renderMenuBar();
            renderToolbar();

            // Split between log list and detail pane
            float availHeight = ImGui.getContentRegionAvailY();
            float listHeight = selectedEntry != null ? availHeight * 0.65f : availHeight;

            renderLogList(listHeight);

            if (selectedEntry != null) {
                ImGui.separator();
                renderDetailPane(availHeight - listHeight - 10);
            }
        }

        ImGui.end();
    }

    private void renderMenuBar() {
        LogBuffer buffer = Log.getManager().getBuffer();

        if (ImGui.beginMenuBar()) {
            if (ImGui.beginMenu("Options")) {
                // Display options
                ImGui.menuItem("Auto-scroll", "", autoScroll);
                ImGui.menuItem("Word Wrap", "", wordWrap);
                if (ImGui.menuItem("Collapse Repeated", "", collapseEnabled)) {
                    buffer.setCollapseEnabled(collapseEnabled.get());
                }

                ImGui.separator();
                ImGui.textDisabled("Show:");
                ImGui.menuItem("Timestamps", "", showTimestamps);
                ImGui.menuItem("Logger Name", "", showLoggerName);

                ImGui.separator();

                // Message type filters
                ImGui.textDisabled("Message Types:");
                renderLevelMenuItem("Trace", showTrace, EditorColors.LOG_TRACE);
                renderLevelMenuItem("Debug", showDebug, EditorColors.LOG_DEBUG);
                renderLevelMenuItem("Info", showInfo, EditorColors.LOG_INFO);
                renderLevelMenuItem("Warning", showWarn, EditorColors.LOG_WARN);
                renderLevelMenuItem("Error", showError, EditorColors.LOG_ERROR);

                ImGui.endMenu();
            }
            ImGui.endMenuBar();
        }
    }

    private void renderLevelMenuItem(String label, ImBoolean enabled, float[] color) {
        // Store state before widget to ensure push/pop match
        boolean wasEnabled = enabled.get();
        if (wasEnabled) {
            ImGui.pushStyleColor(ImGuiCol.Text, color[0], color[1], color[2], color[3]);
        }
        ImGui.menuItem(label, "", enabled);
        if (wasEnabled) {
            ImGui.popStyleColor();
        }
    }

    private void renderToolbar() {
        LogBuffer buffer = Log.getManager().getBuffer();

        // Search filter (left side)
        ImGui.setNextItemWidth(200);
        ImGui.inputTextWithHint("##search", MaterialIcons.Search + " Filter...", searchFilter);

        ImGui.sameLine();

        // Entry count
        List<LogEntry> entries = getFilteredEntries();
        ImGui.textDisabled(String.format("(%d entries)", entries.size()));

        // Clear button (right side)
        float clearButtonWidth = 30;
        ImGui.sameLine(ImGui.getContentRegionMaxX() - clearButtonWidth);
        if (ImGui.button(MaterialIcons.Delete + "##clear")) {
            buffer.clear();
            selectedEntry = null;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Clear all logs");
        }
    }

    private int countLevel(LogLevel level) {
        LogBuffer buffer = Log.getManager().getBuffer();
        return (int) buffer.getEntries().stream()
                .filter(e -> e.getLevel() == level)
                .count();
    }

    private List<LogEntry> getFilteredEntries() {
        LogBuffer buffer = Log.getManager().getBuffer();
        String filter = searchFilter.get();

        return buffer.getEntries().stream()
                .filter(e -> isLevelVisible(e.getLevel()))
                .filter(e -> filter.isEmpty() ||
                        e.getMessage().toLowerCase().contains(filter.toLowerCase()) ||
                        e.getLoggerName().toLowerCase().contains(filter.toLowerCase()))
                .toList();
    }

    private boolean isLevelVisible(LogLevel level) {
        return switch (level) {
            case TRACE -> showTrace.get();
            case DEBUG -> showDebug.get();
            case INFO -> showInfo.get();
            case WARN -> showWarn.get();
            case ERROR -> showError.get();
        };
    }

    private void renderLogList(float height) {
        ImGui.beginChild("LogList", 0, height, true);

        List<LogEntry> entries = getFilteredEntries();

        for (LogEntry entry : entries) {
            renderLogEntry(entry);
        }

        // Auto-scroll
        if (autoScroll.get() && (scrollToBottom || ImGui.getScrollY() >= ImGui.getScrollMaxY() - 10)) {
            ImGui.setScrollHereY(1.0f);
            scrollToBottom = false;
        }

        ImGui.endChild();
    }

    private void renderLogEntry(LogEntry entry) {
        float[] color = getLevelColor(entry.getLevel());
        String icon = getLevelIcon(entry.getLevel());

        // Build display text
        StringBuilder sb = new StringBuilder();
        sb.append(icon).append(" ");

        if (showTimestamps.get()) {
            sb.append("[").append(entry.getFormattedTime()).append("] ");
        }

        if (showLoggerName.get()) {
            sb.append("[").append(entry.getLoggerName()).append("] ");
        }

        sb.append(entry.getMessage());

        if (entry.getRepeatCount() > 1) {
            sb.append(" (x").append(entry.getRepeatCount()).append(")");
        }

        boolean isSelected = entry == selectedEntry;
        String displayText = sb.toString();
        String itemId = "log_" + entry.hashCode();

        ImGui.pushStyleColor(ImGuiCol.Text, color[0], color[1], color[2], color[3]);

        if (wordWrap.get()) {
            // Word wrap mode: use textWrapped with selection background
            boolean wasSelected = isSelected;
            if (wasSelected) {
                ImGui.pushStyleColor(ImGuiCol.Header, 0.3f, 0.3f, 0.5f, 1.0f);
            }

            // Calculate wrapped text height
            float availWidth = ImGui.getContentRegionAvailX();
            float textHeight = ImGui.calcTextSize(displayText, availWidth).y;
            float itemHeight = Math.max(textHeight, ImGui.getTextLineHeight()) + 4;

            // Invisible selectable for click handling and context menu
            if (ImGui.selectable("##" + itemId, isSelected, ImGuiSelectableFlags.AllowItemOverlap, availWidth, itemHeight)) {
                selectedEntry = isSelected ? null : entry;
            }

            // Context menu - must be right after the selectable
            renderContextMenu(itemId, entry);

            // Render text on top
            ImGui.sameLine();
            ImGui.setCursorPosX(ImGui.getCursorPosX() - availWidth);
            ImGui.pushTextWrapPos(ImGui.getCursorPosX() + availWidth);
            ImGui.textWrapped(displayText);
            ImGui.popTextWrapPos();

            if (wasSelected) {
                ImGui.popStyleColor();
            }
        } else {
            // Single line mode: standard selectable
            if (ImGui.selectable(displayText + "##" + itemId, isSelected, ImGuiSelectableFlags.SpanAllColumns)) {
                selectedEntry = isSelected ? null : entry;
            }

            // Context menu - right after selectable
            renderContextMenu(itemId, entry);
        }

        ImGui.popStyleColor();
    }

    private void renderContextMenu(String itemId, LogEntry entry) {
        if (ImGui.beginPopupContextItem(itemId)) {
            if (ImGui.menuItem(MaterialIcons.ContentCopy + " Copy Message")) {
                ImGui.setClipboardText(entry.getMessage());
            }
            if (ImGui.menuItem(MaterialIcons.ContentCopy + " Copy Full Entry")) {
                ImGui.setClipboardText(entry.getFormattedMessage());
            }
            if (entry.getThrowable() != null) {
                if (ImGui.menuItem(MaterialIcons.ContentCopy + " Copy Stack Trace")) {
                    ImGui.setClipboardText(getStackTrace(entry.getThrowable()));
                }
            }
            ImGui.endPopup();
        }
    }

    private void renderDetailPane(float height) {
        ImGui.beginChild("DetailPane", 0, height, true);

        if (selectedEntry != null) {
            float[] color = getLevelColor(selectedEntry.getLevel());

            // Header
            EditorColors.textColored(color,
                    getLevelIcon(selectedEntry.getLevel()) + " " + selectedEntry.getLevel().getLabel());

            ImGui.sameLine();
            ImGui.textDisabled("| " + selectedEntry.getLoggerName());

            ImGui.sameLine();
            ImGui.textDisabled("| " + selectedEntry.getFormattedTime());

            ImGui.sameLine();
            ImGui.textDisabled("| Thread: " + selectedEntry.getThreadName());

            ImGui.separator();

            // Message (wrapped)
            ImGui.textWrapped(selectedEntry.getMessage());

            // Stack trace
            if (selectedEntry.getThrowable() != null) {
                ImGui.separator();
                EditorColors.textColored(EditorColors.LOG_ERROR, "Stack Trace:");
                ImGui.beginChild("StackTrace", 0, 0, false);
                ImGui.textWrapped(getStackTrace(selectedEntry.getThrowable()));
                ImGui.endChild();
            }
        }

        ImGui.endChild();
    }

    private float[] getLevelColor(LogLevel level) {
        return switch (level) {
            case TRACE -> EditorColors.LOG_TRACE;
            case DEBUG -> EditorColors.LOG_DEBUG;
            case INFO -> EditorColors.LOG_INFO;
            case WARN -> EditorColors.LOG_WARN;
            case ERROR -> EditorColors.LOG_ERROR;
        };
    }

    private String getLevelIcon(LogLevel level) {
        return switch (level) {
            case TRACE -> MaterialIcons.Code;
            case DEBUG -> MaterialIcons.BugReport;
            case INFO -> MaterialIcons.Info;
            case WARN -> MaterialIcons.Warning;
            case ERROR -> MaterialIcons.Error;
        };
    }

    private String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Programmatically scroll to the bottom on the next frame.
     */
    public void scrollToBottom() {
        this.scrollToBottom = true;
    }
}
