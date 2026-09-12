package ai.ibrahim.nativeapp;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;

public final class ContinuousConversationRecorder {
    public interface Callback {
        void onSpeechStart();
        void onUtterance(byte[] wavAudio);
        void onError(String message);
    }

    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_SAMPLES = 320;
    private static final int PRE_ROLL_FRAMES = 12;
    private static final int START_FRAMES = 2;
    private static final int START_FRAMES_DURING_TTS = 3;
    private static final int END_SILENCE_FRAMES = 22;
    private static final int MIN_UTTERANCE_FRAMES = 7;
    private static final int MAX_UTTERANCE_FRAMES = 900;

    private final Object lock = new Object();
    private AudioRecord recorder;
    private AcousticEchoCanceler echoCanceler;
    private NoiseSuppressor noiseSuppressor;
    private AutomaticGainControl gainControl;
    private Thread readerThread;
    private volatile boolean running;
    private volatile boolean captureEnabled = true;
    private volatile boolean assistantSpeaking;
    private Callback callback;

    public boolean isRunning() {
        return running;
    }

    public void setCaptureEnabled(boolean enabled) {
        captureEnabled = enabled;
    }

    public void setAssistantSpeaking(boolean speaking) {
        assistantSpeaking = speaking;
    }

    public void start(Callback callback) {
        synchronized (lock) {
            if (running) {
                this.callback = callback;
                captureEnabled = true;
                return;
            }
            this.callback = callback;
            try {
                recorder = createRecorder(MediaRecorder.AudioSource.VOICE_COMMUNICATION);
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    recorder.release();
                    recorder = createRecorder(MediaRecorder.AudioSource.MIC);
                }
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("AudioRecord init failed");
                enableAudioEffects(recorder.getAudioSessionId());
                recorder.startRecording();
                if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("AudioRecord start failed");
                running = true;
                captureEnabled = true;
                readerThread = new Thread(this::readLoop, "noxara-continuous-mic");
                readerThread.setPriority(Thread.MAX_PRIORITY);
                readerThread.start();
            } catch (Exception exception) {
                releaseRecorder();
                notifyError("Sürekli mikrofon başlatılamadı. Mikrofon iznini kontrol et.");
            }
        }
    }

    public void stop() {
        synchronized (lock) {
            running = false;
            captureEnabled = false;
            assistantSpeaking = false;
            if (recorder != null) {
                try { recorder.stop(); } catch (Exception ignored) { }
            }
            if (readerThread != null) {
                try { readerThread.join(350); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                readerThread = null;
            }
            releaseRecorder();
        }
    }

    private AudioRecord createRecorder(int source) {
        int minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferBytes = Math.max(minBytes * 2, FRAME_SAMPLES * 2 * 8);
        return new AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
    }

    private void enableAudioEffects(int sessionId) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId);
                if (echoCanceler != null) echoCanceler.setEnabled(true);
            }
        } catch (Exception ignored) { }
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId);
                if (noiseSuppressor != null) noiseSuppressor.setEnabled(true);
            }
        } catch (Exception ignored) { }
        try {
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(sessionId);
                if (gainControl != null) gainControl.setEnabled(true);
            }
        } catch (Exception ignored) { }
    }

    private void readLoop() {
        short[] frame = new short[FRAME_SAMPLES];
        ArrayDeque<short[]> preRoll = new ArrayDeque<>();
        ByteArrayOutputStream utterance = null;
        double noiseFloor = 240.0;
        int hotFrames = 0;
        int silenceFrames = 0;
        int utteranceFrames = 0;
        boolean inSpeech = false;

        while (running) {
            AudioRecord current = recorder;
            if (current == null) break;
            int read;
            try {
                read = current.read(frame, 0, frame.length, AudioRecord.READ_BLOCKING);
            } catch (Exception exception) {
                if (running) notifyError("Mikrofon okuma hatası oluştu.");
                break;
            }
            if (read <= 0) continue;

            double rms = rms(frame, read);
            if (!captureEnabled) {
                preRoll.clear();
                utterance = null;
                inSpeech = false;
                hotFrames = 0;
                silenceFrames = 0;
                utteranceFrames = 0;
                noiseFloor = adaptNoiseFloor(noiseFloor, rms);
                continue;
            }

            if (!inSpeech) {
                short[] copy = new short[read];
                System.arraycopy(frame, 0, copy, 0, read);
                preRoll.addLast(copy);
                while (preRoll.size() > PRE_ROLL_FRAMES) preRoll.removeFirst();

                noiseFloor = adaptNoiseFloor(noiseFloor, rms);
                double startThreshold = assistantSpeaking
                        ? Math.max(1050.0, noiseFloor * 3.7)
                        : Math.max(300.0, noiseFloor * 1.85);
                if (rms >= startThreshold) hotFrames += 1;
                else hotFrames = Math.max(0, hotFrames - 1);

                int needed = assistantSpeaking ? START_FRAMES_DURING_TTS : START_FRAMES;
                if (hotFrames >= needed) {
                    inSpeech = true;
                    silenceFrames = 0;
                    utteranceFrames = preRoll.size();
                    utterance = new ByteArrayOutputStream(32768);
                    for (short[] buffered : preRoll) writePcm16Le(utterance, buffered, buffered.length);
                    preRoll.clear();
                    Callback currentCallback = callback;
                    if (currentCallback != null) currentCallback.onSpeechStart();
                }
                continue;
            }

            if (utterance == null) utterance = new ByteArrayOutputStream(32768);
            writePcm16Le(utterance, frame, read);
            utteranceFrames += 1;
            double endThreshold = Math.max(220.0, noiseFloor * 1.45);
            if (rms < endThreshold) silenceFrames += 1;
            else silenceFrames = 0;

            if (silenceFrames >= END_SILENCE_FRAMES || utteranceFrames >= MAX_UTTERANCE_FRAMES) {
                if (utteranceFrames >= MIN_UTTERANCE_FRAMES) {
                    byte[] pcm = utterance.toByteArray();
                    Callback currentCallback = callback;
                    if (currentCallback != null && pcm.length > 0) currentCallback.onUtterance(toWav(pcm));
                }
                utterance = null;
                inSpeech = false;
                hotFrames = 0;
                silenceFrames = 0;
                utteranceFrames = 0;
                preRoll.clear();
            }
        }
    }

    private static double adaptNoiseFloor(double current, double rms) {
        if (rms <= 0) return current;
        double sample = Math.min(rms, 1800.0);
        double updated = current * 0.96 + sample * 0.04;
        return Math.max(120.0, Math.min(900.0, updated));
    }

    private static double rms(short[] data, int count) {
        if (count <= 0) return 0.0;
        double sum = 0.0;
        for (int index = 0; index < count; index += 1) {
            double value = data[index];
            sum += value * value;
        }
        return Math.sqrt(sum / count);
    }

    private static void writePcm16Le(ByteArrayOutputStream out, short[] samples, int count) {
        for (int index = 0; index < count; index += 1) {
            int value = samples[index];
            out.write(value & 0xff);
            out.write((value >> 8) & 0xff);
        }
    }

    private static byte[] toWav(byte[] pcm) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(pcm.length + 44);
        int byteRate = SAMPLE_RATE * 2;
        writeAscii(out, "RIFF");
        writeIntLe(out, 36 + pcm.length);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeIntLe(out, 16);
        writeShortLe(out, 1);
        writeShortLe(out, 1);
        writeIntLe(out, SAMPLE_RATE);
        writeIntLe(out, byteRate);
        writeShortLe(out, 2);
        writeShortLe(out, 16);
        writeAscii(out, "data");
        writeIntLe(out, pcm.length);
        out.write(pcm, 0, pcm.length);
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) {
        for (int index = 0; index < value.length(); index += 1) out.write((byte) value.charAt(index));
    }

    private static void writeIntLe(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private static void writeShortLe(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    private void releaseRecorder() {
        releaseEffect(echoCanceler);
        releaseEffect(noiseSuppressor);
        releaseEffect(gainControl);
        echoCanceler = null;
        noiseSuppressor = null;
        gainControl = null;
        if (recorder != null) {
            try { recorder.release(); } catch (Exception ignored) { }
            recorder = null;
        }
    }

    private static void releaseEffect(android.media.audiofx.AudioEffect effect) {
        if (effect == null) return;
        try { effect.release(); } catch (Exception ignored) { }
    }

    private void notifyError(String message) {
        Callback current = callback;
        if (current != null) current.onError(message);
    }
}
