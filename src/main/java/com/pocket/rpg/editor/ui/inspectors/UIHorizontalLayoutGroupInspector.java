package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.ui.LayoutGroup.ChildVerticalAlignment;
import com.pocket.rpg.components.ui.UIHorizontalLayoutGroup;
import com.pocket.rpg.editor.ui.fields.FieldEditors;

/**
 * Custom inspector for {@link UIHorizontalLayoutGroup}.
 * <p>
 * Shows vertical child alignment and the shared layout group fields.
 */
@InspectorFor(UIHorizontalLayoutGroup.class)
public class UIHorizontalLayoutGroupInspector extends LayoutGroupInspectorBase<UIHorizontalLayoutGroup> {

    @Override
    protected boolean drawSpecificFields() {
        boolean changed = false;
        changed |= FieldEditors.drawEnum("Alignment", component, "childAlignment", ChildVerticalAlignment.class);
        changed |= drawForceExpand();
        return changed;
    }
}
