package com.pocket.rpg.editor;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.editor.EditorSelectionManager.SelectionType;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.rendering.resources.Sprite;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EditorSelectionManagerTest {

    private EditorSelectionManager selectionManager;

    @BeforeEach
    void setUp() {
        selectionManager = new EditorSelectionManager();
    }

    // ========================================================================
    // ASSET SELECTION TESTS
    // ========================================================================

    @Nested
    class AssetSelectionTests {

        @Test
        void selectAsset_setsPathAndType() {
            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);

            assertEquals("audio/sound.wav", selectionManager.getSelectedAssetPath());
            assertEquals(AudioClip.class, selectionManager.getSelectedAssetType());
        }

        @Test
        void selectAsset_setsSelectionTypeToAsset() {
            selectionManager.selectAsset("sprites/player.png", Sprite.class);

            assertEquals(SelectionType.ASSET, selectionManager.getSelectionType());
        }

        @Test
        void isAssetSelected_returnsTrueWhenAssetSelected() {
            selectionManager.selectAsset("audio/music.ogg", AudioClip.class);

            assertTrue(selectionManager.isAssetSelected());
        }

        @Test
        void isAssetSelected_returnsFalseWhenNoSelection() {
            assertFalse(selectionManager.isAssetSelected());
        }

        @Test
        void isAssetSelected_returnsFalseAfterClearSelection() {
            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);
            selectionManager.clearSelection();

            assertFalse(selectionManager.isAssetSelected());
        }

        @Test
        void clearSelection_clearsAssetPathAndType() {
            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);
            selectionManager.clearSelection();

            assertNull(selectionManager.getSelectedAssetPath());
            assertNull(selectionManager.getSelectedAssetType());
            assertEquals(SelectionType.NONE, selectionManager.getSelectionType());
        }

        @Test
        void selectAsset_canSelectDifferentAssetTypes() {
            // Select audio
            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);
            assertEquals(AudioClip.class, selectionManager.getSelectedAssetType());

            // Select sprite
            selectionManager.selectAsset("sprites/player.png", Sprite.class);
            assertEquals(Sprite.class, selectionManager.getSelectedAssetType());
            assertEquals("sprites/player.png", selectionManager.getSelectedAssetPath());
        }
    }

    // ========================================================================
    // SELECTION TYPE TRANSITION TESTS
    // ========================================================================

    @Nested
    class SelectionTypeTransitionTests {

        @Test
        void selectCamera_clearsAssetSelection() {
            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);

            selectionManager.selectCamera();

            assertFalse(selectionManager.isAssetSelected());
            assertNull(selectionManager.getSelectedAssetPath());
            assertNull(selectionManager.getSelectedAssetType());
            assertEquals(SelectionType.CAMERA, selectionManager.getSelectionType());
        }

        @Test
        void selectTilemapLayer_clearsAssetSelection() {
            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);

            selectionManager.selectTilemapLayer(0);

            assertFalse(selectionManager.isAssetSelected());
            assertNull(selectionManager.getSelectedAssetPath());
            assertEquals(SelectionType.TILEMAP_LAYER, selectionManager.getSelectionType());
        }

        @Test
        void selectCollisionLayer_clearsAssetSelection() {
            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);

            selectionManager.selectCollisionLayer();

            assertFalse(selectionManager.isAssetSelected());
            assertNull(selectionManager.getSelectedAssetPath());
            assertEquals(SelectionType.COLLISION_LAYER, selectionManager.getSelectionType());
        }

        @Test
        void selectAsset_clearsLayerSelection() {
            selectionManager.selectTilemapLayer(2);

            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);

            assertEquals(-1, selectionManager.getSelectedLayerIndex());
            assertTrue(selectionManager.isAssetSelected());
        }
    }

    // ========================================================================
    // ENTITY-ASSET INTERACTION TESTS
    // ========================================================================

    @Nested
    class EntityAssetInteractionTests {

        private EditorScene mockScene;
        private EditorGameObject testEntity;

        @BeforeEach
        void setUp() {
            // Create a minimal scene for testing
            mockScene = new EditorScene();
            testEntity = new EditorGameObject("TestEntity", new Vector3f(0, 0, 0), false);
            mockScene.addEntity(testEntity);
            selectionManager.setScene(mockScene);
        }

        @Test
        void selectEntity_clearsAssetSelection() {
            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);

            selectionManager.selectEntity(testEntity);

            assertFalse(selectionManager.isAssetSelected());
            assertNull(selectionManager.getSelectedAssetPath());
            assertNull(selectionManager.getSelectedAssetType());
            assertEquals(SelectionType.ENTITY, selectionManager.getSelectionType());
        }

        @Test
        void selectEntities_clearsAssetSelection() {
            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);

            selectionManager.selectEntities(Set.of(testEntity));

            assertFalse(selectionManager.isAssetSelected());
            assertNull(selectionManager.getSelectedAssetPath());
        }

        @Test
        void selectAsset_clearsEntitySelection() {
            selectionManager.selectEntity(testEntity);
            assertTrue(selectionManager.hasEntitySelection());

            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);

            assertFalse(selectionManager.hasEntitySelection());
            assertTrue(selectionManager.isAssetSelected());
        }
    }

    // ========================================================================
    // LISTENER TESTS
    // ========================================================================
    // TODO: Re-enable when EditorSelectionManager.addListener() is implemented
    /*
    @Nested
    class ListenerTests {

        @Test
        void selectAsset_notifiesListeners() {
            AtomicReference<SelectionType> notifiedType = new AtomicReference<>();
            selectionManager.addListener(notifiedType::set);

            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);

            assertEquals(SelectionType.ASSET, notifiedType.get());
        }

        @Test
        void clearSelection_notifiesListeners() {
            AtomicReference<SelectionType> notifiedType = new AtomicReference<>();
            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);
            selectionManager.addListener(notifiedType::set);

            selectionManager.clearSelection();

            assertEquals(SelectionType.NONE, notifiedType.get());
        }

        @Test
        void selectingDifferentAsset_doesNotNotifyIfTypeUnchanged() {
            // First select an asset
            selectionManager.selectAsset("audio/sound1.wav", AudioClip.class);

            // Add listener after initial selection
            AtomicReference<SelectionType> notifiedType = new AtomicReference<>();
            selectionManager.addListener(notifiedType::set);

            // Select different asset of same type
            selectionManager.selectAsset("audio/sound2.wav", AudioClip.class);

            // Listener should not be notified since selection type didn't change
            assertNull(notifiedType.get());
        }
    }
    */

    // ========================================================================
    // QUERY METHOD TESTS
    // ========================================================================

    @Nested
    class QueryMethodTests {

        @Test
        void hasSelection_returnsTrueForAssetSelection() {
            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);

            assertTrue(selectionManager.hasSelection());
        }

        @Test
        void hasSelection_returnsFalseWhenNothingSelected() {
            assertFalse(selectionManager.hasSelection());
        }

        @Test
        void isCameraSelected_returnsFalseWhenAssetSelected() {
            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);

            assertFalse(selectionManager.isCameraSelected());
        }

        @Test
        void isTilemapLayerSelected_returnsFalseWhenAssetSelected() {
            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);

            assertFalse(selectionManager.isTilemapLayerSelected());
        }

        @Test
        void isCollisionLayerSelected_returnsFalseWhenAssetSelected() {
            selectionManager.selectAsset("audio/sound.wav", AudioClip.class);

            assertFalse(selectionManager.isCollisionLayerSelected());
        }
    }
}
