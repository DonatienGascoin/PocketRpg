package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.PersistentEntity;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.GameObjectData;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PersistentEntitySnapshot: capture, restore, and creation from snapshots.
 * Tests both scratch entity and prefab-based paths.
 */
class PersistentEntitySnapshotTest {

    private static final String TEST_PREFAB_ID = "test_persistent_prefab";

    @BeforeAll
    static void initRegistry() {
        ComponentRegistry.initialize();
    }

    @AfterEach
    void cleanupPrefabRegistry() {
        // Remove test prefab if registered
        PrefabRegistry.getInstance().unregister(TEST_PREFAB_ID);
    }

    // ========================================================================
    // SNAPSHOT CAPTURE
    // ========================================================================

    @Nested
    @DisplayName("snapshot()")
    class SnapshotTests {

        @Test
        @DisplayName("returns null for entity without PersistentEntity")
        void snapshotWithoutPersistentEntity() {
            GameObject entity = new GameObject("NPC");

            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(entity);

            assertNull(snapshot);
        }

        @Test
        @DisplayName("captures entity name and tag")
        void snapshotCapturesNameAndTag() {
            GameObject entity = createPlayerEntity("Hero");

            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(entity);

            assertNotNull(snapshot);
            assertEquals("Hero", snapshot.getName());
            assertEquals("Player", snapshot.getTag());
        }

        @Test
        @DisplayName("captures active state")
        void snapshotCapturesActiveState() {
            GameObject entity = createPlayerEntity("Hero");
            entity.setEnabled(false);

            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(entity);

            assertNotNull(snapshot);
            assertFalse(snapshot.isActive());
        }

        @Test
        @DisplayName("captures sourcePrefabId in snapshot metadata")
        void snapshotCapturesSourcePrefabId() {
            GameObject entity = createPlayerEntity("Hero");
            PersistentEntity pe = entity.getComponent(PersistentEntity.class);
            pe.setSourcePrefabId("prefabs/player.prefab");

            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(entity);

            assertNotNull(snapshot);
            // sourcePrefabId is stored in GameObjectData.prefabId (not in the component,
            // since it's @HideInInspector and excluded from cloning)
            assertEquals("prefabs/player.prefab", snapshot.getPrefabId());
        }

        @Test
        @DisplayName("snapshot without sourcePrefabId has null prefabId")
        void snapshotWithoutSourcePrefabHasNullPrefabId() {
            GameObject entity = createPlayerEntity("Hero");

            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(entity);

            assertNotNull(snapshot);
            assertNull(snapshot.getPrefabId());
        }

        @Test
        @DisplayName("snapshot is independent of original entity")
        void snapshotIsIndependent() {
            GameObject entity = createPlayerEntity("Hero");
            entity.getTransform().setPosition(5, 10, 0);

            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(entity);

            // Modify original
            entity.getTransform().setPosition(99, 99, 0);
            entity.setName("Modified");

            // Snapshot should be unchanged
            assertEquals("Hero", snapshot.getName());
        }

        @Test
        @DisplayName("captures custom entityTag")
        void snapshotCapturesCustomTag() {
            GameObject entity = new GameObject("Companion");
            PersistentEntity pe = new PersistentEntity();
            pe.setEntityTag("Companion1");
            entity.addComponent(pe);

            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(entity);

            assertNotNull(snapshot);
            assertEquals("Companion1", snapshot.getTag());
        }

        @Test
        @DisplayName("captures entityTag field value in cloned component")
        void snapshotClonesEntityTagField() {
            GameObject entity = new GameObject("Companion");
            PersistentEntity pe = new PersistentEntity();
            pe.setEntityTag("Companion1");
            entity.addComponent(pe);

            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(entity);

            // The PersistentEntity component is also cloned (entityTag is serializable)
            PersistentEntity clonedPe = snapshot.getComponent(PersistentEntity.class);
            assertNotNull(clonedPe);
            assertEquals("Companion1", clonedPe.getEntityTag());
        }
    }

    // ========================================================================
    // SNAPSHOT APPLY
    // ========================================================================

    @Nested
    @DisplayName("applySnapshot()")
    class ApplySnapshotTests {

        @Test
        @DisplayName("does not overwrite transform position")
        void applyDoesNotOverwriteTransform() {
            // Create entity and snapshot at position (5, 10)
            GameObject original = createPlayerEntity("Hero");
            original.getTransform().setPosition(5, 10, 0);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            // Create target at different position
            GameObject target = createPlayerEntity("Hero");
            target.getTransform().setPosition(20, 30, 0);

            PersistentEntitySnapshot.applySnapshot(snapshot, target);

            // Transform should NOT be overwritten (spawn point handles position)
            assertEquals(20, target.getTransform().getPosition().x, 0.01f);
            assertEquals(30, target.getTransform().getPosition().y, 0.01f);
        }

        @Test
        @DisplayName("preserves target's own PersistentEntity")
        void applyPreservesTargetPersistentEntity() {
            GameObject original = createPlayerEntity("Hero");
            PersistentEntity originalPe = original.getComponent(PersistentEntity.class);
            originalPe.setSourcePrefabId("prefabs/old.prefab");
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            // Target has its own PersistentEntity with different prefab
            GameObject target = createPlayerEntity("Hero");
            PersistentEntity targetPe = target.getComponent(PersistentEntity.class);
            targetPe.setSourcePrefabId("prefabs/new.prefab");

            PersistentEntitySnapshot.applySnapshot(snapshot, target);

            // Target's PersistentEntity should be unchanged
            assertEquals("prefabs/new.prefab", targetPe.getSourcePrefabId());
        }

        @Test
        @DisplayName("handles null components list")
        void applyHandlesNullComponents() {
            GameObjectData emptySnapshot = new GameObjectData(null, "Empty", null);
            GameObject target = createPlayerEntity("Hero");

            assertDoesNotThrow(() ->
                    PersistentEntitySnapshot.applySnapshot(emptySnapshot, target));
        }

        @Test
        @DisplayName("adds missing component to target from snapshot")
        void applyAddsMissingComponent() {
            // Original has PersistentEntity + SpawnPoint (two components beyond Transform)
            GameObject original = createPlayerEntity("Hero");
            com.pocket.rpg.components.interaction.SpawnPoint sp = new com.pocket.rpg.components.interaction.SpawnPoint();
            sp.setSpawnId("test_spawn");
            original.addComponent(sp);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            // Target has only PersistentEntity (no SpawnPoint)
            GameObject target = createPlayerEntity("Hero");
            assertNull(target.getComponent(com.pocket.rpg.components.interaction.SpawnPoint.class));

            PersistentEntitySnapshot.applySnapshot(snapshot, target);

            // SpawnPoint should have been added
            com.pocket.rpg.components.interaction.SpawnPoint addedSp =
                    target.getComponent(com.pocket.rpg.components.interaction.SpawnPoint.class);
            assertNotNull(addedSp, "Missing component should be added from snapshot");
            assertEquals("test_spawn", addedSp.getSpawnId());
        }

        @Test
        @DisplayName("removes component from target that was removed at runtime")
        void applyRemovesComponentNotInSnapshot() {
            // Original has only PersistentEntity (SpawnPoint was removed at runtime)
            GameObject original = createPlayerEntity("Hero");
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            // Target scene's entity still has the SpawnPoint from the prefab/scene
            GameObject target = createPlayerEntity("Hero");
            com.pocket.rpg.components.interaction.SpawnPoint sp = new com.pocket.rpg.components.interaction.SpawnPoint();
            sp.setSpawnId("old_spawn");
            target.addComponent(sp);
            assertNotNull(target.getComponent(com.pocket.rpg.components.interaction.SpawnPoint.class));

            PersistentEntitySnapshot.applySnapshot(snapshot, target);

            // SpawnPoint should be removed (not in snapshot)
            assertNull(target.getComponent(com.pocket.rpg.components.interaction.SpawnPoint.class),
                    "Component not in snapshot should be removed from target");
        }

        @Test
        @DisplayName("empty field values overwrite non-empty on target — P2 regression")
        void applyOverwritesFieldWithSnapshotValue() {
            // Original has SpawnPoint with empty spawnId (cleared at runtime)
            GameObject original = createPlayerEntity("Hero");
            com.pocket.rpg.components.interaction.SpawnPoint sp = new com.pocket.rpg.components.interaction.SpawnPoint();
            sp.setSpawnId(""); // cleared
            original.addComponent(sp);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            // Target has SpawnPoint with a non-empty spawnId
            GameObject target = createPlayerEntity("Hero");
            com.pocket.rpg.components.interaction.SpawnPoint targetSp = new com.pocket.rpg.components.interaction.SpawnPoint();
            targetSp.setSpawnId("old_value");
            target.addComponent(targetSp);

            PersistentEntitySnapshot.applySnapshot(snapshot, target);

            // The empty value should have been propagated (not skipped)
            com.pocket.rpg.components.interaction.SpawnPoint resultSp =
                    target.getComponent(com.pocket.rpg.components.interaction.SpawnPoint.class);
            assertNotNull(resultSp);
            assertEquals("", resultSp.getSpawnId(),
                    "Snapshot field value should overwrite target, even when empty");
        }

        @Test
        @DisplayName("removal preserves Transform and PersistentEntity")
        void removalPreservesTransformAndPersistentEntity() {
            // Snapshot with no extra components (just Transform + PersistentEntity)
            GameObject original = createPlayerEntity("Hero");
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            // Target has extra component
            GameObject target = createPlayerEntity("Hero");
            target.addComponent(new com.pocket.rpg.components.interaction.SpawnPoint());

            PersistentEntitySnapshot.applySnapshot(snapshot, target);

            // Transform and PersistentEntity must survive
            assertNotNull(target.getTransform(), "Transform must not be removed");
            assertNotNull(target.getComponent(PersistentEntity.class),
                    "PersistentEntity must not be removed");
        }
    }

    // ========================================================================
    // CREATE FROM SNAPSHOT - SCRATCH ENTITIES
    // ========================================================================

    @Nested
    @DisplayName("createFromSnapshot() — scratch entities")
    class CreateScratchTests {

        @Test
        @DisplayName("creates scratch entity when no prefabId")
        void createScratchEntity() {
            GameObject original = createPlayerEntity("Hero");
            original.getTransform().setPosition(5, 10, 0);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            GameObject created = PersistentEntitySnapshot.createFromSnapshot(snapshot);

            assertNotNull(created);
            assertEquals("Hero", created.getName());
            assertTrue(created.isEnabled());
            assertNotNull(created.getComponent(PersistentEntity.class));
        }

        @Test
        @DisplayName("scratch entity preserves transform from snapshot")
        void scratchEntityPreservesTransform() {
            GameObject original = createPlayerEntity("Hero");
            original.getTransform().setPosition(5, 10, 2);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            GameObject created = PersistentEntitySnapshot.createFromSnapshot(snapshot);

            assertNotNull(created);
            assertEquals(5, created.getTransform().getPosition().x, 0.01f);
            assertEquals(10, created.getTransform().getPosition().y, 0.01f);
            assertEquals(2, created.getTransform().getPosition().z, 0.01f);
        }

        @Test
        @DisplayName("preserves disabled state")
        void createPreservesDisabledState() {
            GameObject original = createPlayerEntity("Hero");
            original.setEnabled(false);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            GameObject created = PersistentEntitySnapshot.createFromSnapshot(snapshot);

            assertNotNull(created);
            assertFalse(created.isEnabled());
        }

        @Test
        @DisplayName("uses default name when snapshot has blank name")
        void createUsesDefaultNameForBlank() {
            GameObject original = new GameObject("");
            original.addComponent(new PersistentEntity());
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            GameObject created = PersistentEntitySnapshot.createFromSnapshot(snapshot);

            assertNotNull(created);
            assertEquals("PersistentEntity", created.getName());
        }

        @Test
        @DisplayName("scratch entity includes all components from snapshot")
        void scratchEntityIncludesAllComponents() {
            GameObject original = createPlayerEntity("Hero");
            com.pocket.rpg.components.interaction.SpawnPoint sp = new com.pocket.rpg.components.interaction.SpawnPoint();
            sp.setSpawnId("from_cave");
            original.addComponent(sp);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            GameObject created = PersistentEntitySnapshot.createFromSnapshot(snapshot);

            assertNotNull(created);
            assertNotNull(created.getComponent(PersistentEntity.class));
            com.pocket.rpg.components.interaction.SpawnPoint createdSp =
                    created.getComponent(com.pocket.rpg.components.interaction.SpawnPoint.class);
            assertNotNull(createdSp, "All components should be recreated");
            assertEquals("from_cave", createdSp.getSpawnId());
        }
    }

    // ========================================================================
    // CREATE FROM SNAPSHOT - PREFAB ENTITIES
    // ========================================================================

    @Nested
    @DisplayName("createFromSnapshot() — prefab entities")
    class CreatePrefabTests {

        @Test
        @DisplayName("instantiates from prefab when sourcePrefabId matches a registered prefab")
        void createFromRegisteredPrefab() {
            // Register a test prefab
            registerTestPrefab();

            // Create original entity with sourcePrefabId pointing to test prefab
            GameObject original = createPlayerEntity("Hero");
            PersistentEntity pe = original.getComponent(PersistentEntity.class);
            pe.setSourcePrefabId(TEST_PREFAB_ID);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            // Create from snapshot — should use prefab
            GameObject created = PersistentEntitySnapshot.createFromSnapshot(snapshot);

            assertNotNull(created);
            // Prefab instantiate names the entity from its displayName
            // Then applySnapshot doesn't touch name, but snapshot name is applied first
            assertEquals("Hero", created.getName());
            // Should have PersistentEntity from the prefab
            assertNotNull(created.getComponent(PersistentEntity.class));
        }

        @Test
        @DisplayName("prefab entity has snapshot state applied over prefab defaults")
        void prefabEntityHasSnapshotStateApplied() {
            // Register a test prefab that includes a SpawnPoint with default spawnId
            registerTestPrefabWithSpawnPoint("default_spawn");

            // Create original entity with modified SpawnPoint
            GameObject original = new GameObject("Hero");
            PersistentEntity pe = new PersistentEntity();
            pe.setEntityTag("Player");
            pe.setSourcePrefabId(TEST_PREFAB_ID);
            original.addComponent(pe);
            com.pocket.rpg.components.interaction.SpawnPoint sp = new com.pocket.rpg.components.interaction.SpawnPoint();
            sp.setSpawnId("modified_spawn");
            original.addComponent(sp);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            // Create from snapshot
            GameObject created = PersistentEntitySnapshot.createFromSnapshot(snapshot);

            assertNotNull(created);
            com.pocket.rpg.components.interaction.SpawnPoint createdSp =
                    created.getComponent(com.pocket.rpg.components.interaction.SpawnPoint.class);
            assertNotNull(createdSp);
            // SpawnPoint should have snapshot value, not prefab default
            assertEquals("modified_spawn", createdSp.getSpawnId());
        }

        @Test
        @DisplayName("falls back to scratch when prefab not found")
        void createFallsBackWhenPrefabNotFound() {
            GameObject original = createPlayerEntity("Hero");
            PersistentEntity pe = original.getComponent(PersistentEntity.class);
            pe.setSourcePrefabId("prefabs/nonexistent.prefab");
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            // Should not throw, should fall back to scratch entity
            GameObject created = PersistentEntitySnapshot.createFromSnapshot(snapshot);

            assertNotNull(created);
            assertEquals("Hero", created.getName());
            assertNotNull(created.getComponent(PersistentEntity.class));
        }

        @Test
        @DisplayName("prefab entity preserves enabled state from snapshot")
        void prefabEntityPreservesEnabledState() {
            registerTestPrefab();

            GameObject original = createPlayerEntity("Hero");
            original.getComponent(PersistentEntity.class).setSourcePrefabId(TEST_PREFAB_ID);
            original.setEnabled(false);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            GameObject created = PersistentEntitySnapshot.createFromSnapshot(snapshot);

            assertNotNull(created);
            assertFalse(created.isEnabled());
        }

        @Test
        @DisplayName("prefab entity preserves custom name from snapshot")
        void prefabEntityPreservesName() {
            registerTestPrefab();

            GameObject original = createPlayerEntity("CustomName");
            original.getComponent(PersistentEntity.class).setSourcePrefabId(TEST_PREFAB_ID);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            GameObject created = PersistentEntitySnapshot.createFromSnapshot(snapshot);

            assertNotNull(created);
            assertEquals("CustomName", created.getName());
        }
    }

    // ========================================================================
    // CHILDREN & GRANDCHILDREN
    // ========================================================================

    @Nested
    @DisplayName("children and grandchildren")
    class ChildSnapshotTests {

        @Test
        @DisplayName("snapshot captures direct children")
        void snapshotCapturesChildren() {
            GameObject entity = createPlayerEntity("Hero");
            GameObject child = new GameObject("Sword");
            child.addComponent(new com.pocket.rpg.components.interaction.SpawnPoint());
            entity.addChild(child);

            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(entity);

            assertNotNull(snapshot.getChildren());
            assertEquals(1, snapshot.getChildren().size());
            assertEquals("Sword", snapshot.getChildren().get(0).getName());
        }

        @Test
        @DisplayName("snapshot captures grandchildren recursively")
        void snapshotCapturesGrandchildren() {
            GameObject entity = createPlayerEntity("Hero");
            GameObject child = new GameObject("Inventory");
            GameObject grandchild = new GameObject("Sword");
            grandchild.addComponent(new com.pocket.rpg.components.interaction.SpawnPoint());
            child.addChild(grandchild);
            entity.addChild(child);

            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(entity);

            assertNotNull(snapshot.getChildren());
            assertEquals(1, snapshot.getChildren().size());
            GameObjectData childData = snapshot.getChildren().get(0);
            assertEquals("Inventory", childData.getName());
            assertNotNull(childData.getChildren());
            assertEquals(1, childData.getChildren().size());
            assertEquals("Sword", childData.getChildren().get(0).getName());
        }

        @Test
        @DisplayName("applySnapshot restores children to existing target")
        void applyRestoresChildren() {
            // Original has a child
            GameObject original = createPlayerEntity("Hero");
            GameObject child = new GameObject("Sword");
            com.pocket.rpg.components.interaction.SpawnPoint sp = new com.pocket.rpg.components.interaction.SpawnPoint();
            sp.setSpawnId("sword_spawn");
            child.addComponent(sp);
            original.addChild(child);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            // Target has no children
            GameObject target = createPlayerEntity("Hero");
            assertTrue(target.getChildren().isEmpty());

            PersistentEntitySnapshot.applySnapshot(snapshot, target);

            assertEquals(1, target.getChildren().size());
            GameObject restoredChild = target.getChildren().get(0);
            assertEquals("Sword", restoredChild.getName());
        }

        @Test
        @DisplayName("applySnapshot updates existing child by name")
        void applyUpdatesExistingChild() {
            // Original child has modified SpawnPoint
            GameObject original = createPlayerEntity("Hero");
            GameObject child = new GameObject("Sword");
            com.pocket.rpg.components.interaction.SpawnPoint sp = new com.pocket.rpg.components.interaction.SpawnPoint();
            sp.setSpawnId("modified");
            child.addComponent(sp);
            original.addChild(child);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            // Target already has same-named child with different state
            GameObject target = createPlayerEntity("Hero");
            GameObject targetChild = new GameObject("Sword");
            com.pocket.rpg.components.interaction.SpawnPoint targetSp = new com.pocket.rpg.components.interaction.SpawnPoint();
            targetSp.setSpawnId("original");
            targetChild.addComponent(targetSp);
            target.addChild(targetChild);

            PersistentEntitySnapshot.applySnapshot(snapshot, target);

            assertEquals(1, target.getChildren().size());
            com.pocket.rpg.components.interaction.SpawnPoint updatedSp =
                    target.getChildren().get(0).getComponent(com.pocket.rpg.components.interaction.SpawnPoint.class);
            assertNotNull(updatedSp);
            assertEquals("modified", updatedSp.getSpawnId());
        }

        @Test
        @DisplayName("applySnapshot removes extra children not in snapshot")
        void applyRemovesExtraChildren() {
            // Original has no children
            GameObject original = createPlayerEntity("Hero");
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            // Target has a child that shouldn't exist
            GameObject target = createPlayerEntity("Hero");
            target.addChild(new GameObject("OldChild"));
            assertEquals(1, target.getChildren().size());

            PersistentEntitySnapshot.applySnapshot(snapshot, target);

            assertTrue(target.getChildren().isEmpty(),
                    "Children not in snapshot should be removed");
        }

        @Test
        @DisplayName("applySnapshot restores grandchildren recursively")
        void applyRestoresGrandchildren() {
            // Build: Hero -> Inventory -> Sword
            GameObject original = createPlayerEntity("Hero");
            GameObject child = new GameObject("Inventory");
            GameObject grandchild = new GameObject("Sword");
            com.pocket.rpg.components.interaction.SpawnPoint sp = new com.pocket.rpg.components.interaction.SpawnPoint();
            sp.setSpawnId("gc_spawn");
            grandchild.addComponent(sp);
            child.addChild(grandchild);
            original.addChild(child);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            // Target has no children at all
            GameObject target = createPlayerEntity("Hero");

            PersistentEntitySnapshot.applySnapshot(snapshot, target);

            // Verify hierarchy: target -> Inventory -> Sword
            assertEquals(1, target.getChildren().size());
            GameObject restoredChild = target.getChildren().get(0);
            assertEquals("Inventory", restoredChild.getName());
            assertEquals(1, restoredChild.getChildren().size());
            GameObject restoredGrandchild = restoredChild.getChildren().get(0);
            assertEquals("Sword", restoredGrandchild.getName());
        }

        @Test
        @DisplayName("createFromSnapshot recreates children for scratch entity")
        void createFromSnapshotRecreatesChildren() {
            GameObject original = createPlayerEntity("Hero");
            GameObject child = new GameObject("Shield");
            original.addChild(child);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            GameObject created = PersistentEntitySnapshot.createFromSnapshot(snapshot);

            assertNotNull(created);
            assertEquals(1, created.getChildren().size());
            assertEquals("Shield", created.getChildren().get(0).getName());
        }

        @Test
        @DisplayName("createFromSnapshot recreates grandchildren for scratch entity")
        void createFromSnapshotRecreatesGrandchildren() {
            // Build: Hero -> Inventory -> Sword
            GameObject original = createPlayerEntity("Hero");
            GameObject child = new GameObject("Inventory");
            GameObject grandchild = new GameObject("Sword");
            child.addChild(grandchild);
            original.addChild(child);
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            GameObject created = PersistentEntitySnapshot.createFromSnapshot(snapshot);

            assertNotNull(created);
            assertEquals(1, created.getChildren().size());
            GameObject createdChild = created.getChildren().get(0);
            assertEquals("Inventory", createdChild.getName());
            assertEquals(1, createdChild.getChildren().size());
            assertEquals("Sword", createdChild.getChildren().get(0).getName());
        }

        @Test
        @DisplayName("snapshot with no children produces null children list")
        void snapshotWithNoChildrenHasNullChildren() {
            GameObject entity = createPlayerEntity("Hero");

            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(entity);

            assertNull(snapshot.getChildren());
        }

        @Test
        @DisplayName("duplicate child names does not throw — P7 warning test")
        void duplicateChildNamesDoesNotThrow() {
            // Create entity with two children of the same name
            GameObject original = createPlayerEntity("Hero");
            original.addChild(new GameObject("Sword"));
            original.addChild(new GameObject("Sword"));
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(original);

            GameObject target = createPlayerEntity("Hero");

            // Should not throw, just log a warning
            assertDoesNotThrow(() -> PersistentEntitySnapshot.applySnapshot(snapshot, target));
            // Both children should still end up on target (first matched, second created)
            assertTrue(target.getChildren().size() >= 1);
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private static GameObject createPlayerEntity(String name) {
        GameObject entity = new GameObject(name);
        PersistentEntity pe = new PersistentEntity();
        pe.setEntityTag("Player");
        entity.addComponent(pe);
        return entity;
    }

    /**
     * Registers a simple test prefab with just a PersistentEntity component.
     */
    private void registerTestPrefab() {
        if (PrefabRegistry.getInstance().hasPrefab(TEST_PREFAB_ID)) {
            return;
        }
        PrefabRegistry.getInstance().register(new TestPrefab(
                TEST_PREFAB_ID,
                List.of(new PersistentEntity())
        ));
    }

    /**
     * Registers a test prefab with PersistentEntity + SpawnPoint.
     */
    private void registerTestPrefabWithSpawnPoint(String defaultSpawnId) {
        if (PrefabRegistry.getInstance().hasPrefab(TEST_PREFAB_ID)) {
            PrefabRegistry.getInstance().unregister(TEST_PREFAB_ID);
        }
        com.pocket.rpg.components.interaction.SpawnPoint sp = new com.pocket.rpg.components.interaction.SpawnPoint();
        sp.setSpawnId(defaultSpawnId);
        PrefabRegistry.getInstance().register(new TestPrefab(
                TEST_PREFAB_ID,
                List.of(new PersistentEntity(), sp)
        ));
    }

    /**
     * Minimal Prefab implementation for testing.
     */
    private record TestPrefab(String id, List<Component> templateComponents) implements Prefab {

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return "Test Prefab";
        }

        @Override
        public List<Component> getComponents() {
            return templateComponents;
        }

        @Override
        public Sprite getPreviewSprite() {
            return null;
        }
    }
}
