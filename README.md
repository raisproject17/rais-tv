# Rais TV — fork CleanYourTV (Gathin23/cleanyourtv, MIT)

Fork ini BUKAN upstream dan TIDAK di-PR ke upstream: per AGENTS.md upstream,
perubahan sebesar ini (flip clock baru, logo, permission INTERNET di addon
sholat yang sudah ada, dan default untuk semua TV) jalurnya fork sendiri.
Repo upstream di `../cleanyourtv/` TIDAK diubah (git status bersih).

## Yang diubah vs upstream (semua jawaban user 2026-10-25)

### 1. Screensaver FLIP CLOCK (bukan thin clock lagi)
- `launcher/app/src/main/res/layout/dream_clock.xml` (+ salinan `layout-iw/`)
  + `drawable/flip_card.xml` + `drawable-nodpi/rais_logo_white.png`
- Full layar, CENTER: kartu flip HH:MM (24 jam), **logo RAIS.PROJECT
  DI BAWAH JAM**, lalu hari + tanggal + tahun Indonesia
  (contoh: `SENIN, 25 OKTOBER 2026`).
- `ClockScene.java`: isi jam/tanggal tiap menit, glow tetap bergeser,
  konten hanya micro-shift ±8dp tiap menit (terlihat center, piksel
  tidak diam total — kompromi anti burn-in; jam center tetap lebih
  berisiko burn-in di OLED dibanding drift acak upstream).
- `ClockActivity.java`: `FLAG_KEEP_SCREEN_ON` + fullscreen immersive.
  Notifikasi: shade/status tidak tampil DI ATAS screensaver; notifikasi
  tidak dihapus, kembali tampil normal begitu tombol ditekan ke Home.
  Bila ada notifikasi spesifik yang tetap membangunkan TV, aktifkan
  Do Not Disturb manual di TV Settings (per TV, tidak dipaksa script).
- Aktif setelah **2 menit idle di Home**: `home_idle_clock_minutes=2`
  di `config.xml` dan `themes/rais/config.json`. CATAT: ini hanya dari
  Home — tidak memotong nonton / HDMI / Live TV. Screensaver sistem
  (`ClockDream`, komponen yang sama) diatur script ke
  `com.gathin.tvhome/.ClockDream`.

### 2. Layar utama: logo + dock
- Logo kecil RAIS.PROJECT di atas greeting/jam Home
  (`activity_main.xml` → `brandLogo`, tidak bisa difokus, jadi tidak
  mengganggu navigasi D-pad). Sumber logo: file kiriman user
  `230361_8_64tb.png` (JPEG hitam di putih 2000×2000) → diproses jadi
  putih transparan di `assets/rais_logo_white.png` (crop ke konten,
  1564×1207, 35 KB) dan `launcher/.../drawable-nodpi/rais_logo_white.png`.
- Dock default: Netflix, TV Bro, Prayer Times, BitTV.
  `config.xml` memakai placeholder BitTV `com.bitvi.fztoca`; installer
  menimpa dock theme dengan paket BitTV yang BENAR-BENAR terpasang
  (varian sideload bisa beda paket) — bila BitTV belum terpasang,
  slot dock diisi grid pertama (`fill_dock=true`).
- `app_library=true` supaya Play Store & semua app tetap terjangkau.

### 2a. Select logo saat proses (pakai logo / tidak) — update 2026-10-25
- Saat `install-rais.sh` jalan, langkah 7 MENANYAKAN: `Pakai logo
  RAIS.PROJECT di Home + flip clock? [Y/n]`.
  Jawab `n` = logo disembunyikan di Home DAN di screensaver.
- Tanpa rebuild APK: pilihan ini ditulis ke theme sebagai
  `show_brand_logo` (true/false) di `themes/rais/config.json`, lalu
  di-push via `tools/theme.sh`. Kode launcher sudah mendukungnya:
  `config.xml` (`show_brand_logo`, default true) → `Config.java`
  (`cfg.showBrandLogo`) → `MainActivity` (sembunyikan `brandLogo`)
  dan `ClockScene` (sembunyikan `flipLogo`).
- Untuk eksekusi non-interaktif / AI di HP (lihat `AI_PROMPT.md`):
  set env sebelum jalan, script TIDAK bertanya:
  `USE_LOGO=0 ./install-rais.sh` (tanpa logo) atau
  `USE_LOGO=1 ./install-rais.sh` (pakai logo, default bila kosong
  dan non-interaktif).
- Ganti belakangan tanpa install ulang: ubah `show_brand_logo` di
  `themes/rais/config.json` lalu `TV_HOST=<ip> tools/theme.sh themes/rais`.

### 2b. Ikon APK TIDAK di-rebuild / TIDAK diganti
- Ikon aplikasi launcher tetap bawaan upstream (`@mipmap/ic_launcher`,
  file `mipmap-xhdpi/ic_launcher.jpg` identik byte-per-byte dengan
  upstream, md5 `32b8dc4f282887edfe77234a94ba0142`).
- Logo RAIS.PROJECT hanya tampil DI DALAM layar (atas Home + bawah
  jam flip clock), BUKAN sebagai ikon app di drawer/launcher.
  Jadi app di daftar aplikasi tetap kelihatan seperti launcher asli.

### 3. Jadwal sholat: pilih lokasi by provinsi → kota
- `prayer-times/app/src/main/java/com/majnioui/tv/prayer/Locations.java`
  (baru): 37 provinsi, kota-kota besar, koordinat pusat kota.
- `MainActivity.java` (fork): buka pertama TANPA lokasi tersimpan →
  layar pilih Provinsi (D-pad atas/bawah + OK) → pilih Kota → simpan
  SharedPreferences → tampil jadwal. Tombol `GANTI LOKASI` di bawah
  jadwal untuk ganti lagi. BACK di langkah kota kembali ke provinsi.
- Metode Aladhan = **20 (Kementerian Agama RI)** di `config.xml`
  (fallback kota = Serang, Banten, bila prefs kosong).
- Nama waktu Indonesia (Subuh/Terbit/Dzuhur/Ashar/Maghrib/Isya),
  tanggal Indonesia, countdown `… dalam X jam Y menit`.
- BATASAN yang jujur: ini BUKAN auto-detect GPS/IP. TV umumnya tidak
  punya GPS dan addon upstream tidak minta permission lokasi; "pilih
  lokasi" adalah jawaban user #3 (select lokasi by provinsi-kota).
  Card di launcher tetap hanya label + caption "Next prayer" (spec
  module card upstream) — jadwal lengkap tampil saat app sholat dibuka.
  Data: Aladhan, tanpa key; hanya tanggal + koordinat kota yang dikirim.

### 4. Aplikasi: pertahankan / install
- Dipertahankan (TIDAK masuk debloat): Netflix `com.netflix.ninja`,
  Play Store `com.android.vending` (+ Play Services, wajib upstream).
- Install: TV Bro `com.phlox.tvwebbrowser` (Play Store / APK resmi),
  Prayer Times fork ini, BitTV dari APK USER SENDIRI.
- BitTV: user download sendiri dari Drive-nya ke file lokal
  (script TIDAK mendownload / menyimpan ulang APK itu, dan fork ini
  TIDAK membundel APK apa pun — APK pihak ketiga bukan milik repo ini).
  Lalu `BITTV_APK=/path/ke/file.apk ./install-rais.sh`.
  Bila install gagal, cek arsitektur (arm64 vs armv7) & Android TV.

### 5. System: DNS anti-iklan + sleep off
- Private DNS AdGuard (jalur opsional upstream):
  `private_dns_mode=hostname`, `private_dns_specifier=dns.adguard-dns.com`,
  undo `private_dns_mode=opportunistic`.
  Batasan upstream: TIDAK blokir iklan YouTube. Bila router sudah
  Pi-hole/AdGuard, LEWATI (`ROUTER_FILTER=1`) karena Private DNS
  akan bypass aturan router.
- Sleep off: `screen_off_timeout=2147483647` (never) +
  `screensaver_enabled=1` + komponen ClockDream. Konsekuensi: TV
  tidak tidur sendiri; matikan manual via remote / kembalikan timeout
  di TV Settings bila tidak mau.

## Target
Merk umum, **Android 10+ (SDK 29+)**. Script menolak SDK < 29.
Debloat generik (`debloat-generic.txt`) hanya paket Google/Android
yang aman di semua merk; paket merk (PatchWall, partnercustomizer,
dsb) TIDAK dimasukkan — tambahkan manual per TV setelah cek
`dumpsys package` (lihat header file itu). JANGAN sentuh paket inti
Android, TV/input, remote/Bluetooth, keyboard, Play Store/Services.

## Eksekusi via ADB + AI di HP (Gemini, dll — satu jaringan dengan TV)
Repo ini PUBLIC supaya AI di HP bisa baca tanpa login GitHub.
Alurnya: HP + TV satu Wi-Fi, di TV sudah ON Developer options +
Network ADB, lalu AI di HP menjalankan perintah ADB ke IP TV.
Prompt siap tempel ada di `AI_PROMPT.md` — intinya:
1. Clone repo public ini (tanpa auth): `git clone https://github.com/raisproject17/rais-tv.git`
2. Tanyakan dulu ke user: (a) IP TV, (b) pakai logo atau tidak
   (`USE_LOGO=0/1`), (c) file BitTV & TV Bro sudah di HP atau belum.
3. Backup dulu (`install-rais.sh` sudah melakukannya), JANGAN
   uninstall apa pun, JANGAN disable paket inti / TV / input /
   remote / keyboard / Play Store / Play Services.
4. Jalankan `install-rais.sh`, verifikasi Home pindah ke launcher
   Rais (`dumpsys activity activities | grep mFocusedApp`), baru
   matikan stock launcher bila user setuju.
5. Lapor hasil per langkah + undo-nya (restore.sh, DNS opportunistic,
   kembalikan screen_off_timeout).

## Build (BELUM terverifikasi di VM ini — tidak ada JDK/SDK)
VM ini tidak punya `java` / Android SDK, jadi APK BELUM di-build di
sini. Yang sudah divalidasi di sini: semua XML ter-parse, JSON theme
valid, brace/paren Java seimbang, id flip/logo dirujuk layout yang benar,
dan upstream bersih. Build di PC dengan Android SDK:
```bash
# JDK 17, Android SDK platform 34, build-tools 34.0.0 (syarat upstream)
cd launcher && ./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
# -> launcher/app/build/outputs/apk/release/app-release.apk
cd ../prayer-times && ./gradlew --no-daemon assembleRelease
# -> prayer-times/app/build/outputs/apk/release/app-release.apk
```
Catatan ukuran: logo PNG 35 KB menambah APK launcher; gate 512 KB
upstream TIDAK berlaku untuk fork ini, tapi tetap jaga launcher tetap
ringan (tanpa INTERNET di launcher; INTERNET hanya di addon sholat,
seperti upstream).

## Install ke TV (satu Wi-Fi, ADB sudah ON di TV)
```bash
cd rais-tv
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt  # untuk theme.sh
TV_HOST=192.168.1.50 BITTV_APK=/path/BitTV.apk ./install-rais.sh
# TV Bro bila punya APK: TVBRO_APK=/path/tvbro.apk  (bila tidak, install dari Play Store di TV)
# Stock launcher TIDAK dimatikan otomatis: isi STOCK_LAUNCHER=<paket> di run kedua
# setelah Home terverifikasi, atau biarkan stock tetap hidup (aman).
# Router sudah filter: ROUTER_FILTER=1 ./install-rais.sh
```
Urutan script: info TV → backup → debloat generik → install launcher+
prayer → install BitTV/TV Bro (bila file diisi) → set Home + verifikasi
`dumpsys mFocusedApp` (GAGAL = STOP, stock tidak dimatikan) → theme
Rais (dock dari paket terpasang) → DNS → sleep off → reboot.
Verifikasi manual: Home = launcher Rais; diam 2 menit di Home → flip
clock + logo di bawah jam; buka Prayer Times → pilih Provinsi → Kota;
Netflix/TV Bro/BitTV di dock bila terpasang.

## Undo
- Debloat: `TV_HOST=<ip> tools/restore.sh debloat-generic.txt`
- DNS: `adb shell settings put global private_dns_mode opportunistic`
- Sleep: TV Settings → Sleep/Screensaver → kembalikan timeout.
- Home: kembalikan stock launcher sebagai Home, lalu
  `pm enable <paket stock>` bila sempat dimatikan.

## File yang diubah / baru (di fork ini saja)
- Baru: `assets/rais_logo_white*.png`, `themes/rais/config.json`,
  `debloat-generic.txt`, `install-rais.sh`, `prayer-times/.../Locations.java`,
  `launcher/.../drawable/flip_card.xml`
- Diubah: `launcher/.../layout/dream_clock.xml` (+ `layout-iw/`),
  `launcher/.../ClockScene.java`, `ClockActivity.java`,
  `launcher/.../layout/activity_main.xml`, `launcher/.../values/config.xml`,
  `prayer-times/.../MainActivity.java`, `prayer-times/.../values/config.xml`
- Disalin tanpa ubah (kecuali yang di atas): sisa launcher, prayer-times,
  `tools/{tv.py,disable.sh,restore.sh,theme.sh,adb-closed.py,check.py}`
