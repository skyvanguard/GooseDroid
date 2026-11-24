package com.skyvanguard.goozy;

import com.skyvanguard.goozy.core.Vec2;

import org.junit.Test;

import static org.junit.Assert.*;

public class Vec2Test {

    private static final float EPSILON = 0.001f;

    // ============== CONSTRUCTOR ==============

    @Test
    public void constructor_setsXY() {
        Vec2 v = new Vec2(3f, 4f);
        assertEquals(3f, v.x, EPSILON);
        assertEquals(4f, v.y, EPSILON);
    }

    @Test
    public void zero_isOrigin() {
        assertEquals(0f, Vec2.ZERO.x, EPSILON);
        assertEquals(0f, Vec2.ZERO.y, EPSILON);
    }

    // ============== ADDITION ==============

    @Test
    public void add_sumsComponents() {
        Vec2 a = new Vec2(1f, 2f);
        Vec2 b = new Vec2(3f, 4f);
        Vec2 result = Vec2.add(a, b);
        assertEquals(4f, result.x, EPSILON);
        assertEquals(6f, result.y, EPSILON);
    }

    @Test
    public void add_withZero() {
        Vec2 a = new Vec2(5f, 7f);
        Vec2 result = Vec2.add(a, Vec2.ZERO);
        assertEquals(5f, result.x, EPSILON);
        assertEquals(7f, result.y, EPSILON);
    }

    @Test
    public void add_withNegative() {
        Vec2 a = new Vec2(5f, 3f);
        Vec2 b = new Vec2(-2f, -1f);
        Vec2 result = Vec2.add(a, b);
        assertEquals(3f, result.x, EPSILON);
        assertEquals(2f, result.y, EPSILON);
    }

    // ============== SUBTRACTION ==============

    @Test
    public void subtract_diffComponents() {
        Vec2 a = new Vec2(5f, 7f);
        Vec2 b = new Vec2(2f, 3f);
        Vec2 result = Vec2.sub(a, b);
        assertEquals(3f, result.x, EPSILON);
        assertEquals(4f, result.y, EPSILON);
    }

    @Test
    public void subtract_fromSelf_isZero() {
        Vec2 a = new Vec2(5f, 7f);
        Vec2 result = Vec2.sub(a, a);
        assertEquals(0f, result.x, EPSILON);
        assertEquals(0f, result.y, EPSILON);
    }

    // ============== NEGATION ==============

    @Test
    public void negate_flipsSign() {
        Vec2 a = new Vec2(3f, -4f);
        Vec2 result = Vec2.neg(a);
        assertEquals(-3f, result.x, EPSILON);
        assertEquals(4f, result.y, EPSILON);
    }

    // ============== MULTIPLICATION ==============

    @Test
    public void multiply_vectorByVector() {
        Vec2 a = new Vec2(2f, 3f);
        Vec2 b = new Vec2(4f, 5f);
        Vec2 result = Vec2.mul(a, b);
        assertEquals(8f, result.x, EPSILON);
        assertEquals(15f, result.y, EPSILON);
    }

    @Test
    public void multiply_vectorByScalar() {
        Vec2 a = new Vec2(3f, 4f);
        Vec2 result = Vec2.mul(a, 2f);
        assertEquals(6f, result.x, EPSILON);
        assertEquals(8f, result.y, EPSILON);
    }

    @Test
    public void multiply_byZero() {
        Vec2 a = new Vec2(3f, 4f);
        Vec2 result = Vec2.mul(a, 0f);
        assertEquals(0f, result.x, EPSILON);
        assertEquals(0f, result.y, EPSILON);
    }

    @Test
    public void multiply_byNegative() {
        Vec2 a = new Vec2(3f, 4f);
        Vec2 result = Vec2.mul(a, -1f);
        assertEquals(-3f, result.x, EPSILON);
        assertEquals(-4f, result.y, EPSILON);
    }

    // ============== DIVISION ==============

    @Test
    public void divide_byScalar() {
        Vec2 a = new Vec2(6f, 8f);
        Vec2 result = Vec2.div(a, 2f);
        assertEquals(3f, result.x, EPSILON);
        assertEquals(4f, result.y, EPSILON);
    }

    // ============== DISTANCE ==============

    @Test
    public void distance_between_345_triangle() {
        Vec2 a = new Vec2(0f, 0f);
        Vec2 b = new Vec2(3f, 4f);
        assertEquals(5f, Vec2.dist(a, b), EPSILON);
    }

    @Test
    public void distance_toSelf_isZero() {
        Vec2 a = new Vec2(5f, 3f);
        assertEquals(0f, Vec2.dist(a, a), EPSILON);
    }

    @Test
    public void distance_isCommutative() {
        Vec2 a = new Vec2(1f, 2f);
        Vec2 b = new Vec2(4f, 6f);
        assertEquals(Vec2.dist(a, b), Vec2.dist(b, a), EPSILON);
    }

    // ============== MAGNITUDE ==============

    @Test
    public void magnitude_345() {
        Vec2 v = new Vec2(3f, 4f);
        assertEquals(5f, Vec2.length(v), EPSILON);
    }

    @Test
    public void magnitude_zero() {
        assertEquals(0f, Vec2.length(Vec2.ZERO), EPSILON);
    }

    @Test
    public void magnitude_unitX() {
        Vec2 v = new Vec2(1f, 0f);
        assertEquals(1f, Vec2.length(v), EPSILON);
    }

    // ============== NORMALIZE ==============

    @Test
    public void normalize_unitLength() {
        Vec2 v = new Vec2(3f, 4f);
        Vec2 normalized = Vec2.normalize(v);
        assertEquals(1f, Vec2.length(normalized), EPSILON);
    }

    @Test
    public void normalize_preservesDirection() {
        Vec2 v = new Vec2(3f, 4f);
        Vec2 normalized = Vec2.normalize(v);
        assertEquals(0.6f, normalized.x, EPSILON);
        assertEquals(0.8f, normalized.y, EPSILON);
    }

    @Test
    public void normalize_zeroVector_returnsZero() {
        Vec2 result = Vec2.normalize(new Vec2(0f, 0f));
        assertEquals(0f, result.x, EPSILON);
        assertEquals(0f, result.y, EPSILON);
    }

    // ============== DOT PRODUCT ==============

    @Test
    public void dot_perpendicular_isZero() {
        Vec2 a = new Vec2(1f, 0f);
        Vec2 b = new Vec2(0f, 1f);
        assertEquals(0f, Vec2.dot(a, b), EPSILON);
    }

    @Test
    public void dot_parallel_isProduct() {
        Vec2 a = new Vec2(2f, 0f);
        Vec2 b = new Vec2(3f, 0f);
        assertEquals(6f, Vec2.dot(a, b), EPSILON);
    }

    @Test
    public void dot_opposite_isNegative() {
        Vec2 a = new Vec2(1f, 0f);
        Vec2 b = new Vec2(-1f, 0f);
        assertTrue(Vec2.dot(a, b) < 0);
    }

    @Test
    public void dot_withSelf_isMagnitudeSquared() {
        Vec2 v = new Vec2(3f, 4f);
        float mag = Vec2.length(v);
        assertEquals(mag * mag, Vec2.dot(v, v), EPSILON);
    }

    // ============== LERP ==============

    @Test
    public void lerp_atZero_returnsA() {
        Vec2 a = new Vec2(0f, 0f);
        Vec2 b = new Vec2(10f, 20f);
        Vec2 result = Vec2.lerp(a, b, 0f);
        assertEquals(0f, result.x, EPSILON);
        assertEquals(0f, result.y, EPSILON);
    }

    @Test
    public void lerp_atOne_returnsB() {
        Vec2 a = new Vec2(0f, 0f);
        Vec2 b = new Vec2(10f, 20f);
        Vec2 result = Vec2.lerp(a, b, 1f);
        assertEquals(10f, result.x, EPSILON);
        assertEquals(20f, result.y, EPSILON);
    }

    @Test
    public void lerp_atHalf_returnsMidpoint() {
        Vec2 a = new Vec2(0f, 0f);
        Vec2 b = new Vec2(10f, 20f);
        Vec2 result = Vec2.lerp(a, b, 0.5f);
        assertEquals(5f, result.x, EPSILON);
        assertEquals(10f, result.y, EPSILON);
    }

    // ============== ANGLE ==============

    @Test
    public void getFromAngleDegrees_0_isRight() {
        Vec2 v = Vec2.fromDegrees(0f);
        assertEquals(1f, v.x, EPSILON);
        assertEquals(0f, v.y, EPSILON);
    }

    @Test
    public void getFromAngleDegrees_90_isUp() {
        Vec2 v = Vec2.fromDegrees(90f);
        assertEquals(0f, v.x, EPSILON);
        assertEquals(1f, v.y, EPSILON);
    }

    @Test
    public void getFromAngleDegrees_180_isLeft() {
        Vec2 v = Vec2.fromDegrees(180f);
        assertEquals(-1f, v.x, EPSILON);
        assertEquals(0f, v.y, 0.01f);
    }

    @Test
    public void getFromAngleDegrees_resultIsUnitLength() {
        Vec2 v = Vec2.fromDegrees(37f);
        assertEquals(1f, Vec2.length(v), EPSILON);
    }

    // ============== IMMUTABILITY ==============

    @Test
    public void operations_dontModifyOriginals() {
        Vec2 a = new Vec2(1f, 2f);
        Vec2 b = new Vec2(3f, 4f);

        Vec2.add(a, b);
        assertEquals(1f, a.x, EPSILON);
        assertEquals(2f, a.y, EPSILON);
        assertEquals(3f, b.x, EPSILON);
        assertEquals(4f, b.y, EPSILON);

        Vec2.sub(a, b);
        assertEquals(1f, a.x, EPSILON);

        Vec2.mul(a, 5f);
        assertEquals(1f, a.x, EPSILON);

        Vec2.normalize(a);
        assertEquals(1f, a.x, EPSILON);
    }
}
