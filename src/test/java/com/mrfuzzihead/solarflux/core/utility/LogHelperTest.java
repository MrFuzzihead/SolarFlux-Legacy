package com.mrfuzzihead.solarflux.core.utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.logging.log4j.Level;
import org.junit.Test;

/**
 * {@code LogHelper} delegates to FML's {@code FMLLog}, which works outside a game launch (log4j is on the test
 * classpath). These tests therefore exercise the real dispatch rather than a mock.
 */
public class LogHelperTest {

    @Test(expected = NullPointerException.class)
    public void aNullModIdIsRejected() {
        new LogHelper(null);
    }

    @Test
    public void everyLevelIsForwardedWithoutThrowing() {
        LogHelper log = new LogHelper("SolarFluxTest");

        log.all("all %s", 1);
        log.trace("trace %s", 1);
        log.debug("debug %s", 1);
        log.info("info %s", 1);
        log.warn("warn %s", 1);
        log.error("error %s", 1);
        log.fatal("fatal %s", 1);
        log.off("off %s", 1);
        log.log(Level.DEBUG, "level %s", 1);

        assertNotNull(log);
    }

    @Test
    public void formattingArgumentsAreOptional() {
        LogHelper log = new LogHelper("SolarFluxTest");

        log.info("no arguments at all");
        log.info("a throwable %s", new RuntimeException("probe"));

        assertNotNull(log);
    }

    @Test
    public void theLevelConstantsComeFromLog4j() {
        assertEquals("INFO", Level.INFO.name());
    }
}
