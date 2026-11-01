# Spotibook

Spotibook adalah aplikasi Android untuk baca ebook dan mencatat kebiasaan membaca. Buku disimpan di perangkat, bisa dikelompokkan ke rak, lalu dibaca sambil melihat progres, waktu baca, dan target harian.

Aplikasi ini berjalan offline, tanpa akun, iklan, atau sinkronisasi cloud. Antarmukanya berbahasa Indonesia.

## Fitur

- **Pustaka dan rak buku.** Impor beberapa file sekaligus, cari judul, urutkan buku, tandai status baca, dan masukkan buku ke satu atau beberapa rak. Ada pemindai PDF dan EPUB di penyimpanan perangkat.
- **Pembaca PDF.** Zoom, bookmark, catatan halaman, enam mode perpindahan halaman, serta tampilan dua halaman saat landscape. Pencarian teks tersedia di Android 15 ke atas untuk PDF yang memiliki lapisan teks.
- **Pembaca EPUB, TXT, dan HTML.** Atur ukuran huruf, jarak baris, margin, dan tema. Tersedia sorotan teks, catatan, bookmark, dan penyimpanan posisi baca.
- **Catatan membaca.** Waktu baca, kalender, streak, target harian, statistik per buku, dan pencapaian. Waktu dihitung saat pembaca aktif; menjalankan timer saja tidak menambah waktu baca.
- **Timer Fokus.** Pilih durasi dan buku, jeda atau lanjutkan sesi, lalu atur pemberitahuan selesai dengan nada bawaan, MP3 sendiri, atau mode senyap.
- **Pengingat dan widget.** Pengingat harian serta empat widget: streak, target harian, buku aktif, dan akses cepat membaca.
- **Cadangan lokal.** Ekspor dan pulihkan pustaka lewat ZIP, dengan pilihan menyertakan file buku. Riwayat dan statistik juga bisa diekspor ke CSV.

## Cara pakai

1. Buka **Pustaka**, tekan **+**, lalu pilih file buku. Pilihan **Cari buku di perangkat** bisa dipakai untuk memindai PDF dan EPUB; izin akses penyimpanannya opsional untuk impor manual.
2. Ketuk sampul untuk membaca. Posisi terakhir disimpan agar bisa dilanjutkan nanti.
3. Tekan lama buku untuk memilih beberapa buku, lalu masukkan ke rak. Rak bisa dibuat dari dialog tersebut atau tab **Rak buku**.
4. Atur target baca di **Profil**. Kalender dan **Statistik** akan terisi dari aktivitas membaca.
5. Gunakan **Fokus** kalau ingin membaca dengan timer. Timer juga bisa dibuka dari toolbar pembaca.
6. Untuk mencadangkan data, buka **Profil** dan pilih pencadangan lokal. Centang **Sertakan berkas buku** kalau ingin bukunya ikut dipindahkan ke perangkat lain.

Menghapus buku dari pustaka menghapus salinan di aplikasi beserta bookmark dan catatannya. File asli di perangkat tetap ada, begitu juga catatan statistiknya. Mengeluarkan buku dari satu rak hanya menghapus keanggotaan rak tersebut.

## Menjalankan dari source

Versi saat ini **1.4.0** (`versionCode 5`), dengan application ID `com.spotibook.app`. Minimum Android **9 / API 28**; compile dan target SDK **36**.

Yang perlu disiapkan:

- Android Studio yang mendukung konfigurasi Gradle project ini.
- JDK 17 untuk menjalankan Gradle.
- Android SDK Platform 36 dan Android SDK Build-Tools, dipasang lewat SDK Manager.
- Perangkat Android atau emulator untuk menjalankan aplikasi.

Buka folder project di Android Studio, tunggu Gradle Sync selesai, pilih perangkat, lalu tekan **Run**. Koneksi internet dibutuhkan saat mengunduh SDK dan dependency pertama kali; aplikasi yang sudah terpasang tetap bisa dipakai offline.

Untuk build lewat terminal, pastikan lokasi Android SDK sudah terbaca lewat `local.properties` atau environment variable `ANDROID_HOME`. Android Studio biasanya membuat `local.properties` saat project dibuka. File itu khusus komputer masing-masing dan tidak ikut Git.

**Windows / PowerShell**, dari folder utama project:

```powershell
.\gradlew.bat assembleDebug
```

**macOS / Linux:**

```sh
chmod +x gradlew
./gradlew assembleDebug
```

Hasil APK debug ada di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

APK tersebut bisa dipasang ke perangkat Android untuk mencoba aplikasi. Jika perangkat sudah terhubung untuk debugging, gunakan `installDebug` sebagai pengganti `assembleDebug` untuk langsung memasangnya.

Untuk build release di Windows:

```powershell
.\gradlew.bat assembleRelease
```

Hasilnya `app/build/outputs/apk/release/app-release-unsigned.apk`. APK release masih perlu ditandatangani, misalnya lewat **Generate Signed Bundle / APK** di Android Studio. Keystore tidak disertakan dalam repo. Simpan kunci yang sama untuk pembaruan berikutnya; build debug dari komputer berbeda juga bisa memakai kunci berbeda. Cadangkan data sebelum mengganti instalasi yang tanda tangannya tidak cocok.

## Isi project

```text
app/
  src/main/
    java/com/spotibook/app/
      ui/           Halaman Beranda, Pustaka, Fokus, Statistik, dan Profil
      reader/       Impor dokumen, pembaca PDF/teks, dan pengaturan pembaca
      data/         Database, repository, preferensi, dan backup
      domain/       Perhitungan waktu baca, streak, dan pencapaian
      background/   Timer, pengingat, widget, dan perawatan riwayat
    res/            Font, ikon, tema, dan konfigurasi widget
    assets/         Lisensi font yang dibundel
    AndroidManifest.xml
  src/test/         Unit test dan pengujian UI dengan Robolectric
  src/androidTest/  Pengujian pada perangkat atau emulator
  schemas/          Skema database Room untuk migrasi
gradle/wrapper/     Gradle Wrapper
build.gradle.kts    Versi plugin build
settings.gradle.kts
VALIDATION.md       Catatan hasil pengujian dan hal yang perlu dicek di perangkat
```

Project memakai **Kotlin dan Jetpack Compose**. Data pustaka dan aktivitas membaca disimpan lewat **Room**, sedangkan pengaturan memakai **DataStore**. PDF dirender dengan `PdfRenderer`; EPUB dan teks ditampilkan lewat WebView lokal. **WorkManager** menangani pekerjaan latar belakang dan **Glance** dipakai untuk widget.

## Pengujian

Jalankan unit test dan Android Lint:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
```

Untuk pengujian yang membutuhkan perangkat atau emulator tersambung:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Di macOS/Linux, ganti `.\gradlew.bat` dengan `./gradlew`. Laporan unit test ada di `app/build/reports/tests/testDebugUnitTest/index.html`, dan laporan lint di `app/build/reports/lint-results-debug.html`.

Catatan pengujian sebelumnya dan batas pengujiannya ada di [VALIDATION.md](VALIDATION.md). Perilaku launcher, widget, audio, serta alarm saat layar mati masih perlu diperiksa langsung di perangkat.

## Batasan

- EPUB yang didukung adalah reflowable tanpa DRM. EPUB terenkripsi dan fixed-layout belum didukung.
- TXT memakai UTF-8. HTML mandiri tidak memuat file pendukung di luar file yang diimpor.
- PDF belum mendukung sorotan teks atau OCR. Pencarian PDF memerlukan Android 15 ke atas dan teks yang bisa dicari. Zoom tinggi juga masih dibatasi resolusi bitmap.
- Pengingat dan pemberitahuan Fokus bisa terlambat karena pembatasan baterai Android. Fokus menyediakan akses alarm tepat waktu yang opsional; hasilnya tetap perlu diuji di perangkat.
- Daftar riwayat menampilkan 30 hari terakhir. Menghapus entri riwayat menyembunyikannya dari daftar tanpa mengurangi statistik membaca.
- Tidak ada sinkronisasi antarperangkat. Backup ZIP tidak dienkripsi, dan audio Fokus pilihan sendiri tidak ikut backup. Simpan cadangan di tempat pribadi.

## Data dan izin

Aplikasi tidak meminta izin internet. Buku yang diimpor disalin ke penyimpanan pribadi aplikasi, dan impor file dengan isi identik dikenali agar tidak menjadi duplikat. Tautan luar serta akses jaringan dari pembaca diblokir.

Izin notifikasi digunakan untuk pengingat dan pemberitahuan Fokus. Akses penyimpanan luas digunakan oleh pemindai buku; impor manual tetap tersedia tanpa izin tersebut. Keberadaan izin pemindai ini perlu ditinjau terpisah jika aplikasi akan didistribusikan lewat Google Play.

Daftar pustaka pihak ketiga ada di [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Font Nunito Sans dibundel dengan [SIL Open Font License](app/src/main/assets/OFL-NunitoSans.txt).

Panduan mengunggah source project ini ke GitHub ada di [docs/PUBLISHING.md](docs/PUBLISHING.md).
