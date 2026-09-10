package ai.ibrahim.nativeapp;

import android.webkit.JavascriptInterface;

public final class NativeBridge {
    private final MainActivity activity;
    private final SecretStore secretStore;

    public NativeBridge(MainActivity activity) {
        this.activity = activity;
        this.secretStore = new SecretStore(activity);
    }

    @JavascriptInterface
    public boolean needsPairing() {
        return !secretStore.has("device_token");
    }

    @JavascriptInterface
    public void saveDeviceToken(String token, String deviceId) {
        if (token == null || deviceId == null || token.length() < 30 || deviceId.isEmpty()) return;
        secretStore.put("device_token", token);
        secretStore.put("device_id", deviceId);
        activity.runOnUiThread(() -> activity.updateNativeStatus("Native cihaz eşleşti · 7/24 motor hazır"));
    }

    @JavascriptInterface
    public void startBrowserPairing() {
        activity.runOnUiThread(activity::startBrowserPairing);
    }

    @JavascriptInterface
    public void openBrowserApp() {
        activity.runOnUiThread(activity::openBrowserApp);
    }

    @JavascriptInterface
    public void setWebReady() {
        activity.runOnUiThread(activity::refreshNativeStatus);
    }
}
