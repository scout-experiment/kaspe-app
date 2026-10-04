import kaspe.Calculator;
import kaspe.Db;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Pemeriksa data contoh (docs/data-contoh.sql).
 *
 * Memasang berkas data contoh ke database sementara, lalu menghitung ulang setiap
 * baris dengan rumus aplikasi (Calculator) dan membandingkannya dengan angka yang
 * tersimpan. Gunanya supaya klaim "semua angka mengikuti rumus aplikasi" bisa
 * dibuktikan kapan saja, bukan hanya dipercaya.
 *
 * Cara menjalankan (dari folder proyek, setelah ./build.sh):
 *   javac -cp build:lib/h2-2.1.214.jar -d /tmp/tools tools/PeriksaDataContoh.java
 *   java -cp "build:lib/*:/tmp/tools" PeriksaDataContoh
 *
 * Argumen kedua (opsional) menunjuk folder database yang sudah ada, dipakai untuk
 * memeriksa hasil render pratinjau (yang berisi tiga pengiriman tambahan):
 *   java -cp "build:lib/*:/tmp/tools" PeriksaDataContoh "" /tmp/kaspe-pratinjau
 *
 * Keluar dengan kode 1 kalau ada selisih.
 */
public class PeriksaDataContoh {

    public static void main(String[] args) throws Exception {
        Path berkas = Paths.get(args.length > 0 && !args[0].isEmpty()
                ? args[0] : "docs/data-contoh.sql");
        Path rumah = args.length > 1 && !args[1].isEmpty()
                ? Paths.get(args[1])
                : Paths.get(System.getProperty("java.io.tmpdir"), "kaspe-periksa");
        boolean pakaiBerkas = args.length <= 1 || args[1].isEmpty();

        Files.createDirectories(rumah.resolve("kaspe"));
        if (pakaiBerkas) {
            Files.deleteIfExists(rumah.resolve("kaspe/db_kaspe.mv.db"));
        }

        Db.setConfiguration("org.h2.Driver",
                "jdbc:h2:" + rumah.resolve("kaspe/db_kaspe")
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");

        try (Connection c = Db.get(); Statement s = c.createStatement()) {
            if (pakaiBerkas) {
                String isi = new String(Files.readAllBytes(berkas), StandardCharsets.UTF_8);
                for (String perintah : isi.split(";")) {
                    if (!perintah.trim().isEmpty()) {
                        try {
                            s.execute(perintah);
                        } catch (Exception e) {
                            // Pernyataan yang tidak berlaku (mis. CREATE DATABASE) dilewati.
                        }
                    }
                }
            }

            int pengiriman = 0;
            try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM transaksi")) {
                r.next();
                pengiriman = r.getInt(1);
            }

            int baris = 0;
            int selisihBerat = 0;
            int selisihUang = 0;
            try (ResultSet r = s.executeQuery(
                    "SELECT bobot_pabrik, refraksi_persen, berat_bersih, harga, jumlah_uang "
                            + "FROM transaksi_detail")) {
                while (r.next()) {
                    baris++;
                    BigDecimal pabrik = r.getBigDecimal(1);
                    BigDecimal refraksi = r.getBigDecimal(2);
                    BigDecimal beratTersimpan = r.getBigDecimal(3);
                    BigDecimal harga = r.getBigDecimal(4);
                    BigDecimal uangTersimpan = r.getBigDecimal(5);

                    BigDecimal beratHitung = Calculator.netWeight(pabrik, refraksi);
                    BigDecimal uangHitung = Calculator.totalAmount(beratHitung, harga);

                    if (beratHitung.compareTo(beratTersimpan) != 0) {
                        selisihBerat++;
                        System.out.println("  berat beda: tersimpan " + beratTersimpan
                                + ", hitung " + beratHitung);
                    }
                    if (uangHitung.compareTo(uangTersimpan) != 0) {
                        selisihUang++;
                        System.out.println("  uang beda: tersimpan " + uangTersimpan
                                + ", hitung " + uangHitung);
                    }
                }
            }

            System.out.println("sumber          = " + (pakaiBerkas ? berkas.toString()
                    : "database " + rumah.resolve("kaspe/db_kaspe")));
            System.out.println("pengiriman      = " + pengiriman);
            System.out.println("baris diperiksa = " + baris);
            System.out.println("selisih berat   = " + selisihBerat);
            System.out.println("selisih uang    = " + selisihUang);

            try (ResultSet r = s.executeQuery(
                    "SELECT COALESCE(SUM(jumlah_uang),0), COALESCE(SUM(berat_bersih),0) "
                            + "FROM transaksi_detail")) {
                r.next();
                System.out.println("total uang      = Rp " + r.getBigDecimal(1).toPlainString());
                System.out.println("total berat     = " + r.getBigDecimal(2).toPlainString() + " kg");
            }

            if (selisihBerat > 0 || selisihUang > 0) {
                System.out.println("HASIL: ADA SELISIH");
                System.exit(1);
            }
            System.out.println("HASIL: cocok dengan rumus aplikasi");
        }
    }
}
