# Testing Improvement Implementation Plan

## Overview

**Goal:** Improve test coverage from 6.7% with a focus on foundation systems and create a flexible integration testing framework.

**Priority Order:** Asset Pipeline → Serialization → Collision → Animation

**Scope:** Runtime systems + critical editor paths (serialization round-trips, undo/redo)

**Estimated Output:** ~35 new test files, ~220 tests

---

## Current State Analysis

### What's Already Tested (Well)
| System | Files | Coverage |
|--------|-------|----------|
| Input System | 8 test files | Excellent - comprehensive scenarios |
| Time System | 3 test files | Excellent - simulation, time scale |
| Transitions | 5 test files | Excellent - state machine coverage |
| GameObject/Scene | 5 test files | Good - lifecycle, hierarchy |

### Major Gaps (No Tests)
| System | Source Files | Priority |
|--------|--------------|----------|
| Asset Pipeline | 21 files | Critical |
| Serialization | 16 files | Critical |
| Collision System | 15 files | High |
| Animation | 6 files | Medium |
| Rendering | 51 files | Low (visual) |
| Editor | 179 files | Low (manual testing) |

### Existing Test Utilities
- `HeadlessPlatformFactory` - Mock Window, InputBackend, PostProcessor
- `MockTimeContext` - Time simulation (delta, frame count, scale)
- `MockInputTesting` - Input state injection
- `MockSceneManager` - Scene loading with call tracking
- `MockOverlayRenderer` - Rendering call tracking

---

## Phase 1: Integration Testing Framework

### 1.1 TestSceneRunner

**File:** `src/test/java/com/pocket/rpg/testing/TestSceneRunner.java`

**Purpose:** Core framework for headless scene simulation with frame-by-frame control.

**Key Features:**
- Builder pattern for scene setup
- Controlled time progression via MockTimeContext
- Mock asset context integration
- Entity creation helpers
- Fluent assertion API

**API Design:**
```java
public class TestSceneRunner {
    // Builder
    public static Builder create(String sceneName);

    public static class Builder {
        Builder withMockAssets();
        Builder withCollisionMap(int width, int height);
        Builder withTimeContext(MockTimeContext time);
        TestSceneRunner build();
    }

    // Simulation
    void step(float deltaTime);                    // Single frame
    void stepFrames(int count, float deltaTime);   // Multiple frames
    void stepUntil(Predicate<TestSceneRunner> condition, float dt, int maxFrames);

    // Entity management
    EntityBuilder createEntity(String name, float x, float y);
    GameObject findEntity(String name);
    List<GameObject> findEntitiesByTag(String tag);

    // Direct access
    TestScene getScene();
    CollisionMap getCollisionMap();
    CollisionSystem getCollisionSystem();
    MockAssetContext getAssets();
    MockTimeContext getTime();

    // Assertions
    SceneAssertions assertThat();
}
```

**Usage Example:**
```java
@Test
void playerMovesWhenNotBlocked() {
    TestSceneRunner runner = TestSceneRunner.create("TestScene")
        .withMockAssets()
        .withCollisionMap(20, 20)
        .build();

    GameObject player = runner.createEntity("Player", 5 * 16, 5 * 16)
        .with(GridMovement.class, gm -> gm.setSpeed(4.0f))
        .build();

    GridMovement movement = player.getComponent(GridMovement.class);
    movement.move(Direction.RIGHT);

    runner.stepFrames(60, 1.0f / 60.0f);  // 1 second at 60fps

    runner.assertThat().entity("Player").isAtTile(6, 5);
}
```

### 1.2 MockAssetContext

**File:** `src/test/java/com/pocket/rpg/testing/MockAssetContext.java`

**Purpose:** In-memory asset system for testing without file I/O.

**Key Features:**
- Programmatic asset registration
- Load/save call tracking
- Configurable path resolution
- Sub-asset support

**API Design:**
```java
public class MockAssetContext implements AssetContext {
    // Registration
    <T> void register(String path, T asset);
    <T> void registerWithSubAssets(String path, T asset, Map<String, Object> subAssets);

    // Verification
    int getLoadCount(String path);
    List<String> getLoadOrder();
    boolean wasLoaded(String path);
    boolean wasSaved(String path);

    // Configuration
    void setAssetRoot(String root);
    void setErrorMode(ErrorMode mode);

    // AssetContext implementation
    @Override <T> T load(String path);
    @Override <T> T load(String path, Class<T> type);
    @Override <T> T load(String path, Class<T> type, LoadOptions options);
    @Override String getPathForResource(Object resource);
    @Override void save(Object resource, String path);
    // ... other methods
}
```

**Usage Example:**
```java
@BeforeEach
void setUp() {
    mockAssets = new MockAssetContext();
    mockAssets.setAssetRoot("gameData/assets/");

    // Register test assets
    Sprite mockSprite = mock(Sprite.class);
    mockAssets.register("sprites/player.png", mockSprite);

    SpriteSheet mockSheet = mock(SpriteSheet.class);
    when(mockSheet.getSprite(0)).thenReturn(mockSprite);
    mockAssets.registerWithSubAssets("sheets/player.spritesheet", mockSheet,
        Map.of("0", mockSprite, "1", mock(Sprite.class)));

    Assets.initialize(mockAssets);
}

@Test
void loadsSubAssetFromSpritesheet() {
    Sprite sprite = Assets.load("sheets/player.spritesheet#0", Sprite.class);

    assertNotNull(sprite);
    assertTrue(mockAssets.wasLoaded("sheets/player.spritesheet"));
}
```

### 1.3 TestScene

**File:** `src/test/java/com/pocket/rpg/testing/TestScene.java`

**Purpose:** Minimal Scene subclass with test instrumentation.

```java
public class TestScene extends Scene {
    private boolean onLoadCalled = false;
    private boolean onUnloadCalled = false;
    private List<String> eventLog = new ArrayList<>();

    // Lifecycle tracking
    @Override public void onLoad() { onLoadCalled = true; logEvent("onLoad"); }
    @Override public void onUnload() { onUnloadCalled = true; logEvent("onUnload"); }

    // Test helpers
    public boolean wasOnLoadCalled();
    public boolean wasOnUnloadCalled();
    public List<String> getEventLog();
    public void logEvent(String event);
    public void clearEventLog();
}
```

### 1.4 EntityBuilder

**File:** `src/test/java/com/pocket/rpg/testing/EntityBuilder.java`

**Purpose:** Fluent API for creating test entities with components.

```java
public class EntityBuilder {
    public EntityBuilder(String name, Scene scene);

    EntityBuilder at(float x, float y);
    EntityBuilder at(float x, float y, float z);
    EntityBuilder atTile(int tileX, int tileY);
    EntityBuilder withParent(GameObject parent);
    EntityBuilder withTag(String tag);

    <T extends Component> EntityBuilder with(Class<T> componentClass);
    <T extends Component> EntityBuilder with(Class<T> componentClass, Consumer<T> configurer);
    EntityBuilder with(Component instance);

    GameObject build();
}
```

### 1.5 Assertion Classes

**Directory:** `src/test/java/com/pocket/rpg/testing/assertions/`

**GameObjectAssertions.java:**
```java
public class GameObjectAssertions {
    public GameObjectAssertions(GameObject gameObject);

    GameObjectAssertions isAt(float x, float y);
    GameObjectAssertions isAt(float x, float y, float tolerance);
    GameObjectAssertions isAtTile(int tileX, int tileY);
    GameObjectAssertions isActive();
    GameObjectAssertions isInactive();
    GameObjectAssertions hasName(String name);
    GameObjectAssertions hasTag(String tag);
    GameObjectAssertions hasComponent(Class<? extends Component> type);
    GameObjectAssertions doesNotHaveComponent(Class<? extends Component> type);
    GameObjectAssertions hasParent(GameObject parent);
    GameObjectAssertions hasNoParent();
    GameObjectAssertions hasChildCount(int count);

    <T extends Component> T getComponent(Class<T> type);
}
```

**CollisionAssertions.java:**
```java
public class CollisionAssertions {
    public CollisionAssertions(CollisionSystem system, CollisionMap map);

    CollisionAssertions canMoveTo(int fromX, int fromY, int toX, int toY, Direction dir);
    CollisionAssertions cannotMoveTo(int fromX, int fromY, int toX, int toY, Direction dir);
    CollisionAssertions hasCollisionAt(int x, int y, CollisionType type);
    CollisionAssertions hasNoCollisionAt(int x, int y);
    CollisionAssertions entityOccupies(Object entity, int x, int y);
    CollisionAssertions tileIsEmpty(int x, int y);
}
```

**SceneAssertions.java:**
```java
public class SceneAssertions {
    public SceneAssertions(Scene scene);

    SceneAssertions hasEntityCount(int count);
    SceneAssertions hasEntity(String name);
    SceneAssertions doesNotHaveEntity(String name);
    GameObjectAssertions entity(String name);
    CollisionAssertions collision();
}
```

### 1.6 Additional Utilities

**ComponentTestUtils.java:**
```java
public class ComponentTestUtils {
    // Field access without reflection boilerplate
    static Object getFieldValue(Component component, String fieldName);
    static void setFieldValue(Component component, String fieldName, Object value);

    // Component creation
    static <T extends Component> T createComponent(Class<T> type);
    static <T extends Component> T createComponent(Class<T> type, Consumer<T> configurer);

    // Lifecycle simulation
    static void simulateStart(Component component);
    static void simulateUpdate(Component component, float deltaTime);
    static void simulateDestroy(Component component);
}
```

**JsonTestUtils.java:**
```java
public class JsonTestUtils {
    // JSON comparison (ignores formatting)
    static void assertJsonEquals(String expected, String actual);
    static void assertJsonContains(String json, String path, Object value);

    // Test data
    static String loadTestJson(String resourcePath);
    static <T> T loadTestObject(String resourcePath, Class<T> type);
}
```

---

## Phase 2: Asset Pipeline Tests

**Target Directory:** `src/test/java/com/pocket/rpg/resources/`

### 2.1 ResourceCacheTest.java

**Test Scenarios:**
```java
@Nested @DisplayName("Put and Get Operations")
class PutGetTests {
    @Test void get_returnsCachedObject_whenPreviouslyPut()
    @Test void get_returnsNull_whenNotCached()
    @Test void get_returnsSameInstance_onMultipleCalls()
}

@Nested @DisplayName("Statistics Tracking")
class StatisticsTests {
    @Test void recordsHit_whenCacheContainsKey()
    @Test void recordsMiss_whenCacheDoesNotContainKey()
    @Test void hitRate_calculatesCorrectly()
    @Test void totalLoads_incrementsOnEachPut()
}

@Nested @DisplayName("Size Limits")
class SizeLimitTests {
    @Test void warnsWhenFull_doesNotAddMore()
    @Test void respectsConfiguredMaxSize()
}

@Nested @DisplayName("Type Filtering")
class TypeFilteringTests {
    @Test void getAllOfType_returnsOnlyMatchingTypes()
    @Test void getAllOfType_returnsEmptyList_whenNoMatches()
}

@Nested @DisplayName("Thread Safety")
class ThreadSafetyTests {
    @Test void concurrentPutsDoNotLoseData()
    @Test void concurrentGetsReturnConsistentResults()
}
```

### 2.2 AssetManagerPathTest.java

**Test Scenarios:**
```java
@Nested @DisplayName("Path Normalization")
class NormalizationTests {
    @Test void convertsBackslashesToForwardSlashes()
    @Test void removesLeadingDotSlash()
    @Test void preservesForwardSlashes()
    @Test void handlesMultipleBackslashes()
}

@Nested @DisplayName("Asset Root Resolution")
class AssetRootTests {
    @Test void prependsAssetRoot_forRelativePaths()
    @Test void skipsAssetRoot_forAbsolutePaths()
    @Test void skipsAssetRoot_whenRawOptionSet()
    @Test void detectsAbsolutePath_withDriveLetter()
    @Test void detectsAbsolutePath_startingWithSlash()
}

@Nested @DisplayName("Sub-Asset Path Parsing")
class SubAssetPathTests {
    @Test void extractsBasePath_beforeHashSymbol()
    @Test void extractsSubId_afterHashSymbol()
    @Test void handlesNoSubAsset_whenNoHash()
    @Test void handlesMultipleHashes_usesFirst()
}
```

### 2.3 AssetManagerLoadTest.java

**Test Scenarios:**
```java
@Nested @DisplayName("Caching Behavior")
class CachingTests {
    @Test void returnsCachedInstance_onSecondLoad()
    @Test void loadsFromLoader_whenNotCached()
    @Test void skipsCache_whenUncachedOptionSet()
    @Test void cachesSeparately_forDifferentPaths()
}

@Nested @DisplayName("Type Inference")
class TypeInferenceTests {
    @Test void infersTextureType_forPngExtension()
    @Test void infersSpriteSheetType_forSpritesheetExtension()
    @Test void infersAnimationType_forAnimExtension()
    @Test void handlesCompoundExtensions_correctly()
    @Test void throwsForUnknownExtension_whenNoTypeProvided()
}

@Nested @DisplayName("Sub-Asset Loading")
class SubAssetLoadingTests {
    @Test void loadsParentFirst_thenExtractsSubAsset()
    @Test void cachesSubAssetSeparately_withFullPath()
    @Test void throwsForInvalidSubAssetId()
    @Test void tracksResourcePath_includingSubAssetId()
}

@Nested @DisplayName("Error Handling")
class ErrorHandlingTests {
    @Test void throwsRuntimeException_inThrowMode()
    @Test void returnsPlaceholder_inPlaceholderMode()
    @Test void logsError_inPlaceholderMode()
}
```

### 2.4 AssetLoaderContractTest.java (Parameterized)

**Test all loaders with shared contract:**
```java
@ParameterizedTest
@MethodSource("allLoaders")
void getSupportedExtensions_returnsNonEmptyArray(AssetLoader<?> loader) {
    String[] extensions = loader.getSupportedExtensions();
    assertNotNull(extensions);
    assertTrue(extensions.length > 0);
    for (String ext : extensions) {
        assertTrue(ext.startsWith("."));
    }
}

@ParameterizedTest
@MethodSource("allLoaders")
void load_throwsIOException_forInvalidPath(AssetLoader<?> loader) {
    assertThrows(IOException.class, () ->
        loader.load("nonexistent/path/to/nothing.invalid"));
}

static Stream<AssetLoader<?>> allLoaders() {
    return Stream.of(
        new TextureLoader(),
        new SpriteLoader(),
        new SpriteSheetLoader(),
        new AnimationLoader(),
        new ShaderLoader(),
        new FontLoader()
    );
}
```

### 2.5 AssetHotReloadTest.java

**Purpose:** Test hot-reload contract enforcement and reload behavior.

**Test Scenarios:**
```java
@Nested @DisplayName("AssetLoader Default Behavior")
class DefaultBehaviorTests {
    @Test void reload_throwsException_whenSupportsHotReloadTrueButNotOverridden() {
        // Loader that returns supportsHotReload()=true but uses default reload()
        AssetLoader<Object> loader = new AssetLoader<>() {
            public Object load(String p) { return new Object(); }
            public void save(Object r, String p) {}
            public Object getPlaceholder() { return null; }
            public String[] getSupportedExtensions() { return new String[]{".test"}; }
            public boolean supportsHotReload() { return true; } // true but not overridden!
        };

        Object existing = new Object();
        assertThrows(UnsupportedOperationException.class,
            () -> loader.reload(existing, "test.test"));
    }

    @Test void exceptionMessage_containsLoaderNameAndFixInstructions()
}

@Nested @DisplayName("Contract Violation Detection")
class ContractViolationTests {
    @Test void reload_logsError_whenLoaderReturnsNewReference()
    @Test void reload_keepsOldAsset_whenContractViolated()
    @Test void reload_succeedsQuietly_whenSameReferenceReturned()
}

@Nested @DisplayName("Assets.reload() API")
class AssetsReloadTests {
    @Test void reload_returnsFalse_whenAssetNotCached()
    @Test void reload_returnsFalse_whenLoaderDoesNotSupportHotReload()
    @Test void reload_returnsTrue_whenSuccessful()
    @Test void reload_stripsSubAssetSuffix_andReloadsParent()
}

@Nested @DisplayName("Assets.reloadAll() API")
class AssetsReloadAllTests {
    @Test void reloadAll_returnsCountOfReloadedAssets()
    @Test void reloadAll_skipsSubAssets()
    @Test void reloadAll_skipsAssetsWithoutHotReloadSupport()
}

@Nested @DisplayName("Sprite.reloadMetadata()")
class SpriteReloadMetadataTests {
    @Test void reloadMetadata_resetsPivotToDefault_whenMetadataNull()
    @Test void reloadMetadata_appliesPivot_fromSingleModeMetadata()
    @Test void reloadMetadata_appliesNineSlice_fromSingleModeMetadata()
    @Test void reloadMetadata_appliesPpuOverride()
    @Test void reloadMetadata_usesDefaultPivot_forMultipleModeMetadata()
}
```

**Note:** `Texture.reloadFromDisk()` and `Shader.reloadFromDisk()` require OpenGL context and are better suited for manual/integration testing. The unit tests focus on the contract enforcement and API behavior which can be tested with mocks.

**Dependencies:** Requires `MockAssetContext` from Phase 1.

---

## Phase 3: Serialization Tests

**Target Directory:** `src/test/java/com/pocket/rpg/serialization/`

### 3.1 ComponentRegistryTest.java

**Test Scenarios:**
```java
@Nested @DisplayName("Initialization")
class InitializationTests {
    @Test void scansComponentsPackage_onInitialize()
    @Test void registersAllNonAbstractComponents()
    @Test void skipsAbstractClasses()
    @Test void skipsInterfaces()
}

@Nested @DisplayName("Lookup Operations")
class LookupTests {
    @Test void getBySimpleName_returnsCorrectMeta()
    @Test void getByClassName_returnsCorrectMeta()
    @Test void returnsNull_forUnknownComponent()
}

@Nested @DisplayName("Instantiation")
class InstantiationTests {
    @Test void instantiate_createsComponentInstance()
    @Test void instantiate_usesNoArgConstructor()
    @Test void throws_forComponentWithoutNoArgConstructor()
}

@Nested @DisplayName("Field Metadata")
class FieldMetadataTests {
    @Test void includesPublicFields()
    @Test void includesPrivateFields()
    @Test void excludesStaticFields()
    @Test void excludesTransientFields()
    @Test void excludesHideInInspectorFields()
    @Test void excludesComponentRefFields()
    @Test void capturesFieldType()
    @Test void capturesFieldName()
}

@Nested @DisplayName("ComponentRef Metadata")
class ComponentRefMetadataTests {
    @Test void collectsComponentRefFields()
    @Test void capturesSourceAttribute()
    @Test void capturesRequiredAttribute()
    @Test void supportsListFields()
    @Test void extractsGenericTypeForLists()
}
```

### 3.2 SerializationRoundTripTest.java

**Test Scenarios:**
```java
@Nested @DisplayName("Primitive Fields")
class PrimitiveFieldTests {
    @Test void serializesInt_correctly()
    @Test void serializesFloat_correctly()
    @Test void serializesBoolean_correctly()
    @Test void serializesString_correctly()
    @Test void handlesNullString()
}

@Nested @DisplayName("Vector Fields")
class VectorFieldTests {
    @Test void serializesVector2f_correctly()
    @Test void serializesVector3f_correctly()
    @Test void serializesVector4f_correctly()
    @Test void handlesNullVector()
}

@Nested @DisplayName("Enum Fields")
class EnumFieldTests {
    @Test void serializesEnum_asString()
    @Test void deserializesEnum_fromString()
    @Test void handlesNullEnum()
}

@Nested @DisplayName("Asset References")
class AssetReferenceTests {
    @Test void serializesAsset_asClassNameColonPath()
    @Test void deserializesAsset_loadsFromPath()
    @Test void handlesSpriteSheetSubAsset()
    @Test void handlesNullAsset()
}

@Nested @DisplayName("Component Polymorphism")
class PolymorphismTests {
    @Test void preservesComponentType_onRoundTrip()
    @Test void handlesMultipleComponentTypes_inSameScene()
}
```

### 3.3 ComponentRefResolverTest.java

**Test Scenarios:**
```java
@Nested @DisplayName("SELF Source")
class SelfSourceTests {
    @Test void resolvesComponent_onSameGameObject()
    @Test void returnsNull_whenComponentNotFound()
    @Test void resolvesList_withAllMatchingComponents()
}

@Nested @DisplayName("PARENT Source")
class ParentSourceTests {
    @Test void resolvesComponent_onParentGameObject()
    @Test void returnsNull_whenNoParent()
    @Test void returnsNull_whenParentLacksComponent()
}

@Nested @DisplayName("CHILDREN Source")
class ChildrenSourceTests {
    @Test void resolvesComponent_fromDirectChildren()
    @Test void ignoresGrandchildren()
    @Test void returnsFirstMatch_forSingleField()
    @Test void returnsAllMatches_forListField()
}

@Nested @DisplayName("CHILDREN_RECURSIVE Source")
class ChildrenRecursiveSourceTests {
    @Test void resolvesComponent_fromAllDescendants()
    @Test void usesDepthFirstOrder()
    @Test void returnsFirstMatch_forSingleField()
    @Test void returnsAllMatches_forListField()
}

@Nested @DisplayName("Required vs Optional")
class RequiredTests {
    @Test void logsWarning_whenRequiredRefNotFound()
    @Test void noWarning_whenOptionalRefNotFound()
}
```

### 3.4 SceneDataTest.java

**Test Scenarios:**
```java
@Nested @DisplayName("Empty Scene")
class EmptySceneTests {
    @Test void serializesEmptyScene()
    @Test void deserializesEmptyScene()
    @Test void preservesSceneName()
    @Test void setsVersion4()
}

@Nested @DisplayName("Entity Hierarchy")
class HierarchyTests {
    @Test void serializesParentChildRelationship()
    @Test void deserializesParentChildRelationship()
    @Test void preservesSiblingOrder()
    @Test void handlesDeeplyNestedHierarchy()
}

@Nested @DisplayName("Prefab Instances")
class PrefabTests {
    @Test void serializesPrefabId()
    @Test void serializesComponentOverrides()
    @Test void deserializesPrefabInstance()
    @Test void appliesOverridesToPrefab()
}

@Nested @DisplayName("Version Migration")
class MigrationTests {
    @Test void migratesV3ToV4()
    @Test void preservesDataDuringMigration()
}
```

---

## Phase 4: Collision System Tests

**Target Directory:** `src/test/java/com/pocket/rpg/collision/`

### 4.1 CollisionMapTest.java

**Test Scenarios:**
```java
@Nested @DisplayName("Basic Operations")
class BasicOperationsTests {
    @Test void set_storesCollisionType()
    @Test void get_returnsStoredType()
    @Test void get_returnsNone_forUnsetTile()
    @Test void clear_removesCollisionType()
}

@Nested @DisplayName("Chunk Management")
class ChunkManagementTests {
    @Test void createsChunk_onFirstTileSet()
    @Test void reusesChunk_forNearbyTiles()
    @Test void handlesNegativeCoordinates()
    @Test void handlesLargeCoordinates()
}

@Nested @DisplayName("Z-Level Support")
class ZLevelTests {
    @Test void separatesTilesByZLevel()
    @Test void returnsNone_forUnsetZLevel()
    @Test void getZLevels_returnsAllActiveLevels()
}

@Nested @DisplayName("Tile Counting")
class TileCountingTests {
    @Test void getTileCount_returnsZero_forEmptyMap()
    @Test void getTileCount_countsNonNoneTiles()
    @Test void getTileCount_excludesNoneTiles()
}
```

### 4.2 CollisionMapSerializationTest.java

**Test Scenarios:**
```java
@Test void emptyMap_roundTrip()
@Test void singleTile_roundTrip()
@Test void multipleZLevels_roundTrip()
@Test void largeMap_roundTrip()
@Test void allCollisionTypes_roundTrip()
@Test void negativeCoordinates_roundTrip()
@Test void invalidBase64_throwsException()
```

### 4.3 CollisionTypeTest.java

**Test Scenarios:**
```java
@ParameterizedTest @EnumSource(CollisionType.class)
void fromId_returnsCorrectType(CollisionType type) {
    assertEquals(type, CollisionType.fromId(type.getId()));
}

@Test void fromId_returnsNone_forInvalidId()

@Test void isLedge_trueForLedgeTypes()
@Test void isLedge_falseForNonLedgeTypes()

@Test void isSpecialTerrain_trueForWaterGrassIceSand()
@Test void isSpecialTerrain_falseForOthers()

@Test void isInteractionTrigger_trueForWarpDoorScript()
@Test void isInteractionTrigger_falseForOthers()
```

### 4.4 TileBehavior Tests

**SolidBehaviorTest.java:**
```java
@Test void blocksAllDirections_whenNotFlying()
@Test void allowsAllDirections_whenFlying()
@Test void returnsBlockedResult_withReason()
```

**LedgeBehaviorTest.java:**
```java
@Nested @DisplayName("LEDGE_DOWN")
class LedgeDownTests {
    @Test void allowsMovingDown_withJumpModifier()
    @Test void blocksMovingUp()
    @Test void allowsMovingLeft()
    @Test void allowsMovingRight()
}

// Similar nested classes for LEDGE_UP, LEDGE_LEFT, LEDGE_RIGHT
```

**WaterBehaviorTest.java:**
```java
@Test void blocksMovement_whenCannotSwim()
@Test void allowsMovement_whenCanSwim()
@Test void allowsMovement_whenCanFly()
@Test void returnsSwimModifier_whenSwimming()
```

**IceBehaviorTest.java:**
```java
@Test void alwaysAllowsMovement()
@Test void returnsSlideModifier()
```

**SandBehaviorTest.java:**
```java
@Test void alwaysAllowsMovement()
@Test void returnsSlowModifier()
@Test void slowModifierHas60PercentSpeed()
```

**TallGrassBehaviorTest.java:**
```java
@Test void alwaysAllowsMovement()
@Test void returnsEncounterModifier_whenTriggersEncountersTrue()
@Test void returnsNormalModifier_whenTriggersEncountersFalse()
```

### 4.5 CollisionSystemTest.java

**Test Scenarios:**
```java
@Nested @DisplayName("Movement Queries")
class MovementQueryTests {
    @Test void canMove_delegatesToTileBehavior()
    @Test void canMove_checksEntityOccupancy()
    @Test void canMove_usesDefaultZLevel()
    @Test void canMove_respectsZLevel()
}

@Nested @DisplayName("Entity Management")
class EntityManagementTests {
    @Test void registerEntity_tracksPosition()
    @Test void unregisterEntity_removesTracking()
    @Test void moveEntity_updatesPosition()
    @Test void entityDoesNotBlockItself()
}

@Nested @DisplayName("Behavior Triggers")
class BehaviorTriggerTests {
    @Test void triggerEnter_callsBehaviorOnEnter()
    @Test void triggerExit_callsBehaviorOnExit()
}
```

### 4.6 GridMovementTest.java

**Test Scenarios:**
```java
@Nested @DisplayName("Basic Movement")
class BasicMovementTests {
    @Test void move_startsMovement_whenNotBlocked()
    @Test void move_doesNotStart_whenAlreadyMoving()
    @Test void move_doesNotStart_whenBlocked()
    @Test void updatesFacingDirection()
}

@Nested @DisplayName("Movement Interpolation")
class InterpolationTests {
    @Test void interpolates_overTime()
    @Test void snapsToGrid_whenComplete()
    @Test void appliesSpeedModifier()
}

@Nested @DisplayName("Ice Sliding")
class IceSlidingTests {
    @Test void continuesSliding_onIceTile()
    @Test void stopsSliding_onNonIceTile()
    @Test void stopsSliding_whenBlocked()
}

@Nested @DisplayName("Ledge Jumping")
class LedgeJumpingTests {
    @Test void jumpsOverLedge_inCorrectDirection()
    @Test void hasParabolicArc_duringJump()
    @Test void blockedByLedge_inWrongDirection()
}

@Nested @DisplayName("Entity Occupancy")
class OccupancyTests {
    @Test void updatesOccupancy_atMovementStart()
    @Test void triggersExit_atMovementStart()
    @Test void triggersEnter_atMovementEnd()
}
```

---

## Phase 5: Animation Tests

**Target Directory:** `src/test/java/com/pocket/rpg/animation/`

### 5.1 AnimationLoaderTest.java

**Test Scenarios:**
```java
@Nested @DisplayName("Valid Animation Loading")
class ValidLoadingTests {
    @Test void loadsAnimationFromJson()
    @Test void parsesName()
    @Test void parsesLoopingFlag()
    @Test void parsesFrames()
    @Test void parsesFrameSpritePath()
    @Test void parsesFrameDuration()
}

@Nested @DisplayName("Validation")
class ValidationTests {
    @Test void throwsForMissingName()
    @Test void throwsForMissingFrames()
    @Test void throwsForEmptyFrames()
    @Test void throwsForFrameMissingSprite()
    @Test void throwsForFrameMissingDuration()
}

@Nested @DisplayName("Round Trip")
class RoundTripTests {
    @Test void saveAndLoad_preservesData()
}
```

### 5.2 AnimationTest.java

**Test Scenarios:**
```java
@Test void getFrameCount_returnsCorrectCount()
@Test void getFrameSprite_returnsCorrectSprite()
@Test void getTotalDuration_sumsFrameDurations()

@Nested @DisplayName("Frame At Time")
class FrameAtTimeTests {
    @Test void returnsFirstFrame_atTimeZero()
    @Test void returnsCorrectFrame_atMiddleTime()
    @Test void returnsLastFrame_atEndTime()
    @Test void wrapsAround_whenLooping()
    @Test void clampsToLast_whenNotLooping()
}
```

### 5.3 AnimationComponentTest.java

**Test Scenarios:**
```java
@Nested @DisplayName("State Transitions")
class StateTransitionTests {
    @Test void initialState_isStopped()
    @Test void play_changesStateToPlaying()
    @Test void pause_changesStateToPaused()
    @Test void stop_changesStateToStopped()
    @Test void stop_resetsPlaybackTime()
}

@Nested @DisplayName("Playback")
class PlaybackTests {
    @Test void advancesFrame_withTime()
    @Test void updatesSpriteRenderer_onFrameChange()
    @Test void loops_whenAnimationLoops()
    @Test void finishes_whenAnimationDoesNotLoop()
}

@Nested @DisplayName("Animation Switching")
class AnimationSwitchingTests {
    @Test void setAnimation_changesCurrentAnimation()
    @Test void setAnimation_resetsPlaybackTime()
}

@Nested @DisplayName("Auto Play")
class AutoPlayTests {
    @Test void autoPlay_startsAnimationOnStart()
    @Test void noAutoPlay_staysStoppedOnStart()
}
```

---

## Phase 6: Editor Critical Path Tests

**Target Directory:** `src/test/java/com/pocket/rpg/editor/`

### 6.1 SceneSaveLoadTest.java

**Test Scenarios:**
```java
@Test void emptyScene_roundTrip()
@Test void sceneWithSingleEntity_roundTrip()
@Test void sceneWithMultipleEntities_roundTrip()
@Test void sceneWithHierarchy_roundTrip()
@Test void sceneWithAllComponentTypes_roundTrip()
@Test void sceneWithPrefabInstances_roundTrip()
@Test void sceneWithCollisionData_roundTrip()
@Test void sceneWithCameraSettings_roundTrip()
```

### 6.2 UndoManagerTest.java

**Test Scenarios:**
```java
@Nested @DisplayName("Basic Operations")
class BasicOperationsTests {
    @Test void execute_runsCommand()
    @Test void undo_revertsLastCommand()
    @Test void redo_reExecutesUndoneCommand()
    @Test void canUndo_returnsTrueAfterExecute()
    @Test void canRedo_returnsTrueAfterUndo()
}

@Nested @DisplayName("Stack Management")
class StackManagementTests {
    @Test void newCommand_clearsRedoStack()
    @Test void undoStack_respectsMaxSize()
    @Test void clear_emptiesBothStacks()
}

@Nested @DisplayName("Command Merging")
class CommandMergingTests {
    @Test void mergesCommands_withinTimeWindow()
    @Test void doesNotMerge_outsideTimeWindow()
    @Test void doesNotMerge_differentCommandTypes()
}

@Nested @DisplayName("Enable/Disable")
class EnableDisableTests {
    @Test void disabled_doesNotRecordCommands()
    @Test void disabled_stillExecutesCommands()
}
```

### 6.3 EditorAssetLoadingTest.java

**Test Scenarios:**
```java
@Test void getTypeForPath_detectsTextureType()
@Test void getTypeForPath_detectsSpriteSheetType()
@Test void getTypeForPath_detectsAnimationType()
@Test void getEditorPanelType_returnsSpriteSheetEditor_forSpriteSheet()
@Test void getEditorPanelType_returnsAnimationEditor_forAnimation()
@Test void canInstantiate_trueForTexture()
@Test void canInstantiate_trueForAnimation()
@Test void instantiate_createsEntityWithCorrectComponents()
```

### 6.4 SpriteEditorPanelTest.java

**Purpose:** Test sprite metadata editing logic, especially mode transitions.

**Background:** A crash was discovered when switching sprites from SINGLE to MULTIPLE mode - the cached sprite's actual mode didn't match the updated metadata, causing `Assets.getSpriteGrid()` to fail on a SINGLE-mode sprite.

**Test Scenarios:**
```java
@Nested @DisplayName("Apply Metadata to Sprite")
class ApplyMetadataTests {
    @Test void appliesPivot_whenSingleMode()
    @Test void appliesNineSlice_whenSingleMode()
    @Test void clearsGridCache_whenMultipleMode()
    @Test void doesNotCrash_whenModeChangedFromSingleToMultiple()
    @Test void doesNotCrash_whenModeChangedFromMultipleToSingle()
    @Test void skipsUpdate_whenSpriteModeDoesNotMatchMetadata()
}

@Nested @DisplayName("Save Changes")
class SaveChangesTests {
    @Test void savesMetadataToFile()
    @Test void updatesOriginalMetadata_afterSave()
    @Test void clearsUnsavedChangesFlag_afterSave()
    @Test void publishesAssetChangedEvent_afterSave()
}

@Nested @DisplayName("Revert Changes")
class RevertChangesTests {
    @Test void restoresOriginalMetadata()
    @Test void reloadsTabsFromMetadata()
    @Test void clearsUnsavedChangesFlag()
}
```

**Dependencies:** Requires `MockAssetContext` from Phase 1 to mock `Assets.load()`, `Assets.isMultipleMode()`, and `Assets.getSpriteGrid()`.

---

## Phase 7: End-to-End Integration Tests

**Target Directory:** `src/test/java/com/pocket/rpg/integration/`

### 7.1 SceneCollisionIntegrationTest.java

Full scene simulation with collision:
```java
@Test void gridMovement_respectsSolidTiles()
@Test void gridMovement_jumpsOverLedges()
@Test void gridMovement_slidesOnIce()
@Test void gridMovement_slowsOnSand()
@Test void entityBlocking_preventsMovement()
@Test void multiFrameMovement_completesCorrectly()
```

### 7.2 AnimationPlaybackIntegrationTest.java

Animation with SpriteRenderer in scene:
```java
@Test void animation_playsAndUpdatesSprite()
@Test void animation_loopsCorrectly()
@Test void animation_finishesAndStops()
@Test void animationComponent_resolvesComponentRef()
```

### 7.3 AssetSerializationIntegrationTest.java

Assets through full serialization pipeline:
```java
@Test void componentWithSpriteReference_serializesCorrectly()
@Test void deserializedComponent_loadsSpriteFromPath()
@Test void spritesheetSubAssetReference_worksInComponent()
@Test void animationReference_worksInComponent()
```

---

## File Summary

### New Test Utilities (Phase 1)
| File | Lines (est.) |
|------|--------------|
| `testing/TestSceneRunner.java` | ~200 |
| `testing/MockAssetContext.java` | ~150 |
| `testing/TestScene.java` | ~50 |
| `testing/EntityBuilder.java` | ~80 |
| `testing/ComponentTestUtils.java` | ~60 |
| `testing/JsonTestUtils.java` | ~40 |
| `testing/assertions/GameObjectAssertions.java` | ~100 |
| `testing/assertions/CollisionAssertions.java` | ~80 |
| `testing/assertions/SceneAssertions.java` | ~60 |

### Test Files by Phase
| Phase | Files | Est. Tests |
|-------|-------|------------|
| 2: Asset Pipeline | 5 | ~55 |
| 3: Serialization | 4 | ~50 |
| 4: Collision | 10 | ~70 |
| 5: Animation | 3 | ~25 |
| 6: Editor Paths | 4 | ~32 |
| 7: Integration | 3 | ~15 |
| **Total** | **29** | **~247** |

---

## Verification

After implementation:
```bash
# Run all tests
mvn test

# Run specific phase
mvn test -Dtest="com.pocket.rpg.resources.*"
mvn test -Dtest="com.pocket.rpg.serialization.*"
mvn test -Dtest="com.pocket.rpg.collision.*"

# Generate coverage report (if jacoco configured)
mvn jacoco:report
```

---

## Code Review Step

After implementation, spawn a review agent to:
1. Review all new test utilities for correctness and usability
2. Verify test coverage completeness against this plan
3. Check for missing edge cases
4. Ensure consistent patterns with existing tests
5. Write review to `Documents/Plans/testing-improvement/review.md`
