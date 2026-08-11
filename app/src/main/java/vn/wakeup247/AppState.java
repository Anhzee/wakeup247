package vn.wakeup247;

import android.content.Context;
import android.content.SharedPreferences;

final class AppState {
    static final String PREFS = "wakeup247";
    static final String KEY_ACTIVE = "active";
    static final String KEY_END_AT = "end_at";
    static final String KEY_STARTED_AT = "started_at";
    static final String KEY_RESUME_BOOT = "resume_boot";
    static final String KEY_LOW_BATTERY_STOP = "low_battery_stop";
    static final String KEY_DIM_LEVEL = "dim_level";

    private AppState() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    static long endAt(Context context) {
        return prefs(context).getLong(KEY_END_AT, 0L);
    }

    static void setActive(Context context, boolean active, long endAt) {
        prefs(context).edit()
                .putBoolean(KEY_ACTIVE, active)
                .putLong(KEY_END_AT, active ? endAt : 0L)
                .putLong(KEY_STARTED_AT, active ? System.currentTimeMillis() : 0L)
                .apply();
    }
}
