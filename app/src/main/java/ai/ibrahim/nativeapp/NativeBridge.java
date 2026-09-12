package ai.ibrahim.nativeapp;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

public final class NativeBridge {
    private static final int FRESH_CHAT_MAX_ATTEMPTS = 12;
    private static final long FRESH_CHAT_RETRY_MS = 350L;

    private final MainActivity activity;
    private final SecretStore secretStore;
    private final DeviceActionRouter deviceActions;
    private boolean freshChatOpened;
    private int freshChatAttempts;

    public NativeBridge(MainActivity activity) {
        this.activity = activity;
        this.secretStore = new SecretStore(activity);
        this.deviceActions = new DeviceActionRouter(activity);
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
        activity.runOnUiThread(() -> activity.updateNativeStatus("Native cihaz eşleşti · canlı ses hazır"));
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
    public String performAction(String action, String payload) {
        return deviceActions.perform(action, payload);
    }

    @JavascriptInterface
    public String performNaturalLanguage(String text) {
        return deviceActions.performFromNaturalLanguage(text);
    }

    @JavascriptInterface
    public void startConversationMode() {
        activity.runOnUiThread(() -> sendConversationServiceAction(ContinuousConversationService.ACTION_START, null));
    }

    @JavascriptInterface
    public void stopConversationMode() {
        activity.runOnUiThread(() -> sendConversationServiceAction(ContinuousConversationService.ACTION_STOP, null));
    }

    @JavascriptInterface
    public boolean isConversationMode() {
        return activity.getSharedPreferences("native_prefs", Context.MODE_PRIVATE).getBoolean("conversation_mode", false);
    }

    @JavascriptInterface
    public void setVoiceProfile(String profile) {
        String normalized = "aras".equalsIgnoreCase(profile) ? "aras" : "lara";
        activity.getSharedPreferences("native_prefs", Context.MODE_PRIVATE).edit().putString("voice_profile", normalized).apply();
        activity.runOnUiThread(() -> {
            if (isConversationMode()) sendConversationServiceAction(ContinuousConversationService.ACTION_SET_VOICE, normalized);
        });
    }

    @JavascriptInterface
    public String getVoiceProfile() {
        String stored = activity.getSharedPreferences("native_prefs", Context.MODE_PRIVATE).getString("voice_profile", "lara");
        return "aras".equals(stored) ? "aras" : "lara";
    }

    private void sendConversationServiceAction(String action, String profile) {
        Intent intent = new Intent(activity, ContinuousConversationService.class);
        intent.setAction(action);
        if (profile != null) intent.putExtra(ContinuousConversationService.EXTRA_VOICE_PROFILE, profile);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) activity.startForegroundService(intent);
        else activity.startService(intent);
    }

    @JavascriptInterface
    public void setWebReady() {
        activity.runOnUiThread(() -> {
            activity.refreshNativeStatus();
            openFreshChatAfterLaunch();
        });
    }

    private void openFreshChatAfterLaunch() {
        if (freshChatOpened) return;
        WebView webView = findWebView(activity.getWindow().getDecorView());
        if (webView == null) return;

        String url = webView.getUrl();
        if (url == null || !url.startsWith("https://ibrahim-ai-y1xmj0.v2.appdeploy.ai/")) return;

        freshChatAttempts += 1;
        webView.evaluateJavascript(
                "(function(){var b=document.querySelector('.new-chat');if(!b)return 'missing';b.click();return 'opened';})()",
                result -> {
                    if (result != null && result.contains("opened")) {
                        freshChatOpened = true;
                        freshChatAttempts = 0;
                        return;
                    }
                    if (!freshChatOpened && freshChatAttempts < FRESH_CHAT_MAX_ATTEMPTS) {
                        webView.postDelayed(this::openFreshChatAfterLaunch, FRESH_CHAT_RETRY_MS);
                    }
                });
    }

    private WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index += 1) {
            WebView found = findWebView(group.getChildAt(index));
            if (found != null) return found;
        }
        return null;
    }
}
