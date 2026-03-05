package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.ui.UIScrollbar;
import com.pocket.rpg.editor.ui.fields.PrimitiveEditors;

/**
 * Custom inspector for UIScrollbar with conditional field visibility.
 * Shows handleSize when fixedHandleSize is true, minHandleSize when false.
 */
@InspectorFor(UIScrollbar.class)
public class UIScrollbarInspector extends CustomComponentInspector<UIScrollbar> {

    @Override
    public boolean draw() {
        boolean changed = false;

        changed |= PrimitiveEditors.drawBoolean("Fixed Handle Size", component, "fixedHandleSize");

        if (component.isFixedHandleSize()) {
            changed |= PrimitiveEditors.drawFloat("Handle Size", component, "handleSize", 0.5f, 1f, 500f);
        } else {
            changed |= PrimitiveEditors.drawFloat("Min Handle Size", component, "minHandleSize", 0.5f, 1f, 500f);
        }

        changed |= PrimitiveEditors.drawFloat("Track Padding", component, "trackPadding", 0.5f, 0f, 100f);

        return changed;
    }
}
