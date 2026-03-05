package com.pocket.rpg.components.ui;

import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.rendering.ui.UIRendererBackend;
import lombok.Getter;
import lombok.Setter;

/**
 * Clips all children to this GameObject's UITransform bounds.
 * <p>
 * Attach UIMask to any UI element to hide child content that extends
 * beyond its rectangular area. Uses GPU scissor test for clipping.
 * <p>
 * Supports nesting: a UIMask inside another UIMask clips to the
 * intersection of both rects.
 * <p>
 * Properties:
 * <ul>
 *   <li>{@code showMaskGraphic} - if false, the mask owner's own
 *       UIPanel/UIImage is not rendered (invisible clipping rect)</li>
 * </ul>
 */
@ComponentMeta(category = "UI")
public class UIMask extends UIComponent {

    @Getter @Setter
    private boolean showMaskGraphic = true;

    @Override
    public void render(UIRendererBackend backend) {
        // UIMask itself doesn't render anything.
        // Rendering is controlled by the UIRenderer traversal:
        // - If showMaskGraphic is true, sibling UIPanel/UIImage renders normally
        // - If showMaskGraphic is false, UIRenderer skips rendering other components on this GO
        // Scissor push/pop is handled by UIRenderer.renderCanvasSubtree()
    }
}
