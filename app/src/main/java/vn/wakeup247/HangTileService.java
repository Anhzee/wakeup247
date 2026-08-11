package vn.wakeup247;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class HangTileService extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override public void onClick() {
        super.onClick();
        if (AppState.isActive(this)) {
            startForegroundService(new Intent(this, HangService.class)
                    .setAction(HangService.ACTION_ENSURE));
            launchGuardActivity();
            updateTile();
            return;
        }

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            openActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            openActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            return;
        }

        Intent service = new Intent(this, HangService.class)
                .setAction(HangService.ACTION_START)
                .putExtra(HangService.EXTRA_DURATION_MS, 0L);
        startForegroundService(service);

        launchGuardActivity();
        updateTile();
    }

    private void launchGuardActivity() {
        Intent activity = new Intent(this, HangActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (isLocked()) {
            // Android's TileService contract recommends direct startActivity for
            // safe activities that intentionally display above the keyguard.
            startActivity(activity);
            // Direct start is required above the keyguard, while this second call
            // asks System UI to collapse the still-open Quick Settings panel.
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> openActivity(activity), 150L);
        } else {
            openActivity(activity);
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private void openActivity(Intent activity) {
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pi = PendingIntent.getActivity(this, 8, activity,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(activity);
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean active = AppState.isActive(this);
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(active ? "Mở màn hình treo" : "Treo máy");
        if (Build.VERSION.SDK_INT >= 29) {
            tile.setSubtitle(active ? "Chạm để mở lại" : "Vô hạn");
        }
        tile.updateTile();
    }
}
