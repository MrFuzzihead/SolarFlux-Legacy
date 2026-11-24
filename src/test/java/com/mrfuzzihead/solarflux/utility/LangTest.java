package com.mrfuzzihead.solarflux.utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.mrfuzzihead.solarflux.reference.Reference;

public class LangTest {

    @Test
    public void thePrefixIsDerivedFromTheLowerCaseModId() {
        assertEquals("info." + Reference.MOD_ID.toLowerCase() + ".", Lang.MOD_PREFIX);
        assertEquals("info.solarflux.", Lang.MOD_PREFIX);
    }

    @Test
    public void thePrefixMatchesTheKeysInTheLangFiles() {
        // Nothing but the default English language is registered in a unit test, so StatCollector echoes the key.
        String key = Lang.MOD_PREFIX + "energy.stored";

        assertEquals(key, Lang.localise("energy.stored"));
        assertEquals("energy.stored", Lang.localise("energy.stored", false));
    }

    @Test
    public void everyKeyInTheEnglishLangFileIsReachableThroughThePrefix() throws Exception {
        java.util.List<String> keys = new java.util.ArrayList<>();
        try (java.io.InputStream stream = getClass().getResourceAsStream("/assets/solarflux/lang/en_US.lang")) {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("info.")) {
                    keys.add(line.substring(0, line.indexOf('=')));
                }
            }
        }

        assertTrue("the lang file must define info.* keys", keys.size() > 5);
        for (String key : keys) {
            assertTrue(key + " does not start with " + Lang.MOD_PREFIX, key.startsWith(Lang.MOD_PREFIX));
        }
    }

    @Test
    public void thePanelTitleKeyExistsInTheEnglishLangFile() throws Exception {
        StringBuilder lang = new StringBuilder();
        try (java.io.InputStream stream = getClass().getResourceAsStream("/assets/solarflux/lang/en_US.lang")) {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                lang.append(line)
                    .append('\n');
            }
        }

        assertTrue(
            lang.toString()
                .contains("container.solarflux.panel="));
    }
}
