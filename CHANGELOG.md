# Catatan perubahan Spotibook

## 1.6.0 — 2 November 2026

- Tampilan Coral dengan kartu bacaan coral, tombol lanjut biru dan permukaan peach/putih. Tema gelap memakai charcoal dan coral lembut. Pilih Terang, Gelap atau Ikuti sistem melalui Profil.
- Tombol **Dengarkan buku** di pembaca: TTS Indonesia offline, jeda/lanjut/berhenti, bagian sebelumnya/berikutnya, kecepatan 0,75–2× dan penyimpanan posisi dengar.
- Layanan media, notifikasi dan MediaSession untuk pemutaran di luar pembaca serta kontrol layar terkunci. Pemutaran dirancang tetap berjalan saat layar mati.
- Pengambilan teks PDF menggunakan PDFBox-Android, termasuk pada Android sebelum versi 15. EPUB/TXT/HTML mengambil teks bab lokal.
- Mesin suara memilih suara Indonesia yang terpasang dan tidak memerlukan jaringan. Paket suara yang belum tersedia diarahkan ke pengaturan TTS HP. Tidak menambahkan izin INTERNET.
- Perbaikan ketahanan pengujian perpindahan dari zoom ke scrolling; panel audio juga diuji di pembaca landscape.

### Pembaruan Fokus dan PDF yang ikut disertakan

- Banner Fokus selesai yang turun dari atas, serta bunyi yang tidak bergantung pada izin notifikasi. Nada bawaan, MP3 pilihan dan Senyap tetap tersedia.
- Scrolling samping menyambung. Saat zoom, semua mode sementara memakai gulir samping; kembali ke 100% memulihkan mode pilihan. Scroll lama dialihkan ke Scrolling vertikal.

### Validasi dan batasan

68 tes host lulus. Build debug, paket tes Android, release unsigned dan lint berhasil; lint 0 error / 51 warning. APK percobaan ditandatangani dengan kunci debug yang sama, versionCode 7; skema database tetap 2.

Belum ada pengujian HP/emulator terhubung untuk suara speaker, kualitas suara, kontrol layar terkunci dan pemutaran saat layar mati/Doze. Kualitas TTS mengikuti suara perangkat, bukan model neural khusus Spotibook. PDF scan/gambar memerlukan OCR dan belum didukung. Teks yang dibaca tampil di panel audio; sorotan koordinat paragraf PDF belum tersedia. Statistik membaca aktif tidak ditambah oleh audio latar belakang.

Lihat [VALIDATION.md](VALIDATION.md) untuk rincian pemeriksaan.
