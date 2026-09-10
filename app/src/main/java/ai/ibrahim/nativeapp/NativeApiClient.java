package ai.ibrahim.nativeapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

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

public final class NativeApiClient {
    private static final String COMMAND_URL = "https://ibrahim-ai-y1xmj0.v2.appdeploy.ai/api/native/command";
    private final SecretStore secrets;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(String response);
        void onError(String message);
    }

    public NativeApiClient(Context context) {
        secrets = new SecretStore(context);
    }

    public boolean isPaired() {
        return secrets.has("device_token");
    }

    public void sendCommand(String command, Callback callback) {
        final String token = secrets.get("device_token");
        if (token.isEmpty()) {
            callback.onError("Cihaz eşleşmesi yok. İbrahim AI uygulamasını açıp hesabına bir kez giriş yap.");
            return;
        }
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(COMMAND_URL).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(90000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Device " + token);
                connection.setDoOutput(true);
                JSONObject payload = new JSONObject();
                payload.put("text", command);
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                String body = readAll(stream);
                if (code >= 200 && code < 300) {
                    String response = new JSONObject(body).optString("response", "");
                    if (response.isEmpty()) throw new IllegalStateException("Empty AI response");
                    main.post(() -> callback.onSuccess(response));
                } else if (code == 401) {
                    secrets.remove("device_token");
                    secrets.remove("device_id");
                    main.post(() -> callback.onError("Native eşleşmenin süresi doldu. Uygulamayı açıp tekrar giriş yap."));
                } else {
                    main.post(() -> callback.onError("İbrahim AI sunucusu şu anda yanıt veremedi. Kod: " + code));
                }
            } catch (Exception exception) {
                main.post(() -> callback.onError("Bağlantı kurulamadı. İnterneti kontrol et."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }
}
