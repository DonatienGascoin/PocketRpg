package com.pocket.rpg.editor.panels;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationFrame;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.resources.loaders.AnimationLoader;
import com.pocket.rpg.editor.panels.animation.AnimationPreviewRenderer;
import com.pocket.rpg.editor.panels.animation.AnimationTimelineContext;
import com.pocket.rpg.editor.panels.animation.StripTimelineRenderer;
import com.pocket.rpg.editor.panels.animation.TrackTimelineRenderer;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Animation editor panel for creating and editing animation assets.
 * <p>
 * Features:
 * - Animation list browser
 * - Embedded preview with playback
 * - Frame timeline with thumbnails
 * - Properties editor (name, looping)
 * - Frame inspector (duration, sprite path)
 */
public class AnimationEditorPanel {

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    private static final float PROPERTIES_WIDTH = 180f;
    private static final float CURRENT_FRAME_WIDTH = 280f;
    private static final float TIMELINE_HEIGHT = 160f;
    private static final float PIXELS_PER_SECOND_DEFAULT = 200f;
    private static final float MIN_FRAME_DURATION = 0.01f;

    // ========================================================================
    // STATE
    // ========================================================================

    // Animation list
    private final List<AnimationEntry> animations = new ArrayList<>();
    private AnimationEntry selectedEntry = null;
    private Animation editingAnimation = null;
    private boolean hasUnsavedChanges = false;

    // Timeline selection
    private int selectedFrameIndex = -1;
    private int currentPreviewFrame = 0;
    private float previewTimer = 0f;

    // New animation dialog
    private boolean showNewDialog = false;
    private final ImString newAnimationName = new ImString(64);
    private String newAnimationSpritePath = null;
    private Sprite newAnimationSpritePreview = null;
    private int pendingSpritePickerForNewAnimDelay = 0; // Frame delay counter

    // Delete confirmation dialog
    private boolean showDeleteConfirmDialog = false;

    // Unsaved changes confirmation dialog (when switching animations)
    private boolean showUnsavedChangesDialog = false;
    private AnimationEntry pendingAnimationSwitch = null;
    private boolean pendingNewAnimation = false;

    // Frame editor
    private final ImFloat frameDurationInput = new ImFloat(0.1f);

    // Sprite picker for frame editing
    private final AssetPickerPopup spritePicker = new AssetPickerPopup();

    // Refresh tracking
    private boolean needsRefresh = true;
    private long lastRefreshTime = 0;
    private static final long REFRESH_COOLDOWN_MS = 500;

    // Timeline mode
    private TimelineMode timelineMode = TimelineMode.TRACK;
    private float timelineZoom = 1.0f;
    private float timelinePanX = 0f;

    // Resize state (persisted across frames)
    private int resizingFrameIndex = -1;
    private float resizeStartX = 0f;
    private float resizeStartDuration = 0f;

    // Play button pulsing
    private float pulseTimer = 0f;

    // Undo/Redo - local stacks for animation-specific undo
    private static final int MAX_UNDO_HISTORY = 50;
    private final java.util.Deque<AnimationState> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<AnimationState> redoStack = new java.util.ArrayDeque<>();

    // Extracted renderers
    private AnimationPreviewRenderer previewRenderer;
    private TrackTimelineRenderer trackTimelineRenderer;
    private StripTimelineRenderer stripTimelineRenderer;

    // Status message callback (for showing messages in StatusBar)
    private java.util.function.Consumer<String> statusCallback;

    // ========================================================================
    // ENUMS
    // ========================================================================

    private enum TimelineMode {
        TRACK("Track"),
        STRIP("Strip");

        final String label;

        TimelineMode(String label) {
            this.label = label;
        }
    }

    // ========================================================================
    // ANIMATION ENTRY
    // ========================================================================

    private static class AnimationEntry {
        String path;
        String filename;
        Animation animation;
        boolean modified = false;

        AnimationEntry(String path, String filename, Animation animation) {
            this.path = path;
            this.filename = filename;
            this.animation = animation;
        }
    }

    /**
     * Snapshot of animation state for undo/redo.
     */
    private static class AnimationState {
        final String name;
        final boolean looping;
        final List<AnimationFrame> frames;
        final int selectedFrameIndex;

        AnimationState(Animation anim, int selectedFrame) {
            this.name = anim.getName();
            this.looping = anim.isLooping();
            this.frames = new ArrayList<>(anim.getFrames());
            this.selectedFrameIndex = selectedFrame;
        }

        void restore(Animation anim) {
            anim.setName(name);
            anim.setLooping(looping);
            anim.setFrames(frames);
        }
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    public void initialize() {
        previewRenderer = new AnimationPreviewRenderer();
        previewRenderer.setOnPreviewFrameChanged(frame -> {
            // Keep timeline in sync with playback
        });
        refresh();
    }

    public void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime < REFRESH_COOLDOWN_MS) {
            return;
        }
        lastRefreshTime = now;

        animations.clear();

        List<String> animationPaths = Assets.scanByType(Animation.class);

        for (String path : animationPaths) {
            try {
                Animation anim = Assets.load(path, Animation.class);
                int lastSlash = path.lastIndexOf('/');
                String filename = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
                animations.add(new AnimationEntry(path, filename, anim));
            } catch (Exception e) {
                System.err.println("[AnimationEditorPanel] Failed to load animation: " + path + " - " + e.getMessage());
            }
        }

        animations.sort(Comparator.comparing(e -> e.filename.toLowerCase()));
        needsRefresh = false;
    }

    // ========================================================================
    // MAIN RENDER
    // ========================================================================

    public void render() {
        if (needsRefresh) {
            refresh();
        }

        // Update pulse timer for play button animation
        if (previewRenderer != null && previewRenderer.isPlaying()) {
            pulseTimer += ImGui.getIO().getDeltaTime() * 3f;
        }

        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        ImGui.begin("Animation Editor", flags);

        // Handle keyboard shortcuts when window is focused
        if (ImGui.isWindowFocused(imgui.flag.ImGuiFocusedFlags.RootAndChildWindows)) {
            processKeyboardShortcuts();
        }

        renderToolbar();
        ImGui.separator();
        renderMainContent();

        ImGui.end();

        // Dialogs
        if (showNewDialog) {
            renderNewAnimationDialog();
        }
        if (showDeleteConfirmDialog) {
            renderDeleteConfirmDialog();
        }
        if (showUnsavedChangesDialog) {
            renderUnsavedChangesDialog();
        }

        // Handle pending sprite picker for new animation (after modal is fully closed)
        if (pendingSpritePickerForNewAnimDelay > 0) {
            pendingSpritePickerForNewAnimDelay--;
            if (pendingSpritePickerForNewAnimDelay == 0) {
                spritePicker.open(Sprite.class, null, selectedAsset -> {
                    if (selectedAsset != null) {
                        newAnimationSpritePath = Assets.getPathForResource(selectedAsset);
                        // Handle both Sprite and Texture (for .png files)
                        if (selectedAsset instanceof Sprite sprite) {
                            newAnimationSpritePreview = sprite;
                        } else if (selectedAsset instanceof com.pocket.rpg.rendering.resources.Texture texture) {
                            newAnimationSpritePreview = new Sprite(texture);
                        }
                    }
                    // Re-open the new animation dialog
                    showNewDialog = true;
                });
            }
        }

        // Sprite picker popup
        spritePicker.render();
    }

    // ========================================================================
    // KEYBOARD SHORTCUTS
    // ========================================================================

    private void processKeyboardShortcuts() {
        if (showNewDialog || showDeleteConfirmDialog || showUnsavedChangesDialog || ImGui.getIO().getWantTextInput()) {
            return;
        }

        boolean ctrl = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
        boolean shift = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);

        // Ctrl+S: Save
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.S, false)) {
            if (selectedEntry != null && hasUnsavedChanges) {
                saveCurrentAnimation();
            }
        }

        // Ctrl+N: New animation
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.N, false)) {
            openNewDialog();
        }

        // Ctrl+Z: Undo, Ctrl+Shift+Z: Redo
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.Z, false)) {
            if (shift) {
                redo();
            } else {
                undo();
            }
        }

        // Ctrl+Y: Redo (alternative)
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.Y, false)) {
            redo();
        }

        // Space: Play/Pause
        if (ImGui.isKeyPressed(ImGuiKey.Space) && editingAnimation != null && previewRenderer != null) {
            previewRenderer.togglePlayback();
        }

        // Left Arrow: Previous frame
        if (ImGui.isKeyPressed(ImGuiKey.LeftArrow) && editingAnimation != null && editingAnimation.getFrameCount() > 0) {
            if (previewRenderer != null) previewRenderer.setPlaying(false);
            currentPreviewFrame = Math.max(0, currentPreviewFrame - 1);
            selectedFrameIndex = currentPreviewFrame;
            previewTimer = 0;
            if (previewRenderer != null) previewRenderer.setCurrentPreviewFrame(currentPreviewFrame);
        }

        // Right Arrow: Next frame
        if (ImGui.isKeyPressed(ImGuiKey.RightArrow) && editingAnimation != null && editingAnimation.getFrameCount() > 0) {
            if (previewRenderer != null) previewRenderer.setPlaying(false);
            currentPreviewFrame = Math.min(editingAnimation.getFrameCount() - 1, currentPreviewFrame + 1);
            selectedFrameIndex = currentPreviewFrame;
            previewTimer = 0;
            if (previewRenderer != null) previewRenderer.setCurrentPreviewFrame(currentPreviewFrame);
        }

        // Home: First frame
        if (ImGui.isKeyPressed(ImGuiKey.Home) && editingAnimation != null && editingAnimation.getFrameCount() > 0) {
            if (previewRenderer != null) previewRenderer.setPlaying(false);
            currentPreviewFrame = 0;
            selectedFrameIndex = 0;
            previewTimer = 0;
            if (previewRenderer != null) previewRenderer.setCurrentPreviewFrame(0);
        }

        // End: Last frame
        if (ImGui.isKeyPressed(ImGuiKey.End) && editingAnimation != null && editingAnimation.getFrameCount() > 0) {
            if (previewRenderer != null) previewRenderer.setPlaying(false);
            currentPreviewFrame = editingAnimation.getFrameCount() - 1;
            selectedFrameIndex = currentPreviewFrame;
            previewTimer = 0;
            if (previewRenderer != null) previewRenderer.setCurrentPreviewFrame(currentPreviewFrame);
        }

        // Delete: Delete selected frame
        if (ImGui.isKeyPressed(ImGuiKey.Delete) && selectedFrameIndex >= 0) {
            deleteSelectedFrame();
        }

        // F5: Refresh
        if (ImGui.isKeyPressed(ImGuiKey.F5)) {
            needsRefresh = true;
        }
    }

    // ========================================================================
    // TOOLBAR
    // ========================================================================

    private void renderToolbar() {
        // New
        if (ImGui.button(FontAwesomeIcons.Plus + " New")) {
            openNewDialog();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Create new animation (Ctrl+N)");
        }

        ImGui.sameLine();

        // Delete
        boolean canDelete = selectedEntry != null;
        if (!canDelete) ImGui.beginDisabled();
        if (ImGui.button(FontAwesomeIcons.Trash + " Delete")) {
            showDeleteConfirmDialog = true;
        }
        if (!canDelete) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Delete selected animation");
        }

        ImGui.sameLine();

        // Save (with yellow color when unsaved)
        boolean canSave = selectedEntry != null && hasUnsavedChanges;
        if (canSave) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.5f, 0.0f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.7f, 0.6f, 0.0f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.8f, 0.7f, 0.0f, 1.0f);
        } else {
            ImGui.beginDisabled();
        }
        if (ImGui.button(FontAwesomeIcons.Save + " Save")) {
            saveCurrentAnimation();
        }
        if (canSave) {
            ImGui.popStyleColor(3);
        } else {
            ImGui.endDisabled();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Save changes (Ctrl+S)");
        }

        ImGui.sameLine();

        // Refresh
        if (ImGui.button(FontAwesomeIcons.SyncAlt)) {
            needsRefresh = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Refresh animation list (F5)");
        }

        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        // Undo (animation-specific)
        boolean canUndo = !undoStack.isEmpty();
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button(FontAwesomeIcons.Undo)) {
            undo();
        }
        if (!canUndo) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Undo animation edit (Ctrl+Z)");
        }

        ImGui.sameLine();

        // Redo (animation-specific)
        boolean canRedo = !redoStack.isEmpty();
        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.button(FontAwesomeIcons.Redo)) {
            redo();
        }
        if (!canRedo) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Redo animation edit (Ctrl+Y)");
        }

        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        // Play/Stop buttons
        renderPlaybackControls();

        // Animation dropdown on the right
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 200);
        renderAnimationDropdown();
    }

    private void renderPlaybackControls() {
        boolean isPlaying = previewRenderer != null && previewRenderer.isPlaying();
        boolean canPlay = editingAnimation != null && editingAnimation.getFrameCount() > 0;

        if (!canPlay) ImGui.beginDisabled();

        // Play button (green when stopped)
        if (!isPlaying) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.5f, 0.1f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.6f, 0.2f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.3f, 0.7f, 0.3f, 1.0f);
        }
        if (ImGui.button(FontAwesomeIcons.Play + " Play##PlayBtn")) {
            if (previewRenderer != null) previewRenderer.play();
        }
        if (!isPlaying) {
            ImGui.popStyleColor(3);
        }
        if (!canPlay) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Play animation (Space)");
        }

        ImGui.sameLine();

        // Stop button (red with pulse when playing)
        if (!canPlay) ImGui.beginDisabled();
        if (isPlaying) {
            float pulse = (float) (0.5f + 0.5f * Math.sin(pulseTimer));
            float r = 0.6f + pulse * 0.2f;
            float g = 0.1f + pulse * 0.1f;
            ImGui.pushStyleColor(ImGuiCol.Button, r, g, 0.1f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, r + 0.1f, g + 0.1f, 0.2f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, r + 0.2f, g + 0.2f, 0.3f, 1.0f);
        }
        if (ImGui.button(FontAwesomeIcons.Stop + " Stop##StopBtn")) {
            if (previewRenderer != null) previewRenderer.stop();
            currentPreviewFrame = 0;
        }
        if (isPlaying) {
            ImGui.popStyleColor(3);
        }
        if (!canPlay) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Stop animation");
        }

        ImGui.sameLine();

        // Speed control
        ImGui.setNextItemWidth(60);
        float speed = previewRenderer != null ? previewRenderer.getPreviewSpeed() : 1.0f;
        float[] speedArr = {speed};
        if (ImGui.sliderFloat("##Speed", speedArr, 0.1f, 3.0f, "%.1fx")) {
            if (previewRenderer != null) previewRenderer.setPreviewSpeed(speedArr[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Playback speed");
        }
    }

    private void renderAnimationDropdown() {
        String dropdownLabel;
        boolean hasModified = hasUnsavedChanges;

        if (selectedEntry != null) {
            dropdownLabel = selectedEntry.filename;
            if (hasModified) {
                dropdownLabel += " *";
            }
        } else {
            dropdownLabel = "Select Animation...";
        }

        if (hasModified) {
            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.4f, 0.35f, 0.0f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0.5f, 0.45f, 0.0f, 1.0f);
        }

        ImGui.setNextItemWidth(200);
        boolean comboOpen = ImGui.beginCombo("##AnimationDropdown", dropdownLabel);

        if (hasModified) {
            ImGui.popStyleColor(2);
        }

        if (comboOpen) {
            for (AnimationEntry entry : animations) {
                boolean isSelected = entry == selectedEntry;
                if (ImGui.selectable(entry.filename, isSelected)) {
                    requestAnimationSwitch(entry);
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }

            if (animations.isEmpty()) {
                ImGui.textDisabled("No animations found");
            }

            ImGui.endCombo();
        }
    }

    private void requestAnimationSwitch(AnimationEntry entry) {
        if (entry == selectedEntry) {
            return;
        }

        if (hasUnsavedChanges) {
            pendingAnimationSwitch = entry;
            showUnsavedChangesDialog = true;
        } else {
            selectAnimation(entry);
        }
    }

    // ========================================================================
    // MAIN CONTENT
    // ========================================================================

    private void renderMainContent() {
        float availableHeight = ImGui.getContentRegionAvailY();
        float topSectionHeight = availableHeight - TIMELINE_HEIGHT - ImGui.getStyle().getItemSpacingY() * 2;

        // Top section: Properties | Current Frame | Preview
        if (ImGui.beginTable("TopSection", 3, ImGuiTableFlags.BordersInnerV | ImGuiTableFlags.Resizable)) {
            ImGui.tableSetupColumn("Properties", ImGuiTableColumnFlags.WidthFixed, PROPERTIES_WIDTH);
            ImGui.tableSetupColumn("CurrentFrame", ImGuiTableColumnFlags.WidthFixed, CURRENT_FRAME_WIDTH);
            ImGui.tableSetupColumn("Preview", ImGuiTableColumnFlags.WidthStretch);

            ImGui.tableNextRow();

            // Properties
            ImGui.tableNextColumn();
            ImGui.beginChild("Props", 0, topSectionHeight, false);
            renderProperties();
            ImGui.endChild();

            // Current Frame
            ImGui.tableNextColumn();
            ImGui.beginChild("CurrentFrame", 0, topSectionHeight, false);
            renderCurrentFramePanel();
            ImGui.endChild();

            // Preview
            ImGui.tableNextColumn();
            ImGui.beginChild("Preview", 0, topSectionHeight, false, ImGuiWindowFlags.NoScrollbar);
            if (previewRenderer != null) {
                previewRenderer.render();
                // Sync preview frame with panel state
                currentPreviewFrame = previewRenderer.getCurrentPreviewFrame();
            }
            ImGui.endChild();

            ImGui.endTable();
        }

        // Bottom section: Timeline
        ImGui.separator();
        ImGui.beginChild("Timeline", 0, TIMELINE_HEIGHT, false);
        renderTimeline();
        ImGui.endChild();
    }

    // ========================================================================
    // PROPERTIES
    // ========================================================================

    private void renderProperties() {
        ImGui.text("Properties");
        ImGui.separator();

        if (editingAnimation == null || selectedEntry == null) {
            ImGui.textDisabled("No animation selected");
            return;
        }

        // Show filename (source of truth for identification)
        String filename = selectedEntry.filename;
        if (hasUnsavedChanges) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.85f, 0.0f, 1.0f);
            ImGui.text(filename + " *");
            ImGui.popStyleColor();
        } else {
            ImGui.text(filename);
        }

        ImGui.spacing();

        ImBoolean looping = new ImBoolean(editingAnimation.isLooping());
        if (ImGui.checkbox("Looping", looping)) {
            editingAnimation.setLooping(looping.get());
            markModified();
        }

        ImGui.separator();

        ImGui.text("Frames: " + editingAnimation.getFrameCount());
        ImGui.text(String.format("Duration: %.2fs", editingAnimation.getTotalDuration()));

        if (editingAnimation.getFrameCount() > 0) {
            float avgDuration = editingAnimation.getTotalDuration() / editingAnimation.getFrameCount();
            ImGui.text(String.format("Avg Frame: %.3fs", avgDuration));
        }
    }

    // ========================================================================
    // CURRENT FRAME PANEL
    // ========================================================================

    private void renderCurrentFramePanel() {
        ImGui.text("Current Frame");
        ImGui.separator();

        if (editingAnimation == null) {
            ImGui.textDisabled("No animation selected");
            return;
        }

        if (selectedFrameIndex < 0 || selectedFrameIndex >= editingAnimation.getFrameCount()) {
            ImGui.textDisabled("Select a frame in the timeline");
            return;
        }

        AnimationFrame frame = editingAnimation.getFrame(selectedFrameIndex);

        ImGui.text("Frame " + (selectedFrameIndex + 1) + " of " + editingAnimation.getFrameCount());
        ImGui.spacing();

        // Duration
        ImGui.text("Duration:");
        frameDurationInput.set(frame.duration());
        ImGui.setNextItemWidth(-1);
        if (ImGui.inputFloat("##FrameDuration", frameDurationInput, 0.01f, 0.1f, "%.3f s")) {
            captureUndoState();
            float newDuration = Math.max(MIN_FRAME_DURATION, frameDurationInput.get());
            editingAnimation.setFrame(selectedFrameIndex, new AnimationFrame(frame.spritePath(), newDuration));
            markModified();
        }

        ImGui.spacing();

        // Sprite
        ImGui.text("Sprite:");
        String spritePath = frame.spritePath();
        if (spritePath == null || spritePath.isEmpty()) {
            ImGui.textColored(0.7f, 0.5f, 0.2f, 1f, "(No sprite selected)");
        } else {
            String displayPath = spritePath.length() > 35 ? "..." + spritePath.substring(spritePath.length() - 32) : spritePath;
            ImGui.textDisabled(displayPath);
            if (ImGui.isItemHovered() && spritePath.length() > 35) {
                ImGui.setTooltip(spritePath);
            }
        }

        // Browse button
        if (ImGui.button(FontAwesomeIcons.FolderOpen + " Browse...##BrowseSprite", -1, 0)) {
            final int frameIdx = selectedFrameIndex;
            String initialPath = (spritePath == null || spritePath.isEmpty()) ? "" : spritePath;
            spritePicker.open(Sprite.class, initialPath, selectedSprite -> {
                if (editingAnimation != null && frameIdx >= 0 && frameIdx < editingAnimation.getFrameCount()) {
                    AnimationFrame oldFrame = editingAnimation.getFrame(frameIdx);
                    String newPath;
                    if (selectedSprite == null) {
                        newPath = AnimationFrame.EMPTY_SPRITE;
                    } else {
                        newPath = Assets.getPathForResource(selectedSprite);
                        if (newPath == null) {
                            return;
                        }
                    }
                    captureUndoState();
                    editingAnimation.setFrame(frameIdx, new AnimationFrame(newPath, oldFrame.duration()));
                    markModified();
                    recalculateMaxSpriteDimensions();
                }
            });
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        if (ImGui.button(FontAwesomeIcons.Trash + " Delete Frame##DeleteFrame", -1, 0)) {
            deleteSelectedFrame();
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.textDisabled("Events");
        ImGui.textDisabled("(Coming soon)");
    }

    // ========================================================================
    // TIMELINE
    // ========================================================================

    private void renderTimeline() {
        renderTimelineHeader();
        ImGui.separator();

        if (editingAnimation == null) {
            ImGui.textDisabled("No animation selected");
            return;
        }

        // Create context for renderers (pass current resize state so it persists across frames)
        AnimationTimelineContext ctx = new AnimationTimelineContext(
                editingAnimation,
                selectedFrameIndex,
                currentPreviewFrame,
                timelineZoom,
                timelinePanX,
                resizingFrameIndex,
                resizeStartX,
                resizeStartDuration,
                this::captureUndoState,
                this::markModified,
                this::recalculateMaxSpriteDimensions,
                this::setSelectedFrameIndex,
                this::setCurrentPreviewFrame,
                this::resetPreviewTimer,
                (currentPath, onSelect) -> spritePicker.open(Sprite.class, currentPath, obj -> onSelect.accept((Sprite) obj))
        );

        // Render based on mode
        if (timelineMode == TimelineMode.TRACK) {
            if (trackTimelineRenderer == null) {
                trackTimelineRenderer = new TrackTimelineRenderer(
                        ctx,
                        this::addFrameToAnimation,
                        this::deleteFrame,
                        this::getCurrentTime,
                        () -> previewTimer
                );
            } else {
                trackTimelineRenderer = new TrackTimelineRenderer(
                        ctx,
                        this::addFrameToAnimation,
                        this::deleteFrame,
                        this::getCurrentTime,
                        () -> previewTimer
                );
            }
            trackTimelineRenderer.render();

            // Sync state back from context
            timelineZoom = ctx.getTimelineZoom();
            timelinePanX = ctx.getTimelinePanX();
        } else {
            stripTimelineRenderer = new StripTimelineRenderer(
                    ctx,
                    this::addFrameToAnimation,
                    this::deleteFrame
            );
            stripTimelineRenderer.render();
        }

        // Sync selection and resize state back from context
        selectedFrameIndex = ctx.getSelectedFrameIndex();
        currentPreviewFrame = ctx.getCurrentPreviewFrame();
        resizingFrameIndex = ctx.getResizingFrameIndex();
        resizeStartX = ctx.getResizeStartX();
        resizeStartDuration = ctx.getResizeStartDuration();
    }

    private void renderTimelineHeader() {
        // Frame status
        if (editingAnimation == null || editingAnimation.getFrameCount() == 0) {
            ImGui.text("Frame: - / -");
        } else {
            int frameNum = currentPreviewFrame + 1;
            int totalFrames = editingAnimation.getFrameCount();
            ImGui.text(String.format("Frame: %d / %d  Time: %.2fs / %.2fs",
                    frameNum, totalFrames, getCurrentTime(), editingAnimation.getTotalDuration()));
        }

        ImGui.sameLine(ImGui.getContentRegionAvailX() - 250);

        // Zoom controls (for track mode)
        if (timelineMode == TimelineMode.TRACK) {
            if (ImGui.button("-##ZoomOut")) {
                timelineZoom = Math.max(0.25f, timelineZoom / 1.5f);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Zoom out");
            }
            ImGui.sameLine();
            ImGui.text(String.format("%.0f%%", timelineZoom * 100));
            ImGui.sameLine();
            if (ImGui.button("+##ZoomIn")) {
                timelineZoom = Math.min(4.0f, timelineZoom * 1.5f);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Zoom in");
            }
            ImGui.sameLine();
            if (ImGui.button("Fit##ZoomFit")) {
                fitTimelineToView();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Fit to view");
            }
            ImGui.sameLine();
        }

        // Mode toggle
        boolean isTrack = timelineMode == TimelineMode.TRACK;
        if (isTrack) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.7f, 1.0f);
        }
        if (ImGui.button("Track")) {
            timelineMode = TimelineMode.TRACK;
        }
        if (isTrack) {
            ImGui.popStyleColor();
        }

        ImGui.sameLine();

        boolean isStrip = timelineMode == TimelineMode.STRIP;
        if (isStrip) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.7f, 1.0f);
        }
        if (ImGui.button("Strip")) {
            timelineMode = TimelineMode.STRIP;
        }
        if (isStrip) {
            ImGui.popStyleColor();
        }
    }

    private float getCurrentTime() {
        if (editingAnimation == null || editingAnimation.getFrameCount() == 0) {
            return 0;
        }

        // Get current frame from preview renderer (which handles playback)
        int frame = previewRenderer != null ? previewRenderer.getCurrentPreviewFrame() : currentPreviewFrame;
        float timer = previewRenderer != null ? previewRenderer.getPreviewTimer() : 0f;

        float time = 0;
        for (int i = 0; i < frame && i < editingAnimation.getFrameCount(); i++) {
            time += editingAnimation.getFrame(i).duration();
        }
        return time + timer;
    }

    private void fitTimelineToView() {
        if (editingAnimation == null || editingAnimation.getTotalDuration() <= 0) {
            timelineZoom = 1.0f;
            return;
        }
        float availWidth = ImGui.getContentRegionAvailX() - 60;
        float totalDuration = editingAnimation.getTotalDuration();
        float targetPixelsPerSecond = availWidth / totalDuration;
        timelineZoom = targetPixelsPerSecond / PIXELS_PER_SECOND_DEFAULT;
        timelineZoom = Math.max(0.25f, Math.min(4.0f, timelineZoom));
        timelinePanX = 0;
    }

    // ========================================================================
    // STATE SYNC CALLBACKS
    // ========================================================================

    private void setSelectedFrameIndex(int index) {
        this.selectedFrameIndex = index;
    }

    private void setCurrentPreviewFrame(int frame) {
        this.currentPreviewFrame = frame;
        if (previewRenderer != null) {
            previewRenderer.setCurrentPreviewFrame(frame);
        }
    }

    private void resetPreviewTimer() {
        this.previewTimer = 0f;
        if (previewRenderer != null) {
            previewRenderer.resetPreviewTimer();
        }
    }

    // ========================================================================
    // ANIMATION OPERATIONS
    // ========================================================================

    private void selectAnimation(AnimationEntry entry) {
        selectedEntry = entry;
        editingAnimation = entry != null ? entry.animation : null;
        hasUnsavedChanges = false;
        // Select first frame if animation has frames, otherwise -1
        selectedFrameIndex = (editingAnimation != null && editingAnimation.getFrameCount() > 0) ? 0 : -1;
        currentPreviewFrame = 0;
        previewTimer = 0f;

        // Clear undo/redo when switching animations
        undoStack.clear();
        redoStack.clear();

        // Update preview renderer
        if (previewRenderer != null) {
            previewRenderer.setAnimation(editingAnimation);
        }

        recalculateMaxSpriteDimensions();
    }

    private void saveCurrentAnimation() {
        if (selectedEntry == null || editingAnimation == null) {
            return;
        }

        try {
            AnimationLoader loader = new AnimationLoader();
            java.nio.file.Path filePath = Paths.get(Assets.getAssetRoot(), selectedEntry.path);
            loader.save(editingAnimation, filePath.toString());
            hasUnsavedChanges = false;
            selectedEntry.modified = false;
            showStatus("Saved animation: " + selectedEntry.filename);
        } catch (IOException e) {
            System.err.println("[AnimationEditorPanel] Failed to save animation: " + e.getMessage());
            showStatus("Error saving animation: " + e.getMessage());
        }
    }

    private void addFrameToAnimation() {
        if (editingAnimation == null) return;

        captureUndoState();
        AnimationFrame newFrame = new AnimationFrame(AnimationFrame.EMPTY_SPRITE, 1.0f);
        editingAnimation.addFrame(newFrame);
        int newFrameIndex = editingAnimation.getFrameCount() - 1;
        selectedFrameIndex = newFrameIndex;
        markModified();

        // Auto-open sprite picker for the new frame
        openSpritePickerForFrame(newFrameIndex);
    }

    private void openSpritePickerForFrame(int frameIndex) {
        spritePicker.open(Sprite.class, null, selectedSprite -> {
            if (editingAnimation != null && frameIndex >= 0 && frameIndex < editingAnimation.getFrameCount()) {
                if (selectedSprite != null) {
                    String newPath = Assets.getPathForResource(selectedSprite);
                    if (newPath != null) {
                        AnimationFrame oldFrame = editingAnimation.getFrame(frameIndex);
                        editingAnimation.setFrame(frameIndex, new AnimationFrame(newPath, oldFrame.duration()));
                        recalculateMaxSpriteDimensions();
                        markModified();
                    }
                }
                // Note: Don't remove frame if cancelled - user can delete manually
            }
        });
    }

    private void deleteFrame(int frameIndex) {
        if (editingAnimation == null || frameIndex < 0 || frameIndex >= editingAnimation.getFrameCount()) {
            return;
        }

        captureUndoState();
        editingAnimation.removeFrame(frameIndex);

        // Adjust selection
        if (selectedFrameIndex >= editingAnimation.getFrameCount()) {
            selectedFrameIndex = editingAnimation.getFrameCount() - 1;
        }
        if (currentPreviewFrame >= editingAnimation.getFrameCount()) {
            currentPreviewFrame = Math.max(0, editingAnimation.getFrameCount() - 1);
            if (previewRenderer != null) {
                previewRenderer.setCurrentPreviewFrame(currentPreviewFrame);
            }
        }

        markModified();
        recalculateMaxSpriteDimensions();
    }

    private void deleteSelectedFrame() {
        deleteFrame(selectedFrameIndex);
    }

    private void markModified() {
        hasUnsavedChanges = true;
        if (selectedEntry != null) {
            selectedEntry.modified = true;
        }
    }

    private void recalculateMaxSpriteDimensions() {
        if (previewRenderer != null) {
            previewRenderer.recalculateMaxSpriteDimensions();
        }
    }

    // ========================================================================
    // UNDO/REDO
    // ========================================================================

    private void captureUndoState() {
        if (editingAnimation == null) return;

        redoStack.clear();
        undoStack.push(new AnimationState(editingAnimation, selectedFrameIndex));

        while (undoStack.size() > MAX_UNDO_HISTORY) {
            undoStack.removeLast();
        }
    }

    private void undo() {
        if (undoStack.isEmpty() || editingAnimation == null) return;

        redoStack.push(new AnimationState(editingAnimation, selectedFrameIndex));
        AnimationState state = undoStack.pop();
        state.restore(editingAnimation);
        selectedFrameIndex = Math.min(state.selectedFrameIndex, editingAnimation.getFrameCount() - 1);
        if (previewRenderer != null && editingAnimation.getFrameCount() > 0) {
            currentPreviewFrame = Math.min(currentPreviewFrame, editingAnimation.getFrameCount() - 1);
            previewRenderer.setCurrentPreviewFrame(currentPreviewFrame);
        }
        hasUnsavedChanges = true;
        recalculateMaxSpriteDimensions();
    }

    private void redo() {
        if (redoStack.isEmpty() || editingAnimation == null) return;

        undoStack.push(new AnimationState(editingAnimation, selectedFrameIndex));
        AnimationState state = redoStack.pop();
        state.restore(editingAnimation);
        selectedFrameIndex = Math.min(state.selectedFrameIndex, editingAnimation.getFrameCount() - 1);
        if (previewRenderer != null && editingAnimation.getFrameCount() > 0) {
            currentPreviewFrame = Math.min(currentPreviewFrame, editingAnimation.getFrameCount() - 1);
            previewRenderer.setCurrentPreviewFrame(currentPreviewFrame);
        }
        hasUnsavedChanges = true;
        recalculateMaxSpriteDimensions();
    }

    // ========================================================================
    // DIALOGS
    // ========================================================================

    private void openNewDialog() {
        if (hasUnsavedChanges) {
            pendingNewAnimation = true;
            showUnsavedChangesDialog = true;
        } else {
            showNewDialog = true;
            newAnimationName.set("new_animation");
            newAnimationSpritePath = null;
            newAnimationSpritePreview = null;
        }
    }

    private void renderNewAnimationDialog() {
        ImGui.openPopup("New Animation");
        if (ImGui.beginPopupModal("New Animation", new ImBoolean(true), ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Animation Name:");
            ImGui.setNextItemWidth(200);
            ImGui.inputText("##NewAnimName", newAnimationName);

            ImGui.spacing();

            // Sprite picker field
            ImGui.text("First Frame Sprite:");

            // Show sprite preview or placeholder
            if (newAnimationSpritePreview != null && newAnimationSpritePreview.getTexture() != null) {
                int texId = newAnimationSpritePreview.getTexture().getTextureId();
                float u0 = newAnimationSpritePreview.getU0();
                float v0 = newAnimationSpritePreview.getV0();
                float u1 = newAnimationSpritePreview.getU1();
                float v1 = newAnimationSpritePreview.getV1();
                // Flip V for OpenGL
                ImGui.image(texId, 48, 48, u0, v1, u1, v0);
                ImGui.sameLine();
                ImGui.beginGroup();
                String displayPath = newAnimationSpritePath;
                if (displayPath != null && displayPath.length() > 25) {
                    displayPath = "..." + displayPath.substring(displayPath.length() - 22);
                }
                ImGui.textDisabled(displayPath != null ? displayPath : "");
                if (ImGui.button(FontAwesomeIcons.Times + " Clear##ClearSprite")) {
                    newAnimationSpritePath = null;
                    newAnimationSpritePreview = null;
                }
                ImGui.endGroup();
            } else {
                ImGui.textColored(0.7f, 0.5f, 0.2f, 1f, "(No sprite selected)");
            }

            if (ImGui.button(FontAwesomeIcons.FolderOpen + " Browse...##BrowseNewSprite", 200, 0)) {
                // Close modal first, then open sprite picker after 2 frames delay
                showNewDialog = false;
                pendingSpritePickerForNewAnimDelay = 2;
                ImGui.closeCurrentPopup();
            }

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            // Create button - disabled if no sprite selected
            boolean canCreate = newAnimationSpritePath != null;
            if (!canCreate) {
                ImGui.beginDisabled();
            }
            if (ImGui.button("Create", 100, 0)) {
                createNewAnimation(newAnimationName.get(), newAnimationSpritePath);
                showNewDialog = false;
                ImGui.closeCurrentPopup();
            }
            if (!canCreate) {
                ImGui.endDisabled();
                if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
                    ImGui.setTooltip("Select a sprite first");
                }
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 100, 0)) {
                showNewDialog = false;
                newAnimationSpritePath = null;
                newAnimationSpritePreview = null;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void createNewAnimation(String name, String spritePath) {
        if (name == null || name.trim().isEmpty()) {
            name = "new_animation";
        }

        if (spritePath == null || spritePath.isEmpty()) {
            showStatus("Error: No sprite selected");
            return;
        }

        String baseName = name.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
        String filename = baseName + ".anim.json";
        String path = "animations/" + filename;
        java.nio.file.Path filePath = Paths.get(Assets.getAssetRoot(), path);

        // Check if file already exists and generate unique name
        int counter = 1;
        while (Files.exists(filePath)) {
            filename = baseName + "_" + counter + ".anim.json";
            path = "animations/" + filename;
            filePath = Paths.get(Assets.getAssetRoot(), path);
            counter++;
        }

        try {
            Files.createDirectories(filePath.getParent());

            // Use the unique name (without extension) as the animation name
            String animName = filename.replace(".anim.json", "");
            Animation newAnim = new Animation(animName);
            newAnim.addFrame(new AnimationFrame(spritePath, 1.0f));
            AnimationLoader loader = new AnimationLoader();
            loader.save(newAnim, filePath.toString());

            needsRefresh = true;
            refresh();

            // Select the new animation
            final String finalPath = path;
            for (AnimationEntry entry : animations) {
                if (entry.path.equals(finalPath)) {
                    selectAnimation(entry);
                    break;
                }
            }
            showStatus("Animation created: " + filename);
        } catch (IOException e) {
            System.err.println("[AnimationEditorPanel] Failed to create animation: " + e.getMessage());
            showStatus("Error creating animation: " + e.getMessage());
        }

        // Clear dialog state
        newAnimationSpritePath = null;
        newAnimationSpritePreview = null;
    }

    private void renderDeleteConfirmDialog() {
        ImGui.openPopup("Delete Animation?");
        if (ImGui.beginPopupModal("Delete Animation?", new ImBoolean(true), ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Are you sure you want to delete this animation?");
            if (selectedEntry != null) {
                ImGui.textColored(1f, 0.8f, 0.4f, 1f, selectedEntry.filename);
            }
            ImGui.text("This action cannot be undone.");

            ImGui.spacing();

            if (ImGui.button("Delete", 100, 0)) {
                deleteCurrentAnimation();
                showDeleteConfirmDialog = false;
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 100, 0)) {
                showDeleteConfirmDialog = false;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void deleteCurrentAnimation() {
        if (selectedEntry == null) return;

        String filename = selectedEntry.filename;
        try {
            java.nio.file.Path filePath = Paths.get(Assets.getAssetRoot(), selectedEntry.path);
            Files.deleteIfExists(filePath);

            animations.remove(selectedEntry);
            selectAnimation(null);
            showStatus("Deleted animation: " + filename);
        } catch (IOException e) {
            System.err.println("[AnimationEditorPanel] Failed to delete animation: " + e.getMessage());
            showStatus("Error deleting animation: " + e.getMessage());
        }
    }

    private void renderUnsavedChangesDialog() {
        ImGui.openPopup("Unsaved Changes");
        if (ImGui.beginPopupModal("Unsaved Changes", new ImBoolean(true), ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("You have unsaved changes to the current animation.");
            ImGui.text("What would you like to do?");

            ImGui.spacing();

            if (ImGui.button("Save", 100, 0)) {
                saveCurrentAnimation();
                showUnsavedChangesDialog = false;
                ImGui.closeCurrentPopup();
                proceedAfterUnsavedDialog();
            }
            ImGui.sameLine();
            if (ImGui.button("Discard", 100, 0)) {
                showUnsavedChangesDialog = false;
                ImGui.closeCurrentPopup();
                proceedAfterUnsavedDialog();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 100, 0)) {
                pendingAnimationSwitch = null;
                pendingNewAnimation = false;
                showUnsavedChangesDialog = false;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void proceedAfterUnsavedDialog() {
        if (pendingAnimationSwitch != null) {
            selectAnimation(pendingAnimationSwitch);
            pendingAnimationSwitch = null;
        } else if (pendingNewAnimation) {
            pendingNewAnimation = false;
            showNewDialog = true;
            newAnimationName.set("new_animation");
            newAnimationSpritePath = null;
            newAnimationSpritePreview = null;
        }
    }

    /**
     * Sets the callback for showing status messages (e.g., to StatusBar).
     */
    public void setStatusCallback(java.util.function.Consumer<String> callback) {
        this.statusCallback = callback;
    }

    /**
     * Selects an animation by its asset path, refreshing if needed.
     * Also focuses the Animation Editor window.
     * @param path the asset path (e.g., "animations/player_idle.anim.json")
     */
    public void selectAnimationByPath(String path) {
        // Focus the Animation Editor window
        ImGui.setWindowFocus("Animation Editor");

        refresh();
        for (AnimationEntry entry : animations) {
            if (entry.path.equals(path)) {
                if (hasUnsavedChanges) {
                    pendingAnimationSwitch = entry;
                    showUnsavedChangesDialog = true;
                } else {
                    selectAnimation(entry);
                }
                return;
            }
        }
        showStatus("Animation not found: " + path);
    }

    private void showStatus(String message) {
        if (statusCallback != null) {
            statusCallback.accept(message);
        }
    }

    /**
     * Cleans up resources used by this panel.
     */
    public void destroy() {
        // Currently no OpenGL resources to clean up
        previewRenderer = null;
        trackTimelineRenderer = null;
        stripTimelineRenderer = null;
    }
}
