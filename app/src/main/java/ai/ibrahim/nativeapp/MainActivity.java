package ai.ibrahim.nativeapp;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.security.SecureRandom;

public final class MainActivity extends Activity {
    private static final String APP_URL = "https://ibrahim-ai-y1xmj0.v2.appdeploy.ai/";
    private static final int REQUEST_AUDIO = 2101;
    private static final int REQUEST_FILE_CHOOSER = 2104;
    private static final long PAIR_TTL_MS = 10 * 60 * 1000L;

    private WebView webView;
    private NativeApiClient apiClient;
    private Handler pairingHandler;
    private boolean pairingCheckInFlight;
    private ValueCallback<Uri[]> pendingFileChooser;

    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (preferences, key) -> runOnUiThread(this::refreshNativeStatus);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiClient = new NativeApiClient(this);
        pairingHandler = new Handler(Looper.getMainLooper());
        NotificationHelper.createChannels(this);
        getSharedPreferences("native_prefs", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener);

        buildCleanAppShell();
        configureWebView();
        webView.loadUrl(APP_URL);

        if (getIntent() != null && getIntent().getBooleanExtra("restart_wake_word", false)) {
            Toast.makeText(this, "7/24 ses motorunu yeniden başlatmak için NOXARA içindeki Android ayarlarını açabilirsin.", Toast.LENGTH_LONG).show();
        }
        refreshNativeStatus();
    }

    private void buildCleanAppShell() {
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF080808);
        setContentView(webView);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(settings.getUserAgentString() + " NOXARANative/21.0");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);
        WebView.setWebContentsDebuggingEnabled(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new NativeBridge(this), "IbrahimNative");
        webView.setWebViewClient(new TrustedClient());
        webView.setWebChromeClient(new NativeChromeClient());
    }

    private final class TrustedClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String host = uri.getHost();
            if (host != null && (host.equals("ibrahim-ai-y1xmj0.v2.appdeploy.ai") || host.endsWith(".appdeploy.ai"))) {
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            refreshNativeStatus();
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            Toast.makeText(MainActivity.this, "Güvenli bağlantı doğrulanamadı.", Toast.LENGTH_LONG).show();
        }
    }

    private final class NativeChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = filePathCallback;
            try {
                Intent chooser = fileChooserParams.createIntent();
                startActivityForResult(chooser, REQUEST_FILE_CHOOSER);
                return true;
            } catch (ActivityNotFoundException exception) {
                pendingFileChooser = null;
                Toast.makeText(MainActivity.this, "Dosya seçici açılamadı.", Toast.LENGTH_LONG).show();
                return false;
            }
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> {
                if (request == null) return;
                boolean asksAudio = false;
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                        asksAudio = true;
                        break;
                    }
                }
                if (asksAudio && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else if (asksAudio) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
                    request.deny();
                    Toast.makeText(MainActivity.this, "Mikrofon iznini verdikten sonra ses düğmesine tekrar dokun.", Toast.LENGTH_SHORT).show();
                } else {
                    request.deny();
                }
            });
        }
    }

    public void startBrowserPairing() {
        SecretStore secrets = new SecretStore(this);
        if (secrets.has("device_token")) {
            refreshNativeStatus();
            return;
        }

        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String pairSecret = Base64.encodeToString(random, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        getSharedPreferences("native_prefs", MODE_PRIVATE)
                .edit()
                .putString("pending_pair_secret", pairSecret)
                .putLong("pending_pair_started_at", System.currentTimeMillis())
                .apply();

        Uri pairingUrl = Uri.parse(APP_URL).buildUpon().appendQueryParameter("native_pair", pairSecret).build();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, pairingUrl));
            Toast.makeText(this, "Google girişini tarayıcıda tamamla; NOXARA uygulaması otomatik eşleşecek.", Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "Tarayıcı açılamadı.", Toast.LENGTH_LONG).show();
        }
        refreshNativeStatus();
    }

    public void openBrowserApp() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(APP_URL)));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "Tarayıcı açılamadı.", Toast.LENGTH_LONG).show();
        }
    }

    private void checkPendingPairing() {
        if (apiClient == null || pairingCheckInFlight) return;
        SecretStore secrets = new SecretStore(this);
        SharedPreferences prefs = getSharedPreferences("native_prefs", MODE_PRIVATE);
        if (secrets.has("device_token")) {
            clearPendingPairing();
            refreshNativeStatus();
            return;
        }

        String pairSecret = prefs.getString("pending_pair_secret", "");
        long startedAt = prefs.getLong("pending_pair_started_at", 0L);
        if (pairSecret == null || pairSecret.isEmpty()) return;
        if (startedAt <= 0L || System.currentTimeMillis() - startedAt > PAIR_TTL_MS) {
            clearPendingPairing();
            return;
        }

        pairingCheckInFlight = true;
        updateNativeStatus("Hesap eşleştirmesi kontrol ediliyor…");
        apiClient.pollPairing(pairSecret, new NativeApiClient.PairingCallback() {
            @Override
            public void onPaired(String token, String deviceId) {
                pairingCheckInFlight = false;
                SecretStore store = new SecretStore(MainActivity.this);
                store.put("device_token", token);
                store.put("device_id", deviceId);
                clearPendingPairing();
                Toast.makeText(MainActivity.this, "NOXARA hesabın bu telefonla eşleşti.", Toast.LENGTH_SHORT).show();
                refreshNativeStatus();
                if (webView != null) webView.reload();
            }

            @Override
            public void onPending() {
                pairingCheckInFlight = false;
                refreshNativeStatus();
                pairingHandler.postDelayed(MainActivity.this::checkPendingPairing, 1500L);
            }

            @Override
            public void onError(String message) {
                pairingCheckInFlight = false;
                if (message != null && message.contains("süresi doldu")) clearPendingPairing();
                updateNativeStatus(message == null ? "Eşleştirme kontrol edilemedi." : message);
            }
        });
    }

    private void clearPendingPairing() {
        getSharedPreferences("native_prefs", MODE_PRIVATE)
                .edit()
                .remove("pending_pair_secret")
                .remove("pending_pair_started_at")
                .apply();
    }

    public void updateNativeStatus(String text) {
        getSharedPreferences("native_prefs", MODE_PRIVATE).edit()
                .putString("native_status", text == null ? "" : text)
                .apply();
    }

    public void refreshNativeStatus() {
        SharedPreferences prefs = getSharedPreferences("native_prefs", MODE_PRIVATE);
        SecretStore secrets = new SecretStore(this);
        boolean enabled = prefs.getBoolean("desired_enabled", false);
        boolean pairing = !secrets.has("device_token") && !prefs.getString("pending_pair_secret", "").isEmpty();
        String accountState = secrets.has("device_token") ? "hesap bağlı" : pairing ? "eşleştiriliyor" : "hesap bekliyor";
        updateNativeStatus((enabled ? "7/24 açık" : "7/24 kapalı") + " · " + accountState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FILE_CHOOSER) return;
        ValueCallback<Uri[]> callback = pendingFileChooser;
        pendingFileChooser = null;
        if (callback == null) return;
        callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri data = intent != null ? intent.getData() : null;
        if (data != null && "ibrahimai".equals(data.getScheme()) && "paired".equals(data.getHost())) {
            pairingHandler.postDelayed(this::checkPendingPairing, 250L);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNativeStatus();
        pairingHandler.postDelayed(this::checkPendingPairing, 300L);
    }

    @Override
    protected void onDestroy() {
        getSharedPreferences("native_prefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(listener);
        if (pairingHandler != null) pairingHandler.removeCallbacksAndMessages(null);
        if (apiClient != null) apiClient.shutdown();
        if (pendingFileChooser != null) {
            pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("IbrahimNative");
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
