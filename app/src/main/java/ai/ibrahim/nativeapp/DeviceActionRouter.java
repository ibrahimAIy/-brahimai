package ai.ibrahim.nativeapp;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import org.json.JSONObject;

import java.util.Locale;

public final class DeviceActionRouter {
    private final Context context;

    public DeviceActionRouter(Context context) {
        this.context = context;
    }

    public String performFromNaturalLanguage(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        String value = text.toLowerCase(new Locale("tr", "TR"));

        boolean connectVerb = containsAny(value, "bağlan", "baglan", "bağla", "bagla", "yansıt", "yansit", "cast", "smart view", "ekranı aktar", "ekrani aktar");
        if (connectVerb && containsAny(value, "televizyon", "tv", "ekran", "smart tv")) return perform("tv_cast", "");
        if (containsAny(value, "wi-fi", "wifi") && containsAny(value, "ayar", "aç", "ac", "bağlan", "baglan")) return perform("wifi_settings", "");
        if (value.contains("bluetooth") && containsAny(value, "ayar", "aç", "ac", "bağlan", "baglan")) return perform("bluetooth_settings", "");

        boolean openVerb = containsAny(value, "aç", "ac", "başlat", "baslat", "çalıştır", "calistir");
        if (openVerb) {
            if (value.contains("youtube")) return perform("open_app", "youtube");
            if (value.contains("spotify")) return perform("open_app", "spotify");
            if (value.contains("netflix")) return perform("open_app", "netflix");
            if (value.contains("chrome")) return perform("open_app", "chrome");
        }
        return "";
    }

    public String perform(String action, String payload) {
        String cleanAction = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        String cleanPayload = payload == null ? "" : payload.trim().toLowerCase(Locale.ROOT);
        try {
            switch (cleanAction) {
                case "tv_cast":
                    return openSettingsWithFallback(
                            new Intent(Settings.ACTION_CAST_SETTINGS),
                            new Intent(Settings.ACTION_WIRELESS_SETTINGS),
                            cleanAction,
                            "TV / ekran yayınlama ayarını açtım. Android cihaz listesinde televizyonunu seçmen gerekebilir."
                    );
                case "wifi_settings":
                    return launchIntent(new Intent(Settings.ACTION_WIFI_SETTINGS), cleanAction, "Wi-Fi ayarını açtım.");
                case "bluetooth_settings":
                    return launchIntent(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), cleanAction, "Bluetooth ayarını açtım.");
                case "system_settings":
                    return launchIntent(new Intent(Settings.ACTION_SETTINGS), cleanAction, "Telefon ayarlarını açtım.");
                case "open_app":
                    return openKnownApp(cleanPayload);
                default:
                    return result(false, cleanAction, "unsupported", "Bu cihaz eylemi güvenli yerel araç listesinde yok.", false);
            }
        } catch (Exception exception) {
            return result(false, cleanAction, "error", "Cihaz eylemi başlatılamadı.", false);
        }
    }

    private String openKnownApp(String name) {
        String packageName;
        switch (name) {
            case "youtube": packageName = "com.google.android.youtube"; break;
            case "spotify": packageName = "com.spotify.music"; break;
            case "netflix": packageName = "com.netflix.mediaclient"; break;
            case "chrome": packageName = "com.android.chrome"; break;
            default: return result(false, "open_app", "unsupported_app", "Bu uygulama güvenli hızlı-aç listesindeki uygulamalardan biri değil.", false);
        }
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) return result(false, "open_app", "not_installed", name + " bu telefonda bulunamadı.", false);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launch);
        return result(true, "open_app", "launch_requested", name + " uygulamasını açma isteğini gönderdim.", false);
    }

    private String openSettingsWithFallback(Intent primary, Intent fallback, String action, String message) {
        try {
            primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(primary);
            return result(true, action, "chooser_opened", message, false);
        } catch (ActivityNotFoundException exception) {
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fallback);
            return result(true, action, "fallback_settings_opened", "Kablosuz bağlantı ayarını açtım. TV / ekran paylaşımı seçeneğini buradan seçebilirsin.", false);
        }
    }

    private String launchIntent(Intent intent, String action, String message) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return result(true, action, "settings_opened", message, false);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static String result(boolean ok, String action, String status, String message, boolean verifiedConnected) {
        try {
            JSONObject json = new JSONObject();
            json.put("ok", ok);
            json.put("action", action == null ? "" : action);
            json.put("status", status);
            json.put("message", message);
            json.put("verifiedConnected", verifiedConnected);
            return json.toString();
        } catch (Exception exception) {
            return "{\"ok\":false,\"status\":\"serialization_error\",\"message\":\"Cihaz sonucu hazırlanamadı.\"}";
        }
    }
}
