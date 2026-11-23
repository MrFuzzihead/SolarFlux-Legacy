package com.mrfuzzihead.solarflux.config;

import java.util.Objects;

import net.minecraftforge.common.config.Configuration;

/** The production {@link ConfigSource}: a thin delegate over Forge's configuration file. */
public final class ForgeConfigSource implements ConfigSource {

    private final Configuration mConfiguration;

    public ForgeConfigSource(Configuration pConfiguration) {
        mConfiguration = Objects.requireNonNull(pConfiguration);
    }

    @Override
    public boolean getBoolean(String pName, String pCategory, boolean pDefaultValue, String pComment) {
        return mConfiguration.getBoolean(pName, pCategory, pDefaultValue, pComment);
    }

    @Override
    public int getInt(String pName, String pCategory, int pDefaultValue, int pMinValue, int pMaxValue,
        String pComment) {
        return mConfiguration.getInt(pName, pCategory, pDefaultValue, pMinValue, pMaxValue, pComment);
    }

    @Override
    public float getFloat(String pName, String pCategory, float pDefaultValue, float pMinValue, float pMaxValue,
        String pComment) {
        return mConfiguration.getFloat(pName, pCategory, pDefaultValue, pMinValue, pMaxValue, pComment);
    }

    @Override
    public boolean hasCategory(String pCategory) {
        return mConfiguration.hasCategory(pCategory);
    }
}
