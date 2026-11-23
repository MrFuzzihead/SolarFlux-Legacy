package com.mrfuzzihead.solarflux.core.utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ColorTest {

    @Test
    public void everyColorCarriesTheMinecraftFormattingCode() {
        assertEquals("\u00a77", Color.GREY.toString());
        assertEquals("\u00a7b", Color.AQUA.toString());
        assertEquals("\u00a7a", Color.GREEN.toString());
        assertEquals("\u00a7c", Color.RED.toString());
        assertEquals("\u00a7e", Color.YELLOW.toString());
    }

    @Test
    public void concatenatingWithAStringUsesTheFormattingCode() {
        // The tooltips rely on this implicit toString, so it is worth pinning down.
        assertEquals("\u00a7aGreen text", Color.GREEN + "Green text");
    }

    @Test
    public void codesAreUniqueAndWellFormed() {
        for (Color color : Color.values()) {
            assertEquals(2, color.mColorCode.length());
            assertTrue(color.mColorCode, color.mColorCode.startsWith("\u00a7"));
        }
        assertNotEquals(Color.GREEN.mColorCode, Color.RED.mColorCode);
    }
}
