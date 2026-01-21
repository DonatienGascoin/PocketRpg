package com.pocket.rpg.editor.panels.animation;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationFrame;
import com.pocket.rpg.editor.assets.AssetDragPayload;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.Assets;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiWindowFlags;

import static com.pocket.rpg.editor.panels.animation.AnimationTimelineContext.*;

/**
 * Renders the track-based timeline view for animations.
 * Shows frames as proportional width boxes based on duration.
 */
public class TrackTimelineRenderer {

    private final AnimationTimelineContext ctx;

    // Callback for adding frames
    private final Runnable addFrameCallback;

    // Callback for deleting frames
    private final java.util.function.IntConsumer deleteFrameCallback;

    // Current playhead time provider
    private final java.util.function.Supplier<Float> currentTimeProvider;

    // Preview timer provider
    private final java.util.function.Supplier<Float> previewTimerProvider;

    public TrackTimelineRenderer(
            AnimationTimelineContext ctx,
            Runnable addFrameCallback,
            java.util.function.IntConsumer deleteFrameCallback,
            java.util.function.Supplier<Float> currentTimeProvider,
            java.util.function.Supplier<Float> previewTimerProvider
    ) {
        this.ctx = ctx;
        this.addFrameCallback = addFrameCallback;
        this.deleteFrameCallback = deleteFrameCallback;
        this.currentTimeProvider = currentTimeProvider;
        this.previewTimerProvider = previewTimerProvider;
    }

    /**
     * Renders the track timeline.
     */
    public void render() {
        Animation animation = ctx.getAnimation();
        if (animation == null) {
            ImGui.textDisabled("No animation selected");
            return;
        }

        float availWidth = ImGui.getContentRegionAvailX();
        float pixelsPerSecond = ctx.getPixelsPerSecond();
        float totalDuration = animation.getTotalDuration();
        float contentWidth = Math.max(totalDuration * pixelsPerSecond + 100, availWidth);

        ImGui.beginChild("TrackTimeline", availWidth, TRACK_HEIGHT + TIME_RULER_HEIGHT + 10, false,
                ImGuiWindowFlags.HorizontalScrollbar);

        // Check if any drag operation is active (to prevent resize cursor during drag)
        boolean isDragging = ImGui.getIO().getMouseDown(0) &&
                (ImGui.getDragDropPayload() != null || ctx.getResizingFrameIndex() >= 0);

        // Handle mouse wheel zoom (no Ctrl required)
        if (ImGui.isWindowHovered()) {
            float wheel = ImGui.getIO().getMouseWheel();
            if (wheel != 0) {
                float newZoom = ctx.getTimelineZoom() * (wheel > 0 ? 1.2f : 0.8f);
                ctx.setTimelineZoom(Math.max(0.25f, Math.min(4.0f, newZoom)));
            }

            // Middle mouse drag for panning
            if (ImGui.isMouseDragging(2)) {
                float newPanX = ctx.getTimelinePanX() - ImGui.getIO().getMouseDeltaX();
                ctx.setTimelinePanX(Math.max(0, Math.min(contentWidth - availWidth, newPanX)));
            }
        }

        ImDrawList drawList = ImGui.getWindowDrawList();
        ImVec2 cursorStart = ImGui.getCursorScreenPos();
        float startX = cursorStart.x - ctx.getTimelinePanX();
        float startY = cursorStart.y;

        // Draw time ruler
        renderTimeRuler(drawList, startX, startY, contentWidth, pixelsPerSecond);

        float trackY = startY + TIME_RULER_HEIGHT;
        int frameCount = ctx.getFrameCount();

        // Reset indicator tracking
        float insertionLineX = -1;
        float reorderLineX = -1;

        // === PASS 1: Draw all frames ===
        float currentX = startX;
        for (int i = 0; i < frameCount; i++) {
            AnimationFrame frame = ctx.getFrame(i);
            float frameWidth = frame.duration() * pixelsPerSecond;
            float frameEndX = currentX + frameWidth;

            renderFrame(drawList, i, currentX, trackY, frameWidth, frameEndX, pixelsPerSecond);
            currentX = frameEndX;
        }

        // === PASS 2: Handle interactions and collect indicator positions ===
        currentX = startX;
        for (int i = 0; i < frameCount; i++) {
            AnimationFrame frame = ctx.getFrame(i);
            float frameWidth = frame.duration() * pixelsPerSecond;
            float frameEndX = currentX + frameWidth;

            // Handle insertion zone
            float[] indicators = handleInsertionZone(i, currentX, trackY);
            if (indicators[0] >= 0) insertionLineX = indicators[0];
            if (indicators[1] >= 0) reorderLineX = indicators[1];

            // Handle frame interactions
            handleFrameInteraction(drawList, i, frame, currentX, trackY, frameWidth, frameEndX, isDragging);

            currentX = frameEndX;
        }

        // === FINAL INSERTION ZONE ===
        float[] finalIndicators = handleFinalInsertionZone(frameCount, currentX, trackY);
        if (finalIndicators[0] >= 0) insertionLineX = finalIndicators[0];
        if (finalIndicators[1] >= 0) reorderLineX = finalIndicators[1];

        // === PASS 3: Draw indicators ON TOP of frames ===
        drawIndicators(drawList, trackY, insertionLineX, reorderLineX);

        // Handle active resize drag
        handleResizeDrag(pixelsPerSecond);

        // Process deferred operations
        processDeferredOperations();

        // Add frame button
        renderAddButton(currentX, trackY);

        // Draw playhead
        renderPlayhead(drawList, startX, startY, trackY, pixelsPerSecond);

        ImGui.endChild();
    }

    private void renderFrame(ImDrawList drawList, int index, float x, float y, float width, float endX, float pixelsPerSecond) {
        boolean isSelected = index == ctx.getSelectedFrameIndex();
        int bgColor = isSelected
                ? ImGui.colorConvertFloat4ToU32(0.3f, 0.5f, 0.8f, 0.8f)
                : ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.3f, 1f);

        drawList.addRectFilled(x, y, endX, y + TRACK_HEIGHT, bgColor);
        drawList.addRect(x, y, endX, y + TRACK_HEIGHT,
                ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.5f, 1f));

        // Draw sprite thumbnail
        Sprite sprite = ctx.getFrameSpriteSafe(index);
        float thumbSize = TRACK_HEIGHT - 16;
        float thumbX = x + (width - thumbSize) / 2;
        float thumbY = y + 4;

        if (sprite != null && sprite.getTexture() != null && width > 20) {
            float scale = Math.min(thumbSize / sprite.getWidth(), thumbSize / sprite.getHeight());
            float displayW = sprite.getWidth() * scale;
            float displayH = sprite.getHeight() * scale;
            float offsetX = (thumbSize - displayW) / 2;
            float offsetY = (thumbSize - displayH) / 2;

            drawList.addImage(
                    sprite.getTexture().getTextureId(),
                    thumbX + offsetX, thumbY + offsetY,
                    thumbX + offsetX + displayW, thumbY + offsetY + displayH,
                    sprite.getU0(), sprite.getV1(), sprite.getU1(), sprite.getV0()
            );
        } else if (width > 30) {
            drawList.addText(x + 4, y + TRACK_HEIGHT / 2 - 6,
                    ImGui.colorConvertFloat4ToU32(0.7f, 0.5f, 0.2f, 1f), "Empty");
        }

        // Frame number
        if (width > 30) {
            String label = String.format("%d", index + 1);
            drawList.addText(x + 4, y + TRACK_HEIGHT - 14,
                    ImGui.colorConvertFloat4ToU32(0.7f, 0.7f, 0.7f, 1f), label);
        }

        // Current frame indicator (orange border)
        if (index == ctx.getCurrentPreviewFrame()) {
            drawList.addRect(x + 1, y + 1, endX - 1, y + TRACK_HEIGHT - 1,
                    ImGui.colorConvertFloat4ToU32(1f, 0.6f, 0f, 1f), 0, 0, 2f);
        }
    }

    private float[] handleInsertionZone(int index, float currentX, float trackY) {
        float insertionLineX = -1;
        float reorderLineX = -1;

        float insertZoneX = currentX - INSERTION_ZONE_WIDTH / 2;
        ImGui.setCursorScreenPos(insertZoneX, trackY);
        ImGui.invisibleButton("insert_zone_" + index, INSERTION_ZONE_WIDTH, TRACK_HEIGHT);

        if (ImGui.beginDragDropTarget()) {
            // Asset browser drops
            byte[] peekSprite = ImGui.acceptDragDropPayload(AssetDragPayload.DRAG_TYPE, ImGuiDragDropFlags.AcceptPeekOnly);
            if (peekSprite != null) {
                insertionLineX = currentX;
            }

            byte[] spritePayload = ImGui.acceptDragDropPayload(AssetDragPayload.DRAG_TYPE);
            if (spritePayload != null && !AssetDragPayload.isDragCancelled()) {
                AssetDragPayload payload = AssetDragPayload.deserialize(spritePayload);
                if (payload != null && payload.type() == Sprite.class) {
                    ctx.captureUndoState();
                    AnimationFrame newFrame = new AnimationFrame(payload.path(), 1.0f);
                    ctx.getAnimation().addFrame(index, newFrame);
                    ctx.setSelectedFrameIndex(index);
                    ctx.markModified();
                    ctx.recalculateMaxSpriteDimensions();
                }
            }

            // Frame reorder drops
            byte[] peekFrame = ImGui.acceptDragDropPayload(FRAME_DRAG_TYPE, ImGuiDragDropFlags.AcceptPeekOnly);
            if (peekFrame != null && peekFrame.length == 4) {
                int sourceIndex = bytesToInt(peekFrame);
                if (sourceIndex != index && sourceIndex != index - 1) {
                    reorderLineX = currentX;
                }
            }

            byte[] framePayload = ImGui.acceptDragDropPayload(FRAME_DRAG_TYPE);
            if (framePayload != null && framePayload.length == 4) {
                int sourceIndex = bytesToInt(framePayload);
                int targetIndex = (sourceIndex < index) ? index - 1 : index;
                if (targetIndex != sourceIndex) {
                    ctx.setPendingFrameMove(new int[]{sourceIndex, targetIndex});
                }
            }
            ImGui.endDragDropTarget();
        }

        return new float[]{insertionLineX, reorderLineX};
    }

    private void handleFrameInteraction(ImDrawList drawList, int index, AnimationFrame frame,
                                        float currentX, float trackY, float frameWidth, float frameEndX,
                                        boolean isDragging) {
        ImGui.setCursorScreenPos(currentX, trackY);
        ImVec2 mousePos = ImGui.getMousePos();
        boolean inResizeZone = mousePos.x >= frameEndX - RESIZE_HANDLE_WIDTH &&
                mousePos.x <= frameEndX + 2 &&
                mousePos.y >= trackY && mousePos.y <= trackY + TRACK_HEIGHT;

        // Resize handle visual
        if ((inResizeZone || ctx.getResizingFrameIndex() == index) && !isDragging) {
            drawList.addRectFilled(frameEndX - RESIZE_HANDLE_WIDTH, trackY,
                    frameEndX, trackY + TRACK_HEIGHT,
                    ImGui.colorConvertFloat4ToU32(0.5f, 0.7f, 1.0f, 0.5f));
        }

        // Handle resizing BEFORE invisible button (so click isn't consumed)
        if (inResizeZone && !isDragging) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
            if (ImGui.isMouseClicked(0)) {
                ctx.captureUndoState();
                ctx.setResizingFrameIndex(index);
                ctx.setResizeStartX(mousePos.x);
                ctx.setResizeStartDuration(frame.duration());
            }
        }

        if (ImGui.invisibleButton("track_frame_" + index, frameWidth, TRACK_HEIGHT)) {
            if (!inResizeZone && ctx.getResizingFrameIndex() < 0) {
                ctx.setSelectedFrameIndex(index);
                ctx.setCurrentPreviewFrame(index);
                ctx.resetPreviewTimer();
            }
        }

        // Drag source for reordering (disabled while resizing)
        if (!inResizeZone && ctx.getResizingFrameIndex() < 0 && ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceAllowNullID)) {
            ImGui.setDragDropPayload(FRAME_DRAG_TYPE, intToBytes(index));
            ImGui.text("Frame " + (index + 1));
            ImGui.endDragDropSource();
        }

        // Context menu
        if (ImGui.beginPopupContextItem("track_frame_ctx_" + index)) {
            if (ImGui.menuItem(FontAwesomeIcons.Image + " Change Sprite...")) {
                final int frameIdx = index;
                ctx.openSpritePicker(frame.spritePath(), selectedSprite -> {
                    Animation anim = ctx.getAnimation();
                    if (anim != null && frameIdx >= 0 && frameIdx < anim.getFrameCount()) {
                        AnimationFrame oldFrame = anim.getFrame(frameIdx);
                        String newPath;
                        if (selectedSprite == null) {
                            newPath = AnimationFrame.EMPTY_SPRITE;
                        } else {
                            newPath = Assets.getPathForResource(selectedSprite);
                            if (newPath == null) return;
                        }
                        ctx.captureUndoState();
                        anim.setFrame(frameIdx, new AnimationFrame(newPath, oldFrame.duration()));
                        ctx.markModified();
                        ctx.recalculateMaxSpriteDimensions();
                    }
                });
            }
            ImGui.separator();
            if (ImGui.menuItem(FontAwesomeIcons.Trash + " Delete Frame")) {
                ctx.setPendingFrameDelete(index);
            }
            ImGui.endPopup();
        }
    }

    private float[] handleFinalInsertionZone(int frameCount, float currentX, float trackY) {
        float insertionLineX = -1;
        float reorderLineX = -1;

        float finalInsertX = currentX - INSERTION_ZONE_WIDTH / 2;
        ImGui.setCursorScreenPos(finalInsertX, trackY);
        ImGui.invisibleButton("insert_zone_end", INSERTION_ZONE_WIDTH, TRACK_HEIGHT);

        if (ImGui.beginDragDropTarget()) {
            byte[] peekSprite = ImGui.acceptDragDropPayload(AssetDragPayload.DRAG_TYPE, ImGuiDragDropFlags.AcceptPeekOnly);
            if (peekSprite != null) {
                insertionLineX = currentX;
            }

            byte[] spritePayload = ImGui.acceptDragDropPayload(AssetDragPayload.DRAG_TYPE);
            if (spritePayload != null && !AssetDragPayload.isDragCancelled()) {
                AssetDragPayload payload = AssetDragPayload.deserialize(spritePayload);
                if (payload != null && payload.type() == Sprite.class) {
                    ctx.captureUndoState();
                    AnimationFrame newFrame = new AnimationFrame(payload.path(), 1.0f);
                    ctx.getAnimation().addFrame(newFrame);
                    ctx.setSelectedFrameIndex(ctx.getFrameCount() - 1);
                    ctx.markModified();
                    ctx.recalculateMaxSpriteDimensions();
                }
            }

            byte[] peekFrame = ImGui.acceptDragDropPayload(FRAME_DRAG_TYPE, ImGuiDragDropFlags.AcceptPeekOnly);
            if (peekFrame != null && peekFrame.length == 4) {
                int sourceIndex = bytesToInt(peekFrame);
                if (sourceIndex != frameCount - 1) {
                    reorderLineX = currentX;
                }
            }

            byte[] framePayload = ImGui.acceptDragDropPayload(FRAME_DRAG_TYPE);
            if (framePayload != null && framePayload.length == 4) {
                int sourceIndex = bytesToInt(framePayload);
                int targetIndex = frameCount - 1;
                if (targetIndex != sourceIndex) {
                    ctx.setPendingFrameMove(new int[]{sourceIndex, targetIndex});
                }
            }
            ImGui.endDragDropTarget();
        }

        return new float[]{insertionLineX, reorderLineX};
    }

    private void drawIndicators(ImDrawList drawList, float trackY, float insertionLineX, float reorderLineX) {
        if (insertionLineX >= 0) {
            int color = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 1.0f, 1f);
            drawList.addLine(insertionLineX, trackY - 4, insertionLineX, trackY + TRACK_HEIGHT + 4, color, 4f);
            drawList.addTriangleFilled(
                    insertionLineX - 6, trackY - 4,
                    insertionLineX + 6, trackY - 4,
                    insertionLineX, trackY + 4,
                    color
            );
        }

        if (reorderLineX >= 0) {
            int color = ImGui.colorConvertFloat4ToU32(0.2f, 1.0f, 0.4f, 1f);
            drawList.addLine(reorderLineX, trackY - 4, reorderLineX, trackY + TRACK_HEIGHT + 4, color, 4f);
            drawList.addTriangleFilled(
                    reorderLineX - 6, trackY - 4,
                    reorderLineX + 6, trackY - 4,
                    reorderLineX, trackY + 4,
                    color
            );
        }
    }

    private void handleResizeDrag(float pixelsPerSecond) {
        if (ctx.getResizingFrameIndex() >= 0) {
            // Keep resize cursor while dragging
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);

            ImVec2 mousePos = ImGui.getMousePos();
            float delta = (mousePos.x - ctx.getResizeStartX()) / pixelsPerSecond;
            float newDuration = Math.max(MIN_FRAME_DURATION, ctx.getResizeStartDuration() + delta);

            AnimationFrame oldFrame = ctx.getFrame(ctx.getResizingFrameIndex());
            ctx.getAnimation().setFrame(ctx.getResizingFrameIndex(),
                    new AnimationFrame(oldFrame.spritePath(), newDuration));

            if (ImGui.isMouseReleased(0)) {
                ctx.markModified();
                ctx.setResizingFrameIndex(-1);
            }
        }
    }

    private void processDeferredOperations() {
        int[] pendingMove = ctx.getPendingFrameMove();
        if (pendingMove != null) {
            ctx.captureUndoState();
            ctx.getAnimation().moveFrame(pendingMove[0], pendingMove[1]);
            ctx.setSelectedFrameIndex(pendingMove[1]);
            ctx.markModified();
            ctx.setPendingFrameMove(null);
        }

        int pendingDelete = ctx.getPendingFrameDelete();
        if (pendingDelete >= 0) {
            deleteFrameCallback.accept(pendingDelete);
            ctx.setPendingFrameDelete(-1);
        }
    }

    private void renderAddButton(float currentX, float trackY) {
        ImGui.setCursorScreenPos(currentX + INSERTION_ZONE_WIDTH, trackY);
        if (ImGui.button(FontAwesomeIcons.Plus + "##AddTrackFrame", 40, TRACK_HEIGHT)) {
            addFrameCallback.run();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Add frame");
        }

        if (ImGui.beginDragDropTarget()) {
            byte[] spritePayload = ImGui.acceptDragDropPayload(AssetDragPayload.DRAG_TYPE);
            if (spritePayload != null && !AssetDragPayload.isDragCancelled()) {
                AssetDragPayload payload = AssetDragPayload.deserialize(spritePayload);
                if (payload != null && payload.type() == Sprite.class) {
                    ctx.captureUndoState();
                    AnimationFrame newFrame = new AnimationFrame(payload.path(), 1.0f);
                    ctx.getAnimation().addFrame(newFrame);
                    ctx.setSelectedFrameIndex(ctx.getFrameCount() - 1);
                    ctx.markModified();
                    ctx.recalculateMaxSpriteDimensions();
                }
            }
            ImGui.endDragDropTarget();
        }
    }

    private void renderPlayhead(ImDrawList drawList, float startX, float startY, float trackY, float pixelsPerSecond) {
        if (ctx.getFrameCount() > 0) {
            float playheadTime = currentTimeProvider.get();
            float playheadX = startX + playheadTime * pixelsPerSecond;
            drawList.addTriangleFilled(
                    playheadX - 6, startY,
                    playheadX + 6, startY,
                    playheadX, startY + 10,
                    ImGui.colorConvertFloat4ToU32(1f, 0.3f, 0.3f, 1f)
            );
            drawList.addLine(playheadX, startY + 10, playheadX, trackY + TRACK_HEIGHT,
                    ImGui.colorConvertFloat4ToU32(1f, 0.3f, 0.3f, 0.8f), 2f);
        }
    }

    private void renderTimeRuler(ImDrawList drawList, float startX, float startY, float width, float pixelsPerSecond) {
        drawList.addRectFilled(startX, startY, startX + width, startY + TIME_RULER_HEIGHT,
                ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.18f, 1f));

        float minPixelsBetweenLabels = 50f;
        float secondsPerMajorTick;

        float[] intervals = {0.01f, 0.02f, 0.05f, 0.1f, 0.2f, 0.5f, 1.0f, 2.0f, 5.0f, 10.0f};
        secondsPerMajorTick = 1.0f;
        for (float interval : intervals) {
            if (interval * pixelsPerSecond >= minPixelsBetweenLabels) {
                secondsPerMajorTick = interval;
                break;
            }
        }

        float tickSpacing = secondsPerMajorTick * pixelsPerSecond;
        int tickCount = (int) (width / tickSpacing) + 1;

        for (int i = 0; i <= tickCount; i++) {
            float x = startX + i * tickSpacing;
            float time = i * secondsPerMajorTick;

            drawList.addLine(x, startY + TIME_RULER_HEIGHT - 8, x, startY + TIME_RULER_HEIGHT,
                    ImGui.colorConvertFloat4ToU32(0.6f, 0.6f, 0.6f, 1f));

            String label;
            if (secondsPerMajorTick >= 1.0f) {
                label = String.format("%.0fs", time);
            } else if (secondsPerMajorTick >= 0.1f) {
                label = String.format("%.1fs", time);
            } else {
                label = String.format("%.2fs", time);
            }
            drawList.addText(x + 2, startY + 2,
                    ImGui.colorConvertFloat4ToU32(0.7f, 0.7f, 0.7f, 1f), label);

            if (tickSpacing > 40) {
                int minorCount = secondsPerMajorTick >= 0.5f ? 5 : (secondsPerMajorTick >= 0.1f ? 2 : 1);
                float minorSpacing = tickSpacing / minorCount;
                for (int j = 1; j < minorCount; j++) {
                    float minorX = x + j * minorSpacing;
                    drawList.addLine(minorX, startY + TIME_RULER_HEIGHT - 4, minorX, startY + TIME_RULER_HEIGHT,
                            ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 1f));
                }
            }
        }
    }
}
