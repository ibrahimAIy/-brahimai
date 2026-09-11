# İbrahim AI Netlify Shell

Bu klasör, mevcut İbrahim AI AppDeploy uygulamasını Netlify üzerinden yayınlayan kalıcı kabuktur.

Netlify ayarları:
- Repository: `ibrahimAIy/-brahimai`
- Branch: `main`
- Base directory: `netlify-shell`
- Publish directory: `public`
- Build command: boş bırakılabilir

Amaç:
- AppDeploy yayın kotasına takılmadan kabuk/UI güncellemelerini GitHub push ile yayınlamak.
- Her yeni uygulama açılışında temiz `Yeni sohbet` ekranına geçmek.
- Eski konuşmaları silmeden menüde tutmak.
- Mevcut AppDeploy backend, hafıza, piyasa, araştırma ve ajan özelliklerini reverse proxy ile korumak.

Not: AppDeploy backend bağımlılığı daha sonra aşamalı olarak yeni altyapıya taşınabilir.
