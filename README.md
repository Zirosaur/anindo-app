# anindo-app (Anime Indo Android)

> 📱 **Aplikasi Streaming & Unduh Anime Bebas Iklan untuk Android (Subtitle Indonesia), terinspirasi oleh filosofi dan desain Mihon / Tachiyomi.**

[![Download Beta APK](https://img.shields.io/badge/Download-v0.1.0--beta%20APK-00E5FF?style=for-the-badge&logo=android&logoColor=black)](https://github.com/Zirosaur/anindo-app/releases/download/v0.1.0-beta/anindo-app-v0.1.0-beta.apk)
[![Release](https://img.shields.io/github/v/release/Zirosaur/anindo-app?include_prereleases&style=for-the-badge&color=00E5FF)](https://github.com/Zirosaur/anindo-app/releases/tag/v0.1.0-beta)


---

## ✨ Fitur Utama

- 🚫 **100% Bebas Iklan (Zero Ads & Open Source)**: Pengalaman streaming murni tanpa banner judi, pop-under, atau redirect berbahaya.
- 🎨 **Desain Material You 3 (Mihon-Style UX)**:
  - **Koleksi (Library)**: Simpan dan tandai anime favoritmu.
  - **Ongoing Feed**: Episode terbaru yang tayang musim ini terupdate setiap hari.
  - **Jelajah (Explore)**: Pencarian cepat lintas penyedia (*multi-provider search*).
  - **Riwayat (History)**: Riwayat tontonan lengkap dengan persentase dan waktu jeda.
- 🌐 **Arsitektur Scraper Modular & Anti-Blokir**:
  - **Dynamic Domain Resolver**: Otomatis mendeteksi domain baru jika situs web sumber berganti alamat.
  - **Cross-Provider Episode Stream Fallback**: Jika server pada satu penyedia mati, aplikasi otomatis mencari mirror episode di penyedia lain secara transparan.
  - **Remote OTA Rules**: Pembaruan selektor web tanpa perlu mengunduh ulang APK.
- 🎬 **Pemutar Video Terintegrasi**:
  - Menggunakan AndroidX Media3 / ExoPlayer dengan dukungan HLS (`.m3u8`) dan dekripsi Putarin AES-256-GCM.
  - Smart Seek Resume: Otomatis melanjutkan pemutaran di menit/detik terakhir ditonton.
- 📥 **Download Manager Terjadwal**:
  - Unduh episode ke penyimpanan lokal untuk ditonton secara offline saat bepergian.

---

## 🏛️ Arsitektur Teknologi (Tech Stack)

* **Bahasa**: Kotlin (100% Native Android)
* **Antarmuka (UI)**: Jetpack Compose + Material 3 Design System
* **Arsitektur**: MVVM (Model-View-ViewModel) + Single Activity Navigation
* **Jaringan & Ekstraksi**: OkHttp 4 + Jsoup DOM Parser
* **Pemuatan Gambar**: Coil Compose (Memory & Disk Cache otomatis)
* **Media Player**: AndroidX Media3 (ExoPlayer HLS Engine)
* **Database Lokal**: Android Room Database (SQLite)
* **Build System**: Gradle Kotlin DSL (`build.gradle.kts`) + Version Catalog (`libs.versions.toml`)

---

## 🚀 Membangun dari Sumber (Build from Source)

### Prasyarat:
- JDK 17 atau yang lebih baru
- Android SDK (API 34)

### Langkah Kompilasi:
```bash
# Clone repositori
git clone https://github.com/Zirosaur/anindo-app.git
cd anindo-app

# Berikan izin eksekusi gradle wrapper
chmod +x gradlew

# Bangun berkas APK Debug
./gradlew assembleDebug
```
Berkas APK hasil kompilasi akan berada di:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📦 Unduh Berkas APK (Rilis)

Setiap pembaruan dan rilis APK resmi dapat diunduh langsung di halaman:
👉 **[GitHub Releases](https://github.com/Zirosaur/anindo-app/releases)**

---

## 🤝 Kontribusi & Lisensi

Proyek ini bersifat terbuka untuk komunitas anime Indonesia.
Dikembangkan dengan ❤️ untuk menghadirkan pengalaman menonton anime yang bersih dan nyaman di Android.
