package io.github.playgta5.launcher.settings;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.playgta5.launcher.R;

/**
 * Full-screen settings in the style of the iPhone port: a Graphics section (presets, then single settings as "‹ value ›" selectors) and a
 * Touch Controls section. Every change is saved at once; the game picks it up the next time it starts.
 */
public class SettingsDialog extends Dialog {

    public interface SettingsChangeListener {
        void onSettingsChanged();
    }

    private static final int BG = 0xFF101114, ROW_TEXT = Color.WHITE, VALUE_TEXT = 0xFFE8E8E8, HEADER_BG = 0xFF0B0C0E, GOLD = 0xFFFDB813, MUTED = 0xFF8A8D96;

    private final GameSettings settings;
    private final SettingsChangeListener listener;
    private LinearLayout list;
    private final List<Runnable> refreshers = new ArrayList<>();		// re-read values after a preset changes several at once

    public SettingsDialog(@NonNull Context context, SettingsChangeListener listener) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.settings = new GameSettings(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        FrameLayout frame = new FrameLayout(getContext());
        frame.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(getContext());
        list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        frame.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(frame);
        if (getWindow() != null) getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        buildGraphics();
        buildTouch();
        buildAdvanced();
        buildCredits();

        TextView done = new TextView(getContext());
        done.setText("DONE");
        done.setTextColor(Color.BLACK);
        done.setTypeface(Typeface.DEFAULT_BOLD);
        done.setGravity(Gravity.CENTER);
        done.setBackgroundColor(GOLD);
        done.setTextSize(16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(dp(16), dp(20), dp(16), dp(28));
        done.setOnClickListener(v -> dismiss());
        list.addView(done, lp);

        setOnDismissListener(d -> { if (listener != null) listener.onSettingsChanged(); });
    }

    // ---- sections ---------------------------------------------------------------------------------------------------------------------------
    private void buildGraphics() {
        header("GRAPHICS");
        List<TextView> checks = new ArrayList<>();
        for (int i = 0; i < GameSettings.PRESETS.length; i++) {
            final String preset = GameSettings.PRESETS[i];
            LinearLayout row = row(GameSettings.PRESET_NAMES[i]);
            TextView check = value(row, "");
            check.setTextColor(GOLD);
            checks.add(check);
            row.setOnClickListener(v -> { settings.applyPreset(preset); refreshAll(); });
            refreshers.add(() -> check.setText(preset.equals(settings.getGraphicsPreset()) ? "✓" : ""));
        }
        note("This game is limited by the CPU on phones: lower resolution helps little, fewer engine threads and less traffic help most. "
                + "Up to Balanced the low-memory profile is on (Memory cap: Automatic), which keeps textures, draw distance and traffic at their lowest; "
                + "Quality and above use the standard profile, which can run out of memory and is much slower.");
        graphicsChoice("Resolution", GameSettings.SCALE, GameSettings.SCALE_NAMES);
        graphicsChoice("Frame rate", GameSettings.FPS_I, GameSettings.FPS_NAMES);
        toggle("Show FPS counter", settings::isFpsCounter, settings::setFpsCounter);
        toggle("Cool down when hot", settings::isCoolDown, settings::setCoolDown);
        choice("Memory cap", GameSettings.MEMORY, settings::getMemoryMode, settings::setMemoryMode);
        graphicsChoice("Engine threads", GameSettings.CORES_I, GameSettings.CORE_NAMES);
        graphicsChoice("Textures", GameSettings.TEXTURES, GameSettings.LEVEL3);
        graphicsChoice("Shaders", GameSettings.SHADERS, GameSettings.LEVEL3);
        graphicsChoice("Shadows", GameSettings.SHADOW, GameSettings.SHADOWS);
        graphicsChoice("Reflections", GameSettings.REFLECTIONS, GameSettings.LEVEL4);
        graphicsChoice("Draw distance", GameSettings.DRAW, percentNames());
        graphicsChoice("Grass", GameSettings.GRASS, GameSettings.LEVEL4);
        graphicsChoice("Particles", GameSettings.PARTICLES, GameSettings.LEVEL3);
        graphicsChoice("Traffic and people", GameSettings.TRAFFIC, percentNames());
        refreshAll();
    }

    private void buildTouch() {
        header("TOUCH CONTROLS");
        toggle("Touch controls", settings::isTouchOverlayEnabled, settings::setTouchOverlayEnabled);
        choice("Camera", GameSettings.CAMERA, settings::getCameraMode, settings::setCameraMode);
        slider("Camera speed", 5, 50, settings::getCameraSpeed, settings::setCameraSpeed, v -> String.format(Locale.US, "%.1fx", v / 10f));
        slider("Aim speed", 3, 40, settings::getAimSpeed, settings::setAimSpeed, v -> String.format(Locale.US, "%.1fx", v / 10f));
        toggle("Fire also aims", settings::isFireAlsoAims, settings::setFireAlsoAims);
        toggle("Sprint button", settings::isSprintButton, settings::setSprintButton);
        note("Sprint button off: push the movement stick to its edge to sprint.");
        toggle("Steering buttons", settings::isSteeringButtons, settings::setSteeringButtons);
        choice("Gyro aiming", GameSettings.GYRO, settings::getGyroMode, settings::setGyroMode);
        toggle("Invert look up/down", settings::isInvertY, settings::setInvertY);
        slider("Button size", 60, 150, settings::getButtonSize, settings::setButtonSize, v -> v + "%");
        slider("Button opacity", 20, 100, settings::getButtonOpacity, settings::setButtonOpacity, v -> v + "%");
        toggle("Rumble", settings::isHapticsEnabled, settings::setHapticsEnabled);
        toggle("Hide when a controller is used", settings::isHideWithController, settings::setHideWithController);
        note("Edit controls layout: in the game, tap the gear button at the top right, then Edit layout and drag the buttons.");
    }

    private void buildAdvanced() {
        header("ADVANCED");
        choice("Run game in", GameSettings.RUN_IN, settings::getRunIn, settings::setRunIn);
        note("Native engine: the game's engine compiled for this phone (no browser; Vulkan graphics). Chrome: the web version in a Chrome tab. "
                + "Automatic uses the native engine when it is installed with the app.");
        toggle("Diagnostic logging", settings::isDiagnosticLog, settings::setDiagnosticLog);
        note("Sends every engine log line to logcat. Leave off for the best frame rate.");
    }

    // ---- rows -------------------------------------------------------------------------------------------------------------------------------
    private interface IntGet { int get(); }
    private interface IntSet { void set(int v); }
    private interface BoolGet { boolean get(); }
    private interface BoolSet { void set(boolean v); }
    private interface Fmt { String f(int v); }

    private void graphicsChoice(String title, int which, String[] names) {
        choice(title, names, () -> settings.get(which), v -> { settings.set(which, v); refreshAll(); });
    }

    private void choice(String title, String[] names, IntGet get, IntSet set) {
        LinearLayout row = row(title);
        TextView prev = arrow(row, "‹");
        TextView val = value(row, "");
        TextView next = arrow(row, "›");
        Runnable refresh = () -> val.setText(names[GameSettings.clamp(get.get(), names.length)]);
        prev.setOnClickListener(v -> { set.set((GameSettings.clamp(get.get(), names.length) + names.length - 1) % names.length); refresh.run(); });
        View.OnClickListener fwd = v -> { set.set((GameSettings.clamp(get.get(), names.length) + 1) % names.length); refresh.run(); };
        next.setOnClickListener(fwd);
        row.setOnClickListener(fwd);
        refreshers.add(refresh);
        refresh.run();
    }

    private void toggle(String title, BoolGet get, BoolSet set) {
        choice(title, new String[]{"Off", "On"}, () -> get.get() ? 1 : 0, v -> set.set(v == 1));
    }

    private void slider(String title, int min, int max, IntGet get, IntSet set, Fmt fmt) {
        LinearLayout row = row(title);
        SeekBar bar = new SeekBar(getContext());
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, get.get() - min)));
        bar.getProgressDrawable().setTint(Color.WHITE);
        bar.getThumb().setTint(Color.WHITE);
        row.addView(bar, new LinearLayout.LayoutParams(dp(170), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView val = value(row, fmt.f(get.get()));
        val.setMinWidth(dp(52));
        val.setGravity(Gravity.END);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { if (user) { set.set(p + min); val.setText(fmt.f(p + min)); } }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void header(String text) {
        TextView h = new TextView(getContext());
        h.setText(text);
        h.setTextColor(Color.WHITE);
        h.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
        h.setLetterSpacing(0.05f);
        h.setTextSize(17);
        h.setGravity(Gravity.CENTER);
        h.setBackgroundColor(HEADER_BG);
        list.addView(h, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
    }

    private void note(String text) {
        TextView n = new TextView(getContext());
        n.setText(text);
        n.setTextColor(MUTED);
        n.setTextSize(12);
        n.setPadding(dp(18), dp(2), dp(18), dp(10));
        list.addView(n);
    }

    private LinearLayout row(String title) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(12), 0);
        TypedValue tv = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);
        TextView t = new TextView(getContext());
        t.setText(title);
        t.setTextColor(ROW_TEXT);
        t.setTextSize(16);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        return row;
    }

    private TextView value(LinearLayout row, String text) {
        TextView v = new TextView(getContext());
        v.setText(text);
        v.setTextColor(VALUE_TEXT);
        v.setTextSize(15);
        v.setPadding(dp(6), 0, dp(6), 0);
        row.addView(v);
        return v;
    }

    private TextView arrow(LinearLayout row, String glyph) {
        TextView a = new TextView(getContext());
        a.setText(glyph);
        a.setTextColor(VALUE_TEXT);
        a.setTextSize(20);
        a.setGravity(Gravity.CENTER);
        row.addView(a, new LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.MATCH_PARENT));
        return a;
    }

    private void refreshAll() { for (Runnable r : refreshers) r.run(); }

    private static String[] percentNames() {
        String[] s = new String[GameSettings.PERCENTS.length];
        for (int i = 0; i < s.length; i++) s[i] = GameSettings.PERCENTS[i] + "%";
        return s;
    }

    private void buildCredits() {
        header("CREDITS & LICENSE");
        note("GTA V Android Launcher & Native ARM64 Engine\n"
                + "Developed & Ported by Mohit Yadav (@mohitky2018)\n\n"
                + "Third-Party Libraries:\n"
                + "• wgpu-native (WebGPU Vulkan) - MIT / Apache-2.0\n"
                + "• WABT (wasm2c) - Apache-2.0\n"
                + "• SIMDe - MIT\n\n"
                + "Licensed under the MIT License.\n"
                + "Copyright (c) 2026 Mohit Yadav\n\n"
                + "Grand Theft Auto and GTA V are trademarks of Rockstar Games and Take-Two Interactive. Not affiliated with or endorsed by Rockstar Games.");
    }

    private int dp(int v) {
        return Math.round(v * getContext().getResources().getDisplayMetrics().density);
    }
}
