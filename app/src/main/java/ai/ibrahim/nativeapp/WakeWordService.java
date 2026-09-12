package ai.ibrahim.nativeapp;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import ai.picovoice.porcupine.PorcupineManager;

public final class WakeWordService extends Service implements TextToSpeech.OnInitListener {
    public static final String ACTION_START = "ai.ibrahim.nativeapp.START_WAKE";
    public static final String ACTION_PAUSE = "ai.ibrahim.nativeapp.PAUSE_WAKE";
    public static final String ACTION_RESUME = "ai.ibrahim.nativeapp.RESUME_WAKE";
    public static final String ACTION_STOP = "ai.ibrahim.nativeapp.STOP_WAKE";

    private final Handler main = new Handler(Looper.getMainLooper());
    private PorcupineManager porcupineManager;
    private CommandRecognizer commandRecognizer;
    private NativeApiClient apiClient;
    private DeviceActionRouter deviceActions;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean listening;
    private boolean paused;
    private boolean processingCommand;
    private boolean destroyed;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannels(this);
        apiClient = new NativeApiClient(this);
        deviceActions = new DeviceActionRouter(this);
        commandRecognizer = new CommandRecognizer(this);
        tts = new TextToSpeech(this, this);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onError(String utteranceId) {
                if ("wake_prompt".equals(utteranceId)) main.post(WakeWordService.this::startCommandRecognition);
                else if ("assistant_answer".equals(utteranceId)) main.post(WakeWordService.this::resumeAfterCommand);
            }
            @Override public void onDone(String utteranceId) {
                if ("wake_prompt".equals(utteranceId)) main.post(WakeWordService.this::startCommandRecognition);
                else if ("assistant_answer".equals(utteranceId)) main.post(WakeWordService.this::resumeAfterCommand);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopEverything(true);
            return START_NOT_STICKY;
        }
        startForegroundNow("Başlatılıyor…", false);
        if (ACTION_PAUSE.equals(action)) {
            paused = true;
            pauseWakeEngine();
            updateForeground("Duraklatıldı", true);
            return START_STICKY;
        }
        if (ACTION_RESUME.equals(action)) {
            paused = false;
            processingCommand = false;
            startWakeEngine();
            return START_STICKY;
        }
        paused = false;
        getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putBoolean("desired_enabled", true).apply();
        startWakeEngine();
        return START_STICKY;
    }

    private void startForegroundNow(String text, boolean isPaused) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.FOREGROUND_ID, NotificationHelper.foreground(this, text, isPaused), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NotificationHelper.FOREGROUND_ID, NotificationHelper.foreground(this, text, isPaused));
        }
    }

    private void updateForeground(String text, boolean isPaused) {
        getSystemService(NotificationManager.class).notify(NotificationHelper.FOREGROUND_ID, NotificationHelper.foreground(this, text, isPaused));
    }

    private void startWakeEngine() {
        if (destroyed || paused || processingCommand || listening) return;
        String accessKey = new SecretStore(this).get("picovoice_access_key");
        if (accessKey.isEmpty()) {
            paused = true;
            updateForeground("Native Ayarlar → Picovoice AccessKey gerekli", true);
            NotificationHelper.postResult(this, "Wake word kurulumu eksik", "İbrahim AI'ı açıp Native Ayarlar bölümüne Picovoice AccessKey gir.");
            return;
        }
        try {
            if (porcupineManager == null) {
                int sensitivityPercent = getSharedPreferences("native_prefs", MODE_PRIVATE).getInt("wake_sensitivity", 55);
                float sensitivity = Math.max(0.05f, Math.min(0.95f, sensitivityPercent / 100f));
                PorcupineManager.Builder builder = new PorcupineManager.Builder()
                        .setAccessKey(accessKey)
                        .setSensitivity(sensitivity)
                        .setErrorCallback(error -> main.post(() -> onWakeEngineError(error)));
                String customPath = getSharedPreferences("native_prefs", MODE_PRIVATE).getString("custom_keyword_path", "");
                if (customPath != null && !customPath.isEmpty() && new File(customPath).isFile()) builder.setKeywordPath(customPath);
                else builder.setKeyword(Porcupine.BuiltInKeyword.JARVIS);
                porcupineManager = builder.build(this, keywordIndex -> main.post(this::onWakeDetected));
            }
            porcupineManager.start();
            listening = true;
            updateForeground("Dinliyor · “" + (hasCustomKeyword() ? "İbrahim" : "Jarvis") + "” deyince uyanır", false);
        } catch (Exception exception) {
            listening = false;
            updateForeground("Wake-word motoru başlatılamadı", true);
            NotificationHelper.postResult(this, "Wake word hatası", "Picovoice anahtarını ve wake-word dosyasını kontrol et.");
        }
    }

    private boolean hasCustomKeyword() {
        String path = getSharedPreferences("native_prefs", MODE_PRIVATE).getString("custom_keyword_path", "");
        return path != null && !path.isEmpty() && new File(path).isFile();
    }

    private void onWakeEngineError(PorcupineException error) {
        listening = false;
        updateForeground("Wake-word motoru hata verdi · yeniden deneniyor", false);
        main.postDelayed(() -> {
            releasePorcupine();
            startWakeEngine();
        }, 3000);
    }

    private void onWakeDetected() {
        if (processingCommand || paused || destroyed) return;
        processingCommand = true;
        pauseWakeEngine();
        updateForeground("Uyandım · komutunu dinliyorum", false);
        if (ttsReady) tts.speak("Dinliyorum", TextToSpeech.QUEUE_FLUSH, null, "wake_prompt");
        else main.postDelayed(this::startCommandRecognition, 250);
    }

    private void startCommandRecognition() {
        if (destroyed || paused) {
            processingCommand = false;
            return;
        }
        commandRecognizer.start(new CommandRecognizer.Callback() {
            @Override public void onCommand(String text) { handleCommand(text); }
            @Override public void onError(String message) { speakThenResume(message); }
        });
    }

    private void handleCommand(String text) {
        String normalized = text.toLowerCase(new Locale("tr", "TR"));
        updateForeground("Komut: " + compact(text, 80), false);
        if (normalized.contains("dinlemeyi kapat") || normalized.contains("dinlemeyi durdur") || normalized.contains("7 24 kapat")) {
            speak("7 24 dinlemeyi kapatıyorum", "stop_answer");
            getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putBoolean("desired_enabled", false).apply();
            main.postDelayed(() -> stopEverything(true), 1400);
            return;
        }
        if (normalized.contains("saat kaç") || normalized.contains("saati söyle")) {
            speakThenResume("Saat " + new SimpleDateFormat("HH:mm", new Locale("tr", "TR")).format(new Date()));
            return;
        }
        if (normalized.contains("pil") && (normalized.contains("kaç") || normalized.contains("yüzde"))) {
            BatteryManager manager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            speakThenResume("Pil yüzde " + manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            return;
        }
        if (normalized.contains("uygulamayı aç") || normalized.contains("ibrahim ai aç")) {
            NotificationHelper.postResult(this, "İbrahim AI", "Uygulamayı açmak için bu bildirime dokun.");
            speakThenResume("Uygulamayı açmak için bildirime dokunabilirsin");
            return;
        }
        String localAction = deviceActions.performFromNaturalLanguage(text);
        if (!localAction.isEmpty()) {
            try {
                JSONObject result = new JSONObject(localAction);
                String message = result.optString("message", "Cihaz eylemini başlattım.");
                NotificationHelper.postResult(this, "NOXARA · cihaz eylemi", message);
                speakThenResume(message);
            } catch (Exception exception) {
                speakThenResume("Cihaz eylemini başlattım fakat sonuç metnini okuyamadım.");
            }
            return;
        }
        if (!apiClient.isPaired()) {
            NotificationHelper.postResult(this, "Native eşleştirme gerekli", "İbrahim AI'ı açıp hesabına giriş yap; cihaz otomatik eşleşecek.");
            speakThenResume("Önce İbrahim AI uygulamasını açıp hesabına bir kez giriş yapmalısın");
            return;
        }
        updateForeground("İbrahim AI düşünüyor…", false);
        apiClient.sendCommand(text, new NativeApiClient.Callback() {
            @Override public void onSuccess(String response) {
                NotificationHelper.postResult(WakeWordService.this, "İbrahim AI yanıtı", response);
                speakThenResume(response);
            }
            @Override public void onError(String message) {
                NotificationHelper.postResult(WakeWordService.this, "İbrahim AI", message);
                speakThenResume(message);
            }
        });
    }

    private void speakThenResume(String text) {
        if (ttsReady) speak(compactForSpeech(text), "assistant_answer");
        else {
            NotificationHelper.postResult(this, "İbrahim AI", text);
            main.postDelayed(this::resumeAfterCommand, 300);
        }
    }

    private void speak(String text, String utteranceId) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    private String compactForSpeech(String text) {
        String cleaned = text.replaceAll("https?://\\S+", " bağlantı ").replaceAll("\\s+", " ").trim();
        int max = Math.min(3500, TextToSpeech.getMaxSpeechInputLength() - 100);
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max) + ". Devamını bildirimde bıraktım.";
    }

    private static String compact(String text, int max) {
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() <= max ? single : single.substring(0, max - 1) + "…";
    }

    private void resumeAfterCommand() {
        processingCommand = false;
        if (!paused && !destroyed) main.postDelayed(this::startWakeEngine, 250);
    }

    private void pauseWakeEngine() {
        if (porcupineManager != null && listening) {
            try { porcupineManager.stop(); } catch (Exception ignored) { }
        }
        listening = false;
        if (commandRecognizer != null) commandRecognizer.stop();
    }

    private void releasePorcupine() {
        pauseWakeEngine();
        if (porcupineManager != null) {
            try { porcupineManager.delete(); } catch (Exception ignored) { }
            porcupineManager = null;
        }
    }

    private void stopEverything(boolean clearPreference) {
        if (clearPreference) getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putBoolean("desired_enabled", false).apply();
        destroyed = true;
        releasePorcupine();
        if (apiClient != null) apiClient.shutdown();
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) { }
            tts.shutdown();
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("tr", "TR"));
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
            tts.setSpeechRate(1.02f);
        }
    }

    @Override public void onDestroy() {
        destroyed = true;
        releasePorcupine();
        if (commandRecognizer != null) commandRecognizer.stop();
        if (apiClient != null) apiClient.shutdown();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
