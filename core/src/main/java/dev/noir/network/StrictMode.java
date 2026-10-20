package dev.noir.network;

import java.util.function.BooleanSupplier;

/**
 * Global Strict mode switch. When on, every setting value that would need a
 * non-vanilla packet is unavailable. Backed by the client "Strict Mode" setting;
 * on by default and on until something says otherwise.
 */
public final class StrictMode {
    private static volatile BooleanSupplier source = () -> true;

    private StrictMode() {}

    public static boolean enabled() {
        return source.getAsBoolean();
    }

    public static void bind(BooleanSupplier supplier) {
        source = supplier;
    }
}
