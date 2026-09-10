package ai.ibrahim.nativeapp;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

public final class CommandRecognizer implements RecognitionListener {
    public interface Callback {
        void onCommand(String text);
        void onError(String message);
    }

    private final Context context;
    private SpeechRecognizer recognizer;
    private Callback callback;
    private boolean finished;

    public CommandRecognizer(Context context) {
        this.context = context;
    }

    public void start(Callback callback) {
        stop();
        this.callback = callback;
        this.finished = false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            } else {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            }
            recognizer.setRecognitionListener(this);
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            recognizer.startListening(intent);
        } catch (Exception exception) {
            finishError("Türkçe ses tanıma başlatılamadı.");
        }
    }

    public void stop() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
            recognizer.destroy();
            recognizer = null;
        }
    }

    private void finishCommand(String text) {
        if (finished) return;
        finished = true;
        Callback current = callback;
        stop();
        if (current != null) current.onCommand(text);
    }

    private void finishError(String message) {
        if (finished) return;
        finished = true;
        Callback current = callback;
        stop();
        if (current != null) current.onError(message);
    }

    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { }
    @Override public void onPartialResults(Bundle partialResults) { }
    @Override public void onEvent(int eventType, Bundle params) { }

    @Override
    public void onError(int error) {
        String message = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ? "Seni anlayamadım. Wake word'ü tekrar söyleyebilirsin."
                : "Ses tanıma kısa süreli hata verdi.";
        finishError(message);
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty() || matches.get(0).trim().isEmpty()) {
            finishError("Komutu duyamadım.");
            return;
        }
        finishCommand(matches.get(0).trim());
    }
}
