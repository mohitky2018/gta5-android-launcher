package io.github.playgta5.launcher.controls;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.playgta5.launcher.engine.NativeEngine;
import io.github.playgta5.launcher.settings.GameSettings;

/**
 * Touch controls of the native engine that follow what the player is doing, as the iOS app's TouchControlsView does: on foot, car, motorbike,
 * bicycle, boat, submarine, helicopter and plane each get their own buttons and stick, the phone gets a phone dock, and the pause menu gets
 * menu navigation. The situation comes from the engine (NativeEngine.controlState: the player ped, its vehicle's class, the phone task and
 * the pause menu, read from the game's memory). Keys are the PC defaults (x64/data/control/default.meta).
 */
public class NativeControlsView extends View {

    public interface Listener { void onOpenSettings(); }

    // contexts (NativeEngine.controlState bits 0-3), plus the overlays
    static final int C_NONE = 0, C_FOOT = 1, C_CAR = 2, C_BIKE = 3, C_CYCLE = 4, C_BOAT = 5, C_SUB = 6, C_HELI = 7, C_PLANE = 8, C_PHONE = 20, C_MENU = 21;

    private static final int HOLD = 0, LATCH = 1, MOUSE_L = 2, MOUSE_R = 3, WHEEL_UP = 4, WHEEL_DOWN = 5, ACTION = 6;

    private static final class Btn {
        String icon, key; int kind; float x, y, size; boolean look;		// x: >0 from the left, <0 from the right (in view heights); y from the top
        float cx, cy, r; int pointer = -1; boolean latched; Runnable action;
    }

    private final GameSettings settings;
    private Listener listener;
    private final List<Btn> buttons = new ArrayList<>();
    private final Map<String, Bitmap> icons = new HashMap<>();
    private final Map<String, Integer> held = new HashMap<>();		// key -> holders (a stick and a button may hold the same key)
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG), bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();

    private int context = -1, rawState = 0;
    private float alpha;
    private final float camSpeed, sizePref;
    private final boolean invertY, fireAims, steerButtons, haptics;

    // floating stick
    private int stickPointer = -1; private float stickOX, stickOY, stickX, stickY; private final String[] stickKeys = new String[4];
    // camera swipe
    private int lookPointer = -1; private float lookX, lookY, fracX, fracY;
    private final Map<Integer, float[]> buttonLook = new HashMap<>();		// pointers on buttons that also turn the camera (fire, aim)

    private int frame = 0;
    private final Choreographer.FrameCallback tick = new Choreographer.FrameCallback() {
        @Override public void doFrame(long t) {
            if (++frame % 6 == 0) poll();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public NativeControlsView(Context ctx, GameSettings settings) {
        super(ctx);
        this.settings = settings;
        camSpeed = settings.getCameraSpeed() / 10f;
        sizePref = settings.getButtonSize() / 100f;
        alpha = settings.getButtonOpacity() / 100f;
        invertY = settings.isInvertY();
        fireAims = settings.isFireAlsoAims();
        steerButtons = settings.isSteeringButtons();
        haptics = settings.isHapticsEnabled();
        ring.setStyle(Paint.Style.STROKE);
        setContext(C_MENU);
    }

    public void setListener(Listener l) { listener = l; }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); Choreographer.getInstance().postFrameCallback(tick); }
    @Override protected void onDetachedFromWindow() { Choreographer.getInstance().removeFrameCallback(tick); releaseAll(); super.onDetachedFromWindow(); }

    // ---- the player's situation ---------------------------------------------------------------------------------------------------------
    private void poll() {
        int st = NativeEngine.isAvailable() ? NativeEngine.controlState() : 0;
        if (st == rawState && context >= 0) return;
        rawState = st;
        boolean player = (st & (1 << 10)) != 0, phone = (st & (1 << 8)) != 0, menu = (st & (1 << 9)) != 0;
        int c = menu || !player ? C_MENU : phone ? C_PHONE : (st & 15);
        if (c == C_NONE) c = C_FOOT;
        if (c != context) setContext(c);
    }

    private int vehicleUnderPhone() { int c = rawState & 15; return c == 0 ? C_FOOT : c; }

    private void setContext(int c) {
        releaseAll();
        context = c;
        buttons.clear();
        // always: pause, settings
        add("pause", "Escape", HOLD, 0.08f, 0.09f, 0.75f);
        Btn s = add("settings", null, ACTION, -0.08f, 0.09f, 0.75f); s.action = () -> { if (listener != null) listener.onOpenSettings(); };
        switch (c) {
            case C_FOOT:
                add("switch_character", "AltLeft", HOLD, 0.21f, 0.09f, 0.75f);
                add("phone", "ArrowUp", HOLD, -0.21f, 0.09f, 0.75f);
                add("camera", "KeyV", HOLD, -0.34f, 0.09f, 0.75f);
                add("special_ability", "CapsLock", HOLD, -0.47f, 0.09f, 0.75f);
                add("fire", "", MOUSE_L, -0.17f, 0.72f, 1.35f).look = true;
                add("aim", "", MOUSE_R, -0.17f, 0.42f, 1f).look = true;
                add("jump", "Space", HOLD, -0.40f, 0.86f, 1f);
                add("sprint", "ShiftLeft", settings.isSprintButton() ? LATCH : HOLD, -0.38f, 0.62f, 1f);
                add("reload", "KeyR", HOLD, -0.38f, 0.38f, 0.9f);
                add("enter_exit", "KeyF", HOLD, -0.58f, 0.48f, 1f);
                add("cover", "KeyQ", HOLD, -0.60f, 0.72f, 0.9f);
                add("crouch", "ControlLeft", HOLD, -0.62f, 0.93f, 0.75f);
                add("weapons", "Tab", HOLD, -0.30f, 0.22f, 0.85f);
                add("interact", "KeyE", HOLD, -0.47f, 0.22f, 0.75f);
                add("weapon_next", "", WHEEL_UP, -0.60f, 0.22f, 0.7f);
                add("detonate", "KeyB", HOLD, -0.73f, 0.22f, 0.7f);
                break;
            case C_CAR: case C_BOAT: case C_BIKE:
                vehicleTop(c != C_BIKE);
                add("gas", "KeyW", HOLD, -0.17f, 0.72f, 1.35f);
                add("brake", "KeyS", HOLD, -0.40f, 0.86f, 1f);
                add("handbrake", "Space", HOLD, -0.38f, 0.60f, 1f);
                add("horn", "KeyG", HOLD, -0.17f, 0.42f, 0.9f);
                add("enter_exit", "KeyF", HOLD, -0.58f, 0.46f, 1f);
                add("look_back", "KeyC", HOLD, -0.38f, 0.38f, 0.8f);
                add("fire", "", MOUSE_L, -0.60f, 0.70f, 0.9f).look = true;
                add("aim", "", MOUSE_R, -0.62f, 0.92f, 0.75f).look = true;
                add("radio", "KeyQ", HOLD, -0.30f, 0.22f, 0.8f);
                if (c == C_CAR) { add("roof", "KeyE", HOLD, -0.47f, 0.22f, 0.75f); add("duck", "KeyX", HOLD, -0.60f, 0.22f, 0.75f); }
                if (steerButtons) { add("steer_left", "KeyA", HOLD, 0.13f, 0.78f, 1.1f); add("steer_right", "KeyD", HOLD, 0.36f, 0.78f, 1.1f); }
                break;
            case C_CYCLE:
                vehicleTop(false);
                add("gas", "KeyW", HOLD, -0.17f, 0.72f, 1.35f);
                add("sprint", "CapsLock", HOLD, -0.38f, 0.60f, 1f);
                add("brake", "KeyS", HOLD, -0.40f, 0.86f, 1f);
                add("handbrake", "KeyE", HOLD, -0.17f, 0.42f, 0.9f);		// front brake
                add("enter_exit", "KeyF", HOLD, -0.58f, 0.46f, 1f);
                add("look_back", "KeyC", HOLD, -0.38f, 0.38f, 0.8f);
                add("fire", "", MOUSE_L, -0.60f, 0.70f, 0.9f).look = true;
                break;
            case C_SUB:
                vehicleTop(true);
                add("gas", "KeyW", HOLD, -0.17f, 0.72f, 1.35f);
                add("brake", "KeyS", HOLD, -0.40f, 0.86f, 1f);
                add("dive", "ControlLeft", HOLD, -0.38f, 0.60f, 1f);
                add("surface", "ShiftLeft", HOLD, -0.17f, 0.42f, 1f);
                add("enter_exit", "KeyF", HOLD, -0.58f, 0.46f, 1f);
                add("look_back", "KeyC", HOLD, -0.38f, 0.38f, 0.8f);
                break;
            case C_HELI: case C_PLANE:
                vehicleTop(false);
                add("throttle_up", "KeyW", HOLD, -0.16f, 0.52f, 1.15f);
                add("throttle_down", "KeyS", HOLD, -0.16f, 0.84f, 1.15f);
                add("yaw_right", "KeyD", HOLD, -0.38f, 0.86f, 1f);
                add("yaw_left", "KeyA", HOLD, -0.60f, 0.86f, 1f);
                add("fire", "Space", HOLD, -0.38f, 0.60f, 1f);
                add("enter_exit", "KeyF", HOLD, -0.58f, 0.46f, 1f);
                add("look_back", "KeyC", HOLD, -0.38f, 0.36f, 0.8f);
                add("vehicle_weapon", "Tab", HOLD, -0.30f, 0.22f, 0.8f);
                if (c == C_PLANE) add("landing_gear", "KeyG", HOLD, -0.47f, 0.22f, 0.8f);
                break;
            case C_PHONE:
                add("phone", "Backspace", HOLD, -0.21f, 0.09f, 0.75f);		// put it away
                add("up", "ArrowUp", HOLD, -0.40f, 0.42f, 1f);
                add("down", "ArrowDown", HOLD, -0.40f, 0.82f, 1f);
                add("left", "ArrowLeft", HOLD, -0.60f, 0.62f, 1f);
                add("right", "ArrowRight", HOLD, -0.20f, 0.62f, 1f);
                add("accept", "Enter", HOLD, -0.14f, 0.88f, 1.05f);
                add("back", "Backspace", HOLD, -0.14f, 0.36f, 0.95f);
                add("phone_option", "Delete", HOLD, -0.72f, 0.88f, 0.8f);
                add("prompt_option", "Space", HOLD, -0.72f, 0.40f, 0.8f);
                break;
            case C_MENU: default:
                add("up", "ArrowUp", HOLD, 0.30f, 0.44f, 1f);
                add("down", "ArrowDown", HOLD, 0.30f, 0.82f, 1f);
                add("left", "ArrowLeft", HOLD, 0.11f, 0.63f, 1f);
                add("right", "ArrowRight", HOLD, 0.49f, 0.63f, 1f);
                add("accept", "Enter", HOLD, -0.16f, 0.78f, 1.15f);
                add("back", "Backspace", HOLD, -0.38f, 0.86f, 1f);
                add("tab_left", "KeyQ", HOLD, 0.21f, 0.09f, 0.75f);
                add("tab_right", "KeyE", HOLD, -0.21f, 0.09f, 0.75f);
                add("phone_option", "Space", HOLD, -0.16f, 0.48f, 0.85f);
                break;
        }
        layoutButtons();
        invalidate();
    }

    private void vehicleTop(boolean lights) {
        add("phone", "ArrowUp", HOLD, -0.21f, 0.09f, 0.75f);
        add("camera", "KeyV", HOLD, -0.34f, 0.09f, 0.75f);
        if (lights) add("lights", "KeyH", HOLD, -0.47f, 0.09f, 0.75f);
    }

    private Btn add(String icon, String key, int kind, float x, float y, float size) {
        Btn b = new Btn(); b.icon = icon; b.key = key; b.kind = kind; b.x = x; b.y = y; b.size = size;
        buttons.add(b);
        return b;
    }

    private void layoutButtons() {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        for (Btn b : buttons) {
            b.cx = b.x >= 0 ? b.x * h : w + b.x * h;
            b.cy = b.y * h;
            b.r = 0.068f * h * sizePref * b.size;
        }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); icons.clear(); layoutButtons(); }

    // ---- keys -------------------------------------------------------------------------------------------------------------------------------
    private void press(String key) {
        if (key == null || key.isEmpty()) return;
        Integer n = held.get(key);
        held.put(key, n == null ? 1 : n + 1);
        if (n == null || n == 0) NativeEngine.keyCode(key, true);
    }
    private void release(String key) {
        if (key == null || key.isEmpty()) return;
        Integer n = held.get(key);
        if (n == null || n == 0) return;
        if (n == 1) { held.remove(key); NativeEngine.keyCode(key, false); } else held.put(key, n - 1);
    }
    private void releaseAll() {
        for (String k : held.keySet()) NativeEngine.keyCode(k, false);
        held.clear();
        if (NativeEngine.isAvailable()) { NativeEngine.mouseButton(0, false); NativeEngine.mouseButton(1, false); }
        for (int i = 0; i < 4; i++) stickKeys[i] = null;
        for (Btn b : buttons) { b.pointer = -1; b.latched = false; }
        stickPointer = -1; lookPointer = -1; buttonLook.clear();
    }

    private void buttonDown(Btn b) {
        if (haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        switch (b.kind) {
            case HOLD: press(b.key); break;
            case LATCH: b.latched = !b.latched; if (b.latched) press(b.key); else release(b.key); break;
            case MOUSE_L: NativeEngine.mouseButton(0, true); if (fireAims && context == C_FOOT) NativeEngine.mouseButton(1, true); break;
            case MOUSE_R: NativeEngine.mouseButton(1, true); break;
            case WHEEL_UP: NativeEngine.wheel(1); break;
            case WHEEL_DOWN: NativeEngine.wheel(-1); break;
            case ACTION: if (b.action != null) b.action.run(); break;
        }
    }
    private void buttonUp(Btn b) {
        switch (b.kind) {
            case HOLD: release(b.key); break;
            case MOUSE_L: NativeEngine.mouseButton(0, false); if (fireAims && context == C_FOOT && !isPressed("aim")) NativeEngine.mouseButton(1, false); break;
            case MOUSE_R: NativeEngine.mouseButton(1, false); break;
            default: break;
        }
    }
    private boolean isPressed(String icon) { for (Btn b : buttons) if (b.icon.equals(icon) && b.pointer >= 0) return true; return false; }

    // the stick's four directions for the context: up, down, left, right
    private String[] stickMap() {
        switch (context) {
            case C_FOOT: return new String[] { "KeyW", "KeyS", "KeyA", "KeyD" };
            case C_CAR: case C_BOAT: return new String[] { null, null, "KeyA", "KeyD" };
            case C_BIKE: case C_CYCLE: return new String[] { "ShiftLeft", "ControlLeft", "KeyA", "KeyD" };		// lean forward / back
            case C_SUB: return new String[] { "Numpad8", "Numpad2", "KeyA", "KeyD" };
            case C_HELI: case C_PLANE: return new String[] { "Numpad8", "Numpad2", "Numpad4", "Numpad6" };		// pitch, roll
            case C_PHONE: { int v = vehicleUnderPhone(); return v == C_FOOT ? new String[] { "KeyW", "KeyS", "KeyA", "KeyD" } : new String[] { null, null, "KeyA", "KeyD" }; }
            default: return new String[] { null, null, null, null };
        }
    }
    private void updateStick(float dx, float dy) {
        String[] m = stickMap();
        boolean[] want = { dy < -0.35f, dy > 0.35f, dx < -0.35f, dx > 0.35f };
        for (int i = 0; i < 4; i++) {
            String k = want[i] ? m[i] : null;
            if (k == null ? stickKeys[i] == null : k.equals(stickKeys[i])) continue;
            release(stickKeys[i]); stickKeys[i] = k; press(k);
        }
    }
    private void endStick() { for (int i = 0; i < 4; i++) { release(stickKeys[i]); stickKeys[i] = null; } stickPointer = -1; stickX = stickY = 0; }

    private void look(float dx, float dy) {
        float k = 0.32f * camSpeed;
        fracX += dx * k; fracY += (invertY ? -dy : dy) * k;
        int ix = (int) fracX, iy = (int) fracY;
        fracX -= ix; fracY -= iy;
        if (ix != 0 || iy != 0) NativeEngine.mouseMove(ix, iy);
    }

    // ---- touch --------------------------------------------------------------------------------------------------------------------------
    private Btn hit(float x, float y) {
        Btn best = null; float bd = Float.MAX_VALUE;
        for (Btn b : buttons) {
            float dx = x - b.cx, dy = y - b.cy, d = dx * dx + dy * dy, rr = b.r * 1.25f;
            if (d < rr * rr && d < bd) { best = b; bd = d; }
        }
        return best;
    }
    private boolean stickAllowed() { return context != C_MENU && !(steerButtons && (context == C_CAR || context == C_BOAT)); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            int i = e.getActionIndex(), id = e.getPointerId(i);
            float x = e.getX(i), y = e.getY(i);
            Btn b = hit(x, y);
            if (b != null && b.pointer < 0) {
                b.pointer = id; buttonDown(b);
                if (b.look) buttonLook.put(id, new float[] { x, y });
            } else if (b == null && x < getWidth() * 0.42f && stickPointer < 0 && stickAllowed()) {
                stickPointer = id; stickOX = x; stickOY = y; stickX = stickY = 0;
            } else if (b == null && lookPointer < 0) {
                lookPointer = id; lookX = x; lookY = y;
            }
            invalidate();
        } else if (a == MotionEvent.ACTION_MOVE) {
            float R = 0.16f * getHeight();
            for (int i = 0; i < e.getPointerCount(); i++) {
                int id = e.getPointerId(i); float x = e.getX(i), y = e.getY(i);
                if (id == stickPointer) {
                    float dx = (x - stickOX) / R, dy = (y - stickOY) / R, len = (float) Math.hypot(dx, dy);
                    if (len > 1) { stickOX += (x - stickOX) * (1 - 1 / len) ; stickOY += (y - stickOY) * (1 - 1 / len); dx /= len; dy /= len; }		// the base follows the finger
                    stickX = dx; stickY = dy; updateStick(dx, dy);
                } else if (id == lookPointer) {
                    look(x - lookX, y - lookY); lookX = x; lookY = y;
                } else {
                    float[] p = buttonLook.get(id);
                    if (p != null) { look(x - p[0], y - p[1]); p[0] = x; p[1] = y; }
                }
            }
            invalidate();
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP || a == MotionEvent.ACTION_CANCEL) {
            if (a == MotionEvent.ACTION_CANCEL) { releaseAll(); invalidate(); return true; }
            int id = e.getPointerId(e.getActionIndex());
            if (id == stickPointer) endStick();
            if (id == lookPointer) lookPointer = -1;
            buttonLook.remove(id);
            for (Btn b : buttons) if (b.pointer == id) { b.pointer = -1; buttonUp(b); }
            invalidate();
        }
        return true;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------------------------
    private Bitmap icon(String name, int size) {
        String k = name + "@" + size;
        Bitmap b = icons.get(k);
        if (b != null || icons.containsKey(k)) return b;
        try (InputStream in = getContext().getAssets().open("touch/buttons/icon_" + name + ".png")) {
            Bitmap raw = BitmapFactory.decodeStream(in);
            b = raw == null ? null : Bitmap.createScaledBitmap(raw, size, size, true);
        } catch (Exception ex) { b = null; }
        icons.put(k, b);
        return b;
    }

    @Override protected void onDraw(Canvas c) {
        int a = (int) (255 * alpha);
        for (Btn b : buttons) {
            boolean on = b.pointer >= 0 || b.latched;
            fill.setColor(on ? 0x66FDB813 : 0x55000000); fill.setAlpha((int) ((on ? 0.55f : 0.40f) * a));
            c.drawCircle(b.cx, b.cy, b.r, fill);
            ring.setColor(on ? 0xFFFDB813 : 0xFFFFFFFF); ring.setAlpha((int) ((on ? 0.95f : 0.45f) * a)); ring.setStrokeWidth(Math.max(2f, b.r * 0.05f));
            c.drawCircle(b.cx, b.cy, b.r, ring);
            int s = (int) (b.r * 1.15f);
            Bitmap bmp = icon(b.icon, Math.max(8, s));
            if (bmp != null) { bmpPaint.setAlpha(a); rect.set(b.cx - s / 2f, b.cy - s / 2f, b.cx + s / 2f, b.cy + s / 2f); c.drawBitmap(bmp, null, rect, bmpPaint); }
        }
        if (stickPointer >= 0) {
            float R = 0.16f * getHeight();
            fill.setColor(0x33000000); fill.setAlpha((int) (0.35f * a)); c.drawCircle(stickOX, stickOY, R, fill);
            ring.setColor(0xFFFFFFFF); ring.setAlpha((int) (0.5f * a)); ring.setStrokeWidth(3f); c.drawCircle(stickOX, stickOY, R, ring);
            fill.setColor(0xFFFFFFFF); fill.setAlpha((int) (0.55f * a)); c.drawCircle(stickOX + stickX * R, stickOY + stickY * R, R * 0.42f, fill);
        }
    }
}
