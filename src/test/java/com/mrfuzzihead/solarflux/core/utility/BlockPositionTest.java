package com.mrfuzzihead.solarflux.core.utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Test;

public class BlockPositionTest {

    @Test
    public void coordinatesAreReadBack() {
        BlockPosition position = new BlockPosition(1, -2, 3);

        assertEquals(1, position.x());
        assertEquals(-2, position.y());
        assertEquals(3, position.z());
        assertNull(position.getDirection());
    }

    @Test
    public void moveUsesTheGivenDirectionAndIgnoresTheStoredOne() {
        BlockPosition position = new BlockPosition(5, 5, 5, ForgeDirection.NORTH);

        BlockPosition moved = position.move(ForgeDirection.UP);

        assertEquals(5, moved.x());
        assertEquals(6, moved.y());
        assertEquals(5, moved.z());
        assertEquals(ForgeDirection.UP, moved.getDirection());
    }

    @Test
    public void movingIsRelativeToTheCurrentCoordinates() {
        BlockPosition position = new BlockPosition(10, 20, 30);

        assertEquals(
            11,
            position.move(ForgeDirection.EAST)
                .x());
        assertEquals(
            9,
            position.move(ForgeDirection.WEST)
                .x());
        assertEquals(
            31,
            position.move(ForgeDirection.SOUTH)
                .z());
        assertEquals(
            19,
            position.move(ForgeDirection.DOWN)
                .y());
    }

    @Test
    public void directionIsNotPartOfEqualityOrHashing() {
        BlockPosition withoutDirection = new BlockPosition(1, 2, 3);
        BlockPosition north = new BlockPosition(1, 2, 3, ForgeDirection.NORTH);
        BlockPosition south = new BlockPosition(1, 2, 3, ForgeDirection.SOUTH);

        assertEquals(withoutDirection, north);
        assertEquals(north, south);
        assertEquals(withoutDirection.hashCode(), south.hashCode());
    }

    @Test
    public void positionsWithDifferentCoordinatesAreDifferent() {
        BlockPosition origin = new BlockPosition(0, 0, 0);

        assertNotEquals(origin, new BlockPosition(1, 0, 0));
        assertNotEquals(origin, new BlockPosition(0, 1, 0));
        assertNotEquals(origin, new BlockPosition(0, 0, 1));
        assertNotEquals(origin, null);
        assertNotEquals(origin, "[0,0,0]");
    }

    @Test
    public void equalsIsReflexive() {
        BlockPosition position = new BlockPosition(7, 8, 9);

        assertTrue(position.equals(position));
        assertEquals(position, new BlockPosition(7, 8, 9));
    }

    @Test
    public void toStringRendersTheCoordinatesOnly() {
        assertEquals("[4,-5,6]", new BlockPosition(4, -5, 6, ForgeDirection.UP).toString());
    }
}
