package com.pocket.rpg.serialization;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ComponentReflectionUtilsTest {

    // ========================================================================
    // VECTOR TYPES (regression)
    // ========================================================================

    @Nested
    class VectorDeepCopyTests {

        @Test
        void vector2f_isCopied() {
            var original = new Vector2f(1.0f, 2.0f);
            var copy = (Vector2f) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original, copy);
            assertEquals(original, copy);

            copy.x = 99f;
            assertEquals(1.0f, original.x);
        }

        @Test
        void vector3f_isCopied() {
            var original = new Vector3f(1.0f, 2.0f, 3.0f);
            var copy = (Vector3f) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original, copy);
            assertEquals(original, copy);

            copy.y = 99f;
            assertEquals(2.0f, original.y);
        }

        @Test
        void vector4f_isCopied() {
            var original = new Vector4f(1.0f, 2.0f, 3.0f, 4.0f);
            var copy = (Vector4f) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original, copy);
            assertEquals(original, copy);

            copy.z = 99f;
            assertEquals(3.0f, original.z);
        }
    }

    // ========================================================================
    // IMMUTABLE PASSTHROUGH (regression)
    // ========================================================================

    @Nested
    class ImmutablePassthroughTests {

        @Test
        void string_returnsSameInstance() {
            String original = "hello";
            assertSame(original, ComponentReflectionUtils.deepCopyValue(original));
        }

        @Test
        void integer_returnsSameInstance() {
            Integer original = 42;
            assertSame(original, ComponentReflectionUtils.deepCopyValue(original));
        }

        @Test
        void enum_returnsSameInstance() {
            var original = Thread.State.RUNNABLE;
            assertSame(original, ComponentReflectionUtils.deepCopyValue(original));
        }

        @Test
        void null_returnsNull() {
            assertNull(ComponentReflectionUtils.deepCopyValue(null));
        }
    }

    // ========================================================================
    // LIST
    // ========================================================================

    @Nested
    class ListDeepCopyTests {

        @Test
        void listOfStrings_isIsolated() {
            var original = new ArrayList<>(List.of("a", "b", "c"));

            @SuppressWarnings("unchecked")
            var copy = (List<String>) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original, copy);
            assertEquals(original, copy);

            copy.add("d");
            assertEquals(3, original.size());
        }

        @Test
        void listOfVectors_elementsAreCopied() {
            var v1 = new Vector2f(1, 2);
            var v2 = new Vector2f(3, 4);
            var original = new ArrayList<>(List.of(v1, v2));

            @SuppressWarnings("unchecked")
            var copy = (List<Vector2f>) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original, copy);
            assertNotSame(original.get(0), copy.get(0));
            assertEquals(v1, copy.get(0));

            copy.get(0).x = 99f;
            assertEquals(1f, original.get(0).x);
        }

        @Test
        void nestedList_innerListIsIsolated() {
            var inner = new ArrayList<>(List.of("x", "y"));
            var original = new ArrayList<>(List.of(inner));

            @SuppressWarnings("unchecked")
            var copy = (List<List<String>>) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original.get(0), copy.get(0));

            copy.get(0).add("z");
            assertEquals(2, original.get(0).size());
        }

        @Test
        void emptyList_isCopied() {
            var original = new ArrayList<>();

            @SuppressWarnings("unchecked")
            var copy = (List<Object>) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original, copy);
            assertTrue(copy.isEmpty());
        }

        @Test
        void listWithNullElement_handledGracefully() {
            var original = new ArrayList<>();
            original.add("a");
            original.add(null);
            original.add("c");

            @SuppressWarnings("unchecked")
            var copy = (List<Object>) ComponentReflectionUtils.deepCopyValue(original);

            assertEquals(3, copy.size());
            assertEquals("a", copy.get(0));
            assertNull(copy.get(1));
            assertEquals("c", copy.get(2));
        }
    }

    // ========================================================================
    // MAP
    // ========================================================================

    @Nested
    class MapDeepCopyTests {

        @Test
        void mapOfStringToObject_isIsolated() {
            var original = new LinkedHashMap<String, Object>();
            original.put("name", "test");
            original.put("count", 42);

            @SuppressWarnings("unchecked")
            var copy = (Map<String, Object>) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original, copy);
            assertEquals(original, copy);

            copy.put("extra", true);
            assertFalse(original.containsKey("extra"));
        }

        @Test
        void mapWithMutableValues_valuesAreCopied() {
            var vec = new Vector2f(1, 2);
            var original = new LinkedHashMap<String, Vector2f>();
            original.put("pos", vec);

            @SuppressWarnings("unchecked")
            var copy = (Map<String, Vector2f>) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original.get("pos"), copy.get("pos"));
            assertEquals(vec, copy.get("pos"));

            copy.get("pos").x = 99f;
            assertEquals(1f, original.get("pos").x);
        }

        @Test
        void emptyMap_isCopied() {
            var original = new LinkedHashMap<>();

            @SuppressWarnings("unchecked")
            var copy = (Map<Object, Object>) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original, copy);
            assertTrue(copy.isEmpty());
        }
    }

    // ========================================================================
    // ARRAYS
    // ========================================================================

    @Nested
    class ArrayDeepCopyTests {

        @Test
        void intArray_isIsolated() {
            int[] original = {1, 2, 3};
            int[] copy = (int[]) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original, copy);
            assertArrayEquals(original, copy);

            copy[0] = 99;
            assertEquals(1, original[0]);
        }

        @Test
        void floatArray_isIsolated() {
            float[] original = {1.5f, 2.5f};
            float[] copy = (float[]) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original, copy);
            assertArrayEquals(original, copy);

            copy[0] = 99f;
            assertEquals(1.5f, original[0]);
        }

        @Test
        void vectorArray_elementsAreCopied() {
            Vector2f[] original = {new Vector2f(1, 2), new Vector2f(3, 4)};
            Vector2f[] copy = (Vector2f[]) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original, copy);
            assertNotSame(original[0], copy[0]);
            assertEquals(original[0], copy[0]);

            copy[0].x = 99f;
            assertEquals(1f, original[0].x);
        }

        @Test
        void stringArray_isIsolated() {
            String[] original = {"a", "b"};
            String[] copy = (String[]) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original, copy);
            assertArrayEquals(original, copy);

            copy[0] = "z";
            assertEquals("a", original[0]);
        }

        @Test
        void emptyArray_isCopied() {
            int[] original = {};
            int[] copy = (int[]) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original, copy);
            assertEquals(0, copy.length);
        }

        @Test
        void arrayWithNullElements_handledGracefully() {
            String[] original = {"a", null, "c"};
            String[] copy = (String[]) ComponentReflectionUtils.deepCopyValue(original);

            assertEquals(3, copy.length);
            assertEquals("a", copy[0]);
            assertNull(copy[1]);
            assertEquals("c", copy[2]);
        }
    }

    // ========================================================================
    // NESTED / COMPLEX STRUCTURES
    // ========================================================================

    @Nested
    class NestedStructureTests {

        @Test
        void listOfMaps_fullyIsolated() {
            var map1 = new LinkedHashMap<String, Object>();
            map1.put("x", new Vector2f(1, 2));
            var original = new ArrayList<>(List.of(map1));

            @SuppressWarnings("unchecked")
            var copy = (List<Map<String, Object>>) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original.get(0), copy.get(0));
            assertNotSame(original.get(0).get("x"), copy.get(0).get("x"));

            ((Vector2f) copy.get(0).get("x")).x = 99f;
            assertEquals(1f, ((Vector2f) original.get(0).get("x")).x);
        }

        @Test
        void mapOfLists_fullyIsolated() {
            var list = new ArrayList<>(List.of("a", "b"));
            var original = new LinkedHashMap<String, List<String>>();
            original.put("items", list);

            @SuppressWarnings("unchecked")
            var copy = (Map<String, List<String>>) ComponentReflectionUtils.deepCopyValue(original);

            assertNotSame(original.get("items"), copy.get("items"));

            copy.get("items").add("c");
            assertEquals(2, original.get("items").size());
        }
    }
}
