package com.mrfuzzihead.solarflux.utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.mrfuzzihead.solarflux.config.ModConfiguration;

/**
 * Data integrity for the shipped assets: a translation that changes a {@code %s} to a {@code %d} throws at runtime, and
 * a missing connected texture is only visible in game.
 */
public class LangIntegrityTest {

    private static final String LANG_PATH = "/assets/solarflux/lang/";
    private static final String TEXTURE_PATH = "/assets/solarflux/textures/blocks/";
    private static final Pattern FORMAT_SPECIFIER = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]");

    @Test
    public void everyTranslationKeepsTheEnglishFormatSpecifiers() throws Exception {
        Map<String, String> english = readLang("en_US");
        assertTrue("the English file must define a useful number of keys", english.size() > 20);

        for (String language : new String[] { "de_DE", "fr_FR", "pt_BR", "zh_CN", "ko_KR", "pl_PL", "ru_RU" }) {
            Map<String, String> translated = readLang(language);
            for (Map.Entry<String, String> entry : translated.entrySet()) {
                String source = english.get(entry.getKey());
                if (source != null) {
                    assertEquals(
                        language + " changes the format specifiers of " + entry.getKey(),
                        specifiers(source),
                        specifiers(entry.getValue()));
                }
            }
        }
    }

    @Test
    public void theEnglishFileNamesEveryDefaultTier() throws Exception {
        Map<String, String> english = readLang("en_US");

        for (int tier = 0; tier < ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.size(); ++tier) {
            assertNotNull(
                "missing tile.solarflux:solar" + tier + ".name",
                english.get("tile.solarflux:solar" + tier + ".name"));
        }
    }

    @Test
    public void everyDefaultTierHasItsWholeConnectedTextureSet() throws Exception {
        for (int tier = 0; tier < ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.size(); ++tier) {
            for (int mask = 0; mask < 16; ++mask) {
                assertTextureExists("solar" + tier + "_" + mask + ".png");
            }
            assertTextureExists("solar" + tier + "_side.png");
        }
    }

    private static void assertTextureExists(String pName) {
        assertNotNull("missing texture " + pName, LangIntegrityTest.class.getResourceAsStream(TEXTURE_PATH + pName));
    }

    private static Map<String, String> readLang(String pName) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        InputStream stream = LangIntegrityTest.class.getResourceAsStream(LANG_PATH + pName + ".lang");
        assertNotNull("missing lang file " + pName, stream);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator > 0) {
                    entries.put(line.substring(0, separator), line.substring(separator + 1));
                }
            }
        }
        return entries;
    }

    /** Positional specifiers (%1$s) are normalised, and the literal %% is ignored. */
    private static List<String> specifiers(String pText) {
        List<String> found = new ArrayList<>();
        Matcher matcher = FORMAT_SPECIFIER.matcher(pText);
        while (matcher.find()) {
            String token = matcher.group();
            if ("%%".equals(token)) {
                continue;
            }
            found.add(token.replaceAll("\\d+\\$", ""));
        }
        Collections.sort(found);
        return found;
    }
}
