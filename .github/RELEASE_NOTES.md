# AirDrop for Android — v1.0.0

Quick Share desteği almayan Android telefonlar için AirDrop benzeri,
internetsiz yakın dosya paylaşımı. İlk sürüm! 🎉

## Özellikler

- 📡 **BLE keşif** — yakındaki cihazlar otomatik bulunur
- 📶 **İki bağlantı yolu** — aynı Wi-Fi ağında mDNS + TCP (hızlı yol),
  ortak ağ yoksa Wi-Fi Direct (fallback)
- ✅ **Kabul/Reddet onayı** — AirDrop'taki gibi alıcı onaylamadan transfer başlamaz
- 🔔 **Arka planda alım** — uygulama kapalıyken de dosya alınır; gelen istekler
  bildirimden Kabul/Reddet ile yanıtlanır, ilerleme bildirimde görünür
- 🔒 **TLS şifreli transfer** + her dosyada CRC32 bütünlük doğrulaması
- 📤 **Paylaş menüsü entegrasyonu** — Galeri vb. her uygulamadan tek dokunuşla
- 📁 Alınan dosyalar **İndirilenler/AirDrop** klasörüne kaydedilir

## Kurulum

1. Aşağıdaki APK'yı **iki cihaza da** indirip kurun (Android 16 gerekir).
2. Uygulamayı açın, Bluetooth ve bildirim izinlerini verin.
3. Gönderen: Galeri → Paylaş → AirDrop → cihaza dokun.
4. Alıcı: bildirimden veya uygulamadan **Kabul et**.

## Bilinen sınırlamalar

- iPhone/Mac ile gerçek AirDrop uyumu teknik olarak mümkün değildir
  (Apple'ın kapalı AWDL protokolü; ayrıntı README'de).
- APK ilk sürümde debug anahtarıyla imzalıdır.
