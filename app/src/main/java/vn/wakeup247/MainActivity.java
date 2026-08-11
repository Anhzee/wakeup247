package vn.wakeup247;

import android.Manifest;
import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.Executor;

public class MainActivity extends Activity {
    private static final String[] DURATIONS = {
            "Vô hạn", "1 phút", "5 phút", "15 phút", "30 phút", "1 giờ", "2 giờ", "Tùy chọn"
    };
    private static final long[] DURATION_MS = {
            0, 60_000L, 5 * 60_000L, 15 * 60_000L, 30 * 60_000L, 60 * 60_000L, 2 * 60 * 60_000L, -1
    };

    private LinearLayout root;
    private Spinner durationSpinner;
    private EditText customMinutes;
    private TextView permissionSummary;
    private TextView updateSummary;
    private Button updateButton;
    private String updateUrl;
    private Button startButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF0A0C10);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(30), dp(20), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = label("WakeUp 24/7", 30, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        TextView subtitle = label("Giữ máy thức để ứng dụng tự động hóa tiếp tục nhận và xử lý thông báo.", 15, 0xFFAAB2C0);
        subtitle.setPadding(0, dp(6), 0, dp(22));
        root.addView(subtitle);

        permissionSummary = label("Đang kiểm tra…", 14, 0xFFAAB2C0);
        root.addView(card("Sẵn sàng hoạt động", permissionSummary));

        addSectionTitle("Thời gian treo");
        durationSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, DURATIONS);
        durationSpinner.setAdapter(adapter);
        durationSpinner.setSelection(0);
        durationSpinner.setBackgroundColor(0xFF202631);
        durationSpinner.setPadding(dp(12), 0, dp(12), 0);
        root.addView(durationSpinner, new LinearLayout.LayoutParams(-1, dp(54)));

        customMinutes = new EditText(this);
        customMinutes.setHint("Số phút tùy chọn (ví dụ: 90)");
        customMinutes.setHintTextColor(0xFF6F7887);
        customMinutes.setTextColor(Color.WHITE);
        customMinutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        customMinutes.setPadding(dp(14), dp(8), dp(14), dp(8));
        root.addView(customMinutes, new LinearLayout.LayoutParams(-1, dp(54)));

        addSectionTitle("Bảo vệ & tiết kiệm pin");
        CheckBox resumeBoot = check("Khôi phục phiên treo sau khi khởi động lại",
                AppState.prefs(this).getBoolean(AppState.KEY_RESUME_BOOT, true));
        resumeBoot.setOnCheckedChangeListener((button, checked) -> AppState.prefs(this).edit()
                .putBoolean(AppState.KEY_RESUME_BOOT, checked).apply());
        root.addView(resumeBoot);
        CheckBox lowBattery = check("Tự dừng khi pin ≤ 10% và không sạc",
                AppState.prefs(this).getBoolean(AppState.KEY_LOW_BATTERY_STOP, false));
        lowBattery.setOnCheckedChangeListener((button, checked) -> AppState.prefs(this).edit()
                .putBoolean(AppState.KEY_LOW_BATTERY_STOP, checked).apply());
        root.addView(lowBattery);

        addSectionTitle("Thiết lập một lần");
        Button notify = secondaryButton("Cho phép thông báo hệ thống");
        notify.setOnClickListener(v -> requestNotifications());
        root.addView(notify);
        Button battery = secondaryButton("Tắt tối ưu pin cho WakeUp 24/7");
        battery.setOnClickListener(v -> openBatteryPermission());
        root.addView(battery);
        Button targetApps = secondaryButton("Mở cài đặt pin cho app nhận thông báo");
        targetApps.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        root.addView(targetApps);
        Button tile = secondaryButton("Thêm phím nhanh “Treo máy”");
        tile.setOnClickListener(v -> requestTile());
        root.addView(tile);

        addSectionTitle("Cập nhật ứng dụng");
        LinearLayout updatePanel = new LinearLayout(this);
        updatePanel.setOrientation(LinearLayout.VERTICAL);
        updateSummary = label("Phiên bản hiện tại: " + currentVersion(), 14, 0xFFAAB2C0);
        updatePanel.addView(updateSummary);
        updateButton = secondaryButton("Kiểm tra cập nhật");
        LinearLayout.LayoutParams updateButtonLp = new LinearLayout.LayoutParams(-1, dp(48));
        updateButtonLp.topMargin = dp(10);
        updateButton.setLayoutParams(updateButtonLp);
        updateButton.setOnClickListener(v -> {
            if (updateUrl != null) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl)));
                } catch (RuntimeException error) {
                    Toast.makeText(this, "Không mở được liên kết tải xuống", Toast.LENGTH_LONG).show();
                }
            } else {
                checkForUpdates();
            }
        });
        updatePanel.addView(updateButton);
        root.addView(card("Phiên bản & cập nhật", updatePanel));

        TextView caveat = label("Lưu ý: WakeUp 24/7 giữ CPU và màn hình thức, nhưng bạn vẫn cần đặt ứng dụng nhận thông báo/Telegram thành “Không hạn chế pin” và bật tự khởi động trên Xiaomi, OPPO, vivo…", 13, 0xFF8F99A8);
        caveat.setPadding(dp(4), dp(18), dp(4), dp(18));
        root.addView(caveat);

        startButton = new Button(this);
        startButton.setText("BẮT ĐẦU TREO");
        startButton.setTextSize(16);
        startButton.setTextColor(0xFF07120A);
        startButton.setTypeface(null, android.graphics.Typeface.BOLD);
        startButton.setBackground(round(0xFF78E08F, 16));
        startButton.setOnClickListener(v -> toggleSession());
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(60));
        startLp.topMargin = dp(4);
        root.addView(startButton, startLp);
        setContentView(scroll);
        root.post(this::checkForUpdates);
    }

    private void checkForUpdates() {
        if (updateButton == null || !updateButton.isEnabled()) return;
        updateUrl = null;
        updateButton.setEnabled(false);
        updateButton.setText("ĐANG KIỂM TRA…");
        updateSummary.setText("Đang kết nối GitHub Releases…");
        UpdateChecker.check(currentVersion(), result -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            updateButton.setEnabled(true);
            if (result.error != null) {
                updateSummary.setText("Không kiểm tra được: " + result.error);
                updateSummary.setTextColor(0xFFFFC866);
                updateButton.setText("THỬ LẠI");
                return;
            }
            if (result.updateAvailable) {
                updateUrl = result.downloadUrl != null ? result.downloadUrl : result.releaseUrl;
                updateSummary.setText("Có bản mới " + result.latestVersion
                        + " • đang dùng " + currentVersion());
                updateSummary.setTextColor(0xFF78E08F);
                updateButton.setText("TẢI CẬP NHẬT " + result.latestVersion);
            } else {
                updateSummary.setText("Bạn đang dùng bản mới nhất " + currentVersion());
                updateSummary.setTextColor(0xFF78E08F);
                updateButton.setText("KIỂM TRA LẠI");
            }
        }));
    }

    private String currentVersion() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return version == null ? "?" : version;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "?";
        }
    }

    private View card(String titleText, View body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(0xFF161A22, 18));
        TextView title = label(titleText, 17, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.topMargin = dp(7);
        card.addView(body, bodyLp);
        return card;
    }

    private void addSectionTitle(String value) {
        TextView title = label(value, 14, 0xFF78E08F);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(22), 0, dp(9));
        root.addView(title);
    }

    private CheckBox check(String value, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(value);
        box.setTextColor(0xFFD8DEE8);
        box.setTextSize(14);
        box.setChecked(checked);
        box.setPadding(0, dp(4), 0, dp(4));
        return box;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(0xFFE8ECF2);
        button.setTextSize(14);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setAllCaps(false);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(round(0xFF1A202A, 13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.bottomMargin = dp(8);
        button.setLayoutParams(lp);
        return button;
    }

    private void toggleSession() {
        if (AppState.isActive(this)) {
            startService(new Intent(this, HangService.class).setAction(HangService.ACTION_STOP));
            refreshStatus();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 247);
            Toast.makeText(this, "Hãy cho phép thông báo rồi nhấn bắt đầu lại", Toast.LENGTH_LONG).show();
            return;
        }
        int selected = durationSpinner.getSelectedItemPosition();
        long duration = DURATION_MS[selected];
        if (duration < 0) {
            String value = customMinutes.getText().toString().trim();
            if (value.isEmpty()) {
                customMinutes.setError("Nhập số phút");
                return;
            }
            try {
                duration = Math.multiplyExact(Long.parseLong(value), 60_000L);
            } catch (RuntimeException error) {
                customMinutes.setError("Thời gian không hợp lệ");
                return;
            }
        }
        Intent service = new Intent(this, HangService.class)
                .setAction(HangService.ACTION_START)
                .putExtra(HangService.EXTRA_DURATION_MS, duration);
        startForegroundService(service);
        startActivity(new Intent(this, HangActivity.class));
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 247);
        } else {
            Toast.makeText(this, "Phiên bản Android này đã cho phép sẵn", Toast.LENGTH_SHORT).show();
        }
    }

    private void openBatteryPermission() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "WakeUp 24/7 đã được đặt Không hạn chế pin", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void requestTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            StatusBarManager manager = getSystemService(StatusBarManager.class);
            Executor executor = getMainExecutor();
            manager.requestAddTileService(new ComponentName(this, HangTileService.class),
                    "Treo máy", android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_tile),
                    executor, result -> Toast.makeText(this,
                            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                                    ? "Đã thêm phím nhanh" : "Kiểm tra Trung tâm điều khiển",
                            Toast.LENGTH_SHORT).show());
        } else {
            Toast.makeText(this, "Kéo Trung tâm điều khiển xuống, chọn Sửa và thêm “Treo máy”",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void refreshStatus() {
        if (permissionSummary == null) return;
        boolean notification = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean battery = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        permissionSummary.setText((notification ? "✓" : "!") + " Thông báo hệ thống\n"
                + (battery ? "✓" : "!") + " Không giới hạn pin\n"
                + "• Hãy cài riêng app auto/Telegram thành Không hạn chế");
        permissionSummary.setTextColor(notification && battery ? 0xFF78E08F : 0xFFFFC866);
        boolean active = AppState.isActive(this);
        startButton.setText(active ? "DỪNG PHIÊN TREO" : "BẮT ĐẦU TREO");
        startButton.setBackground(round(active ? 0xFFFF6B6B : 0xFF78E08F, 16));
    }

    private TextView label(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.15f);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
