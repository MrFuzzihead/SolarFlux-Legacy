package com.mrfuzzihead.solarflux.support;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mrfuzzihead.solarflux.config.ConfigSource;

/**
 * In-memory {@link ConfigSource} for tests: Forge's {@code Configuration} needs an FML launch, this does not.
 *
 * <p>
 * Like Forge, reading a value creates its category, so the "tier category exists" checks behave the same way.
 * </p>
 */
public final class MapConfigSource implements ConfigSource {

    private final Map<String, Object> mValues = new HashMap<>();
    private final Set<String> mCategories = new HashSet<>();

    public MapConfigSource put(String pCategory, String pName, Object pValue) {
        mCategories.add(pCategory);
        mValues.put(key(pCategory, pName), pValue);
        return this;
    }

    public MapConfigSource category(String pCategory) {
        mCategories.add(pCategory);
        return this;
    }

    @Override
    public boolean getBoolean(String pName, String pCategory, boolean pDefaultValue, String pComment) {
        mCategories.add(pCategory);
        Object value = mValues.get(key(pCategory, pName));
        return value instanceof Boolean ? (Boolean) value : pDefaultValue;
    }

    @Override
    public int getInt(String pName, String pCategory, int pDefaultValue, int pMinValue, int pMaxValue,
        String pComment) {
        mCategories.add(pCategory);
        Object value = mValues.get(key(pCategory, pName));
        return value instanceof Number ? ((Number) value).intValue() : pDefaultValue;
    }

    @Override
    public float getFloat(String pName, String pCategory, float pDefaultValue, float pMinValue, float pMaxValue,
        String pComment) {
        mCategories.add(pCategory);
        Object value = mValues.get(key(pCategory, pName));
        return value instanceof Number ? ((Number) value).floatValue() : pDefaultValue;
    }

    @Override
    public boolean hasCategory(String pCategory) {
        return mCategories.contains(pCategory);
    }

    private static String key(String pCategory, String pName) {
        return pCategory + "." + pName;
    }
}
