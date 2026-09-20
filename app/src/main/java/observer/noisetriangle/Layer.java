package observer.noisetriangle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class Layer {
    static final int NOISE = 0, CLICK = 1, SINE = 2;
    final int type;
    final long id;
    int volume, pan, parameter, speed, randomness, brightness, decay, fine, fluctuation, fluctuationSpeed;
    boolean muted;

    Layer(int type) { this(type, System.nanoTime()); }
    Layer(int type, long id) { this.type = type; this.id = id; fine = 250; pan = 100; }
    static List<Layer> defaults() {
        List<Layer> result = new ArrayList<>();
        for (int type = 0; type < 3; type++) result.add(new Layer(type));
        return result;
    }
    static String encode(List<Layer> layers) {
        JSONArray array = new JSONArray();
        for (Layer l : layers) {
            JSONObject o = new JSONObject();
            try {
                o.put("schema", 4); o.put("type", l.type); o.put("id", l.id); o.put("volume", l.volume);
                o.put("pan", l.pan);
                o.put("parameter", l.parameter); o.put("speed", l.speed);
                o.put("randomness", l.randomness); o.put("brightness", l.brightness);
                o.put("decay", l.decay); o.put("fine", l.fine);
                o.put("fluctuation", l.fluctuation); o.put("fluctuationSpeed", l.fluctuationSpeed);
                o.put("muted", l.muted);
            } catch (JSONException ignored) { }
            array.put(o);
        }
        return array.toString();
    }
    static List<Layer> decode(String json) {
        if (json == null) return defaults();
        try {
            JSONArray array = new JSONArray(json);
            List<Layer> result = new ArrayList<>();
            for (int i = 0; i < array.length() && i < 48; i++) {
                JSONObject o = array.getJSONObject(i);
                int type = o.getInt("type");
                if (type < NOISE || type > SINE) continue;
                Layer l = new Layer(type, o.optLong("id", i + 1));
                int schema = o.optInt("schema", 1);
                l.volume = schema >= 4 ? clamp(o.optInt("volume", 0), 1000)
                        : clamp(o.optInt("volume", 0) * 10, 1000);
                l.pan = schema >= 4 ? clamp(o.optInt("pan", 100), 200) : 100;
                l.parameter = clamp(o.optInt("parameter", 0), 100);
                // Preserve earlier click character approximately when migrating old settings.
                l.speed = clamp(o.optInt("speed", l.parameter), 100);
                l.randomness = clamp(o.optInt("randomness", l.parameter), 100);
                l.brightness = clamp(o.optInt("brightness", l.parameter), 100);
                l.decay = clamp(o.optInt("decay", 0), 100);
                int oldFine = schema >= 2 ? o.optInt("fine", 0)
                        : o.optInt("fine", 500) - 500;
                l.fine = schema >= 3 ? clamp(o.optInt("fine", 250), 500)
                        : clamp(250 + oldFine, 500);
                if (schema < 3 && type == SINE && oldFine > 250) {
                    double target = 25 * Math.pow(600, l.parameter / 100.0) + oldFine / 10.0;
                    double bestError = Double.MAX_VALUE;
                    for (int coarse = 0; coarse <= 100; coarse++) {
                        double base = 25 * Math.pow(600, coarse / 100.0);
                        int fine = clamp((int)Math.round((target - base) * 10) + 250, 500);
                        double error = Math.abs(target - Math.max(1, base + (fine - 250) / 10.0));
                        if (error < bestError) {
                            bestError = error; l.parameter = coarse; l.fine = fine;
                        }
                    }
                }
                l.fluctuation = clamp(o.optInt("fluctuation", 0), 100);
                l.fluctuationSpeed = clamp(o.optInt("fluctuationSpeed", 0), 100);
                l.muted = o.optBoolean("muted", false);
                result.add(l);
            }
            return result;
        } catch (JSONException ex) { return defaults(); }
    }
    private static int clamp(int value, int max) { return Math.max(0, Math.min(max, value)); }
    double frequency() { return Math.max(1, 25 * Math.pow(600, parameter / 100.0) + (fine - 250) / 10.0); }
    double decaySeconds() { return decay == 0 ? 0.003 : 0.003 * Math.pow(10.0 / 0.003, decay / 100.0); }
    double fluctuationRate() { return 0.1 * Math.pow(1000, fluctuationSpeed / 100.0); }
}
