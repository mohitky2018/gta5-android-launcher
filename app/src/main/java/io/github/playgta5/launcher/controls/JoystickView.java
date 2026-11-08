package io.github.playgta5.launcher.controls;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

public class JoystickView extends View {

    public interface JoystickListener {
        void onJoystickMoved(float xPercent, float yPercent, int id);
    }

    private JoystickListener listener;
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float centerX;
    private float centerY;
    private float baseRadius;
    private float hatRadius;
    private float currentHatX;
    private float currentHatY;
    private int pointerId = -1;

    public JoystickView(Context context) {
        super(context);
        init();
    }

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public JoystickView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        basePaint.setColor(Color.parseColor("#44FFFFFF"));
        basePaint.setStyle(Paint.Style.FILL);

        strokePaint.setColor(Color.parseColor("#88FDB813"));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(4f);

        hatPaint.setColor(Color.parseColor("#EEFDB813"));
        hatPaint.setStyle(Paint.Style.FILL);
    }

    public void setJoystickListener(JoystickListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        baseRadius = Math.min(w, h) / 3f;
        hatRadius = baseRadius / 2.2f;
        currentHatX = centerX;
        currentHatY = centerY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Base outer ring & fill
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint);
        canvas.drawCircle(centerX, centerY, baseRadius, strokePaint);

        // Thumbstick hat
        canvas.drawCircle(currentHatX, currentHatY, hatRadius, hatPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (pointerId == -1) {
                    pointerId = event.getPointerId(actionIndex);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    updatePosition(event.getX(actionIndex), event.getY(actionIndex));
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (pointerId != -1) {
                    int pointerIndex = event.findPointerIndex(pointerId);
                    if (pointerIndex != -1) {
                        updatePosition(event.getX(pointerIndex), event.getY(pointerIndex));
                        return true;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                int pId = event.getPointerId(actionIndex);
                if (pId == pointerId) {
                    pointerId = -1;
                    resetPosition();
                    return true;
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                pointerId = -1;
                resetPosition();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updatePosition(float touchX, float touchY) {
        float dx = touchX - centerX;
        float dy = touchY - centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance <= baseRadius) {
            currentHatX = touchX;
            currentHatY = touchY;
        } else {
            float ratio = baseRadius / distance;
            currentHatX = centerX + dx * ratio;
            currentHatY = centerY + dy * ratio;
        }

        invalidate();

        if (listener != null) {
            float normX = (currentHatX - centerX) / baseRadius;
            float normY = (currentHatY - centerY) / baseRadius;
            // Deadzone of 10%
            if (Math.abs(normX) < 0.1f) normX = 0f;
            if (Math.abs(normY) < 0.1f) normY = 0f;
            listener.onJoystickMoved(normX, normY, getId());
        }
    }

    private void resetPosition() {
        currentHatX = centerX;
        currentHatY = centerY;
        invalidate();
        if (listener != null) {
            listener.onJoystickMoved(0f, 0f, getId());
        }
    }
}

