package xiaote.FanSuoJi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        // 发送通知提醒用户反锁机2已就绪
        // 无障碍服务如果已在设置中开启，系统会自动启动它
        // 如果未开启，用户点击通知可以打开 App 进行设置
        try {
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("boot_antilock", "反锁机2",
                        NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("开机自启动提醒");
                nm.createNotificationChannel(ch);
            }

            Intent openIntent = new Intent(context, MainActivity.class);
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(context, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification noti = new Notification.Builder(context, "boot_antilock")
                    .setContentTitle("反锁机2")
                    .setContentText("开机已就绪，点击检查无障碍服务状态")
                    .setSmallIcon(android.R.drawable.ic_menu_delete)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build();

            nm.notify(1002, noti);
        } catch (Exception ignored) {}
    }
}