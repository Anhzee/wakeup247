package vn.wakeup247;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class UpdateChecker {
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/Anhzee/wakeup247/releases/latest";

    interface Callback {
        void onResult(Result result);
    }

    static final class Result {
        final boolean updateAvailable;
        final String latestVersion;
        final String releaseUrl;
        final String downloadUrl;
        final String error;

        private Result(boolean updateAvailable, String latestVersion, String releaseUrl,
                       String downloadUrl, String error) {
            this.updateAvailable = updateAvailable;
            this.latestVersion = latestVersion;
            this.releaseUrl = releaseUrl;
            this.downloadUrl = downloadUrl;
            this.error = error;
        }

        static Result error(String message) {
            return new Result(false, null, null, null, message);
        }
    }

    private UpdateChecker() {}

    static void check(String currentVersion, Callback callback) {
        new Thread(() -> callback.onResult(fetch(currentVersion)), "WakeUp247-UpdateCheck").start();
    }

    private static Result fetch(String currentVersion) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(8_000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", "WakeUp247-Android/" + currentVersion);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                return Result.error(status == 403
                        ? "GitHub tạm giới hạn lượt kiểm tra"
                        : "GitHub trả về mã " + status);
            }
            JSONObject release = new JSONObject(readAll(connection.getInputStream()));
            String tag = release.optString("tag_name", "");
            String latestVersion = normalize(tag);
            if (latestVersion.isEmpty()) return Result.error("Release không có số phiên bản");
            String releaseUrl = release.optString("html_url", null);
            String apkUrl = null;
            JSONArray assets = release.optJSONArray("assets");
            if (assets != null) {
                for (int index = 0; index < assets.length(); index++) {
                    JSONObject asset = assets.optJSONObject(index);
                    if (asset == null) continue;
                    String name = asset.optString("name", "");
                    if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", null);
                        break;
                    }
                }
            }
            return new Result(isNewer(latestVersion, currentVersion), latestVersion,
                    releaseUrl, apkUrl, null);
        } catch (Exception error) {
            return Result.error("hãy kiểm tra kết nối mạng");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        return text.toString();
    }

    private static String normalize(String version) {
        if (version == null) return "";
        String clean = version.trim();
        return clean.startsWith("v") || clean.startsWith("V") ? clean.substring(1) : clean;
    }

    static boolean isNewer(String latest, String current) {
        String[] left = normalize(latest).split("[^0-9]+");
        String[] right = normalize(current).split("[^0-9]+");
        int length = Math.max(left.length, right.length);
        for (int index = 0; index < length; index++) {
            long a = numberAt(left, index);
            long b = numberAt(right, index);
            if (a != b) return a > b;
        }
        return false;
    }

    private static long numberAt(String[] parts, int index) {
        if (index >= parts.length || parts[index].isEmpty()) return 0;
        try {
            return Long.parseLong(parts[index]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
