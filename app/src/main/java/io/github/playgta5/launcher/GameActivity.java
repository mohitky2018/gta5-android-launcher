package io.github.playgta5.launcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.PowerManager;
import android.view.WindowManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.github.playgta5.launcher.server.GameServerService;
import io.github.playgta5.launcher.settings.GameSettings;

/**
 * Starts the local game server and opens the game in a Chrome Custom Tab.
 *
 * The game cannot run in Android WebView: WebView ignores the COOP/COEP headers, so the page is not cross-origin isolated, SharedArrayBuffer
 * is missing, and the engine (WebAssembly threads) never starts; the page then sits at about 6% and guesses "out of memory". Chrome isolates
 * the page and has WebGPU. The touch controls are part of the page (assets/touch/touch.js, injected by GameServer).
 */
public class GameActivity extends AppCompatActivity {
    private static final String TAG = "GTA5GameActivity";
    private static final String[] CHROME_PACKAGES = {"com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary"};
    private static final String STATE_LAUNCHED = "launched";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private ProgressBar spinner;
    private Button btnOpen;
    private GameSettings settings;
    private boolean launched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        bindViews();
        settings = new GameSettings(this);
        launched = savedInstanceState != null && savedInstanceState.getBoolean(STATE_LAUNCHED);

        // The server's notification (and its Stop button) is only visible with this permission; the server runs either way.
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        if (useNativeEngine()) {		// the native engine needs no server and no browser
            startActivity(new Intent(this, NativeGameActivity.class));
            finish();
            return;
        }
        if (!launched) startServerThenOpen();
        else showReady();
    }

    /** The native engine is used when chosen, or on Automatic when its library is in the app (checked by file: loading it here would map the engine into the launcher's process). */
    private boolean useNativeEngine() {
        int run = settings.getRunIn();
        if (run == GameSettings.RUN_CHROME) return false;
        boolean installed = new java.io.File(getApplicationInfo().nativeLibraryDir, "libgta5engine.so").exists() || nativeLibInApk();
        return run == GameSettings.RUN_APP ? true : installed;
    }

    private boolean nativeLibInApk() {
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(getApplicationInfo().sourceDir)) {
            return z.getEntry("lib/arm64-v8a/libgta5engine.so") != null;
        } catch (Exception e) { return false; }
    }

    private void bindViews() {
        status = findViewById(R.id.loading_status);
        spinner = findViewById(R.id.loading_spinner);
        btnOpen = findViewById(R.id.btn_open_game);
        Button btnStop = findViewById(R.id.btn_stop_game);
        btnOpen.setOnClickListener(v -> startServerThenOpen());
        btnStop.setOnClickListener(v -> {
            GameServerService.stop(this);
            finish();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_LAUNCHED, launched);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (launched && webView == null) showReady();		// back from the game tab
    }

    private void startServerThenOpen() {
        btnOpen.setEnabled(false);
        spinner.setVisibility(View.VISIBLE);
        status.setText("Starting the game server…");
        try {
            GameServerService.start(this);
        } catch (Throwable t) {
            Log.e(TAG, "Could not start the game server service", t);
            showError("Could not start the game server:\n" + t.getMessage());
            return;
        }
        waitForServer(System.currentTimeMillis() + 10_000);
    }

    private void waitForServer(long deadline) {
        String url = GameServerService.getBaseUrl();
        String err = GameServerService.getStartError();
        if (url != null) {
            openGame(url);
        } else if (err != null) {
            showError("The game server failed to start:\n" + err);
        } else if (System.currentTimeMillis() > deadline) {
            showError("The game server did not start within 10 seconds.");
        } else {
            handler.postDelayed(() -> waitForServer(deadline), 100);
        }
    }

    private boolean isPhoneWarm() {
        if (Build.VERSION.SDK_INT < 29) return false;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_LIGHT;
    }

    private void openGame(String baseUrl) {
        boolean warm = isPhoneWarm();
        String url = settings.buildLaunchUrl(baseUrl, warm);
        if (warm && settings.isCoolDown()) Toast.makeText(this, "Phone is warm: 30 FPS and lower resolution this time", Toast.LENGTH_LONG).show();
        if (settings.getRunIn() == GameSettings.RUN_APP && !triedWebView) {
            openInWebView(url);
            return;
        }
        openInChrome(url);
    }

    // "In the app": the WebView, if it is cross-origin isolated (it was not on WebView 158: no SharedArrayBuffer, the engine cannot start).
    // Checked when the page has loaded; otherwise the game moves to Chrome.
    private boolean triedWebView = false;
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    private void openInWebView(String url) {
        triedWebView = true;
        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= 26) webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        webView.setWebViewClient(new WebViewClient() {
            private boolean checked = false;

            @Override
            public void onPageFinished(WebView view, String loaded) {
                if (checked) return;
                checked = true;
                view.evaluateJavascript("self.crossOriginIsolated === true && typeof SharedArrayBuffer === 'function' && !!navigator.gpu", v -> {
                    if ("true".equals(v)) return;		// runs here
                    Log.w(TAG, "WebView is not cross-origin isolated (" + v + "): moving the game to Chrome");
                    Toast.makeText(GameActivity.this, "This phone's WebView cannot run the engine: opening it in Chrome", Toast.LENGTH_LONG).show();
                    closeWebView();
                    openInChrome(url);
                });
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                closeWebView();		// without this the whole app dies with the renderer
                showError("The game stopped (the graphics process " + (detail.didCrash() ? "crashed" : "was killed for memory") + "). Tap Play to start it again.");
                return true;
            }
        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(webView);
        webView.loadUrl(url);
        launched = true;
    }

    private void closeWebView() {
        if (webView == null) return;
        WebView w = webView;
        webView = null;
        setContentView(R.layout.activity_game);
        bindViews();
        w.destroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();		// touch.js keeps a history entry: first back = pause menu, second (within 2.5 s) = quit
            return;
        }
        super.onBackPressed();
    }

    private void openInChrome(String url) {
        String chrome = findChrome();
        if (chrome == null) {
            spinner.setVisibility(View.GONE);
            btnOpen.setEnabled(true);
            status.setText("Google Chrome is required.");
            new AlertDialog.Builder(this)
                    .setTitle("Google Chrome required")
                    .setMessage("The game needs WebGPU and cross-origin isolation, which Android's built-in WebView does not provide. "
                            + "Install or enable Google Chrome, then tap Play.")
                    .setPositiveButton("Open Play Store", (d, w) -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.android.chrome")));
                        } catch (ActivityNotFoundException ignored) {}
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // A Custom Tab without the androidx.browser library: the session extra is what makes Chrome open it as a tab of this task.
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        Bundle extras = new Bundle();
        extras.putBinder("android.support.customtabs.extra.SESSION", null);
        intent.putExtras(extras);
        intent.putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0xFF000000);
        intent.putExtra("androidx.browser.customtabs.extra.SHARE_STATE", 2);		// no share button
        intent.putExtra("android.support.customtabs.extra.ENABLE_URLBAR_HIDING", true);
        intent.setPackage(chrome);
        Log.i(TAG, "Opening game in " + chrome + ": " + url);
        try {
            startActivity(intent);
            launched = true;
        } catch (ActivityNotFoundException e) {
            showError("Chrome could not be opened: " + e.getMessage());
        }
    }

    private String findChrome() {
        PackageManager pm = getPackageManager();
        for (String pkg : CHROME_PACKAGES) {
            try {
                if (pm.getApplicationInfo(pkg, 0).enabled) return pkg;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return null;
    }

    private void showReady() {
        spinner.setVisibility(View.GONE);
        btnOpen.setEnabled(true);
        boolean running = GameServerService.getBaseUrl() != null;
        status.setText(running
                ? "The game opens in Chrome with touch controls. Tap Play to start it again, or stop the server when you are done."
                : "The game server is stopped. Tap Play to start it.");
    }

    private void showError(String message) {
        spinner.setVisibility(View.GONE);
        btnOpen.setEnabled(true);
        status.setText(message);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
