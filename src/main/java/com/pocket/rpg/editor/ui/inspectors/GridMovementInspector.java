package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.components.pokemon.GridMovement;
import com.pocket.rpg.components.pokemon.GridMovement.AnchorMode;
import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.editor.undo.UndoManager;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Custom inspector for GridMovement component.
 * <p>
 * Shows configuration fields (speed, jump height, tile size, z-level, facing)
 * and a grid anchor section with AUTO/MANUAL mode, resolved preview, and
 * 3x3 preset buttons for common anchor positions.
 */
@InspectorFor(GridMovement.class)
public class GridMovementInspector extends CustomComponentInspector<GridMovement> {

    private static final String[][] PRESET_LABELS = {
            {"TL", "TC", "TR"},
            {"ML", "CC", "MR"},
            {"BL", "BC", "BR"}
    };

    private static final float[][] PRESET_VALUES = {
            {0f, 1f,   0.5f, 1f,   1f, 1f},
            {0f, 0.5f, 0.5f, 0.5f, 1f, 0.5f},
            {0f, 0f,   0.5f, 0f,   1f, 0f}
    };

    @Override
    public boolean draw() {
        boolean changed = false;

        // Configuration section
        ImGui.text("Configuration");
        ImGui.separator();

        changed |= FieldEditors.drawFloat("Base Speed", component, "baseSpeed", 0.1f);
        changed |= FieldEditors.drawFloat("Jump Height", component, "jumpHeight", 0.01f);
        changed |= FieldEditors.drawFloat("Tile Size", component, "tileSize", 0.1f);
        changed |= FieldEditors.drawInt("Z Level", component, "zLevel");
        changed |= FieldEditors.drawEnum("Facing Dir", component, "facingDirection", Direction.class);

        ImGui.spacing();
        ImGui.spacing();

        // Grid Anchor section
        ImGui.text("Grid Anchor");
        ImGui.separator();

        changed |= FieldEditors.drawEnum("Anchor Mode", component, "anchorMode", AnchorMode.class);

        ImGui.spacing();

        if (component.getAnchorMode() == AnchorMode.AUTO) {
            drawAutoAnchorInfo();
        } else {
            changed |= drawManualAnchor();
        }

        return changed;
    }

    private void drawAutoAnchorInfo() {
        // In editor mode, gameObject is null — look up sibling SpriteRenderer via entity
        SpriteRenderer sr = entity != null ? entity.getComponent(SpriteRenderer.class) : null;
        Vector2f resolved;
        if (sr != null) {
            resolved = new Vector2f(sr.getEffectiveOriginX(), sr.getEffectiveOriginY());
        } else {
            resolved = new Vector2f(0.5f, 0.5f);
        }

        // Show resolved anchor value (read-only)
        FieldEditors.inspectorRow("Resolved", () -> {
            ImGui.textDisabled(String.format("(%.2f, %.2f)", resolved.x, resolved.y));
        });

        // Show source info
        FieldEditors.inspectorRow("Source", () -> {
            if (sr != null) {
                EditorColors.textColored(EditorColors.SUCCESS, "SpriteRenderer");
            } else {
                ImGui.textDisabled("No SpriteRenderer, using (0.5, 0.5)");
            }
        });
    }

    private boolean drawManualAnchor() {
        boolean changed = false;

        changed |= FieldEditors.drawVector2f("Anchor", component, "manualAnchor", 0.01f);

        ImGui.spacing();

        // Preset buttons label
        ImGui.textDisabled("Presets");

        // 3x3 grid of preset buttons, all rows below the label
        float buttonSize = 24f;
        float spacing = ImGui.getStyle().getItemSpacingX();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (col > 0) ImGui.sameLine(0, spacing);

                float px = PRESET_VALUES[row][col * 2];
                float py = PRESET_VALUES[row][col * 2 + 1];
                String label = PRESET_LABELS[row][col] + "##anchor_preset";

                // Highlight active preset
                Vector2f current = component.getManualAnchor();
                boolean isActive = Math.abs(current.x - px) < 0.001f
                        && Math.abs(current.y - py) < 0.001f;

                if (isActive) {
                    ImGui.pushStyleColor(ImGuiCol.Button, EditorColors.INFO[0], EditorColors.INFO[1], EditorColors.INFO[2], EditorColors.INFO[3]);
                }

                if (ImGui.button(label, buttonSize, buttonSize)) {
                    applyPreset(px, py);
                }

                if (isActive) {
                    ImGui.popStyleColor();
                }
            }
        }

        return changed;
    }

    private void applyPreset(float x, float y) {
        Vector2f oldAnchor = new Vector2f(component.getManualAnchor());
        Vector2f newAnchor = new Vector2f(x, y);

        if (oldAnchor.equals(newAnchor)) return;

        if (editorEntity() == null) {
            component.setManualAnchor(newAnchor);
            return;
        }

        // Compute grid cell from current position + old anchor, then re-snap
        // transform so the entity stays on the same tile
        float tileSize = component.getTileSize();
        Vector3f pos = editorEntity().getPosition();
        int gx = Math.round((pos.x - tileSize * oldAnchor.x) / tileSize);
        int gy = Math.round((pos.y - tileSize * oldAnchor.y) / tileSize);

        Vector3f oldPos = new Vector3f(pos);
        Vector3f newPos = new Vector3f(
                gx * tileSize + tileSize * newAnchor.x,
                gy * tileSize + tileSize * newAnchor.y,
                pos.z
        );

        var editorObj = editorEntity();
        UndoManager.getInstance().execute(new EditorCommand() {
            @Override
            public void execute() {
                component.setManualAnchor(newAnchor);
                editorObj.setPosition(newPos.x, newPos.y, newPos.z);
            }

            @Override
            public void undo() {
                component.setManualAnchor(oldAnchor);
                editorObj.setPosition(oldPos.x, oldPos.y, oldPos.z);
            }

            @Override
            public String getDescription() {
                return "Set Grid Anchor Preset";
            }
        });
    }
}
