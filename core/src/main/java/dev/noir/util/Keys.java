package dev.noir.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Key codes. Keyboard keys use GLFW key codes so the Minecraft layer can pass them
 * straight through; mouse buttons are encoded as {@code MOUSE_BASE - button}.
 */
public final class Keys {
    private Keys() {}

    public static final int NONE = -1;
    public static final int MOUSE_BASE = -100;

    public static final int SPACE = 32, APOSTROPHE = 39, COMMA = 44, MINUS = 45, PERIOD = 46, SLASH = 47;
    public static final int SEMICOLON = 59, EQUAL = 61, LEFT_BRACKET = 91, BACKSLASH = 92, RIGHT_BRACKET = 93, GRAVE = 96;
    public static final int A = 65, C = 67, V = 86, X = 88, Z = 90;
    public static final int ESCAPE = 256, ENTER = 257, TAB = 258, BACKSPACE = 259, INSERT = 260, DELETE = 261;
    public static final int RIGHT = 262, LEFT = 263, DOWN = 264, UP = 265, PAGE_UP = 266, PAGE_DOWN = 267;
    public static final int HOME = 268, END = 269, CAPS_LOCK = 280, F1 = 290, KP_0 = 320, KP_ENTER = 335;
    public static final int LEFT_SHIFT = 340, LEFT_CONTROL = 341, LEFT_ALT = 342, LEFT_SUPER = 343;
    public static final int RIGHT_SHIFT = 344, RIGHT_CONTROL = 345, RIGHT_ALT = 346, RIGHT_SUPER = 347, MENU = 348;

    public static final int MOD_SHIFT = 0x1, MOD_CONTROL = 0x2, MOD_ALT = 0x4, MOD_SUPER = 0x8;

    public static final int ACTION_RELEASE = 0, ACTION_PRESS = 1, ACTION_REPEAT = 2;

    private static final Map<String, Integer> BY_NAME = new HashMap<>();
    private static final Map<Integer, String> BY_CODE = new HashMap<>();

    static {
        for (char c = 'a'; c <= 'z'; c++) put(String.valueOf(c), A + (c - 'a'));
        for (int d = 0; d <= 9; d++) put(String.valueOf(d), 48 + d);
        for (int f = 1; f <= 25; f++) put("f" + f, F1 + f - 1);
        for (int k = 0; k <= 9; k++) put("kp" + k, KP_0 + k);
        put("space", SPACE); put("apostrophe", APOSTROPHE); put("comma", COMMA); put("minus", MINUS);
        put("period", PERIOD); put("slash", SLASH); put("semicolon", SEMICOLON); put("equal", EQUAL);
        put("lbracket", LEFT_BRACKET); put("backslash", BACKSLASH); put("rbracket", RIGHT_BRACKET); put("grave", GRAVE);
        put("escape", ESCAPE); put("enter", ENTER); put("tab", TAB); put("backspace", BACKSPACE);
        put("insert", INSERT); put("delete", DELETE); put("right", RIGHT); put("left", LEFT); put("down", DOWN);
        put("up", UP); put("pageup", PAGE_UP); put("pagedown", PAGE_DOWN); put("home", HOME); put("end", END);
        put("capslock", CAPS_LOCK); put("kpenter", KP_ENTER);
        put("lshift", LEFT_SHIFT); put("lctrl", LEFT_CONTROL); put("lalt", LEFT_ALT); put("lsuper", LEFT_SUPER);
        put("rshift", RIGHT_SHIFT); put("rctrl", RIGHT_CONTROL); put("ralt", RIGHT_ALT); put("rsuper", RIGHT_SUPER);
        put("menu", MENU);
        for (int b = 0; b < 8; b++) put("mouse" + (b + 1), mouse(b));
        // Friendly aliases accepted when parsing; canonical names above are used for display.
        BY_NAME.put("rightshift", RIGHT_SHIFT); BY_NAME.put("leftshift", LEFT_SHIFT);
        BY_NAME.put("rightctrl", RIGHT_CONTROL); BY_NAME.put("leftctrl", LEFT_CONTROL);
        BY_NAME.put("rightalt", RIGHT_ALT); BY_NAME.put("leftalt", LEFT_ALT);
        BY_NAME.put("esc", ESCAPE); BY_NAME.put("return", ENTER);
    }

    private static void put(String name, int code) {
        BY_NAME.put(name, code);
        BY_CODE.put(code, name);
    }

    public static int mouse(int button) {
        return MOUSE_BASE - button;
    }

    public static boolean isMouse(int code) {
        return code <= MOUSE_BASE && code > MOUSE_BASE - 16;
    }

    public static int mouseButton(int code) {
        return MOUSE_BASE - code;
    }

    /** Canonical lowercase name, e.g. "rshift", "mouse4", "none". */
    public static String name(int code) {
        if (code == NONE) return "none";
        String n = BY_CODE.get(code);
        return n != null ? n : "key" + code;
    }

    /** Short display label for chips, e.g. "RSHIFT". */
    public static String display(int code) {
        return name(code).toUpperCase(Locale.ROOT);
    }

    /** Parses a key name; returns null if unknown. "none"/"unbind" give {@link #NONE}. */
    public static Integer parse(String text) {
        if (text == null) return null;
        String s = text.trim().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        if (s.equals("none") || s.equals("unbind") || s.equals("off")) return NONE;
        Integer code = BY_NAME.get(s);
        if (code != null) return code;
        if (s.startsWith("key")) {
            try {
                return Integer.parseInt(s.substring(3));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
