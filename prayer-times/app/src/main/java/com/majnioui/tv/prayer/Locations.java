package com.majnioui.tv.prayer;

/**
 * Rais TV fork — daftar lokasi Indonesia by PROVINSI → KOTA.
 * Dipakai pemilih lokasi di MainActivity (remote D-pad, bukan GPS:
 * TV umumnya tidak punya GPS dan addon upstream tidak minta
 * permission lokasi; user memilih provinsi lalu kota sekali,
 * pilihan disimpan di SharedPreferences).
 *
 * Koordinat = pusat kota (bukan pusat provinsi) — waktu sholat
 * dihitung Aladhan dari koordinat kota yang dipilih, jadi akurasinya
 * setingkat kota, bukan se-provinsi. Angka dibulatkan 2 desimal
 * (cukup untuk jadwal harian; selisih vs koordinat presisi < 1 menit
 * untuk kota yang sama).
 * Metode Aladhan di fork ini = 20 (Kementerian Agama RI).
 * Kota yang belum ada bisa ditambah di sini lalu rebuild.
 */
public final class Locations {
    private Locations() {}

    public static final class City {
        public final String name;
        public final double lat;
        public final double lon;
        public City(String name, double lat, double lon) {
            this.name = name; this.lat = lat; this.lon = lon;
        }
    }

    public static final class Province {
        public final String name;
        public final City[] cities;
        public Province(String name, City[] cities) {
            this.name = name; this.cities = cities;
        }
    }

    public static final Province[] PROVINCES = {
        new Province("Aceh", new City[]{
            new City("Banda Aceh", 5.55, 95.32), new City("Lhokseumawe", 5.18, 97.15)}),
        new Province("Sumatera Utara", new City[]{
            new City("Medan", 3.59, 98.67), new City("Pematangsiantar", 2.96, 99.06)}),
        new Province("Sumatera Barat", new City[]{
            new City("Padang", -0.95, 100.35), new City("Bukittinggi", -0.30, 100.37)}),
        new Province("Riau", new City[]{
            new City("Pekanbaru", 0.51, 101.44), new City("Dumai", 1.67, 101.44)}),
        new Province("Kepulauan Riau", new City[]{
            new City("Batam", 1.13, 104.05), new City("Tanjungpinang", 0.92, 104.45)}),
        new Province("Jambi", new City[]{
            new City("Jambi", -1.61, 103.61)}),
        new Province("Sumatera Selatan", new City[]{
            new City("Palembang", -2.99, 104.76)}),
        new Province("Bengkulu", new City[]{
            new City("Bengkulu", -3.80, 102.26)}),
        new Province("Lampung", new City[]{
            new City("Bandar Lampung", -5.43, 105.27)}),
        new Province("Bangka Belitung", new City[]{
            new City("Pangkal Pinang", -2.13, 106.11)}),
        new Province("Banten", new City[]{
            new City("Serang", -6.11, 106.15), new City("Tangerang", -6.17, 106.63),
            new City("Cilegon", -6.00, 106.02), new City("Tangerang Selatan", -6.29, 106.72)}),
        new Province("DKI Jakarta", new City[]{
            new City("Jakarta", -6.21, 106.85)}),
        new Province("Jawa Barat", new City[]{
            new City("Bandung", -6.91, 107.61), new City("Bekasi", -6.24, 106.99),
            new City("Bogor", -6.59, 106.79), new City("Depok", -6.40, 106.79),
            new City("Cirebon", -6.71, 108.55)}),
        new Province("Jawa Tengah", new City[]{
            new City("Semarang", -6.99, 110.42), new City("Solo", -7.57, 110.82),
            new City("Purwokerto", -7.42, 109.24)}),
        new Province("DI Yogyakarta", new City[]{
            new City("Yogyakarta", -7.80, 110.36)}),
        new Province("Jawa Timur", new City[]{
            new City("Surabaya", -7.26, 112.74), new City("Malang", -7.98, 112.63),
            new City("Sidoarjo", -7.45, 112.72), new City("Madiun", -7.63, 111.52)}),
        new Province("Bali", new City[]{
            new City("Denpasar", -8.65, 115.22)}),
        new Province("Nusa Tenggara Barat", new City[]{
            new City("Mataram", -8.58, 116.10)}),
        new Province("Nusa Tenggara Timur", new City[]{
            new City("Kupang", -10.18, 123.58)}),
        new Province("Kalimantan Barat", new City[]{
            new City("Pontianak", -0.03, 109.32)}),
        new Province("Kalimantan Tengah", new City[]{
            new City("Palangka Raya", -2.21, 113.92)}),
        new Province("Kalimantan Selatan", new City[]{
            new City("Banjarmasin", -3.32, 114.59)}),
        new Province("Kalimantan Timur", new City[]{
            new City("Samarinda", -0.50, 117.15), new City("Balikpapan", -1.24, 116.83)}),
        new Province("Kalimantan Utara", new City[]{
            new City("Tarakan", 3.31, 117.59)}),
        new Province("Sulawesi Utara", new City[]{
            new City("Manado", 1.47, 124.84)}),
        new Province("Gorontalo", new City[]{
            new City("Gorontalo", 0.54, 123.06)}),
        new Province("Sulawesi Tengah", new City[]{
            new City("Palu", -0.90, 119.87)}),
        new Province("Sulawesi Barat", new City[]{
            new City("Mamuju", -2.67, 118.89)}),
        new Province("Sulawesi Selatan", new City[]{
            new City("Makassar", -5.15, 119.41)}),
        new Province("Sulawesi Tenggara", new City[]{
            new City("Kendari", -3.99, 122.52)}),
        new Province("Maluku", new City[]{
            new City("Ambon", -3.65, 128.19)}),
        new Province("Maluku Utara", new City[]{
            new City("Ternate", 0.79, 127.38)}),
        new Province("Papua", new City[]{
            new City("Jayapura", -2.53, 140.72)}),
        new Province("Papua Barat", new City[]{
            new City("Manokwari", -0.86, 134.08)}),
        new Province("Papua Pegunungan", new City[]{
            new City("Wamena", -4.10, 138.95)}),
        new Province("Papua Selatan", new City[]{
            new City("Merauke", -8.49, 140.40)}),
        new Province("Papua Tengah", new City[]{
            new City("Nabire", -3.36, 135.50)}),
    };
}
