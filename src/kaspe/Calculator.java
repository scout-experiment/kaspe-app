package kaspe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Mesin hitung transaksi singkong.
 *
 * Rumus (hasil verifikasi terhadap buku transaksi mitra, cocok 4/4 baris):
 *   berat_bersih = (bobot_pabrik x (1 - refraksi/100)) dibulatkan KE BAWAH ke kelipatan 5
 *   jumlah_uang  = berat_bersih x harga
 *
 * Contoh nyata dari buku:
 *   bobot_pabrik 7050, refraksi 15%, harga 1150
 *     7050 x 0.85 = 5992.5  -&gt; 5990
 *     5990 x 1150 = 6.888.500
 */
public final class Calculator {

    /** pembulatan berat bersih ke bawah, kelipatan 5 kg */
    public static final BigDecimal WEIGHT_MULTIPLE = new BigDecimal("5");

    private Calculator() {
    }

    /**
     * Berat bersih = bobot pabrik dikurangi refraksi, dibulatkan ke bawah ke kelipatan 5.
     *
     * @param factoryWeight  berat timbangan di pabrik (kg)
     * @param refractionPercent potongan susut dalam persen, contoh 15 untuk 15%
     * @return berat bersih (kg)
     */
    public static BigDecimal netWeight(BigDecimal factoryWeight, BigDecimal refractionPercent) {
        if (factoryWeight == null || refractionPercent == null) {
            throw new IllegalArgumentException("bobot pabrik dan refraksi wajib diisi");
        }
        if (factoryWeight.signum() < 0) {
            throw new IllegalArgumentException("bobot pabrik tidak boleh negatif");
        }
        if (refractionPercent.signum() < 0 || refractionPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("refraksi harus antara 0 dan 100 persen");
        }
        BigDecimal factor = BigDecimal.ONE.subtract(
                refractionPercent.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
        BigDecimal gross = factoryWeight.multiply(factor);
        // bulatkan ke bawah ke kelipatan 5
        BigDecimal divided = gross.divide(WEIGHT_MULTIPLE, 0, RoundingMode.FLOOR);
        return divided.multiply(WEIGHT_MULTIPLE).stripTrailingZeros();
    }

    /**
     * Jumlah uang = berat bersih x harga per kg.
     */
    public static BigDecimal totalAmount(BigDecimal netWeight, BigDecimal price) {
        if (netWeight == null || price == null) {
            throw new IllegalArgumentException("berat bersih dan harga wajib diisi");
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("harga tidak boleh negatif");
        }
        return netWeight.multiply(price).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Susut = bobot lapak - bobot pabrik.
     * Angka positif berarti ada kg yang hilang di perjalanan (fitur monitoring).
     * Contoh: lapak 7200, pabrik 7050 -&gt; susut 150 kg.
     */
    public static BigDecimal shrinkage(BigDecimal fieldWeight, BigDecimal factoryWeight) {
        if (fieldWeight == null || factoryWeight == null) {
            throw new IllegalArgumentException("bobot lapak dan bobot pabrik wajib diisi");
        }
        return fieldWeight.subtract(factoryWeight);
    }

    /**
     * Angka dengan pemisah ribuan titik, dan koma untuk desimalnya bila ada.
     *
     * <p>Dipakai bersama oleh uang, bobot, dan persen, supaya ketiganya tidak mungkin
     * menuliskan angka dengan cara yang berbeda.
     *
     * <p>Angka yang tersimpan berdesimal tetap ditulis berdesimal. Isian menerima koma
     * sebagai pemisah desimal ("12,5"), dan kolomnya menyimpan dua angka desimal, jadi
     * refraksi 12,5 tersimpan sebagai 12,50 dan bobot 7200,5 sebagai 7200,50.
     * Membulatkannya menjadi angka bulat di layar membuat laporan yang tercetak berbeda
     * dari yang tersimpan dan dari isian yang dimuat ulang: 12,5% tercetak "13%" dan
     * 7200,5 kg tercetak "7.201 kg" - angkanya berubah di atas kertas.
     *
     * <p>Locale.GERMANY dipaku, bukan Locale bawaan mesin: pemisahnya persis kebalikan
     * Locale.US (ribuan titik, desimal koma), jadi angkanya langsung terbaca dengan
     * kebiasaan Indonesia tanpa penggantian tanda yang bisa meleset. Di mesin berbahasa
     * Prancis pemisah ribuan bawaan adalah spasi tak-terpisah, dan angkanya tercetak
     * salah tanpa satu pun galat.
     */
    public static String formatNumber(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal rapi = value.stripTrailingZeros();
        // Nol di ekor dibuang supaya 7050 -yang tersimpan sebagai 7050,00- terbaca 7.050,
        // bukan 7.050,00. Dua angka di paling banyak: itu batas yang dipakai kolomnya.
        int desimal = Math.max(0, Math.min(2, rapi.scale()));
        return String.format(Locale.GERMANY, "%,." + desimal + "f", rapi);
    }

    /**
     * Angka bobot beserta satuannya, mis. {@code 7.530 kg}.
     *
     * <p>Satuannya ditulis di selnya, bukan di judul kolomnya. Judul kolom yang menentukan
     * lebar kolom - menambahkan "(kg)" di judul melebarkan tiga kolom sekaligus, sedangkan
     * di dalam sel muat tanpa menambah lebar sama sekali karena judulnya tetap yang
     * terpanjang. Ini juga sejalan dengan kolom uang, yang sudah menulis "Rp" di selnya.
     */
    public static String formatKg(BigDecimal value) {
        return formatNumber(value) + " kg";
    }

    /** Angka refraksi beserta tanda persennya, mis. {@code 15%}. */
    public static String formatPercent(BigDecimal value) {
        return formatNumber(value) + "%";
    }
}
