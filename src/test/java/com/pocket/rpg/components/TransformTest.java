package com.pocket.rpg.components;

import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransformTest {

    private Transform transform;

    @BeforeEach
    void setUp() {
        transform = new Transform();
    }

    @Test
    void testDefaultValues() {
        assertEquals(0, transform.getPosition().x);
        assertEquals(0, transform.getPosition().y);
        assertEquals(0, transform.getPosition().z);

        assertEquals(0, transform.getRotation().x);
        assertEquals(0, transform.getRotation().y);
        assertEquals(0, transform.getRotation().z);

        assertEquals(1, transform.getScale().x);
        assertEquals(1, transform.getScale().y);
        assertEquals(1, transform.getScale().z);
    }

    @Test
    void testSetPosition() {
        transform.setPosition(5, 10, 15);

        assertEquals(5, transform.getPosition().x);
        assertEquals(10, transform.getPosition().y);
        assertEquals(15, transform.getPosition().z);
    }

    @Test
    void testTranslate() {
        transform.setPosition(10, 10, 10);
        transform.translate(5, -3, 2);

        assertEquals(15, transform.getPosition().x);
        assertEquals(7, transform.getPosition().y);
        assertEquals(12, transform.getPosition().z);
    }

    @Test
    void testSetRotation() {
        transform.setRotation(45, 90, 180);

        assertEquals(45, transform.getRotation().x);
        assertEquals(90, transform.getRotation().y);
        assertEquals(180, transform.getRotation().z);
    }

    @Test
    void testRotate() {
        transform.setRotation(10, 20, 30);
        transform.rotate(5, 10, 15);

        assertEquals(15, transform.getRotation().x);
        assertEquals(30, transform.getRotation().y);
        assertEquals(45, transform.getRotation().z);
    }

    @Test
    void testSetScale() {
        transform.setScale(2, 3, 4);

        assertEquals(2, transform.getScale().x);
        assertEquals(3, transform.getScale().y);
        assertEquals(4, transform.getScale().z);
    }

    @Test
    void testSetScaleUniform() {
        transform.setScale(2.5f);

        assertEquals(2.5f, transform.getScale().x);
        assertEquals(2.5f, transform.getScale().y);
        assertEquals(2.5f, transform.getScale().z);
    }
}
