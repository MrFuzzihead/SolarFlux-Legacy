package com.nauktis.solarflux.core.utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class MetricUnitsTest {

    @Test
    public void theMetricPrefixesArePowersOfTen() {
        assertEquals(1_000, MetricUnits.KILO);
        assertEquals(1_000_000, MetricUnits.MEGA);
        assertEquals(1_000_000_000, MetricUnits.GIGA);
        assertEquals(1_000_000_000_000L, MetricUnits.TERA);
    }

    @Test
    public void theDefaultTierLimitsFitAnInt() {
        // ModConfiguration clamps the tier limits to 2 GIGA, so the constants have to stay inside the int range.
        assertEquals(2 * MetricUnits.GIGA, 2_000_000_000);
        assertNotNull(MetricUnits.class.getDeclaredConstructors());
    }

    @Test
    public void theRandomSourceIsShared() {
        assertSame(Utils.RANDOM, Utils.RANDOM);
    }
}
