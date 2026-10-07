package kaspe.test;

import kaspe.Calculator;

import java.math.BigDecimal;

/**
 * Uji mesin hitung terhadap data ASLI dari buku transaksi mitra.
 * Sumber: foto buku, transaksi No. 6 dan No. 7.
 *
 * Jalankan: javac -d out $(find src -name '*.java') &amp;&amp; java -cp out kaspe.test.TestCalculator
 */
public class TestCalculator {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== UJI MESIN HITUNG SINGKONG (data asli dari buku) ===\n");

        // plat, bobot_lapak, bobot_pabrik, refraksi%, harga, berat_bersih_buku, jumlah_uang_buku
        check("KB 8234 HD / Rahman", 7200, 7050, 15, 1150, 5990, 6888500L);
        check("BE 8009 CF / Usup",   6000, 5970, 15, 1150, 5070, 5830500L);
        check("BE 8009 CF / Usup",   6280, 6150, 15, 1150, 5225, 6008750L);
        check("BE 8570 CF / Alex",   6480, 6380, 15, 1150, 5420, 6233000L);

        System.out.println("\n--- uji tambahan ---");
        checkShrinkage(7200, 7050, 150);
        checkCurrency(new BigDecimal("6888500"), "6.888.500");
        // Angka berdesimal. Isian menerima koma sebagai pemisah desimal, dan kolomnya
        // menyimpan dua angka desimal - jadi nilai-nilai ini memang bisa tersimpan.
        // Dulu ketiganya dibulatkan menjadi angka bulat di layar: refraksi 12,5 tercetak
        // "13%", bobot 7200,5 tercetak "7.201 kg", dan harga 1250,5 tercetak "Rp 1.251" -
        // laporan yang diserahkan berbeda dari yang tersimpan, tanpa satu pun galat.
        checkCurrency(new BigDecimal("1250.50"), "1.250,5");
        checkCurrency(new BigDecimal("7050.00"), "7.050");
        checkCurrency(new BigDecimal("1250.25"), "1.250,25");
        // Satuan ditulis di dalam sel tabel, bukan di judul kolomnya: judul kolom yang
        // menentukan lebar kolom, jadi menaruh "(kg)" di judul melebarkan tiga kolom
        // sekaligus. Diperiksa di sini supaya kedua halaman yang memakainya - transaksi
        // dan laporan - tidak bisa menampilkan satuan yang berbeda.
        checkSatuan(new BigDecimal("7530"), "7.530 kg", new BigDecimal("15"), "15%");
        checkSatuan(new BigDecimal("7200.5"), "7.200,5 kg", new BigDecimal("12.5"), "12,5%");
        checkRefractionError();

        System.out.println("\n=== HASIL: " + passed + " lulus, " + failed + " gagal ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(String label, long fieldWeight, long factoryWeight, int refraction,
                            long price, long expectedNetWeight, long expectedAmount) {
        BigDecimal netWeight = Calculator.netWeight(bd(factoryWeight), bd(refraction));
        BigDecimal amount = Calculator.totalAmount(netWeight, bd(price));
        boolean netWeightOk = netWeight.compareTo(bd(expectedNetWeight)) == 0;
        boolean amountOk = amount.compareTo(bd(expectedAmount)) == 0;
        boolean ok = netWeightOk && amountOk;

        System.out.printf("%-22s lapak=%d pabrik=%d ref=%d%% harga=%d%n", label, fieldWeight, factoryWeight, refraction, price);
        System.out.printf("   berat bersih : hitung=%s  buku=%d  %s%n", netWeight.toPlainString(), expectedNetWeight, netWeightOk ? "OK" : "SALAH");
        System.out.printf("   jumlah uang : hitung=%s  buku=%d  %s%n", Calculator.formatNumber(amount), expectedAmount, amountOk ? "OK" : "SALAH");
        record(ok, "baris " + label);
    }

    private static void checkShrinkage(long fieldWeight, long factoryWeight, long expected) {
        BigDecimal s = Calculator.shrinkage(bd(fieldWeight), bd(factoryWeight));
        boolean ok = s.compareTo(bd(expected)) == 0;
        System.out.printf("selisih susut lapak=%d pabrik=%d -> %s (harap %d) %s%n",
                fieldWeight, factoryWeight, s.toPlainString(), expected, ok ? "OK" : "SALAH");
        record(ok, "selisih susut");
    }

    private static void checkCurrency(BigDecimal v, String expected) {
        String s = Calculator.formatNumber(v);
        boolean ok = s.equals(expected);
        System.out.printf("format rupiah %s -> %s (harap %s) %s%n", v.toPlainString(), s, expected, ok ? "OK" : "SALAH");
        record(ok, "format rupiah");
    }

    private static void checkSatuan(BigDecimal berat, String harapBerat,
                                   BigDecimal refraksi, String harapRefraksi) {
        String b = Calculator.formatKg(berat);
        String r = Calculator.formatPercent(refraksi);
        boolean ok = b.equals(harapBerat) && r.equals(harapRefraksi);
        System.out.printf("satuan: bobot %s -> \"%s\" (harap \"%s\"), refraksi %s -> \"%s\" (harap \"%s\") %s%n",
                berat.toPlainString(), b, harapBerat, refraksi.toPlainString(), r, harapRefraksi,
                ok ? "OK" : "SALAH");
        record(ok, "satuan bobot dan refraksi");
    }

    private static void checkRefractionError() {
        boolean ok = false;
        try {
            Calculator.netWeight(bd(1000), bd(150));
        } catch (IllegalArgumentException e) {
            ok = true;
        }
        System.out.printf("tolak refraksi 150%% -> %s%n", ok ? "OK (ditolak)" : "SALAH (tidak ditolak)");
        record(ok, "validasi refraksi");
    }

    private static void record(boolean ok, String name) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("   >>> GAGAL: " + name);
        }
    }

    private static BigDecimal bd(long v) {
        return new BigDecimal(v);
    }
}
