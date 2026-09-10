package ai.ibrahim.nativeapp;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DeveloperAgent {
    private final Context context;
    private final NativeApiClient api;

    public interface Listener {
        void onProgress(String message);
        void onSuccess(ProjectResult result);
        void onError(String message);
    }

    public static final class ProjectResult {
        public final String title;
        public final String specification;
        public final String html;
        public final String css;
        public final String javascript;
        public final String playableHtml;
        public final File directory;

        ProjectResult(String title, String specification, String html, String css, String javascript, String playableHtml, File directory) {
            this.title = title;
            this.specification = specification;
            this.html = html;
            this.css = css;
            this.javascript = javascript;
            this.playableHtml = playableHtml;
            this.directory = directory;
        }
    }

    public DeveloperAgent(Context context, NativeApiClient api) {
        this.context = context.getApplicationContext();
        this.api = api;
    }

    public void buildGame(String request, Listener listener) {
        String clean = compact(request, 700);
        if (clean.isEmpty()) {
            listener.onError("Nasıl bir oyun istediğini yaz.");
            return;
        }
        listener.onProgress("Geliştirici Ajanı oyunu planlıyor…");
        String planPrompt = "Sen İbrahim AI Geliştirici Ajanısın. Mobil tarayıcıda çalışan, dokunmatik kontrollü, dış kütüphane ve dış görsel gerektirmeyen küçük ama oynanabilir bir HTML5 oyun tasarla. İstek: " + clean + "\n" +
                "Yalnızca kısa teknik plan ver. İlk satır OYUN_ADI: ile başlasın. Sonra mekanik, kontrol, kazanma/kaybetme ve gereken DOM öğelerini yaz. 1100 karakteri geçme.";
        api.sendCommand(planPrompt, new NativeApiClient.Callback() {
            @Override
            public void onSuccess(String plan) {
                String specification = compact(plan, 1300);
                String title = extractTitle(specification);
                generateHtml(clean, title, specification, listener);
            }

            @Override
            public void onError(String message) {
                listener.onError("Plan oluşturulamadı: " + message);
            }
        });
    }

    private void generateHtml(String request, String title, String specification, Listener listener) {
        listener.onProgress("Arayüz oluşturuluyor…");
        String prompt = "İbrahim AI Geliştirici Ajanı olarak şu oyun için SADECE body içine konacak HTML parçasını üret. Markdown/fence/açıklama yok. script/style/html/head/body etiketi kullanma. Dokunmatik butonlar ve oyun alanı erişilebilir olsun.\n" +
                "İstek: " + compact(request, 500) + "\nPlan: " + compact(specification, 1200);
        api.sendCommand(prompt, new NativeApiClient.Callback() {
            @Override
            public void onSuccess(String html) {
                generateCss(request, title, specification, stripCodeFences(html), listener);
            }

            @Override
            public void onError(String message) {
                listener.onError("HTML üretilemedi: " + message);
            }
        });
    }

    private void generateCss(String request, String title, String specification, String html, Listener listener) {
        listener.onProgress("Görünüm ve mobil kontroller hazırlanıyor…");
        String prompt = "Şu mobil HTML5 oyun için SADECE CSS üret. Markdown/fence/açıklama yok. Ekrana sığsın, touch-action ve seçilememe gibi oyun mobil optimizasyonlarını ekle; okunaklı ve koyu temalı olsun.\n" +
                "İstek: " + compact(request, 420) + "\nPlan: " + compact(specification, 1050) + "\nHTML özeti: " + compact(html, 900);
        api.sendCommand(prompt, new NativeApiClient.Callback() {
            @Override
            public void onSuccess(String css) {
                generateJavascript(request, title, specification, html, stripCodeFences(css), listener);
            }

            @Override
            public void onError(String message) {
                listener.onError("CSS üretilemedi: " + message);
            }
        });
    }

    private void generateJavascript(String request, String title, String specification, String html, String css, Listener listener) {
        listener.onProgress("Oyun motoru ve kurallar kodlanıyor…");
        String prompt = "Şu HTML5 oyun için SADECE vanilla JavaScript üret. Markdown/fence/açıklama yok. Harici paket kullanma. Mobil dokunma + mümkünse klavye desteği, oyun döngüsü, skor/can, yeniden başlatma, kazanma/kaybetme ve hata vermeyen başlangıç ekle. DOM id/class adlarını verilen HTML ile uyumlu kullan.\n" +
                "İstek: " + compact(request, 420) + "\nPlan: " + compact(specification, 1100) + "\nHTML: " + compact(html, 1150);
        api.sendCommand(prompt, new NativeApiClient.Callback() {
            @Override
            public void onSuccess(String javascript) {
                finishProject(title, specification, html, css, stripCodeFences(javascript), listener);
            }

            @Override
            public void onError(String message) {
                listener.onError("JavaScript üretilemedi: " + message);
            }
        });
    }

    private void finishProject(String title, String specification, String html, String css, String javascript, Listener listener) {
        listener.onProgress("Dosyalar kontrol edilip proje kaydediliyor…");
        String cleanHtml = sanitizeFragment(html);
        String cleanCss = css.trim();
        String cleanJs = javascript.trim();
        if (cleanHtml.length() < 20 || cleanJs.length() < 40) {
            listener.onError("Üretilen oyun kodu eksik kaldı. Aynı isteği tekrar deneyebilirsin.");
            return;
        }

        String playable = "<!doctype html><html lang=\"tr\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\"><title>" +
                escapeHtml(title) + "</title><style>" + cleanCss + "</style></head><body>" + cleanHtml + "<script>\n" + cleanJs + "\n</script></body></html>";
        try {
            File root = new File(context.getFilesDir(), "generated-games");
            if (!root.exists() && !root.mkdirs()) throw new IllegalStateException("games-dir");
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File directory = new File(root, safeName(title) + "-" + stamp);
            if (!directory.mkdirs()) throw new IllegalStateException("project-dir");
            write(new File(directory, "index.html"), playable);
            write(new File(directory, "style.css"), cleanCss);
            write(new File(directory, "game.js"), cleanJs);
            write(new File(directory, "PLAN.txt"), specification);
            listener.onSuccess(new ProjectResult(title, specification, cleanHtml, cleanCss, cleanJs, playable, directory));
        } catch (Exception exception) {
            listener.onError("Oyun dosyaları telefona kaydedilemedi.");
        }
    }

    private static void write(File file, String content) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String extractTitle(String plan) {
        if (plan != null) {
            for (String line : plan.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (trimmed.toUpperCase(Locale.ROOT).startsWith("OYUN_ADI:")) {
                    String value = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                    if (!value.isEmpty()) return compact(value, 55);
                }
            }
        }
        return "İbrahim AI Oyunu";
    }

    private static String stripCodeFences(String value) {
        if (value == null) return "";
        String clean = value.trim();
        if (clean.startsWith("```")) {
            int firstNewline = clean.indexOf('\n');
            if (firstNewline >= 0) clean = clean.substring(firstNewline + 1);
            int closing = clean.lastIndexOf("```");
            if (closing >= 0) clean = clean.substring(0, closing);
        }
        return clean.trim();
    }

    private static String sanitizeFragment(String value) {
        String clean = stripCodeFences(value);
        clean = clean.replaceAll("(?is)<!doctype[^>]*>", "");
        clean = clean.replaceAll("(?is)</?(html|head|body)[^>]*>", "");
        clean = clean.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        clean = clean.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        return clean.trim();
    }

    private static String compact(String value, int max) {
        if (value == null) return "";
        String clean = value.replace('\u0000', ' ').replaceAll("[\\t\\r ]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private static String safeName(String value) {
        String clean = value == null ? "oyun" : value.toLowerCase(new Locale("tr", "TR"));
        clean = clean.replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u').replace('ş', 's').replace('ö', 'o').replace('ç', 'c');
        clean = clean.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return clean.isEmpty() ? "oyun" : compact(clean, 40);
    }

    private static String escapeHtml(String value) {
        if (value == null) return "Oyun";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
