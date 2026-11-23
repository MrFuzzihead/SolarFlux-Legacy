package com.mrfuzzihead.solarflux.support;

import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;

import com.mrfuzzihead.solarflux.blocks.SolarPanelTileEntity;

/**
 * A mocked {@link World} with the handful of methods a panel reads, plus a helper that picks a world time where the
 * panel's random tick shift lines up. The sun tests read like a table instead of 20 lines of stubbing each.
 */
public final class TestWorld {

    private final World mWorld;

    private TestWorld(World pWorld) {
        mWorld = pWorld;
    }

    public static TestWorld create() {
        World world = mock(World.class);
        // EntityItem (used by the dismantle tests) reads the provider in its constructor.
        TestFixture.setInstanceField(world, World.class, "provider", mock(WorldProvider.class));
        world.isRemote = false;
        return new TestWorld(world).sky(false)
            .celestialAngle(0.0F)
            .weather(false, false)
            .totalTime(0L);
    }

    public World world() {
        return mWorld;
    }

    public TestWorld sky(boolean pCanSeeSky) {
        when(mWorld.canBlockSeeTheSky(anyInt(), anyInt(), anyInt())).thenReturn(pCanSeeSky);
        return this;
    }

    public TestWorld celestialAngle(float pRadians) {
        when(mWorld.getCelestialAngleRadians(anyFloat())).thenReturn(pRadians);
        return this;
    }

    public TestWorld weather(boolean pRaining, boolean pThundering) {
        when(mWorld.isRaining()).thenReturn(pRaining);
        when(mWorld.isThundering()).thenReturn(pThundering);
        return this;
    }

    public TestWorld totalTime(long pTotalTime) {
        when(mWorld.getTotalWorldTime()).thenReturn(pTotalTime);
        return this;
    }

    /** Picks a total time at which {@code atTickRate(pRate)} is true for this panel, without waiting for the shift. */
    public TestWorld alignedTo(SolarPanelTileEntity pPanel, int pRate) {
        int shift = ((Short) TestFixture.getField(pPanel, "mTickShift")).intValue();
        int offset = ((shift % pRate) + pRate) % pRate;
        return totalTime((pRate - offset) % pRate);
    }

    public TestWorld attach(SolarPanelTileEntity pPanel) {
        TestFixture.setWorld(pPanel, mWorld);
        return this;
    }
}
