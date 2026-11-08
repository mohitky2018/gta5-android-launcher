package io.github.playgta5.launcher;

import android.app.GameManager;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.playgta5.launcher.controls.NativeControlsView;
import io.github.playgta5.launcher.engine.NativeEngine;
import io.github.playgta5.launcher.settings.GameSettings;
import io.github.playgta5.launcher.settings.SettingsDialog;
import io.github.playgta5.launcher.utils.GameDirectoryResolver;
import io.github.playgta5.launcher.utils.PathUtils;

/**
 * The game on the native engine: no browser. A SurfaceView for the Vulkan renderer, the touch controls on top. Runs in its own process
 * (":engine", see the manifest): the engine starts once per process, so leaving the game ends the process.
 */
public class NativeGameActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String TAG = "GTA5NativeGame";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GameSettings settings;
    private View overlay;		// NativeControlsView: touch controls that follow the player's situation
    private TextView status;
    private boolean started = false;
    private long lastBack = 0, startedAt = 0;
    private int sessionFps = -1;		// the frame rate limit of this session (settings, or 30 in the system's battery game mode)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        settings = new GameSettings(this);
        if (settings.isSustainedPerformance() && Build.VERSION.SDK_INT >= 24) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean ok = pm != null && pm.isSustainedPerformanceModeSupported();
            if (ok) getWindow().setSustainedPerformanceMode(true);
            Log.i(TAG, "sustained performance mode: " + (ok ? "on" : "not supported on this phone"));
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        SurfaceView surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        root.addView(surface, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        NativeControlsView controls = new NativeControlsView(this, settings);
        controls.setListener(() -> { new SettingsDialog(NativeGameActivity.this, null).show(); Toast.makeText(NativeGameActivity.this, "Settings apply the next time the game starts", Toast.LENGTH_SHORT).show(); });
        controls.setVisibility(settings.isTouchOverlayEnabled() ? View.VISIBLE : View.GONE);
        overlay = controls;
        root.addView(overlay, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        status = new TextView(this);
        status.setTextColor(0xFFFDB813);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setText("Starting the native engine…");
        root.addView(status, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        immersive();

        if (!NativeEngine.isAvailable()) {
            fail("The native engine library could not be loaded:\n" + NativeEngine.loadError());
        }
    }

    private void immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().getDecorView().post(() -> {
                WindowInsetsController c = getWindow().getDecorView().getWindowInsetsController();
                if (c != null) { c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()); c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
            });
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    // ---- surface ----------------------------------------------------------------------------------------------------------------------
    @Override public void surfaceCreated(SurfaceHolder holder) {}

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (!NativeEngine.isAvailable()) return;
        NativeEngine.setSurface(holder.getSurface(), width, height);
        if (!started && width > 0 && height > 0) startEngine(holder, width, height);
        requestFrameRate(holder);
    }

    /** The frame rate limit of this session: the setting, or 30 when the system's game mode is Battery (Android 12+ game dashboard). */
    private int sessionFps() {
        if (sessionFps >= 0) return sessionFps;
        int fps = settings.getFpsLimit();
        if (Build.VERSION.SDK_INT >= 31) {
            GameManager gm = (GameManager) getSystemService(Context.GAME_SERVICE);
            if (gm != null && gm.getGameMode() == GameManager.GAME_MODE_BATTERY && (fps == 0 || fps > 30)) { fps = 30; Log.i(TAG, "battery game mode: 30 FPS"); }
        }
        return sessionFps = fps;
    }

    /** Asks the display for the game's frame rate (Android 11+): 60 on a 120 Hz panel paces evenly and saves power; unlocked asks nothing. */
    private void requestFrameRate(SurfaceHolder holder) {
        int fps = sessionFps();
        if (Build.VERSION.SDK_INT < 30 || fps <= 0 || !holder.getSurface().isValid()) return;
        try { holder.getSurface().setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT); } catch (Throwable t) { Log.w(TAG, "setFrameRate: " + t); }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (NativeEngine.isAvailable()) NativeEngine.setSurface(null, 0, 0);
    }

    private void startEngine(SurfaceHolder holder, int surfaceW, int surfaceH) {
        started = true;
        startedAt = System.currentTimeMillis();
        File base = null;
        String path = settings.getGameDirectoryPath();
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) base = f;
            else try { File r = PathUtils.getFileFromUri(this, Uri.parse(path)); if (r != null && r.exists()) base = r; } catch (Throwable ignored) {}
        }
        if (base == null) base = getExternalFilesDir(null);
        GameDirectoryResolver.ResolvedPaths res = GameDirectoryResolver.resolve(base);
        File data = res.dataDir, web = res.webRootDir;
        File shaders = findShaders(web);
        if (data == null || !new File(data, "manifest.json").exists()) { fail("Game data not found (no manifest.json) in " + data); return; }
        if (shaders == null) { fail("Translated shaders not found in " + web + "/b/<build>/shaders"); return; }

        float scale = settings.getResolutionScale();
        int w = Math.max(320, Math.round(surfaceW * scale / 2f) * 2), h = Math.max(240, Math.round(surfaceH * scale / 2f) * 2);
        List<String> args = engineArgs(w, h);
        int cores = settings.getCores();
        if (cores == 0) cores = 4;		// native threads are cheap (each was a ~20 MB web worker in the browser)
        int mode = GameSettings.MODE_STORY.equals(settings.getGameMode()) ? 1 : GameSettings.MODE_ENV_TEST.equals(settings.getGameMode()) ? 3 : 2;
        File user = new File(getFilesDir(), "engine");
        Log.i(TAG, "engine " + w + "x" + h + " (surface " + surfaceW + "x" + surfaceH + "), cores " + cores + ", args " + args);
        try { NativeEngine.setPerformance(settings.isFastCores(), sessionFps()); NativeEngine.setLanguage(settings.getLanguageTag()); }
        catch (UnsatisfiedLinkError e) { Log.w(TAG, "engine library without setPerformance/setLanguage (older build): " + e); }
        int rc = NativeEngine.start(data.getAbsolutePath(), user.getAbsolutePath(), shaders.getAbsolutePath(), args.toArray(new String[0]), cores, mode, w, h);
        if (rc != 0) { fail("The engine did not start (" + rc + ")"); return; }
        // the window buffers at the render resolution: the display hardware scales them to the screen, so the GPU neither runs a full-screen
        // scaling pass nor writes full-resolution buffers every frame (the next surfaceChanged hands the engine the smaller surface)
        if (settings.isDisplayScaling() && (w != surfaceW || h != surfaceH)) { holder.setFixedSize(w, h); Log.i(TAG, "display scaling: window buffers " + w + "x" + h); }
        handler.postDelayed(this::watch, 500);
    }

    private static File findShaders(File webRoot) {
        if (webRoot == null) return null;
        File[] builds = new File(webRoot, "b").listFiles();
        if (builds == null) return null;
        for (File b : builds) { File s = new File(b, "shaders"); if (new File(s, "index.json").exists()) return s; }
        return null;
    }

    /** The page's engine arguments (index.html) for these settings. */
    private List<String> engineArgs(int w, int h) {
        List<String> a = new ArrayList<>();
        for (String s : new String[]{"-rootdir=/game/", "-wgpu", "-nodisplaycalibration", "-noSocialClub", "-pc:nosocialclub", "-nonetwork", "-output",
                "-width=" + w, "-height=" + h, "-forceResolution", "-Script_all=warning", "-replay_all=warning", "-fastscriptcore"}) a.add(s);
        int fps = sessionFps();
        a.add("-frameLimit=" + (fps == 30 ? 2 : fps == 0 ? 0 : 1));
        if (settings.getCores() == 2) a.add("-numCores=2");
        // Texture quality 0 is the port's browser memory saver (the top mip of every texture skipped on the GPU: blurry); the native engine
        // is not held to the browser's 3 GB, so 1 is the lowest it uses.
        if (settings.isLowProfile()) {
            for (String s : new String[]{"-textureQuality=1", "-pedVariety=0", "-vehicleVariety=0", "-shadowQuality=" + (settings.get(GameSettings.SHADOW) > 0 ? 0 : -1),
                    "-reflectionQuality=0", "-particleQuality=0", "-grassQuality=0", "-cityDensity=0", "-lodScale=0", "-pedLodBias=0", "-vehicleLodBias=0"}) a.add(s);
        } else {
            for (String s : settings.engineArgs()) a.add(s.startsWith("-textureQuality=0") ? "-textureQuality=1" : s);
        }
        NativeEngine.setKeepThreads(1);		// the engine's replay threads run: skipped (as in the browser) the main thread hangs once a replay block fills
        return a;
    }

    private void watch() {
        if (isFinishing()) return;
        String crash = NativeEngine.crashReason();
        if (crash != null) { fail("The game stopped: " + crash); return; }
        long frames = NativeEngine.framesPresented();
        if (frames > 0 && status.getVisibility() == View.VISIBLE) status.setVisibility(View.GONE);
        else if (frames == 0) status.setText(String.format(Locale.US, "Loading the game… (native engine, %d s)", (System.currentTimeMillis() - startedAt) / 1000));
        handler.postDelayed(this::watch, 500);
    }

    private void fail(String message) {
        Log.e(TAG, message);
        status.setVisibility(View.VISIBLE);
        status.setText(message);
        new AlertDialog.Builder(this).setTitle("Native engine").setMessage(message)
                .setPositiveButton("Close", (d, w) -> quit()).setCancelable(false).show();
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------------------------------------
    @Override protected void onResume() { super.onResume(); immersive(); if (NativeEngine.isAvailable() && started) NativeEngine.setFocus(true); }
    @Override protected void onPause() { super.onPause(); if (NativeEngine.isAvailable() && started) NativeEngine.setFocus(false); }

    @Override
    public void onBackPressed() {
        long now = System.currentTimeMillis();
        if (now - lastBack < 2500) {
            new AlertDialog.Builder(this).setTitle("Quit the game?").setMessage("Unsaved progress is lost.")
                    .setPositiveButton("Quit", (d, w) -> quit()).setNegativeButton("Keep playing", null).show();
            return;
        }
        lastBack = now;
        tapKey("Escape");		// the first back opens the pause menu
        Toast.makeText(this, "Press back again to quit", Toast.LENGTH_SHORT).show();
    }

    private void tapKey(String code) {
        NativeEngine.keyCode(code, true);
        handler.postDelayed(() -> NativeEngine.keyCode(code, false), 80);
    }

    private void quit() {
        finishAndRemoveTask();
        handler.postDelayed(() -> Process.killProcess(Process.myPid()), 300);		// the engine cannot be stopped or restarted in this process
    }

    @Override protected void onDestroy() { super.onDestroy(); if (started) Process.killProcess(Process.myPid()); }

    // ---- gamepad ------------------------------------------------------------------------------------------------------------------------
    private float padRx, padRy;
    private boolean padW, padS, padA, padD;
    private final Runnable padLook = new Runnable() {
        @Override public void run() {
            if (Math.abs(padRx) > 0.12f || Math.abs(padRy) > 0.12f) NativeEngine.mouseMove(Math.round(padRx * 14), Math.round(padRy * 10));
            handler.postDelayed(this, 16);
        }
    };
    private boolean padLooking = false;

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK && e.getAction() == MotionEvent.ACTION_MOVE && started) {
            if (overlay != null && settings.isHideWithController()) overlay.setVisibility(View.GONE);
            float lx = e.getAxisValue(MotionEvent.AXIS_X), ly = e.getAxisValue(MotionEvent.AXIS_Y);
            padRx = e.getAxisValue(MotionEvent.AXIS_Z); padRy = e.getAxisValue(MotionEvent.AXIS_RZ);
            boolean w = ly < -0.4f, s = ly > 0.4f, a = lx < -0.4f, d = lx > 0.4f;
            if (w != padW) { padW = w; NativeEngine.keyCode("KeyW", w); }
            if (s != padS) { padS = s; NativeEngine.keyCode("KeyS", s); }
            if (a != padA) { padA = a; NativeEngine.keyCode("KeyA", a); }
            if (d != padD) { padD = d; NativeEngine.keyCode("KeyD", d); }
            NativeEngine.mouseButton(1, e.getAxisValue(MotionEvent.AXIS_LTRIGGER) > 0.5f);
            NativeEngine.mouseButton(0, e.getAxisValue(MotionEvent.AXIS_RTRIGGER) > 0.5f);
            if (!padLooking) { padLooking = true; handler.post(padLook); }
            return true;
        }
        return super.onGenericMotionEvent(e);
    }

    private static String padKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return "Space";
            case KeyEvent.KEYCODE_BUTTON_B: return "ControlLeft";
            case KeyEvent.KEYCODE_BUTTON_X: return "KeyR";
            case KeyEvent.KEYCODE_BUTTON_Y: return "KeyF";
            case KeyEvent.KEYCODE_BUTTON_L1: return "Tab";
            case KeyEvent.KEYCODE_BUTTON_R1: return "KeyQ";
            case KeyEvent.KEYCODE_BUTTON_START: return "Escape";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "KeyV";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "ShiftLeft";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "KeyC";
            case KeyEvent.KEYCODE_DPAD_UP: return "ArrowUp";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "ArrowDown";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "ArrowLeft";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "ArrowRight";
            default: return null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String k = padKey(keyCode);
        if (k != null && started) { if (event.getRepeatCount() == 0) NativeEngine.keyCode(k, true); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        String k = padKey(keyCode);
        if (k != null && started) { NativeEngine.keyCode(k, false); return true; }
        return super.onKeyUp(keyCode, event);
    }
}
