package ai.ibrahim.nativeapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class DeveloperActivity extends Activity {
    private NativeApiClient apiClient;
    private DeveloperAgent developerAgent;
    private EditText requestInput;
    private TextView statusView;
    private Button buildButton;
    private Button previewButton;
    private DeveloperAgent.ProjectResult lastProject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiClient = new NativeApiClient(this);
        developerAgent = new DeveloperAgent(this, apiClient);
        buildUi();
        refreshAccountState();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(10, 10, 12));

        TextView title = new TextView(this);
        title.setText("İbrahim AI · Geliştirici Ajanı");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(null, 1);
        root.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText("Nasıl bir oyun istediğini yaz. İbrahim AI planı, HTML'i, görünümü ve JavaScript oyun motorunu kendi oluşturup telefonda oynanabilir proje haline getirir.");
        description.setTextColor(Color.rgb(182, 182, 192));
        description.setTextSize(13);
        description.setPadding(0, dp(8), 0, dp(12));
        root.addView(description, matchWrap());

        Button engineButton = button("AI Motoru / API Anahtarı");
        engineButton.setOnClickListener(v -> showAiEngineSettings());
        root.addView(engineButton, matchWrap());

        requestInput = new EditText(this);
        requestInput.setHint("Örn: Seafight tarzı basit bir deniz savaşı oyunu yap. Joystick ile gemi hareket etsin, düşman gemileri ve can sistemi olsun.");
        requestInput.setTextColor(Color.WHITE);
        requestInput.setHintTextColor(Color.rgb(120, 120, 130));
        requestInput.setBackgroundColor(Color.rgb(24, 24, 29));
        requestInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        requestInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        requestInput.setMinLines(5);
        requestInput.setMaxLines(10);
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.topMargin = dp(10);
        root.addView(requestInput, inputParams);

        buildButton = button("Oyunu Yap");
        buildButton.setOnClickListener(v -> buildGame());
        LinearLayout.LayoutParams buildParams = matchWrap();
        buildParams.topMargin = dp(12);
        root.addView(buildButton, buildParams);

        previewButton = button("Son Oyunu Aç");
        previewButton.setEnabled(false);
        previewButton.setOnClickListener(v -> {
            if (lastProject != null) showPreview(lastProject);
        });
        LinearLayout.LayoutParams previewParams = matchWrap();
        previewParams.topMargin = dp(6);
        root.addView(previewButton, previewParams);

        Button mainAppButton = button("Ana İbrahim AI'yi Aç");
        mainAppButton.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        LinearLayout.LayoutParams mainParams = matchWrap();
        mainParams.topMargin = dp(6);
        root.addView(mainAppButton, mainParams);

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(160, 210, 175));
        statusView.setTextSize(12);
        statusView.setPadding(0, dp(14), 0, 0);
        root.addView(statusView, matchWrap());

        TextView note = new TextView(this);
        note.setText("V17: Google/native eşleştirmesi çalışırsa onu kullanır. Eşleştirme bozuksa AI Motoru bölümünden kişisel API anahtarı ekleyerek Geliştirici Ajanını bağımsız çalıştırabilirsin. Anahtar yalnızca bu telefonda şifreli saklanır.");
        note.setTextColor(Color.rgb(125, 125, 135));
        note.setTextSize(11);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private void showAiEngineSettings() {
        SecretStore secrets = new SecretStore(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);

        TextView info = new TextView(this);
        info.setText(secrets.has("openai_api_key")
                ? "Doğrudan AI motoru hazır. Yeni anahtar girersen eskisinin üzerine yazılır."
                : "Google eşleştirmesi çalışmadığında Geliştirici Ajanı için doğrudan AI motoru kullanabilirsin.");
        info.setTextColor(Color.rgb(190, 190, 198));
        info.setTextSize(12);
        content.addView(info, matchWrap());

        EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setHint(secrets.has("openai_api_key") ? "API anahtarı kayıtlı" : "OpenAI API anahtarı");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(keyInput, matchWrap());

        Button openKeys = button("API anahtarı sayfasını aç");
        openKeys.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/api-keys")));
            } catch (Exception ignored) {
            }
        });
        LinearLayout.LayoutParams keyPageParams = matchWrap();
        keyPageParams.topMargin = dp(6);
        content.addView(openKeys, keyPageParams);

        Button remove = button("Kayıtlı doğrudan AI anahtarını kaldır");
        remove.setEnabled(secrets.has("openai_api_key"));
        remove.setOnClickListener(v -> {
            secrets.remove("openai_api_key");
            Toast.makeText(this, "Doğrudan AI anahtarı kaldırıldı.", Toast.LENGTH_SHORT).show();
            refreshAccountState();
        });
        LinearLayout.LayoutParams removeParams = matchWrap();
        removeParams.topMargin = dp(6);
        content.addView(remove, removeParams);

        new AlertDialog.Builder(this)
                .setTitle("AI Motoru")
                .setMessage("API anahtarını sohbete gönderme. Buraya doğrudan telefonda gir.")
                .setView(content)
                .setPositiveButton("Kaydet", (dialog, which) -> {
                    String key = keyInput.getText().toString().trim();
                    if (!key.isEmpty()) {
                        secrets.put("openai_api_key", key);
                        Toast.makeText(this, "AI motoru kaydedildi.", Toast.LENGTH_SHORT).show();
                    }
                    refreshAccountState();
                })
                .setNegativeButton("Kapat", null)
                .show();
    }

    private void refreshAccountState() {
        boolean paired = apiClient != null && apiClient.isPaired();
        boolean direct = apiClient != null && apiClient.hasDirectAi();
        buildButton.setEnabled(paired || direct);
        if (paired) {
            statusView.setText("İbrahim AI hesabı eşleşti · oyun üretmeye başlayabilirsin.");
        } else if (direct) {
            statusView.setText("Doğrudan AI motoru hazır · Google eşleştirmesi gerekmiyor.");
        } else {
            statusView.setText("AI motoru bekliyor · Google eşleştirmesini deneyebilir veya yukarıdaki AI Motoru bölümünden API anahtarı ekleyebilirsin.");
        }
    }

    private void buildGame() {
        if (apiClient == null || !apiClient.hasAnyAiEngine()) {
            Toast.makeText(this, "Önce AI Motoru bölümünden bağlantı kur.", Toast.LENGTH_LONG).show();
            refreshAccountState();
            return;
        }
        String request = requestInput.getText().toString().trim();
        if (request.isEmpty()) {
            Toast.makeText(this, "Nasıl bir oyun istediğini yaz.", Toast.LENGTH_SHORT).show();
            return;
        }
        buildButton.setEnabled(false);
        previewButton.setEnabled(false);
        lastProject = null;
        developerAgent.buildGame(request, new DeveloperAgent.Listener() {
            @Override
            public void onProgress(String message) {
                statusView.setText(message);
            }

            @Override
            public void onSuccess(DeveloperAgent.ProjectResult result) {
                lastProject = result;
                buildButton.setEnabled(true);
                previewButton.setEnabled(true);
                statusView.setText("Hazır: " + result.title + "\nProje: " + result.directory.getName());
                showFinishedDialog(result);
            }

            @Override
            public void onError(String message) {
                buildButton.setEnabled(true);
                statusView.setText("Üretim durdu: " + message);
                Toast.makeText(DeveloperActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showFinishedDialog(DeveloperAgent.ProjectResult result) {
        new AlertDialog.Builder(this)
                .setTitle(result.title + " hazır")
                .setMessage("İbrahim AI oyunun dosyalarını oluşturdu. Şimdi oyunu uygulamanın içinde açıp deneyebilirsin.\n\n" + result.specification)
                .setPositiveButton("Oyunu Aç", (dialog, which) -> showPreview(result))
                .setNegativeButton("Kapat", null)
                .show();
    }

    private void showPreview(DeveloperAgent.ProjectResult result) {
        WebView preview = new WebView(this);
        WebSettings settings = preview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        preview.setBackgroundColor(Color.BLACK);
        preview.loadDataWithBaseURL("https://local.ibrahim.ai/", result.playableHtml, "text/html", "UTF-8", null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(result.title)
                .setView(preview)
                .setPositiveButton("Kapat", null)
                .create();
        dialog.setOnDismissListener(ignored -> preview.destroy());
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                dialog.getWindow().setGravity(Gravity.CENTER);
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (buildButton != null) refreshAccountState();
    }

    @Override
    protected void onDestroy() {
        if (apiClient != null) apiClient.shutdown();
        super.onDestroy();
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setMinHeight(dp(46));
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
