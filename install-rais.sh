#!/usr/bin/env bash
# Rais TV — script setup generik Android 10+ (SDK 29+), semua merk.
# BERDASARKAN upstream CleanYourTV (PROMPT.md / AGENTS.md), BUKAN pengganti
# aturan safety-nya: disable, bukan uninstall; backup dulu; stock launcher
# HANYA dimatikan setelah launcher Rais terverifikasi jadi Home.
#
# Yang dibutuhkan di PC (satu Wi-Fi dengan TV):
#   - adb di PATH, TV sudah: Settings > About > Build 7x > Developer options
#     > USB debugging (+ Network ADB bila ada) ON, dan prompt
#     "Allow USB debugging?" sudah di-OK-kan di TV.
#   - APK launcher + prayer SUDAH di-build (lihat README.md di folder ini):
#       launcher/app/build/outputs/apk/release/app-release.apk
#       prayer-times/app/build/outputs/apk/release/app-release.apk
#   - APK BitTV: JANGAN di-download script ini dari Drive/mirror.
#     User download sendiri dari Drive-nya ke file lokal, lalu:
#       BITTV_APK=/path/ke/BitTV.apk ./install-rais.sh
#     (script ini TIDAK memuat / menyimpan ulang APK itu; APK tidak
#     dibundel di repo ini.)
#   - APK TV Bro (opsional): bila tidak diisi dan TV Bro belum terpasang,
#     script hanya memberi peringatan — install dari Play Store di TV.
#       TVBRO_APK=/path/ke/tvbro.apk ./install-rais.sh
#
# Variabel lain:
#   TV_HOST=192.168.1.50   (wajib)
#   ROUTER_FILTER=1        (bila router sudah Pi-hole/AdGuard: LEWATI Private DNS,
#                           karena Private DNS akan bypass aturan router)
#   SKIP_DNS=1             (lewati Private DNS apa pun alasannya)
#   STOCK_LAUNCHER=com.xxx.launcher  (bila sudah tahu paket launcher bawaan;
#                           tanpa ini script MENANYAKAN dulu sebelum mematikannya)
#   NO_REBOOT=1            (jangan reboot di akhir; verifikasi manual)
#   USE_LOGO=0/1           (pakai logo RAIS.PROJECT di Home + flip clock?
#                           kosong = DITANYAKAN saat jalan (interaktif);
#                           non-interaktif / AI (Gemini di HP) = default 1.
#                           0 = logo disembunyikan TANPA rebuild APK — hanya
#                           theme show_brand_logo=false yang di-push.)
#
# Undo debloat:  TV_HOST=<ip> tools/restore.sh debloat-generic.txt
# Undo DNS:      adb shell settings put global private_dns_mode opportunistic
# Undo sleep:    TV Settings > Sleep / Screensaver > kembalikan timeout TV.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST, contoh: TV_HOST=192.168.1.50 $0}"
command -v adb >/dev/null || { echo "adb tidak ditemukan di PATH"; exit 1; }

LAUNCHER_APK="launcher/app/build/outputs/apk/release/app-release.apk"
PRAYER_APK="prayer-times/app/build/outputs/apk/release/app-release.apk"
[ -f "$LAUNCHER_APK" ] || { echo "BELUM DI-BUILD: $LAUNCHER_APK tidak ada. Baca README.md (JDK 17 + Android SDK 34, ./gradlew assembleRelease di launcher/)."; exit 1; }
[ -f "$PRAYER_APK" ] || { echo "BELUM DI-BUILD: $PRAYER_APK tidak ada. Baca README.md (./gradlew assembleRelease di prayer-times/)."; exit 1; }

adb connect "$TV_HOST:5555" || true
echo "== 1. Info TV (Android 10+ wajib) =="
SDK=$(adb -s "$TV_HOST:5555" shell getprop ro.build.version.sdk | tr -d '\r')
MODEL=$(adb -s "$TV_HOST:5555" shell getprop ro.product.model | tr -d '\r')
echo "model=$MODEL sdk=$SDK"
if [ "${SDK:-0}" -lt 29 ]; then echo "STOP: SDK $SDK di bawah Android 10 (29). Launcher fork ini untuk Android 10+."; exit 1; fi

echo "== 2. Backup (jangan dihapus — ini jalan pulang) =="
BACKUP="backup-rais-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP"
adb -s "$TV_HOST:5555" shell pm list packages > "$BACKUP/packages.txt"
adb -s "$TV_HOST:5555" shell pm list packages -d > "$BACKUP/packages-disabled.txt" || true
adb -s "$TV_HOST:5555" shell getprop > "$BACKUP/getprop.txt" || true
adb -s "$TV_HOST:5555" shell settings get system screen_off_timeout > "$BACKUP/screen_off_timeout.txt" || true
adb -s "$TV_HOST:5555" shell settings get global private_dns_mode > "$BACKUP/private_dns_mode.txt" || true
echo "backup di $BACKUP"

echo "== 3. Debloat generik (disable, bukan uninstall) =="
TV_HOST="$TV_HOST" tools/disable.sh debloat-generic.txt || echo "PERINGATAN: disable.sh berhenti — cek output di atas, jangan lanjut ke langkah launcher bila ini gagal total."

echo "== 4. Install launcher Rais + Prayer Times fork =="
adb -s "$TV_HOST:5555" install -r "$LAUNCHER_APK"
adb -s "$TV_HOST:5555" install -r "$PRAYER_APK"

echo "== 5. Install aplikasi (dari file user / yang sudah terpasang) =="
if [ -n "${BITTV_APK:-}" ]; then
  [ -f "$BITTV_APK" ] || { echo "BITTV_APK tidak ditemukan: $BITTV_APK"; exit 1; }
  adb -s "$TV_HOST:5555" install -r "$BITTV_APK" || echo "PERINGATAN: install BitTV gagal — cek APK (arsitektur / versi Android TV) lalu install manual."
else
  echo "BITTV_APK tidak diisi — lewati install BitTV (bila sudah terpasang, dock tetap dicari di langkah 7)."
fi
if [ -n "${TVBRO_APK:-}" ]; then
  adb -s "$TV_HOST:5555" install -r "$TVBRO_APK" || echo "PERINGATAN: install TV Bro gagal."
elif ! adb -s "$TV_HOST:5555" shell pm list packages | grep -q com.phlox.tvwebbrowser; then
  echo "PERINGATAN: TV Bro belum terpasang dan TVBRO_APK tidak diisi — install TV Bro dari Play Store di TV (com.phlox.tvwebbrowser), lalu push theme lagi."
fi

echo "== 6. Jadikan launcher Rais sebagai Home — VERIFIKASI DULU, baru matikan stock =="
adb -s "$TV_HOST:5555" shell cmd package set-home-activity com.gathin.tvhome/.MainActivity || true
sleep 2
FOCUSED=$(adb -s "$TV_HOST:5555" shell dumpsys activity activities | grep -m1 mFocusedApp || true)
echo "focused: $FOCUSED"
case "$FOCUSED" in
  *com.gathin.tvhome*) echo "OK: launcher Rais sudah jadi Home." ;;
  *) echo "STOP: Home belum pindah ke launcher Rais. JANGAN matikan launcher bawaan. Cek di TV (tekan Home), lalu jalankan ulang langkah 6."; exit 1 ;;
esac
echo "Aplikasi yang mengklaim HOME:"
adb -s "$TV_HOST:5555" shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME || true
if [ -n "${STOCK_LAUNCHER:-}" ]; then
  echo "Mematikan stock launcher: $STOCK_LAUNCHER"
  adb -s "$TV_HOST:5555" shell pm disable-user --user 0 "$STOCK_LAUNCHER"
else
  echo "STOCK_LAUNCHER tidak diisi — stock launcher TIDAK dimatikan di run ini (aman)."
  echo "Bila mau dimatikan: STOCK_LAUNCHER=<paket stock> $0  (ulangi run; langkah 1-5 idempotent)"
  echo "Khusus Google TV: cek dulu setupwraith tidak memegang Wi-Fi tersimpan"
  echo "  adb shell dumpsys wifi | grep -o 'cname=[^ ]*'  — bila ada, forget + re-join Wi-Fi dulu."
fi

echo "== 7. Logo (select saat proses): pakai logo RAIS.PROJECT? =="
# Item 1 user 2026-10-25: ditanyakan saat proses, bukan diputuskan diam-diam.
# AI (mis. Gemini di HP, satu Wi-Fi dengan TV) cukup set USE_LOGO=0/1 di env.
if [ -z "${USE_LOGO:-}" ]; then
  if [ -t 0 ]; then
    printf "Pakai logo RAIS.PROJECT di Home + flip clock? [Y/n] "
    read -r _logo_ans || true
    case "${_logo_ans}" in n|N|no|NO|0) USE_LOGO=0 ;; *) USE_LOGO=1 ;; esac
  else
    USE_LOGO=1
    echo "USE_LOGO tidak diisi + non-interaktif -> default 1 (pakai logo). Set USE_LOGO=0 untuk tanpa logo."
  fi
fi
echo "USE_LOGO=$USE_LOGO"
echo "== 8. Theme Rais: dock dari paket yang BENAR-BENAR terpasang + logo =="
BITTV_PKG=$(adb -s "$TV_HOST:5555" shell pm list packages | grep -i -E "bit|fztoca" | head -n1 | sed 's/package://' | tr -d '\r' || true)
if [ -n "$BITTV_PKG" ]; then echo "BitTV terpasang sebagai: $BITTV_PKG."; else echo "BitTV tidak ditemukan di pm list - dock memakai placeholder com.bitvi.fztoca."; fi
BITTV_PKG="$BITTV_PKG" USE_LOGO="$USE_LOGO" python3 - << 'PY'
import json, os
p = "themes/rais/config.json"
cfg = json.load(open(p))
bit = os.environ.get("BITTV_PKG") or "com.bitvi.fztoca"
cfg["dock_apps"] = ["com.netflix.ninja", "com.phlox.tvwebbrowser", "com.majnioui.tv.prayer", bit]
cfg["show_brand_logo"] = (os.environ.get("USE_LOGO", "1") != "0")
json.dump(cfg, open(p, "w"), indent=2)
print("theme show_brand_logo:", cfg["show_brand_logo"])
print("theme dock:", cfg["dock_apps"])
PY
echo "Push theme (butuh tools/venv upstream — lihat README bila venv belum ada):"
TV_HOST="$TV_HOST" tools/theme.sh themes/rais || echo "PERINGATAN: theme push gagal — setting di config.xml fork (idle 2 menit, dock) tetap aktif; theme hanya override."

echo "== 9. System: Private DNS anti-iklan (opsional, bisa di-skip) =="
if [ "${ROUTER_FILTER:-0}" = "1" ]; then
  echo "ROUTER_FILTER=1 — Private DNS DILEWATI (router sudah filter; Private DNS akan bypass-nya)."
elif [ "${SKIP_DNS:-0}" = "1" ]; then
  echo "SKIP_DNS=1 — Private DNS dilewati."
else
  adb -s "$TV_HOST:5555" shell settings put global private_dns_specifier dns.adguard-dns.com || true
  adb -s "$TV_HOST:5555" shell settings put global private_dns_mode hostname || true
  echo "Private DNS = AdGuard. Batasan upstream: TIDAK memblokir iklan YouTube."
  echo "Undo: adb shell settings put global private_dns_mode opportunistic"
fi

echo "== 10. System: sleep OFF + screensaver Rais =="
# screen_off_timeout never (2147483647) = TV tidak tidur sendiri; screensaver
# flip clock dari Home (2 menit idle) + ClockActivity FLAG_KEEP_SCREEN_ON
# yang menjaga layar tetap jalan selama screensaver aktif.
adb -s "$TV_HOST:5555" shell settings put system screen_off_timeout 2147483647 || true
adb -s "$TV_HOST:5555" shell settings put secure screensaver_components com.gathin.tvhome/.ClockDream || true
adb -s "$TV_HOST:5555" shell settings put secure screensaver_enabled 1 || true
echo "Catatan 'notif running': ClockActivity fullscreen immersive — shade/status"
echo "notifikasi tidak tampil DI ATAS screensaver. Notifikasi tidak dihapus;"
echo "begitu tombol ditekan dan kembali ke Home, notifikasi tampil normal."
echo "Bila TV tetap membangunkan screensaver karena notifikasi tertentu,"
echo "aktifkan Do Not Disturb manual di TV Settings (opsional, per TV)."

if [ "${NO_REBOOT:-0}" != "1" ]; then
  echo "== 10. Reboot + verifikasi =="
  adb -s "$TV_HOST:5555" reboot || true
  echo "Tunggu TV nyala, terima prompt ADB bila muncul, lalu cek:"
  echo "  adb shell pm list packages -d   (harus berisi daftar debloat-generic)"
  echo "  Tekan Home -> launcher Rais, diam 2 menit di Home -> flip clock + logo di bawah jam"
  echo "  Buka Prayer Times -> pilih Provinsi -> Kota (pertama kali), cek jadwal"
  echo "  Netflix / TV Bro / BitTV ada di dock (bila terpasang)"
else
  echo "NO_REBOOT=1 — reboot dilewati, verifikasi manual seperti di atas."
fi
echo "SELESAI (run). Bila ada yang salah: restore debloat + kembalikan Home ke stock launcher, lalu reboot."
