package ai.ibrahim.nativeapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;

public final class MainActivity extends Activity {
    private static final String APP_URL = "https://ibrahim-ai-y1xmj0.v2.appdeploy.ai/";
    private static final int REQUEST_AUDIO = 2101;
    private static final int REQUEST_NOTIFICATIONS = 2102;
    private static final int REQUEST_PPN = 2103;
    private static final long PAIR_TTL_MS = 10 * 60 * 1000L;

    private WebView webView;
    private TextView statusView;
    private Button startButton;
    private Button stopButton;
    private NativeApiClient apiClient;
    private Handler pairingHandler;
    private boolean pairingCheckInFlight;

    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (preferences, key) -> runOnUiThread(this::refreshNativeStatus);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiClient = new NativeApiClient(this);
        pairingHandler = new Handler(Looper.getMainLooper());
        NotificationHelper.createChannels(this);
        getSharedPreferences("native_prefs", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener);
        buildUi();
        configureWebView();
        webView.loadUrl(APP_URL);

        if (getIntent() != null && getIntent().getBooleanExtra("restart_wake_word", false)) {
            Toast.makeText(this, "Telefon yeniden başladı. Android kuralı nedeniyle 7/24 dinlemeyi bir kez yeniden başlat.", Toast.LENGTH_LONG).show();
        }
        refreshNativeStatus();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(11, 11, 13));

        LinearLayout nativeBar = new LinearLayout(this);
        nativeBar.setOrientation(LinearLayout.VERTICAL);
        nativeBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        nativeBar.setBackgroundColor(Color.rgb(18, 18, 21));

        TextView title = new TextView(this);
        title.setText("İbrahim AI · Native 7/24");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(null, 1);
        nativeBar.addView(title, matchWrap());

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(170, 170, 180));
        statusView.setTextSize(11);
        statusView.setPadding(0, dp(3), 0, dp(6));
        nativeBar.addView(statusView, matchWrap());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        startButton = smallButton("7/24 Başlat");
        stopButton = smallButton("Durdur");
        Button settingsButton = smallButton("Native Ayarlar");
        Button batteryButton = smallButton("Pil");
        startButton.setOnClickListener(v -> startWakeListening());
        stopButton.setOnClickListener(v -> stopWakeListening());
        settingsButton.setOnClickListener(v -> showNativeSettings());
        batteryButton.setOnClickListener(v -> openBatterySettings());
        row.addView(startButton, weightButton(0));
        row.addView(stopButton, weightButton(dp(5)));
        row.addView(settingsButton, weightButton(dp(5)));
        LinearLayout.LayoutParams batteryParams = new LinearLayout.LayoutParams(dp(58), dp(38));
        batteryParams.leftMargin = dp(5);
        row.addView(batteryButton, batteryParams);
        nativeBar.addView(row, matchWrap());

        Button quickAskButton = smallButton("AI'ye Sor · Native Hızlı Komut");
        quickAskButton.setOnClickListener(v -> showQuickAsk());
        LinearLayout.LayoutParams quickAskParams = matchWrap();
        quickAskParams.topMargin = dp(5);
        nativeBar.addView(quickAskButton, quickAskParams);

        root.addView(nativeBar, matchWrap());

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " IbrahimAINative/14.0");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new NativeBridge(this), "IbrahimNative");
        webView.setWebViewClient(new TrustedClient());
    }

    private final class TrustedClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String host = uri.getHost();
            if (host != null && (host.equals("ibrahim-ai-y1xmj0.v2.appdeploy.ai") || host.endsWith(".appdeploy.ai"))) return false;
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

    public void startBrowserPairing() {
        SecretStore secrets = new SecretStore(this);
        if (secrets.has("device_token")) {
            Toast.makeText(this, "Bu cihaz zaten İbrahim AI hesabınla eşleşmiş.", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Google girişini Chrome'da tamamla. Sonra uygulama otomatik eşleşecek.", Toast.LENGTH_LONG).show();
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

    private void showQuickAsk() {
        SecretStore secrets = new SecretStore(this);
        if (!secrets.has("device_token")) {
            Toast.makeText(this, "Önce Google hesabını İbrahim AI ile eşleştirelim.", Toast.LENGTH_LONG).show();
            startBrowserPairing();
            return;
        }

        EditText input = new EditText(this);
        input.setHint("Örn: Bugünkü planımı düzenle veya ASELS'i araştır");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(3);
        input.setMaxLines(7);
        input.setPadding(dp(18), dp(10), dp(18), dp(10));

        new AlertDialog.Builder(this)
                .setTitle("İbrahim AI'ye Sor")
                .setMessage("Bu komut doğrudan hesabına bağlı AI motoruna gider; WebView'de tekrar giriş yapman gerekmez.")
                .setView(input)
                .setPositiveButton("Gönder", (dialog, which) -> sendNativeQuestion(input.getText().toString()))
                .setNegativeButton("Kapat", null)
                .show();
    }

    private void sendNativeQuestion(String question) {
        String clean = question == null ? "" : question.trim();
        if (clean.isEmpty()) {
            Toast.makeText(this, "Önce bir soru veya komut yaz.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (apiClient == null) {
            Toast.makeText(this, "AI bağlantısı hazırlanamadı. Uygulamayı yeniden aç.", Toast.LENGTH_LONG).show();
            return;
        }

        updateNativeStatus("İbrahim AI düşünüyor…");
        apiClient.sendCommand(clean, new NativeApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                refreshNativeStatus();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("İbrahim AI")
                        .setMessage(response)
                        .setPositiveButton("Tamam", null)
                        .setNeutralButton("Tekrar sor", (dialog, which) -> showQuickAsk())
                        .show();
            }

            @Override
            public void onError(String message) {
                refreshNativeStatus();
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
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
            Toast.makeText(this, "Hesap eşleştirme süresi doldu. Yeniden deneyebilirsin.", Toast.LENGTH_LONG).show();
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
                Toast.makeText(MainActivity.this, "Google hesabın İbrahim AI ile eşleşti.", Toast.LENGTH_LONG).show();
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
                if (message.contains("süresi doldu")) clearPendingPairing();
                updateNativeStatus(message);
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

    private void startWakeListening() {
        SecretStore secrets = new SecretStore(this);
        if (!secrets.has("device_token")) {
            Toast.makeText(this, "Önce Google hesabını native uygulamayla eşleştirelim.", Toast.LENGTH_LONG).show();
            startBrowserPairing();
            return;
        }
        if (!secrets.has("picovoice_access_key")) {
            Toast.makeText(this, "Önce Native Ayarlar'dan Picovoice AccessKey gir.", Toast.LENGTH_LONG).show();
            showNativeSettings();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }

        SharedPreferences prefs = getSharedPreferences("native_prefs", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !prefs.getBoolean("notification_permission_prompted", false)) {
            prefs.edit().putBoolean("notification_permission_prompted", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }

        Intent service = new Intent(this, WakeWordService.class).setAction(WakeWordService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service); else startService(service);
        prefs.edit().putBoolean("desired_enabled", true).apply();
        refreshNativeStatus();
    }

    private void stopWakeListening() {
        Intent service = new Intent(this, WakeWordService.class).setAction(WakeWordService.ACTION_STOP);
        startService(service);
        getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putBoolean("desired_enabled", false).apply();
        refreshNativeStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startWakeListening();
        } else if (requestCode == REQUEST_NOTIFICATIONS) {
            startWakeListening();
        }
    }

    private void showNativeSettings() {
        SecretStore secrets = new SecretStore(this);
        SharedPreferences prefs = getSharedPreferences("native_prefs", MODE_PRIVATE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), 0);

        TextView accountLabel = label(secrets.has("device_token")
                ? "Hesap: eşleşti · 7/24 komutlar aynı İbrahim AI hesabını kullanır"
                : "Hesap: eşleşmedi · Google girişi normal tarayıcıda yapılır");
        content.addView(accountLabel, matchWrap());

        Button accountButton = smallButton(secrets.has("device_token") ? "Özel alanımı tarayıcıda aç" : "Google hesabımı bağla");
        accountButton.setOnClickListener(v -> {
            if (new SecretStore(this).has("device_token")) openBrowserApp(); else startBrowserPairing();
        });
        LinearLayout.LayoutParams accountParams = matchWrap();
        accountParams.topMargin = dp(6);
        content.addView(accountButton, accountParams);

        TextView keyLabel = label("Picovoice AccessKey (yalnızca bu telefonda şifreli saklanır)");
        keyLabel.setPadding(0, dp(14), 0, 0);
        content.addView(keyLabel, matchWrap());
        EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setHint(secrets.has("picovoice_access_key") ? "Kayıtlı · değiştirmek için yeni anahtar gir" : "AccessKey");
        content.addView(keyInput, matchWrap());

        TextView sensitivityLabel = label("Wake-word hassasiyeti");
        sensitivityLabel.setPadding(0, dp(10), 0, 0);
        content.addView(sensitivityLabel, matchWrap());
        SeekBar sensitivity = new SeekBar(this);
        sensitivity.setMax(95);
        sensitivity.setProgress(Math.max(5, prefs.getInt("wake_sensitivity", 55)));
        content.addView(sensitivity, matchWrap());

        TextView keyword = label(keywordStatusText());
        keyword.setPadding(0, dp(10), 0, dp(4));
        content.addView(keyword, matchWrap());

        Button choose = smallButton("İbrahim .ppn wake-word dosyasını seç");
        choose.setOnClickListener(v -> pickKeywordFile());
        content.addView(choose, matchWrap());

        Button console = smallButton("Picovoice Console'u aç");
        console.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://console.picovoice.ai/")));
            } catch (Exception ignored) {
            }
        });
        LinearLayout.LayoutParams consoleParams = matchWrap();
        consoleParams.topMargin = dp(6);
        content.addView(console, consoleParams);

        Button clear = smallButton("Özel wake word'ü kaldır · Jarvis'e dön");
        clear.setOnClickListener(v -> {
            prefs.edit().remove("custom_keyword_path").remove("custom_model_path").apply();
            keyword.setText(keywordStatusText());
        });
        LinearLayout.LayoutParams clearParams = matchWrap();
        clearParams.topMargin = dp(6);
        content.addView(clear, clearParams);

        TextView privacy = label("Wake word yerel çalışır. Sunucuya wake-word sesi değil, yalnızca wake word sonrası tanınan komut metni gönderilir.");
        privacy.setPadding(0, dp(12), 0, 0);
        content.addView(privacy, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("Native 7/24 Ayarları")
                .setView(content)
                .setPositiveButton("Kaydet", (dialog, which) -> {
                    String key = keyInput.getText().toString().trim();
                    if (!key.isEmpty()) secrets.put("picovoice_access_key", key);
                    prefs.edit().putInt("wake_sensitivity", Math.max(5, sensitivity.getProgress())).apply();
                    Toast.makeText(this, "Ayarlar kaydedildi. Çalışan servisi durdurup yeniden başlatırsan uygulanır.", Toast.LENGTH_LONG).show();
                    refreshNativeStatus();
                })
                .setNegativeButton("Kapat", null)
                .show();
    }

    private void pickKeywordFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_PPN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PPN || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            File dir = new File(getFilesDir(), "wakeword");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("folder");
            File output = new File(dir, "ibrahim_android.ppn");
            try (InputStream input = getContentResolver().openInputStream(data.getData()); FileOutputStream out = new FileOutputStream(output)) {
                if (input == null) throw new IllegalStateException("input");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) > 0) out.write(buffer, 0, count);
            }
            getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putString("custom_keyword_path", output.getAbsolutePath()).apply();
            Toast.makeText(this, "İbrahim wake-word dosyası kaydedildi. Servisi yeniden başlat.", Toast.LENGTH_LONG).show();
            refreshNativeStatus();
        } catch (Exception exception) {
            Toast.makeText(this, "Wake-word dosyası içe aktarılamadı.", Toast.LENGTH_LONG).show();
        }
    }

    private String keywordStatusText() {
        String path = getSharedPreferences("native_prefs", MODE_PRIVATE).getString("custom_keyword_path", "");
        return path != null && !path.isEmpty() && new File(path).isFile()
                ? "Aktif wake word: İbrahim / özel .ppn"
                : "Aktif wake word: Jarvis (yedek). İbrahim için Picovoice Console'dan Android .ppn oluşturup seç.";
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception exception) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    public void updateNativeStatus(String text) {
        if (statusView != null) statusView.setText(text);
        updateButtons();
    }

    public void refreshNativeStatus() {
        SharedPreferences prefs = getSharedPreferences("native_prefs", MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("desired_enabled", false);
        SecretStore secrets = new SecretStore(this);
        String path = prefs.getString("custom_keyword_path", "");
        String wake = path != null && !path.isEmpty() && new File(path).isFile() ? "İbrahim" : "Jarvis";
        boolean pairing = !secrets.has("device_token") && !prefs.getString("pending_pair_secret", "").isEmpty();
        String accountState = secrets.has("device_token") ? "AI eşleşti" : pairing ? "Google eşleştirmesi sürüyor" : "AI eşleşmesi bekliyor";
        updateNativeStatus((enabled ? "7/24 açık" : "7/24 kapalı") + " · wake: " + wake + " · "
                + (secrets.has("picovoice_access_key") ? "motor hazır" : "AccessKey gerekli") + " · " + accountState);
    }

    private void updateButtons() {
        boolean enabled = getSharedPreferences("native_prefs", MODE_PRIVATE).getBoolean("desired_enabled", false);
        if (startButton != null) startButton.setEnabled(!enabled);
        if (stopButton != null) stopButton.setEnabled(enabled);
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

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(190, 190, 198));
        view.setTextSize(12);
        return view;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(5), 0, dp(5), 0);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightButton(int left) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
        params.leftMargin = left;
        return params;
    }
}
