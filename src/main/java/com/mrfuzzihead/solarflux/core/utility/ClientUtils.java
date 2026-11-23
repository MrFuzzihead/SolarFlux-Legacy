package com.mrfuzzihead.solarflux.core.utility;

import org.lwjgl.input.Keyboard;

/**
 * Client-only helpers.
 *
 * <p>
 * Kept out of {@link Utils} because that class is loaded on the dedicated server; the LWJGL reference is only safe
 * as long as it stays inside a method that the server never calls, which is too fragile a rule to rely on.
 * </p>
 */
public final class ClientUtils {

    private ClientUtils() {}

    public static boolean isShiftKeyDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }
}
