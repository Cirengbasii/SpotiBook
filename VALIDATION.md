# Validasi Spotibook 1.6.0

2 November 2026, Asia/Jakarta. VersionCode 7, package com.spotibook.app. Room tetap skema 2; migrasi 1 ke 2 yang sudah ada tetap dipakai.

## Hasil

BUILD SUCCESSFUL untuk testDebugUnitTest, assembleDebug, assembleDebugAndroidTest, assembleRelease dan lintDebug. **68 tes lulus; 0 gagal; 0 error; 0 dilewati.** Lint: 0 error, 51 warning. Release vital lint lulus. Log lengkap: app/build/final-validation.log.

| Suite | Tes | Gagal |
| --- | ---: | ---: |
| CalendarTest | 2 | 0 |
| DeviceBooksTest | 2 | 0 |
| FocusIntegrationTest | 4 | 0 |
| HomeUiTest | 6 | 0 |
| LaunchTest | 2 | 0 |
| LibraryIntegrationTest | 2 | 0 |
| LibraryUiTest | 4 | 0 |
| PdfModesUiTest | 8 | 0 |
| PdfZoomTest | 4 | 0 |
| ReaderPreferencesTest | 4 | 0 |
| ReaderRotationUiTest | 1 | 0 |
| ReaderUiTest | 4 | 0 |
| ReadingTest | 18 | 0 |
| RetentionMigrationTest | 2 | 0 |
| WidgetTest | 1 | 0 |

## Cakupan revisi

- Tema Coral diterapkan melalui sistem warna bersama: kartu bacaan coral, tombol lanjut biru, permukaan putih/peach dan pasangan charcoal/coral untuk gelap. Render beranda-coral-terang.png dan beranda-coral-gelap.png diperiksa. Pilihan tema gelap diuji melalui UI dan tersimpan ke preferensi. Navigasi HP dan rail tablet tetap lulus. Sampul pada render adalah fixture, bukan buku yang ditambahkan ke aplikasi.
- SpeechTextTest (2 tes) memeriksa pembagian teks panjang tanpa kehilangan kata serta halaman kosong. SpeechIntegrationTest (2 tes) benar-benar membuat/membaca PDF lewat PDFBox dan mengambil HTML bab EPUB dari direktori impor; script/style tidak dibacakan. Kedua suite ini menambah 4 tes pada tabel di atas.
- Pengujian layanan TTS menyediakan suara Indonesia offline dan online berperingkat lebih tinggi: layanan memilih offline, mengirim teks ke Android TTS, menerima jeda/stop dan menutup mesin. Batas mesin suara diwakili ShadowTextToSpeech; tes tidak menghasilkan suara speaker nyata.
- Pembaca lanskap diuji membuka/menutup panel Dengarkan buku. Layanan media, notifikasi MediaStyle/MediaSession, audio focus, wake lock terbatas dan penyimpanan posisi terpisah menangani pemutaran di luar pembaca. Teks yang sedang dibaca ditampilkan dalam panel; sorotan koordinat paragraf PDF belum ditambahkan. Suara neural khusus dan OCR tidak disertakan.

- Nada bawaan benar-benar dipanggil melalui ShadowToneGenerator saat POST_NOTIFICATIONS ditolak. Pilihan Silent tidak menambah bunyi. Ini membuktikan pemanggilan API, bukan suara fisik speaker HP. Suara memakai stream alarm; izin notifikasi hanya mengatur notifikasi Android. Mode senyap/DND sistem tetap dihormati.
- Penyampaian bunyi dimiliki scope aplikasi. Receiver dan worker menunggu penyampaian terbatas selesai sebelum melepaskan masa aktifnya; menutup layar/panel Fokus tidak membatalkan suara. Foreground deadline observer tetap aktif di akar aplikasi, termasuk saat membaca.
- Pengujian UI menyelesaikan timer tanpa membuka Fokus, memeriksa banner terlihat dan menutupnya. Pratinjau app/build/qa/fokus-selesai.png telah diperiksa. Banner bergerak dari atas, memakai warna tema dan pengumuman aksesibilitas.
- Enam pilihan yang tersedia diuji: Withdrawal, Overlay, Shift, No Animation, Scrolling vertikal, Scrolling samping. Scroll lama disembunyikan dan dinormalisasi ke Scrolling; pengaturan lama dan cadangan tetap dapat dibaca tanpa membuang preferensi lain.
- Pengujian gestur meliputi cubit, pan, ruang kosong di tepi, ukuran layar sempit/lanskap, pasangan dua halaman, perubahan ukuran viewport dan pemulihan posisi. Dalam zoom, gulir samping mencapai halaman lain. Double tap mengembalikan 100% dan mode pilihan (termasuk gulir vertikal), tanpa mengubah preferensi tersimpan.
- Renderer PDF framework diganti fixture hanya pada batas framework untuk tes host Windows. Compose, pengendali gestur, navigasi, database dan pengaturan memakai kode produksi. Paket tes Android berhasil dikompilasi.

## APK

APK pemasangan: app/build/outputs/apk/debug/app-debug.apk (85.247.873 byte). Verifikasi apksigner v2 valid dengan satu penanda tangan; sertifikat SHA-256 tetap 9bf12be1919a4af8d264c7f01034bb91a9a1ac91fc8e38dc4eb6397f5237ee1a. aapt memastikan versi 1.6.0/code 7 dan label Spotibook. Release APK masih unsigned. Ukuran serta SHA-256 masing-masing artefak ada di app/build/verification.json.

## Batas pemeriksaan

adb tidak menemukan perangkat. Kualitas suara Indonesia, audio speaker/headset, kontrol layar terkunci dan pemutaran saat layar mati/Doze belum diuji langsung pada HP. Izin normal FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK dan WAKE_LOCK ditambahkan untuk audio latar belakang; INTERNET tetap tidak diizinkan. Paket suara Indonesia offline harus tersedia di mesin TTS HP. PDF scan/gambar memerlukan OCR dan dilaporkan belum didukung. Pemutaran audio tidak menambah statistik membaca aktif ketika pembaca tidak aktif.

Tes perangkat dapat dijalankan dengan .\gradlew.bat :app:connectedDebugAndroidTest setelah perangkat tersedia. Laporan host dan lint ada di app/build/reports/.
