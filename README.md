# AirDrop for Android

Quick Share desteği almayan Android telefonlar için AirDrop benzeri,
internetsiz yakın dosya paylaşım uygulaması.

> **Not — iPhone uyumluluğu:** Gerçek AirDrop, Apple'ın kapalı **AWDL**
> protokolünü kullanır ve bunu çalıştırmak Wi-Fi çipinde monitor mode +
> frame injection gerektirir. Android telefonlar bu erişimi vermez; bu yüzden
> root'suz hiçbir Android uygulaması iPhone'la gerçek AirDrop yapamaz
> (bkz. [Open Wireless Link](https://owlink.org/)). Bu uygulama, AirDrop
> deneyimini **Android ↔ Android** arasında birebir sunar.

## Özellikler

- **BLE keşif** — yakındaki cihazlar Bluetooth Low Energy yayını ile bulunur
  (AirDrop'un keşif fazı gibi). Dosya BLE'den değil, Wi-Fi'dan gider.
- **İki bağlantı yolu**
  - *Aynı Wi-Fi ağı:* mDNS (`NsdManager`, `_airdropdroid._tcp`) ile otomatik
    keşif, doğrudan TCP transfer (öncelikli, en hızlı yol).
  - *Wi-Fi Direct:* ortak ağ yoksa cihazlar doğrudan eşleşir (fallback).
- **Kabul/Ret onayı** — AirDrop'taki gibi alıcı, gönderenin adını ve dosya
  listesini görüp onaylar.
- **Şifreli transfer** — TLS (oturum başına self-signed sertifika).
- **Bütünlük doğrulaması** — her dosyada CRC32 checksum.
- **Paylaş menüsü entegrasyonu** — Galeri vb. herhangi bir uygulamadan
  "Paylaş" deyince bu uygulama listede çıkar (`ACTION_SEND` /
  `ACTION_SEND_MULTIPLE`).
- Alınan dosyalar `İndirilenler/AirDrop` klasörüne kaydedilir.

## Gereksinimler

- Android 16 (API 36)
- BLE destekli cihaz; Wi-Fi Direct opsiyonel

## Derleme

```bash
./gradlew assembleDebug
# çıktı: app/build/outputs/apk/debug/app-debug.apk
```

## Kullanım

1. Uygulamayı **iki** cihaza da kur ve aç (Bluetooth + bildirim izinlerini ver).
2. Alıcı cihazda uygulama açık kalsın (görünür mod).
3. Gönderen cihazda: Galeri → dosya seç → **Paylaş** → **AirDrop** →
   listeden hedef cihaza dokun.
4. Alıcıda çıkan pencerede **Kabul et**'e bas. Dosyalar
   `İndirilenler/AirDrop` klasörüne iner.

## Mimari

```
ui/         Compose ekranları (radar, onay penceresi, ilerleme)
data/       NearbyRepository (keşif + transferi birleştirir), dosya kaydetme
discovery/  BLE yayın + tarama
transport/  LAN (mDNS) keşfi, Wi-Fi Direct, TLS sunucu/istemci
protocol/   Framed uygulama protokolü (HELLO → kabul/ret → veri + CRC32)
service/    Foreground servis (arka planda alım için iskelet)
```

## Test

Protokol katmanı saf JVM'dir ve birim testlidir:

```bash
./gradlew test
```
