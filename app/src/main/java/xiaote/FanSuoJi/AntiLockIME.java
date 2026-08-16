package xiaote.FanSuoJi;

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.KeyboardView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;
import android.content.Intent;

public class AntiLockIME extends InputMethodService {

    @Override
    public View onCreateInputView() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.argb(240, 20, 20, 30));

        TextView title = new TextView(this);
        title.setText("反锁机 - 应急键盘");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 20, 0, 10);
        layout.addView(title);

        TextView hint = new TextView(this);
        hint.setText("点击下方按钮打开卸载列表");
        hint.setTextColor(Color.argb(180, 200, 200, 200));
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, 30);
        layout.addView(hint);

        Button btnOpen = new Button(this);
        btnOpen.setText("打开卸载列表");
        btnOpen.setTextSize(18);
        btnOpen.setTextColor(Color.WHITE);
        btnOpen.setBackgroundColor(0xFFFF6A00);
        btnOpen.setPadding(40, 20, 40, 20);
        btnOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent("xiaote.FanSuoJi.SHOW_UNINSTALL");
                sendBroadcast(intent);
            }
        });
        layout.addView(btnOpen);

        TextView footer = new TextView(this);
        footer.setText("当手机被锁时，切换到此键盘卸载恶意应用");
        footer.setTextColor(Color.argb(120, 200, 200, 200));
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, 30, 0, 20);
        layout.addView(footer);

        return layout;
    }
}
