package com.mrfuzzihead.solarflux.blocks;

/**
 * The returns-to-scale maths used by the transfer-rate and capacity upgrades.
 *
 * <p>
 * Both upgrades share the same shape ({@code base * (1 + increase * count ^ returnsToScale)}), so it lives in one
 * place where the double-to-int cast can be tested.
 * </p>
 */
public final class UpgradeMath {

    private UpgradeMath() {}

    /**
     * @param pBase           tier value without upgrades
     * @param pCount          number of installed upgrades of that type
     * @param pIncrease       configured increase per upgrade
     * @param pReturnsToScale configured exponent
     * @return the upgraded value, truncated like the original code did
     */
    public static int apply(int pBase, int pCount, float pIncrease, float pReturnsToScale) {
        return (int) (pBase * multiplier(pCount, pIncrease, pReturnsToScale));
    }

    /** The returns-to-scale multiplier itself, for callers that work in floating point. */
    public static double multiplier(int pCount, float pIncrease, float pReturnsToScale) {
        return 1 + pIncrease * Math.pow(pCount, pReturnsToScale);
    }
}
