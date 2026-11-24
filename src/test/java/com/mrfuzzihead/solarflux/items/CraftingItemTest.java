package com.mrfuzzihead.solarflux.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.mrfuzzihead.solarflux.support.TestFixture;
import com.mrfuzzihead.solarflux.utility.Lang;

public class CraftingItemTest {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void theTooltipIsLocalisedThroughLang() {
        CraftingItem mirror = new CraftingItem("mirror");
        List tooltip = new ArrayList();

        mirror.addInformation(TestFixture.stack(mirror, 1, 0), null, tooltip, false);

        // Nothing but the default English language is registered in a unit test, so Lang echoes the key.
        assertEquals(1, tooltip.size());
        assertEquals(Lang.localise("crafting.component"), tooltip.get(0));
        assertEquals(Lang.MOD_PREFIX + "crafting.component", tooltip.get(0));
    }

    @Test
    public void theTooltipKeyExistsInTheEnglishLangFile() throws Exception {
        String lang = readEnglishLangFile();

        assertTrue(lang.contains("info.solarflux.crafting.component="));
    }

    private static String readEnglishLangFile() throws Exception {
        StringBuilder builder = new StringBuilder();
        InputStream stream = CraftingItemTest.class.getResourceAsStream("/assets/solarflux/lang/en_US.lang");
        assertTrue("the English lang file must be on the classpath", stream != null);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line)
                    .append('\n');
            }
        }
        return builder.toString();
    }
}
