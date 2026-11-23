package com.mrfuzzihead.solarflux.blocks;

import net.minecraft.util.MathHelper;

/**
 * The pure part of the solar panel's light model: no world, no configuration, no tile entity.
 *
 * <p>
 * Keeping the curve here means the generation table can be asserted exactly in a unit test instead of through a
 * mocked world, and the tile entity only has to supply the inputs.
 * </p>
 */
public final class SunIntensity {

    /** At zenith, before the cosine and the clamps. */
    private static final float BASE_MULTIPLICATOR = 1.5F;
    private static final float LOW_LIGHT_MULTIPLICATOR_STEP = 0.122F;
    /** Widens the daylight window: the cosine is divided by this. */
    private static final float BASE_DISPLACEMENT = 1.2F;
    private static final float LOW_LIGHT_DISPLACEMENT_STEP = 0.08F;
    private static final float MAX_INTENSITY = 1.0F;
    private static final float MIN_INTENSITY = 0.0F;
    /** Matches the original constant so the curve does not shift. */
    private static final float TWO_PI = 2 * 3.141592F;

    private SunIntensity() {}

    /**
     * @param pCanSeeSky             whether the panel can see the sky at all
     * @param pCelestialAngleRadians 0 at zenith, {@code PI} at midnight
     * @param pRaining               whether the world is raining
     * @param pThundering            whether the world is thundering
     * @param pLowLightUpgrades      number of installed low-light upgrades
     * @param pRainFactor            configured rain multiplier
     * @param pThunderFactor         configured thunder multiplier
     * @return the usable light intensity, in {@code [0, 1]}
     */
    public static float calculate(boolean pCanSeeSky, float pCelestialAngleRadians, boolean pRaining,
        boolean pThundering, int pLowLightUpgrades, float pRainFactor, float pThunderFactor) {
        if (!pCanSeeSky) {
            return 0.0F;
        }

        float multiplicator = BASE_MULTIPLICATOR - pLowLightUpgrades * LOW_LIGHT_MULTIPLICATOR_STEP;
        float displacement = BASE_DISPLACEMENT + pLowLightUpgrades * LOW_LIGHT_DISPLACEMENT_STEP;
        // Fold the night half onto the day half: the cosine is symmetric around PI.
        float angle = pCelestialAngleRadians;
        if (angle > Math.PI) {
            angle = TWO_PI - angle;
        }

        float intensity = multiplicator * MathHelper.cos(angle / displacement);
        intensity = Math.max(MIN_INTENSITY, Math.min(MAX_INTENSITY, intensity));

        if (intensity > 0.0F) {
            if (pRaining) {
                intensity *= pRainFactor;
            }
            if (pThundering) {
                intensity *= pThunderFactor;
            }
        }
        return intensity;
    }
}
