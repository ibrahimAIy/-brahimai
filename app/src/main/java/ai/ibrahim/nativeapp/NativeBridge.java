package ai.ibrahim.nativeapp;

import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

public final class NativeBridge {
    private static final int FRESH_CHAT_MAX_ATTEMPTS = 12;
    private static final long FRESH_CHAT_RETRY_MS = 350L;

    private final MainActivity activity;
    private final SecretStore secretStore;
    private boolean freshChatOpened;
    private int freshChatAttempts;

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
