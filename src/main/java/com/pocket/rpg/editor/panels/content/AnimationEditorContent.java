package com.pocket.rpg.editor.panels.content;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationFrame;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.panels.AssetCreationInfo;
import com.pocket.rpg.editor.panels.AssetEditorContent;
import com.pocket.rpg.editor.panels.AssetEditorShell;
import com.pocket.rpg.editor.panels.AssetPickerPopup;
import com.pocket.rpg.editor.panels.animation.AnimationPreviewRenderer;
import com.pocket.rpg.editor.panels.animation.AnimationTimelineContext;
import com.pocket.rpg.editor.panels.animation.StripTimelineRenderer;
import com.pocket.rpg.editor.panels.animation.TrackTimelineRenderer;
import com.pocket.rpg.editor.shortcut.KeyboardLayout;
import com.pocket.rpg.editor.shortcut.ShortcutAction;
import com.pocket.rpg.editor.shortcut.ShortcutBinding;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SnapshotCommand;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.loaders.AnimationLoader;
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
import java.util.List;

/**
 * Content implementation for editing .anim.json assets in the unified AssetEditorPanel.
 * <p>
 * Renders properties, current frame inspector, preview, and timeline.
 * The hamburger sidebar handles animation file selection.
 */
@EditorContentFor(com.pocket.rpg.animation.Animation.class)
public class AnimationEditorContent implements AssetEditorContent {

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    private static final AssetCreationInfo CREATION_INFO = new AssetCreationInfo("animations/", ".anim.json");
    private static final float PROPERTIES_WIDTH = 180f;
    private static final float CURRENT_FRAME_WIDTH = 280f;
    private static final float TIMELINE_HEIGHT = 160f;
    private static final float PIXELS_PER_SECOND_DEFAULT = 200f;
    private static final float MIN_FRAME_DURATION = 0.01f;

    // ========================================================================
    // STATE
    // ========================================================================

    private Animation editingAnimation;
    private AssetEditorShell shell;

    // Timeline selection
    private int selectedFrameIndex = -1;
    private int currentPreviewFrame = 0;
    private float previewTimer = 0f;

    // Frame editor
    private final ImFloat frameDurationInput = new ImFloat(0.1f);

    // Sprite picker for frame editing
    private final AssetPickerPopup spritePicker = new AssetPickerPopup();

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

    // Undo — deferred snapshot pattern
    private AnimationState pendingBeforeSnapshot = null;

    // Preview renderer
    private AnimationPreviewRenderer previewRenderer;
    private TrackTimelineRenderer trackTimelineRenderer;
    private StripTimelineRenderer stripTimelineRenderer;

    // New animation dialog
    private boolean showNewDialog = false;
    private final ImString newAnimationName = new ImString(64);
    private String newAnimationSpritePath = null;
    private Sprite newAnimationSpritePreview = null;
    private int pendingSpritePickerForNewAnimDelay = 0;

    // Delete confirmation dialog
    private boolean showDeleteConfirmDialog = false;

    // ========================================================================
    // INNER TYPES
    // ========================================================================

    private enum TimelineMode {
        TRACK("Track"),
        STRIP("Strip");

        final String label;

        TimelineMode(String label) {
            this.label = label;
        }
    }

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
    // LIFECYCLE
    // ========================================================================

    @Override
    public void initialize() {
        previewRenderer = new AnimationPreviewRenderer();
        previewRenderer.setOnPreviewFrameChanged(frame -> {
            // Keep timeline in sync with playback
        });
    }

    @Override
    public void destroy() {
        if (previewRenderer != null) {
            previewRenderer.destroy();
            previewRenderer = null;
        }
        trackTimelineRenderer = null;
        stripTimelineRenderer = null;
    }

    @Override
    public void onAssetLoaded(String path, Object asset, AssetEditorShell shell) {
        this.editingAnimation = (Animation) asset;
        this.shell = shell;

        selectedFrameIndex = (editingAnimation.getFrameCount() > 0) ? 0 : -1;
        currentPreviewFrame = 0;
        previewTimer = 0f;
        pendingBeforeSnapshot = null;

        if (previewRenderer != null) {
            previewRenderer.setAnimation(editingAnimation);
        }
        recalculateMaxSpriteDimensions();
    }

    @Override
    public void onAssetUnloaded() {
        editingAnimation = null;
        selectedFrameIndex = -1;
        currentPreviewFrame = 0;
        previewTimer = 0f;
        pendingBeforeSnapshot = null;

        if (previewRenderer != null) {
            previewRenderer.setAnimation(null);
        }
    }

    @Override
    public AssetCreationInfo getCreationInfo() {
        return CREATION_INFO;
    }

    @Override
    public Class<?> getAssetClass() {
        return Animation.class;
    }

    // ========================================================================
    // CONTENT INTERFACE
    // ========================================================================

    @Override
    public void render() {
        if (editingAnimation == null) return;

        flushPendingUndo();

        // Update pulse timer for play button animation
        if (previewRenderer != null && previewRenderer.isPlaying()) {
            pulseTimer += ImGui.getIO().getDeltaTime() * 3f;
        }

        renderMainContent();
    }

    @Override
    public void renderToolbarExtras() {
        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        // Playback controls
        renderPlaybackControls();

        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        // New
        if (ImGui.button(MaterialIcons.Add + " New##animNew")) {
            openNewDialog();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Create new animation (Ctrl+N)");
        }

        ImGui.sameLine();

        // Delete
        boolean canDelete = editingAnimation != null;
        if (!canDelete) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Delete + "##animDelete")) {
            showDeleteConfirmDialog = true;
        }
        if (!canDelete) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Delete current animation");
        }

        ImGui.sameLine();

        // Refresh
        if (ImGui.button(MaterialIcons.Sync + "##animRefresh")) {
            if (shell != null) shell.requestSidebarRefresh();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Refresh asset list (F5)");
        }
    }

    @Override
    public void renderPopups() {
        if (showNewDialog) {
            renderNewAnimationDialog();
        }
        if (showDeleteConfirmDialog) {
            renderDeleteConfirmDialog();
        }

        // Handle pending sprite picker for new animation (after modal is fully closed)
        if (pendingSpritePickerForNewAnimDelay > 0) {
            pendingSpritePickerForNewAnimDelay--;
            if (pendingSpritePickerForNewAnimDelay == 0) {
                spritePicker.open(Sprite.class, null, selectedAsset -> {
                    if (selectedAsset != null) {
                        newAnimationSpritePath = Assets.getPathForResource(selectedAsset);
                        if (selectedAsset instanceof Sprite sprite) {
                            newAnimationSpritePreview = sprite;
                        } else if (selectedAsset instanceof com.pocket.rpg.rendering.resources.Texture texture) {
                            newAnimationSpritePreview = new Sprite(texture);
                        }
                    }
                    showNewDialog = true;
                });
            }
        }

        // Sprite picker popup
        spritePicker.render();
    }

    @Override
    public List<ShortcutAction> provideExtraShortcuts(KeyboardLayout layout) {
        return List.of(
                ShortcutAction.builder()
                        .id("editor.animation.playPause")
                        .displayName("Play/Pause Animation")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.Space))
                        .handler(this::togglePlayback)
                        .build(),
                ShortcutAction.builder()
                        .id("editor.animation.prevFrame")
                        .displayName("Previous Frame")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.LeftArrow))
                        .handler(this::gotoPreviousFrame)
                        .build(),
                ShortcutAction.builder()
                        .id("editor.animation.nextFrame")
                        .displayName("Next Frame")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.RightArrow))
                        .handler(this::gotoNextFrame)
                        .build(),
                ShortcutAction.builder()
                        .id("editor.animation.firstFrame")
                        .displayName("First Frame")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.Home))
                        .handler(this::gotoFirstFrame)
                        .build(),
                ShortcutAction.builder()
                        .id("editor.animation.lastFrame")
                        .displayName("Last Frame")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.End))
                        .handler(this::gotoLastFrame)
                        .build(),
                ShortcutAction.builder()
                        .id("editor.animation.deleteFrame")
                        .displayName("Delete Frame")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.Delete))
                        .handler(this::deleteSelectedFrame)
                        .build()
        );
    }

    @Override
    public void onNewRequested() {
        openNewDialog();
    }

    @Override
    public boolean hasCustomSave() {
        return true;
    }

    @Override
    public void customSave(String path) {
        if (editingAnimation == null) return;
        try {
            AnimationLoader loader = new AnimationLoader();
            java.nio.file.Path filePath = Paths.get(Assets.getAssetRoot(), path);
            loader.save(editingAnimation, filePath.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save animation: " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // SHORTCUT HANDLERS
    // ========================================================================

    private void togglePlayback() {
        if (editingAnimation != null && previewRenderer != null) {
            previewRenderer.togglePlayback();
        }
    }

    private void gotoPreviousFrame() {
        if (editingAnimation != null && editingAnimation.getFrameCount() > 0) {
            if (previewRenderer != null) previewRenderer.setPlaying(false);
            currentPreviewFrame = Math.max(0, currentPreviewFrame - 1);
            selectedFrameIndex = currentPreviewFrame;
            previewTimer = 0;
            if (previewRenderer != null) previewRenderer.setCurrentPreviewFrame(currentPreviewFrame);
        }
    }

    private void gotoNextFrame() {
        if (editingAnimation != null && editingAnimation.getFrameCount() > 0) {
            if (previewRenderer != null) previewRenderer.setPlaying(false);
            currentPreviewFrame = Math.min(editingAnimation.getFrameCount() - 1, currentPreviewFrame + 1);
            selectedFrameIndex = currentPreviewFrame;
            previewTimer = 0;
            if (previewRenderer != null) previewRenderer.setCurrentPreviewFrame(currentPreviewFrame);
        }
    }

    private void gotoFirstFrame() {
        if (editingAnimation != null && editingAnimation.getFrameCount() > 0) {
            if (previewRenderer != null) previewRenderer.setPlaying(false);
            currentPreviewFrame = 0;
            selectedFrameIndex = 0;
            previewTimer = 0;
            if (previewRenderer != null) previewRenderer.setCurrentPreviewFrame(0);
        }
    }

    private void gotoLastFrame() {
        if (editingAnimation != null && editingAnimation.getFrameCount() > 0) {
            if (previewRenderer != null) previewRenderer.setPlaying(false);
            currentPreviewFrame = editingAnimation.getFrameCount() - 1;
            selectedFrameIndex = currentPreviewFrame;
            previewTimer = 0;
            if (previewRenderer != null) previewRenderer.setCurrentPreviewFrame(currentPreviewFrame);
        }
    }

    // ========================================================================
    // MAIN CONTENT
    // ========================================================================

    private void renderMainContent() {
        float availableHeight = ImGui.getContentRegionAvailY();
        // Reserve space for timeline + separator; account for table cell padding overhead
        float cellPaddingY = ImGui.getStyle().getCellPaddingY() * 2;
        float topSectionHeight = Math.max(100f,
                availableHeight - TIMELINE_HEIGHT - ImGui.getStyle().getItemSpacingY() * 2 - cellPaddingY);

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
                currentPreviewFrame = previewRenderer.getCurrentPreviewFrame();
            }
            ImGui.endChild();

            ImGui.endTable();
        }

        // Bottom section: Timeline — use remaining available space
        ImGui.separator();
        float timelineHeight = ImGui.getContentRegionAvailY();
        if (timelineHeight > 10f) {
            ImGui.beginChild("Timeline", 0, timelineHeight, false);
            renderTimeline();
            ImGui.endChild();
        }
    }

    // ========================================================================
    // PROPERTIES
    // ========================================================================

    private void renderProperties() {
        ImGui.text("Properties");
        ImGui.separator();

        if (editingAnimation == null) {
            ImGui.textDisabled("No animation selected");
            return;
        }

        // Show filename
        String filename = shell != null && shell.getEditingPath() != null
                ? extractFilename(shell.getEditingPath()) : "Unknown";
        if (shell != null && shell.isDirty()) {
            EditorColors.textColored(EditorColors.WARNING, filename + " *");
        } else {
            ImGui.text(filename);
        }

        ImGui.spacing();

        ImBoolean looping = new ImBoolean(editingAnimation.isLooping());
        if (ImGui.checkbox("Looping", looping)) {
            captureUndoState();
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
            EditorColors.textColored(EditorColors.WARNING, "(No sprite selected)");
        } else {
            String displayPath = spritePath.length() > 35 ? "..." + spritePath.substring(spritePath.length() - 32) : spritePath;
            ImGui.textDisabled(displayPath);
            if (ImGui.isItemHovered() && spritePath.length() > 35) {
                ImGui.setTooltip(spritePath);
            }
        }

        // Browse button
        if (ImGui.button(MaterialIcons.FolderOpen + " Browse...##BrowseSprite", -1, 0)) {
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
                        if (newPath == null) return;
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

        if (ImGui.button(MaterialIcons.Delete + " Delete Frame##DeleteFrame", -1, 0)) {
            deleteSelectedFrame();
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.textDisabled("Events");
        ImGui.textDisabled("(Coming soon)");
    }

    // ========================================================================
    // PLAYBACK CONTROLS (toolbar extras)
    // ========================================================================

    private void renderPlaybackControls() {
        boolean isPlaying = previewRenderer != null && previewRenderer.isPlaying();
        boolean canPlay = editingAnimation != null && editingAnimation.getFrameCount() > 0;

        if (!canPlay) ImGui.beginDisabled();

        // Play button (green when stopped)
        if (!isPlaying) {
            EditorColors.pushSuccessButton();
        }
        if (ImGui.button(MaterialIcons.PlayArrow + "##PlayBtn")) {
            if (previewRenderer != null) previewRenderer.play();
        }
        if (!isPlaying) {
            EditorColors.popButtonColors();
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
        if (ImGui.button(MaterialIcons.Stop + "##StopBtn")) {
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

        // Create context for renderers
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

        if (timelineMode == TimelineMode.TRACK) {
            trackTimelineRenderer = new TrackTimelineRenderer(
                    ctx,
                    this::addFrameToAnimation,
                    this::deleteFrame,
                    this::getCurrentTime,
                    () -> previewTimer
            );
            trackTimelineRenderer.render();
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

        // Sync state back from context
        selectedFrameIndex = ctx.getSelectedFrameIndex();
        currentPreviewFrame = ctx.getCurrentPreviewFrame();
        resizingFrameIndex = ctx.getResizingFrameIndex();
        resizeStartX = ctx.getResizeStartX();
        resizeStartDuration = ctx.getResizeStartDuration();
    }

    private void renderTimelineHeader() {
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
            EditorColors.pushInfoButton();
        }
        if (ImGui.button("Track")) {
            timelineMode = TimelineMode.TRACK;
        }
        if (isTrack) {
            EditorColors.popButtonColors();
        }

        ImGui.sameLine();

        boolean isStrip = timelineMode == TimelineMode.STRIP;
        if (isStrip) {
            EditorColors.pushInfoButton();
        }
        if (ImGui.button("Strip")) {
            timelineMode = TimelineMode.STRIP;
        }
        if (isStrip) {
            EditorColors.popButtonColors();
        }
    }

    private float getCurrentTime() {
        if (editingAnimation == null || editingAnimation.getFrameCount() == 0) return 0;

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

    private void addFrameToAnimation() {
        if (editingAnimation == null) return;

        captureUndoState();
        AnimationFrame newFrame = new AnimationFrame(AnimationFrame.EMPTY_SPRITE, 1.0f);
        editingAnimation.addFrame(newFrame);
        int newFrameIndex = editingAnimation.getFrameCount() - 1;
        selectedFrameIndex = newFrameIndex;
        markModified();

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
            }
        });
    }

    private void deleteFrame(int frameIndex) {
        if (editingAnimation == null || frameIndex < 0 || frameIndex >= editingAnimation.getFrameCount()) return;

        captureUndoState();
        editingAnimation.removeFrame(frameIndex);

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
        if (shell != null) shell.markDirty();
    }

    private void recalculateMaxSpriteDimensions() {
        if (previewRenderer != null) {
            previewRenderer.recalculateMaxSpriteDimensions();
        }
    }

    // ========================================================================
    // UNDO/REDO (deferred snapshot pattern)
    // ========================================================================

    private void captureUndoState() {
        flushPendingUndo();
        if (editingAnimation == null) return;
        pendingBeforeSnapshot = new AnimationState(editingAnimation, selectedFrameIndex);
    }

    private void flushPendingUndo() {
        if (pendingBeforeSnapshot == null || editingAnimation == null) return;
        AnimationState beforeSnapshot = pendingBeforeSnapshot;
        AnimationState afterSnapshot = new AnimationState(editingAnimation, selectedFrameIndex);
        pendingBeforeSnapshot = null;

        UndoManager um = UndoManager.getInstance();
        um.push(new SnapshotCommand<>(editingAnimation, beforeSnapshot, afterSnapshot,
                (target, snapshot) -> {
                    AnimationState state = (AnimationState) snapshot;
                    state.restore(target);
                    selectedFrameIndex = Math.min(state.selectedFrameIndex, target.getFrameCount() - 1);
                    if (previewRenderer != null && target.getFrameCount() > 0) {
                        currentPreviewFrame = Math.min(currentPreviewFrame, target.getFrameCount() - 1);
                        previewRenderer.setCurrentPreviewFrame(currentPreviewFrame);
                    }
                    markModified();
                    recalculateMaxSpriteDimensions();
                },
                "Edit animation"));
    }

    // ========================================================================
    // DIALOGS
    // ========================================================================

    private void openNewDialog() {
        if (shell != null) {
            shell.requestDirtyGuard(() -> {
                showNewDialog = true;
                newAnimationName.set("new_animation");
                newAnimationSpritePath = null;
                newAnimationSpritePreview = null;
            });
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

            if (newAnimationSpritePreview != null && newAnimationSpritePreview.getTexture() != null) {
                int texId = newAnimationSpritePreview.getTexture().getTextureId();
                float u0 = newAnimationSpritePreview.getU0();
                float v0 = newAnimationSpritePreview.getV0();
                float u1 = newAnimationSpritePreview.getU1();
                float v1 = newAnimationSpritePreview.getV1();
                ImGui.image(texId, 48, 48, u0, v1, u1, v0);
                ImGui.sameLine();
                ImGui.beginGroup();
                String displayPath = newAnimationSpritePath;
                if (displayPath != null && displayPath.length() > 25) {
                    displayPath = "..." + displayPath.substring(displayPath.length() - 22);
                }
                ImGui.textDisabled(displayPath != null ? displayPath : "");
                if (ImGui.button(MaterialIcons.Close + " Clear##ClearSprite")) {
                    newAnimationSpritePath = null;
                    newAnimationSpritePreview = null;
                }
                ImGui.endGroup();
            } else {
                EditorColors.textColored(EditorColors.WARNING, "(No sprite selected)");
            }

            if (ImGui.button(MaterialIcons.FolderOpen + " Browse...##BrowseNewSprite", 200, 0)) {
                showNewDialog = false;
                pendingSpritePickerForNewAnimDelay = 2;
                ImGui.closeCurrentPopup();
            }

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            boolean canCreate = newAnimationSpritePath != null;
            if (!canCreate) ImGui.beginDisabled();
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
        if (spritePath == null || spritePath.isEmpty()) {
            if (shell != null) shell.showStatus("Error: No sprite selected");
            return;
        }
        if (name == null || name.trim().isEmpty()) name = "new_animation";
        Animation anim = new Animation(name.trim());
        anim.addFrame(new AnimationFrame(spritePath, 1.0f));
        if (shell != null) shell.createAsset(name, anim);
        newAnimationSpritePath = null;
        newAnimationSpritePreview = null;
    }

    private void renderDeleteConfirmDialog() {
        ImGui.openPopup("Delete Animation?");
        if (ImGui.beginPopupModal("Delete Animation?", new ImBoolean(true), ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Are you sure you want to delete this animation?");
            if (shell != null && shell.getEditingPath() != null) {
                EditorColors.textColored(EditorColors.WARNING, extractFilename(shell.getEditingPath()));
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
        if (shell == null || shell.getEditingPath() == null) return;

        String path = shell.getEditingPath();
        String filename = extractFilename(path);
        try {
            java.nio.file.Path filePath = Paths.get(Assets.getAssetRoot(), path);
            Files.deleteIfExists(filePath);

            EditorEventBus.get().publish(new AssetChangedEvent(path, AssetChangedEvent.ChangeType.DELETED));

            shell.showStatus("Deleted animation: " + filename);
        } catch (IOException e) {
            System.err.println("[AnimationEditorContent] Failed to delete animation: " + e.getMessage());
            shell.showStatus("Error deleting animation: " + e.getMessage());
            return;
        }
        shell.clearEditingAsset();
    }

    // ========================================================================
    // UTILITIES
    // ========================================================================

    private String extractFilename(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
