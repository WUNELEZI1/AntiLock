package xiaote.FanSuoJi;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final int SHIZUKU_REQUEST_CODE = 10086;
    private SharedPreferences prefs;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
	new Shizuku.OnRequestPermissionResultListener() {
		@Override
		public void onRequestPermissionResult(int requestCode, int grantResult) {
			if (requestCode == SHIZUKU_REQUEST_CODE) {
				if (grantResult == PackageManager.PERMISSION_GRANTED) {
					Toast.makeText(MainActivity.this, "Shizuku " + getString(R.string.toast_shizuku_ok), Toast.LENGTH_SHORT).show();
				}
			}
		}
	};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Shizuku.addRequestPermissionResultListener(permissionListener);
        prefs = getSharedPreferences("dot_config", MODE_PRIVATE);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getBgColor());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(40, 40, 40, 40);
        root.setBackgroundColor(getBgColor());

        // 标题
        TextView title = new TextView(this);
        title.setText(R.string.main_title);
        title.setTextSize(28);
        title.setPadding(0, 0, 0, 10);
        title.setTextColor(getTextColor());
        root.addView(title);

        // 状态说明
        String shizukuStatus;
        if (Shizuku.pingBinder()) {
            shizukuStatus = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
				? getString(R.string.toast_shizuku_ok) : "Shizuku \u672A\u6388\u6743";
        } else {
            shizukuStatus = getString(R.string.toast_shizuku_norun);
        }
        TextView info = new TextView(this);
        info.setText(getString(R.string.main_info));
        info.setTextSize(13);
        info.setPadding(0, 0, 0, 10);
        info.setTextColor(getTextColor());
        root.addView(info);

        // Shizuku 状态
        TextView statusView = new TextView(this);
        statusView.setText("Shizuku: " + shizukuStatus + "\nRoot: " + (isRootAvailable() ? "已授权" : "未授权"));
        statusView.setTextColor(getSecondaryTextColor());
        statusView.setTextSize(11);
        statusView.setPadding(0, 0, 0, 20);
        root.addView(statusView);

        // 授权Shizuku（单独一行）
        Button btnShizuku = new Button(this);
        btnShizuku.setText(R.string.btn_shizuku);
        btnShizuku.setTextSize(14);
        btnShizuku.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (Shizuku.pingBinder()) {
						if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
							Shizuku.requestPermission(SHIZUKU_REQUEST_CODE);
						else
							Toast.makeText(MainActivity.this, R.string.toast_shizuku_ok, Toast.LENGTH_SHORT).show();
					} else {
						Toast.makeText(MainActivity.this, R.string.toast_shizuku_norun, Toast.LENGTH_SHORT).show();
					}
				}
			});
        root.addView(btnShizuku);

        // 无障碍设置（单独一行）
        Button btnAccess = new Button(this);
        btnAccess.setText(R.string.btn_accessibility);
        btnAccess.setTextSize(14);
        btnAccess.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
				}
			});
        root.addView(btnAccess);

        // 一键开启无障碍（通过 shell 执行）
        Button btnEnableAccess = new Button(this);
        btnEnableAccess.setText("一键开启无障碍");
        btnEnableAccess.setTextSize(14);
        btnEnableAccess.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					enableAccessibilityByShell();
				}
			});
        root.addView(btnEnableAccess);

        // 应用详细（跳转到应用详情页）
        Button btnAppDetail = new Button(this);
        btnAppDetail.setText("应用详细");
        btnAppDetail.setTextSize(14);
        btnAppDetail.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					try {
						Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
						intent.setData(Uri.parse("package:" + getPackageName()));
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(intent);
					} catch (Exception e) {
						Toast.makeText(MainActivity.this, "无法打开应用详情", Toast.LENGTH_SHORT).show();
					}
				}
			});
        root.addView(btnAppDetail);

        // 禁止省电优化（单独一行）
        Button batteryBtn = new Button(this);
        batteryBtn.setText("禁止省电优化");
        batteryBtn.setTextSize(14);
        batteryBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					try {
						Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
						intent.setData(android.net.Uri.parse("package:" + getPackageName()));
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(intent);
					} catch (Exception e) {
						Toast.makeText(MainActivity.this, "无法打开设置", Toast.LENGTH_SHORT).show();
					}
				}
			});
        root.addView(batteryBtn);

        // 分隔
        TextView divider = new TextView(this);
        divider.setText(R.string.section_settings);
        divider.setTextColor(getSecondaryTextColor());
        divider.setTextSize(13);
        divider.setGravity(Gravity.CENTER);
        divider.setPadding(0, 30, 0, 20);
        root.addView(divider);

        // 宽度 SeekBar
        addSeekBarSetting(root, R.string.label_width, "width", 10, 120, prefs.getInt("dot_width", 40), "dp");

        // 高度 SeekBar
        addSeekBarSetting(root, R.string.label_height, "height", 0, 60, prefs.getInt("dot_height", 0), "dp");
        TextView heightTip = new TextView(this);
        heightTip.setText(R.string.tip_height);
        heightTip.setTextColor(getHintTextColor());
        heightTip.setTextSize(11);
        heightTip.setPadding(dpToPx(90), 0, 0, 10);
        root.addView(heightTip);

        // 透明度 SeekBar
        addSeekBarSetting(root, R.string.label_alpha, "alpha", 0, 255, prefs.getInt("dot_alpha", 140), "");
        // 颜色
        TextView colorLabel = new TextView(this);
        colorLabel.setText("颜色:");
        colorLabel.setTextColor(getTextColor());
        colorLabel.setTextSize(14);
        colorLabel.setPadding(0, 10, 0, 8);
        root.addView(colorLabel);

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER);

        final int[] colors = {
			Color.argb(140, 255, 200, 100),  // 橙（默认）
			Color.argb(140, 255, 255, 255),  // 白
			Color.argb(140, 160, 100, 230),  // 紫
			Color.argb(140, 100, 180, 255),  // 蓝
			Color.argb(140, 100, 230, 150),  // 绿
			Color.argb(140, 255, 100, 100),  // 红
        };
        final String[] colorNames = {"\u6A59", "\u767D", "\u7D2B", "\u84DD", "\u7EFF", "\u7EA2"};
        final int currentColor = prefs.getInt("dot_color", colors[0]);

        for (int i = 0; i < colors.length; i++) {
            final int ci = i;
            final int colorVal = colors[i];
            Button cb = new Button(this);
            cb.setText(colorNames[i]);
            cb.setTextSize(11);
            cb.setTextColor(Color.WHITE);
            cb.setBackgroundColor(colorVal);
            cb.setPadding(12, 8, 12, 8);
            cb.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						prefs.edit().putInt("dot_color", colorVal).apply();
						Toast.makeText(MainActivity.this, R.string.toast_color_set, Toast.LENGTH_SHORT).show();
					}
				});
            colorRow.addView(cb);
        }
        root.addView(colorRow);

        // 位置
        TextView posLabel = new TextView(this);
        posLabel.setText("位置:");
        posLabel.setTextColor(getTextColor());
        posLabel.setTextSize(14);
        posLabel.setPadding(0, 15, 0, 8);
        root.addView(posLabel);

        LinearLayout posRow = new LinearLayout(this);
        posRow.setOrientation(LinearLayout.HORIZONTAL);
        posRow.setGravity(Gravity.CENTER);

        final int currentPos = prefs.getInt("dot_position", 1);
        final String[] posNames = {"\u5DE6\u4FA7", "\u5C45\u4E2D", "\u53F3\u4FA7"};
        final int[] posValues = {0, 1, 2};

        for (int i = 0; i < posNames.length; i++) {
            final int pi = i;
            final int posVal = posValues[i];
            Button pb = new Button(this);
            pb.setText(posNames[i]);
            pb.setTextSize(13);
            pb.setTextColor(currentPos == posVal ? Color.WHITE : getSecondaryTextColor());
            pb.setBackgroundColor(currentPos == posVal ? Color.argb(150, 100, 60, 190) : Color.argb(60, 100, 100, 100));
            pb.setPadding(16, 8, 16, 8);
            pb.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						prefs.edit().putInt("dot_position", posVal).apply();
						recreate();
					}
				});
            posRow.addView(pb);
        }
        root.addView(posRow);

        // ========== 强制置顶开关 ==========
        TextView forceLabel = new TextView(this);
        forceLabel.setText(R.string.label_force);
        forceLabel.setTextColor(getTextColor());
        forceLabel.setTextSize(13);
        forceLabel.setPadding(0, 20, 0, 8);
        root.addView(forceLabel);

        final android.widget.Switch forceSwitch = new android.widget.Switch(this);
        forceSwitch.setChecked(prefs.getBoolean("force_top", true));
        forceSwitch.setTextOn(getString(R.string.force_on));
        forceSwitch.setTextOff(getString(R.string.force_off));
        forceSwitch.setTextSize(14);
        forceSwitch.setTextColor(getTextColor());
        forceSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
					prefs.edit().putBoolean("force_top", isChecked).apply();
					Toast.makeText(MainActivity.this, isChecked ? getString(R.string.toast_force_on) : getString(R.string.toast_force_off), Toast.LENGTH_SHORT).show();
				}
			});
        root.addView(forceSwitch);

        // 间隔时间
        addSeekBarSetting(root, R.string.label_interval, "interval", 5, 60, prefs.getInt("force_interval", 5), "秒");
        TextView intervalTip = new TextView(this);
        intervalTip.setText(R.string.tip_interval);
        intervalTip.setTextColor(getHintTextColor());
        intervalTip.setTextSize(11);
        intervalTip.setPadding(dpToPx(90), 0, 0, 10);
        root.addView(intervalTip);

        // 广告按钮
        Button moreBtn = new Button(this);
        moreBtn.setText(getString(R.string.btn_more));
        moreBtn.setTextSize(14);
        moreBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					try {
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setData(Uri.parse("https://xiaote.data.blog/tool"));
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(intent);
					} catch (Exception e) {
						Toast.makeText(MainActivity.this, "无法打开链接", Toast.LENGTH_SHORT).show();
					}
				}
			});
        root.addView(moreBtn);



        // 开源地址
        Button openSourceBtn = new Button(this);
        openSourceBtn.setText(getString(R.string.btn_opensource));
        openSourceBtn.setTextSize(14);
        openSourceBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					try {
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setData(Uri.parse(getString(R.string.open_source_url)));
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(intent);
					} catch (Exception e) {
						Toast.makeText(MainActivity.this, "无法打开链接", Toast.LENGTH_SHORT).show();
					}
				}
			});
        root.addView(openSourceBtn);

        // ==================== 加入QQ群（修正后） ====================
        Button qqGroupBtn = new Button(this);
        qqGroupBtn.setText(R.string.btn_qq_group);
        qqGroupBtn.setTextSize(14);
        qqGroupBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					try {
						Intent intent = new Intent(Intent.ACTION_VIEW);
						// 使用群资料卡协议，card_type=group 表示群，uin 填群号（纯数字）
						// 请将 173485519 替换为你的实际群号！
						intent.setData(Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&card_type=group&uin=173485519"));
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(intent);
					} catch (Exception e) {
						// 未安装QQ或协议不支持时提示
						Toast.makeText(MainActivity.this, "请安装QQ或稍后重试", Toast.LENGTH_SHORT).show();
					}
				}
			});
        root.addView(qqGroupBtn);

        // 底部提示
        TextView footer = new TextView(this);
        footer.setText(R.string.footer_settings);
        footer.setTextColor(getSecondaryTextColor());
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, 30, 0, 10);
        root.addView(footer);

        scrollView.addView(root);
        setContentView(scrollView);

        // 自动请求Shizuku权限
        if (Shizuku.pingBinder()
			&& Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE);
        }
    }

    private void addSeekBarSetting(LinearLayout root, int labelResId, final String key,
								   int min, int max, int currentValue, String unit) {
        final String label = getString(labelResId);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 5, 0, 5);

        TextView lbl = new TextView(this);
        lbl.setText(label + ":");
        lbl.setTextColor(getTextColor());
        lbl.setTextSize(14);
        lbl.setWidth(dpToPx(50));
        row.addView(lbl);

        final TextView valDisplay = new TextView(this);
        String displayText = (currentValue == 0 && key.equals("height")) ? "自动" : currentValue + unit;
        valDisplay.setText(displayText);
        valDisplay.setTextColor((getThemeColor() & 0x00FFFFFF) | (0xCC << 24));
        valDisplay.setTextSize(14);
        valDisplay.setWidth(dpToPx(50));
        valDisplay.setGravity(Gravity.CENTER);
        row.addView(valDisplay);

        final int fMin = min;
        final int fMax = max;
        final String fKey = key;
        final String fUnit = unit;
        final SharedPreferences fPrefs = prefs;

        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(currentValue - min);
        seek.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        if (Build.VERSION.SDK_INT >= 21) {
            seek.setProgressTintList(android.content.res.ColorStateList.valueOf(getThemeColor()));
            seek.setThumbTintList(android.content.res.ColorStateList.valueOf(getThemeColor()));
        }
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					int val = progress + fMin;
					String display = (val == 0 && fKey.equals("height")) ? "自动" : val + fUnit;
					valDisplay.setText(display);
					if (fromUser) {
						fPrefs.edit().putInt("dot_" + fKey, val).apply();
					}
				}
				@Override public void onStartTrackingTouch(SeekBar seekBar) {}
				@Override public void onStopTrackingTouch(SeekBar seekBar) {}
			});
        row.addView(seek);
        root.addView(row);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(permissionListener);
    }

    // ============================================================
    // 深浅色模式适配
    // ============================================================
    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private int getTextColor() {
        return isDarkMode() ? Color.WHITE : Color.argb(255, 30, 30, 30);
    }

    private int getSecondaryTextColor() {
        return isDarkMode() ? Color.argb(150, 200, 200, 200) : Color.argb(150, 90, 90, 90);
    }

    private int getHintTextColor() {
        return isDarkMode() ? Color.argb(120, 200, 200, 200) : Color.argb(120, 90, 90, 90);
    }

    private int getBgColor() {
        return isDarkMode() ? Color.argb(255, 20, 20, 30) : Color.argb(255, 245, 245, 250);
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

    // 一键开启无障碍：通过 Shizuku 或 Root 执行 shell
    private void enableAccessibilityByShell() {
        String component = "xiaote.FanSuoJi/xiaote.FanSuoJi.AntiLockService";
        String cmd1 = "settings put secure enabled_accessibility_services '" + component + "'";
        String cmd2 = "settings put secure accessibility_enabled 1";
        boolean done = false;

        // 优先 Shizuku
        if (Shizuku.pingBinder()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            try {
                Shizuku.newProcess(new String[]{"sh", "-c", cmd1 + " && " + cmd2}, null, null);
                done = true;
            } catch (Exception e) {}
        }

        // Root 兜底
        if (!done && isRootAvailable()) {
            try {
                Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd1 + " && " + cmd2});
                done = true;
            } catch (Exception e) {}
        }

        if (done) {
            Toast.makeText(MainActivity.this, "无障碍已开启", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(MainActivity.this, "需要Shizuku或Root权限", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isRootAvailable() {
        try {
            Process p = Runtime.getRuntime().exec("su -c id");
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            br.close();
            p.destroy();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
