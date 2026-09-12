package ai.ibrahim.nativeapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NativeTranscriptionClient {
    private static final String TRANSCRIBE_URL = "https://ibrahim-ai-y1xmj0.v2.appdeploy.ai/api/native/transcribe";

    public interface Callback {
        void onSuccess(String text);
        void onError(String message);
    }

    private final SecretStore secrets;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public NativeTranscriptionClient(Context context) {
        secrets = new SecretStore(context);
    }

    public void transcribeWav(byte[] wavAudio, Callback callback) {
        final String token = secrets.get("device_token");
        if (token.isEmpty()) {
            callback.onError("Native eşleştirme gerekli. NOXARA'yı açıp hesabına bir kez giriş yap.");
            return;
        }
        if (wavAudio == null || wavAudio.length < 1000) {
            callback.onError("Ses çok kısa kaldı. Tekrar söyle.");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(TRANSCRIBE_URL).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(60000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Device " + token);
                connection.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("data", Base64.encodeToString(wavAudio, Base64.NO_WRAP));
                payload.put("mimeType", "audio/wav");
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }

                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                String body = readAll(stream);
                if (code >= 200 && code < 300) {
                    String text = new JSONObject(body).optString("text", "").trim();
                    main.post(() -> callback.onSuccess(text));
                } else if (code == 401) {
                    main.post(() -> callback.onError("Native eşleştirme geçersiz. Uygulamayı açıp hesabına yeniden bağlan."));
                } else {
                    main.post(() -> callback.onError("Ses çözümlenemedi. Kod: " + code));
                }
            } catch (Exception exception) {
                main.post(() -> callback.onError("Ses çözümleme bağlantısı kurulamadı. İnterneti kontrol et."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }
}
