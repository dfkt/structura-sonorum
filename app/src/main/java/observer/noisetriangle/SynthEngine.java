package observer.noisetriangle;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class SynthEngine {
    private static final int RATE = 48000;
    private static final int FADE_IN_SAMPLES = RATE / 50;  // 20 ms
    private static final int FADE_OUT_SAMPLES = RATE * 3 / 100; // 30 ms
    private static final double PAN_SMOOTHING = 0.002;
    private static final double NOISE_COLOR_SMOOTHING = 0.0015;
    private static final double[] NOISE_MEASURED_POSITIONS = {
            0, .01, .02, .03, .05, .075, .10, .15, .20, .25,
            .35, .45, .55, .65, .70, .75, .80, .85, .90, .95, 1
    };
    private static final double[] NOISE_MEASURED_LOUDNESS = {
            -42.1, -42.1, -41.57, -40.36, -38.63, -35.96, -34.24,
            -30.56, -28.30, -26.78, -26.65, -26.32, -26.07, -25.73,
            -25.23, -23.44, -21.61, -20.14, -19.03, -18.17, -17.55
    };
    private static final double[] NOISE_SYNTH_POSITION = new double[1001];
    private static final double[] NOISE_LEVEL_GAIN = new double[1001];
    static {
        // A 27-position line-input recording showed that the old raw-energy curve
        // still differed by about 24 LU from sub-Brownian to White. Preserve the
        // dark end's headroom with only 1.5 dB of boost and obtain most of the
        // improvement by attenuating the perceptually louder colors. Applying 85%
        // of the measured correction leaves a gentle difference of roughly 4 LU.
        final double referenceLoudness = -26.31;
        final double correctionAmount = 0.85;
        final double darkestBoostDb = 1.5;
        final double darkestCorrection = referenceLoudness - NOISE_MEASURED_LOUDNESS[0];
        final double commonOffsetDb = correctionAmount * darkestCorrection - darkestBoostDb;
        for (int i = 0; i <= 1000; i++) {
            double uiPosition = i / 1000.0;
            // Spread the former 0-1% character over about 0-7%, while retaining
            // the exact sub-Brownian and Brownian endpoints at 0% and 25%.
            double synthPosition = uiPosition;
            if (uiPosition < 0.25) {
                double t = uiPosition / 0.25;
                synthPosition = 0.25 * t * t * Math.sqrt(t); // t^2.5
            }
            NOISE_SYNTH_POSITION[i] = synthPosition;
            double measuredLoudness = interpolateNoiseLoudness(synthPosition);
            double correctionDb = correctionAmount * (referenceLoudness - measuredLoudness)
                    - commonOffsetDb;
            double oldSpectralGain = 2.5 - 1.5 * synthPosition;
            NOISE_LEVEL_GAIN[i] = oldSpectralGain * Math.pow(10, correctionDb / 20.0);
        }
    }
    private final Random random = new Random();
    private volatile boolean running;
    private volatile boolean masterMuted;
    private volatile boolean stopping;
    private volatile double masterTarget = 1;
    private double masterGain;
    void setMasterMuted(boolean muted) {
        masterMuted = muted;
        masterTarget = muted ? 0 : 1;
    }
    private Thread worker;
    private AudioTrack track;
    private List<Voice> voices = new ArrayList<>();

    private static double interpolateNoiseLoudness(double position) {
        for (int i = 1; i < NOISE_MEASURED_POSITIONS.length; i++) {
            if (position <= NOISE_MEASURED_POSITIONS[i]) {
                double lowPosition = NOISE_MEASURED_POSITIONS[i - 1];
                double highPosition = NOISE_MEASURED_POSITIONS[i];
                double t = (position - lowPosition) / (highPosition - lowPosition);
                // Smoothstep avoids abrupt gain-slope changes at measurement anchors.
                t = t * t * (3 - 2 * t);
                return NOISE_MEASURED_LOUDNESS[i - 1]
                        + (NOISE_MEASURED_LOUDNESS[i] - NOISE_MEASURED_LOUDNESS[i - 1]) * t;
            }
        }
        return NOISE_MEASURED_LOUDNESS[NOISE_MEASURED_LOUDNESS.length - 1];
    }

    synchronized void setLayers(List<Layer> layers) {
        List<Voice> next = new ArrayList<>();
        for (Layer layer : layers) {
            Voice voice = null;
            for (Voice old : voices) if (old.id == layer.id) { voice = old; break; }
            if (voice == null) voice = new Voice(layer.id, layer.type, layer.pan);
            voice.layer = layer;
            next.add(voice);
        }
        voices = next;
    }
    synchronized boolean start() {
        if (running) return true;
        int minimum = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) return false;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(Math.max(minimum, 4096 * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release(); track = null; return false;
            }
            masterGain = 0;
            masterTarget = masterMuted ? 0 : 1;
            stopping = false;
            track.play(); running = true;
            worker = new Thread(this::render, "Structura Sonorum audio"); worker.start();
            return true;
        } catch (RuntimeException ex) {
            if (track != null) { track.release(); track = null; }
            return false;
        }
    }
    void stop() {
        Thread thread;
        synchronized (this) {
            stopping = true;
            masterTarget = 0;
            thread = worker;
        }
        if (thread != null && thread != Thread.currentThread()) {
            try { thread.join(1500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }
        synchronized (this) {
            running = false;
            if (track != null) { track.pause(); track.flush(); track.release(); track = null; }
            worker = null;
        }
    }
    private void render() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        short[] pcm = new short[1024 * 2];
        long submittedFrames = 0;
        while (running) {
            boolean finishAfterBuffer = false;
            synchronized (this) {
                for (int i = 0; i < pcm.length; i += 2) {
                    double sumLeft = 0, sumRight = 0;
                    for (Voice v : voices) {
                        double mono = v.sample(random);
                        v.pan += (v.layer.pan - v.pan) * PAN_SMOOTHING;
                        double angle = v.pan / 200.0 * Math.PI / 2;
                        // Equal-power pan, normalized so center retains the previous level.
                        sumLeft += mono * Math.cos(angle) * Math.sqrt(2);
                        sumRight += mono * Math.sin(angle) * Math.sqrt(2);
                    }
                    if (masterGain < masterTarget)
                        masterGain = Math.min(masterTarget, masterGain + 1.0 / FADE_IN_SAMPLES);
                    else if (masterGain > masterTarget)
                        masterGain = Math.max(masterTarget, masterGain - 1.0 / FADE_OUT_SAMPLES);
                    if (stopping && masterGain <= 0) finishAfterBuffer = true;
                    double left = Math.tanh(sumLeft * 0.55) * 0.65 * masterGain;
                    double right = Math.tanh(sumRight * 0.55) * 0.65 * masterGain;
                    pcm[i] = (short) Math.round(left * 32767);
                    pcm[i + 1] = (short) Math.round(right * 32767);
                }
            }
            AudioTrack current = track;
            if (current == null) break;
            int offset = 0;
            while (running && offset < pcm.length) {
                int count = current.write(pcm, offset, pcm.length - offset);
                if (count <= 0) { running = false; break; }
                offset += count;
                submittedFrames += count / 2;
            }
            if (finishAfterBuffer) {
                // MODE_STREAM writes are queued. Wait until the final silent end of
                // the fade reaches the device before stop() is allowed to flush it.
                waitForPlayback(current, submittedFrames);
                running = false;
            }
        }
    }
    private void waitForPlayback(AudioTrack current, long submittedFrames) {
        long target = submittedFrames & 0xffffffffL;
        long deadline = System.nanoTime() + 1_000_000_000L;
        while (System.nanoTime() < deadline) {
            long played = Integer.toUnsignedLong(current.getPlaybackHeadPosition());
            long remaining = (target - played) & 0xffffffffL;
            if (remaining == 0 || remaining > 0x80000000L) return;
            try { Thread.sleep(2); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
        }
    }
    private static final class Pulse {
        int age;
        double brightness, duration, polarity;
        Pulse(double brightness, double duration, double polarity) {
            this.brightness = brightness; this.duration = duration; this.polarity = polarity;
        }
    }
    private static final class Voice {
        final long id;
        final int type;
        Layer layer;
        double gain, pan, phase, lfoPhase, brown, pink, ultraBrown;
        double noisePosition = Double.NaN, noiseLevelGain = Double.NaN;
        double clickCountdown;
        final List<Pulse> pulses = new ArrayList<>();
        Voice(long id, int type, int pan) { this.id = id; this.type = type; this.pan = pan; }
        double sample(Random random) {
            Layer l = layer;
            double target = l.muted ? 0 : Math.pow(l.volume / 1000.0, 2) * 0.52;
            gain += (target - gain) * 0.0015;
            if (type == Layer.SINE) {
                lfoPhase += 2 * Math.PI * l.fluctuationRate() / RATE;
                if (lfoPhase >= 2 * Math.PI) lfoPhase -= 2 * Math.PI;
                double hz = Math.max(1, l.frequency() + l.fluctuation * Math.sin(lfoPhase));
                phase += 2 * Math.PI * hz / RATE;
                if (phase >= 2 * Math.PI) phase -= 2 * Math.PI;
                return Math.sin(phase) * gain;
            }
            if (type == Layer.NOISE) {
                double white = random.nextDouble() * 2 - 1;
                pink = 0.985 * pink + 0.015 * white;
                brown = Math.max(-1, Math.min(1, brown * 0.998 + white * 0.035));
                // An additional slow low-pass stage makes the far left sub-Brownian.
                ultraBrown += 0.00035 * (brown - ultraBrown);
                int color = Math.max(0, Math.min(1000, l.parameter));
                double targetPosition = NOISE_SYNTH_POSITION[color];
                double targetLevelGain = NOISE_LEVEL_GAIN[color];
                if (Double.isNaN(noisePosition)) {
                    noisePosition = targetPosition;
                    noiseLevelGain = targetLevelGain;
                } else {
                    noisePosition += (targetPosition - noisePosition) * NOISE_COLOR_SMOOTHING;
                    noiseLevelGain += (targetLevelGain - noiseLevelGain) * NOISE_COLOR_SMOOTHING;
                }
                double position = noisePosition;
                double value;
                if (position < 0.25) {
                    double blend = position * 4;
                    value = ultraBrown * 3 * (1 - blend) + brown * blend;
                } else if (position < 0.65) {
                    double blend = (position - 0.25) / 0.4;
                    value = brown * (1 - blend) + pink * 5 * blend;
                } else {
                    double blend = (position - 0.65) / 0.35;
                    value = pink * 5 * (1 - blend) + white * blend;
                }
                return value * gain * noiseLevelGain;
            }
            if (--clickCountdown <= 0) {
                double jitter = -Math.log(Math.max(1e-9, random.nextDouble()));
                double interval = 1 + (jitter - 1) * l.randomness / 100.0;
                clickCountdown = Math.max(1, RATE * 60.0 / (20 + l.speed / 10.0) * interval);
                if (pulses.size() < 48) pulses.add(new Pulse(l.brightness / 100.0,
                        l.decaySeconds(), random.nextBoolean() ? 1 : -1));
            }
            double result = 0;
            for (int i = pulses.size() - 1; i >= 0; i--) {
                Pulse p = pulses.get(i);
                double t = p.age++ / (double) RATE;
                if (t >= p.duration) { pulses.remove(i); continue; }
                // A brief transient blends into a low drum-like tone at the dark end.
                double thud = Math.sin(2 * Math.PI * 65 * t) * (1 - p.brightness);
                double transientPart = p.brightness * Math.exp(-t * 5000) * p.polarity;
                result += (thud + transientPart) * Math.exp(-6.9 * t / p.duration);
            }
            return result * gain;
        }
    }
}
