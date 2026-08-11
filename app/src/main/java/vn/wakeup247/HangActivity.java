package vn.wakeup247;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

public class HangActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stateWatcher = new Runnable() {
        @Override public void run() {
            if (!AppState.isActive(HangActivity.this)) {
                finishAndRemoveTask();
                return;
            }
            handler.postDelayed(this, 500L);
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
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        setContentView(new GuardView(this, this::stopSession));
        hideSystemBars();
    }

    private void stopSession() {
        startService(new Intent(this, HangService.class).setAction(HangService.ACTION_STOP));
        finishAndRemoveTask();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!AppState.isActive(this)) {
            startForegroundService(new Intent(this, HangService.class)
                    .setAction(HangService.ACTION_START)
                    .putExtra(HangService.EXTRA_DURATION_MS, 0L));
        }
        hideSystemBars();
        handler.post(stateWatcher);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(stateWatcher);
        super.onPause();
    }

    @Override public void onBackPressed() {
        // The guarded slide is the only intentional way to leave a hang session.
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
}
