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
import java.util.Set;

import android.speech.tts.Voice;

import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import ai.picovoice.porcupine.PorcupineManager;

public final class WakeWordService extends Service implements TextToSpeech.OnInitListener {
    public static final String ACTION_START = "ai.ibrahim.nativeapp.START_WAKE";
    public static final String ACTION_PAUSE = "ai.ibrahim.nativeapp.PAUSE_WAKE";
    public static final String ACTION_RESUME = "ai.ibrahim.nativeapp.RESUME_WAKE";
    public static final String ACTION_STOP = "ai.ibrahim.nativeapp.STOP_WAKE";
    public static final String ACTION_CONVERSATION_START = "ai.ibrahim.nativeapp.START_CONVERSATION";
    public static final String ACTION_CONVERSATION_STOP = "ai.ibrahim.nativeapp.STOP_CONVERSATION";

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
    private boolean conversationMode;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannels(this);
        apiClient = new NativeApiClient(this);
        deviceActions = new DeviceActionRouter(this);
        commandRecognizer = new CommandRecognizer(this);
        conversationMode = getSharedPreferences("native_prefs", MODE_PRIVATE).getBoolean("conversation_mode", false);
        tts = new TextToSpeech(this, this);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onError(String utteranceId) {
                if ("wake_prompt".equals(utteranceId) || "conversation_prompt".equals(utteranceId)) main.post(WakeWordService.this::startCommandRecognition);
                else if ("assistant_answer".equals(utteranceId)) main.post(WakeWordService.this::afterAssistantSpeech);
            }
            @Override public void onDone(String utteranceId) {
                if ("wake_prompt".equals(utteranceId) || "conversation_prompt".equals(utteranceId)) main.post(WakeWordService.this::startCommandRecognition);
                else if ("assistant_answer".equals(utteranceId)) main.post(WakeWordService.this::afterAssistantSpeech);
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
        if (ACTION_CONVERSATION_START.equals(action)) {
            paused = false;
            conversationMode = true;
            getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putBoolean("conversation_mode", true).apply();
            pauseWakeEngine();
            processingCommand = true;
            updateForeground("Canlı sohbet · hazırlanıyor", false);
            // Do not speak a repetitive opening prompt. Start listening immediately.
            main.postDelayed(this::startCommandRecognition, 80);
            return START_STICKY;
        }
        if (ACTION_CONVERSATION_STOP.equals(action)) {
            conversationMode = false;
            getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putBoolean("conversation_mode", false).apply();
            if (commandRecognizer != null) commandRecognizer.stop();
            processingCommand = false;
            paused = false;
            updateForeground("Canlı sohbet kapalı · wake word hazır", false);
            main.postDelayed(this::startWakeEngine, 250);
            return START_STICKY;
        }
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
        if (conversationMode) {
            startConversationListening();
            return;
        }
        String accessKey = new SecretStore(this).get("picovoice_access_key");
        if (accessKey.isEmpty()) {
            paused = true;
            updateForeground("Native Ayarlar → Picovoice AccessKey gerekli", true);
            NotificationHelper.postResult(this, "Wake word kurulumu eksik", "NOXARA'yı açıp Native Ayarlar bölümüne Picovoice AccessKey gir.");
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

    private void startConversationListening() {
        if (destroyed || paused || !conversationMode) return;
        processingCommand = true;
        pauseWakeEngine();
        updateForeground("Canlı sohbet · dinliyorum", false);
        main.postDelayed(this::startCommandRecognition, 120);
    }

    private void startCommandRecognition() {
        if (destroyed || paused) {
            processingCommand = false;
            return;
        }
        commandRecognizer.start(new CommandRecognizer.Callback() {
            @Override public void onCommand(String text) { handleCommand(text); }
            @Override public void onError(String message) {
                if (conversationMode) {
                    updateForeground("Canlı sohbet · seni bekliyorum", false);
                    main.postDelayed(WakeWordService.this::startConversationListening, 650);
                } else {
                    speakThenResume(message);
                }
            }
        });
    }

    private void handleCommand(String text) {
        String normalized = text.toLowerCase(new Locale("tr", "TR"));
        updateForeground("Komut: " + compact(text, 80), false);
        if (normalized.contains("sohbet modunu kapat") || normalized.contains("konuşma modunu kapat") || normalized.contains("canlı sesi kapat")) {
            conversationMode = false;
            getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putBoolean("conversation_mode", false).apply();
            speakThenResume("Canlı sohbeti kapattım. Wake word ile devam edebilirsin.");
            return;
        }
        if (normalized.contains("sohbet modunu aç") || normalized.contains("konuşma modunu aç") || normalized.contains("canlı sesi aç")) {
            conversationMode = true;
            getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putBoolean("conversation_mode", true).apply();
            main.postDelayed(this::startCommandRecognition, 80);
            return;
        }
        if (normalized.contains("dinlemeyi kapat") || normalized.contains("dinlemeyi durdur") || normalized.contains("7 24 kapat")) {
            speak("7 24 dinlemeyi kapatıyorum", "stop_answer");
            getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putBoolean("desired_enabled", false).putBoolean("conversation_mode", false).apply();
            conversationMode = false;
            main.postDelayed(() -> stopEverything(true), 1400);
            return;
        }
        String clockAnswer = localClockAnswer(normalized);
        if (!clockAnswer.isEmpty()) {
            speakThenResume(clockAnswer);
            return;
        }
        if (normalized.contains("pil") && (normalized.contains("kaç") || normalized.contains("yüzde"))) {
            BatteryManager manager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            speakThenResume("Pil yüzde " + manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            return;
        }
        if (normalized.contains("uygulamayı aç") || normalized.contains("ibrahim ai aç") || normalized.contains("noxara aç")) {
            NotificationHelper.postResult(this, "NOXARA", "Uygulamayı açmak için bu bildirime dokun.");
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
            NotificationHelper.postResult(this, "Native eşleştirme gerekli", "NOXARA'yı açıp hesabına giriş yap; cihaz otomatik eşleşecek.");
            speakThenResume("Önce NOXARA uygulamasını açıp hesabına bir kez giriş yapmalısın");
            return;
        }
        updateForeground("NOXARA düşünüyor…", false);
        apiClient.sendCommand(text, new NativeApiClient.Callback() {
            @Override public void onSuccess(String response) {
                speakThenResume(response);
            }
            @Override public void onError(String message) {
                NotificationHelper.postResult(WakeWordService.this, "NOXARA", message);
                speakThenResume(message);
            }
        });
    }

    private String localClockAnswer(String normalized) {
        boolean asksTime = normalized.contains("saat kaç") || normalized.contains("saati söyle") || normalized.contains("şu an saat") || normalized.contains("simdi saat") || normalized.contains("şimdi saat");
        boolean asksDate = normalized.contains("bugün tarih") || normalized.contains("bugunun tarihi") || normalized.contains("bugünün tarihi") || normalized.contains("ayın kaçı") || normalized.contains("ayin kaci");
        boolean asksYear = normalized.contains("hangi yıldayız") || normalized.contains("hangi yildayiz") || normalized.contains("yıl kaç") || normalized.contains("yil kac");
        boolean asksWeekday = normalized.contains("günlerden ne") || normalized.contains("gunlerden ne");
        if (!asksTime && !asksDate && !asksYear && !asksWeekday) return "";
        Date now = new Date();
        String time = new SimpleDateFormat("HH:mm:ss", new Locale("tr", "TR")).format(now);
        String date = new SimpleDateFormat("dd.MM.yyyy", new Locale("tr", "TR")).format(now);
        String year = new SimpleDateFormat("yyyy", new Locale("tr", "TR")).format(now);
        String weekday = new SimpleDateFormat("EEEE", new Locale("tr", "TR")).format(now);
        StringBuilder answer = new StringBuilder();
        if (asksTime) answer.append("Saat ").append(time).append(".");
        if (asksDate) appendSentence(answer, "Tarih " + date + ".");
        if (asksYear) appendSentence(answer, year + " yılındayız.");
        if (asksWeekday) appendSentence(answer, "Bugün " + weekday + ".");
        return answer.toString();
    }

    private static void appendSentence(StringBuilder builder, String sentence) {
        if (builder.length() > 0) builder.append(' ');
        builder.append(sentence);
    }

    private void speakThenResume(String text) {
        if (ttsReady) speak(compactForSpeech(text), "assistant_answer");
        else {
            NotificationHelper.postResult(this, "NOXARA", text);
            main.postDelayed(this::afterAssistantSpeech, 300);
        }
    }

    private void speak(String text, String utteranceId) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    private void afterAssistantSpeech() {
        if (conversationMode && !paused && !destroyed) {
            startConversationListening();
            return;
        }
        resumeAfterCommand();
    }

    private String compactForSpeech(String text) {
        String cleaned = text
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("https?://\\S+", " bağlantı ")
                .replaceAll("[*_#>`|]", " ")
                .replace("%", " yüzde ")
                .replaceAll("\\s+", " ")
                .trim();
        int max = Math.min(1400, TextToSpeech.getMaxSpeechInputLength() - 100);
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max) + ". İstersen devamını anlatırım.";
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
        if (clearPreference) getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putBoolean("desired_enabled", false).putBoolean("conversation_mode", false).apply();
        conversationMode = false;
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
            selectBestTurkishVoice();
            tts.setSpeechRate(1.08f);
            tts.setPitch(1.04f);
        }
    }

    private void selectBestTurkishVoice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || tts == null) return;
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return;
            Voice best = null;
            int bestScore = Integer.MIN_VALUE;
            for (Voice voice : voices) {
                Locale locale = voice.getLocale();
                if (locale == null || !"tr".equalsIgnoreCase(locale.getLanguage())) continue;
                int score = voice.getQuality() * 10 - voice.getLatency();
                String name = voice.getName().toLowerCase(Locale.ROOT);
                if (name.contains("female") || name.contains("kadın") || name.contains("woman")) score += 5000;
                if (!voice.isNetworkConnectionRequired()) score += 250;
                if (best == null || score > bestScore) {
                    best = voice;
                    bestScore = score;
                }
            }
            if (best != null) tts.setVoice(best);
        } catch (Exception ignored) {
            // Some vendor TTS engines expose incomplete voice metadata; language fallback still works.
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
