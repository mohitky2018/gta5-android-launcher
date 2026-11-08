package io.github.playgta5.launcher.controls;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.view.Choreographer;

import io.github.playgta5.launcher.engine.NativeEngine;

import io.github.playgta5.launcher.R;

public class TouchOverlayView extends FrameLayout {

    public interface OverlayListener {
        void onOpenSettingsRequested();
        void onOpenCheatsRequested();
    }

    private WebView webView;
    private OverlayListener overlayListener;

    private JoystickView leftStick;
    private JoystickView rightStick;

    private View onFootContainer;
    private View inVehicleContainer;
    private View modTrayContainer;

    private boolean isDrivingMode = false;
    private boolean isModTrayOpen = false;

    // Movement state
    private boolean keyW = false;
    private boolean keyS = false;
    private boolean keyA = false;
    private boolean keyD = false;

    public TouchOverlayView(Context context) {
        super(context);
        init(context);
    }

    public TouchOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TouchOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void setWebView(WebView webView) {
        this.webView = webView;
    }

    // ---- native engine mode: input goes to NativeEngine (the engine's input block) instead of a WebView ---------------------------------
    private boolean nativeMode = false;
    private float camSpeed = 1.6f, rightX = 0f, rightY = 0f, fracX = 0f, fracY = 0f;
    private boolean ticking = false;
    private final Choreographer.FrameCallback tick = new Choreographer.FrameCallback() {
        @Override public void doFrame(long t) {
            if (!nativeMode) { ticking = false; return; }
            // right stick: continuous turning while it is deflected (curve for fine aim near the centre)
            if (Math.abs(rightX) > 0.05f || Math.abs(rightY) > 0.05f) {
                float cx = Math.signum(rightX) * (float) Math.pow(Math.abs(rightX), 1.6), cy = Math.signum(rightY) * (float) Math.pow(Math.abs(rightY), 1.6);
                dispatchMouseMove(cx * 9f * camSpeed, cy * 6f * camSpeed);
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public void setNativeMode(boolean on, float cameraSpeed) {
        nativeMode = on;
        camSpeed = cameraSpeed;
        if (on && !ticking) { ticking = true; Choreographer.getInstance().postFrameCallback(tick); }
    }

    public void setOverlayListener(OverlayListener listener) {
        this.overlayListener = listener;
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_touch_overlay, this, true);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            setForceDarkAllowed(false);
        }

        leftStick = findViewById(R.id.left_joystick);
        rightStick = findViewById(R.id.right_joystick);

        onFootContainer = findViewById(R.id.container_on_foot);
        inVehicleContainer = findViewById(R.id.container_in_vehicle);
        modTrayContainer = findViewById(R.id.container_mod_tray);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (onFootContainer != null) onFootContainer.setForceDarkAllowed(false);
            if (inVehicleContainer != null) inVehicleContainer.setForceDarkAllowed(false);
            if (modTrayContainer != null) modTrayContainer.setForceDarkAllowed(false);
        }

        setupJoysticks();
        setupOnFootButtons();
        setupDrivingButtons();
        setupSystemButtons();
    }

    private void setupJoysticks() {
        if (leftStick != null) {
            leftStick.setJoystickListener((x, y, id) -> {
                // Left stick: Movement (W, A, S, D)
                boolean nextW = y < -0.3f;
                boolean nextS = y > 0.3f;
                boolean nextA = x < -0.3f;
                boolean nextD = x > 0.3f;

                if (nextW != keyW) { sendKeyEvent("KeyW", "w", nextW); keyW = nextW; }
                if (nextS != keyS) { sendKeyEvent("KeyS", "s", nextS); keyS = nextS; }
                if (nextA != keyA) { sendKeyEvent("KeyA", "a", nextA); keyA = nextA; }
                if (nextD != keyD) { sendKeyEvent("KeyD", "d", nextD); keyD = nextD; }
            });
        }

        if (rightStick != null) {
            rightStick.setJoystickListener((x, y, id) -> {
                if (nativeMode) { rightX = x; rightY = y; return; }		// turned every frame by the ticker
                // Right stick: Camera look (dispatch mouse move or look axis)
                if (Math.abs(x) > 0.05f || Math.abs(y) > 0.05f) {
                    dispatchMouseMove(x * 15f, y * 15f);
                }
            });
        }
    }

    private void setupOnFootButtons() {
        bindButton(R.id.btn_fire, "Mouse0", "Attack", true);
        bindButton(R.id.btn_aim, "Mouse2", "Aim", true);
        bindButton(R.id.btn_sprint, "ShiftLeft", "Shift", false);
        bindButton(R.id.btn_jump, "Space", " ", false);
        bindButton(R.id.btn_crouch, "ControlLeft", "Control", false);
        bindButton(R.id.btn_enter_exit, "KeyF", "f", false);
        bindButton(R.id.btn_reload, "KeyR", "r", false);
        bindButton(R.id.btn_cover, "KeyQ", "q", false);
        bindButton(R.id.btn_weapons, "Tab", "Tab", false);
        bindButton(R.id.btn_phone, "ArrowUp", "ArrowUp", false);
        bindButton(R.id.btn_radio, "KeyQ", "q", false);
        bindButton(R.id.btn_special, "CapsLock", "CapsLock", false);

        // Toggle driving mode when enter/exit is clicked
        View enterExit = findViewById(R.id.btn_enter_exit);
        if (enterExit != null) {
            enterExit.setOnLongClickListener(v -> {
                setDrivingMode(!isDrivingMode);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                return true;
            });
        }
    }

    private void setupDrivingButtons() {
        bindButton(R.id.btn_gas, "KeyW", "w", false);
        bindButton(R.id.btn_brake, "KeyS", "s", false);
        bindButton(R.id.btn_handbrake, "Space", " ", false);
        bindButton(R.id.btn_steer_left, "KeyA", "a", false);
        bindButton(R.id.btn_steer_right, "KeyD", "d", false);
        bindButton(R.id.btn_horn, "KeyE", "e", false);
        bindButton(R.id.btn_lights, "KeyH", "h", false);
        bindButton(R.id.btn_camera, "KeyV", "v", false);
        bindButton(R.id.btn_vehicle_exit, "KeyF", "f", false);

        View vehExit = findViewById(R.id.btn_vehicle_exit);
        if (vehExit != null) {
            vehExit.setOnClickListener(v -> setDrivingMode(false));
        }
    }

    private void setupSystemButtons() {
        // Cheats button
        View btnCheats = findViewById(R.id.btn_quick_cheats);
        if (btnCheats != null) {
            btnCheats.setOnClickListener(v -> {
                if (overlayListener != null) overlayListener.onOpenCheatsRequested();
            });
        }

        // Settings button
        View btnSettings = findViewById(R.id.btn_quick_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                if (overlayListener != null) overlayListener.onOpenSettingsRequested();
            });
        }

        // Mod Tray Toggle
        View btnModToggle = findViewById(R.id.btn_mod_tray_toggle);
        if (btnModToggle != null) {
            btnModToggle.setOnClickListener(v -> {
                isModTrayOpen = !isModTrayOpen;
                if (modTrayContainer != null) {
                    modTrayContainer.setVisibility(isModTrayOpen ? View.VISIBLE : View.GONE);
                }
            });
        }

        // Mod buttons
        bindButton(R.id.btn_script_f4, "F4", "F4", false);
        bindButton(R.id.btn_script_enter, "Numpad5", "5", false);
        bindButton(R.id.btn_script_back, "Numpad0", "0", false);
        bindButton(R.id.btn_script_up, "Numpad8", "8", false);
        bindButton(R.id.btn_script_down, "Numpad2", "2", false);
    }

    public void setDrivingMode(boolean driving) {
        this.isDrivingMode = driving;
        if (onFootContainer != null) onFootContainer.setVisibility(driving ? View.GONE : View.VISIBLE);
        if (inVehicleContainer != null) inVehicleContainer.setVisibility(driving ? View.VISIBLE : View.GONE);
    }

    private void bindButton(int viewId, String keyCode, String keyName, boolean isMouse) {
        View btn = findViewById(viewId);
        if (btn == null) return;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            btn.setForceDarkAllowed(false);
        }

        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (isMouse) {
                        sendMouseEvent(keyCode, true);
                    } else {
                        sendKeyEvent(keyCode, keyName, true);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    if (isMouse) {
                        sendMouseEvent(keyCode, false);
                    } else {
                        sendKeyEvent(keyCode, keyName, false);
                    }
                    return true;
            }
            return false;
        });
    }

    public void sendKeyEvent(String code, String key, boolean down) {
        if (nativeMode) { NativeEngine.keyCode(code, down); return; }
        if (webView == null) return;
        String type = down ? "keydown" : "keyup";
        String js = String.format(
                "window.dispatchEvent(new KeyboardEvent('%s', {code: '%s', key: '%s', bubbles: true}));",
                type, code, key
        );
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    public void sendMouseEvent(String button, boolean down) {
        if (nativeMode) { NativeEngine.mouseButton("Mouse2".equals(button) ? 1 : 0, down); return; }
        if (webView == null) return;
        int btnNum = "Mouse2".equals(button) ? 2 : 0;
        String type = down ? "mousedown" : "mouseup";
        String js = String.format(
                "window.dispatchEvent(new MouseEvent('%s', {button: %d, buttons: %d, bubbles: true}));",
                type, btnNum, down ? (btnNum == 2 ? 2 : 1) : 0
        );
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    public void dispatchMouseMove(float dx, float dy) {
        if (nativeMode) {
            fracX += dx; fracY += dy;
            int ix = (int) fracX, iy = (int) fracY;
            fracX -= ix; fracY -= iy;
            if (ix != 0 || iy != 0) NativeEngine.mouseMove(ix, iy);
            return;
        }
        if (webView == null) return;
        String js = String.format(
                "window.dispatchEvent(new MouseEvent('mousemove', {movementX: %f, movementY: %f, bubbles: true}));",
                dx, dy
        );
        webView.post(() -> webView.evaluateJavascript(js, null));
    }
}

