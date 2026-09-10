package ai.ibrahim.nativeapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NativeApiClient {
    private static final String BASE_URL = "https://ibrahim-ai-y1xmj0.v2.appdeploy.ai";
    private static final String COMMAND_URL = BASE_URL + "/api/native/command";
    private static final String PAIR_RESULT_URL = BASE_URL + "/api/native/pair/result";
    private static final String OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final String DIRECT_MODEL = "gpt-5.6-luna";

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

    public boolean hasDirectAi() {
        return secrets.has("openai_api_key");
    }

    public boolean hasAnyAiEngine() {
        return isPaired() || hasDirectAi();
    }

    public void pollPairing(String pairSecret, PairingCallback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String encoded = URLEncoder.encode(pairSecret, StandardCharsets.UTF_8.name());
                connection = (HttpURLConnection) new URL(PAIR_RESULT_URL + "?pairSecret=" + encoded).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(20000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                String body = readAll(stream);
                if (code == 200) {
                    JSONObject json = new JSONObject(body);
                    String token = json.optString("token", "");
                    String deviceId = json.optString("deviceId", "");
                    if (token.length() < 30 || deviceId.isEmpty()) throw new IllegalStateException("Invalid pair result");
                    main.post(() -> callback.onPaired(token, deviceId));
                } else if (code == 202) {
                    main.post(callback::onPending);
                } else if (code == 410) {
                    main.post(() -> callback.onError("Eşleştirme süresi doldu. Hesabı yeniden bağla."));
                } else {
                    main.post(() -> callback.onError("Eşleştirme kontrolü başarısız. Kod: " + code));
                }
            } catch (Exception exception) {
                main.post(() -> callback.onError("Eşleştirme sunucusuna ulaşılamadı. İnterneti kontrol et."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public void sendCommand(String command, Callback callback) {
        final String token = secrets.get("device_token");
        if (!token.isEmpty()) {
            sendNativeCommand(command, token, callback);
            return;
        }

        final String apiKey = secrets.get("openai_api_key");
        if (!apiKey.isEmpty()) {
            sendDirectOpenAi(command, apiKey, callback);
            return;
        }

        callback.onError("AI motoru bağlı değil. Google eşleştirmesini tamamla veya Geliştirici Ajanı içinden API anahtarı ekle.");
    }

    private void sendNativeCommand(String command, String token, Callback callback) {
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
                writeJson(connection, payload);
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
                    final String apiKey = secrets.get("openai_api_key");
                    if (!apiKey.isEmpty()) {
                        main.post(() -> sendDirectOpenAi(command, apiKey, callback));
                    } else {
                        main.post(() -> callback.onError("Native eşleşme geçersiz. Geliştirici Ajanı içinden doğrudan AI motorunu bağlayabilirsin."));
                    }
                } else {
                    main.post(() -> callback.onError("İbrahim AI sunucusu şu anda yanıt veremedi. Kod: " + code));
                }
            } catch (Exception exception) {
                main.post(() -> callback.onError("İbrahim AI bağlantısı kurulamadı. İnterneti kontrol et."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void sendDirectOpenAi(String command, String apiKey, Callback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(OPENAI_RESPONSES_URL).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(120000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                connection.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("model", DIRECT_MODEL);
                payload.put("input", command);
                payload.put("max_output_tokens", 6000);
                writeJson(connection, payload);

                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                String body = readAll(stream);
                if (code >= 200 && code < 300) {
                    String text = extractResponseText(new JSONObject(body));
                    if (text.isEmpty()) throw new IllegalStateException("Empty direct AI response");
                    main.post(() -> callback.onSuccess(text));
                    return;
                }

                String detail = extractApiError(body);
                if (code == 401) {
                    main.post(() -> callback.onError("API anahtarı kabul edilmedi. AI Motoru ayarından anahtarı kontrol et."));
                } else if (code == 429) {
                    main.post(() -> callback.onError("AI API kullanım/bakiye sınırına ulaştı. API hesabındaki kullanım ve bakiyeyi kontrol et."));
                } else {
                    String suffix = detail.isEmpty() ? "" : " · " + detail;
                    main.post(() -> callback.onError("Doğrudan AI motoru yanıt vermedi. Kod: " + code + suffix));
                }
            } catch (Exception exception) {
                main.post(() -> callback.onError("Doğrudan AI motoruna bağlanılamadı. İnterneti kontrol et."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static String extractResponseText(JSONObject json) {
        StringBuilder out = new StringBuilder();
        JSONArray output = json.optJSONArray("output");
        if (output == null) return "";
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) continue;
            JSONArray content = item.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part == null) continue;
                String type = part.optString("type", "");
                if (!"output_text".equals(type)) continue;
                String text = part.optString("text", "").trim();
                if (!text.isEmpty()) {
                    if (out.length() > 0) out.append('\n');
                    out.append(text);
                }
            }
        }
        return out.toString().trim();
    }

    private static String extractApiError(String body) {
        try {
            JSONObject error = new JSONObject(body).optJSONObject("error");
            if (error == null) return "";
            String message = error.optString("message", "").replaceAll("\\s+", " ").trim();
            return message.length() <= 180 ? message : message.substring(0, 180);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void writeJson(HttpURLConnection connection, JSONObject payload) throws Exception {
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }
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
