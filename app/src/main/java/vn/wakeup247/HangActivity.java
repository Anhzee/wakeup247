package vn.wakeup247;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HangActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView clock;
    private TextView session;
    private boolean lockRequestIssued;
    private boolean lockWasActive;
    private boolean exiting;

    private final Runnable updater = new Runnable() {
        @Override public void run() {
            if (!AppState.isActive(HangActivity.this)) {
                leaveLockTask();
                finishAndRemoveTask();
                return;
            }
            monitorLockTask();
            clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            long endAt = AppState.endAt(HangActivity.this);
            if (endAt == 0) {
                session.setText("ĐANG TREO • VÔ HẠN");
            } else {
                long seconds = Math.max(0, (endAt - System.currentTimeMillis()) / 1000);
                session.setText(String.format(Locale.getDefault(), "CÒN %02d:%02d:%02d",
                        seconds / 3600, (seconds / 60) % 60, seconds % 60));
            }
            handler.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        int dim = AppState.prefs(this).getInt(AppState.KEY_DIM_LEVEL, 1);
        params.screenBrightness = dim == 0 ? 0.01f : dim == 1 ? 0.03f : 0.08f;
        getWindow().setAttributes(params);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        buildScreen();
        enterImmersive();
    }

    private void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setKeepScreenOn(true);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(android.view.Gravity.CENTER);
        FrameLayout.LayoutParams infoLp = new FrameLayout.LayoutParams(-1, -2);
        infoLp.gravity = android.view.Gravity.CENTER;

        clock = text("00:00", 42, 0xFF25292D);
        clock.setGravity(android.view.Gravity.CENTER);
        session = text("ĐANG TREO", 12, 0xFF25412D);
        session.setGravity(android.view.Gravity.CENTER);
        TextView note = text("ĐÃ KHÓA ĐIỀU HƯỚNG\nDùng bong bóng chat/cuộc gọi rồi đóng để quay lại", 12, 0xFF1D2226);
        note.setGravity(android.view.Gravity.CENTER);
        note.setPadding(0, dp(12), 0, 0);
        info.addView(clock, new LinearLayout.LayoutParams(-1, -2));
        info.addView(session, new LinearLayout.LayoutParams(-1, -2));
        info.addView(note, new LinearLayout.LayoutParams(-1, -2));
        root.addView(info, infoLp);

        SlideToExit slider = new SlideToExit(this, () -> {
            exiting = true;
            leaveLockTask();
            startService(new Intent(this, HangService.class).setAction(HangService.ACTION_STOP));
            finishAndRemoveTask();
        });
        FrameLayout.LayoutParams slideLp = new FrameLayout.LayoutParams(-1, dp(82));
        slideLp.gravity = android.view.Gravity.BOTTOM;
        slideLp.setMargins(dp(28), 0, dp(28), dp(38));
        root.addView(slider, slideLp);
        setContentView(root);
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setFontFeatureSettings("tnum");
        return view;
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!AppState.isActive(this)) {
            startForegroundService(new Intent(this, HangService.class)
                    .setAction(HangService.ACTION_START)
                    .putExtra(HangService.EXTRA_DURATION_MS, 0L));
        }
        enterImmersive();
        handler.postDelayed(this::requestLockTaskOnce, 350L);
        handler.post(updater);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(updater);
        super.onPause();
    }

    @Override public void onBackPressed() {
        // Deliberately ignored. The slide control prevents accidental exits.
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Home/Recents should be consumed by screen pinning. Reassert immersive
        // mode if an OEM briefly exposes its navigation gesture UI.
        if (!exiting) handler.postDelayed(this::enterImmersive, 100L);
    }

    private void requestLockTaskOnce() {
        if (exiting || lockRequestIssued || !AppState.isActive(this)) return;
        if (lockTaskState() != ActivityManager.LOCK_TASK_MODE_NONE) {
            lockWasActive = true;
            return;
        }
        lockRequestIssued = true;
        try {
            startLockTask();
        } catch (RuntimeException ignored) {
            // Some OEMs require Screen pinning to be enabled manually in Security settings.
        }
    }

    private void monitorLockTask() {
        int state = lockTaskState();
        if (state != ActivityManager.LOCK_TASK_MODE_NONE) {
            lockWasActive = true;
            return;
        }
        // If the task was unpinned through a system gesture while the session is
        // still active, immediately request pinning again. The guarded slide is
        // the intended way to leave the session.
        if (lockWasActive && !exiting) {
            lockWasActive = false;
            lockRequestIssued = false;
            handler.post(this::requestLockTaskOnce);
        }
    }

    private int lockTaskState() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        return manager.getLockTaskModeState();
    }

    private void leaveLockTask() {
        if (lockTaskState() == ActivityManager.LOCK_TASK_MODE_NONE) return;
        try {
            stopLockTask();
        } catch (RuntimeException ignored) {
            // The OS may already have ended pinning for an emergency/system call.
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class SlideToExit extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Runnable onComplete;
        private float progress = 0f;
        private boolean dragging;

        SlideToExit(Activity context, Runnable onComplete) {
            super(context);
            this.onComplete = onComplete;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            float h = getHeight();
            float radius = h / 2f;
            paint.setColor(0xFF111416);
            canvas.drawRoundRect(0, 0, getWidth(), h, radius, radius, paint);
            float knobR = radius - dp(7);
            float knobX = radius + progress * (getWidth() - 2 * radius);
            paint.setColor(progress > .85f ? 0xFF78E08F : 0xFF242A2E);
            canvas.drawCircle(knobX, radius, knobR, paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(14));
            paint.setFakeBoldText(true);
            paint.setColor(0xFF5D686F);
            canvas.drawText("TRƯỢT ĐỂ DỪNG", getWidth() / 2f, radius + dp(5), paint);
            paint.setColor(0xFFB8C2C8);
            paint.setTextSize(dp(22));
            canvas.drawText("›", knobX, radius + dp(7), paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float radius = getHeight() / 2f;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = event.getX() <= radius * 1.5f;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        progress = Math.max(0f, Math.min(1f,
                                (event.getX() - radius) / Math.max(1f, getWidth() - 2 * radius)));
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging && progress >= .9f) onComplete.run();
                    progress = 0f;
                    dragging = false;
                    invalidate();
                    return true;
                default: return true;
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }

        private float dp(int value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }
}
