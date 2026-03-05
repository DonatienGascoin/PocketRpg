package com.pocket.rpg.editor.scene;

import com.pocket.rpg.components.ui.UIButton;
import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UIGridLayoutGroup;
import com.pocket.rpg.components.ui.UIHorizontalLayoutGroup;
import com.pocket.rpg.components.ui.UIImage;
import com.pocket.rpg.components.ui.UIMask;
import com.pocket.rpg.components.ui.UIPanel;
import com.pocket.rpg.components.ui.UIScrollView;
import com.pocket.rpg.components.ui.UIScrollbar;
import com.pocket.rpg.components.ui.UIText;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.components.ui.UIVerticalLayoutGroup;
import com.pocket.rpg.config.GameConfig;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Factory for creating UI entity templates in the editor.
 * Creates EditorEntity instances with pre-configured UI components.
 */
public class UIEntityFactory {

    private final GameConfig gameConfig;

    public UIEntityFactory(GameConfig gameConfig) {
        this.gameConfig = gameConfig;
    }

    /**
     * Creates a UI entity by type name.
     *
     * @param type Type: "Canvas", "Panel", "Image", "Button", "Text"
     * @param name Optional custom name (null for default)
     * @return New EditorEntity with UI components
     */
    public EditorGameObject create(String type, String name) {
        return switch (type) {
            case "Canvas" -> createCanvas(name);
            case "Panel" -> createPanel(name);
            case "Image" -> createImage(name);
            case "Button" -> createButton(name);
            case "Text" -> createText(name);
            case "HorizontalLayout" -> createHorizontalLayout(name);
            case "VerticalLayout" -> createVerticalLayout(name);
            case "GridLayout" -> createGridLayout(name);
            case "ScrollView" -> createScrollView(name);
            default -> null;
        };
    }

    /**
     * Creates a UICanvas entity.
     * Canvas is the root container for UI elements.
     */
    public EditorGameObject createCanvas(String name) {
        EditorGameObject entity = new EditorGameObject(
                name != null ? name : "Canvas",
                new Vector3f(0, 0, 0),
                false
        );

        UICanvas canvas = new UICanvas();
        canvas.setRenderMode(UICanvas.RenderMode.SCREEN_SPACE_OVERLAY);
        canvas.setSortOrder(0);
        entity.addComponent(canvas);

        return entity;
    }

    /**
     * Creates a UIPanel entity.
     * Panel is a container with optional background.
     */
    public EditorGameObject createPanel(String name) {
        EditorGameObject entity = new EditorGameObject(
                name != null ? name : "Panel",
                new Vector3f(0, 0, 0),
                false
        );

        UITransform transform = new UITransform();
        transform.setWidth(200f);
        transform.setHeight(100f);
        transform.setAnchor(new Vector2f(0, 0));
        transform.setOffset(new Vector2f(10, 10));
        transform.setPivot(new Vector2f(0, 0));
        entity.addComponent(transform);

        UIPanel panel = new UIPanel();
        panel.setColor(new Vector4f(1f, 1f, 1f, 1f));
        entity.addComponent(panel);

        return entity;
    }

    /**
     * Creates a UIImage entity.
     * Image displays a sprite.
     */
    public EditorGameObject createImage(String name) {
        EditorGameObject entity = new EditorGameObject(
                name != null ? name : "Image",
                new Vector3f(0, 0, 0),
                false
        );

        UITransform transform = new UITransform();
        transform.setWidth(64f);
        transform.setHeight(64f);
        transform.setAnchor(new Vector2f(0, 0));
        transform.setOffset(new Vector2f(10, 10));
        transform.setPivot(new Vector2f(0, 0));
        entity.addComponent(transform);

        UIImage image = new UIImage();
        image.setColor(new Vector4f(1f, 1f, 1f, 1f));
        entity.addComponent(image);

        return entity;
    }

    /**
     * Creates a UIButton entity.
     * Button with text and click handling.
     */
    public EditorGameObject createButton(String name) {
        EditorGameObject entity = new EditorGameObject(
                name != null ? name : "Button",
                new Vector3f(0, 0, 0),
                false
        );

        UITransform transform = new UITransform();
        transform.setWidth(120f);
        transform.setHeight(40f);
        transform.setAnchor(new Vector2f(0.5f, 0.5f));
        transform.setOffset(new Vector2f(0, 0));
        transform.setPivot(new Vector2f(0.5f, 0.5f));
        entity.addComponent(transform);

        UIButton button = new UIButton();
        System.err.println("UIButton has no text field, only uses child UIText. To add ?");
//        button.setText("Button"); // TODO: UIButton has no text field, only uses child UIText. To add ?
        button.setColor(new Vector4f(1f, 1f, 1f, 1f));
        button.setHoverTint(.8f);
//        button.setNormalColor(new Vector4f(1f, 1f, 1f, 1f)); // TODO: Does not exists, only using HoverTint for now. To add ?
//        button.setHoverColor(new Vector4f(0.9f, 0.9f, 0.9f, 1f));
//        button.setPressedColor(new Vector4f(0.8f, 0.8f, 0.8f, 1f));
        entity.addComponent(button);

        return entity;
    }

    /**
     * Creates a UIText entity.
     * Text displays a string with font settings.
     */
    public EditorGameObject createText(String name) {
        EditorGameObject entity = new EditorGameObject(
                name != null ? name : "Text",
                new Vector3f(0, 0, 0),
                false
        );

        UITransform transform = new UITransform();
        transform.setWidth(200f);
        transform.setHeight(30f);
        transform.setAnchor(new Vector2f(0, 0));
        transform.setOffset(new Vector2f(10, 10));
        transform.setPivot(new Vector2f(0, 0));
        entity.addComponent(transform);

        UIText text = new UIText();
        text.setText("New Text");
        // text.setFontSize(16f); // TODO: Does not exists, font size is from font asset. To add ?
        text.setColor(new Vector4f(1f, 1f, 1f, 1f));
        entity.addComponent(text);

        return entity;
    }

    /**
     * Creates a UIHorizontalLayoutGroup entity.
     * Horizontal layout arranges children left-to-right.
     */
    public EditorGameObject createHorizontalLayout(String name) {
        EditorGameObject entity = new EditorGameObject(
                name != null ? name : "Horizontal Layout",
                new Vector3f(0, 0, 0),
                false
        );

        UITransform transform = new UITransform();
        transform.setWidth(200f);
        transform.setHeight(100f);
        transform.setAnchor(new Vector2f(0, 0));
        transform.setOffset(new Vector2f(10, 10));
        transform.setPivot(new Vector2f(0, 0));
        entity.addComponent(transform);

        UIHorizontalLayoutGroup layout = new UIHorizontalLayoutGroup();
        entity.addComponent(layout);

        return entity;
    }

    /**
     * Creates a UIVerticalLayoutGroup entity.
     * Vertical layout arranges children top-to-bottom.
     */
    public EditorGameObject createVerticalLayout(String name) {
        EditorGameObject entity = new EditorGameObject(
                name != null ? name : "Vertical Layout",
                new Vector3f(0, 0, 0),
                false
        );

        UITransform transform = new UITransform();
        transform.setWidth(100f);
        transform.setHeight(200f);
        transform.setAnchor(new Vector2f(0, 0));
        transform.setOffset(new Vector2f(10, 10));
        transform.setPivot(new Vector2f(0, 0));
        entity.addComponent(transform);

        UIVerticalLayoutGroup layout = new UIVerticalLayoutGroup();
        entity.addComponent(layout);

        return entity;
    }

    /**
     * Creates a UIScrollView entity with full hierarchy.
     * <pre>
     * ScrollView (UIScrollView + UIPanel)
     * ├── Viewport (UIMask + UITransform)
     * │   └── Content (UITransform + UIVerticalLayoutGroup)
     * └── Scrollbar (UIScrollbar + UIImage)
     *     └── Handle (UIImage + UITransform)
     * </pre>
     */
    public EditorGameObject createScrollView(String name) {
        float scrollbarWidth = 12f;

        // Root: ScrollView
        EditorGameObject root = new EditorGameObject(
                name != null ? name : "Scroll View",
                new Vector3f(0, 0, 0),
                false
        );

        UITransform rootTransform = new UITransform();
        rootTransform.setWidth(200f);
        rootTransform.setHeight(300f);
        rootTransform.setAnchor(new Vector2f(0, 0));
        rootTransform.setOffset(new Vector2f(10, 10));
        rootTransform.setPivot(new Vector2f(0, 0));
        root.addComponent(rootTransform);

        UIPanel rootPanel = new UIPanel();
        rootPanel.setColor(new Vector4f(0.15f, 0.15f, 0.15f, 1f));
        root.addComponent(rootPanel);

        UIScrollView scrollView = new UIScrollView();
        root.addComponent(scrollView);

        // Viewport: clips children
        EditorGameObject viewport = new EditorGameObject(
                "Viewport", new Vector3f(0, 0, 0), false
        );

        UITransform vpTransform = new UITransform();
        vpTransform.setAnchor(new Vector2f(0, 0));
        vpTransform.setPivot(new Vector2f(0, 0));
        vpTransform.setOffset(new Vector2f(0, 0));
        // Fill parent size — UIScrollView dynamically adjusts width for scrollbar
        vpTransform.setWidthMode(UITransform.SizeMode.PERCENT);
        vpTransform.setWidthPercent(100);
        vpTransform.setHeightMode(UITransform.SizeMode.PERCENT);
        vpTransform.setHeightPercent(100);
        viewport.addComponent(vpTransform);

        UIMask mask = new UIMask();
        mask.setShowMaskGraphic(false);
        viewport.addComponent(mask);

        viewport.setParent(root);

        // Content: holds scrollable items
        EditorGameObject content = new EditorGameObject(
                "Content", new Vector3f(0, 0, 0), false
        );

        UITransform contentTransform = new UITransform();
        contentTransform.setAnchor(new Vector2f(0, 0));
        contentTransform.setPivot(new Vector2f(0, 0));
        contentTransform.setOffset(new Vector2f(0, 0));
        contentTransform.setWidthMode(UITransform.SizeMode.PERCENT);
        contentTransform.setWidthPercent(100);
        contentTransform.setHeight(0); // Will grow with children
        content.addComponent(contentTransform);

        content.setParent(viewport);

        // Scrollbar: track
        EditorGameObject scrollbarGo = new EditorGameObject(
                "Scrollbar", new Vector3f(0, 0, 0), false
        );

        UITransform sbTransform = new UITransform();
        sbTransform.setAnchor(new Vector2f(1, 0));
        sbTransform.setPivot(new Vector2f(1, 0));
        sbTransform.setOffset(new Vector2f(0, 0));
        sbTransform.setWidth(scrollbarWidth);
        sbTransform.setHeightMode(UITransform.SizeMode.PERCENT);
        sbTransform.setHeightPercent(100);
        scrollbarGo.addComponent(sbTransform);

        UIPanel trackPanel = new UIPanel();
        trackPanel.setColor(new Vector4f(0.2f, 0.2f, 0.2f, 0.5f));
        scrollbarGo.addComponent(trackPanel);

        UIScrollbar scrollbar = new UIScrollbar();
        scrollbarGo.addComponent(scrollbar);

        scrollbarGo.setParent(root);

        // Handle: draggable
        EditorGameObject handle = new EditorGameObject(
                "Handle", new Vector3f(0, 0, 0), false
        );

        UITransform handleTransform = new UITransform();
        handleTransform.setAnchor(new Vector2f(0, 0));
        handleTransform.setPivot(new Vector2f(0, 0));
        handleTransform.setOffset(new Vector2f(0, 0));
        handleTransform.setWidthMode(UITransform.SizeMode.PERCENT);
        handleTransform.setWidthPercent(100);
        handleTransform.setHeight(40); // Will be resized dynamically
        handle.addComponent(handleTransform);

        UIPanel handlePanel = new UIPanel();
        handlePanel.setColor(new Vector4f(0.6f, 0.6f, 0.6f, 1f));
        handle.addComponent(handlePanel);

        handle.setParent(scrollbarGo);

        return root;
    }

    /**
     * Creates a UIGridLayoutGroup entity.
     * Grid layout arranges children in a grid pattern.
     */
    public EditorGameObject createGridLayout(String name) {
        EditorGameObject entity = new EditorGameObject(
                name != null ? name : "Grid Layout",
                new Vector3f(0, 0, 0),
                false
        );

        UITransform transform = new UITransform();
        transform.setWidth(200f);
        transform.setHeight(200f);
        transform.setAnchor(new Vector2f(0, 0));
        transform.setOffset(new Vector2f(10, 10));
        transform.setPivot(new Vector2f(0, 0));
        entity.addComponent(transform);

        UIGridLayoutGroup layout = new UIGridLayoutGroup();
        entity.addComponent(layout);

        return entity;
    }
}
