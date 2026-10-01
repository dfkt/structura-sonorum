package observer.noisetriangle;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(31, 29, 28);
    private static final int CONTROL_RED = 0xff704348;
    private static final int[] COLORS = {0xff665677, 0xffA66A45, 0xff6F8A68};
    private List<Layer> layers;
    private LinearLayout list;
    private ScrollView scroll;
    private long focusLayerId = Long.MIN_VALUE;
    private ImageButton play;
    private boolean playing;
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            playing = intent.getBooleanExtra("playing", false); updateButton();
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        layers = Layer.decode(getPreferences(MODE_PRIVATE).getString("layers", null));
        buildUi();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !getPreferences(MODE_PRIVATE).getBoolean("notificationExplanationShown", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationExplanationShown", true).apply();
            new AlertDialog.Builder(this)
                    .setTitle("Allow playback controls?")
                    .setMessage("Structura Sonorum uses notifications only to keep audio playback available in the background and to provide mute/unmute, resume, and Exit controls. It does not send promotional notifications.")
                    .setNegativeButton("Not now", (dialog, which) -> dialog.dismiss())
                    .setPositiveButton("Continue", (dialog, which) -> requestPermissions(
                            new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1))
                    .show();
        }
    }
    @Override public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(AudioService.STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(stateReceiver, filter);
        playing = AudioService.isPlaying; updateButton();
    }
    @Override public void onStop() { unregisterReceiver(stateReceiver); super.onStop(); }
    private void buildUi() {
        LinearLayout root = column();
        root.setPadding(dp(18), dp(18), dp(18), dp(10));
        root.setBackgroundColor(BACKGROUND); setContentView(root);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout headings = column();
        header.addView(headings, new LinearLayout.LayoutParams(0, -2, 1));
        headings.addView(label("Structura Sonorum", 26));
        TextView subtitle = label("Ex minimis rerum audibilium elementis orior", 13);
        subtitle.setTextColor(0xffbcb6b0); headings.addView(subtitle);
        ImageButton logo = new ImageButton(this);
        logo.setImageResource(R.drawable.ic_triangle_header);
        logo.setBackground(pressBackground(0x001f1d1c, 0xff544643));
        logo.setContentDescription("About Structura Sonorum");
        logo.setPadding(0, 0, 0, 0);
        logo.setTranslationY(dp(6));
        header.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));
        logo.setOnClickListener(v -> showAbout());
        LinearLayout controls = new LinearLayout(this);
        LinearLayout.LayoutParams controlRow = new LinearLayout.LayoutParams(-1, dp(56));
        controlRow.setMargins(0, dp(18), 0, dp(14)); root.addView(controls, controlRow);
        play = iconButton(R.drawable.ic_play, "Play", CONTROL_RED);
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(0, -1, 2);
        playParams.setMargins(0, 0, dp(3), 0); controls.addView(play, playParams);
        ImageButton reset = iconButton(R.drawable.ic_reset, "Reset", CONTROL_RED);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, -1, 1);
        resetParams.setMargins(dp(3), 0, dp(3), 0); controls.addView(reset, resetParams);
        ImageButton export = iconButton(R.drawable.ic_import_export, "Import or export configuration", CONTROL_RED);
        LinearLayout.LayoutParams exportParams = new LinearLayout.LayoutParams(0, -1, 1);
        exportParams.setMargins(dp(3), 0, 0, 0); controls.addView(export, exportParams);
        export.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Configuration")
                .setItems(new String[]{"Import configuration", "Export configuration"},
                        (dialog, choice) -> {
                            if (choice == 0) chooseImport();
                            else exportConfiguration();
                        }).show());
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to reset the application to its initial state?")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Reset", (dialog, which) -> {
                    playing = false;
                    startService(new Intent(this, AudioService.class).setAction(AudioService.STOP));
                    layers = Layer.defaults(); changed(true); updateButton();
                }).show());
        play.setOnClickListener(v -> {
            if (playing) {
                playing = false;
                startService(new Intent(this, AudioService.class).setAction(AudioService.PAUSE));
            } else {
                playing = true;
                startForegroundService(new Intent(this, AudioService.class)
                        .setAction(AudioService.SYNC).putExtra(AudioService.JSON, Layer.encode(layers)));
            }
            updateButton();
        });
        scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.setMargins(0, 0, 0, dp(14));
        root.addView(scroll, scrollParams);
        list = column(); scroll.addView(list); renderLayers();
        LinearLayout add = new LinearLayout(this); add.setGravity(Gravity.CENTER);
        root.addView(add);
        for (int type = 0; type < 3; type++) {
            final int selected = type;
            Button plus = button("+ " + name(type), COLORS[type]);
            plus.setTextSize(17);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1);
            params.setMargins(type == 0 ? 0 : dp(3), 0,
                    type == 2 ? 0 : dp(3), 0); add.addView(plus, params);
            plus.setOnClickListener(v -> {
                if (layers.size() >= 48) return;
                int position = 0;
                for (int i = 0; i < layers.size(); i++)
                    if (layers.get(i).type <= selected) position = i + 1;
                Layer added = new Layer(selected);
                layers.add(position, added);
                focusLayerId = added.id;
                changed(true);
            });
        }
    }
    private String exportName() {
        return "△-" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
                .format(new Date()) + ".json";
    }
    private byte[] exportBytes() throws org.json.JSONException {
        JSONObject backup = new JSONObject();
        backup.put("format", "Structura Sonorum configuration");
        backup.put("formatVersion", 1);
        backup.put("appVersion", getPackageVersion());
        backup.put("generators", new JSONArray(Layer.encode(layers)));
        return backup.toString(2).getBytes(StandardCharsets.UTF_8);
    }
    private void exportConfiguration() {
        if (Build.VERSION.SDK_INT < 29) {
            Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            create.addCategory(Intent.CATEGORY_OPENABLE);
            create.setType("application/json");
            create.putExtra(Intent.EXTRA_TITLE, exportName());
            startActivityForResult(create, 42);
            return;
        }
        Uri uri = null;
        try {
            byte[] data = exportBytes();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, exportName());
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Could not create file");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("Could not open file");
                out.write(data);
            }
            ContentValues published = new ContentValues();
            published.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, published, null, null);
            Toast.makeText(this, "Configuration saved to Downloads", Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            if (uri != null) getContentResolver().delete(uri, null, null);
            Toast.makeText(this, "Could not export configuration", Toast.LENGTH_LONG).show();
        }
    }
    private void chooseImport() {
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        open.addCategory(Intent.CATEGORY_OPENABLE);
        open.setType("application/json");
        startActivityForResult(open, 43);
    }
    private void importConfiguration(Uri uri) {
        try {
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Could not open file");
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (bytes.size() + count > 1048576) throw new IOException("File is too large");
                    bytes.write(buffer, 0, count);
                }
            }
            JSONObject backup = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            if (!"Structura Sonorum configuration".equals(backup.optString("format"))
                    || backup.getInt("formatVersion") != 1) throw new IOException("Unknown format");
            // Accept the pre-1.9 key as well, so existing backups remain importable.
            JSONArray items = backup.optJSONArray("generators");
            if (items == null) items = backup.getJSONArray("layers");
            if (items.length() > 48) throw new IOException("Too many generators");
            HashSet<Long> ids = new HashSet<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                int type = item.getInt("type");
                if (type < 0 || type > 2 || !item.has("volume") || !item.has("parameter")
                        || !ids.add(item.getLong("id"))) throw new IOException("Invalid generator");
                item.getInt("volume"); item.getInt("parameter");
            }
            List<Layer> imported = Layer.decode(items.toString());
            if (imported.size() != items.length()) throw new IOException("Invalid generator data");
            new AlertDialog.Builder(this)
                    .setMessage("Replace your current configuration with the one in this file?")
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .setPositiveButton("Import", (dialog, which) -> {
                        // Replace the service state in order. If sound was already
                        // playing, SYNC starts the imported configuration after the
                        // old one has completed its fade-out.
                        boolean resumeAfterImport = playing;
                        startService(new Intent(this, AudioService.class).setAction(AudioService.STOP));
                        layers = imported;
                        playing = resumeAfterImport;
                        changed(true);
                        updateButton();
                        scroll.post(() -> scroll.scrollTo(0, 0));
                        Toast.makeText(this, "Configuration imported", Toast.LENGTH_SHORT).show();
                    }).show();
        } catch (Exception ex) {
            new AlertDialog.Builder(this).setMessage("This is not a valid Structura Sonorum configuration file.")
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss()).show();
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == 43) { importConfiguration(data.getData()); return; }
        if (requestCode != 42) return;
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new IOException("Could not open file");
            out.write(exportBytes());
            Toast.makeText(this, "Configuration exported", Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(this, "Could not export configuration", Toast.LENGTH_LONG).show();
        }
    }
    private void showAbout() {
        String url = "https://dfkt.at/";
        String content = "Structura Sonorum v" + getPackageVersion()
                + "\nBuilt 2026 by DFKT\n\n" + url;
        SpannableString linked = new SpannableString(content);
        linked.setSpan(new URLSpan(url), content.lastIndexOf(url), content.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        AlertDialog dialog = new AlertDialog.Builder(this).setMessage(linked)
                .setPositiveButton("Close", (d, which) -> d.dismiss()).create();
        dialog.show();
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setGravity(Gravity.CENTER);
            message.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }
    private String getPackageVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException ex) { return "1.12"; }
    }
    private void renderLayers() {
        list.removeAllViews();
        // Group by type even for settings saved by the first version.
        for (int type = 0; type < 3; type++) for (Layer layer : layers) {
            if (layer.type != type) continue;
            LinearLayout card = column();
            card.setPadding(dp(12), dp(8), dp(12), dp(12));
            card.setBackground(rounded(COLORS[type]));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.setMargins(0, 0, 0, dp(12)); list.addView(card, cardParams);
            LinearLayout heading = new LinearLayout(this);
            heading.setGravity(Gravity.CENTER_VERTICAL); card.addView(heading);
            heading.addView(label(name(type), 19), new LinearLayout.LayoutParams(0, -2, 1));
            ImageButton mute = iconButton(layer.muted ? R.drawable.ic_play : R.drawable.ic_pause,
                    layer.muted ? "Unmute" : "Mute", buttonShade(type));
            LinearLayout.LayoutParams muteParams = new LinearLayout.LayoutParams(dp(54), dp(48));
            muteParams.setMargins(0, 0, dp(6), 0); heading.addView(mute, muteParams);
            ImageButton remove = iconButton(R.drawable.ic_remove, "Remove " + name(type), buttonShade(type));
            heading.addView(remove, new LinearLayout.LayoutParams(dp(54), dp(48)));
            mute.setOnClickListener(v -> {
                layer.muted = !layer.muted;
                mute.setImageResource(layer.muted ? R.drawable.ic_play : R.drawable.ic_pause);
                mute.setContentDescription(layer.muted ? "Unmute" : "Mute");
                updateCardAppearance(card, layer, mute, remove);
                changed(false);
            });
            remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage("Do you really want to remove this generator?")
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .setPositiveButton("Remove", (dialog, which) -> {
                        layers.remove(layer); changed(true);
                    }).show());
            addSlider(card, "Volume", layer.volume, 1000, x -> {
                layer.volume = x;
                return String.format(Locale.US, "%.1f%%", x / 10.0);
            }, numeric(0, 100, "%", x -> x / 10.0, x -> (int)Math.round(x * 10)));
            addSlider(card, "Pan", layer.pan, 200, x -> {
                layer.pan = x;
                int pan = x - 100;
                if (pan == 0) return "center";
                return String.format(Locale.US, "%+d%% (%s)", pan, pan < 0 ? "L" : "R");
            }, numeric(-100, 100, "%", x -> x - 100, x -> (int)Math.round(x + 100)));
            if (type == Layer.NOISE) {
                addSlider(card, "Color", layer.parameter, 1000, x -> {
                    layer.parameter = x;
                    if (x == 0) return "0% · sub-Brownian / dark";
                    if (x == 250) return "25% · Brownian";
                    if (x == 650) return "65% · Pink";
                    if (x == 1000) return "100% · White / bright";
                    return String.format(Locale.US, "%.1f%%", x / 10.0);
                }, numeric(0, 100, "%", x -> x / 10.0, x -> (int)Math.round(x * 10)));
            } else if (type == Layer.CLICK) {
                addSlider(card, "Speed", layer.speed, 3800, x -> {
                    layer.speed = x;
                    double bpm = 20 + x / 10.0;
                    return (x % 10 == 0 ? String.format(Locale.US, "%.0f", bpm)
                            : String.format(Locale.US, "%.1f", bpm)) + " BPM";
                }, numeric(20, 400, "BPM", x -> 20 + x / 10.0,
                        x -> (int)Math.round((x - 20) * 10)));
                addSlider(card, "Randomness", layer.randomness, 100, x -> {
                    layer.randomness = x; return x + "%";
                }, numeric(0, 100, "%", x -> x, x -> (int)Math.round(x)));
                addSlider(card, "Brightness", layer.brightness, 100, x -> {
                    layer.brightness = x; return x + "%";
                }, numeric(0, 100, "%", x -> x, x -> (int)Math.round(x)));
                addSlider(card, "Decay", layer.decay, 100, x -> {
                    layer.decay = x; return String.format(Locale.US, "%.3f s", layer.decaySeconds());
                }, numeric(0.003, 10, "s",
                        x -> x == 0 ? 0.003 : 0.003 * Math.pow(10.0 / 0.003, x / 100.0),
                        x -> (int)Math.round(Math.log(x / 0.003) / Math.log(10.0 / 0.003) * 100)));
            } else {
                final TextView[] frequencyLabels = new TextView[2];
                frequencyLabels[0] = addSlider(card, "Frequency · coarse", layer.parameter, 100, x -> {
                    layer.parameter = x;
                    updateSineLabels(layer, frequencyLabels);
                    return coarseFrequencyText(layer);
                }, numeric(25, 15000, "Hz", x -> 25 * Math.pow(600, x / 100.0),
                        x -> (int)Math.round(Math.log(x / 25) / Math.log(600) * 100)));
                frequencyLabels[1] = addSlider(card, "Frequency · fine", layer.fine, 500, x -> {
                    layer.fine = x;
                    updateSineLabels(layer, frequencyLabels);
                    return fineFrequencyText(layer);
                }, numeric(-25, 25, "Hz", x -> (x - 250) / 10.0,
                        x -> (int)Math.round(x * 10 + 250)));
                updateSineLabels(layer, frequencyLabels);
                addSlider(card, "Fluctuation", layer.fluctuation, 100, x -> {
                    layer.fluctuation = x; return "±" + x + " Hz";
                }, numeric(0, 100, "Hz", x -> x, x -> (int)Math.round(x)));
                addSlider(card, "Fluctuation speed", layer.fluctuationSpeed, 100, x -> {
                    layer.fluctuationSpeed = x;
                    double hz = layer.fluctuationRate();
                    return String.format(Locale.US, "%.2f Hz · %.1f BPM", hz, hz * 60);
                }, numeric(0.1, 100, "Hz", x -> 0.1 * Math.pow(1000, x / 100.0),
                        x -> (int)Math.round(Math.log(x / 0.1) / Math.log(1000) * 100)));
            }
            updateCardAppearance(card, layer, mute, remove);
            if (layer.id == focusLayerId) {
                focusLayerId = Long.MIN_VALUE;
                card.post(() -> scroll.smoothScrollTo(0, card.getTop()));
            }
        }
        // The fixed gap below the scroll view supplies the last card's bottom spacing.
        if (list.getChildCount() > 0) {
            android.view.View last = list.getChildAt(list.getChildCount() - 1);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) last.getLayoutParams();
            params.bottomMargin = 0;
            last.setLayoutParams(params);
        }
    }
    private static String coarseFrequencyText(Layer layer) {
        return String.format(Locale.US, "%.1f Hz", 25 * Math.pow(600, layer.parameter / 100.0));
    }
    private static String fineFrequencyText(Layer layer) {
        return String.format(Locale.US, "%+.1f Hz · total %.1f Hz",
                (layer.fine - 250) / 10.0, layer.frequency());
    }
    private void updateSineLabels(Layer layer, TextView[] labels) {
        if (labels[0] != null) {
            labels[0].setText("Frequency · coarse · " + coarseFrequencyText(layer));
            labels[0].setContentDescription(labels[0].getText() + ", tap to edit value");
        }
        if (labels[1] != null) {
            labels[1].setText("Frequency · fine · " + fineFrequencyText(layer));
            labels[1].setContentDescription(labels[1].getText() + ", tap to edit value");
        }
    }
    private interface Setting { String set(int value); }
    private interface ProgressNumber { double get(int progress); }
    private interface NumberProgress { int get(double number); }
    private static final class NumericInput {
        final double min, max;
        final String unit;
        final ProgressNumber fromProgress;
        final NumberProgress toProgress;
        NumericInput(double min, double max, String unit,
                ProgressNumber fromProgress, NumberProgress toProgress) {
            this.min = min; this.max = max; this.unit = unit;
            this.fromProgress = fromProgress; this.toProgress = toProgress;
        }
    }
    private static NumericInput numeric(double min, double max, String unit,
            ProgressNumber fromProgress, NumberProgress toProgress) {
        return new NumericInput(min, max, unit, fromProgress, toProgress);
    }
    private TextView addSlider(LinearLayout card, String title, int value, int max,
            Setting setting, NumericInput numeric) {
        TextView caption = label(title + " · " + setting.set(value), 14);
        caption.setContentDescription(caption.getText() + ", tap to edit value");
        card.addView(caption);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max); bar.setProgress(value);
        bar.setProgressTintList(ColorStateList.valueOf(0xffe2dcd6));
        bar.setThumbTintList(ColorStateList.valueOf(0xfff4eee8));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xffb5aaa5));
        card.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int v, boolean user) {
                if (user) {
                    caption.setText(title + " · " + setting.set(v));
                    caption.setContentDescription(caption.getText() + ", tap to edit value");
                    changed(false);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        });
        caption.setOnClickListener(v -> showNumberEditor(title, caption, bar, setting, numeric));
        return caption;
    }
    private void showNumberEditor(String title, TextView caption, SeekBar bar,
            Setting setting, NumericInput numeric) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(editableNumber(numeric.fromProgress.get(bar.getProgress())));
        int pad = dp(20);
        LinearLayout holder = column();
        holder.setPadding(pad, 0, pad, 0);
        LinearLayout entry = new LinearLayout(this);
        entry.setGravity(Gravity.CENTER_VERTICAL);
        entry.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        if (numeric.min < 0) {
            Button sign = new Button(this);
            sign.setText("±");
            sign.setContentDescription("Change sign");
            sign.setOnClickListener(v -> {
                String value = input.getText().toString().trim();
                if (value.startsWith("-")) value = value.substring(1);
                else if (!value.isEmpty() && !"0".equals(value)) value = "-" + value;
                input.setText(value); input.setSelection(value.length());
            });
            entry.addView(sign, new LinearLayout.LayoutParams(dp(56), -2));
        }
        holder.addView(entry, new LinearLayout.LayoutParams(-1, -2));
        String range = "Enter a value from " + editableNumber(numeric.min) + " to "
                + editableNumber(numeric.max) + (numeric.unit.isEmpty() ? "." : " " + numeric.unit + ".");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title).setMessage(range).setView(holder)
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .setPositiveButton("Set", null).create();
        dialog.setOnShowListener(unused -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    double number = Double.parseDouble(input.getText().toString().trim().replace(',', '.'));
                    if (!Double.isFinite(number) || number < numeric.min || number > numeric.max) {
                        input.setError(range); return;
                    }
                    int progress = Math.max(0, Math.min(bar.getMax(), numeric.toProgress.get(number)));
                    bar.setProgress(progress);
                    caption.setText(title + " · " + setting.set(progress));
                    caption.setContentDescription(caption.getText() + ", tap to edit value");
                    changed(false);
                    dialog.dismiss();
                } catch (NumberFormatException ex) { input.setError("Enter a numerical value."); }
            });
            input.requestFocus();
            if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }
    private static String editableNumber(double value) {
        String text = String.format(Locale.US, "%.3f", value);
        while (text.contains(".") && text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }
    private void changed(boolean rebuild) {
        getPreferences(MODE_PRIVATE).edit().putString("layers", Layer.encode(layers)).apply();
        if (rebuild) renderLayers();
        if (playing) startService(new Intent(this, AudioService.class)
                .setAction(AudioService.SYNC).putExtra(AudioService.JSON, Layer.encode(layers)));
    }
    private void updateButton() {
        if (play != null) {
            play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            play.setContentDescription(playing ? "Pause" : "Play");
        }
    }
    private static String name(int type) {
        return type == Layer.NOISE ? "Noise" : type == Layer.CLICK ? "Impulse" : "Sine";
    }
    private int buttonShade(int type) {
        int c = COLORS[type];
        return Color.rgb((int)(Color.red(c) * .73), (int)(Color.green(c) * .73),
                (int)(Color.blue(c) * .73));
    }
    private static int shade(int color, double amount) {
        return Color.rgb(Math.min(255, (int)(Color.red(color) * amount)),
                Math.min(255, (int)(Color.green(color) * amount)),
                Math.min(255, (int)(Color.blue(color) * amount)));
    }
    private void updateCardAppearance(LinearLayout card, Layer layer, ImageButton mute, ImageButton remove) {
        int base = COLORS[layer.type];
        card.setBackground(rounded(layer.muted ? shade(base, .63) : base));
        int buttonColor = layer.muted ? shade(buttonShade(layer.type), .7) : buttonShade(layer.type);
        int pressedColor = shade(layer.muted ? shade(base, .63) : base, 1.18);
        mute.setBackground(pressBackground(buttonColor, pressedColor));
        remove.setBackground(pressBackground(buttonColor, pressedColor));
        int foreground = layer.muted ? 0xffc3bcb8 : Color.WHITE;
        mute.setImageTintList(ColorStateList.valueOf(foreground));
        remove.setImageTintList(ColorStateList.valueOf(foreground));
        tintCardContents(card, foreground);
    }
    private void tintCardContents(ViewGroup group, int color) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) ((TextView) child).setTextColor(color);
            if (child instanceof SeekBar) {
                SeekBar seek = (SeekBar) child;
                seek.setProgressTintList(ColorStateList.valueOf(color));
                seek.setThumbTintList(ColorStateList.valueOf(color));
                seek.setProgressBackgroundTintList(ColorStateList.valueOf(
                        color == Color.WHITE ? 0xffb5aaa5 : 0xff847b79));
            }
            if (child instanceof ViewGroup) tintCardContents((ViewGroup) child, color);
        }
    }
    private StateListDrawable pressBackground(int normal, int pressed) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, rounded(pressed));
        states.addState(new int[]{}, rounded(normal));
        return states;
    }
    private GradientDrawable rounded(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(4)); return shape;
    }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); return layout;
    }
    private TextView label(String text, int sp) {
        TextView label = new TextView(this);
        label.setText(text); label.setTextSize(sp); label.setTextColor(Color.WHITE); return label;
    }
    private Button button(String text, int color) {
        Button button = new Button(this);
        button.setText(text); button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackground(pressBackground(color, shade(color, 1.35)));
        return button;
    }
    private ImageButton iconButton(int icon, String description, int color) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setScaleType(ImageButton.ScaleType.CENTER);
        button.setContentDescription(description);
        button.setBackground(pressBackground(color, shade(color, 1.35)));
        return button;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
