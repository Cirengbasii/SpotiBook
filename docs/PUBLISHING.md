# Upload Spotibook ke GitHub

README menjelaskan aplikasinya. Panduan ini buat menaruh source project di GitHub dan memperbaruinya nanti.

## Nama dan deskripsi repo

Nama repo: `spotibook`

Description / About:

> Aplikasi baca ebook offline untuk Android, dengan rak buku, timer fokus, dan catatan kebiasaan membaca. Dibuat dengan Kotlin dan Jetpack Compose.

Topics yang sesuai: `android`, `kotlin`, `jetpack-compose`, `ebook-reader`, `pdf-reader`, `epub-reader`, `offline`, `reading-tracker`.

## 1. Buat repo kosong

Buka [github.com/new](https://github.com/new), isi nama dan deskripsi, lalu pilih **Public** kalau ingin source bisa dilihat orang lain, atau **Private** untuk membatasi akses.

Biarkan pilihan pembuatan README, `.gitignore`, dan license kosong karena file project akan dikirim dari komputer. Klik **Create repository**, lalu salin URL HTTPS repo.

README dan `.gitignore` sudah ada di project. Lisensi untuk kode project belum ditentukan; pilih terpisah kalau ingin memberikan izin penggunaan ulang. Lisensi dependency tetap tercatat di `THIRD_PARTY_NOTICES.md` dan berkas lisensi font.

## 2. Buka PowerShell di folder project

Di File Explorer, buka folder `app_spotibook` yang berisi `gradlew.bat` dan `README.md`. Klik kolom alamat, ketik `powershell`, lalu Enter.

Git sudah tersedia di komputer ini dan folder project sudah punya Git lokal. Tidak perlu menjalankan `git init` lagi.

Cek daftar file:

```powershell
git status --short
```

Source di `app/`, `gradle/`, Gradle Wrapper, serta dokumentasi perlu ikut. `.gradle/`, `.kotlin/`, `app/build/`, `local.properties`, keystore, dan file `.env` dikecualikan oleh `.gitignore`.

## 3. Simpan versi pertama

Perintah ini menyiapkan file untuk dicatat lalu memperlihatkan daftarnya:

```powershell
git add .
git diff --cached --stat
git diff --cached --name-only
```

Cek daftar sebelum lanjut. Jangan sertakan kunci penandatanganan, password, file buku pribadi, atau cadangan data pengguna. `.gitignore` tidak mengecualikan file buku/backup yang ditaruh sembarangan di folder source.

Kalau ada file yang terlanjur dipilih, keluarkan dari daftar commit pertama tanpa menghapus file aslinya:

```powershell
git rm --cached -- 'path/ke/file'
```

Ganti path tersebut dengan file yang dimaksud, lalu tambahkan path-nya ke `.gitignore` jika harus selalu dikecualikan.

Jika daftar sudah sesuai:

```powershell
git commit -m "Add Spotibook Android app"
```

Kalau Git menampilkan **Author identity unknown**, atur nama dan email untuk repo ini, lalu ulangi commit. Ganti contoh berikut dengan identitas yang ingin ditampilkan pada commit:

```powershell
git config user.name "Nama kamu"
git config user.email "EMAIL_COMMIT_KAMU"
```

Untuk menjaga email pribadi, pakai alamat noreply dari [GitHub Settings → Emails](https://github.com/settings/emails). Penjelasan alamat commit tersedia di [dokumentasi email GitHub](https://docs.github.com/en/account-and-profile/concepts/email-addresses).

## 4. Hubungkan dan upload

Ganti `USERNAME` pada URL di bawah dengan akun GitHub kamu. Kalau nama repo berbeda, sesuaikan juga `spotibook`. Jalankan setelah commit berhasil:

```powershell
git branch -M main
git remote add origin https://github.com/USERNAME/spotibook.git
git remote -v
git push -u origin main
```

Kalau diminta login, selesaikan autentikasi GitHub. Jika memakai Git Credential Manager di Windows, ikuti jendela login browser yang muncul; lihat [panduan Git Credential Manager](https://docs.github.com/en/get-started/git-basics/caching-your-github-credentials-in-git?platform=windows). Password akun GitHub tidak digunakan sebagai password Git lewat HTTPS; lihat [metode autentikasi GitHub](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/about-authentication-to-github?platform=windows).

Setelah push berhasil, buka kembali halaman repo. README akan tampil di halaman utama. Langkah repo kosong dan push ini mengikuti [panduan GitHub untuk project lokal](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github).

## Kalau ada error

- **`remote origin already exists`**: jalankan `git remote -v`. Jika URL salah, perbarui dengan `git remote set-url origin https://github.com/USERNAME/spotibook.git` setelah mengganti contohnya.
- **`Repository not found`**: cek nama akun, nama repo, dan akun yang dipakai login.
- **`src refspec main does not match any`**: pastikan commit pertama berhasil dan nama branch sudah `main`.
- **Push ditolak karena repo sudah berisi commit**: jangan pakai force push. Panduan ini mengasumsikan repo GitHub kosong; periksa dulu isi repo supaya tidak menimpa pekerjaan yang sudah ada.

## Update berikutnya

Sesudah mengubah kode atau dokumentasi, jalankan dari folder project:

```powershell
git status --short
git add .
git diff --cached --stat
git diff --cached
git commit -m "Jelaskan perubahan yang dibuat"
git push
```

Periksa perubahan sebelum commit. Ganti pesan contoh dengan perubahan sebenarnya, misalnya `Perbaiki posisi baca saat rotasi`.

## Membagikan APK

Folder build dan file APK dikecualikan dari source. Untuk membagikan aplikasi siap pasang, unggah APK yang sudah ditandatangani sebagai lampiran GitHub Release. APK debug bisa dipakai mencoba aplikasi, sedangkan `app-release-unsigned.apk` perlu ditandatangani lebih dulu. Jangan unggah keystore bersama APK.

Draft **Spotibook 1.4.0 — Preview** sudah disiapkan, tetapi unggahan APK belum berhasil. Buka [halaman Releases](https://github.com/Cirengbasii/SpotiBook/releases) sambil login sebagai pemilik repo, edit draft tersebut, lampirkan APK, lalu publikasikan. File siap pasang yang tersedia di komputer adalah `app/build/outputs/apk/debug/app-debug.apk`; boleh disalin dan diberi nama `Spotibook-1.4.0-preview.apk` sebelum dilampirkan. Hapus kalimat status menunggu unggahan di README setelah rilis tersedia.

Untuk versi berikutnya:

1. Build dan periksa APK yang akan dibagikan. Gunakan kunci penandatanganan yang sama agar pengguna bisa memperbarui tanpa menghapus aplikasi.
2. Push kode yang sesuai ke GitHub. Buka repo → **Releases** → **Draft a new release**.
3. Buat tag baru sesuai versi, pilih commit atau branch yang sesuai, lalu isi judul dan catatan perubahan.
4. Lampirkan file `.apk`. Centang **This is a pre-release** untuk versi percobaan. Siapkan lampiran sebelum menekan **Publish release**.
5. Perbarui link download di README ke tag dan nama aset yang baru, lalu commit dan push README.

Langkah pembuatan rilis mengikuti [panduan GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).
