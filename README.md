# İbrahim AI Native Android

Bu proje, mevcut İbrahim AI web/backend uygulamasını native Android kabuğuna taşır ve ekran kapalıyken çalışabilen foreground wake-word servisi ekler.

## İçerik

- Native Android WebView kabuğu: `https://ibrahim-ai-y1xmj0.v2.appdeploy.ai/`
- `foregroundServiceType="microphone"` ile kalıcı 7/24 dinleme servisi
- Düşük güç wake-word motoru: Picovoice Porcupine 4.0.2
- Özel `.ppn` wake-word dosyası desteği
- Uygulama içinden `Ibrahim` wake-word modeli üretme denemesi (English model)
- Wake word algılandıktan sonra tek seferlik Türkçe `SpeechRecognizer` komut çözümleme; Android 12+ cihaz-içi tanıyıcı varsa öncelik verilir
- Native komutların güvenli cihaz tokenı ile İbrahim AI backend'ine gönderilmesi; İbrahim AI sunucusuna ses değil yalnızca tanınmış metin gider
- Token ve Picovoice AccessKey için Android Keystore AES/GCM şifreleme
- Türkçe Text-to-Speech ile sesli cevap
- Saat ve pil yüzdesi için internetsiz yerel komutlar
- Kalıcı foreground bildirimi: duraklat / devam / kapat
- Telefon yeniden açıldığında Android kısıtları nedeniyle mikrofonu sessizce başlatmak yerine yeniden etkinleştirme bildirimi
- Pil optimizasyonu ayarına hızlı geçiş
- GitHub Actions ile debug APK üretme workflow'u

## İlk kurulum

1. APK'yı kur ve İbrahim AI hesabına giriş yap. Web uygulaması native kabuğu algılar ve cihazı otomatik eşleştirir.
2. `Native Ayarlar` bölümünü aç.
3. Picovoice Console'dan aldığın AccessKey'i **uygulamanın içindeki alana** gir. Anahtarı sohbetlere gönderme.
4. İstersen `“İbrahim” wake-word modelini üret` düğmesini dene. Başarılı olmazsa Picovoice Console'dan Android için oluşturulmuş `.ppn` dosyasını `Hazır .ppn wake-word dosyası seç` ile içe aktar.
5. `7/24 Başlat` düğmesine bas ve mikrofon iznini ver.
6. Pil ayarlarında İbrahim AI'ı mümkünse `Kısıtlanmamış` yap.

Özel model yoksa servis geçici wake word olarak `Jarvis` kullanır.

## Android kısıtı

Android 14+ sürümlerde mikrofon kullanan foreground service uygulama arka plandayken veya BOOT_COMPLETED alıcısından doğrudan başlatılamaz. Bu nedenle ilk başlatma görünür Activity içinden yapılır. Telefon yeniden açılırsa uygulama bir bildirim gösterir; kullanıcı bildirime dokunup servisi tekrar başlatır. Servis başladıktan sonra uygulama ekranda değilken ve ekran kapalıyken çalışmaya devam edebilir, ancak üretici pil yönetimi / kullanıcı tarafından zorla durdurma / sistem kaynak baskısı nedeniyle hiçbir üçüncü taraf Android uygulaması mutlak %100 kesintisiz çalışma garantisi veremez.

## APK oluşturma

Projeyi GitHub'a koyarsan `.github/workflows/build-apk.yml` otomatik olarak `app-debug.apk` üretir. Android Studio ile de projeyi açıp Run veya Build APK kullanabilirsin.

## Ses gizliliği

Porcupine wake-word dinleme telefonda yerel çalışır. Wake word algılandıktan sonra komut çözümlemede Android 12+ cihaz-içi `SpeechRecognizer` varsa o tercih edilir; cihaz-içi model yoksa Android sistem tanıyıcısı sağlayıcısına göre ses tanıma için ağ kullanabilir. İbrahim AI backend'ine gönderilen veri tanınmış komut metnidir.
