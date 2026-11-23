package com.mrfuzzihead.solarflux.config;

/**
 * The read side of a configuration, abstracted away from Forge's {@code Configuration}.
 *
 * <p>
 * Forge's class can only be constructed during an FML launch, which made every loader in this package untestable.
 * The interface mirrors only the methods the loaders use; {@link ForgeConfigSource} adapts the real file, and the
 * tests use an in-memory map.
 * </p>
 */
public interface ConfigSource {

    boolean getBoolean(String pName, String pCategory, boolean pDefaultValue, String pComment);

    int getInt(String pName, String pCategory, int pDefaultValue, int pMinValue, int pMaxValue, String pComment);

    float getFloat(String pName, String pCategory, float pDefaultValue, float pMinValue, float pMaxValue,
        String pComment);

    boolean hasCategory(String pCategory);
}
