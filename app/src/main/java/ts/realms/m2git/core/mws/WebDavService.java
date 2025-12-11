package ts.realms.m2git.core.mws;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ts.realms.m2git.R;
import ts.realms.m2git.ui.screens.settings.SettingsFragment;

public class WebDavService extends Service {
    private static final String CHANNEL_ID = "webdav_service";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "WebDavService";

    private SimpleMiltonServer server;
    private ExecutorService serverExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        server = SimpleMiltonServer.getInstance();
        createNotificationChannel();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        Log.i(TAG, "Service action: " + action);
        if ("START".equals(action)) {
            int port = intent.getIntExtra("PORT", 8080);
            String home = intent.getStringExtra("HOME");
            startForeground(NOTIFICATION_ID, createNotification(port, "正在启动..."));
            // 在后台线程启动服务器
            serverExecutor = Executors.newSingleThreadExecutor();
            serverExecutor.submit(() -> {
                try {
                    server.build(home, port);
                    server.start();
                    String ip = getLocalIpAddress();
                    updateNotification(port, "运行中", "http://" + ip + ":" + port);

                } catch (Exception e) {
                    Log.e(TAG, "服务器启动失败", e);
                    Log.e("WebDavService", "启动失败", e);
                    stopSelf();
                }
            });

        } else if ("STOP".equals(action)) {
            stopSelf();
        }
        return START_STICKY;
    }

    private Notification createNotification(int port, String status, String detail) {
        Intent stopIntent = new Intent(this, WebDavService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("M2Git WebDAV 服务器 - " + status)
            .setContentText(detail != null ? detail : status)
            .setSmallIcon(R.drawable.ic_logo) // 替换为你的图标
            .setOngoing(true)
            .setOnlyAlertOnce(true) // 避免重复声音
            .addAction(R.drawable.ic_logo, "停止", stopPendingIntent)
            .setContentIntent(PendingIntent.getActivity(
                this, 0, new Intent(this, SettingsFragment.class),
                PendingIntent.FLAG_IMMUTABLE
            ))
            .build();
    }

    private Notification createNotification(int port, String status) {
        return createNotification(port, status, null);
    }

    private void updateNotification(int port, String status, String detail) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, createNotification(port, status, detail));
    }

    private void updateNotification(int port, String status) {
        updateNotification(port, status, null);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "WebDAV 服务", NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("WebDAV 服务器运行状态");
            channel.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface networkInterface = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddress = networkInterface.getInetAddresses(); enumIpAddress.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddress.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取IP失败", e);
        }
        return "localhost";
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
        if (serverExecutor != null) {
            serverExecutor.shutdown();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
