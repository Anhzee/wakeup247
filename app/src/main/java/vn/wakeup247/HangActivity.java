package vn.wakeup247;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

public class HangActivity extends Activity {
    private static final String TAG = "WakeUp247";
    private static volatile boolean guardResumed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean userTriedToLeave;
    private OnBackInvokedCallback backCallback;
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
        Log.i(TAG, "HangActivity onCreate");
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
        if (Build.VERSION.SDK_INT >= 33) {
            // Target-SDK 33+ routes edge-back through OnBackInvokedDispatcher;
            // overriding onBackPressed() alone does not reliably consume it.
            backCallback = () -> Log.i(TAG, "Blocked predictive-back gesture");
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        hideSystemBars();
    }

    private void stopSession() {
        startService(new Intent(this, HangService.class).setAction(HangService.ACTION_STOP));
        finishAndRemoveTask();
    }

    @Override protected void onResume() {
        super.onResume();
        guardResumed = true;
        Log.i(TAG, "HangActivity onResume keyguard=" + isKeyguardShowing());
        userTriedToLeave = false;
        boolean wasActive = AppState.isActive(this);
        startForegroundService(new Intent(this, HangService.class)
                .setAction(wasActive ? HangService.ACTION_ENSURE : HangService.ACTION_START)
                .putExtra(HangService.EXTRA_DURATION_MS, 0L));
        hideSystemBars();
        handler.post(stateWatcher);
    }

    @Override protected void onPause() {
        guardResumed = false;
        Log.i(TAG, "HangActivity onPause keyguard=" + isKeyguardShowing());
        handler.removeCallbacks(stateWatcher);
        // NotificationShade is a SystemUI window and does not pause this Activity.
        // Home, Recents, contextual AI and external activities do, so reclaim the
        // foreground immediately instead of waiting for the service watchdog.
        if (AppState.isActive(this)) {
            handler.postDelayed(this::restoreGuardAboveKeyguard, 80L);
        }
        super.onPause();
    }

    @Override public void onBackPressed() {
        // The guarded slide is the only intentional way to leave a hang session.
        Log.i(TAG, "Blocked legacy-back gesture");
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        Log.i(TAG, "HangActivity onUserLeaveHint keyguard=" + isKeyguardShowing());
        if (!AppState.isActive(this)) return;
        userTriedToLeave = true;
        handler.postDelayed(this::restoreGuardAboveKeyguard, 80L);
    }

    @Override protected void onStop() {
        super.onStop();
        Log.i(TAG, "HangActivity onStop keyguard=" + isKeyguardShowing());
        if (userTriedToLeave && AppState.isActive(this)) {
            handler.postDelayed(this::restoreGuardAboveKeyguard, 80L);
        }
    }

    private boolean isKeyguardShowing() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return keyguard != null && keyguard.isKeyguardLocked();
    }

    private void restoreGuardAboveKeyguard() {
        if (guardResumed || !AppState.isActive(this)) return;
        try {
            ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            manager.moveTaskToFront(getTaskId(), 0);
        } catch (RuntimeException ignored) {
            Intent restore = new Intent(this, HangActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            try {
                startActivity(restore);
            } catch (RuntimeException ignoredAgain) {
                // HyperOS may briefly reject task movement while keyguard animates.
            }
        }
    }

    static boolean isGuardResumed() {
        return guardResumed;
    }

    @Override protected void onDestroy() {
        guardResumed = false;
        handler.removeCallbacks(stateWatcher);
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        Log.i(TAG, "HangActivity onDestroy finishing=" + isFinishing());
        super.onDestroy();
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
