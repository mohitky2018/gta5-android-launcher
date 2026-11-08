package io.github.playgta5.launcher.engine;

import android.view.Surface;

import java.util.HashMap;
import java.util.Map;

/**
 * The native GTA V engine (libgta5engine.so: the web build's engine translated to native arm64, with a Vulkan renderer through
 * wgpu-native). One engine per process: NativeGameActivity runs in its own process and ends it when the game closes.
 */
public final class NativeEngine {
    private static boolean loaded;
    private static String loadError;

    static {
        try {
            System.loadLibrary("wgpu_native");
            System.loadLibrary("gta5engine");
            loaded = true;
        } catch (Throwable t) {
            loadError = t.toString();
        }
    }

    private NativeEngine() {}

    public static boolean isAvailable() { return loaded; }
    public static String loadError() { return loadError; }

    /** Starts the engine's main thread; returns 0 when it started. */
    public static native int start(String dataDir, String userDir, String shaderDir, String[] args, int cores, int startMode, int width, int height);
    public static native void setSurface(Surface surface, int width, int height);
    public static native void key(int vk, boolean down);
    public static native void mouseMove(int dx, int dy);
    /** bit: 0 left, 1 right, 2 middle */
    public static native void mouseButton(int bit, boolean down);
    public static native void wheel(int notches);
    public static native void setFocus(boolean focus);
    /** Engine thread groups not skipped (1 replay, 2 net, 4 bink); call before start. Default 1: see android_main.cpp moduleInt. */
    public static native void setKeepThreads(int mask);
    /** Before start: keep the frame's critical threads off the slowest CPU cluster; the frame rate target of the performance hints (0 = unlocked). */
    public static native void setPerformance(boolean pinThreads, int targetFps);
    /** Before start: the game's language as a browser language tag (GameSettings.LANGUAGE_TAGS). */
    public static native void setLanguage(String tag);
    public static native long framesPresented();
    public static native String crashReason();
    /** The player's situation for the touch controls: bits 0-3 context (1 on foot, 2 car, 3 motorbike, 4 bicycle, 5 boat, 6 sub, 7 heli,
     *  8 plane), bit 8 phone out, bit 9 pause menu, bit 10 a player exists. */
    public static native int controlState();

    // ---- KeyboardEvent.code -> Windows virtual key (the page's VK table, index.html) --------------------------------------------------
    private static final Map<String, Integer> VK = new HashMap<>();
    static {
        Object[] t = {
                "Backspace", 0x08, "Tab", 0x09, "Enter", 0x0D, "NumpadEnter", 0xFD, "Pause", 0x13, "CapsLock", 0x14, "Escape", 0x1B, "Space", 0x20,
                "PageUp", 0x21, "PageDown", 0x22, "End", 0x23, "Home", 0x24, "ArrowLeft", 0x25, "ArrowUp", 0x26, "ArrowRight", 0x27, "ArrowDown", 0x28,
                "Insert", 0x2D, "Delete", 0x2E, "NumpadMultiply", 0x6A, "NumpadAdd", 0x6B, "NumpadSubtract", 0x6D, "NumpadDecimal", 0x6E, "NumpadDivide", 0x6F,
                "ShiftLeft", 0xA0, "ShiftRight", 0xA1, "ControlLeft", 0xA2, "ControlRight", 0xA3, "AltLeft", 0xA4, "AltRight", 0xA5,
                "Semicolon", 0xBA, "Equal", 0xBB, "Comma", 0xBC, "Minus", 0xBD, "Period", 0xBE, "Slash", 0xBF, "Backquote", 0xC0,
                "BracketLeft", 0xDB, "Backslash", 0xDC, "BracketRight", 0xDD, "Quote", 0xDE,
        };
        for (int i = 0; i < t.length; i += 2) VK.put((String) t[i], (Integer) t[i + 1]);
        for (int i = 0; i < 10; i++) { VK.put("Digit" + i, 0x30 + i); VK.put("Numpad" + i, 0x60 + i); }
        for (int i = 0; i < 26; i++) VK.put("Key" + (char) ('A' + i), 0x41 + i);
        for (int i = 1; i <= 24; i++) VK.put("F" + i, 0x6F + i);
    }

    public static int vk(String code) { Integer v = VK.get(code); return v == null ? 0 : v; }

    /** A key by its KeyboardEvent.code name (the touch controls' names), e.g. "KeyW". */
    public static void keyCode(String code, boolean down) { int v = vk(code); if (v != 0 && loaded) key(v, down); }
}
