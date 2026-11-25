package ts.realms.m2git.core.network.mws;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import timber.log.Timber;
import ts.realms.m2git.R;
import ts.realms.m2git.ui.screens.settings.UserSettingsActivity;

public class WebDavService extends Service {

    public static final String ACTION_SERVICE_STOPPED = "ts.realms.m2git.WEBDAV_SERVICE_STOPPED";
    private static final String CHANNEL_ID = "webdav_service";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "WebDavService";

    private SimpleMiltonServer server;
    private ExecutorService serverExecutor;
    private volatile boolean stopping = false;
    private PowerManager.WakeLock wakeLock;

    // ==================================================================
    // URL 生成
    // ==================================================================

    /**
     * 生成全部可访问的 URL：
     * <ul>
     *   <li>仅本机：只返回 127.0.0.1</li>
     *   <li>局域网：每个非回环 IPv4 一个 URL（WiFi + 热点共存时返回两个）</li>
     *   <li>局域网但无可用接口（如飞行模式）：回退到 localhost，
     *       让用户看到服务在跑，恢复网络后重新点击"运行"即可刷新</li>
     * </ul>
     */
    private static List<String> serverUrls(boolean exposeLan, int port) {
        if (!exposeLan) {
            return Collections.singletonList("https://127.0.0.1:" + port);
        }
        List<String> ips = LanAddress.listInet4Strings();
        if (ips.isEmpty()) {
            return Collections.singletonList("https://localhost:" + port);
        }
        List<String> urls = new ArrayList<>(ips.size());
        for (String ip : ips) {
            urls.add("https://" + ip + ":" + port);
        }
        return urls;
    }

    /**
     * 多 URL 合并为多行文本：紧凑视图取第一行，展开视图显示全部，
     * 复制按钮写入完整多行（"所见即所复制"）。
     */
    private static String serverUrlsText(boolean exposeLan, int port) {
        return String.join("\n", serverUrls(exposeLan, port));
    }

    private static String firstLine(String s) {
        int idx = s.indexOf('\n');
        return idx < 0 ? s : s.substring(0, idx);
    }

    private static String statusText(boolean exposeLan) {
        return exposeLan ? "运行中(局域网)" : "运行中(仅本机)";
    }

    // ==================================================================
    // 通知(全 static,供 CopyUrlReceiver 复用)
    // ==================================================================

    private static Notification createNotification(Context ctx, String status, String detail) {
        Intent switchIntent = new Intent(ctx, UserSettingsActivity.class);
        switchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Intent stopIntent = new Intent(ctx, WebDavService.class);
        stopIntent.setAction("STOP");

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle("m2git WebDAV 服务器 - " + status)
            // 紧凑视图显示第一行；detail 为 null 时显示状态
            .setContentText(detail != null ? firstLine(detail) : status)
            .setSmallIcon(R.drawable.ic_logo)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(PendingIntent.getActivity(ctx, 0, switchIntent,
                PendingIntent.FLAG_IMMUTABLE))
            .addAction(R.drawable.ic_logo, "停止",
                PendingIntent.getService(ctx, 0, stopIntent,
                    PendingIntent.FLAG_IMMUTABLE));

        if (detail != null) {
            // 多行时展开视图显示全部 URL；单行时无需 BigTextStyle（保持紧凑）
            if (detail.indexOf('\n') >= 0) {
                b.setStyle(new NotificationCompat.BigTextStyle().bigText(detail));
            }
            Intent copyIntent = new Intent(ctx, CopyUrlReceiver.class);
            copyIntent.setAction(CopyUrlReceiver.ACTION_COPY_URL);
            copyIntent.putExtra(CopyUrlReceiver.EXTRA_URL, detail);
            b.addAction(R.drawable.ic_logo, "复制",
                PendingIntent.getBroadcast(ctx, 1, copyIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        return b.build();
    }

    private static void updateNotification(Context ctx, String status, String detail) {
        NotificationManager manager = ctx.getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, createNotification(ctx, status, detail));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "M2Git:WebDavServer");
        wakeLock.setReferenceCounted(false);
        server = SimpleMiltonServer.getInstance();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // null intent = 系统自动重启,不自动拉起(避免前台超时崩溃)
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        Timber.tag(TAG).i("Service action: %s", action);
        createNotificationChannel();

        if ("START".equals(action)) {
            stopping = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, createNotification(this, "正在启动...", null),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, createNotification(this, "正在启动...", null));
            }
            // 前台服务生命周期持有此锁,onDestroy 显式释放;进程死亡时内核自动回收。
            // 不能用有限 timeout:过期后无回调,进程会重新变为可冻结状态,
            // 复发"后台 TLS 握手 15~31 秒"的 freezer 问题。
            // noinspection WakelockTimeout
            wakeLock.acquire();

            String home = intent.getStringExtra("HOME");
            int port = intent.getIntExtra("PORT", 8080);
            String user = intent.getStringExtra("USER");
            String password = intent.getStringExtra("PASSWORD");
            // 默认开启局域网模式(不传 extra 也生效);显式传 false 才关闭
            boolean exposeLan = intent.getBooleanExtra("EXPOSE_LAN", true);
            // 密码不留在 Intent 里(dumpsys / bugreport / ACRA 都能看到 extras)
            intent.removeExtra("PASSWORD");

            // 情况 A:已在运行
            if (server.isRunning()) {
                // exposeLan 和端口都要比较,否则改端口会被静默忽略,
                // 且通知显示新端口而服务器实际还在旧端口上——按通知地址连不上。
                boolean sameConfig = server.isExposeToLan() == exposeLan
                    && server.getPort() == port;
                if (sameConfig) {
                    updateNotification(this, statusText(exposeLan),
                        serverUrlsText(exposeLan, server.getPort()));
                    return START_NOT_STICKY;
                }
                Timber.tag(TAG).i("config changed (lan/port), restarting server");
                server.stop();
            }

            server.setExposeToLan(exposeLan);

            if (serverExecutor == null || serverExecutor.isShutdown()) {
                serverExecutor = Executors.newSingleThreadExecutor();
            }
            final boolean fExposeLan = exposeLan;
            serverExecutor.submit(() -> {
                if (stopping) return;
                if (server.isRunning()) {
                    // 排队期间配置又被改了：运行配置与本任务目标不一致 → 重启，让最后一次请求生效
                    if (server.getPort() != port || server.isExposeToLan() != fExposeLan) {
                        try {
                            server.stop();
                        } catch (Exception e) {
                            Timber.tag(TAG).w(e, "stop before rebuild failed");
                        }
                        // 落到下面的 build 逻辑，用本任务的 home/port/user/password 重建
                    } else {
                        updateNotification(this, statusText(fExposeLan),
                            serverUrlsText(fExposeLan, server.getPort()));
                        return;
                    }
                }
                try {
                    server.build(getApplicationContext(), home, port, user, password);
                    if (stopping) {
                        server.stop();
                        return;
                    }
                    server.start();
                    if (stopping) {
                        server.stop();
                        return;
                    }
                    updateNotification(this, statusText(fExposeLan),
                        serverUrlsText(fExposeLan, server.getPort()));
                } catch (IllegalStateException e) {
                    Timber.tag(TAG).w(e, "build conflict (concurrent START?)");
                    if (stopping) return;
                    if (server.isRunning()) {
                        updateNotification(this, statusText(fExposeLan),
                            serverUrlsText(fExposeLan, server.getPort()));
                    } else {
                        // 冲突后服务器没起来：不能永远停在"正在启动..."
                        stopping = true;
                        LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent(ACTION_SERVICE_STOPPED));
                        stopSelf();
                    }
                } catch (Exception e) {
                    // onDestroy 的 shutdownNow 会打断 build/start,那是用户
                    // 主动停止,不该弹"启动失败"
                    if (stopping) {
                        Timber.tag(TAG).i("start aborted by stop, ignoring: %s", e.toString());
                        return;
                    }
                    Timber.tag(TAG).e(e, "WebDav 服务器启动失败");
                    String m = "服务器启动失败: " + e.getMessage();
                    new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(getApplicationContext(), m, Toast.LENGTH_LONG).show());
                    stopping = true;
                    LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(new Intent(ACTION_SERVICE_STOPPED));
                    stopSelf();
                }
            });
        } else if ("STOP".equals(action)) {
            stopping = true;
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(ACTION_SERVICE_STOPPED));
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @SuppressLint("ObsoleteSdkInt")
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "WebDAV 服务", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("WebDAV 服务器运行状态");
            channel.setShowBadge(false);
            channel.setBypassDnd(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    // ==================================================================
    // 生命周期
    // ==================================================================

    @Override
    public void onDestroy() {
        super.onDestroy();

        // 修复：补发停止广播。原来只有"通知栏 STOP"和"启动失败"两条路径
        // 会发 ACTION_SERVICE_STOPPED，系统直接杀服务时设置页开关不同步。
        // 注意：必须放在 stopSelf 之后的清理之前、且 try 包裹——
        // onDestroy 可能在进程被回收的边缘调用，广播失败不应中断清理。
        try {
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(ACTION_SERVICE_STOPPED));
        } catch (Exception e) {
            Timber.tag(TAG).w(e, "send ACTION_SERVICE_STOPPED failed");
        }

        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopping = true;
        if (server != null) {
            server.stop();
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ==================================================================
    // 复制地址。反馈直接写在通知本身上:用户就是在通知栏里点的按钮,
    // 把这条通知的标题临时改成"已复制",2.5 秒后恢复——反馈出现在用户
    // 视线正中央,不存在被通知栏遮挡的问题;也不依赖 Toast 或系统剪贴板
    // 横幅(后者是 Android 13+ 的隐私功能,应用无法控制其显示)。
    //
    // 多 IP 场景下复制按钮写入完整多行（所有 URL），与展开视图一致。
    // ==================================================================

    public static class CopyUrlReceiver extends BroadcastReceiver {
        public static final String ACTION_COPY_URL = "ts.realms.m2git.WEBDAV_COPY_URL";
        public static final String EXTRA_URL = "url";
        private static final long FEEDBACK_MS = 2500L;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_COPY_URL.equals(intent.getAction())) return;
            String url = intent.getStringExtra(EXTRA_URL);
            if (url == null || url.isEmpty()) return;

            ClipboardManager cm = context.getSystemService(ClipboardManager.class);
            if (cm == null) return;
            cm.setPrimaryClip(ClipData.newPlainText("WebDAV URL", url));

            SimpleMiltonServer s = SimpleMiltonServer.getInstance();
            if (!s.isRunning()) return;
            Context app = context.getApplicationContext();
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            nm.notify(NOTIFICATION_ID, createNotification(app, "已复制到剪贴板 ✓", url));

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!s.isRunning()) return; // 期间服务被停了,不恢复旧状态
                boolean lan = s.isExposeToLan();
                nm.notify(NOTIFICATION_ID, createNotification(app,
                    statusText(lan), serverUrlsText(lan, s.getPort())));
            }, FEEDBACK_MS);
        }
    }
}