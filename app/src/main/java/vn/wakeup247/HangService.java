package vn.wakeup247;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.util.Locale;

public class HangService extends Service {
    private static final String TAG = "WakeUp247";
    static final String ACTION_START = "vn.wakeup247.START";
    static final String ACTION_ENSURE = "vn.wakeup247.ENSURE";
    static final String ACTION_STOP = "vn.wakeup247.STOP";
    static final String ACTION_ADD_30 = "vn.wakeup247.ADD_30";
    static final String EXTRA_DURATION_MS = "duration_ms";
    static final String EXTRA_GUARD_ACTIVITY_VISIBLE = "guard_activity_visible";
    static final String CHANNEL_ID = "hang_session";
    static final int NOTIFICATION_ID = 247;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private WindowManager windowManager;
    private View blockerOverlay;
    private GuardView guardOverlay;
    private View exitOverlay;
    private long guardMissingSince;
    private long lastGuardRestoreAt;
    private boolean guardActivityVisible;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) || !AppState.isActive(context)) return;
            // During an explicit hang session the power key should return to the guarded,
            // black screen instead of leaving the device in Doze. This wake lock is short;
            // HangActivity's FLAG_KEEP_SCREEN_ON takes over as soon as the display returns.
            handler.postDelayed(() -> {
                if (!AppState.isActive(HangService.this)) return;
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                @SuppressWarnings("deprecation")
                PowerManager.WakeLock screenWake = pm.newWakeLock(
                        PowerManager.SCREEN_DIM_WAKE_LOCK
                                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                                | PowerManager.ON_AFTER_RELEASE,
                        "WakeUp247:RestoreGuardScreen");
                screenWake.acquire(3_000L);
            }, 650L);
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!AppState.isActive(HangService.this)) {
                stopSession();
                return;
            }
            long endAt = AppState.endAt(HangService.this);
            if (endAt > 0 && System.currentTimeMillis() >= endAt) {
                stopSession();
                return;
            }
            if (AppState.prefs(HangService.this).getBoolean(AppState.KEY_LOW_BATTERY_STOP, false)
                    && batteryPercent() <= 10 && !isCharging()) {
                stopSession();
                return;
            }
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.notify(NOTIFICATION_ID, buildNotification(endAt));
            handler.postDelayed(this, 30_000L);
        }
    };

    private final Runnable keyguardWatcher = new Runnable() {
        @Override public void run() {
            if (!AppState.isActive(HangService.this)) return;
            if (HangActivity.isGuardResumed()) {
                guardMissingSince = 0L;
            } else {
                long now = android.os.SystemClock.elapsedRealtime();
                if (guardMissingSince == 0L) guardMissingSince = now;
                // Notification shade is a SystemUI window and normally leaves the
                // activity resumed. A paused activity here means Home, Recents, an
                // assistant, or another activity actually displaced the guard.
                if (now - guardMissingSince >= 150L && now - lastGuardRestoreAt >= 500L) {
                    lastGuardRestoreAt = now;
                    Log.i(TAG, "Restoring displaced guard activity");
                    Intent restore = new Intent(HangService.this, HangActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    try {
                        startActivity(restore);
                    } catch (RuntimeException error) {
                        Log.w(TAG, "Guard restore rejected", error);
                    }
                }
            }
            handler.postDelayed(this, 100L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSession();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            AppState.setActive(this, false, 0L);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_ADD_30.equals(action)) {
            long current = AppState.endAt(this);
            long base = Math.max(System.currentTimeMillis(), current);
            AppState.setActive(this, true, base + 30 * 60_000L);
        } else if (ACTION_START.equals(action)) {
            long duration = intent.getLongExtra(EXTRA_DURATION_MS, 0L);
            long endAt = duration > 0 ? System.currentTimeMillis() + duration : 0L;
            AppState.setActive(this, true, endAt);
        } else if (!ACTION_ENSURE.equals(action) && intent != null) {
            stopSelf();
            return START_NOT_STICKY;
        } else if (!AppState.isActive(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && intent.hasExtra(EXTRA_GUARD_ACTIVITY_VISIBLE)) {
            guardActivityVisible = intent.getBooleanExtra(EXTRA_GUARD_ACTIVITY_VISIBLE, false);
        }

        acquireWakeLock();
        startForeground(NOTIFICATION_ID, buildNotification(AppState.endAt(this)));
        if (guardActivityVisible) {
            // HangActivity itself supplies the black guard and exit slider. Keeping
            // TYPE_APPLICATION_OVERLAY here would cover expanded chat bubbles,
            // whose content is an application task embedded by System UI.
            hideGuardOverlay();
        } else if (!showGuardOverlay()) {
            stopSession();
            return START_NOT_STICKY;
        }
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        handler.removeCallbacks(keyguardWatcher);
        handler.post(keyguardWatcher);
        requestTileUpdate();
        return START_STICKY;
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeUp247:AutomationSession");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private boolean showGuardOverlay() {
        if (blockerOverlay != null && guardOverlay != null && exitOverlay != null) return true;
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            int backgroundFlags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

            // HyperOS caps a non-touchable overlay's obscuring alpha at 0.8.
            // Two same-UID layers combine above Android's pass-through threshold,
            // blocking hidden app touches while leaving trusted System UI gestures above them.
            blockerOverlay = new View(this);
            blockerOverlay.setBackgroundColor(android.graphics.Color.BLACK);
            WindowManager.LayoutParams blockerParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    backgroundFlags,
                    PixelFormat.OPAQUE);
            blockerParams.gravity = Gravity.TOP | Gravity.START;
            blockerParams.alpha = 0.8f;
            windowManager.addView(blockerOverlay, blockerParams);

            guardOverlay = new GuardView(this);
            WindowManager.LayoutParams backgroundParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    backgroundFlags,
                    PixelFormat.OPAQUE);
            backgroundParams.gravity = Gravity.TOP | Gravity.START;
            backgroundParams.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            int dim = AppState.prefs(this).getInt(AppState.KEY_DIM_LEVEL, 1);
            backgroundParams.screenBrightness = dim == 0 ? 0.01f : dim == 1 ? 0.03f : 0.08f;
            backgroundParams.alpha = 0.8f;
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                backgroundParams.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            windowManager.addView(guardOverlay, backgroundParams);

            exitOverlay = GuardView.createExitOverlay(this, this::stopSession);
            WindowManager.LayoutParams exitParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    GuardView.exitOverlayHeight(this),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            exitParams.gravity = Gravity.BOTTOM | Gravity.START;
            windowManager.addView(exitOverlay, exitParams);
            return true;
        } catch (RuntimeException error) {
            hideGuardOverlay();
            return false;
        }
    }

    private void hideGuardOverlay() {
        if (windowManager == null) return;
        if (exitOverlay != null) {
            try {
                windowManager.removeViewImmediate(exitOverlay);
            } catch (RuntimeException ignored) {
                // The system may already have removed it after permission revocation.
            }
            exitOverlay = null;
        }
        if (guardOverlay == null) return;
        try {
            windowManager.removeViewImmediate(guardOverlay);
        } catch (RuntimeException ignored) {
            // The system may already have removed it after permission revocation.
        }
        guardOverlay = null;
        if (blockerOverlay != null) {
            try {
                windowManager.removeViewImmediate(blockerOverlay);
            } catch (RuntimeException ignored) {
                // The system may already have removed it after permission revocation.
            }
            blockerOverlay = null;
        }
    }

    private Notification buildNotification(long endAt) {
        Intent open = new Intent(this, HangActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopPi = PendingIntent.getService(this, 2,
                new Intent(this, HangService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent addPi = PendingIntent.getService(this, 3,
                new Intent(this, HangService.class).setAction(ACTION_ADD_30),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = endAt == 0 ? "Đang treo vô hạn • chạm để mở màn hình đen"
                : "Còn " + remainingText(endAt) + " • chạm để mở";
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle("WakeUp 24/7 đang hoạt động")
                .setContentText(text)
                .setColor(Color.rgb(120, 224, 143))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(null, "+30 phút", addPi).build())
                .addAction(new Notification.Action.Builder(null, "Dừng", stopPi).build())
                .build();
    }

    private String remainingText(long endAt) {
        long min = Math.max(1, (endAt - System.currentTimeMillis() + 59_999) / 60_000);
        if (min < 60) return min + " phút";
        return String.format(Locale.getDefault(), "%d giờ %02d phút", min / 60, min % 60);
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Phiên treo máy", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Hiển thị trạng thái khi WakeUp 24/7 đang giữ máy hoạt động");
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private int batteryPercent() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private boolean isCharging() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private void stopSession() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(keyguardWatcher);
        hideGuardOverlay();
        AppState.setActive(this, false, 0L);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        requestTileUpdate();
        sendBroadcast(new Intent("vn.wakeup247.SESSION_STOPPED").setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void requestTileUpdate() {
        TileService.requestListeningState(this,
                new android.content.ComponentName(this, HangTileService.class));
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(keyguardWatcher);
        hideGuardOverlay();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        unregisterReceiver(screenReceiver);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
