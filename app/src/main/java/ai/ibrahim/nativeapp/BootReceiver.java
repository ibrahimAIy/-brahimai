package ai.ibrahim.nativeapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean wanted = context.getSharedPreferences("native_prefs", Context.MODE_PRIVATE).getBoolean("desired_enabled", false);
        if (wanted) NotificationHelper.postRestartRequired(context);
    }
}
