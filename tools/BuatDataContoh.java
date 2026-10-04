import kaspe.Calculator;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pembuat berkas data contoh (docs/data-contoh.sql).
 *
 * Data contoh dipakai untuk pratinjau dan demo. Dibuat lewat program supaya
 * hasilnya selalu sama dan bisa dibuat ulang kapan saja. Rumusnya diambil dari
 * Calculator, bukan ditulis ulang, supaya angkanya tidak bisa melenceng kalau
 * aturan pembulatan di aplikasi berubah.
 *
 * Cara menjalankan (dari folder proyek, setelah ./build.sh):
 *   javac -cp build -d /tmp/tools tools/BuatDataContoh.java
 *   java -cp "build:/tmp/tools" BuatDataContoh docs/data-contoh.sql
 *
 * Isinya: 23 nota, 64 baris, Juli sampai September 2026.
 */
public class BuatDataContoh {

    static final String[] RENTAL = {
        "Rental Sinar Jaya",
        "Rental Barokah",
        "CV Mitra Tani",
    };

    static final String[][] TRUK = {
        {"BE 8234 HD", "1"}, {"BE 8009 CF", "1"}, {"BE 8570 CF", "1"}, {"BE 8437 CF", "1"},
        {"BE 9120 XY", "2"}, {"BE 7788 AB", "2"}, {"KB 1234 CD", "3"},
    };

    /** Jumlah baris tiap nota. Totalnya harus 64. */
    static final int[] BARIS = {3,2,2,2,4,4,2,2,4,2,3,4,2,3,2,3,4,4,2,2,3,4,1};

    /** Nilai dasar yang divariasikan supaya datanya terlihat wajar. */
    static final int[] LAPAK = {6350,7250,6330,7000,6170,6530,6120,7500,6500,8400,7530,7950,7550,6300,6580,7170,6270,8320,7050,6090,6070,8350,7920,7080,6150,8000,7450,6530,6270,8380,8400,7920,6170,8320,7500,6070,6250,7470,6330,6120,7500,6580,6270,7450};
    static final int[] REFRAKSI = {15,15,12,10,15,12,15,15,15,15,15,18,12,12,15,12,12,12,18,15,12,15,18,15,15,15,15,10,15,15,18,15,10,12,18,15,15,12,12,12,15,15,15,15,10};
    static final int[] HARGA = {1150,1150,1150,1200,1200,1150,1150,1150,1150,1150,1150,1200,1200,1200,1200,1150,1150,1150,1150,1150,1200,1200,1150,1150,1200,1200,1150,1200,1150,1200,1200,1150,1200,1200,1150,1150,1150,1200,1200,1200,1200,1150,1150,1200,1200};

    public static void main(String[] args) throws Exception {
        String keluar = args.length > 0 ? args[0] : "docs/data-contoh.sql";

        int totalBaris = 0;
        for (int b : BARIS) {
            totalBaris += b;
        }
        if (BARIS.length != 23 || totalBaris != 64) {
            throw new IllegalStateException("susunan baris salah: " + BARIS.length + " nota, " + totalBaris + " baris");
        }

        try (PrintWriter w = new PrintWriter(keluar, "UTF-8")) {
            w.println("-- =====================================================================");
            w.println("-- DATA CONTOH - Aplikasi Pencatatan Transaksi Kaspe");
            w.println("--");
            w.println("-- 64 pengiriman, satu catatan per pengiriman, Juli sampai September 2026.");
            w.println("--");
            w.println("-- Satu pengiriman = satu catatan (satu baris transaksi dan satu baris");
            w.println("-- transaksi_detail). Dua pengiriman bertanggal sama tetap dua catatan.");
            w.println("-- Bentuk 1:1 ini disengaja: aplikasi mengubah tanggal lewat header, jadi");
            w.println("-- data contoh yang menumpuk beberapa pengiriman pada satu header akan");
            w.println("-- ikut berpindah tanggal saat salah satunya diubah.");
            w.println("-- Dipakai untuk demo dan pembuatan gambar pratinjau.");
            w.println("--");
            w.println("-- PERHATIAN: berkas ini MENGHAPUS lebih dulu seluruh isi tabel");
            w.println("-- rental, truk, transaksi, dan transaksi_detail, baru");
            w.println("-- mengisinya dengan data contoh. Pakai hanya pada database demo");
            w.println("-- atau salinan - jangan pada database yang berisi data sungguhan.");
            w.println("--");
            w.println("-- Cara memakai (dari folder proyek):");
            w.println("--   1. Jalankan aplikasi sekali supaya tabelnya terbentuk, lalu tutup.");
            w.println("--      Perintah di bawah gagal kalau tabelnya belum ada.");
            w.println("--   2. Isikan berkas ini ke database:");
            w.println("--        java -cp lib/h2-2.1.214.jar org.h2.tools.RunScript \\");
            w.println("--          -url \"jdbc:h2:~/kaspe/db_kaspe;MODE=MySQL;DATABASE_TO_LOWER=TRUE\" \\");
            w.println("--          -user sa -password \"\" -script docs/data-contoh.sql");
            w.println("--   3. Buka aplikasi lagi - 64 pengiriman itu sudah ada.");
            w.println("--");
            w.println("-- Semua angka mengikuti rumus aplikasi:");
            w.println("--   berat_bersih = FLOOR(bobot_pabrik x (1 - refraksi/100) / 5) x 5");
            w.println("--   jumlah_uang  = berat_bersih x harga");
            w.println("--");
            w.println("-- Berkas ini dibuat oleh tools/BuatDataContoh.java - jangan diubah manual.");
            w.println("-- =====================================================================");
            w.println();
            w.println("DELETE FROM transaksi_detail;");
            w.println("DELETE FROM transaksi;");
            w.println("DELETE FROM truk;");
            w.println("DELETE FROM rental;");
            w.println();

            w.println("INSERT INTO rental (id_rental, nama_rental) VALUES");
            for (int i = 0; i < RENTAL.length; i++) {
                w.println("  (" + (i + 1) + ", '" + RENTAL[i] + "')"
                        + (i < RENTAL.length - 1 ? "," : ";"));
            }
            w.println();

            w.println("INSERT INTO truk (id_truk, plat, id_rental) VALUES");
            for (int i = 0; i < TRUK.length; i++) {
                w.println("  (" + (i + 1) + ", '" + TRUK[i][0] + "', " + TRUK[i][1] + ")"
                        + (i < TRUK.length - 1 ? "," : ";"));
            }
            w.println();

            // Satu header untuk SETIAP pengiriman, bukan satu header untuk sekelompok
            // pengiriman bertanggal sama. Model aplikasi sekarang: satu pengiriman = satu
            // catatan. Kalau data contoh masih menumpuk beberapa pengiriman pada satu
            // header, mengubah tanggal salah satunya akan ikut memindahkan yang lain -
            // dan data contoh justru dipakai untuk memperagakan cara kerja yang benar.
            int jumlahPengiriman = 0;
            for (int nota = 0; nota < 23; nota++) {
                jumlahPengiriman += BARIS[nota];
            }
            LocalDate[] tanggalPengiriman = new LocalDate[jumlahPengiriman];
            int isi = 0;
            for (int nota = 0; nota < 23; nota++) {
                LocalDate t = LocalDate.of(2026, 7, 6).plusWeeks(nota / 2);
                for (int k = 0; k < BARIS[nota]; k++) {
                    tanggalPengiriman[isi++] = t;
                }
            }

            w.println("INSERT INTO transaksi (id_transaksi, tanggal) VALUES");
            for (int i = 0; i < jumlahPengiriman; i++) {
                w.println("  (" + (i + 1) + ", '" + tanggalPengiriman[i] + "')"
                        + (i < jumlahPengiriman - 1 ? "," : ";"));
            }
            w.println();

            w.println("INSERT INTO transaksi_detail (id_transaksi, id_truk, bobot_lapak, bobot_pabrik,");
            w.println("       refraksi_persen, berat_bersih, tanggal_lunas, harga, jumlah_uang) VALUES");

            StringBuilder baris = new StringBuilder();
            int jumlahBaris = 0;
            for (int idx = 0; idx < jumlahPengiriman; idx++) {
                int trukId = (idx % 7) + 1;
                int lapak = LAPAK[idx % LAPAK.length];
                int pabrik = lapak - (50 + (idx % 4) * 50);
                int refraksi = REFRAKSI[idx % REFRAKSI.length];
                int harga = HARGA[idx % HARGA.length];
                BigDecimal bersih = kaspe.Calculator.netWeight(
                        new BigDecimal(pabrik), new BigDecimal(refraksi));
                BigDecimal uang = kaspe.Calculator.totalAmount(bersih, new BigDecimal(harga));
                LocalDate lunas = tanggalPengiriman[idx].plusDays(3 + (idx % 10));

                if (jumlahBaris > 0) {
                    baris.append(",\n");
                }
                // Header untuk baris ini adalah baris ke-idx juga: 1 : 1.
                baris.append("  (").append(idx + 1).append(", ").append(trukId).append(", ")
                     .append(lapak).append(", ").append(pabrik).append(", ")
                     .append(refraksi).append(", ").append(bersih.toPlainString()).append(", '")
                     .append(lunas).append("', ").append(harga).append(", ")
                     .append(uang.toPlainString()).append(")");
                jumlahBaris++;
            }
            w.println(baris.append(";").toString());
            System.out.println("dibuat: " + keluar + " (" + jumlahPengiriman + " pengiriman, "
                    + jumlahBaris + " baris)");
        }
    }
}
