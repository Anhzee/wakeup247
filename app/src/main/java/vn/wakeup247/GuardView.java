package vn.wakeup247;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class GuardView extends FrameLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView clock;
    private final TextView session;

    private final Runnable updater = new Runnable() {
        @Override public void run() {
            clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            long endAt = AppState.endAt(getContext());
            if (endAt == 0) {
                session.setText("ĐANG TREO • VÔ HẠN");
            } else {
                long seconds = Math.max(0, (endAt - System.currentTimeMillis()) / 1000);
                session.setText(String.format(Locale.getDefault(), "CÒN %02d:%02d:%02d",
                        seconds / 3600, (seconds / 60) % 60, seconds % 60));
            }
            handler.postDelayed(this, 1_000L);
        }
    };

    GuardView(Context context, Runnable onStop) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setKeepScreenOn(true);
        setFocusable(true);
        setFocusableInTouchMode(true);

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(android.view.Gravity.CENTER);
        LayoutParams infoLp = new LayoutParams(-1, -2);
        infoLp.gravity = android.view.Gravity.CENTER;

        clock = text("00:00", 42, 0xFF25292D);
        clock.setGravity(android.view.Gravity.CENTER);
        session = text("ĐANG TREO", 12, 0xFF25412D);
        session.setGravity(android.view.Gravity.CENTER);
        TextView note = text("KÉO XUỐNG ĐỂ XEM THÔNG BÁO\nTrả lời nhanh và điều khiển nhạc vẫn hoạt động", 12, 0xFF293039);
        note.setGravity(android.view.Gravity.CENTER);
        note.setPadding(0, dp(12), 0, 0);
        info.addView(clock, new LinearLayout.LayoutParams(-1, -2));
        info.addView(session, new LinearLayout.LayoutParams(-1, -2));
        info.addView(note, new LinearLayout.LayoutParams(-1, -2));
        addView(info, infoLp);

        SlideToExit slider = new SlideToExit(context, onStop);
        LayoutParams slideLp = new LayoutParams(-1, dp(82));
        slideLp.gravity = android.view.Gravity.BOTTOM;
        slideLp.setMargins(dp(28), 0, dp(28), dp(38));
        addView(slider, slideLp);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        requestFocus();
        handler.post(updater);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(updater);
        super.onDetachedFromWindow();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_MENU
                || key == KeyEvent.KEYCODE_APP_SWITCH) return true;
        return super.dispatchKeyEvent(event);
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setFontFeatureSettings("tnum");
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class SlideToExit extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Runnable onComplete;
        private float progress;
        private boolean dragging;

        SlideToExit(Context context, Runnable onComplete) {
            super(context);
            this.onComplete = onComplete;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setContentDescription("Trượt sang phải để dừng phiên treo");
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
                    if (dragging && progress >= .9f) {
                        performClick();
                        onComplete.run();
                    }
                    progress = 0f;
                    dragging = false;
                    invalidate();
                    return true;
                default:
                    return true;
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
