package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.ui.*;
import com.pocket.rpg.editor.scene.EditorGameObject;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransformDriverDetectorTest {

    private EditorGameObject createScratchEntity(String name) {
        return new EditorGameObject(name, new Vector3f(), false);
    }

    private void addUITransform(EditorGameObject entity, float w, float h) {
        entity.addComponent(new UITransform(w, h));
    }

    private void setParent(EditorGameObject child, EditorGameObject parent) {
        child.setParent(parent);
    }

    // ========================================================================
    // UICanvas (same GO) — entirely managed
    // ========================================================================

    @Nested
    class CanvasDriver {

        @Test
        void entityWithUICanvasIsEntirelyDriven() {
            EditorGameObject entity = createScratchEntity("Canvas");
            addUITransform(entity, 320, 180);
            entity.addComponent(new UICanvas());

            TransformDriverInfo info = TransformDriverDetector.detect(entity);

            assertNotNull(info);
            assertTrue(info.entirelyDriven());
            assertEquals("UICanvas", info.label());
        }

        @Test
        void entityWithoutUICanvasNotDriven() {
            EditorGameObject entity = createScratchEntity("Panel");
            addUITransform(entity, 100, 100);

            TransformDriverInfo info = TransformDriverDetector.detect(entity);

            assertNull(info);
        }
    }

    // ========================================================================
    // LayoutGroup (parent) — position always driven, width/height conditional
    // ========================================================================

    @Nested
    class LayoutGroupDriver {

        @Test
        void verticalLayoutNoForceExpand_positionDriven() {
            EditorGameObject parent = createScratchEntity("Parent");
            addUITransform(parent, 200, 400);
            UIVerticalLayoutGroup layout = new UIVerticalLayoutGroup();
            layout.setChildForceExpandWidth(false);
            layout.setChildForceExpandHeight(false);
            parent.addComponent(layout);

            EditorGameObject child = createScratchEntity("Child");
            addUITransform(child, 100, 50);
            setParent(child, parent);

            TransformDriverInfo info = TransformDriverDetector.detect(child);

            assertNotNull(info);
            assertTrue(info.positionDriven());
            assertFalse(info.widthDriven());
            assertFalse(info.heightDriven());
            assertFalse(info.entirelyDriven());
        }

        @Test
        void verticalLayoutForceExpandWidth_widthAndPositionDriven() {
            EditorGameObject parent = createScratchEntity("Parent");
            addUITransform(parent, 200, 400);
            UIVerticalLayoutGroup layout = new UIVerticalLayoutGroup();
            layout.setChildForceExpandWidth(true);
            layout.setChildForceExpandHeight(false);
            parent.addComponent(layout);

            EditorGameObject child = createScratchEntity("Child");
            addUITransform(child, 100, 50);
            setParent(child, parent);

            TransformDriverInfo info = TransformDriverDetector.detect(child);

            assertNotNull(info);
            assertTrue(info.widthDriven());
            assertTrue(info.positionDriven());
            assertFalse(info.heightDriven());
        }

        @Test
        void horizontalLayoutForceExpandHeight_heightAndPositionDriven() {
            EditorGameObject parent = createScratchEntity("Parent");
            addUITransform(parent, 400, 200);
            UIHorizontalLayoutGroup layout = new UIHorizontalLayoutGroup();
            layout.setChildForceExpandHeight(true);
            layout.setChildForceExpandWidth(false);
            parent.addComponent(layout);

            EditorGameObject child = createScratchEntity("Child");
            addUITransform(child, 50, 100);
            setParent(child, parent);

            TransformDriverInfo info = TransformDriverDetector.detect(child);

            assertNotNull(info);
            assertTrue(info.heightDriven());
            assertTrue(info.positionDriven());
            assertFalse(info.widthDriven());
        }

        @Test
        void gridLayout_allDriven() {
            EditorGameObject parent = createScratchEntity("Parent");
            addUITransform(parent, 400, 400);
            parent.addComponent(new UIGridLayoutGroup());

            EditorGameObject child = createScratchEntity("Child");
            addUITransform(child, 50, 50);
            setParent(child, parent);

            TransformDriverInfo info = TransformDriverDetector.detect(child);

            assertNotNull(info);
            assertTrue(info.widthDriven());
            assertTrue(info.heightDriven());
            assertTrue(info.positionDriven());
        }

        @Test
        void verticalLayoutBothForceExpand_allDriven() {
            EditorGameObject parent = createScratchEntity("Parent");
            addUITransform(parent, 200, 400);
            UIVerticalLayoutGroup layout = new UIVerticalLayoutGroup();
            layout.setChildForceExpandWidth(true);
            layout.setChildForceExpandHeight(true);
            parent.addComponent(layout);

            EditorGameObject child = createScratchEntity("Child");
            addUITransform(child, 100, 50);
            setParent(child, parent);

            TransformDriverInfo info = TransformDriverDetector.detect(child);

            assertNotNull(info);
            assertTrue(info.widthDriven());
            assertTrue(info.heightDriven());
            assertTrue(info.positionDriven());
        }
    }

    // ========================================================================
    // No driver — returns null
    // ========================================================================

    @Nested
    class NoDriver {

        @Test
        void rootEntityReturnsNull() {
            EditorGameObject entity = createScratchEntity("Root");
            addUITransform(entity, 100, 100);

            assertNull(TransformDriverDetector.detect(entity));
        }

        @Test
        void parentWithNoLayoutReturnsNull() {
            EditorGameObject parent = createScratchEntity("Parent");
            addUITransform(parent, 200, 200);

            EditorGameObject child = createScratchEntity("Child");
            addUITransform(child, 100, 100);
            setParent(child, parent);

            assertNull(TransformDriverDetector.detect(child));
        }

        @Test
        void entityWithUITransformNoParentReturnsNull() {
            EditorGameObject entity = createScratchEntity("Solo");
            addUITransform(entity, 50, 50);

            assertNull(TransformDriverDetector.detect(entity));
        }

        @Test
        void parentWithMultipleNonLayoutComponentsReturnsNull() {
            EditorGameObject parent = createScratchEntity("Parent");
            addUITransform(parent, 200, 200);
            parent.addComponent(new UIMask());

            EditorGameObject child = createScratchEntity("Child");
            addUITransform(child, 100, 100);
            setParent(child, parent);

            assertNull(TransformDriverDetector.detect(child));
        }
    }

    // ========================================================================
    // ScrollView/Scrollbar drivers
    // ========================================================================

    @Nested
    class ScrollViewDriver {

        @Test
        void viewportChildWithUIMask_widthDriven() {
            EditorGameObject scrollViewGo = createScratchEntity("ScrollView");
            addUITransform(scrollViewGo, 200, 300);
            scrollViewGo.addComponent(new UIScrollView());

            EditorGameObject viewport = createScratchEntity("Viewport");
            addUITransform(viewport, 188, 300);
            viewport.addComponent(new UIMask());
            setParent(viewport, scrollViewGo);

            TransformDriverInfo info = TransformDriverDetector.detect(viewport);

            assertNotNull(info);
            assertTrue(info.widthDriven());
            assertFalse(info.heightDriven());
            assertFalse(info.positionDriven());
            assertFalse(info.entirelyDriven());
            assertEquals("ScrollView", info.label());
        }

        @Test
        void scrollbarChildWithoutUIMask_notDrivenByScrollView() {
            EditorGameObject scrollViewGo = createScratchEntity("ScrollView");
            addUITransform(scrollViewGo, 200, 300);
            scrollViewGo.addComponent(new UIScrollView());

            EditorGameObject scrollbar = createScratchEntity("Scrollbar");
            addUITransform(scrollbar, 12, 300);
            scrollbar.addComponent(new UIScrollbar());
            setParent(scrollbar, scrollViewGo);

            TransformDriverInfo info = TransformDriverDetector.detect(scrollbar);

            // UIScrollView returns null for non-UIMask children
            // UIScrollbar on the child itself is not a parent driver
            assertNull(info);
        }
    }

    @Nested
    class ScrollbarDriver {

        @Test
        void handleChildOfScrollbar_entirelyDriven() {
            EditorGameObject scrollbarGo = createScratchEntity("Scrollbar");
            addUITransform(scrollbarGo, 12, 300);
            scrollbarGo.addComponent(new UIScrollbar());

            EditorGameObject handle = createScratchEntity("Handle");
            addUITransform(handle, 12, 40);
            setParent(handle, scrollbarGo);

            TransformDriverInfo info = TransformDriverDetector.detect(handle);

            assertNotNull(info);
            assertTrue(info.entirelyDriven());
            assertTrue(info.widthDriven());
            assertTrue(info.heightDriven());
            assertTrue(info.positionDriven());
            assertEquals("Scrollbar", info.label());
        }
    }
}
