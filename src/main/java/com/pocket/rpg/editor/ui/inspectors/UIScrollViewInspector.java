package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.ui.UIScrollView;
import com.pocket.rpg.components.ui.UIScrollbar;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyItem;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.ui.fields.PrimitiveEditors;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/**
 * Custom inspector for UIScrollView.
 */
@InspectorFor(UIScrollView.class)
public class UIScrollViewInspector extends CustomComponentInspector<UIScrollView> {

    private Float scrollEditStart;

    @Override
    public boolean draw() {
        boolean changed = false;

        // Scroll sensitivity
        changed |= PrimitiveEditors.drawFloat("Scroll Sensitivity", component, "scrollSensitivity", 0.5f, 1f, 200f);

        ImGui.spacing();

        // Scrollbar visibility
        changed |= FieldEditors.drawEnum("Show Scrollbar", "showScrollbar",
                component::getShowScrollbar, component::setShowScrollbar,
                UIScrollView.ScrollbarVisibility.class);

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Scroll position slider — controls the scrollbar handle position for preview
        ImGui.text(MaterialIcons.SwapVert + " Scroll Position");
        ImGui.sameLine();
        ImGui.setNextItemWidth(-1);

        // Read current value from scrollbar's previewNormalized (or scrollView if content exists)
        float normalized = component.canScroll()
                ? component.getScrollNormalized()
                : getScrollbarPreview();

        float[] buf = { normalized };
        if (ImGui.sliderFloat("##scrollPosition", buf, 0f, 1f, "%.2f")) {
            float val = Math.max(0, Math.min(1, buf[0]));
            if (component.canScroll()) {
                component.setScrollNormalized(val);
            }
            setScrollbarPreview(val);
            changed = true;
        }

        if (ImGui.isItemActivated()) {
            scrollEditStart = normalized;
        }
        if (ImGui.isItemDeactivatedAfterEdit() && scrollEditStart != null) {
            scrollEditStart = null;
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Preview handle position (0 = top, 1 = bottom)");
        }

        // Info section
        ImGui.spacing();
        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f);
        ImGui.text(String.format("Content: %.0fpx | Viewport: %.0fpx | Can scroll: %s",
                component.getContentHeight(),
                component.getViewportHeight(),
                component.canScroll() ? "yes" : "no"));
        ImGui.popStyleColor();

        return changed;
    }

    private UIScrollbar findScrollbar() {
        if (entity == null) return null;
        for (HierarchyItem child : entity.getHierarchyChildren()) {
            UIScrollbar sb = child.getComponent(UIScrollbar.class);
            if (sb != null) return sb;
        }
        return null;
    }

    private float getScrollbarPreview() {
        UIScrollbar sb = findScrollbar();
        return sb != null ? sb.getPreviewNormalized() : 0f;
    }

    private void setScrollbarPreview(float val) {
        UIScrollbar sb = findScrollbar();
        if (sb != null) {
            sb.setPreviewNormalized(val);
        }
    }
}
