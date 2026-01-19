package com.pocket.rpg.editor.panels.animation;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationFrame;
import com.pocket.rpg.rendering.resources.Sprite;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Shared context for timeline renderers.
 * Contains all state and callbacks needed to render and interact with animation timelines.
 */
public class AnimationTimelineContext {

    // Constants
    public static final String FRAME_DRAG_TYPE = "ANIM_FRAME";
    public static final float TRACK_HEIGHT = 60f;
    public static final float TIME_RULER_HEIGHT = 20f;
    public static final float PIXELS_PER_SECOND_DEFAULT = 200f;
    public static final float RESIZE_HANDLE_WIDTH = 6f;
    public static final float MIN_FRAME_DURATION = 0.01f;
    public static final float INSERTION_ZONE_WIDTH = 12f;
    public static final float FRAME_THUMBNAIL_SIZE = 48f;
    public static final float FRAME_SPACING = 8f;

    // Animation data
    private final Animation animation;
    private int selectedFrameIndex;
    private int currentPreviewFrame;

    // Timeline state
    private float timelineZoom;
    private float timelinePanX;

    // Resize state
    private int resizingFrameIndex = -1;
    private float resizeStartX = 0f;
    private float resizeStartDuration = 0f;

    // Deferred operations
    private int pendingFrameDelete = -1;
    private int[] pendingFrameMove = null;

    // Callbacks
    private final Runnable captureUndoState;
    private final Runnable markModified;
    private final Runnable recalculateMaxSpriteDimensions;
    private final IntConsumer setSelectedFrameIndex;
    private final IntConsumer setCurrentPreviewFrame;
    private final Runnable resetPreviewTimer;
    private final SpritePickerOpener spritePickerOpener;

    /**
     * Functional interface for opening sprite picker.
     */
    @FunctionalInterface
    public interface SpritePickerOpener {
        void open(String currentPath, Consumer<Sprite> onSelect);
    }

    public AnimationTimelineContext(
            Animation animation,
            int selectedFrameIndex,
            int currentPreviewFrame,
            float timelineZoom,
            float timelinePanX,
            int resizingFrameIndex,
            float resizeStartX,
            float resizeStartDuration,
            Runnable captureUndoState,
            Runnable markModified,
            Runnable recalculateMaxSpriteDimensions,
            IntConsumer setSelectedFrameIndex,
            IntConsumer setCurrentPreviewFrame,
            Runnable resetPreviewTimer,
            SpritePickerOpener spritePickerOpener
    ) {
        this.animation = animation;
        this.selectedFrameIndex = selectedFrameIndex;
        this.currentPreviewFrame = currentPreviewFrame;
        this.timelineZoom = timelineZoom;
        this.timelinePanX = timelinePanX;
        this.resizingFrameIndex = resizingFrameIndex;
        this.resizeStartX = resizeStartX;
        this.resizeStartDuration = resizeStartDuration;
        this.captureUndoState = captureUndoState;
        this.markModified = markModified;
        this.recalculateMaxSpriteDimensions = recalculateMaxSpriteDimensions;
        this.setSelectedFrameIndex = setSelectedFrameIndex;
        this.setCurrentPreviewFrame = setCurrentPreviewFrame;
        this.resetPreviewTimer = resetPreviewTimer;
        this.spritePickerOpener = spritePickerOpener;
    }

    // Getters
    public Animation getAnimation() { return animation; }
    public int getSelectedFrameIndex() { return selectedFrameIndex; }
    public int getCurrentPreviewFrame() { return currentPreviewFrame; }
    public float getTimelineZoom() { return timelineZoom; }
    public float getTimelinePanX() { return timelinePanX; }
    public int getResizingFrameIndex() { return resizingFrameIndex; }
    public float getResizeStartX() { return resizeStartX; }
    public float getResizeStartDuration() { return resizeStartDuration; }
    public int getPendingFrameDelete() { return pendingFrameDelete; }
    public int[] getPendingFrameMove() { return pendingFrameMove; }

    // Setters for mutable state
    public void setSelectedFrameIndex(int index) {
        this.selectedFrameIndex = index;
        setSelectedFrameIndex.accept(index);
    }

    public void setCurrentPreviewFrame(int frame) {
        this.currentPreviewFrame = frame;
        setCurrentPreviewFrame.accept(frame);
    }

    public void setTimelineZoom(float zoom) { this.timelineZoom = zoom; }
    public void setTimelinePanX(float panX) { this.timelinePanX = panX; }

    public void setResizingFrameIndex(int index) { this.resizingFrameIndex = index; }
    public void setResizeStartX(float x) { this.resizeStartX = x; }
    public void setResizeStartDuration(float duration) { this.resizeStartDuration = duration; }

    public void setPendingFrameDelete(int index) { this.pendingFrameDelete = index; }
    public void setPendingFrameMove(int[] move) { this.pendingFrameMove = move; }

    public void resetPreviewTimer() { resetPreviewTimer.run(); }

    // Callback invocations
    public void captureUndoState() { captureUndoState.run(); }
    public void markModified() { markModified.run(); }
    public void recalculateMaxSpriteDimensions() { recalculateMaxSpriteDimensions.run(); }

    public void openSpritePicker(String currentPath, Consumer<Sprite> onSelect) {
        spritePickerOpener.open(currentPath, onSelect);
    }

    // Helper methods
    public int getFrameCount() {
        return animation != null ? animation.getFrameCount() : 0;
    }

    public AnimationFrame getFrame(int index) {
        return animation != null ? animation.getFrame(index) : null;
    }

    public float getPixelsPerSecond() {
        return PIXELS_PER_SECOND_DEFAULT * timelineZoom;
    }

    /**
     * Gets sprite for a frame, safely handling null/missing sprites.
     */
    public Sprite getFrameSpriteSafe(int index) {
        if (animation == null || index < 0 || index >= animation.getFrameCount()) {
            return null;
        }
        AnimationFrame frame = animation.getFrame(index);
        if (!frame.hasSprite()) {
            return null;
        }
        try {
            return animation.getFrameSprite(index);
        } catch (Exception e) {
            return null;
        }
    }

    // Utility methods for byte conversion (for drag/drop payloads)
    public static byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >> 24),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value
        };
    }

    public static int bytesToInt(byte[] bytes) {
        if (bytes == null || bytes.length != 4) return -1;
        return ((bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) |
                (bytes[3] & 0xFF);
    }
}
