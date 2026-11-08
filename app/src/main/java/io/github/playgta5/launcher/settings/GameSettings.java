package io.github.playgta5.launcher.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Launcher settings. Graphics settings become engine command-line options (the page's ?args=, see index.html: -textureQuality etc.), the
 * page's own options (?low, ?cores, ?fps, ?scale) and options of the touch controls script (assets/touch/touch.js reads them from the URL).
 */
public class GameSettings {
    private static final String PREF_NAME = "gta5_launcher_settings";

    public static final String MODE_STORY = "story";
    public static final String MODE_SANDBOX = "sandbox";
    public static final String MODE_ENV_TEST = "env_test";

    // Graphics presets, as in the iPhone port's list. "custom" once any single setting is changed.
    public static final String PRESET_ULTRA_PERF = "ultra_perf";
    public static final String PRESET_BATTERY = "battery";
    public static final String PRESET_PERFORMANCE = "performance";
    public static final String PRESET_BALANCED = "balanced";
    public static final String PRESET_QUALITY = "quality";
    public static final String PRESET_ULTRA = "ultra";
    public static final String PRESET_MAX = "max";
    public static final String PRESET_CUSTOM = "custom";
    public static final String PRESET_HIGH = PRESET_ULTRA;		// old name, kept for callers

    public static final String[] PRESETS = {PRESET_ULTRA_PERF, PRESET_BATTERY, PRESET_PERFORMANCE, PRESET_BALANCED, PRESET_QUALITY, PRESET_ULTRA, PRESET_MAX};
    public static final String[] PRESET_NAMES = {"Ultra Performance", "Battery", "Performance (recommended)", "Balanced", "Quality", "Ultra", "Max"};

    // Value lists of the single settings (index stored). Quality levels are the engine's command-line values; shadows -1 = off (index.html).
    public static final float[] SCALES = {0.5f, 0.67f, 0.75f, 0.85f, 1.0f};
    public static final String[] SCALE_NAMES = {"50%", "67%", "75%", "85%", "Native"};
    public static final int[] FPS = {30, 60, 0};
    public static final String[] FPS_NAMES = {"30 FPS", "60 FPS", "Unlocked"};
    public static final String[] LEVEL3 = {"Normal", "High", "Very high"};				// textures, shaders, particles: 0..2
    public static final String[] LEVEL4 = {"Normal", "High", "Very high", "Ultra"};	// reflections, grass: 0..3
    public static final String[] SHADOWS = {"Off", "Normal", "High", "Very high"};		// -shadowQuality = index - 1
    public static final int[] PERCENTS = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
    public static final String[] MEMORY = {"Automatic", "Low (saves memory)", "Standard"};
    public static final int[] CORES = {0, 2, 4, 8};
    public static final String[] CORE_NAMES = {"Automatic (2)", "2", "4", "8 (all)"};
    public static final String[] RUN_IN = {"Automatic", "Native engine (in the app)", "Chrome"};
    public static final int RUN_AUTO = 0, RUN_APP = 1, RUN_CHROME = 2;

    // Touch controls
    public static final String[] CAMERA = {"Swipe", "Right stick"};
    public static final String[] GYRO = {"Off", "When aiming", "Always"};

    // Measured on a Poco F6 (Snapdragon 8s Gen 3) in Chrome, 2026-11-08: the game is CPU-bound (50 % resolution was no faster than 75 %), 2 engine
    // threads beat 4 (25 vs 18 FPS), the standard memory profile ran out of the engine's 3 GB heap with 4 threads, and Balanced-level qualities
    // (draw distance, traffic, shadows) took it to ~11 FPS. So the presets up to Balanced use the low-memory profile (isLowProfile) and 2 threads.
    //                          scale fps tex shd shadow refl grass part draw% traffic% cores
    private static final int[][] PRESET_VALUES = {
            {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},		// Ultra Performance: 67 %, 30 FPS
            {2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},		// Battery: 75 %, 30 FPS
            {2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0},		// Performance (recommended): 75 %, 60 FPS cap
            {3, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0},		// Balanced: 85 % + lowest shadows (?shadows=1 of the low profile)
            {3, 1, 1, 1, 1, 0, 1, 1, 2, 3, 0},		// Quality: standard memory profile (may run out of memory, slower)
            {4, 1, 2, 2, 2, 2, 2, 2, 4, 5, 0},		// Ultra
            {4, 2, 2, 2, 3, 3, 3, 2, 10, 10, 0},	// Max
    };
    private static final String[] KEYS = {"g_scale", "g_fps", "g_textures", "g_shaders", "g_shadows", "g_reflections", "g_grass", "g_particles", "g_draw", "g_traffic", "g_cores"};
    public static final int SCALE = 0, FPS_I = 1, TEXTURES = 2, SHADERS = 3, SHADOW = 4, REFLECTIONS = 5, GRASS = 6, PARTICLES = 7, DRAW = 8, TRAFFIC = 9, CORES_I = 10;

    private final SharedPreferences prefs;

    public GameSettings(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (prefs.getInt("presets_version", 0) < 2) { applyPreset(PRESET_PERFORMANCE); prefs.edit().putInt("presets_version", 2).apply(); }
        if (prefs.getInt("presets_version", 0) < 3) {		// v3: higher resolutions for the native engine: the chosen preset is applied again
            String cur = getGraphicsPreset();
            applyPreset(PRESET_CUSTOM.equals(cur) ? PRESET_PERFORMANCE : cur);
            prefs.edit().putInt("presets_version", 3).apply();
        }
    }

    // ---- game mode / folder -----------------------------------------------------------------------------------------------------------------
    public String getGameMode() { return prefs.getString("game_mode", MODE_SANDBOX); }
    public void setGameMode(String mode) { prefs.edit().putString("game_mode", mode).apply(); }
    public String getGameDirectoryPath() { return prefs.getString("game_dir_path", ""); }
    public void setGameDirectoryPath(String path) { prefs.edit().putString("game_dir_path", path).apply(); }

    // ---- graphics ---------------------------------------------------------------------------------------------------------------------------
    public String getGraphicsPreset() { return prefs.getString("graphics_preset", PRESET_PERFORMANCE); }

    public void setGraphicsPreset(String preset) { applyPreset(preset); }

    public void applyPreset(String preset) {
        int i = indexOf(PRESETS, preset);
        if (i < 0) return;
        SharedPreferences.Editor e = prefs.edit();
        for (int k = 0; k < KEYS.length; k++) e.putInt(KEYS[k], PRESET_VALUES[i][k]);
        e.putString("graphics_preset", preset).apply();
    }

    public String getPresetName() {
        int i = indexOf(PRESETS, getGraphicsPreset());
        return i < 0 ? "Custom" : PRESET_NAMES[i].replace(" (recommended)", "");
    }

    /** Index of one graphics setting (SCALE, FPS_I, ...) in its value list. */
    public int get(int which) { return prefs.getInt(KEYS[which], PRESET_VALUES[2][which]); }

    public void set(int which, int index) {
        prefs.edit().putInt(KEYS[which], index).putString("graphics_preset", PRESET_CUSTOM).apply();
    }

    public float getResolutionScale() { return SCALES[clamp(get(SCALE), SCALES.length)]; }
    public int getFpsLimit() { return FPS[clamp(get(FPS_I), FPS.length)]; }
    public int getCores() { return CORES[clamp(get(CORES_I), CORES.length)]; }

    public void setResolutionScale(float scale) {
        int best = 0;
        for (int i = 0; i < SCALES.length; i++) if (Math.abs(SCALES[i] - scale) < Math.abs(SCALES[best] - scale)) best = i;
        set(SCALE, best);
    }

    public void setFpsLimit(int fps) {
        set(FPS_I, fps == 30 ? 0 : fps == 60 ? 1 : 2);
    }

    public int getMemoryMode() { return prefs.getInt("memory_mode", 0); }
    public void setMemoryMode(int m) { prefs.edit().putInt("memory_mode", m).apply(); }

    /** The page's low-memory profile: lowest qualities, smaller render targets. Automatic: on except for Quality, Ultra and Max. */
    public boolean isLowProfile() {
        int m = getMemoryMode();
        if (m != 0) return m == 1;
        String p = getGraphicsPreset();
        return !(PRESET_QUALITY.equals(p) || PRESET_ULTRA.equals(p) || PRESET_MAX.equals(p));
    }
    public boolean isLowMemoryMode() { return isLowProfile(); }
    public void setLowMemoryMode(boolean enabled) { setMemoryMode(enabled ? 1 : 0); }

    public boolean isFpsCounter() { return prefs.getBoolean("fps_counter", false); }
    public void setFpsCounter(boolean on) { prefs.edit().putBoolean("fps_counter", on).apply(); }
    /** When the phone is already warm at launch: 30 FPS and one resolution step lower for that session (GameActivity reads the thermal status). */
    public boolean isCoolDown() { return prefs.getBoolean("cool_down", false); }
    public void setCoolDown(boolean on) { prefs.edit().putBoolean("cool_down", on).apply(); }
    public int getRunIn() { return prefs.getInt("run_in", RUN_AUTO); }
    public void setRunIn(int r) { prefs.edit().putInt("run_in", r).apply(); }
    public boolean isDiagnosticLog() { return prefs.getBoolean("diag_log", false); }
    public void setDiagnosticLog(boolean on) { prefs.edit().putBoolean("diag_log", on).apply(); }

    // ---- touch controls ---------------------------------------------------------------------------------------------------------------------
    public boolean isTouchOverlayEnabled() { return prefs.getBoolean("touch_overlay_enabled", true); }
    public void setTouchOverlayEnabled(boolean on) { prefs.edit().putBoolean("touch_overlay_enabled", on).apply(); }
    public int getCameraMode() { return prefs.getInt("t_camera", 0); }
    public void setCameraMode(int m) { prefs.edit().putInt("t_camera", m).apply(); }
    /** Camera speed x10 (5..50 = 0.5x..5.0x). */
    public int getCameraSpeed() { return prefs.getInt("t_cam_speed", 16); }
    public void setCameraSpeed(int v) { prefs.edit().putInt("t_cam_speed", v).apply(); }
    public int getAimSpeed() { return prefs.getInt("t_aim_speed", 10); }
    public void setAimSpeed(int v) { prefs.edit().putInt("t_aim_speed", v).apply(); }
    public boolean isFireAlsoAims() { return prefs.getBoolean("t_fire_aims", true); }
    public void setFireAlsoAims(boolean on) { prefs.edit().putBoolean("t_fire_aims", on).apply(); }
    public boolean isSprintButton() { return prefs.getBoolean("t_sprint_btn", true); }
    public void setSprintButton(boolean on) { prefs.edit().putBoolean("t_sprint_btn", on).apply(); }
    public boolean isSteeringButtons() { return prefs.getBoolean("t_steer_btns", false); }
    public void setSteeringButtons(boolean on) { prefs.edit().putBoolean("t_steer_btns", on).apply(); }
    public boolean isInvertY() { return prefs.getBoolean("t_invert_y", false); }
    public void setInvertY(boolean on) { prefs.edit().putBoolean("t_invert_y", on).apply(); }
    /** Button size and opacity in percent. */
    public int getButtonSize() { return prefs.getInt("t_size", 100); }
    public void setButtonSize(int v) { prefs.edit().putInt("t_size", v).apply(); }
    public int getButtonOpacity() { return prefs.getInt("t_opacity", 75); }
    public void setButtonOpacity(int v) { prefs.edit().putInt("t_opacity", v).apply(); }
    public int getGyroMode() { return prefs.getInt("t_gyro", 0); }
    public void setGyroMode(int m) { prefs.edit().putInt("t_gyro", m).apply(); }
    public boolean isGyroEnabled() { return getGyroMode() != 0; }
    public void setGyroEnabled(boolean on) { setGyroMode(on ? 1 : 0); }
    public boolean isHapticsEnabled() { return prefs.getBoolean("haptics_enabled", true); }
    public void setHapticsEnabled(boolean on) { prefs.edit().putBoolean("haptics_enabled", on).apply(); }
    public boolean isHideWithController() { return prefs.getBoolean("t_hide_pad", true); }
    public void setHideWithController(boolean on) { prefs.edit().putBoolean("t_hide_pad", on).apply(); }

    // ---- launch URL -------------------------------------------------------------------------------------------------------------------------
    public String buildLaunchUrl(String serverBaseUrl) {
        return buildLaunchUrl(serverBaseUrl, false);
    }

    /** @param hot the phone is already warm and "Cool down when hot" is on: 30 FPS and one resolution step lower for this session. */
    public String buildLaunchUrl(String serverBaseUrl, boolean hot) {
        String mode = getGameMode();
        StringBuilder url = new StringBuilder(serverBaseUrl);
        if (MODE_ENV_TEST.equals(mode)) url.append("/?mode=sandbox&map=env_test");
        else if (MODE_STORY.equals(mode)) url.append("/?mode=story");
        else url.append("/?mode=sandbox");

        boolean low = isLowProfile();
        url.append("&low=").append(low ? 1 : 0);
        int cores = getCores();
        if (cores == 0) cores = 2;		// fastest measured (see PRESET_VALUES), and every engine thread is a ~20 MB worker
        url.append("&cores=").append(cores);
        if (low && get(SHADOW) > 0) url.append("&shadows=1");		// the low profile's lowest shadows instead of none
        boolean cool = hot && isCoolDown();
        url.append("&fps=").append(cool ? 30 : getFpsLimit());
        float scale = cool ? SCALES[Math.max(0, clamp(get(SCALE), SCALES.length) - 1)] : getResolutionScale();
        url.append("&scale=").append(String.format(Locale.US, "%.2f", scale));
        if (!low) url.append("&args=").append(String.join(",", engineArgs()));		// the low profile sets its own (lowest) qualities
        if (isDiagnosticLog()) url.append("&console=1&log=1");		// every engine line to the browser console and logcat: costs frame time

        // touch controls (assets/touch/touch.js)
        if (!isTouchOverlayEnabled()) url.append("&touch=0");
        url.append("&cam=").append(getCameraMode() == 1 ? "stick" : "swipe");
        url.append("&cs=").append(getCameraSpeed() / 10f).append("&as=").append(getAimSpeed() / 10f);
        url.append("&fa=").append(isFireAlsoAims() ? 1 : 0).append("&sb=").append(isSprintButton() ? 1 : 0);
        url.append("&steer=").append(isSteeringButtons() ? 1 : 0).append("&iy=").append(isInvertY() ? 1 : 0);
        url.append("&bs=").append(getButtonSize() / 100f).append("&bo=").append(getButtonOpacity() / 100f);
        url.append("&gyro=").append(new String[]{"off", "aim", "always"}[clamp(getGyroMode(), 3)]);
        url.append("&hap=").append(isHapticsEnabled() ? 1 : 0);
        if (isFpsCounter()) url.append("&showfps=1");
        return url.toString();
    }

    /** Engine options (GTA V command line) for the chosen qualities. */
    public List<String> engineArgs() {
        List<String> a = new ArrayList<>();
        a.add("-textureQuality=" + clamp(get(TEXTURES), 3));
        // shader quality 0 selects the low-quality shader set (common/shaders/win32_40_lq), which the port never translated to WGSL: its draws
        // would have no shaders and the engine stops ("Required grmShaderGroupVar 'DistortionParams' not found"). 1 is the lowest usable.
        a.add("-shaderQuality=" + Math.max(1, clamp(get(SHADERS), 3)));
        a.add("-shadowQuality=" + (clamp(get(SHADOW), 4) - 1));
        a.add("-reflectionQuality=" + clamp(get(REFLECTIONS), 4));
        a.add("-grassQuality=" + clamp(get(GRASS), 4));
        a.add("-particleQuality=" + clamp(get(PARTICLES), 3));
        float draw = PERCENTS[clamp(get(DRAW), PERCENTS.length)] / 100f;
        float traffic = PERCENTS[clamp(get(TRAFFIC), PERCENTS.length)] / 100f;
        a.add("-lodScale=" + fmt(draw));
        a.add("-pedLodBias=" + fmt(draw));
        a.add("-vehicleLodBias=" + fmt(draw));
        a.add("-cityDensity=" + fmt(traffic));
        a.add("-pedVariety=" + fmt(traffic));
        a.add("-vehicleVariety=" + fmt(traffic));
        return a;
    }

    public String describe() {
        return String.format(Locale.US, "%s • %s • %s%s", getPresetName(), SCALE_NAMES[clamp(get(SCALE), SCALES.length)],
                FPS_NAMES[clamp(get(FPS_I), FPS.length)], isLowProfile() ? " • Low memory" : "");
    }

    private static String fmt(float f) { return String.format(Locale.US, "%.2f", f); }
    static int clamp(int i, int n) { return Math.max(0, Math.min(n - 1, i)); }
    private static int indexOf(String[] a, String s) { for (int i = 0; i < a.length; i++) if (a[i].equals(s)) return i; return -1; }
}
