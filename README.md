# İbrahim AI Native Android

Bu proje mevcut İbrahim AI web/backend sistemini native Android kabuğunda çalıştırır ve ekran kapalıyken çalışabilen foreground wake-word servisi ekler.

## Neler var?

- Native Android WebView kabuğu: `https://ibrahim-ai-y1xmj0.v2.appdeploy.ai/`
- `foregroundServiceType="microphone"` ile 7/24 wake-word servisi
- Düşük güç wake-word motoru: Picovoice Porcupine 4.0.2
- Varsayılan yedek wake word: `Jarvis`
- Picovoice Console'dan oluşturulan Android `.ppn` dosyasını içe aktararak `İbrahim` gibi özel wake word kullanma
- Wake word algılandıktan sonra tek seferlik Türkçe Android `SpeechRecognizer`
- Tanınan komutun güvenli cihaz tokenı ile İbrahim AI backend'ine gönderilmesi
- Cihaz tokenı ve Picovoice AccessKey için Android Keystore AES/GCM şifreleme
- Türkçe Text-to-Speech ile sesli cevap
- V29 ayrı Sesli Sohbet bölümü: mikrofon yalnızca üstteki ses simgesinden bu bölüme girildiğinde açılır
- Sesli bölümden çıkınca mikrofon ve canlı sohbet servisi tamamen kapanır
- Ekrana yanıt basmadan sesli cevap, tekrarlanan açılış anonsu olmadan hızlı dinleme
- Cihazdaki en kaliteli Türkçe sesi otomatik seçme, akıcı hız/perde ve konuşmaya uygun metin temizleme
- Saat ve pil yüzdesi gibi bazı komutları internetsiz yerel çalıştırma
- Foreground bildiriminden duraklat / devam / kapat
- Telefon yeniden başladığında Android kısıtları nedeniyle kullanıcıya yeniden etkinleştirme bildirimi
- Pil optimizasyonu ayarına hızlı geçiş
- GitHub Actions ile otomatik debug APK üretimi

## İlk kurulum

1. GitHub Actions'ın ürettiği `app-debug.apk` dosyasını telefona kur.
2. Uygulamayı aç ve İbrahim AI hesabına giriş yap. Native kabuk algılanınca cihaz otomatik eşleşir.
3. Üstteki `Native Ayarlar` bölümünü aç.
4. Picovoice Console'dan aldığın AccessKey'i uygulamanın içindeki alana gir. Anahtarı sohbetlere gönderme.
5. İlk testte özel model olmadan `Jarvis` diyerek uyandırabilirsin.
6. `İbrahim` wake word kullanmak için Picovoice Console'da Android için özel wake word oluştur, `.ppn` dosyasını indir ve `İbrahim .ppn wake-word dosyasını seç` düğmesiyle içe aktar.
7. `7/24 Başlat` düğmesine bas ve mikrofon iznini ver.
8. Android pil ayarlarında İbrahim AI'ı mümkünse `Kısıtlanmamış` yap.

## Android kısıtı

Android 14+ sürümlerde mikrofon kullanan foreground service uygulama arka plandayken veya `BOOT_COMPLETED` alıcısından doğrudan başlatılamaz. İlk başlatma görünür Activity içinden yapılır. Telefon yeniden açılırsa uygulama bir bildirim gösterir; kullanıcı bildirime dokunup servisi tekrar başlatır. Servis başladıktan sonra uygulama ekranda değilken ve ekran kapalıyken çalışabilir. Üretici pil yönetimi, kullanıcı tarafından zorla durdurma veya sistem kaynak baskısı nedeniyle üçüncü taraf Android uygulamalarında mutlak %100 kesintisiz çalışma garantisi yoktur.

## Ses gizliliği

Porcupine wake-word dinleme telefonda yerel çalışır. Wake word algılandıktan sonra Android 12+ cihaz-içi `SpeechRecognizer` varsa o tercih edilir; yoksa Android sistem ses tanıyıcısı sağlayıcısına göre ağ kullanabilir. İbrahim AI backend'ine wake-word sesi değil, yalnızca tanınmış komut metni gönderilir.

## APK

`.github/workflows/build-apk.yml`, `main` dalına gönderilen her güncellemede `app-debug.apk` üretir ve `IbrahimAI-Native-debug-apk` adlı artifact olarak saklar.
