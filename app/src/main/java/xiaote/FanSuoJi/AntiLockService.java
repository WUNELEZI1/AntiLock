package xiaote.FanSuoJi;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.KeyEvent;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rikka.shizuku.Shizuku;

public class AntiLockService extends AccessibilityService {

    private WindowManager wm;
    private View statusBarView;
    private View listOverlayView;
    private View confirmView;
    private WindowManager.LayoutParams statusBarParams;
    private WindowManager.LayoutParams listParams;
    private WindowManager.LayoutParams confirmParams;
    private PackageManager pm;
    private Handler handler;
    private SharedPreferences dotPrefs;

    private Runnable forceTopTask;
    private boolean forceTopRunning = false;
    private boolean screenOn = true;
    private BroadcastReceiver screenReceiver;

    private static final String KNOWN_VIRUS_PKG = "com.ShiYuan.base";
    private static final String ACTION_SHOW_UNINSTALL = "xiaote.FanSuoJi.SHOW_UNINSTALL";

    // 物理触发：音量键计数器
    private int volumePressCount = 0;
    private long lastVolumePressTime = 0;
    private static final long VOLUME_TIME_WINDOW = 3000; // 3秒内
    private static final int VOLUME_PRESS_THRESHOLD = 10; // 10次

    // ============================================================
    // 服务启动
    // ============================================================
    @Override
    public void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                   | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                   | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.notificationTimeout = 100;
        setServiceInfo(info);

        pm = getPackageManager();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler();

        // SharedPreferences 监听（设置实时生效）
        dotPrefs = getSharedPreferences("dot_config", MODE_PRIVATE);
        dotPrefs.registerOnSharedPreferenceChangeListener(dotPrefsListener);

        createStatusBarDot();
        showNotification();
        registerScreenReceiver();
        registerIMEReceiver();
        startForceTopTask();
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener dotPrefsListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    if (key == null) return;
                    if (key.startsWith("dot_")) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                createStatusBarDot();
                            }
                        });
                    } else if (key.equals("force_top")) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (dotPrefs.getBoolean("force_top", false)) {
                                    startForceTopTask();
                                } else {
                                    stopForceTopTask();
                                }
                            }
                        });
                    }
                }
            };

    // ============================================================
    // 关闭指定应用的无障碍服务
    // ============================================================
    private void disableAppAccessibility(String packageName) {
        try {
            String currentList = Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (currentList == null || currentList.isEmpty()) {
                Toast.makeText(this, R.string.no_accessibility, Toast.LENGTH_SHORT).show();
                return;
            }
            String[] parts = currentList.split(":");
            StringBuilder newList = new StringBuilder();
            boolean found = false;
            for (String p : parts) {
                if (p.startsWith(packageName + "/")) {
                    found = true;
                } else {
                    if (newList.length() > 0) newList.append(":");
                    newList.append(p);
                }
            }
            if (!found) {
                Toast.makeText(this, R.string.no_accessibility, Toast.LENGTH_SHORT).show();
                return;
            }
            String result = newList.toString();
            boolean done = runCommand(
                    new String[]{"settings", "put", "secure",
                            "enabled_accessibility_services", result},
                    "settings put secure enabled_accessibility_services '" + result + "'");
            if (done) {
                Toast.makeText(this, getString(R.string.disabled_accessibility) + ": " + packageName, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.shizuku_no_perm, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.op_failed, Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================
    // 屏幕状态监听（息屏停止强制置顶，避免耗电）
    // ============================================================
    private void registerScreenReceiver() {
        try {
            if (screenReceiver != null) return;
            screenReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                        screenOn = false;
                        stopForceTopTask();
                    } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                        screenOn = true;
                        if (dotPrefs.getBoolean("force_top", false)) {
                            startForceTopTask();
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(screenReceiver, filter);
        } catch (Exception ignored) {}
    }

    // ============================================================
    // IME 广播接收器（键盘方案触发）
    // ============================================================
    private BroadcastReceiver imeReceiver;

    private void registerIMEReceiver() {
        try {
            if (imeReceiver != null) return;
            imeReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (ACTION_SHOW_UNINSTALL.equals(intent.getAction())) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                showUninstallList();
                            }
                        });
                    }
                }
            };
            registerReceiver(imeReceiver, new IntentFilter(ACTION_SHOW_UNINSTALL));
        } catch (Exception ignored) {}
    }

    private void unregisterIMEReceiver() {
        try {
            if (imeReceiver != null) {
                unregisterReceiver(imeReceiver);
                imeReceiver = null;
            }
        } catch (Exception ignored) {}
    }

    // ============================================================
    // 物理按键触发（快速按10下音量-打开卸载列表）
    // ============================================================
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            long now = System.currentTimeMillis();
            if (now - lastVolumePressTime > VOLUME_TIME_WINDOW) {
                volumePressCount = 1;
            } else {
                volumePressCount++;
            }
            lastVolumePressTime = now;
            if (volumePressCount >= VOLUME_PRESS_THRESHOLD) {
                volumePressCount = 0;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showUninstallList();
                    }
                });
                return true;
            }
        }
        return super.onKeyEvent(event);
    }

    private void unregisterScreenReceiver() {
        try {
            if (screenReceiver != null) {
                unregisterReceiver(screenReceiver);
                screenReceiver = null;
            }
        } catch (Exception ignored) {}
    }

    // ============================================================
    // 强制置顶（间隔轮询，不在列表时重启服务）
    // ============================================================
    private void startForceTopTask() {
        stopForceTopTask();
        if (!dotPrefs.getBoolean("force_top", true)) return;
        forceTopRunning = true;
        final int interval = dotPrefs.getInt("force_interval", 5);
        forceTopTask = new Runnable() {
            @Override
            public void run() {
                if (forceTopRunning && handler != null) {
                    // 不在列表界面和卸载弹窗界面时执行强制置顶
                    if (listOverlayView == null && confirmView == null) {
                        forceTopNow();
                    }
                    handler.postDelayed(this, interval * 1000);
                }
            }
        };
        handler.postDelayed(forceTopTask, interval * 1000);
    }

    private void stopForceTopTask() {
        forceTopRunning = false;
        if (forceTopTask != null && handler != null) {
            handler.removeCallbacks(forceTopTask);
            forceTopTask = null;
        }
    }

    private void forceTopNow() {
        if (Shizuku.pingBinder()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            try {
                String component = "xiaote.FanSuoJi/xiaote.FanSuoJi.AntiLockService";

                // 获取当前无障碍服务列表
                String currentList = Settings.Secure.getString(
                        getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (currentList == null) currentList = "";

                // 如果反锁机2不在列表中，直接添加
                if (!currentList.contains(component)) {
                    if (currentList.isEmpty()) {
                        currentList = component;
                    } else {
                        currentList = currentList + ":" + component;
                    }
                    runCommand(
                            new String[]{"settings", "put", "secure",
                                    "enabled_accessibility_services", currentList},
                            "settings put secure enabled_accessibility_services '" + currentList + "'");
                    return;
                }

                // 已在列表中：用 sh -c 先移除，sleep 1s 再添加，确保系统有时间处理
                String without = "";
                String[] parts = currentList.split(":");
                for (String p : parts) {
                    if (!p.equals(component)) {
                        if (without.isEmpty()) without = p;
                        else without = without + ":" + p;
                    }
                }
                String withBack = without.isEmpty() ? component : without + ":" + component;
                String cmd = "settings put secure enabled_accessibility_services '" + without
                        + "' && sleep 1 && settings put secure enabled_accessibility_services '" + withBack + "'";
                runCommand(new String[]{"sh", "-c", cmd}, cmd);
            } catch (Exception ignored) {}
        }
        // Root 兜底：无 Shizuku 授权但已 Root 时也尝试强制置顶
        if (isRootAvailable()) {
            try {
                String component = "xiaote.FanSuoJi/xiaote.FanSuoJi.AntiLockService";
                String currentList = Settings.Secure.getString(
                        getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (currentList == null) currentList = "";
                if (!currentList.contains(component)) {
                    if (currentList.isEmpty()) currentList = component;
                    else currentList = currentList + ":" + component;
                    execAsRoot("settings put secure enabled_accessibility_services '" + currentList + "'");
                    return;
                }
                String without = "";
                String[] parts = currentList.split(":");
                for (String p : parts) {
                    if (!p.equals(component)) {
                        if (without.isEmpty()) without = p;
                        else without = without + ":" + p;
                    }
                }
                String withBack = without.isEmpty() ? component : without + ":" + component;
                String cmd = "settings put secure enabled_accessibility_services '" + without
                        + "' && sleep 1 && settings put secure enabled_accessibility_services '" + withBack + "'";
                execAsRoot(cmd);
            } catch (Exception ignored) {}
        }
    }

    // ============================================================
    // 状态栏悬浮按钮（可配置宽高颜色位置）
    // ============================================================
    private void createStatusBarDot() {
        if (statusBarView != null) {
            try { wm.removeView(statusBarView); } catch (Exception e) {}
            statusBarView = null;
        }

        // 读取配置
        int cfgWidth = dotPrefs.getInt("dot_width", 40);
        int cfgHeight = dotPrefs.getInt("dot_height", 0);
        int cfgColor = dotPrefs.getInt("dot_color", Color.argb(140, 255, 200, 100));
        int cfgPosition = dotPrefs.getInt("dot_position", 1);
        int cfgAlpha = dotPrefs.getInt("dot_alpha", 140);
        // 用透明度设置替换颜色中的 alpha 通道（只影响悬浮块，不影响列表）
        cfgColor = (cfgColor & 0x00FFFFFF) | (Math.min(255, Math.max(0, cfgAlpha)) << 24);

        // 实际高度
        int statusBarHeight = getStatusBarHeight();
        int realHeight = cfgHeight > 0 ? dpToPx(cfgHeight) : statusBarHeight + 4;

        // 位置
        int gravity = Gravity.TOP;
        switch (cfgPosition) {
            case 0: gravity |= Gravity.START; break;
            case 2: gravity |= Gravity.END; break;
            default: gravity |= Gravity.CENTER_HORIZONTAL; break;
        }

        View dot = new View(this);
        dot.setBackgroundColor(cfgColor);

        dot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reTopStatusBar();
                showUninstallList();
            }
        });

        int windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                  | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                  | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                  | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                  | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                  | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

        statusBarParams = new WindowManager.LayoutParams(
                dpToPx(cfgWidth),
                realHeight,
                windowType,
                flags,
                PixelFormat.TRANSLUCENT);
        statusBarParams.gravity = gravity;
        statusBarParams.x = 0;
        statusBarParams.y = 0;

        try {
            wm.addView(dot, statusBarParams);
            statusBarView = dot;
        } catch (Exception e) {
            Toast.makeText(this, R.string.dot_create_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void reTopStatusBar() {
        if (statusBarView != null && wm != null) {
            try {
                wm.removeView(statusBarView);
                wm.addView(statusBarView, statusBarParams);
            } catch (Exception e) {
                statusBarView = null;
                createStatusBarDot();
            }
        }
    }

    private int getStatusBarHeight() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) return getResources().getDimensionPixelSize(resId);
        return (int) (24 * getResources().getDisplayMetrics().density);
    }

    // ============================================================
    // 卸载列表（含搜索 + 无障碍管理 + 加载动画）
    // ============================================================
    private void showUninstallList() {
        reTopStatusBar();

        if (listOverlayView != null) {
            try { wm.removeView(listOverlayView); } catch (Exception e) {}
            listOverlayView = null;
        }

        // 先显示"加载中"（避免空白）
        int wType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        int baseFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

        final WindowManager.LayoutParams loadParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                wType, baseFlags, PixelFormat.TRANSLUCENT);
        loadParams.gravity = Gravity.CENTER;

        final LinearLayout loadingRoot = new LinearLayout(this);
        loadingRoot.setOrientation(LinearLayout.VERTICAL);
        loadingRoot.setGravity(Gravity.CENTER);
        loadingRoot.setBackgroundColor(isDarkMode() ? Color.argb(255, 20, 20, 30) : Color.argb(255, 245, 245, 250));
        // 居中加载转圈
        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= 21) {
            progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(getThemeColor()));
        }
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)));
        loadingRoot.addView(progressBar);
        TextView loadingText = new TextView(this);
        loadingText.setText(R.string.loading);
        loadingText.setTextColor(getSecondaryTextColor());
        loadingText.setTextSize(14);
        loadingText.setPadding(0, 16, 0, 0);
        loadingRoot.addView(loadingText);

        try {
            wm.addView(loadingRoot, loadParams);
            listOverlayView = loadingRoot;
            listParams = loadParams;
        } catch (Exception e) {
            return;
        }

        // handler.post 让"加载中"先渲染，再执行耗时操作
        handler.post(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> appList = getThirdPartyApps();
                final Set<String> accPkgs = getEnabledAccessibilityPackages();

                LinearLayout listRoot = buildListUI(appList, accPkgs);

                try {
                    wm.removeView(loadingRoot);
                    wm.addView(listRoot, listParams);
                    listOverlayView = listRoot;
                    // 淡入动画
                    listRoot.setAlpha(0f);
                    listRoot.animate().alpha(1f).setDuration(350).start();
                } catch (Exception e) {
                    try { wm.addView(listRoot, listParams); } catch (Exception e2) {}
                    listOverlayView = listRoot;
                }
            }
        });
    }

    // 构建列表UI（抽取为独立方法）
    private LinearLayout buildListUI(final List<AppInfo> appList, final Set<String> accPkgs) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getBgColor());

        // 标题栏
        int themeColor = getThemeColor();
        int titleBarColor = (themeColor & 0x00FFFFFF) | (0xFF << 24);
        // 浅色模式下增强标题栏对比度
        if (!isDarkMode()) {
            int r = Color.red(titleBarColor), g = Color.green(titleBarColor), b = Color.blue(titleBarColor);
            double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
            if (lum > 0.6) {
                // 颜色太浅，加深
                titleBarColor = Color.argb(255, Math.max(0, r - 60), Math.max(0, g - 60), Math.max(0, b - 60));
            }
        }

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackgroundColor(titleBarColor);
        titleBar.setPadding(16, 14, 16, 14);

        TextView titleText = new TextView(this);
        titleText.setText(getString(R.string.app_name) + " - " + getString(R.string.uninstall_title));
        titleText.setTextColor(getContrastTextColor(titleBarColor));
        titleText.setTextSize(17);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        titleBar.addView(titleText);

        Button closeBtn = new Button(this);
        closeBtn.setText(R.string.hide);
        closeBtn.setTextSize(14);
        closeBtn.setTextColor(getContrastTextColor(titleBarColor));
        GradientDrawable hideBg = new GradientDrawable();
        hideBg.setCornerRadius(dpToPx(16));
        hideBg.setColor(Color.argb(60, 0, 0, 0));
        closeBtn.setBackground(hideBg);
        closeBtn.setPadding(16, 6, 16, 6);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dismissList(); }
        });
        titleBar.addView(closeBtn);
        root.addView(titleBar);

        // 搜索框
        EditText searchBox = new EditText(this);
        searchBox.setHint(R.string.search_hint);
        searchBox.setHintTextColor(getHintTextColor());
        searchBox.setTextColor(getTextColor());
        searchBox.setTextSize(14);
        searchBox.setBackgroundColor(getSearchBgColor());
        searchBox.setPadding(16, 10, 16, 10);
        searchBox.setSingleLine(true);
        root.addView(searchBox);

        // 提示行
        boolean shizukuReady = Shizuku.pingBinder()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        String uninstallMode;
        if (shizukuReady) {
            uninstallMode = getString(R.string.shizuku_silent);
        } else if (isRootAvailable()) {
            uninstallMode = "Root静默卸载";
        } else {
            uninstallMode = getString(R.string.system_uninstall);
        }
        String statusText = String.format(getString(R.string.count_format),
                appList.size(), uninstallMode);
        TextView hint = new TextView(this);
        hint.setText(statusText);
        hint.setTextColor(getSecondaryTextColor());
        hint.setTextSize(11);
        hint.setPadding(20, 8, 20, 8);
        hint.setBackgroundColor(getHintBgColor());
        root.addView(hint);

        // 列表
        ListView listView = new ListView(this);
        listView.setBackgroundColor(getHintBgColor());
        listView.setDividerHeight(1);
        listView.setChoiceMode(ListView.CHOICE_MODE_NONE);

        final AppListAdapter adapter = new AppListAdapter(this, appList);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppInfo ai = (AppInfo) parent.getItemAtPosition(position);
                if (ai == null) return;
                dismissList();
                reTopStatusBar();
                showConfirmDialog(ai.name, ai.packageName);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                AppInfo ai = (AppInfo) parent.getItemAtPosition(position);
                if (ai == null) return false;
                // 长按：关闭该应用的无障碍服务
                disableAppAccessibility(ai.packageName);
                return true;
            }
        });

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        root.addView(listView);

        // 底部栏
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setBackgroundColor(getSurfaceColor());
        bottomBar.setPadding(20, 12, 20, 12);

        Button refreshBtn = new Button(this);
        refreshBtn.setText(R.string.refresh_list);
        refreshBtn.setTextSize(14);
        refreshBtn.setTextColor(Color.WHITE);
        refreshBtn.setBackgroundColor((getThemeColor() & 0x00FFFFFF) | (0x99 << 24));
        refreshBtn.setPadding(20, 8, 20, 8);
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showUninstallList(); }
        });
        bottomBar.addView(refreshBtn);

        TextView spacer = new TextView(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        bottomBar.addView(spacer);

        Button homeBtn = new Button(this);
        homeBtn.setText(R.string.back_home);
        homeBtn.setTextSize(14);
        homeBtn.setTextColor(getTextColor());
        homeBtn.setBackgroundColor(getTransparentBgColor());
        homeBtn.setPadding(20, 8, 20, 8);
        homeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { performGlobalAction(GLOBAL_ACTION_HOME); }
        });
        bottomBar.addView(homeBtn);
        root.addView(bottomBar);

        return root;
    }

    private void dismissList() {
        if (listOverlayView != null) {
            try { wm.removeView(listOverlayView); } catch (Exception e) {}
            listOverlayView = null;
        }
    }

    // ============================================================
    // 二次确认对话框
    // ============================================================
    private void showConfirmDialog(final String appName, final String packageName) {
        if (confirmView != null) {
            try { wm.removeView(confirmView); } catch (Exception e) {}
            confirmView = null;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        GradientDrawable layoutBg = new GradientDrawable();
        layoutBg.setCornerRadius(dpToPx(16));
        int bgColor = 0xFFFF6A00;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                bgColor = getResources().getColor(android.R.color.system_accent2_100, getTheme());
            } catch (Exception ignored) {}
        }
        // 降低透明度（60%不透明）
        bgColor = (bgColor & 0x00FFFFFF) | (0x99 << 24);
        layoutBg.setColor(bgColor);
        layout.setBackground(layoutBg);

        TextView title = new TextView(this);
        title.setText(R.string.confirm_uninstall);
        title.setTextColor(getContrastTextColor(bgColor));
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(30, 25, 30, 10);
        layout.addView(title);

        TextView info = new TextView(this);
        info.setText(appName + "\n" + packageName);
        info.setTextColor(getContrastTextColor(bgColor));
        info.setTextSize(14);
        info.setGravity(Gravity.CENTER);
        info.setPadding(30, 10, 30, 20);
        layout.addView(info);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(20, 10, 20, 20);

        Button cancelBtn = new Button(this);
        cancelBtn.setText(R.string.cancel);
        cancelBtn.setTextSize(15);
        cancelBtn.setTextColor(getContrastTextColor(bgColor));
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setCornerRadius(dpToPx(16));
        cancelBg.setColor(isDarkMode() ? Color.argb(80, 255, 255, 255) : Color.argb(80, 0, 0, 0));
        cancelBtn.setBackground(cancelBg);
        cancelBtn.setPadding(30, 10, 30, 10);
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dismissConfirm(); }
        });
        btnRow.addView(cancelBtn);

        TextView spacer = new TextView(this);
        spacer.setWidth(dpToPx(20));
        btnRow.addView(spacer);

        Button okBtn = new Button(this);
        okBtn.setText(R.string.ok_uninstall);
        okBtn.setTextSize(15);
        okBtn.setTextColor(getContrastTextColor(getThemeColor()));
        GradientDrawable okBg = new GradientDrawable();
        okBg.setCornerRadius(dpToPx(16));
        okBg.setColor(getThemeColor());
        okBtn.setBackground(okBg);
        okBtn.setPadding(30, 10, 30, 10);
        okBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissConfirm();
                uninstallApp(packageName);
            }
        });
        btnRow.addView(okBtn);
        layout.addView(btnRow);

        int windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                  | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                  | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                  | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                  | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                  | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

        confirmParams = new WindowManager.LayoutParams(
                dpToPx(300),
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType, flags, PixelFormat.TRANSLUCENT);
        confirmParams.gravity = Gravity.CENTER;

        try {
            wm.addView(layout, confirmParams);
            confirmView = layout;
        } catch (Exception e) {
            Toast.makeText(this, R.string.confirm_show_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void dismissConfirm() {
        if (confirmView != null) {
            try { wm.removeView(confirmView); } catch (Exception e) {}
            confirmView = null;
        }
    }

    // ============================================================
    // 卸载
    // ============================================================
    private void uninstallApp(String packageName) {
        // 1. 优先 Shizuku 静默卸载
        if (Shizuku.pingBinder()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            try {
                Shizuku.newProcess(
                        new String[]{"pm", "uninstall", "--user", "0", packageName},
                        null, null);
                Toast.makeText(this, getString(R.string.uninstall_sent) + ": " + packageName,
                        Toast.LENGTH_SHORT).show();
                return;
            } catch (Exception e) {
                Toast.makeText(this, R.string.uninstall_fail,
                        Toast.LENGTH_SHORT).show();
            }
        }

        // 2. Root 静默卸载
        if (isRootAvailable()) {
            boolean ok = execAsRoot("pm uninstall --user 0 " + packageName);
            if (ok) {
                Toast.makeText(this, getString(R.string.uninstall_sent) + " [Root]: " + packageName,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, R.string.uninstall_fail,
                    Toast.LENGTH_SHORT).show();
        }

        // 3. 兜底：系统卸载页
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.uninstall_error) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ============================================================
    // 无障碍检测
    // ============================================================
    private Set<String> getEnabledAccessibilityPackages() {
        Set<String> pkgs = new HashSet<String>();
        try {
            String enabledStr = Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabledStr != null && !enabledStr.isEmpty()) {
                String[] services = enabledStr.split(":");
                for (String s : services) {
                    int idx = s.indexOf('/');
                    if (idx > 0) pkgs.add(s.substring(0, idx));
                }
            }
        } catch (Exception ignored) {}
        return pkgs;
    }

    // ============================================================
    // 获取第三方应用列表
    // ============================================================
    private List<AppInfo> getThirdPartyApps() {
        Set<String> accPkgs = getEnabledAccessibilityPackages();
        List<AppInfo> list = new ArrayList<AppInfo>();
        AppInfo virusApp = null;
        try {
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (ApplicationInfo app : apps) {
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                        && !app.packageName.equals(getPackageName())) {
                    AppInfo ai = new AppInfo();
                    ai.name = pm.getApplicationLabel(app).toString();
                    ai.packageName = app.packageName;
                    ai.hasAccessibility = accPkgs.contains(app.packageName);
                    if (app.packageName.equals(KNOWN_VIRUS_PKG)) virusApp = ai;
                    else list.add(ai);
                }
            }
            Collections.sort(list, new Comparator<AppInfo>() {
                @Override
                public int compare(AppInfo a, AppInfo b) {
                    if (a.hasAccessibility != b.hasAccessibility)
                        return a.hasAccessibility ? -1 : 1;
                    return a.name.compareToIgnoreCase(b.name);
                }
            });
            if (virusApp != null) list.add(0, virusApp);
        } catch (Exception e) {
            Toast.makeText(this, R.string.list_error, Toast.LENGTH_SHORT).show();
        }
        return list;
    }

    private int getThemeColor() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                return getResources().getColor(android.R.color.system_accent1_500, getTheme());
            } catch (Exception e) {
                return 0xFFFF6A00;
            }
        }
        return 0xFFFF6A00;
    }

    // 根据背景色亮度返回对比文字色（深底白字，浅底黑字）
    private int getContrastTextColor(int bgColor) {
        int r = Color.red(bgColor), g = Color.green(bgColor), b = Color.blue(bgColor);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
        return luminance > 0.5 ? Color.argb(255, 30, 30, 30) : Color.WHITE;
    }

    // ============================================================
    // Root 模式
    // ============================================================
    private boolean isRootAvailable() {
        try {
            Process p = Runtime.getRuntime().exec("su -c id");
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            br.close();
            p.destroy();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean execAsRoot(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 统一的命令执行：优先 Shizuku，其次 Root，返回是否成功
    private boolean runCommand(String[] shizukuCmd, String rootCmd) {
        if (Shizuku.pingBinder()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            try {
                Shizuku.newProcess(shizukuCmd, null, null);
                return true;
            } catch (Exception ignored) {}
        }
        return execAsRoot(rootCmd);
    }

    // ============================================================
    // 深浅色模式适配
    // ============================================================
    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private int getBgColor() {
        return isDarkMode() ? Color.argb(255, 20, 20, 30) : Color.argb(255, 245, 245, 250);
    }

    private int getSurfaceColor() {
        return isDarkMode() ? Color.argb(200, 40, 40, 55) : Color.argb(200, 230, 230, 240);
    }

    private int getTextColor() {
        return isDarkMode() ? Color.WHITE : Color.argb(255, 30, 30, 30);
    }

    private int getSecondaryTextColor() {
        return isDarkMode() ? Color.argb(180, 200, 200, 200) : Color.argb(180, 90, 90, 90);
    }

    private int getHintTextColor() {
        return isDarkMode() ? Color.argb(120, 255, 255, 255) : Color.argb(120, 80, 80, 80);
    }

    private int getHintBgColor() {
        return isDarkMode() ? Color.argb(40, 255, 255, 255) : Color.argb(40, 0, 0, 0);
    }

    private int getListItemBgColor() {
        return isDarkMode() ? Color.argb(15, 255, 255, 255) : Color.argb(15, 0, 0, 0);
    }

    private int getSearchBgColor() {
        return isDarkMode() ? Color.argb(60, 255, 255, 255) : Color.argb(60, 0, 0, 0);
    }

    private int getTransparentBgColor() {
        return isDarkMode() ? Color.argb(80, 255, 255, 255) : Color.argb(80, 0, 0, 0);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    // ============================================================
    // 前台通知
    // ============================================================
    private void showNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("antilock2", getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_MIN);
            ch.setDescription(getString(R.string.notify_ready));
            nm.createNotificationChannel(ch);
        }

        Notification noti = new Notification.Builder(this, "antilock2")
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notify_ready))
                .setSmallIcon(android.R.drawable.ic_menu_delete)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN)
                .build();

        try {
            startForeground(1001, noti);
        } catch (Exception e) {
            try { nm.notify(1001, noti); } catch (Exception ignored) {}
        }
    }

    // ============================================================
    // 生命周期
    // ============================================================
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() { cleanup(); }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    private void cleanup() {
        if (dotPrefs != null) {
            dotPrefs.unregisterOnSharedPreferenceChangeListener(dotPrefsListener);
        }
        stopForceTopTask();
        unregisterScreenReceiver();
        unregisterIMEReceiver();
        if (handler != null) handler.removeCallbacksAndMessages(null);
        dismissList();
        dismissConfirm();
        if (statusBarView != null) {
            try { wm.removeView(statusBarView); } catch (Exception e) {}
            statusBarView = null;
        }
        try { stopForeground(true); } catch (Exception e) {}
    }

    // ============================================================
    // 数据类
    // ============================================================
    static class AppInfo {
        String name;
        String packageName;
        boolean hasAccessibility;
    }

    // ============================================================
    // 自定义适配器
    // ============================================================
    private static class AppListAdapter extends ArrayAdapter<AppInfo> {
        private final AntiLockService service;
        private final List<AppInfo> originalData;
        private final List<AppInfo> filteredData;
        private final Filter filter;

        AppListAdapter(AntiLockService service, List<AppInfo> data) {
            super(service, android.R.layout.simple_list_item_1, data);
            this.service = service;
            this.originalData = new ArrayList<AppInfo>(data);
            this.filteredData = new ArrayList<AppInfo>(data);
            this.filter = new AppFilter();
        }

        @Override public int getCount() { return filteredData.size(); }
        @Override public AppInfo getItem(int position) { return filteredData.get(position); }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            TextView tv;
            if (convertView instanceof TextView) tv = (TextView) convertView;
            else tv = new TextView(service);

            AppInfo ai = filteredData.get(position);
            boolean isVirus = ai.packageName.equals(KNOWN_VIRUS_PKG);
            String prefix = isVirus ? "⚠ " : "";
            String accMark = ai.hasAccessibility ? " [无障碍]" : "";
            tv.setText(prefix + ai.name + accMark + "\n" + ai.packageName);

            if (isVirus) {
                tv.setTextColor(Color.argb(255, 255, 200, 100));
                tv.setBackgroundColor(Color.argb(80, 255, 0, 0));
            } else if (ai.hasAccessibility) {
                tv.setTextColor(Color.argb(255, 150, 255, 150));
                tv.setBackgroundColor(Color.argb(40, 0, 180, 0));
            } else {
                tv.setTextColor(service.getTextColor());
                tv.setBackgroundColor(service.getListItemBgColor());
            }
            tv.setTextSize(15);
            tv.setPadding(24, 16, 24, 16);
            tv.setLines(2);
            tv.setTypeface(null, isVirus || ai.hasAccessibility ? Typeface.BOLD : Typeface.NORMAL);
            return tv;
        }

        @Override public Filter getFilter() { return filter; }

        private class AppFilter extends Filter {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<AppInfo> filtered = new ArrayList<AppInfo>();
                if (constraint == null || constraint.length() == 0) {
                    filtered.addAll(originalData);
                } else {
                    String q = constraint.toString().toLowerCase();
                    for (AppInfo ai : originalData) {
                        if (ai.name.toLowerCase().contains(q) || ai.packageName.toLowerCase().contains(q))
                            filtered.add(ai);
                    }
                }
                results.values = filtered;
                results.count = filtered.size();
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredData.clear();
                filteredData.addAll((List<AppInfo>) results.values);
                notifyDataSetChanged();
            }
        }
    }
}
