package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Rais TV fork — FLIP CLOCK screensaver shown by Home itself after
 * home_idle_clock_minutes (2 di fork ini) without input.
 * Perubahan vs upstream:
 * - FLAG_KEEP_SCREEN_ON: layar tidak tidur selama screensaver jalan
 *   (pasangan dari setup script yang mematikan sleep timeout TV).
 * - Fullscreen immersive (sudah ada di upstream): status bar / shade
 *   notifikasi tidak tampil di atas screensaver, jadi "notif running"
 *   tidak memotong tampilan jam. Notifikasi itu sendiri tidak dihapus
 *   dari sistem — hanya tidak tampil selama screensaver aktif; begitu
 *   tombol ditekan dan kembali ke Home, notifikasi tampil normal lagi.
 * This one only ever starts from Home. Any key returns to Home.
 */
public class ClockActivity extends Activity {

    private ClockScene scene;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.dream_clock);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);
        scene = new ClockScene(getWindow().getDecorView());
        scene.useHomeWallpaper(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scene.start();
    }

    @Override
    protected void onPause() {
        scene.stop();
        super.onPause();
        finish();   // never left behind in the back stack
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // The key that wakes the clock should not also act on Home, so it is consumed here.
        if (event.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        finish();
        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
