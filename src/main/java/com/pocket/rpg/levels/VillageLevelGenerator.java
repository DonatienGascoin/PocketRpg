package com.pocket.rpg.levels;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.components.TilemapRenderer.Tile;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.Sprite;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a Pokemon-style village level with:
 * - Grass base layer
 * - Road network
 * - Houses with fenced yards (houses as SpriteRenderer GameObjects)
 * - Trees as SpriteRenderer GameObjects (16x48, 3 tiles tall)
 * - Water pond
 * - Solid collision tracking for obstacles
 */
public class VillageLevelGenerator {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 200;

    private final Random random = new Random(12345); // Fixed seed for reproducibility

    // Sprite lists
    private List<Sprite> outdoorSprites;  // 8 cols x 11 rows, 16x16
    private List<Sprite> roadSprites;     // 16x16
    private List<Sprite> treeSprites;     // 10 cols x 4 rows, 16x48
    private List<Sprite> waterSprites;    // 16x16
    private List<Sprite> houseSprites;    // Single 64x96 sprite
    private List<Sprite> fenceSprites;    // 16x16

    // Tilemaps (attached to level GameObject)
    private TilemapRenderer groundLayer;   // z = -2: grass, water
    private TilemapRenderer roadLayer;     // z = -1: roads
    private TilemapRenderer propsLayer;    // z = 0: flowers, small decorations
    private TilemapRenderer fenceLayer;    // z = 1: fences (solid)

    // Child GameObjects for multi-tile sprites
    private final List<GameObject> treeObjects = new ArrayList<>();
    private final List<GameObject> houseObjects = new ArrayList<>();

    // Collision tracking (since trees/houses aren't in tilemap)
    private final boolean[][] solidMap = new boolean[WIDTH][HEIGHT];

    // ========================================================================
    // SPRITE INDICES - Outdoors_misc.png (8 cols x 11 rows = 88 sprites)
    // ========================================================================
    // Row 0 (indices 0-7): Plain colors / grass
    private static final int GRASS_LIGHT = 0;
    private static final int GRASS_DARK = 1;
    private static final int GRASS_PATCH_1 = 2;
    private static final int GRASS_PATCH_2 = 3;

    // Row 1 (indices 8-15): Walls

    // Rows 2-6 (indices 16-55): Flowers
    private static final int FLOWERS_RED = 16;
    private static final int FLOWERS_YELLOW = 17;
    private static final int FLOWERS_WHITE = 18;
    private static final int FLOWERS_PINK = 19;

    // Row 7 (indices 56-63): Blocks/rocks
    private static final int SMALL_ROCK = 56;
    private static final int BLOCK_STONE = 57;

    // Rows 8-9 (indices 64-79): Objects 2 tiles tall (use bottom tile index)
    // Row 10-11 (indices 80-95): Panels

    // ========================================================================
    // SPRITE INDICES - Trees.png (10 cols x 4 rows, 16x48 each)
    // Pattern: tree, trunk, tree, trunk... per row
    // ========================================================================
    private static final int TREE_GREEN = 0;       // Green tree
    private static final int TREE_GREEN_TRUNK = 1; // Just trunk
    private static final int TREE_DARK = 2;        // Darker green tree
    private static final int TREE_AUTUMN = 4;      // Orange/autumn tree
    private static final int TREE_PINK = 6;        // Pink/cherry blossom
    private static final int TREE_DEAD = 8;        // Dead/bare tree

    // ========================================================================
    // SPRITE INDICES - Fence.png (analyzing image)
    // ========================================================================
    private static final int FENCE_H = 0;
    private static final int FENCE_V = 1;
    private static final int FENCE_POST = 2;
    private static final int FENCE_CORNER_TL = 3;
    private static final int FENCE_CORNER_TR = 4;
    private static final int FENCE_CORNER_BL = 5;
    private static final int FENCE_CORNER_BR = 6;

    // ========================================================================
    // SPRITE INDICES - Water.png (analyzing image - appears to be edge tiles)
    // ========================================================================
    private static final int WATER_CENTER = 0;
    private static final int WATER_EDGE_TOP = 1;
    private static final int WATER_EDGE_BOTTOM = 2;
    private static final int WATER_EDGE_LEFT = 3;
    private static final int WATER_EDGE_RIGHT = 4;
    private static final int WATER_CORNER_TL = 5;
    private static final int WATER_CORNER_TR = 6;
    private static final int WATER_CORNER_BL = 7;
    private static final int WATER_CORNER_BR = 8;
    // Inner corners
    private static final int WATER_INNER_TL = 9;
    private static final int WATER_INNER_TR = 10;
    private static final int WATER_INNER_BL = 11;
    private static final int WATER_INNER_BR = 12;

    // ========================================================================
    // SPRITE INDICES - Road_16x16.png
    // ========================================================================
    private static final int ROAD_CENTER = 0;
    private static final int ROAD_H = 1;
    private static final int ROAD_V = 2;

    public VillageLevelGenerator() {
    }

    /**
     * Sets all sprite lists needed for generation.
     */
    public void setSprites(
            List<Sprite> outdoor,
            List<Sprite> road,
            List<Sprite> trees,
            List<Sprite> water,
            List<Sprite> houses,
            List<Sprite> fences
    ) {
        this.outdoorSprites = outdoor;
        this.roadSprites = road;
        this.treeSprites = trees;
        this.waterSprites = water;
        this.houseSprites = houses;
        this.fenceSprites = fences;
    }

    /**
     * Generates the complete village level.
     *
     * @return GameObject containing all tilemap layers and child objects
     */
    public GameObject generate() {
        GameObject levelObj = new GameObject("VillageLevel", new Vector3f(0, 0, 0));

        // Create tilemap layers
        groundLayer = levelObj.addComponent(new TilemapRenderer());
        groundLayer.setZIndex(-2);

        roadLayer = levelObj.addComponent(new TilemapRenderer());
        roadLayer.setZIndex(-1);

        propsLayer = levelObj.addComponent(new TilemapRenderer());
        propsLayer.setZIndex(0);

        fenceLayer = levelObj.addComponent(new TilemapRenderer());
        fenceLayer.setZIndex(1);

        // Generate in order
        generateGrassBase();
        generateWaterPond(160, 160, 18, 12);
        generateWaterPond(25, 175, 10, 8);
        generateRoadNetwork();
        generateHouses(levelObj);
        // generateFencedYards();
        // generateForestBorder(levelObj);
        // generateScatteredTrees(levelObj);
        // generateVillageCenter();
        // generateScatteredProps();

        return levelObj;
    }

    // ========================================================================
    // GRASS BASE
    // ========================================================================

    private void generateGrassBase() {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                Sprite grass = pickGrassSprite(x, y);
                groundLayer.set(x, y, new Tile("grass", grass));
            }
        }
    }

    private Sprite pickGrassSprite(int x, int y) {
        int noise = (x * 7 + y * 13 + x * y) % 100;
        if (noise < 65) {
            return safeGet(outdoorSprites, GRASS_LIGHT);
        } else if (noise < 80) {
            return safeGet(outdoorSprites, GRASS_DARK);
        } else if (noise < 92) {
            return safeGet(outdoorSprites, GRASS_PATCH_1);
        } else {
            return safeGet(outdoorSprites, GRASS_PATCH_2);
        }
    }

    // ========================================================================
    // WATER
    // ========================================================================

    private void generateWaterPond(int centerX, int centerY, int width, int height) {
        int halfW = width / 2;
        int halfH = height / 2;

        int left = centerX - halfW;
        int right = centerX + halfW;
        int bottom = centerY - halfH;
        int top = centerY + halfH;

        for (int x = left; x <= right; x++) {
            for (int y = bottom; y <= top; y++) {
                if (!inBounds(x, y)) continue;

                boolean isLeft = (x == left);
                boolean isRight = (x == right);
                boolean isTop = (y == top);
                boolean isBottom = (y == bottom);

                Sprite waterSprite;
                if (isTop && isLeft) {
                    waterSprite = safeGet(waterSprites, WATER_CORNER_TL);
                } else if (isTop && isRight) {
                    waterSprite = safeGet(waterSprites, WATER_CORNER_TR);
                } else if (isBottom && isLeft) {
                    waterSprite = safeGet(waterSprites, WATER_CORNER_BL);
                } else if (isBottom && isRight) {
                    waterSprite = safeGet(waterSprites, WATER_CORNER_BR);
                } else if (isTop) {
                    waterSprite = safeGet(waterSprites, WATER_EDGE_TOP);
                } else if (isBottom) {
                    waterSprite = safeGet(waterSprites, WATER_EDGE_BOTTOM);
                } else if (isLeft) {
                    waterSprite = safeGet(waterSprites, WATER_EDGE_LEFT);
                } else if (isRight) {
                    waterSprite = safeGet(waterSprites, WATER_EDGE_RIGHT);
                } else {
                    waterSprite = safeGet(waterSprites, WATER_CENTER);
                }

                groundLayer.set(x, y, Tile.solid("water", waterSprite));
                markSolid(x, y);
            }
        }
    }

    // ========================================================================
    // ROADS
    // ========================================================================

    private void generateRoadNetwork() {
        // Main horizontal roads
        generateHorizontalRoad(15, 50, 170);
        generateHorizontalRoad(15, 100, 170);
        generateHorizontalRoad(15, 150, 170);

        // Main vertical roads
        generateVerticalRoad(50, 20, 160);
        generateVerticalRoad(100, 20, 160);
        generateVerticalRoad(150, 20, 160);

        // Village plaza at center
        for (int x = 95; x <= 105; x++) {
            for (int y = 95; y <= 105; y++) {
                roadLayer.set(x, y, new Tile("plaza", safeGet(roadSprites, ROAD_CENTER)));
            }
        }
    }

    private void generateHorizontalRoad(int startX, int y, int length) {
        for (int x = startX; x < startX + length && x < WIDTH; x++) {
            if (inBounds(x, y)) {
                roadLayer.set(x, y, new Tile("road", safeGet(roadSprites, ROAD_H)));
            }
        }
    }

    private void generateVerticalRoad(int x, int startY, int length) {
        for (int y = startY; y < startY + length && y < HEIGHT; y++) {
            if (inBounds(x, y)) {
                roadLayer.set(x, y, new Tile("road", safeGet(roadSprites, ROAD_V)));
            }
        }
    }

    // ========================================================================
    // HOUSES (as SpriteRenderer GameObjects)
    // ========================================================================

    private void generateHouses(GameObject parent) {
        // House positions - carefully placed to avoid roads
        int[][] positions = {
                // West district
                {25, 60}, {35, 60}, {25, 75}, {35, 75},
                {25, 110}, {35, 110}, {25, 125}, {35, 125},
                {25, 160}, {35, 160},
                // Central-west
                {60, 60}, {70, 60}, {60, 75}, {70, 75},
                {60, 110}, {70, 110}, {60, 125}, {70, 125},
                {60, 160}, {70, 160},
                // Central-east
                {115, 60}, {125, 60}, {115, 75}, {125, 75},
                {115, 110}, {125, 110}, {115, 125}, {125, 125},
                {115, 160}, {125, 160},
                // East district
                {160, 60}, {170, 60}, {160, 75}, {170, 75},
                {160, 110}, {170, 110}, {160, 125}, {170, 125},
        };

        for (int[] pos : positions) {
            placeHouse(parent, pos[0], pos[1]);
        }
    }

    private void placeHouse(GameObject parent, int tileX, int tileY) {
        if (houseSprites.isEmpty()) return;

        // House is 64x96 pixels = 4x6 tiles
        // Position is bottom-left of house
        GameObject house = new GameObject("House_" + tileX + "_" + tileY,
                new Vector3f(tileX, tileY, 0));

        SpriteRenderer renderer = new SpriteRenderer(houseSprites.get(0));
        renderer.setZIndex(2);
        renderer.setOriginBottomLeft();
        house.addComponent(renderer);

        house.setParent(parent);
        houseObjects.add(house);

        // Mark 4x6 area as solid
        for (int dx = 0; dx < 4; dx++) {
            for (int dy = 0; dy < 6; dy++) {
                markSolid(tileX + dx, tileY + dy);
            }
        }
    }

    // ========================================================================
    // FENCES (in tilemap, around house yards)
    // ========================================================================

    private void generateFencedYards() {
        // Place fences around groups of houses
        // West yards
        placeFenceRect(22, 57, 20, 25);   // Lower west
        placeFenceRect(22, 107, 20, 25);  // Upper west

        // Central-west yards
        placeFenceRect(57, 57, 20, 25);
        placeFenceRect(57, 107, 20, 25);

        // Central-east yards
        placeFenceRect(112, 57, 20, 25);
        placeFenceRect(112, 107, 20, 25);

        // East yards
        placeFenceRect(157, 57, 20, 25);
        placeFenceRect(157, 107, 20, 25);
    }

    private void placeFenceRect(int left, int bottom, int width, int height) {
        int right = left + width - 1;
        int top = bottom + height - 1;

        // Bottom edge (with gate in middle)
        int gateStart = left + width / 2 - 1;
        int gateEnd = gateStart + 2;
        for (int x = left; x <= right; x++) {
            if (x >= gateStart && x <= gateEnd) continue; // Gate opening
            if (inBounds(x, bottom) && !isSolidAt(x, bottom)) {
                fenceLayer.set(x, bottom, Tile.solid("fence", safeGet(fenceSprites, FENCE_H)));
                markSolid(x, bottom);
            }
        }

        // Top edge
        for (int x = left; x <= right; x++) {
            if (inBounds(x, top) && !isSolidAt(x, top)) {
                fenceLayer.set(x, top, Tile.solid("fence", safeGet(fenceSprites, FENCE_H)));
                markSolid(x, top);
            }
        }

        // Left edge
        for (int y = bottom + 1; y < top; y++) {
            if (inBounds(left, y) && !isSolidAt(left, y)) {
                fenceLayer.set(left, y, Tile.solid("fence", safeGet(fenceSprites, FENCE_V)));
                markSolid(left, y);
            }
        }

        // Right edge
        for (int y = bottom + 1; y < top; y++) {
            if (inBounds(right, y) && !isSolidAt(right, y)) {
                fenceLayer.set(right, y, Tile.solid("fence", safeGet(fenceSprites, FENCE_V)));
                markSolid(right, y);
            }
        }

        // Corners
        placeFenceTile(left, bottom, FENCE_CORNER_BL);
        placeFenceTile(right, bottom, FENCE_CORNER_BR);
        placeFenceTile(left, top, FENCE_CORNER_TL);
        placeFenceTile(right, top, FENCE_CORNER_TR);
    }

    private void placeFenceTile(int x, int y, int spriteIndex) {
        if (inBounds(x, y) && !isSolidAt(x, y)) {
            fenceLayer.set(x, y, Tile.solid("fence", safeGet(fenceSprites, spriteIndex)));
            markSolid(x, y);
        }
    }

    // ========================================================================
    // TREES (as SpriteRenderer GameObjects, 32x48 = 2x3 tiles)
    // ========================================================================

    private void generateForestBorder(GameObject parent) {
        int forestDepth = 12;

        // North forest
        fillForestArea(parent, 0, HEIGHT - forestDepth, WIDTH, forestDepth, 0.18f);
        // South forest
        fillForestArea(parent, 0, 0, WIDTH, forestDepth, 0.18f);
        // West forest
        fillForestArea(parent, 0, forestDepth, forestDepth, HEIGHT - 2 * forestDepth, 0.18f);
        // East forest
        fillForestArea(parent, WIDTH - forestDepth, forestDepth, forestDepth, HEIGHT - 2 * forestDepth, 0.18f);
    }

    private void generateScatteredTrees(GameObject parent) {
        // Sparse trees inside village (avoiding roads and buildings)
        fillForestArea(parent, 15, 15, WIDTH - 30, HEIGHT - 30, 0.004f);
    }

    private void fillForestArea(GameObject parent, int startX, int startY, int width, int height, float density) {
        // Step by 2 in X since trees are 2 tiles wide (reduces overlap checks)
        for (int x = startX; x < startX + width; x += 2) {
            for (int y = startY; y < startY + height; y++) {
                if (!canPlaceTree(x, y)) continue;

                if (random.nextFloat() < density) {
                    placeTree(parent, x, y);
                }
            }
        }
    }

    private boolean canPlaceTree(int x, int y) {
        // Trees are 2 tiles wide × 3 tiles tall
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                int checkX = x + dx;
                int checkY = y + dy;
                if (!inBounds(checkX, checkY)) return false;
                if (isSolidAt(checkX, checkY)) return false;
                if (isRoadAt(checkX, checkY)) return false;
            }
        }
        return true;
    }

    private void placeTree(GameObject parent, int tileX, int tileY) {
        if (treeSprites.isEmpty()) return;

        // Pick tree variant (indices 0, 2, 4, 6, 8 are full trees in each row)
        int[] treeIndices = {TREE_GREEN, TREE_DARK, TREE_AUTUMN, TREE_PINK, TREE_DEAD};
        int treeIndex = treeIndices[random.nextInt(treeIndices.length)];

        GameObject tree = new GameObject("Tree_" + tileX + "_" + tileY,
                new Vector3f(tileX, tileY, 0));

        SpriteRenderer renderer = new SpriteRenderer(safeGet(treeSprites, treeIndex));
        renderer.setZIndex(2);
        renderer.setOriginBottomLeft();
        tree.addComponent(renderer);

        tree.setParent(parent);
        treeObjects.add(tree);

        // Mark bottom row (2 tiles wide × 1 tile tall) as solid - trunk area
        // Upper 2 rows are walkable (player can walk behind foliage)
        markSolid(tileX, tileY);
        markSolid(tileX + 1, tileY);
    }

    // ========================================================================
    // VILLAGE CENTER
    // ========================================================================

    private void generateVillageCenter() {
        int cx = 100;
        int cy = 100;

        // Decorative flowers around plaza
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                if (Math.abs(dx) == 3 || Math.abs(dy) == 3) {
                    int x = cx + dx;
                    int y = cy + dy;
                    if (inBounds(x, y) && random.nextFloat() < 0.6f) {
                        propsLayer.set(x, y, new Tile("flower", pickFlowerSprite()));
                    }
                }
            }
        }

        // Central monument (rocks)
        propsLayer.set(cx, cy, new Tile("monument", safeGet(outdoorSprites, BLOCK_STONE)));
        markSolid(cx, cy);
    }

    // ========================================================================
    // SCATTERED PROPS
    // ========================================================================

    private void generateScatteredProps() {
        for (int x = 15; x < WIDTH - 15; x++) {
            for (int y = 15; y < HEIGHT - 15; y++) {
                if (isSolidAt(x, y)) continue;
                if (isRoadAt(x, y)) continue;
                if (propsLayer.get(x, y) != null) continue;

                float roll = random.nextFloat();
                if (roll < 0.015f) {
                    propsLayer.set(x, y, new Tile("flower", pickFlowerSprite()));
                } else if (roll < 0.02f) {
                    propsLayer.set(x, y, new Tile("rock", safeGet(outdoorSprites, SMALL_ROCK)));
                }
            }
        }
    }

    private Sprite pickFlowerSprite() {
        int[] flowers = {FLOWERS_RED, FLOWERS_YELLOW, FLOWERS_WHITE, FLOWERS_PINK};
        return safeGet(outdoorSprites, flowers[random.nextInt(flowers.length)]);
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }

    private void markSolid(int x, int y) {
        if (inBounds(x, y)) {
            solidMap[x][y] = true;
        }
    }

    private boolean isSolidAt(int x, int y) {
        if (!inBounds(x, y)) return true;
        return solidMap[x][y];
    }

    private boolean isRoadAt(int x, int y) {
        return roadLayer.get(x, y) != null;
    }

    private <T> T safeGet(List<T> list, int index) {
        if (list == null || list.isEmpty()) return null;
        if (index < 0 || index >= list.size()) return list.get(0);
        return list.get(index);
    }

    // ========================================================================
    // PUBLIC ACCESSORS
    // ========================================================================

    public TilemapRenderer getGroundLayer() { return groundLayer; }
    public TilemapRenderer getRoadLayer() { return roadLayer; }
    public TilemapRenderer getPropsLayer() { return propsLayer; }
    public TilemapRenderer getFenceLayer() { return fenceLayer; }
    public List<GameObject> getTreeObjects() { return treeObjects; }
    public List<GameObject> getHouseObjects() { return houseObjects; }

    /**
     * Checks if a tile position blocks movement.
     */
    public boolean isSolid(int tileX, int tileY) {
        return isSolidAt(tileX, tileY);
    }

    /**
     * Gets the player spawn position (center of village).
     */
    public Vector3f getPlayerSpawnPosition() {
        return new Vector3f(100, 100, 0);
    }
}