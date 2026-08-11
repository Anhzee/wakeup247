package vn.wakeup247;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!AppState.isActive(context)
                || !AppState.prefs(context).getBoolean(AppState.KEY_RESUME_BOOT, true)) return;
        long endAt = AppState.endAt(context);
        if (endAt > 0 && endAt <= System.currentTimeMillis()) {
            AppState.setActive(context, false, 0L);
            return;
        }
        long duration = endAt == 0 ? 0L : endAt - System.currentTimeMillis();
        Intent service = new Intent(context, HangService.class)
                .setAction(HangService.ACTION_START)
                .putExtra(HangService.EXTRA_DURATION_MS, duration);
        context.startForegroundService(service);
    }
}
