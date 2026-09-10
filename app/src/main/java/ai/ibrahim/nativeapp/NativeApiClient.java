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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class NativeApiClient {
    private static final String BASE_URL = "https://ibrahim-ai-y1xmj0.v2.appdeploy.ai";
    private static final String COMMAND_URL = BASE_URL + "/api/native/command";
    private static final String PAIR_STATUS_URL = BASE_URL + "/api/native/pair/status";

    private final SecretStore secrets;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(String response);
        void onError(String message);
    }

    public interface PairingCallback {
        void onPaired(String token, String deviceId);
        void onPending();
        void onError(String message);
    }

    public NativeApiClient(Context context) {
        secrets = new SecretStore(context);
    }

    public boolean isPaired() {
        return secrets.has("device_token");
    }

    public void pollPairing(String pairSecret, PairingCallback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String encoded = URLEncoder.encode(pairSecret, StandardCharsets.UTF_8.name());
                connection = (HttpURLConnection) new URL(PAIR_STATUS_URL + "?code=" + encoded).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(20000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                String body = readAll(stream);
                if (code >= 200 && code < 300) {
                    JSONObject json = new JSONObject(body);
                    String status = json.optString("status", "pending");
                    if ("pending".equals(status)) {
                        main.post(callback::onPending);
                        return;
                    }
                    if ("expired".equals(status)) {
                        main.post(() -> callback.onError("Eşleştirme süresi doldu. Hesabı yeniden bağla."));
                        return;
                    }
                    if ("invalid".equals(status)) {
                        main.post(() -> callback.onError("Eşleştirme kodu geçersiz. Hesabı yeniden bağla."));
                        return;
                    }
                    if (!"ready".equals(status)) {
                        main.post(() -> callback.onError("Eşleştirme sonucu anlaşılamadı."));
                        return;
                    }
                    JSONObject payload = decryptPairingPayload(pairSecret, json);
                    String token = payload.optString("token", "");
                    String deviceId = payload.optString("deviceId", "");
                    if (token.length() < 30 || deviceId.isEmpty()) throw new IllegalStateException("Invalid pair result");
                    main.post(() -> callback.onPaired(token, deviceId));
                } else if (code == 410) {
                    main.post(() -> callback.onError("Eşleştirme süresi doldu. Hesabı yeniden bağla."));
                } else {
                    main.post(() -> callback.onError("Eşleştirme kontrolü başarısız. Kod: " + code));
                }
            } catch (Exception exception) {
                main.post(() -> callback.onError("Eşleştirme sunucusuna ulaşılamadı veya güvenli paket açılamadı."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private JSONObject decryptPairingPayload(String pairSecret, JSONObject response) throws Exception {
        byte[] encrypted = Base64.getUrlDecoder().decode(response.getString("ciphertext"));
        byte[] iv = Base64.getUrlDecoder().decode(response.getString("iv"));
        byte[] tag = Base64.getUrlDecoder().decode(response.getString("authTag"));
        byte[] combined = new byte[encrypted.length + tag.length];
        System.arraycopy(encrypted, 0, combined, 0, encrypted.length);
        System.arraycopy(tag, 0, combined, encrypted.length, tag.length);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(("ibrahim-native-pair:" + pairSecret).getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] clear = cipher.doFinal(combined);
        return new JSONObject(new String(clear, StandardCharsets.UTF_8));
    }

    public void sendCommand(String command, Callback callback) {
        final String token = secrets.get("device_token");
        if (token.isEmpty()) {
            callback.onError("Cihaz eşleşmesi yok. İbrahim AI uygulamasını açıp hesabını bağla.");
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
                    main.post(() -> callback.onError("Native eşleşmenin süresi doldu. Uygulamayı açıp hesabını yeniden bağla."));
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
