package com.majnioui.tv.prayer;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.content.res.ColorStateList;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Rais TV fork — Today's prayer times for one city from Aladhan (free, no
 * account or key; only the coordinates and the date are sent).
 * Perubahan vs upstream:
 * - Lokasi DIPILIH user by provinsi -> kota (Locations.java, D-pad),
 *   disimpan di SharedPreferences; bukan lagi hardcode Casablanca.
 *   Pertama dibuka tanpa lokasi tersimpan -> layar pilih lokasi.
 * - Metode = 20 (Kementerian Agama RI) di config.xml.
 * - Nama waktu ditampilkan Indonesia (Subuh/Dzuhur/dll), tanggal Indonesia.
 * - Tombol "Ganti Lokasi" di bawah jadwal untuk ganti provinsi/kota.
 * The next prayer is highlighted with a countdown.
 */
public class MainActivity extends Activity {
    private static final String TAG = "PrayerTimes";
    private static final String[] NAMES = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final String[] DISPLAY = {"Subuh", "Terbit", "Dzuhur", "Ashar", "Maghrib", "Isya"};
    private static final String[] ARABIC = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final int[] ICONS = {R.drawable.ic_fajr, R.drawable.ic_sunrise, R.drawable.ic_dhuhr,
            R.drawable.ic_asr, R.drawable.ic_maghrib, R.drawable.ic_isha};
    private static final long MINUTE_MS = 60_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Typeface font;
    private int accent;
    private TextView dates, next, status;
    private LinearLayout rows;
    /** Today's times as minutes after midnight, in NAMES order; null until loaded. */
    private int[] minutes;
    private String[] times;
    /** The calculation method's name as Aladhan gives it, for the status line. */
    private String methodName = "";
    /** The day the times are for (yyyy-MM-dd), to reload after midnight. */
    private String loadedFor;
    private boolean loading;
    private SharedPreferences prefs;
    private String savedProvince = "";
    private String savedCity = "";
    private double savedLat;
    private double savedLon;
    private int selectProvince = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!today().equals(loadedFor)) load();
            else render();
            main.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        font = getResources().getFont(R.font.outfit);
        accent = getColor(R.color.accent);
        prefs = getSharedPreferences("rais_prayer", MODE_PRIVATE);
        savedProvince = prefs.getString("province", "");
        savedCity = prefs.getString("city", "");
        savedLat = Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(-6.11)));
        savedLon = Double.longBitsToDouble(prefs.getLong("lon", Double.doubleToLongBits(106.15)));
        if (savedCity.isEmpty()) {
            showSelector();
            return;
        }
        buildTimesUi();
    }

    /** Layar pilih lokasi: langkah 1 provinsi, langkah 2 kota (D-pad: atas/bawah + OK). */
    private void showSelector() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(80), dp(48), dp(80), dp(40));
        scroll.addView(col);
        TextView title = text(28, 500, 0xFFFFFFFF);
        TextView sub = text(18, 400, 0xB3FFFFFF);
        if (selectProvince < 0) {
            title.setText("PILIH PROVINSI");
            sub.setText("Pakai remote: atas / bawah, lalu OK");
            sub.setPadding(0, dp(10), 0, dp(30));
            col.addView(title);
            col.addView(sub);
            for (int i = 0; i < Locations.PROVINCES.length; i++) {
                final int idx = i;
                col.addView(selectorItem(Locations.PROVINCES[i].name, () -> {
                    selectProvince = idx;
                    showSelector();
                }));
            }
        } else {
            Locations.Province prov = Locations.PROVINCES[selectProvince];
            title.setText("PILIH KOTA  ·  " + prov.name.toUpperCase(java.util.Locale.ROOT));
            sub.setText("Pakai remote: atas / bawah, lalu OK  ·  BACK untuk ganti provinsi");
            sub.setPadding(0, dp(10), 0, dp(30));
            col.addView(title);
            col.addView(sub);
            for (Locations.City city : prov.cities) {
                final Locations.City c = city;
                col.addView(selectorItem(c.name, () -> {
                    prefs.edit().putString("province", prov.name).putString("city", c.name)
                            .putLong("lat", Double.doubleToLongBits(c.lat))
                            .putLong("lon", Double.doubleToLongBits(c.lon)).apply();
                    savedProvince = prov.name; savedCity = c.name;
                    savedLat = c.lat; savedLon = c.lon;
                    selectProvince = -1; minutes = null; loadedFor = null;
                    buildTimesUi();
                    load();
                }));
            }
        }
        setContentView(scroll);
        // Fokus ke item pertama supaya remote langsung bisa jalan.
        if (col.getChildCount() > 2) col.getChildAt(2).requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (selectProvince >= 0) {
            selectProvince = -1;
            showSelector();
            return;
        }
        super.onBackPressed();
    }

    private TextView selectorItem(String label, Runnable onClick) {
        TextView item = text(24, 400, 0xFFFFFFFF);
        item.setText(label);
        item.setPadding(dp(20), dp(16), dp(20), dp(16));
        item.setFocusable(true);
        item.setFocusableInTouchMode(true);
        item.setOnClickListener(v -> onClick.run());
        item.setOnFocusChangeListener((v, focused) -> {
            v.setBackgroundColor(focused ? accent : 0x00000000);
            ((TextView) v).setTextColor(focused ? 0xFFFFFFFF : 0xB3FFFFFF);
        });
        return item;
    }

    private void buildTimesUi() {

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {(accent & 0x00FFFFFF) | 0x55000000, 0xFF050607, 0xFF050607}));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(80), dp(48), dp(80), dp(40));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextView place = text(18, 400, 0xB3FFFFFF);
        place.setLetterSpacing(0.25f);
        place.setText((savedProvince.isEmpty() ? getString(R.string.place) : savedProvince + "  ·  " + savedCity).toUpperCase(Locale.ROOT));
        col.addView(place);

        next = text(52, 300, 0xFFFFFFFF);
        next.setPadding(0, dp(10), 0, dp(4));
        col.addView(next);
        dates = text(20, 400, 0xB3FFFFFF);
        col.addView(dates);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        rlp.setMargins(0, dp(36), 0, 0);
        col.addView(rows, rlp);

        status = text(16, 400, 0x80FFFFFF);
        status.setPadding(0, dp(18), 0, 0);
        status.setText("Loading…");
        col.addView(status);

        TextView change = text(18, 500, 0xB3FFFFFF);
        change.setText("GANTI LOKASI");
        change.setPadding(dp(20), dp(18), dp(20), dp(18));
        change.setFocusable(true);
        change.setOnClickListener(v -> {
            main.removeCallbacks(tick);
            selectProvince = -1;
            showSelector();
        });
        change.setOnFocusChangeListener((v, focused) ->
                v.setBackgroundColor(focused ? accent : 0x00000000));
        col.addView(change);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (savedCity == null || savedCity.isEmpty()) return;  // masih di layar pilih lokasi
        load();
        main.postDelayed(tick, MINUTE_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    private void load() {
        if (loading) return;
        loading = true;
        final String day = today();
        String url = "https://api.aladhan.com/v1/timings/"
                + new SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).format(new Date())
                + "?latitude=" + savedLat + "&longitude=" + savedLon
                + "&method=" + getString(R.string.method);
        new Thread(() -> {
            JSONObject json = null;
            try {
                json = new JSONObject(fetch(url));
            } catch (Exception e) {
                Log.w(TAG, "fetch: " + e);
            }
            final JSONObject result = json;
            main.post(() -> {
                loading = false;
                if (result == null) {
                    status.setText("Can't reach Aladhan. Showing the last times, if any.");
                    return;
                }
                try {
                    parse(result.getJSONObject("data"));
                    loadedFor = day;
                    render();
                } catch (Exception e) {
                    Log.w(TAG, "parse: " + e);
                    status.setText("Unexpected answer from Aladhan.");
                }
            });
        }).start();
    }

    private void parse(JSONObject data) throws Exception {
        JSONObject t = data.getJSONObject("timings");
        int[] m = new int[NAMES.length];
        String[] s = new String[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            String v = t.getString(NAMES[i]).trim();
            int sp = v.indexOf(' ');
            if (sp > 0) v = v.substring(0, sp);   // "05:12 (+01)" style suffixes
            s[i] = v;
            m[i] = Integer.parseInt(v.substring(0, 2)) * 60 + Integer.parseInt(v.substring(3, 5));
        }
        minutes = m;
        times = s;
        JSONObject meta = data.optJSONObject("meta");
        JSONObject method = meta != null ? meta.optJSONObject("method") : null;
        methodName = method != null ? method.optString("name", "") : "";
        JSONObject date = data.getJSONObject("date");
        JSONObject hijri = date.getJSONObject("hijri");
        dates.setText(new SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("id", "ID")).format(new Date()) + "  ·  "
                + hijri.getString("day") + " " + hijri.getJSONObject("month").getString("en") + " "
                + hijri.getString("year") + " AH");
    }

    private void render() {
        if (minutes == null) return;
        Calendar c = Calendar.getInstance();
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int nextIdx = -1;
        for (int i = 0; i < NAMES.length; i++) {
            if (i == 1) continue;   // sunrise is shown but is not a prayer
            if (minutes[i] > now) { nextIdx = i; break; }
        }
        if (nextIdx >= 0) {
            int left = minutes[nextIdx] - now;
            next.setText(DISPLAY[nextIdx] + " dalam " + (left >= 60 ? left / 60 + " jam " : "") + left % 60 + " menit");
        } else {
            next.setText("Subuh besok");
        }

        rows.removeAllViews();
        for (int i = 0; i < NAMES.length; i++) {
            boolean hl = i == nextIdx;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(6), dp(20), dp(6), dp(20));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(22));
            bg.setColor(hl ? accent : 0x14FFFFFF);
            card.setBackground(bg);
            int dim = i == 1 ? 0x80FFFFFF : 0xFFFFFFFF;
            int fg = hl ? 0xFFFFFFFF : dim;
            ImageView icon = new ImageView(this);
            icon.setImageResource(ICONS[i]);
            icon.setImageTintList(ColorStateList.valueOf(hl ? 0xFFFFFFFF : (i == 1 ? 0x80FFFFFF : accent)));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
            ilp.bottomMargin = dp(22);
            card.addView(icon, ilp);
            TextView ar = centered(text(26, 400, fg));
            ar.setText(ARABIC[i]);
            card.addView(ar);
            TextView name = centered(text(20, 500, hl ? 0xE6FFFFFF : (i == 1 ? 0x80FFFFFF : 0xB3FFFFFF)));
            name.setText(DISPLAY[i]);
            name.setPadding(0, dp(8), 0, dp(20));
            card.addView(name);
            TextView time = centered(text(42, hl ? 500 : 300, fg));
            time.setText(times[i]);
            // Shrink rather than cut "05:10" when the card is narrow.
            time.setMaxLines(1);
            time.setAutoSizeTextTypeUniformWithConfiguration(20, 42, 1, TypedValue.COMPLEX_UNIT_SP);
            card.addView(time, new LinearLayout.LayoutParams(-1, dp(52)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            lp.setMargins(i == 0 ? 0 : dp(8), 0, i == NAMES.length - 1 ? 0 : dp(8), 0);
            rows.addView(card, lp);
        }
        status.setText("Aladhan" + (methodName.isEmpty() ? "" : " · " + methodName) + " · updated "
                + new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date()));
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** Centred on its own line, whatever the script direction. */
    private static TextView centered(TextView t) {
        t.setGravity(Gravity.CENTER);
        t.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        return t;
    }

    private TextView text(float sp, int weight, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, weight, false) : font);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
