package ai.ibrahim.nativeapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class NotificationHelper {
    public static final String WAKE_CHANNEL = "ibrahim_wake_service";
    public static final String RESULT_CHANNEL = "ibrahim_assistant_results";
    public static final int FOREGROUND_ID = 1301;
    public static final int RESTART_ID = 1302;
    public static final int RESULT_ID = 1303;

    private NotificationHelper() { }

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel wake = new NotificationChannel(WAKE_CHANNEL, "7/24 Wake Word", NotificationManager.IMPORTANCE_LOW);
        wake.setDescription("İbrahim AI wake-word dinleme servisi");
        wake.setShowBadge(false);
        NotificationChannel results = new NotificationChannel(RESULT_CHANNEL, "İbrahim AI Yanıtları", NotificationManager.IMPORTANCE_DEFAULT);
        results.setDescription("Sesli asistan yanıtları ve yeniden başlatma uyarıları");
        manager.createNotificationChannel(wake);
        manager.createNotificationChannel(results);
    }

    public static PendingIntent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 31, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static Notification foreground(Context context, String status, boolean paused) {
        Intent toggleIntent = new Intent(context, WakeWordService.class).setAction(paused ? WakeWordService.ACTION_RESUME : WakeWordService.ACTION_PAUSE);
        PendingIntent toggle = PendingIntent.getService(context, 32, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(context, WakeWordService.class).setAction(WakeWordService.ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(context, 33, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(context, WAKE_CHANNEL)
                .setSmallIcon(R.drawable.ic_ibrahim)
                .setContentTitle("İbrahim AI · 7/24 dinleme")
                .setContentText(status)
                .setContentIntent(openAppIntent(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(R.drawable.ic_ibrahim, paused ? "Devam" : "Duraklat", toggle).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_ibrahim, "Kapat", stop).build())
                .build();
    }

    public static void postRestartRequired(Context context) {
        createChannels(context);
        Intent intent = new Intent(context, MainActivity.class).putExtra("restart_wake_word", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 34, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, RESULT_CHANNEL)
                .setSmallIcon(R.drawable.ic_ibrahim)
                .setContentTitle("İbrahim AI dinlemeyi bekliyor")
                .setContentText("Telefon yeniden başladı. 7/24 dinlemeyi yeniden açmak için dokun.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();
        context.getSystemService(NotificationManager.class).notify(RESTART_ID, notification);
    }

    public static void postResult(Context context, String title, String text) {
        createChannels(context);
        Notification notification = new Notification.Builder(context, RESULT_CHANNEL)
                .setSmallIcon(R.drawable.ic_ibrahim)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .build();
        context.getSystemService(NotificationManager.class).notify(RESULT_ID, notification);
    }
}
