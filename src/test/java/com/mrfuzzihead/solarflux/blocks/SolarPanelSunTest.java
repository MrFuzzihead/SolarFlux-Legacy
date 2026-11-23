package com.mrfuzzihead.solarflux.blocks;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.solarflux.config.ModConfiguration;
import com.mrfuzzihead.solarflux.support.TestFixture;
import com.mrfuzzihead.solarflux.support.TestWorld;

/**
 * The wiring between the world and the pure {@link SunIntensity}: a mocked world, a panel, one tick.
 */
public class SolarPanelSunTest {

    private static final float PI = (float) Math.PI;

    @Before
    public void setUp() {
        TestFixture.applyDefaultConfiguration();
        TestFixture.disableAutoBalance();
        TestFixture.installUpgradeItems();
    }

    private static SolarPanelTileEntity newPanel() {
        // Tier 3 generates 64 RF/t, so the rounding in the rain cases is unambiguous.
        return new SolarPanelTileEntity(2);
    }

    @Test
    public void noonProducesTheTierMaximum() {
        SolarPanelTileEntity panel = newPanel();
        TestWorld world = TestWorld.create()
            .sky(true)
            .celestialAngle(0.0F)
            .alignedTo(panel, 20)
            .attach(panel);

        panel.updateEntity();

        assertEquals(1.0F, panel.getSunIntensity(), 0.0F);
        assertEquals(panel.getMaximumEnergyGeneration(), panel.getCurrentEnergyGeneration());
    }

    @Test
    public void rainReducesTheGeneration() {
        SolarPanelTileEntity panel = newPanel();
        TestWorld.create()
            .sky(true)
            .celestialAngle(0.0F)
            .weather(true, false)
            .alignedTo(panel, 20)
            .attach(panel);

        panel.updateEntity();

        assertEquals(ModConfiguration.getRainGenerationFactor(), panel.getSunIntensity(), 0.0F);
        assertEquals(
            Math.round(panel.getMaximumEnergyGeneration() * ModConfiguration.getRainGenerationFactor()),
            panel.getCurrentEnergyGeneration());
    }

    @Test
    public void thunderReducesTheGeneration() {
        SolarPanelTileEntity panel = newPanel();
        TestWorld.create()
            .sky(true)
            .celestialAngle(0.0F)
            .weather(false, true)
            .alignedTo(panel, 20)
            .attach(panel);

        panel.updateEntity();

        assertEquals(ModConfiguration.getThunderGenerationFactor(), panel.getSunIntensity(), 0.0F);
        assertEquals(
            Math.round(panel.getMaximumEnergyGeneration() * ModConfiguration.getThunderGenerationFactor()),
            panel.getCurrentEnergyGeneration());
    }

    @Test
    public void midnightProducesNothing() {
        SolarPanelTileEntity panel = newPanel();
        TestWorld.create()
            .sky(true)
            .celestialAngle(PI)
            .alignedTo(panel, 20)
            .attach(panel);

        panel.updateEntity();

        assertEquals(0.0F, panel.getSunIntensity(), 0.0F);
        assertEquals(0, panel.getCurrentEnergyGeneration());
    }

    @Test
    public void anObstructedPanelProducesNothing() {
        SolarPanelTileEntity panel = newPanel();
        TestWorld.create()
            .sky(false)
            .celestialAngle(0.0F)
            .alignedTo(panel, 20)
            .attach(panel);

        panel.updateEntity();

        assertEquals(0.0F, panel.getSunIntensity(), 0.0F);
        assertEquals(0, panel.getCurrentEnergyGeneration());
    }
}
