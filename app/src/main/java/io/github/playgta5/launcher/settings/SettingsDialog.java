package io.github.playgta5.launcher.settings;

import android.app.Dialog;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.playgta5.launcher.mods.ModManager;
import io.github.playgta5.launcher.radio.SelfRadioDialog;
import io.github.playgta5.launcher.saves.StorySavesDialog;

/**
 * Full-screen settings laid out like the game's own pause-menu settings: a title bar, the categories on the left, the chosen category's
 * rows on the right with a note under them, and BACK at the bottom right. Every change is saved at once; the game picks it up the next
 * time it starts. The categories that act on files (game folder, file check, save import) need the home screen: it passes a {@link Host}.
 */
public class SettingsDialog extends Dialog {

    public interface SettingsChangeListener { void onSettingsChanged(); }

    /** What only the home screen can do (folder picker, file check). Null in the game: those rows then say so. */
    public interface Host {
        String gameFilesSummary();
        void chooseGameFolder();
        void enterGameFolderPath();
        void verifyGameFiles();
        void importStorySave();
    }

    private static final int TITLE_BG = 0xFFEDEDED, ACCENT = 0xFF2F7BD6, SIDEBAR_BG = 0xB8181A15, PANEL_BG = 0xF2161713, PANEL_HEAD_BG = 0xFF111210,
            NOTE_BG = 0xE61D1E1B, ROW_TEXT = Color.WHITE, VALUE_TEXT = 0xFFE6E6E6, MUTED = 0xFFA4A6A0, ROW_PRESSED = 0x33FFFFFF;

    private static final String[] CATEGORIES = {"Graphics", "Controls", "Language and saves", "Game files", "Mods & patches", "Self Radio", "This device"};

    private final GameSettings settings;
    private final SettingsChangeListener listener;
    private Host host;
    private Typeface condensed, body;
    private LinearLayout sidebar, list;
    private TextView panelTitle, noteView;
    private ScrollView listScroll;
    private int category = 0;
    private final List<Runnable> refreshers = new ArrayList<>();		// re-read values after a preset changes several at once

    public SettingsDialog(@NonNull Context context, SettingsChangeListener listener) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.settings = new GameSettings(context);
        this.listener = listener;
    }

    /** The home screen's file actions (see {@link Host}). */
    public SettingsDialog setHost(Host host) { this.host = host; return this; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        loadFonts();
        DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        int sideMargin = Math.round(dm.widthPixels * 0.085f), topMargin = Math.round(dm.heightPixels * 0.05f);

        FrameLayout root = new FrameLayout(getContext());
        root.setBackground(background());

        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        clp.setMargins(sideMargin, topMargin, sideMargin, Math.round(dm.heightPixels * 0.03f));
        root.addView(column, clp);

        // title bar: a blue line over a light bar
        View accent = new View(getContext());
        accent.setBackgroundColor(ACCENT);
        column.addView(accent, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));
        TextView title = new TextView(getContext());
        title.setText("SETTINGS");
        title.setTextColor(0xFF111111);
        title.setTypeface(condensed);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        title.setBackgroundColor(TITLE_BG);
        column.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        // body: categories | rows + note
        LinearLayout bodyRow = new LinearLayout(getContext());
        bodyRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        blp.topMargin = dp(5);
        column.addView(bodyRow, blp);

        ScrollView sideScroll = new ScrollView(getContext());
        sideScroll.setVerticalScrollBarEnabled(false);
        sidebar = new LinearLayout(getContext());
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setBackgroundColor(SIDEBAR_BG);
        sideScroll.addView(sidebar, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bodyRow.addView(sideScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.30f));
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int idx = i;
            TextView t = new TextView(getContext());
            t.setText(CATEGORIES[i]);
            t.setTypeface(body);
            t.setTextSize(16);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(dp(14), 0, dp(10), 0);
            t.setSingleLine(true);
            t.setOnClickListener(v -> showCategory(idx));
            sidebar.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        }

        LinearLayout right = new LinearLayout(getContext());
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.70f);
        rlp.leftMargin = dp(5);
        bodyRow.addView(right, rlp);

        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(PANEL_BG);
        right.addView(panel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panelTitle = new TextView(getContext());
        panelTitle.setTextColor(Color.WHITE);
        panelTitle.setTypeface(condensed);
        panelTitle.setTextSize(20);
        panelTitle.setGravity(Gravity.CENTER);
        panelTitle.setBackgroundColor(PANEL_HEAD_BG);
        panel.addView(panelTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        listScroll = new ScrollView(getContext());
        list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(8));
        listScroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(listScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        noteView = new TextView(getContext());
        noteView.setTextColor(Color.WHITE);
        noteView.setTypeface(body);
        noteView.setTextSize(13);
        noteView.setBackgroundColor(NOTE_BG);
        noteView.setPadding(dp(14), dp(9), dp(14), dp(9));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(5);
        right.addView(noteView, nlp);

        // BACK [x] at the bottom right
        LinearLayout back = new LinearLayout(getContext());
        back.setOrientation(LinearLayout.HORIZONTAL);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setBackgroundColor(0xE6202120);
        back.setPadding(dp(12), dp(5), dp(6), dp(5));
        TextView backText = new TextView(getContext());
        backText.setText("BACK");
        backText.setTextColor(Color.WHITE);
        backText.setTypeface(condensed);
        backText.setTextSize(18);
        backText.setPadding(0, 0, dp(8), 0);
        back.addView(backText);
        TextView x = new TextView(getContext());
        x.setText("✕");
        x.setTextColor(0xFF111111);
        x.setTextSize(16);
        x.setTypeface(Typeface.DEFAULT_BOLD);
        x.setGravity(Gravity.CENTER);
        GradientDrawable xbg = new GradientDrawable(); xbg.setColor(Color.WHITE); xbg.setCornerRadius(dp(6));
        x.setBackground(xbg);
        back.addView(x, new LinearLayout.LayoutParams(dp(30), dp(30)));
        back.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams bklp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bklp.gravity = Gravity.END; bklp.topMargin = dp(6);
        column.addView(back, bklp);

        setContentView(root);
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            hideSystemBars();
        }
        showCategory(0);
        setOnDismissListener(d -> { if (listener != null) listener.onSettingsChanged(); });
    }

    private void hideSystemBars() {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            decor.post(() -> {
                WindowInsetsController c = decor.getWindowInsetsController();
                if (c != null) { c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()); c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
            });
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    /** Your own picture as res/drawable/settings_background (app/src/local/res, not in git) behind the settings; a dark gradient without one. */
    private android.graphics.drawable.Drawable background() {
        int id = getContext().getResources().getIdentifier("settings_background", "drawable", getContext().getPackageName());
        if (id != 0) {
            try {
                android.graphics.drawable.Drawable pic = getContext().getDrawable(id);
                android.graphics.drawable.ColorDrawable dim = new android.graphics.drawable.ColorDrawable(0x33000000);
                return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{pic, dim});
            } catch (Exception ignored) {}
        }
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF3A4A3A, 0xFF1C221D, 0xFF0E100E});
    }

    /** The condensed Chalet faces of app/src/local/assets/fonts when present (yours, not in git); system faces otherwise. */
    private void loadFonts() {
        condensed = font("fonts/ChaletComprimeCologneSixty.ttf", Typeface.create("sans-serif-condensed", Typeface.BOLD));
        body = Typeface.create("sans-serif", Typeface.NORMAL);
    }
    private Typeface font(String asset, Typeface fallback) {
        try { return Typeface.createFromAsset(getContext().getAssets(), asset); } catch (Exception e) { return fallback; }
    }

    // ---- categories ---------------------------------------------------------------------------------------------------------------------
    private void showCategory(int idx) {
        category = idx;
        for (int i = 0; i < sidebar.getChildCount(); i++) {
            TextView t = (TextView) sidebar.getChildAt(i);
            boolean sel = i == idx;
            t.setBackgroundColor(sel ? Color.WHITE : Color.TRANSPARENT);
            t.setTextColor(sel ? 0xFF111111 : Color.WHITE);
        }
        list.removeAllViews();
        refreshers.clear();
        panelTitle.setText(CATEGORIES[idx].toUpperCase(Locale.US));
        switch (idx) {
            case 0: buildGraphics(); break;
            case 1: buildControls(); break;
            case 2: buildLanguageAndSaves(); break;
            case 3: buildGameFiles(); break;
            case 4: buildMods(); break;
            case 5: buildRadio(); break;
            default: buildDevice(); break;
        }
        listScroll.scrollTo(0, 0);
        refreshAll();
    }

    private void buildGraphics() {
        setNote("Changes apply the next time the game starts. Presets set every option below at once.");
        for (int i = 0; i < GameSettings.PRESETS.length; i++) {
            final String preset = GameSettings.PRESETS[i];
            LinearLayout row = row(GameSettings.PRESET_NAMES[i]);
            TextView check = value(row, "");
            check.setTextSize(22);
            row.setOnClickListener(v -> { settings.applyPreset(preset); refreshAll(); });
            refreshers.add(() -> check.setText(preset.equals(settings.getGraphicsPreset()) ? "✓" : ""));
        }
        section("CUSTOM");
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
        rowNote("This game is limited by the CPU on phones: lower resolution helps little, fewer engine threads and less traffic help most. "
                + "Up to Balanced the low-memory profile is on (Memory cap: Automatic); Quality and above use the standard profile, which can run out of memory.");
    }

    private void buildControls() {
        setNote("Touch controls change with what you are doing: on foot, driving, flying, the phone and the pause menu each get their own buttons.");
        toggle("Touch controls", settings::isTouchOverlayEnabled, settings::setTouchOverlayEnabled);
        choice("Camera", GameSettings.CAMERA, settings::getCameraMode, settings::setCameraMode);
        slider("Camera speed", 5, 50, settings::getCameraSpeed, settings::setCameraSpeed, v -> String.format(Locale.US, "%.1fx", v / 10f));
        slider("Aim speed", 3, 40, settings::getAimSpeed, settings::setAimSpeed, v -> String.format(Locale.US, "%.1fx", v / 10f));
        toggle("Fire also aims", settings::isFireAlsoAims, settings::setFireAlsoAims);
        toggle("Sprint button", settings::isSprintButton, settings::setSprintButton);
        toggle("Steering buttons", settings::isSteeringButtons, settings::setSteeringButtons);
        choice("Gyro aiming", GameSettings.GYRO, settings::getGyroMode, settings::setGyroMode);
        toggle("Invert look up/down", settings::isInvertY, settings::setInvertY);
        slider("Button size", 60, 150, settings::getButtonSize, settings::setButtonSize, v -> v + "%");
        slider("Button opacity", 20, 100, settings::getButtonOpacity, settings::setButtonOpacity, v -> v + "%");
        toggle("Rumble", settings::isHapticsEnabled, settings::setHapticsEnabled);
        toggle("Hide when a controller is used", settings::isHideWithController, settings::setHideWithController);
        rowNote("Sprint button off: push the movement stick to its edge to sprint. Camera Right stick: a second stick on the right turns the camera.");
    }

    private void buildLanguageAndSaves() {
        setNote("The language of the game's text and subtitles. It applies the next time the game starts.");
        choice("Language", GameSettings.LANGUAGE_NAMES, settings::getLanguageIndex, settings::setLanguageIndex);
        String[] modes = {"Story mode", "Sandbox", "GTA VI map (env_test)"};
        String[] keys = {GameSettings.MODE_STORY, GameSettings.MODE_SANDBOX, GameSettings.MODE_ENV_TEST};
        choice("Start in", modes, () -> { String m = settings.getGameMode(); for (int i = 0; i < keys.length; i++) if (keys[i].equals(m)) return i; return 1; },
                v -> settings.setGameMode(keys[v]));
        section("SAVES");
        action("Story saves", "Manage ›", () -> new StorySavesDialog(getContext(), host == null ? null : host::importStorySave).show());
        rowNote("Story saves lists, imports and deletes the saves of Story mode (files/engine/userdata).");
    }

    private void buildGameFiles() {
        setNote("The game files: the folder with manifest.json, common/ and x64/ (the web mirror's data folder or a folder above it).");
        String path = settings.getGameDirectoryPath();
        info("Folder", path == null || path.isEmpty() ? "Not chosen (app storage)" : shortPath(path));
        if (host == null) {
            rowNote("Choosing the folder and checking the files is on the home screen (leave the game first).");
            return;
        }
        info("Status", host.gameFilesSummary());
        action("Choose folder", "›", () -> { dismiss(); host.chooseGameFolder(); });
        action("Enter path", "›", () -> { dismiss(); host.enterGameFolderPath(); });
        action("Check files", "›", () -> { dismiss(); host.verifyGameFiles(); });
        rowNote("Check files compares every file with manifest.json (missing files, wrong sizes).");
    }

    private void buildMods() {
        ModManager mods = new ModManager(getContext());
        setNote("Script mods (.asi, .dll, .lua, .cs) in " + mods.getScriptsDir().getAbsolutePath() + ". Tap one to turn it on or off.");
        List<ModManager.ModItem> items = mods.getModsList();
        if (items.isEmpty()) { rowNote("No mods found. Copy mod files into the folder above, then open this page again."); return; }
        for (ModManager.ModItem m : items) {
            LinearLayout row = row(m.name);
            TextView v = value(row, m.isEnabled ? "On" : "Off");
            row.setOnClickListener(x -> { if (mods.toggleMod(m)) showCategory(category); });
            v.setTextColor(m.isEnabled ? Color.WHITE : MUTED);
        }
    }

    private void buildRadio() {
        setNote("Self Radio plays your own music in the game's Self Radio station.");
        action("Self Radio tracks", "Open ›", () -> new SelfRadioDialog(getContext()).show());
    }

    private void buildDevice() {
        setNote("How the game runs on this phone. Changes apply the next time the game starts.");
        info("Phone", Build.MANUFACTURER + " " + Build.MODEL);
        String soc = Build.VERSION.SDK_INT >= 31 ? Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL : Build.HARDWARE;
        info("Chip", soc + " • " + Runtime.getRuntime().availableProcessors() + " cores");
        ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) { ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo(); am.getMemoryInfo(mi); info("Memory", String.format(Locale.US, "%.1f GB", mi.totalMem / 1073741824.0)); }
        info("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        section("ENGINE");
        choice("Run game in", GameSettings.RUN_IN, settings::getRunIn, settings::setRunIn);
        toggle("Fast CPU cores for the game", settings::isFastCores, settings::setFastCores);
        toggle("Display scaling", settings::isDisplayScaling, settings::setDisplayScaling);
        toggle("Sustained performance", settings::isSustainedPerformance, settings::setSustainedPerformance);
        toggle("Diagnostic logging", settings::isDiagnosticLog, settings::setDiagnosticLog);
        rowNote("Fast CPU cores keeps the game's main and graphics threads off the slowest cores (turn off to save battery). Display scaling lets "
                + "the screen hardware scale the picture up below Native resolution. Sustained performance trades peak speed for speed the phone can "
                + "hold when hot. Diagnostic logging costs frame rate.");
        section("CREDITS & LICENSE");
        rowNote("GTA V Android Launcher & Native ARM64 Engine\n"
                + "Developed & Ported by Mohit Yadav (@mohitky2018)\n\n"
                + "Third-Party Libraries:\n"
                + "• wgpu-native (WebGPU Vulkan) - MIT / Apache-2.0\n"
                + "• WABT (wasm2c) - Apache-2.0\n"
                + "• SIMDe - MIT\n\n"
                + "Licensed under the MIT License.\n"
                + "Copyright (c) 2026 Mohit Yadav\n\n"
                + "Grand Theft Auto and GTA V are trademarks of Rockstar Games and Take-Two Interactive. Not affiliated with or endorsed by Rockstar Games.");
    }

    // ---- rows -------------------------------------------------------------------------------------------------------------------------------
    private interface IntGet { int get(); }
    private interface IntSet { void set(int v); }
    private interface BoolGet { boolean get(); }
    private interface BoolSet { void set(boolean v); }
    private interface Fmt { String f(int v); }

    private void setNote(String text) { noteView.setText(text); }

    private void graphicsChoice(String title, int which, String[] names) {
        choice(title, names, () -> settings.get(which), v -> { settings.set(which, v); refreshAll(); });
    }

    private void choice(String title, String[] names, IntGet get, IntSet set) {
        LinearLayout row = row(title);
        TextView prev = arrow(row, "‹");
        TextView val = value(row, "");
        val.setMinWidth(dp(110));
        val.setGravity(Gravity.CENTER);
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

    private void action(String title, String hint, Runnable run) {
        LinearLayout row = row(title);
        value(row, hint);
        row.setOnClickListener(v -> run.run());
    }

    private void info(String title, String text) {
        LinearLayout row = row(title);
        TextView v = value(row, text);
        v.setTextColor(MUTED);
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.START);
        v.setMaxWidth(dp(320));
        row.setBackground(null);
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

    private void section(String text) {
        TextView h = new TextView(getContext());
        h.setText(text);
        h.setTextColor(MUTED);
        h.setTypeface(condensed);
        h.setTextSize(15);
        h.setLetterSpacing(0.04f);
        h.setPadding(dp(18), dp(14), dp(18), dp(4));
        list.addView(h);
    }

    private void rowNote(String text) {
        TextView n = new TextView(getContext());
        n.setText(text);
        n.setTextColor(MUTED);
        n.setTypeface(body);
        n.setTextSize(12);
        n.setPadding(dp(18), dp(8), dp(18), dp(8));
        list.addView(n);
    }

    private LinearLayout row(String title) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(14), 0);
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_pressed}, new android.graphics.drawable.ColorDrawable(ROW_PRESSED));
        bg.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        row.setBackground(bg);
        TextView t = new TextView(getContext());
        t.setText(title);
        t.setTextColor(ROW_TEXT);
        t.setTypeface(body);
        t.setTextSize(17);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return row;
    }

    private TextView value(LinearLayout row, String text) {
        TextView v = new TextView(getContext());
        v.setText(text);
        v.setTextColor(VALUE_TEXT);
        v.setTypeface(body);
        v.setTextSize(16);
        v.setPadding(dp(6), 0, dp(6), 0);
        row.addView(v);
        return v;
    }

    private TextView arrow(LinearLayout row, String glyph) {
        TextView a = new TextView(getContext());
        a.setText(glyph);
        a.setTextColor(VALUE_TEXT);
        a.setTextSize(22);
        a.setGravity(Gravity.CENTER);
        row.addView(a, new LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.MATCH_PARENT));
        return a;
    }

    private void refreshAll() { for (Runnable r : refreshers) r.run(); }

    private static String shortPath(String p) {
        String s = p;
        int i = s.indexOf("/tree/");
        if (i >= 0) s = android.net.Uri.decode(s.substring(i + 6));
        return s;
    }

    private static String[] percentNames() {
        String[] s = new String[GameSettings.PERCENTS.length];
        for (int i = 0; i < s.length; i++) s[i] = GameSettings.PERCENTS[i] + "%";
        return s;
    }

    private int dp(int v) {
        return Math.round(v * getContext().getResources().getDisplayMetrics().density);
    }
}
