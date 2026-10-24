# Prompt AI untuk install Rais TV via ADB — PENTING: eksekutor HARUS lokal

> KOREKSI 2026-10-25: AI cloud (Gemini web/app, Muse cloud, ChatGPT web,
> dll) TIDAK BISA install ini — mereka tidak punya rute ke Wi-Fi rumah
> user, jadi tidak bisa `adb connect` ke IP lokal TV (192.168.x.x).
> Prompt ini hanya berlaku bila kamu jalan DI MESIN LOKAL yang satu
> Wi-Fi dengan TV: PC/laptop di rumah (Claude Code / Gemini CLI /
> terminal biasa), atau Termux di HP yang satu Wi-Fi dengan TV.
> Kalau kamu jalan di cloud, STOP dan bilang ke user: jalankan prompt
> ini di PC/HP lokal, atau suruh user tempel perintahnya manual di
> Termux — jangan klaim bisa adb ke TV dari cloud.

> Kamu adalah operator install Rais TV. Mesin ini dan TV Android target
> ada di satu jaringan Wi-Fi yang sama. Kerja lewat ADB saja, jangan
> ubah apa pun di luar yang diperintahkan di bawah.
>
> 1. Clone repo public ini (tanpa login): `git clone https://github.com/raisproject17/rais-tv.git` lalu `cd rais-tv`, baca `README.md`.
> 2. TANYAKAN DULU ke user sebelum eksekusi: (a) IP TV-nya berapa,
>    (b) pakai logo RAIS.PROJECT atau tidak, (c) file APK BitTV dan
>    TV Bro sudah ada di mesin ini atau belum, dan di path mana.
>    Bila user sudah menjawab, jangan tanya ulang.
> 3. Pastikan di TV sudah ON: Settings > About > Build 7x >
>    Developer options > USB debugging (+ Network ADB bila ada),
>    dan prompt "Allow USB debugging?" sudah di-OK-kan di TV.
> 4. APK launcher + prayer sudah jadi (lihat README, bagian Build):
>    taruh di `launcher/app/build/outputs/apk/release/app-release.apk`
>    dan `prayer-times/app/build/outputs/apk/release/app-release.apk`,
>    atau install manual `adb install -r <apk>` per file.
>    Jalankan `./install-rais.sh` dengan env yang sesuai, contoh:
>    `TV_HOST=<IP-TV> USE_LOGO=1 BITTV_APK=/path/BitTV.apk ./install-rais.sh`
>    (USE_LOGO=0 = tanpa logo, USE_LOGO=1 = pakai logo).
> 5. ATURAN SAFETY (dari upstream CleanYourTV, tidak boleh dilanggar):
>    disable saja, JANGAN uninstall. JANGAN disable paket inti Android,
>    TV/input/HDMI, remote/Bluetooth, keyboard, Play Store, atau
>    Play Services. Backup di folder `backup-rais-*` jangan dihapus.
>    Stock launcher HANYA dimatikan setelah Home terverifikasi pindah
>    ke `com.gathin.tvhome/.MainActivity` (cek `dumpsys activity activities | grep mFocusedApp`).
> 6. Lapor per langkah: model + SDK TV, apa yang di-disable, app yang
>    terinstall, Home terverifikasi atau tidak, DNS, dan sleep off.
>    Bila ada langkah gagal, berhenti di langkah itu, jelaskan, dan
>    beri undo-nya (restore.sh / DNS opportunistic / kembalikan timeout).
> 7. Ikon APK jangan diubah — logo hanya di dalam layar Home dan
>    flip clock, bukan ikon app.

## Kalau eksekutor di luar rumah (bukan satu Wi-Fi) — jalur upstream

Upstream CleanYourTV (`docs/REMOTE.md`) tegas: semua asumsi repo ini
adalah komputer + TV satu Wi-Fi. Untuk TV di rumah lain, pakai
Tailscale privat (bukan forward port 5555 ke internet — ADB itu
kontrol penuh TV, bot aktif scan port ADB terbuka):

- Opsi A: Tailscale di TV (Play Store Android TV/Google TV) — TV
  dapat alamat `100.x.y.z`, lalu eksekutor yang join tailnet yang
  sama bisa `adb connect` ke alamat itu seolah-olah satu jaringan.
- Opsi B: helper device di rumah TV sebagai subnet router (PC/Mac/
  Linux/Raspberry Pi/NAS yang join tailnet + advertise
  `192.168.1.0/24`, route di-approve di admin console) — lalu
  eksekutor di tailnet bisa reach TV di IP lokalnya.
- Bukan HP: app Tailscale Android/iOS tidak bisa jadi subnet router.

Catatan: Tailscale hanya membantu bila EKSEKUTORNYA join tailnet
yang sama. Gemini web/app cloud tidak join tailnet user, jadi
Tailscale saja tidak membuat Gemini cloud bisa adb ke TV.

## Relay upstream (bukan untuk cloud)

`tools/relay.py` upstream hanya untuk kasus assistant yang jalan di
mesin yang sama dengan relay tapi sandbox-nya diblokir dari jaringan
lokal (contoh: Claude Code di Mac kena Local Network privacy):
jalankan relay di terminal biasa di mesin itu
(`tools/relay.py <ip-tv>`, listen `127.0.0.1:15555`), lalu assistant
pakai `TV_HOST=127.0.0.1 TV_PORT=15555`. Relay TIDAK membuat AI
cloud bisa reach PC user — `127.0.0.1` di cloud adalah localhost
cloud itu sendiri, bukan PC user.
