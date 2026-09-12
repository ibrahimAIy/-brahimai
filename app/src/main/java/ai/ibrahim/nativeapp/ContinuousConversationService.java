package ai.ibrahim.nativeapp;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.Locale;
import java.util.Set;

public final class ContinuousConversationService extends Service implements TextToSpeech.OnInitListener {
    public static final String ACTION_START = "ai.ibrahim.nativeapp.CONTINUOUS_CONVERSATION_START";
    public static final String ACTION_STOP = "ai.ibrahim.nativeapp.CONTINUOUS_CONVERSATION_STOP";
    public static final String ACTION_SET_VOICE = "ai.ibrahim.nativeapp.CONTINUOUS_CONVERSATION_SET_VOICE";
    public static final String EXTRA_VOICE_PROFILE = "voice_profile";

    private static final int FOREGROUND_ID = 1310;
    private static final String PROFILE_LARA = "lara";
    private static final String PROFILE_ARAS = "aras";

    private final Handler main = new Handler(Looper.getMainLooper());
    private VoiceAudioEngine audioEngine;
    private NativeTranscriptionClient transcriptionClient;
    private NativeApiClient apiClient;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean busy;
    private boolean assistantSpeaking;
    private boolean destroyed;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannels(this);
        audioEngine = VoiceAudioEngine.get();
        transcriptionClient = new NativeTranscriptionClient(this);
        apiClient = new NativeApiClient(this);
        tts = new TextToSpeech(this, this);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onError(String utteranceId) {
                if ("continuous_answer".equals(utteranceId)) main.post(ContinuousConversationService.this::finishAssistantSpeech);
            }
            @Override public void onDone(String utteranceId) {
                if ("continuous_answer".equals(utteranceId)) main.post(ContinuousConversationService.this::finishAssistantSpeech);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        startForegroundNow("Canlı ses hazırlanıyor");

        if (ACTION_STOP.equals(action)) {
            stopConversation();
            return START_NOT_STICKY;
        }

        if (ACTION_SET_VOICE.equals(action)) {
            String requested = intent == null ? "" : intent.getStringExtra(EXTRA_VOICE_PROFILE);
            setVoiceProfile(requested);
            updateForeground("Canlı ses · " + voiceDisplayName() + " · mikrofon tek oturum");
            return START_STICKY;
        }

        getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putBoolean("conversation_mode", true).apply();
        busy = false;
        destroyed = false;
        if (ttsReady) applyVoiceProfile();
        attachPersistentMicrophone();
        updateForeground("Canlı ses · " + voiceDisplayName() + " · mikrofon tek oturum");
        return START_STICKY;
    }

    private void attachPersistentMicrophone() {
        if (audioEngine == null) return;
        audioEngine.attach(new ContinuousConversationRecorder.Callback() {
            @Override public void onSpeechStart() {
                main.post(() -> {
                    if (!assistantSpeaking) return;
                    try { if (tts != null) tts.stop(); } catch (Exception ignored) { }
                    assistantSpeaking = false;
                    audioEngine.setAssistantSpeaking(false);
                    busy = false;
                    updateForeground("Seni dinliyorum · mikrofon açık kalıyor");
                });
            }

            @Override public void onUtterance(byte[] wavAudio) {
                main.post(() -> processUtterance(wavAudio));
            }

            @Override public void onError(String message) {
                main.post(() -> {
                    updateForeground("Canlı ses mikrofon hatası");
                    NotificationHelper.postResult(ContinuousConversationService.this, "NOXARA · Canlı Ses", message);
                });
            }
        });
        audioEngine.setCaptureEnabled(true);
    }

    private void processUtterance(byte[] wavAudio) {
        if (destroyed || busy || !isConversationMode()) return;
        busy = true;
        // Hardware microphone remains recording. We only ignore frames in software while this
        // utterance is being transcribed and answered.
        audioEngine.setCaptureEnabled(false);
        audioEngine.setAssistantSpeaking(false);
        updateForeground("Duydum · mikrofon hâlâ açık · çözümlüyorum");

        transcriptionClient.transcribeWav(wavAudio, new NativeTranscriptionClient.Callback() {
            @Override public void onSuccess(String text) {
                if (destroyed || !isConversationMode()) return;
                String spoken = text == null ? "" : text.trim();
                if (spoken.isEmpty()) {
                    resumeListening();
                    return;
                }
                String normalized = spoken.toLowerCase(new Locale("tr", "TR"));
                if (normalized.contains("canlı sesi kapat") || normalized.contains("sohbet modunu kapat") || normalized.contains("konuşma modunu kapat")) {
                    speakAndStop("Canlı sohbeti kapattım.");
                    return;
                }
                updateForeground("NOXARA düşünüyor · mikrofon hâlâ açık");
                apiClient.sendCommand(spoken, new NativeApiClient.Callback() {
                    @Override public void onSuccess(String response) {
                        busy = false;
                        speakAnswer(response);
                    }

                    @Override public void onError(String message) {
                        busy = false;
                        speakAnswer(message);
                    }
                });
            }

            @Override public void onError(String message) {
                busy = false;
                NotificationHelper.postResult(ContinuousConversationService.this, "NOXARA · Canlı Ses", message);
                resumeListening();
            }
        });
    }

    private void speakAnswer(String text) {
        if (destroyed || !isConversationMode()) return;
        String spoken = compactForSpeech(text);
        if (spoken.isEmpty()) {
            resumeListening();
            return;
        }
        if (!ttsReady || tts == null) {
            NotificationHelper.postResult(this, "NOXARA", spoken);
            resumeListening();
            return;
        }
        assistantSpeaking = true;
        audioEngine.setCaptureEnabled(true);
        audioEngine.setAssistantSpeaking(true);
        updateForeground("Konuşuyorum · mikrofon açık · sözümü kesebilirsin");
        tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "continuous_answer");
    }

    private void speakAndStop(String text) {
        getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putBoolean("conversation_mode", false).apply();
        if (!ttsReady || tts == null) {
            stopConversation();
            return;
        }
        busy = true;
        assistantSpeaking = true;
        audioEngine.setCaptureEnabled(false);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "continuous_answer");
        main.postDelayed(this::stopConversation, 1300);
    }

    private void finishAssistantSpeech() {
        if (destroyed) return;
        assistantSpeaking = false;
        busy = false;
        if (audioEngine != null) audioEngine.setAssistantSpeaking(false);
        if (!isConversationMode()) return;
        resumeListening();
    }

    private void resumeListening() {
        if (destroyed || !isConversationMode()) return;
        busy = false;
        assistantSpeaking = false;
        audioEngine.setAssistantSpeaking(false);
        audioEngine.setCaptureEnabled(true);
        updateForeground("Seni dinliyorum · mikrofon tek oturumda açık");
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) return;
        int result = tts.setLanguage(new Locale("tr", "TR"));
        ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
        if (ttsReady) applyVoiceProfile();
    }

    private void setVoiceProfile(String requested) {
        String profile = PROFILE_ARAS.equalsIgnoreCase(requested) ? PROFILE_ARAS : PROFILE_LARA;
        getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putString("voice_profile", profile).apply();
        if (ttsReady) applyVoiceProfile();
    }

    private String voiceProfile() {
        String stored = getSharedPreferences("native_prefs", MODE_PRIVATE).getString("voice_profile", PROFILE_LARA);
        return PROFILE_ARAS.equals(stored) ? PROFILE_ARAS : PROFILE_LARA;
    }

    private String voiceDisplayName() {
        return PROFILE_ARAS.equals(voiceProfile()) ? "Aras" : "Lara";
    }

    private void applyVoiceProfile() {
        if (tts == null) return;
        String profile = voiceProfile();
        selectBestTurkishVoice(profile);
        if (PROFILE_ARAS.equals(profile)) {
            tts.setSpeechRate(1.02f);
            tts.setPitch(0.92f);
        } else {
            tts.setSpeechRate(1.06f);
            tts.setPitch(1.06f);
        }
    }

    private void selectBestTurkishVoice(String profile) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || tts == null) return;
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return;
            Voice best = null;
            int bestScore = Integer.MIN_VALUE;
            for (Voice voice : voices) {
                Locale locale = voice.getLocale();
                if (locale == null || !"tr".equalsIgnoreCase(locale.getLanguage())) continue;
                String name = voice.getName().toLowerCase(Locale.ROOT);
                int score = voice.getQuality() * 10 - voice.getLatency();
                if (!voice.isNetworkConnectionRequired()) score += 220;
                if (PROFILE_ARAS.equals(profile)) {
                    if (matchesAny(name, "male", "man", "erkek", "ahmet", "mehmet", "murat", "tolga", "cem", "ali", "emre")) score += 5200;
                    if (matchesAny(name, "female", "woman", "kadın", "aylin", "selin", "seda", "zeynep", "filiz", "eda", "derya")) score -= 3200;
                } else {
                    if (matchesAny(name, "female", "woman", "kadın", "aylin", "selin", "seda", "zeynep", "filiz", "eda", "derya")) score += 5200;
                    if (matchesAny(name, "male", "man", "erkek", "ahmet", "mehmet", "murat", "tolga", "cem", "ali", "emre")) score -= 3200;
                }
                if (best == null || score > bestScore) {
                    best = voice;
                    bestScore = score;
                }
            }
            if (best != null) tts.setVoice(best);
        } catch (Exception ignored) {
            // Vendor TTS voice metadata can be incomplete; pitch/rate still keep Lara and Aras distinct.
        }
    }

    private static boolean matchesAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private boolean isConversationMode() {
        return getSharedPreferences("native_prefs", Context.MODE_PRIVATE).getBoolean("conversation_mode", false);
    }

    private String compactForSpeech(String text) {
        if (text == null) return "";
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

    private void startForegroundNow(String status) {
        Notification notification = new Notification.Builder(this, NotificationHelper.WAKE_CHANNEL)
                .setSmallIcon(R.drawable.ic_ibrahim)
                .setContentTitle("NOXARA · Canlı Ses")
                .setContentText(status)
                .setContentIntent(NotificationHelper.openAppIntent(this))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(FOREGROUND_ID, notification);
        }
    }

    private void updateForeground(String status) {
        Notification notification = new Notification.Builder(this, NotificationHelper.WAKE_CHANNEL)
                .setSmallIcon(R.drawable.ic_ibrahim)
                .setContentTitle("NOXARA · Canlı Ses")
                .setContentText(status)
                .setContentIntent(NotificationHelper.openAppIntent(this))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        getSystemService(NotificationManager.class).notify(FOREGROUND_ID, notification);
    }

    private void stopConversation() {
        if (destroyed) return;
        destroyed = true;
        getSharedPreferences("native_prefs", MODE_PRIVATE).edit().putBoolean("conversation_mode", false).apply();
        if (audioEngine != null) audioEngine.stopExplicitly();
        shutdownServiceResources();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void shutdownServiceResources() {
        if (transcriptionClient != null) transcriptionClient.shutdown();
        if (apiClient != null) apiClient.shutdown();
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) { }
            tts.shutdown();
        }
    }

    @Override
    public void onDestroy() {
        if (!destroyed) {
            // Service recreation is not a user request to close the microphone. Detach the service
            // callback but leave the process-wide AudioRecord alive so START_STICKY can reattach
            // without another microphone open/close event.
            if (audioEngine != null) {
                if (isConversationMode()) audioEngine.detach();
                else audioEngine.stopExplicitly();
            }
            shutdownServiceResources();
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
