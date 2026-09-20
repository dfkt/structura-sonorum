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
    private final Random random = new Random();
    private volatile boolean running;
    private volatile boolean masterMuted;
    void setMasterMuted(boolean muted) { masterMuted = muted; }
    private Thread worker;
    private AudioTrack track;
    private List<Voice> voices = new ArrayList<>();

    synchronized void setLayers(List<Layer> layers) {
        List<Voice> next = new ArrayList<>();
        for (Layer layer : layers) {
            Voice voice = null;
            for (Voice old : voices) if (old.id == layer.id) { voice = old; break; }
            if (voice == null) voice = new Voice(layer.id, layer.type);
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
            running = false; thread = worker;
            if (track != null) track.pause();
        }
        if (thread != null && thread != Thread.currentThread()) {
            try { thread.join(1500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }
        synchronized (this) {
            if (track != null) { track.flush(); track.release(); track = null; }
            worker = null;
        }
    }
    private void render() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        short[] pcm = new short[1024 * 2];
        while (running) {
            synchronized (this) {
                for (int i = 0; i < pcm.length; i += 2) {
                    double sumLeft = 0, sumRight = 0;
                    for (Voice v : voices) {
                        double mono = v.sample(random);
                        double angle = v.layer.pan / 200.0 * Math.PI / 2;
                        // Equal-power pan, normalized so center retains the previous level.
                        sumLeft += mono * Math.cos(angle) * Math.sqrt(2);
                        sumRight += mono * Math.sin(angle) * Math.sqrt(2);
                    }
                    double left = masterMuted ? 0 : Math.tanh(sumLeft * 0.55) * 0.65;
                    double right = masterMuted ? 0 : Math.tanh(sumRight * 0.55) * 0.65;
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
            }
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
        double gain, phase, lfoPhase, brown, pink, ultraBrown;
        double clickCountdown;
        final List<Pulse> pulses = new ArrayList<>();
        Voice(long id, int type) { this.id = id; this.type = type; }
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
                // An additional slow low-pass stage makes the far left darker than brown.
                ultraBrown += 0.00035 * (brown - ultraBrown);
                double position = l.parameter / 100.0;
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
                // Compensate for the lower perceived/RMS level of strongly filtered noise.
                double spectralGain = 2.5 - 1.5 * position;
                return value * gain * spectralGain;
            }
            if (--clickCountdown <= 0) {
                double jitter = -Math.log(Math.max(1e-9, random.nextDouble()));
                double interval = 1 + (jitter - 1) * l.randomness / 100.0;
                clickCountdown = Math.max(1, RATE * 60.0 / (20 + 180 * l.speed / 100.0) * interval);
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
