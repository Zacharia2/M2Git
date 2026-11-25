package ts.realms.m2git.ui.screens.settings;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import java.io.File;

import ts.realms.m2git.R;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.core.network.mws.SimpleMiltonServer;
import ts.realms.m2git.core.network.mws.WebDavService;
import ts.realms.m2git.local.preference.PreferenceHelper;
import ts.realms.m2git.ui.components.dialogs.DummyDialogListener;
import ts.realms.m2git.ui.components.fragments.ExploreRootDirActivity;
import ts.realms.m2git.ui.components.fragments.PrivateKeyManageActivity;
import ts.realms.m2git.ui.screens.main.RepoListActivity;
import ts.realms.m2git.utils.BasicFunctions;

public class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceClickListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int BATTERY_HINT_TITLE_RES = R.string.dialog_battery_hint_title;
    private static final int BATTERY_HINT_MSG_RES = R.string.dialog_battery_hint_msg;
    private PreferenceHelper preferenceHelper;
    private BroadcastReceiver serviceStatusReceiver; // 加字段
    /**
     * 递归守卫：setChecked(false) / setDefaultWebdavStatus() 会同步写
     * SharedPreferences 并再次触发 onSharedPreferenceChanged，
     * 造成重入（走 else 分支 stopService 未启动的服务、重复弹 Toast）。
     */
    private boolean syncingWebdavSwitch = false;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {

        preferenceHelper = PreferenceHelper.getInstance(this.getContext());
        // need to set as for historical reasons SGit uses custom prefs file
        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(getString(R.string.preference_file_key));
        prefMgr.setSharedPreferencesMode(Context.MODE_PRIVATE);

        // /storage/emulated/0/Documents
        if (preferenceHelper == null || preferenceHelper.getRepoRoot() == null || preferenceHelper.getRepoRoot().toString().isEmpty()) {
            File documentsDir = new File(Environment.getExternalStorageDirectory(), "Documents");
            Repo.setLocalRepoRoot(this.getContext(), documentsDir);
        }

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        PreferenceScreen screen = getPreferenceScreen();
        int count = screen.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            Preference pref = screen.getPreference(i);
            processPreference(pref);
        }

        registerOnPreferenceClickListener();
        registerBroadcastReceiver();
    }

    private void processPreference(Preference pref) {
        if (pref instanceof PreferenceGroup group) {
            // 如果是分组容器（PreferenceCategory或嵌套的PreferenceScreen）
            int groupCount = group.getPreferenceCount();
            for (int j = 0; j < groupCount; j++) {
                // 递归处理分组内的每一项
                processPreference(group.getPreference(j));
            }
        } else {
            // 这才是真正的设置项（EditTextPreference等）
            setupSummaryProviderForSinglePreference(pref);
        }
    }

    private void setupSummaryProviderForSinglePreference(Preference pref) {
        // EditTextPreference
        if (pref.getClass() == EditTextPreference.class && pref.getSummary() != null) {
            CharSequence summary = pref.getSummary();
            // 已经设置持久化，所以框架会自己存储到文件中。
            pref.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> {
                if (summary != null) {
                    // 特殊情况，为webdav设置默认值
                    if (preference.getKey().equals(getString(R.string.pref_key_webdav_home_dir)) && preference.getText() == null) {
                        String defaultHome = Environment.getExternalStorageDirectory().toString();
                        preferenceHelper.setWebdavHomeDir(defaultHome);
                        return defaultHome;
                    } else if (preference.getKey().equals(getString(R.string.pref_key_webdav_port)) && preference.getText() == null) {
                        String defaultPort = "8080";
                        preferenceHelper.setWebdavPort(defaultPort);
                        return defaultPort;
                    } else if (preference.getKey().equals(getString(R.string.pref_key_webdav_user)) && preference.getText() == null) {
                        String defaultUser = "user";
                        preferenceHelper.setWebdavUser(defaultUser);
                        return defaultUser;
                    } else if (preference.getKey().equals(getString(R.string.pref_key_webdav_password)) && preference.getText() == null) {
                        String defaultPassword = "123456";
                        preferenceHelper.setWebdavPassword(defaultPassword);
                        return defaultPassword;
                    }
                    // 正常情况下
                    return String.format(summary.toString(), preference.getText());
                } else {
                    return null;
                }
            });
            pref.setOnPreferenceChangeListener((preference, newValue) -> true);
        }
        // Preference
        if (pref.getClass() == Preference.class && pref.getSummary() != null) {
            CharSequence summary = pref.getSummary();
            pref.setSummaryProvider((Preference.SummaryProvider<Preference>) preference -> {
                if (summary != null) {
                    String preference_file_key = getContext().getString(R.string.preference_file_key);
                    SharedPreferences sharedPreference = getContext().getSharedPreferences(preference_file_key, Context.MODE_PRIVATE);
                    String key = preference.getKey();
                    String value = sharedPreference.getString(key, "");
                    return String.format(summary.toString(), value);
                } else {
                    return null;
                }
            });
            pref.setOnPreferenceChangeListener((preference, newValue) -> true);
        }
    }

    private void showBatteryHintDialog() {
        BasicFunctions.getActiveActivity().showMessageDialog(
            BATTERY_HINT_TITLE_RES,
            BasicFunctions.getActiveActivity().getString(BATTERY_HINT_MSG_RES),
            R.string.label_ok,
            new DummyDialogListener());
    }

    @Override
    public void onResume() {
        super.onResume();
        // 获取当前 PreferenceScreen 关联的 SharedPreferences 并注册监听
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        // 用户从白名单确认框返回后,弹厂商耗电管理提示(只弹一次)
        Context ctx = getContext();
        if (ctx != null) {
            SharedPreferences sp = ctx.getSharedPreferences("webdav_prefs", Context.MODE_PRIVATE);
            if (sp.getBoolean("pending_battery_hint", false)) {
                sp.edit().putBoolean("pending_battery_hint", false).apply();
                showBatteryHintDialog();
            }
        }
        if (serviceStatusReceiver == null) registerBroadcastReceiver(); // 挪到这里注册
        reconcileSwitchWithServerState();
    }

    /**
     * 开关 UI 与服务实际状态对账。
     * LocalBroadcast 不粘滞：服务在设置页不在前台时停止（或进程被直接杀掉，
     * onDestroy 都不会执行），停止广播会丢失，开关会错误地停留 ON。
     * 只做"显示 ON 但实际已死 → 同步为 OFF"；不反向同步——
     * 服务器活着而开关 OFF 时绝不自动置 ON，那会触发 listener 意外启动服务。
     */
    private void reconcileSwitchWithServerState() {
        if (syncingWebdavSwitch) return;
        SwitchPreferenceCompat switchPref =
            findPreference(getString(R.string.pref_key_webdav_server));
        if (switchPref == null || !switchPref.isChecked()) return;
        if (!SimpleMiltonServer.getInstance().isRunning()) {
            syncWebdavSwitchToOff();
        }
    }


    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        Context context = getContext();
        if (serviceStatusReceiver != null && context != null) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(serviceStatusReceiver);
            serviceStatusReceiver = null;
        }
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        Context context = preference.getContext();
        String key = preference.getKey();
        if (key != null) {
            // 需要注册监听器
            if (key.equals(getString(R.string.pref_key_repo_root_location))) {
                Intent exploreRootDirIntent = new Intent(context, ExploreRootDirActivity.class);
                context.startActivity(exploreRootDirIntent);
                return true;
            } else if (key.equals(getString(R.string.pref_key_manage_ssh_keys))) {
                Intent privateKeyManageActivityIntent = new Intent(context, PrivateKeyManageActivity.class);
                context.startActivity(privateKeyManageActivityIntent);
                return true;
            } else if (key.equals(getString(R.string.pref_key_credential_manager))) {
                Intent credentialActivityIntent = new Intent(context, CredentialActivity.class);
                context.startActivity(credentialActivityIntent);
                return true;
            } else if (key.equals(getString(R.string.pref_key_send_feedback))) {
                String feedbackUrl = context.getString(R.string.feedback_url);
                Intent feedbackIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(feedbackUrl));
                context.startActivity(feedbackIntent);
                return true;
            }
        }
        return false;
    }

    /**
     * 首次开启 WebDAV 服务时,引导加入电池优化白名单(只引导一次)。
     * 此时用户正在设置页里点开关,Fragment 的 context 是 Activity——
     * 确认框作为正常窗口盖在界面上,必然在最上层,不会被通知栏遮挡;
     * 这也是 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 确认框
     * 唯一可靠的启动路径(从 Service 启动需要 NEW_TASK,无法控制 z-order)。
     * 厂商 ROM 的应用速冻/省电模式管不到,由"后台运行保活"项的
     * summary 文字引导用户手动处理。
     */
    private void maybeAskBatteryOptimization(Context context) {
        SharedPreferences sp = context.getSharedPreferences("webdav_prefs", Context.MODE_PRIVATE);
        if (sp.getBoolean("battery_opt_asked", false)) return;

        sp.edit().putBoolean("battery_opt_asked", true)
            .putBoolean("pending_battery_hint", true).apply();

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm.isIgnoringBatteryOptimizations(context.getPackageName())) {
            // 修复：白名单分支就地弹提示后必须清掉 pending 标志，
            // 否则下次 onResume 检测到 pending_battery_hint 会再弹一次。
            sp.edit().putBoolean("pending_battery_hint", false).apply();
            showBatteryHintDialog();
            return;
        }
        context.startActivity(new Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:" + context.getPackageName())));
        // 非白名单分支保持原样：pending 标志留给 onResume 弹（避免与系统确认框抢屏幕）
    }

    /**
     * 首次开启 WebDAV 时显示 HTTPS 证书指引（只显示一次），
     * 用户点「确定」后再进入电池优化引导流程——两个首次引导按顺序串行，
     * 避免两个对话框叠在一起。
     */
    private void maybeShowCertHintThenBattery(Context context) {
        SharedPreferences sp = context.getSharedPreferences("webdav_prefs", Context.MODE_PRIVATE);
        if (sp.getBoolean("cert_hint_shown", false)) {
            // 非首次：直接走原有的电池流程
            maybeAskBatteryOptimization(context);
            return;
        }
        sp.edit().putBoolean("cert_hint_shown", true).apply();
        BasicFunctions.getActiveActivity().showMessageDialog(
            R.string.dialog_cert_hint_title,
            BasicFunctions.getActiveActivity().getString(R.string.dialog_cert_hint_msg),
            R.string.label_ok,
            (dialog, which) -> maybeAskBatteryOptimization(context));
    }

    private void registerOnPreferenceClickListener() {
        final Preference sshPreference = findPreference(getString(R.string.pref_key_manage_ssh_keys));
        final Preference feedbackPreference = findPreference(getString(R.string.pref_key_send_feedback));
        final Preference repoPreference = findPreference(getString(R.string.pref_key_repo_root_location));
        final Preference credentialPreference = findPreference(getString(R.string.pref_key_credential_manager));

        if (sshPreference != null) {
            sshPreference.setOnPreferenceClickListener(this);
        }
        if (feedbackPreference != null) {
            feedbackPreference.setOnPreferenceClickListener(this);
        }
        if (repoPreference != null) {
            repoPreference.setOnPreferenceClickListener(this);
        }
        if (credentialPreference != null) {
            credentialPreference.setOnPreferenceClickListener(this);
        }
    }

    /**
     * 静默把 WebDAV 开关同步为 OFF（UI + 持久层），不触发服务启停逻辑。
     * 供"通知权限被拒"和"收到服务已停止广播"两处复用。
     */
    private void syncWebdavSwitchToOff() {
        if (syncingWebdavSwitch) return;
        syncingWebdavSwitch = true;
        try {
            SwitchPreferenceCompat switchPref =
                findPreference(getString(R.string.pref_key_webdav_server));
            if (switchPref != null && switchPref.isChecked()) {
                switchPref.setChecked(false); // 内部重入会被守卫拦住
            }
            preferenceHelper.setDefaultWebdavStatus();
        } finally {
            syncingWebdavSwitch = false;
        }
    }

    // ===================== onSharedPreferenceChanged 修改 =====================
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String s) {
        final String themePrefKey = getString(R.string.pref_key_use_theme_id);
        final String gravatarPrefKey = getString(R.string.pref_key_use_gravatar);
        final String useEnglishPrefKey = getString(R.string.pref_key_use_english);
        final String webdavServerPrefKey = getString(R.string.pref_key_webdav_server);

        if (themePrefKey.equals(s) || useEnglishPrefKey.equals(s)) {
            TaskStackBuilder.create(getActivity())
                .addNextIntent(new Intent(getActivity(), RepoListActivity.class))
                .addNextIntent(getActivity().getIntent())
                .startActivities();
        } else if (gravatarPrefKey.equals(s)) {
            BasicFunctions.getImageLoader().clearMemoryCache();
            BasicFunctions.getImageLoader().clearDiskCache();
        } else if (webdavServerPrefKey.equals(s)) {
            // 修复：来自 syncWebdavSwitchToOff() 的回写直接忽略，阻断递归
            if (syncingWebdavSwitch) return;

            if (preferenceHelper.getWebdavStatus()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        // 权限被拒绝，静默关闭开关并提示
                        syncWebdavSwitchToOff();   // ← 替换原来的 setChecked(false) + setDefaultWebdavStatus()
                        BasicFunctions.getActiveActivity().showToastMessage(
                            "WebDAV服务需要通知权限来显示前台服务。请在系统设置中授予通知权限。"
                        );
                        return;
                    }
                }
                maybeShowCertHintThenBattery(getContext());
                Intent intent = new Intent(getContext(), WebDavService.class);
                intent.setAction("START");
                intent.putExtra("HOME", preferenceHelper.getWebdavHomeDir());
                intent.putExtra("PORT", preferenceHelper.getWebdavPort());
                intent.putExtra("USER", preferenceHelper.getWebdavUser());
                intent.putExtra("PASSWORD", preferenceHelper.getWebdavPassword());
                ContextCompat.startForegroundService(getContext(), intent);
            } else {
                Intent intent = new Intent(getContext(), WebDavService.class);
                getContext().stopService(intent);
            }
        }
    }

    private void registerBroadcastReceiver() {
        serviceStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WebDavService.ACTION_SERVICE_STOPPED.equals(intent.getAction())) {
                    syncWebdavSwitchToOff();   // ← 替换手写的 setChecked + setDefaultWebdavStatus
                }
            }
        };
        Context context = getContext();
        if (context != null) {
            IntentFilter filter = new IntentFilter(WebDavService.ACTION_SERVICE_STOPPED);
            LocalBroadcastManager.getInstance(context).registerReceiver(serviceStatusReceiver, filter);
        }
    }
}
