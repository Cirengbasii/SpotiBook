# Validasi Spotibook 1.4.0

Diperiksa pada 1 November 2026 (Asia/Jakarta). VersionCode 5, package `com.spotibook.app`, label Spotibook. Skema Room versi 2 memakai migrasi eksplisit 1 → 2, tanpa migrasi destruktif. Kunci penandatanganan debug sama dengan versi sebelumnya.

## Build dan hasil uji

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug --console=plain
```

**BUILD SUCCESSFUL. 59 tes host lulus, tanpa kegagalan atau tes dilewati.** Android Lint: **0 error, 46 warning**. Release vital lint lulus. Tidak ada baseline untuk menyembunyikan error. Log build: `app/build/final-validation.log`.

| Suite | Tes | Gagal |
| --- | ---: | ---: |
| CalendarTest | 2 | 0 |
| LibraryIntegrationTest | 2 | 0 |
| DeviceBooksTest | 2 | 0 |
| ReadingTest | 18 | 0 |
| FocusIntegrationTest | 3 | 0 |
| HomeUiTest | 4 | 0 |
| LaunchTest | 2 | 0 |
| LibraryUiTest | 4 | 0 |
| PdfModesUiTest | 7 | 0 |
| PdfZoomTest | 4 | 0 |
| ReaderPreferencesTest | 3 | 0 |
| ReaderRotationUiTest | 1 | 0 |
| ReaderUiTest | 4 | 0 |
| RetentionMigrationTest | 2 | 0 |
| WidgetTest | 1 | 0 |

## Bukti perubahan

- Alur UI bulk memilih semua lalu memasukkan ke rak tetap lulus. Pengujian baru mengeluarkan buku dari satu rak sambil mempertahankan rak lain, posisi buku dan salinan pribadi. Hapus dari pustaka menghapus buku/keanggotaan dan salinan pribadi; berkas asli pembanding tetap utuh. Tombol hapus tunggal juga tetap tersedia.
- Pengujian navigasi membuka Fokus, menjalankan timer, lalu berpindah ke Beranda, Pustaka, Statistik, Profil, Fokus dan Beranda. Timer tetap berjalan.
- Pembaca produksi diuji dalam lanskap: zoom, pemulihan saved state, pindah ke pasangan berikutnya, membuka Fokus dari toolbar, memilih 10 menit, memulai, lalu menutup panel. Timer yang sama tetap berjalan dengan ID buku yang dibaca.
- Uji integrasi menghitung 30 detik membaca tanpa Fokus dan 60 detik selama Fokus, termasuk jeda/lanjut. Segmen tetap satu kunjungan membaca logis. Timer sendiri tidak memberi waktu baca.
- Penyelesaian timer menghasilkan satu FocusRecord dan satu notifikasi pada kanal `focus_complete`; pemulihan berulang tidak menduplikasi hasil, dan ID alarm usang ditolak. Kanal tidak memakai bunyi sistem ganda karena bunyi pilihan dikelola FocusAudio. Impor audio diuji sebagai salinan byte, mempertahankan sumber, dan menolak berkas kosong tanpa mengganti audio sebelumnya. Tes salinan ini tidak membuktikan decoding atau bunyi MP3 pada HP.
- Migrasi benar-benar membuka database SQLite skema 1, mempertahankan buku/posisi/zoom/waktu baca dan menambahkan nilai default kolom historyHidden/focusId. Dari 41 tanggal, 30 tanggal terbaru terlihat; penghapusan manual menyembunyikan entri sementara seluruh total statistik tetap utuh.
- Beranda dan halaman riwayat memakai komponen sampul dan tombol hapus yang sama. Beranda diuji menghapus entri dari daftar tanpa mengurangi ledger statistik. Batas 30 hari adalah batas visibilitas riwayat, bukan penghapusan catatan statistik mentah.
- Keenam mode PDF diuji memuat dua halaman penuh berdampingan dalam viewport lanskap. Pengujian zoom menggeser tepi atas halaman ke tengah dengan ruang kosong yang nyata; posisi Canvas diukur, bukan hanya nilai zoom. Potret dan lanskap punya pembesaran terpisah, dan pasangan lanskap dipaksa muat lebar serta tinggi.
- Tes sebelumnya untuk gestur, tema sepia berwarna, preferensi pembaca, impor/backup, kalender sampul, streak, pemindai dan widget tetap lulus.

## APK dan izin

`apksigner verify --verbose --print-certs` berhasil: satu penanda tangan, tanda tangan APK v2 valid. Sidik jari sertifikat: `9bf12be1919a4af8d264c7f01034bb91a9a1ac91fc8e38dc4eb6397f5237ee1a`. `aapt dump badging` memastikan Spotibook 1.4.0, versionCode 5, minimum SDK 28, target SDK 36.

| Artefak | Byte |
| --- | ---: |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 3251150 |
| `app/build/outputs/apk/debug/app-debug.apk` | 70243062 |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 51907828 |

Debug APK ditandatangani dan dapat dipasang; release APK masih unsigned. SHA-256 setiap artefak tercatat di `app/build/verification.json`.

Revisi menambahkan SCHEDULE_EXACT_ALARM untuk akses alarm tepat waktu opsional yang dibuka dari pengaturan Fokus. Tanpa akses tersebut, penjadwalan latar belakang dapat tertunda. Izin notifikasi dibutuhkan untuk pemberitahuan selesai. Bunyi mengikuti volume pilihan, mode senyap/Jangan Ganggu, batas 8 detik, dan tidak berulang. Audio pilihan maksimal 20 MB disalin ke penyimpanan pribadi; sumber tidak diubah. Byte audio pilihan tidak termasuk backup JSON/ZIP; jika tidak ditemukan saat restore, bunyi lembut menjadi cadangan.

Tidak ada INTERNET, ACCESS_NETWORK_STATE atau FOREGROUND_SERVICE. Akses pemindai opsional MANAGE_EXTERNAL_STORAGE/READ_EXTERNAL_STORAGE berasal dari revisi sebelumnya; impor manual memakai pemilih berkas Android. Izin luas pemindaian bukan klaim kelayakan distribusi Google Play.

## Pratinjau dan batas pembuktian

`app/build/qa/pdf-dua-halaman.png` dan `app/build/qa/zoom-ruang-kosong.png` diperiksa secara visual. Gambar lain untuk rak, isi rak, kalender/streak dan widget tetap di `app/build/qa/`. Buku/sesi pada gambar adalah fixture pengujian; aplikasi tidak menambahkan buku contoh ke pustaka pengguna.

Host menggunakan grafis native Robolectric. Hanya batas renderer PDF framework diganti fixture karena dukungan PDF Android di Windows belum lengkap; Compose, pengendali halaman/zoom, gestur, pembaca dan database memakai kode produksi. Perubahan ukuran viewport memodelkan lanskap/rotasi; tidak membuktikan sensor HP atau kualitas renderer PDF pada perangkat.

`adb devices -l` tidak menemukan perangkat. Bunyi/volume MP3, ketepatan alarm saat layar mati/Doze, izin OEM, sensor rotasi, GPU, pemindaian perangkat dan launcher/widget belum diuji pada HP/emulator. Paket tes perangkat berhasil dikompilasi. Jalankan ketika perangkat tersedia:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Laporan: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`, `app/build/verification.json`. Raster PDF tetap dibatasi anggaran memori; zoom tinggi belum menggunakan renderer tile.
