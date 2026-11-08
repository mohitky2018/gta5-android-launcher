package io.github.playgta5.launcher.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;

import io.github.playgta5.launcher.GameActivity;
import io.github.playgta5.launcher.R;
import io.github.playgta5.launcher.settings.GameSettings;
import io.github.playgta5.launcher.utils.GameDirectoryResolver;
import io.github.playgta5.launcher.utils.PathUtils;

/**
 * Hosts the local GameServer while the game runs in Chrome. The game cannot run in Android WebView (no cross-origin isolation, so no
 * SharedArrayBuffer for the engine's threads), so the launcher is in the background during play; a foreground service keeps the system
 * (MIUI in particular) from killing the server, which would stop the game's streaming reads.
 */
public class GameServerService extends Service {
    private static final String TAG = "GTA5ServerService";
    private static final String CHANNEL_ID = "game_server";
    private static final int NOTIFICATION_ID = 1;
    public static final String ACTION_STOP = "io.github.playgta5.launcher.STOP_SERVER";

    private static volatile String baseUrl;		// set while the server is listening
    private static volatile String startError;

    private GameServer server;
    private PowerManager.WakeLock wakeLock;

    public static String getBaseUrl() {
        return baseUrl;
    }

    public static String getStartError() {
        return startError;
    }

    public static void start(Context context) {
        startError = null;
        Intent intent = new Intent(context, GameServerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, GameServerService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        goForeground();
        if (server == null) {
            try {
                File[] dirs = resolveDirs();
                Log.i(TAG, "Starting GameServer with dataDir=" + dirs[0] + ", webRootDir=" + dirs[1]);
                server = new GameServer(this, dirs[0], dirs[1]);
                server.start();
                baseUrl = server.getBaseUrl();
                acquireWakeLock();
            } catch (Throwable t) {
                Log.e(TAG, "Game server failed to start", t);
                startError = String.valueOf(t.getMessage());
                server = null;
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private File[] resolveDirs() {
        GameSettings settings = new GameSettings(this);
        File userDir = null;
        String path = settings.getGameDirectoryPath();
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) {
                userDir = f;
            } else {
                try {
                    File resolved = PathUtils.getFileFromUri(this, Uri.parse(path));
                    if (resolved != null && resolved.exists()) userDir = resolved;
                } catch (Throwable ignored) {}
            }
        }
        if (userDir == null) userDir = getExternalFilesDir(null);

        GameDirectoryResolver.ResolvedPaths res = GameDirectoryResolver.resolve(userDir);
        File dataDir = res.dataDir != null && res.dataDir.exists() ? res.dataDir : userDir;
        File webRootDir = res.webRootDir != null && res.webRootDir.exists() ? res.webRootDir : userDir;
        return new File[]{dataDir, webRootDir};
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Game server", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps the local game server running while you play in Chrome");
            nm.createNotificationChannel(ch);
        }
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, GameActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), piFlags);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, GameServerService.class).setAction(ACTION_STOP), piFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Notification n = b.setSmallIcon(R.drawable.icon_settings)
                .setContentTitle("GTA V game server running")
                .setContentText("Stop it when you are done playing.")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    // The CPU must keep serving game data while the screen is on in Chrome; released when the service stops.
    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GTA5Launcher:GameServer");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(6 * 60 * 60 * 1000L);		// upper bound: a forgotten server does not hold the CPU forever
            }
        } catch (Throwable t) {
            Log.w(TAG, "Wake lock unavailable", t);
        }
    }

    @Override
    public void onDestroy() {
        baseUrl = null;
        if (server != null) {
            server.stop();
            server = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
