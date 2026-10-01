package observer.noisetriangle;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;

public final class AudioService extends Service {
    static final String SYNC = "observer.noisetriangle.SYNC";
    static final String PAUSE = "observer.noisetriangle.PAUSE";
    static final String RESUME = "observer.noisetriangle.RESUME";
    static final String TOGGLE_MUTE = "observer.noisetriangle.TOGGLE_MUTE";
    static final String STOP = "observer.noisetriangle.STOP";
    static final String EXIT = "observer.noisetriangle.EXIT";
    static final String STATE = "observer.noisetriangle.STATE";
    static final String JSON = "layers";
    private static final String CHANNEL = "playback";
    static volatile boolean isPlaying;
    private final SynthEngine engine = new SynthEngine();
    private AudioManager audioManager;
    private boolean playing, muted, foreground;
    private String lastLayersJson;
    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (change <= 0) pausePlayback();
    };
    private final BroadcastReceiver unplugReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { pausePlayback(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "Structura Sonorum playback", NotificationManager.IMPORTANCE_LOW));
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(unplugReceiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(unplugReceiver, filter);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? STOP : intent.getAction();
        if (EXIT.equals(action)) {
            stopPlayback();
            ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            for (ActivityManager.AppTask task : manager.getAppTasks()) task.finishAndRemoveTask();
            return START_NOT_STICKY;
        }
        if (STOP.equals(action)) { stopPlayback(); return START_NOT_STICKY; }
        if (PAUSE.equals(action)) { pausePlayback(); return START_NOT_STICKY; }
        if (TOGGLE_MUTE.equals(action)) {
            muted = !muted;
            engine.setMasterMuted(muted);
            refreshNotification();
            return START_NOT_STICKY;
        }
        if (SYNC.equals(action) || RESUME.equals(action)) {
            if (SYNC.equals(action)) lastLayersJson = intent.getStringExtra(JSON);
            if (lastLayersJson == null) { stopPlayback(); return START_NOT_STICKY; }
            engine.setLayers(Layer.decode(lastLayersJson));
            if (!playing) {
                int focus = audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
                if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    broadcastState(false); return START_NOT_STICKY;
                }
                // Start foreground promptly before initializing audio.
                if (!foreground) { startForeground(1, notification()); foreground = true; }
                if (!engine.start()) { pausePlayback(); return START_NOT_STICKY; }
                playing = true; isPlaying = true; broadcastState(true);
            }
            refreshNotification();
        }
        return START_NOT_STICKY;
    }
    private void pausePlayback() {
        if (!foreground) return;
        engine.stop(); audioManager.abandonAudioFocus(focusListener);
        playing = false; isPlaying = false; broadcastState(false);
        refreshNotification();
    }
    private PendingIntent serviceAction(String action, int requestCode) {
        return PendingIntent.getService(this, requestCode, new Intent(this, AudioService.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification() {
        PendingIntent content = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_triangle_notification)
                .setContentTitle("Structura Sonorum")
                .setContentText(playing ? (muted ? "Muted" : "Playing") : "Paused · tap to open")
                .setContentIntent(content)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true);
        if (playing) {
            builder.addAction(new Notification.Action.Builder(muted ? R.drawable.ic_play : R.drawable.ic_pause,
                    muted ? "Unmute" : "Mute", serviceAction(TOGGLE_MUTE, 2)).build());
        } else {
            builder.addAction(new Notification.Action.Builder(R.drawable.ic_play,
                    "Play", serviceAction(RESUME, 3)).build());
        }
        builder.addAction(new Notification.Action.Builder(R.drawable.ic_remove,
                "Exit", serviceAction(EXIT, 1)).build());
        builder.setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1));
        return builder.build();
    }
    private void refreshNotification() {
        if (foreground) getSystemService(NotificationManager.class).notify(1, notification());
    }
    private void stopPlayback() {
        engine.stop(); audioManager.abandonAudioFocus(focusListener);
        playing = false; isPlaying = false; broadcastState(false);
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; }
        stopSelf();
    }
    private void broadcastState(boolean state) {
        Intent update = new Intent(STATE).setPackage(getPackageName()).putExtra("playing", state);
        sendBroadcast(update);
    }
    @Override public void onDestroy() {
        engine.stop(); audioManager.abandonAudioFocus(focusListener);
        isPlaying = false; unregisterReceiver(unplugReceiver);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
