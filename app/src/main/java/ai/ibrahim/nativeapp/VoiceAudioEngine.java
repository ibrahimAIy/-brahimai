package ai.ibrahim.nativeapp;

/**
 * Process-wide owner for live voice microphone capture.
 *
 * The AudioRecord instance lives here instead of inside an Android Service object, so a service
 * recreation does not tear down and reopen the microphone. Only an explicit user stop releases
 * the recorder. During transcription/thinking/TTS the hardware stream remains open; capture is
 * merely enabled/disabled in software inside ContinuousConversationRecorder.
 */
public final class VoiceAudioEngine {
    private static final VoiceAudioEngine INSTANCE = new VoiceAudioEngine();

    private final Object lock = new Object();
    private final ContinuousConversationRecorder recorder = new ContinuousConversationRecorder();
    private volatile ContinuousConversationRecorder.Callback callback;
    private boolean bridgeInstalled;

    private VoiceAudioEngine() { }

    public static VoiceAudioEngine get() {
        return INSTANCE;
    }

    public void attach(ContinuousConversationRecorder.Callback callback) {
        synchronized (lock) {
            this.callback = callback;
            if (!bridgeInstalled) {
                bridgeInstalled = true;
                recorder.start(new ContinuousConversationRecorder.Callback() {
                    @Override public void onSpeechStart() {
                        ContinuousConversationRecorder.Callback current = VoiceAudioEngine.this.callback;
                        if (current != null) current.onSpeechStart();
                    }

                    @Override public void onUtterance(byte[] wavAudio) {
                        ContinuousConversationRecorder.Callback current = VoiceAudioEngine.this.callback;
                        if (current != null) current.onUtterance(wavAudio);
                    }

                    @Override public void onError(String message) {
                        ContinuousConversationRecorder.Callback current = VoiceAudioEngine.this.callback;
                        if (current != null) current.onError(message);
                    }
                });
            } else if (!recorder.isRunning()) {
                // Only reached after a real recorder failure. Ordinary service recreation never
                // comes here because the process-wide recorder remains alive.
                bridgeInstalled = false;
                attach(callback);
                return;
            }
            recorder.setCaptureEnabled(true);
        }
    }

    public void detach() {
        callback = null;
    }

    public boolean isRunning() {
        return recorder.isRunning();
    }

    public void setCaptureEnabled(boolean enabled) {
        recorder.setCaptureEnabled(enabled);
    }

    public void setAssistantSpeaking(boolean speaking) {
        recorder.setAssistantSpeaking(speaking);
    }

    /** Releases microphone hardware only for an explicit user/session stop. */
    public void stopExplicitly() {
        synchronized (lock) {
            callback = null;
            bridgeInstalled = false;
            recorder.stop();
        }
    }
}
