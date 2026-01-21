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
import imgui.flag.ImGuiWindowFlags;

import static com.pocket.rpg.editor.panels.animation.AnimationTimelineContext.*;

/**
 * Renders the strip-based timeline view for animations.
 * Shows frames as equal-sized thumbnails in a horizontal strip.
 */
public class StripTimelineRenderer {

    private final AnimationTimelineContext ctx;
    private final Runnable addFrameCallback;
    private final java.util.function.IntConsumer deleteFrameCallback;

    public StripTimelineRenderer(
            AnimationTimelineContext ctx,
            Runnable addFrameCallback,
            java.util.function.IntConsumer deleteFrameCallback
    ) {
        this.ctx = ctx;
        this.addFrameCallback = addFrameCallback;
        this.deleteFrameCallback = deleteFrameCallback;
    }

    /**
     * Renders the strip timeline.
     */
    public void render() {
        Animation animation = ctx.getAnimation();
        if (animation == null) {
            ImGui.textDisabled("No animation selected");
            return;
        }

        float availWidth = ImGui.getContentRegionAvailX();
        float frameWidth = FRAME_THUMBNAIL_SIZE + FRAME_SPACING;

        ImGui.beginChild("FrameStrip", availWidth, FRAME_THUMBNAIL_SIZE + 30, false, ImGuiWindowFlags.HorizontalScrollbar);

        ImDrawList drawList = ImGui.getWindowDrawList();
        ImVec2 cursorStart = ImGui.getCursorScreenPos();

        int frameCount = ctx.getFrameCount();

        // Indicator positions
        float insertionLineX = -1;
        float reorderLineX = -1;
        float indicatorY = cursorStart.y;

        // === PASS 1: Draw all frames ===
        for (int i = 0; i < frameCount; i++) {
            float frameX = cursorStart.x + i * frameWidth;
            float frameY = cursorStart.y;
            renderFrame(drawList, i, frameX, frameY);
        }

        // === PASS 2: Handle interactions and collect indicator positions ===
        for (int i = 0; i < frameCount; i++) {
            float frameX = cursorStart.x + i * frameWidth;
            float frameY = cursorStart.y;

            // Handle insertion zone
            float[] indicators = handleInsertionZone(i, frameX, frameY);
            if (indicators[0] >= 0) insertionLineX = indicators[0];
            if (indicators[1] >= 0) reorderLineX = indicators[1];

            // Handle frame interaction
            handleFrameInteraction(i, frameX, frameY);
        }

        // === FINAL INSERTION ZONE ===
        float lastFrameEnd = cursorStart.x + frameCount * frameWidth;
        float[] finalIndicators = handleFinalInsertionZone(frameCount, lastFrameEnd, cursorStart.y);
        if (finalIndicators[0] >= 0) insertionLineX = finalIndicators[0];
        if (finalIndicators[1] >= 0) reorderLineX = finalIndicators[1];

        // === PASS 3: Draw indicators ON TOP of frames ===
        drawIndicators(drawList, indicatorY, insertionLineX, reorderLineX);

        // Process deferred operations
        processDeferredOperations();

        // Add frame button
        renderAddButton(cursorStart.x + frameCount * frameWidth, cursorStart.y);

        ImGui.endChild();
    }

    private void renderFrame(ImDrawList drawList, int index, float frameX, float frameY) {
        // Selection highlight
        if (index == ctx.getSelectedFrameIndex()) {
            drawList.addRectFilled(
                    frameX - 2, frameY - 2,
                    frameX + FRAME_THUMBNAIL_SIZE + 2, frameY + FRAME_THUMBNAIL_SIZE + 18,
                    ImGui.colorConvertFloat4ToU32(0.3f, 0.5f, 0.8f, 0.5f)
            );
        }

        // Current preview frame marker
        if (index == ctx.getCurrentPreviewFrame()) {
            drawList.addRect(
                    frameX - 1, frameY - 1,
                    frameX + FRAME_THUMBNAIL_SIZE + 1, frameY + FRAME_THUMBNAIL_SIZE + 1,
                    ImGui.colorConvertFloat4ToU32(1f, 0.8f, 0f, 1f), 0, 0, 2f
            );
        }

        // Frame thumbnail background
        drawList.addRectFilled(
                frameX, frameY,
                frameX + FRAME_THUMBNAIL_SIZE, frameY + FRAME_THUMBNAIL_SIZE,
                ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
        );

        // Draw sprite thumbnail
        Sprite sprite = ctx.getFrameSpriteSafe(index);
        if (sprite != null && sprite.getTexture() != null) {
            float scale = Math.min(
                    FRAME_THUMBNAIL_SIZE / sprite.getWidth(),
                    FRAME_THUMBNAIL_SIZE / sprite.getHeight()
            );
            float displayW = sprite.getWidth() * scale;
            float displayH = sprite.getHeight() * scale;
            float offsetX = (FRAME_THUMBNAIL_SIZE - displayW) / 2;
            float offsetY = (FRAME_THUMBNAIL_SIZE - displayH) / 2;

            drawList.addImage(
                    sprite.getTexture().getTextureId(),
                    frameX + offsetX, frameY + offsetY,
                    frameX + offsetX + displayW, frameY + offsetY + displayH,
                    sprite.getU0(), sprite.getV1(), sprite.getU1(), sprite.getV0()
            );
        } else {
            drawList.addText(
                    frameX + 4, frameY + FRAME_THUMBNAIL_SIZE / 2 - 6,
                    ImGui.colorConvertFloat4ToU32(0.7f, 0.5f, 0.2f, 1f), "Empty"
            );
        }

        // Duration and frame number label
        AnimationFrame frame = ctx.getFrame(index);
        String label = String.format("%d: %.2fs", index + 1, frame.duration());
        drawList.addText(frameX, frameY + FRAME_THUMBNAIL_SIZE + 2,
                ImGui.colorConvertFloat4ToU32(0.7f, 0.7f, 0.7f, 1f), label);
    }

    private float[] handleInsertionZone(int index, float frameX, float frameY) {
        float insertionLineX = -1;
        float reorderLineX = -1;

        float insertZoneX = frameX - FRAME_SPACING / 2 - INSERTION_ZONE_WIDTH / 2;
        float insertLineX = frameX - FRAME_SPACING / 2;
        ImGui.setCursorScreenPos(insertZoneX, frameY);
        ImGui.invisibleButton("strip_insert_" + index, INSERTION_ZONE_WIDTH, FRAME_THUMBNAIL_SIZE + 16);

        if (ImGui.beginDragDropTarget()) {
            // Asset browser drops
            byte[] peekSprite = ImGui.acceptDragDropPayload(AssetDragPayload.DRAG_TYPE, ImGuiDragDropFlags.AcceptPeekOnly);
            if (peekSprite != null) {
                insertionLineX = insertLineX;
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
                    reorderLineX = insertLineX;
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

    private void handleFrameInteraction(int index, float frameX, float frameY) {
        ImGui.setCursorScreenPos(frameX, frameY);
        if (ImGui.invisibleButton("strip_frame_" + index, FRAME_THUMBNAIL_SIZE, FRAME_THUMBNAIL_SIZE + 16)) {
            ctx.setSelectedFrameIndex(index);
            ctx.setCurrentPreviewFrame(index);
            ctx.resetPreviewTimer();
        }

        // Drag source for reordering
        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceAllowNullID)) {
            ImGui.setDragDropPayload(FRAME_DRAG_TYPE, intToBytes(index));
            ImGui.text("Frame " + (index + 1));
            ImGui.endDragDropSource();
        }

        // Context menu
        AnimationFrame frame = ctx.getFrame(index);
        if (ImGui.beginPopupContextItem("strip_frame_ctx_" + index)) {
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

    private float[] handleFinalInsertionZone(int frameCount, float lastFrameEnd, float cursorY) {
        float insertionLineX = -1;
        float reorderLineX = -1;

        float finalInsertX = lastFrameEnd - FRAME_SPACING / 2 - INSERTION_ZONE_WIDTH / 2;
        float finalLineX = lastFrameEnd - FRAME_SPACING / 2;
        ImGui.setCursorScreenPos(finalInsertX, cursorY);
        ImGui.invisibleButton("strip_insert_end", INSERTION_ZONE_WIDTH, FRAME_THUMBNAIL_SIZE + 16);

        if (ImGui.beginDragDropTarget()) {
            byte[] peekSprite = ImGui.acceptDragDropPayload(AssetDragPayload.DRAG_TYPE, ImGuiDragDropFlags.AcceptPeekOnly);
            if (peekSprite != null) {
                insertionLineX = finalLineX;
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
                    reorderLineX = finalLineX;
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

    private void drawIndicators(ImDrawList drawList, float indicatorY, float insertionLineX, float reorderLineX) {
        if (insertionLineX >= 0) {
            int color = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 1.0f, 1f);
            drawList.addLine(insertionLineX, indicatorY - 4, insertionLineX, indicatorY + FRAME_THUMBNAIL_SIZE + 20, color, 4f);
            drawList.addTriangleFilled(
                    insertionLineX - 6, indicatorY - 4,
                    insertionLineX + 6, indicatorY - 4,
                    insertionLineX, indicatorY + 4,
                    color
            );
        }

        if (reorderLineX >= 0) {
            int color = ImGui.colorConvertFloat4ToU32(0.2f, 1.0f, 0.4f, 1f);
            drawList.addLine(reorderLineX, indicatorY - 4, reorderLineX, indicatorY + FRAME_THUMBNAIL_SIZE + 20, color, 4f);
            drawList.addTriangleFilled(
                    reorderLineX - 6, indicatorY - 4,
                    reorderLineX + 6, indicatorY - 4,
                    reorderLineX, indicatorY + 4,
                    color
            );
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

    private void renderAddButton(float addX, float addY) {
        ImGui.setCursorScreenPos(addX, addY);
        if (ImGui.button(FontAwesomeIcons.Plus + "##AddStripFrame", FRAME_THUMBNAIL_SIZE, FRAME_THUMBNAIL_SIZE)) {
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
}
