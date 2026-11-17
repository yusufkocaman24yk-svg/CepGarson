# CepGarson

Dijital adisyon & QR menü uygulaması (Capacitor + tek sayfa HTML).

## APK nasıl üretilir
GitHub'a yükleyin (`main` dalı). **Actions → Build Android APK** otomatik çalışır; bittiğinde
*Artifacts → CepGarson* altından `app-debug.apk` indirilir.
Workflow şunları APK'ya otomatik ekler:
- `android-assets/java/*` → yazıcı eklentisi (Wi-Fi / Bluetooth / sistem yazdırma)
- `android-assets/res/*`  → uygulama ikonu (giriş ekranındaki logo ile aynı)
- Bluetooth / ağ izinleri

## Yazıcı ekleme
Yönetici Paneli → **Yazıcı** → **Yazıcı Ekle**
- **Wi-Fi / Ağ:** IP + port (genelde 9100) yazın, ya da *Ağda Yazıcı Ara* / *QR ile Ekle*.
- **Bluetooth:** yazıcıyı önce telefon ayarlarından eşleştirin, sonra *Eşleşmiş Cihazları Getir*.
- QR içeriği olarak şunlar okunur: `192.168.1.50:9100`, `tcp://192.168.1.50:9100`,
  `http://192.168.1.50`, `AA:BB:CC:DD:EE:FF`, `bt://AA:BB:CC:DD:EE:FF`,
  `{"name":"Mutfak","ip":"192.168.1.50","port":9100}`

## Ürün seçenekleri / ekstreler
Menü → Ürün Ekle/Düzenle → **Seçenekler & Ekstreler** (Turşu, İçecek, Ekstra peynir + fiyat…).
Müşteri ürünü eklerken seçer; seçimler fişte görünür. Sepette **Burada / Paket** seçilir.

## Logo
`branding/logo-512.png` (mağaza / paylaşım için).

## Gün sonu, iptal, düzenleme
- **Satışı iptal et / düzenle:** Gün Sonu sekmesi → satırdaki *İptal* / *Düzenle*. Neden zorunlu (iptal), şifre/PIN sorulur.
  İptal edilen satış silinmez; cirodan düşer, "İptaller" bölümünde görünür. Düzenlemede sadece adet azaltılır, yeni fiş
  "DÜZELTİLMİŞ FİŞ (Eski Fiş No: …)" olarak çıkar, eski hâl kayıtta saklanır.
- **Gün sonu:** *Gün Sonu Al* ile günün satışları kilitlenir (iptal/düzenleme butonları gizlenir).
  *Günü Yeniden Aç* (şifreli) satışlara dokunmaz, sadece o gün sonu raporunu geçersiz sayar.
- **Otomatik gün sonu:** gece 00:00'da (uygulama açıksa) ya da sonraki ilk açılışta, kapanmamış geçmiş günler otomatik kapanır.
- **İşlem PIN'i:** Şifre sekmesi → İşlem PIN'i. Görmek/değiştirmek için yönetici şifresi gerekir. PIN iki kez girilir (göz ikonuyla göster/gizle), kayıttan sonra PIN ve QR kodu görünür; *Düzenle* ile değiştirilir. PIN tanımlı değilse işlemlerde yönetici şifresi sorulur.
- Tüm işlemler günün altındaki "İşlem Kaydı"nda listelenir.

## Ürün servis saati
Ürün Ekle/Düzenle → **Servis Saati** (başlangıç / bitiş). Boş bırakılırsa ürün her zaman satışta.
Saat dışında müşteri menüsünde ürün ya soluk gösterilir ("17:00 itibarıyla servis edilir", sipariş verilemez)
ya da tamamen gizlenir (ürün bazında seçilir). Gece yarısını geçen servis desteklenir (örn: 18:00 – 02:00).
Menü 30 sn'de bir kendini yeniler; sepetteki ürün servis dışına çıkarsa sipariş anında sepetten çıkarılır.

## Web / PC sürümü
- `www/` klasörü aynı zamanda web sitesidir (Netlify / Cloudflare Pages / Firebase Hosting'e `www` klasörü yayınlanır; build komutu gerekmez).
- Chrome/Edge'de "Uygulamayı yükle" ile bilgisayara/telefona kurulur (`manifest.webmanifest`, `sw.js`, `icon-*.png`). İnternet kesilince önbellekten açılır.
- Masa QR'ları: Masa QR sekmesinde "Web adresi" doluysa QR `https://adres/?masa=Masa 1` içerir; müşteri kamerayla okutunca menü doğrudan açılır, masa otomatik gelir.
- PC'de "Fiş Yazdır" Windows'a kurulu yazıcıdan çıkar (USB/Zebra dahil).
