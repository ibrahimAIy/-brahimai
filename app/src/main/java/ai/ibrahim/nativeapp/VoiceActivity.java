package ai.ibrahim.nativeapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class VoiceActivity extends Activity {
    private static final int REQUEST_MIC = 2901;
    private boolean started;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildVoiceScreen();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoice();
        } else {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
        }
    }

    private void buildVoiceScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(44), dp(28), dp(36));
        root.setBackgroundColor(Color.rgb(7, 9, 8));

        TextView title = text("NOXARA", 28, Color.rgb(151, 255, 183), true);
        TextView subtitle = text("Sesli Sohbet", 20, Color.WHITE, true);
        TextView orb = text("≋", 64, Color.rgb(125, 255, 166), true);
        GradientDrawable orbBackground = new GradientDrawable();
        orbBackground.setShape(GradientDrawable.OVAL);
        orbBackground.setColor(Color.rgb(12, 45, 27));
        orbBackground.setStroke(dp(2), Color.rgb(49, 143, 82));
        orb.setBackground(orbBackground);
        orb.setGravity(Gravity.CENTER);
        orb.setElevation(dp(12));
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(dp(176), dp(176));
        orbParams.setMargins(0, dp(42), 0, dp(34));

        TextView status = text("Dinliyorum…", 23, Color.WHITE, true);
        TextView hint = text("Konuşmaya başla. Cevap verdikten sonra seni otomatik dinlemeye devam ederim.", 15, Color.rgb(166, 177, 170), false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(12), dp(12), dp(12), dp(30));

        Button close = new Button(this);
        close.setText("Sesli sohbetten çık");
        close.setTextSize(16);
        close.setTextColor(Color.WHITE);
        close.setAllCaps(false);
        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setCornerRadius(dp(18));
        buttonBackground.setColor(Color.rgb(23, 75, 43));
        close.setBackground(buttonBackground);
        close.setOnClickListener(view -> finish());

        root.addView(title);
        root.addView(subtitle);
        root.addView(orb, orbParams);
        root.addView(status);
        root.addView(hint, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(close, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));
        setContentView(root);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void startVoice() {
        if (started) return;
        started = true;
        Intent intent = new Intent(this, WakeWordService.class);
        intent.setAction(WakeWordService.ACTION_CONVERSATION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
    }

    private void stopVoice() {
        if (!started) return;
        started = false;
        stopService(new Intent(this, WakeWordService.class));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MIC && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startVoice();
        else if (requestCode == REQUEST_MIC) finish();
    }

    @Override
    protected void onDestroy() {
        stopVoice();
        super.onDestroy();
    }
}
