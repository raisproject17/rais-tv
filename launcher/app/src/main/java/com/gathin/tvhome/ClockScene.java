package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

/**
 * Rais TV fork — FLIP CLOCK screensaver (layout dream_clock): jam flip HH:MM
 * full layar CENTER, logo RAIS.PROJECT di bawah jam, lalu hari + tanggal +
 * tahun (Indonesia, 24 jam), di atas glow yang berubah warna perlahan.
 * Berbeda dari upstream: jam TIDAK lagi drift acak ke seluruh layar.
 * Sebagai ganti anti burn-in, konten hanya micro-shift beberapa dp tiap
 * menit (tetap terlihat center) + glow yang terus bergeser.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene {

    private static final long MOVE_MS = 60_000L;
    /** The glow's colour change: 16 steps over 8 s. Animated per frame it redrew the full-screen gradient ~480 times. */
    private static final int GLOW_STEPS = 16;
    private static final long GLOW_STEP_MS = 500L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final View ambient;
    private final TextView flipHour;
    private final TextView flipMinute;
    private final TextView flipDate;
    private final View flipLogo;
    private int tickCount;
    private static final Locale ID_LOCALE = new Locale("id", "ID");
    private final GradientDrawable glow;
    private int colorIndex;
    private int glowColor;
    /** clock_wallpaper or clock_glow false: something else (a picture, or plain black) replaced the coloured glow. */
    private boolean wallpaperShown;
    /** clock_wallpaper: the picture start() decodes, its darkening, and the resources to draw it with. */
    private File wallpaperFile;
    private int wallpaperShade;
    private Resources wallpaperRes;
    /** Bumped by stop(): a decode started before it finds a different number and drops its result. Main thread only. */
    private int wallpaperGeneration;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            tickCount++;
            updateFlip();
            moveClock();
            shiftGlow();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    /** root is the inflated dream_clock layout. */
    ClockScene(View root) {
        content = root.findViewById(R.id.dreamContent);
        ambient = root.findViewById(R.id.dreamAmbient);
        flipHour = root.findViewById(R.id.flipHour);
        flipMinute = root.findViewById(R.id.flipMinute);
        flipDate = root.findViewById(R.id.flipDate);
        flipLogo = root.findViewById(R.id.flipLogo);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
    }

    /** Isi jam + tanggal flip (Indonesia, 24 jam). Dipanggil di start() dan tiap tick. */
    private void updateFlip() {
        if (flipHour == null || flipMinute == null || flipDate == null) return;
        Calendar now = Calendar.getInstance();
        flipHour.setText(new SimpleDateFormat("HH", ID_LOCALE).format(now.getTime()));
        flipMinute.setText(new SimpleDateFormat("mm", ID_LOCALE).format(now.getTime()));
        // Contoh hasil: "Senin, 25 Oktober 2026" (TextView di layout memakai textAllCaps).
        flipDate.setText(new SimpleDateFormat("EEEE, d MMMM yyyy", ID_LOCALE).format(now.getTime()));
    }

    void start() {
        updateFlip();
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
        if (wallpaperFile != null && !wallpaperShown) loadWallpaper();
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);   // the tick and any glow steps still queued
        content.animate().cancel();
        wallpaperGeneration++;   // a decode still running must not attach its picture to a stopped scene
    }

    /**
     * clock_wallpaper: put Home's wallpaper (the theme's single picture, or the first of its collection) behind
     * the clock, darkened by clock_wallpaper_dim, in place of the glow. Decoded off the main thread at screen size
     * in RGB_565 (4 MB at 1080p), like Home's own; until then, or if it cannot be read, the glow stays.
     * Call before start(), which starts the decode (again after a stop() that dropped an unfinished one).
     */
    void useHomeWallpaper(Context ctx) {
        Config cfg;
        try {
            cfg = Config.load(ctx);
        } catch (RuntimeException e) {
            return;
        }
        // Rais TV fork: logo screensaver mengikuti show_brand_logo (theme/installer).
        if (flipLogo != null) flipLogo.setVisibility(cfg.showBrandLogo ? View.VISIBLE : View.GONE);
        if (!cfg.clockWallpaper) {
            // clock_glow false: plain black behind the clock (OLED); the clock itself still moves every minute.
            if (!cfg.clockGlow) {
                wallpaperShown = true;   // also stops the glow's colour cycle
                ambient.setBackground(null);
            }
            return;
        }
        wallpaperFile = cfg.wallpaper != null ? cfg.wallpaper : cfg.wallpapers.isEmpty() ? null : cfg.wallpapers.get(0);
        wallpaperShade = (cfg.clockWallpaperDim * 255 / 100) << 24;
        wallpaperRes = ctx.getResources();
    }

    private void loadWallpaper() {
        File file = wallpaperFile;
        int shade = wallpaperShade;
        Resources res = wallpaperRes;
        DisplayMetrics dm = res.getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;
        int generation = wallpaperGeneration;
        new Thread(() -> {
            Bitmap bmp = decode(file, w, h);
            if (bmp == null) return;
            handler.post(() -> {
                if (generation != wallpaperGeneration || wallpaperShown) {
                    bmp.recycle();   // stopped meanwhile (or already shown): this picture was never attached
                    return;
                }
                wallpaperShown = true;
                ambient.setBackground(new LayerDrawable(new Drawable[]{
                        new BitmapDrawable(res, bmp), new ColorDrawable(shade)}));
            });
        }, "clock-wallpaper").start();
    }

    /** WallpaperPlayer's decode: sampled and scaled down to cover the screen, never bigger than 4K. */
    private static Bitmap decode(File file, int screenW, int screenH) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, screenW, screenH);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, screenW, screenH);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withLayer().withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).withLayer().start();
        }).start();
    }

    /**
     * Rais fork: CENTER, bukan drift acak. dreamContent sudah layout_gravity
     * center di XML; di sini hanya micro-shift +-8dp bergantian tiap menit
     * (tidak terlihat sebagai pindah posisi, tapi piksel tidak diam total)
     * dan glow tetap di tengah layar.
     * Fallback: bila layout flip tidak ada (layout lama), pakai drift acak lama.
     */
    private void placeClock() {
        View parent = (View) content.getParent();
        if (flipHour != null && parent.getWidth() > 0 && parent.getHeight() > 0) {
            float density = content.getResources().getDisplayMetrics().density;
            float dx = (tickCount % 2 == 0 ? 8f : -8f) * density;
            float dy = (tickCount % 4 < 2 ? 6f : -6f) * density;
            content.setTranslationX(dx);
            content.setTranslationY(dy);
            glow.setGradientCenter(0.5f, 0.5f);
            return;
        }
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        if (wallpaperShown) return;
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        int to = PALETTE[colorIndex];
        ArgbEvaluator mix = new ArgbEvaluator();
        for (int i = 1; i <= GLOW_STEPS; i++) {
            float f = (float) i / GLOW_STEPS;
            handler.postDelayed(() -> setGlow((int) mix.evaluate(f, from, to)), i * GLOW_STEP_MS);
        }
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
