package ts.realms.m2git.core.network.mws;


import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import timber.log.Timber;
import ts.realms.m2git.R;
import ts.realms.m2git.ui.screens.settings.UserSettingsActivity;
import ts.realms.m2git.utils.BasicFunctions;

public class WebDavService extends Service {
    public static final String ACTION_SERVICE_STOPPED = "ts.realms.m2git.WEBDAV_SERVICE_STOPPED";
    private static final String CHANNEL_ID = "webdav_service";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "WebDavService";
    private SimpleMiltonServer server;
    private ExecutorService serverExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        server = SimpleMiltonServer.getInstance();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        Timber.tag(TAG).i("Service action: %s", action);
        createNotificationChannel();
        if ("START".equals(action)) {
            int port = intent.getIntExtra("PORT", 8080);
            String home = intent.getStringExtra("HOME");
            startForeground(NOTIFICATION_ID, createNotification("正在启动..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            // 在后台线程启动服务器
            serverExecutor = Executors.newSingleThreadExecutor();
            serverExecutor.submit(() -> {
                try {
                    server.build(home, port);
                    server.start();
                    String ip = getLocalIpAddress();
                    updateNotification("运行中", "http://" + ip + ":" + port);
                } catch (Exception e) {
                    Timber.tag(TAG).e(e, "WebDav 服务器启动失败");
                    String m = "服务器启动失败: " + e.getMessage();
                    BasicFunctions.getActiveActivity().showToastMessage(m);
                    // 本地广播通知更新switchPref关闭状态
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_SERVICE_STOPPED));
                    stopSelf();
                }
            });

        } else if ("STOP".equals(action)) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_SERVICE_STOPPED));
            stopSelf();
        }
        return START_STICKY;
    }

    private Notification createNotification(String status, String detail) {
        Intent switchIntent = new Intent(this, UserSettingsActivity.class);
        switchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // 提高通知优先级，立即显示通知
        Intent stopIntent = new Intent(this, WebDavService.class);
        stopIntent.setAction("STOP");
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("m2git WebDAV 服务器 - " + status)
            .setContentText(detail != null ? detail : status)
            .setSmallIcon(R.drawable.ic_logo) // 替换为你的图标
            .setOngoing(true)
            .setOnlyAlertOnce(true) // 避免重复声音
            .addAction(R.drawable.ic_logo, "停止", PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
            ))
            .setContentIntent(PendingIntent.getActivity(this, 0, switchIntent, PendingIntent.FLAG_IMMUTABLE))
            //  保活：强化前台服务
            .setPriority(NotificationCompat.PRIORITY_MAX) // 最高优先级
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 锁屏可见
            .build();
    }

    private Notification createNotification(String status) {
        return createNotification(status, null);
    }

    private void updateNotification(String status, String detail) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, createNotification(status, detail));
    }

    @SuppressLint("ObsoleteSdkInt")
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "WebDAV 服务", NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("WebDAV 服务器运行状态");
            channel.setShowBadge(false);
            channel.setBypassDnd(false);  // 关闭绕过免打扰模式
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
            Timber.tag(TAG).e(e, "获取IP失败");
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
