import kaspe.Calculator;
import kaspe.Db;
import kaspe.dao.MasterDao;
import kaspe.dao.TransactionDao;
import kaspe.model.Rental;
import kaspe.model.ReportRow;
import kaspe.model.Truck;
import kaspe.util.Dates;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pemeriksa kerusakan data KaspeApp (BACA-SAJA).
 *
 * Alat ini menjalankan sendiri di komputer operator untuk melihat apakah datanya
 * menunjukkan tanda rusak akibat bug yang sudah diketahui: rental atau truk hantu
 * (tercatat tetapi tidak pernah dipakai), truk tanpa pemilik, baris lama yang
 * kehilangan plat truknya, transaksi kosong tanpa baris, dan nama rental yang hanya
 * beda besar-kecil huruf (yang membuat rekap uang per pemilik terpecah).
 *
 * Alat ini HANYA MEMBACA. Ia tidak menambah, mengubah, atau menghapus data apa pun,
 * dan tidak membuat berkas. Kalau ada masalah, alat ini hanya melaporkannya; perbaikan
 * tetap dilakukan lewat aplikasi seperti biasa.
 *
 * Cara mengompilasi dan menjalankan (dari folder proyek, setelah ./build.sh):
 *   javac -cp build:lib/h2-2.1.214.jar -d /tmp/tools tools/PeriksaData.java
 *   java -cp "build:lib/*:/tmp/tools" PeriksaData
 */
public class PeriksaData {

    private int jumlahMasalah = 0;

    public static void main(String[] args) throws Exception {
        new PeriksaData().periksa();
    }

    /** Jalankan semua pemeriksaan dan cetak hasilnya. Mengembalikan jumlah masalah. */
    public int periksa() throws SQLException {
        MasterDao master = new MasterDao();
        TransactionDao trx = new TransactionDao();

        List<Rental> rentals = master.listRental();
        List<Truck> trucks = master.listTrucks();
        List<ReportRow> baris = trx.listReport(null, null);
        Set<Integer> trukDipakai = idTrukDipakai();
        Set<Integer> rentalDipakai = idRentalDipakai();

        System.out.println("=== Pemeriksa data KaspeApp ===");
        System.out.println("Alat ini hanya membaca data; tidak ada yang diubah.");

        // 1. Rental yang tidak dipakai transaksi mana pun
        judul("1. Rental yang tidak dipakai satu pun transaksi");
        List<Rental> rentalHantu = new ArrayList<>();
        for (Rental r : rentals) {
            if (!rentalDipakai.contains(r.getRentalId())) {
                rentalHantu.add(r);
            }
        }
        if (rentalHantu.isEmpty()) {
            System.out.println("Aman: setiap rental pernah dipakai oleh baris transaksi.");
        } else {
            System.out.println("Ditemukan " + rentalHantu.size() + " kandidat rental hantu:");
            for (Rental r : rentalHantu) {
                System.out.println("  - \"" + r.getRentalName() + "\"");
            }
            System.out.println("Artinya: nama ini tercatat sebagai pemilik, tetapi tidak satu pun");
            System.out.println("truknya muncul di buku catatan. Biasanya sisa salah ketik nama yang");
            System.out.println("lalu ditulis ulang dengan ejaan benar. Hitungan uang tidak terganggu,");
            System.out.println("tetapi sebaiknya diperiksa dan dihapus lewat halaman Data Master.");
            jumlahMasalah += rentalHantu.size();
        }

        // 2. Truk yang tidak dipakai transaksi mana pun
        judul("2. Truk yang tidak dipakai satu pun transaksi");
        List<Truck> trukHantu = new ArrayList<>();
        for (Truck t : trucks) {
            if (!trukDipakai.contains(t.getTruckId())) {
                trukHantu.add(t);
            }
        }
        if (trukHantu.isEmpty()) {
            System.out.println("Aman: setiap truk pernah dipakai oleh baris transaksi.");
        } else {
            System.out.println("Ditemukan " + trukHantu.size() + " kandidat truk hantu:");
            for (Truck t : trukHantu) {
                System.out.println("  - plat " + t.getPlate());
            }
            System.out.println("Artinya: plat ini tercatat di daftar truk, tetapi tidak pernah muncul");
            System.out.println("di buku catatan. Kalau truk ini memang tidak dikenal, kemungkinan sisa");
            System.out.println("salah ketik plat; bisa diperiksa lalu dihapus lewat halaman Data Master.");
            jumlahMasalah += trukHantu.size();
        }

        // 3. Truk tanpa pemilik
        judul("3. Truk yang tidak punya pemilik (rental)");
        List<Truck> tanpaPemilik = new ArrayList<>();
        for (Truck t : trucks) {
            if (t.getRentalId() == null) {
                tanpaPemilik.add(t);
            }
        }
        if (tanpaPemilik.isEmpty()) {
            System.out.println("Aman: semua truk punya pemilik.");
        } else {
            System.out.println("Ditemukan " + tanpaPemilik.size() + " truk tanpa pemilik:");
            for (Truck t : tanpaPemilik) {
                System.out.println("  - plat " + t.getPlate());
            }
            System.out.println("Artinya: baris laporan untuk truk ini tidak bisa masuk ke rekap uang");
            System.out.println("pemilik mana pun. Penyebab yang paling sering: rental pemiliknya pernah");
            System.out.println("dihapus. Pemilik bisa diisi ulang lewat halaman Data Master.");
            jumlahMasalah += tanpaPemilik.size();
        }

        // 4. Baris transaksi tanpa plat truk
        judul("4. Baris buku yang kehilangan plat truknya");
        List<ReportRow> tanpaPlat = new ArrayList<>();
        for (ReportRow b : baris) {
            if (b.getPlate() == null) {
                tanpaPlat.add(b);
            }
        }
        if (tanpaPlat.isEmpty()) {
            System.out.println("Aman: semua baris buku punya plat truk.");
        } else {
            System.out.println("Ditemukan " + tanpaPlat.size() + " baris tanpa plat:");
            for (ReportRow b : tanpaPlat) {
                System.out.println("  - tanggal " + Dates.format(b.getDate())
                        + ", jumlah uang Rp " + Calculator.formatCurrency(b.getTotalAmount()));
            }
            System.out.println("Artinya: baris lama ini menampilkan kolom plat kosong, karena truknya");
            System.out.println("sudah dihapus dari daftar truk. Uangnya tetap terhitung di total,");
            System.out.println("tetapi tidak bisa dilacak per pemilik. Ini tidak bisa diperbaiki dari");
            System.out.println("aplikasi; barisnya hanya berguna sebagai arsip.");
            jumlahMasalah += tanpaPlat.size();
        }

        // 5. Transaksi tanpa satu pun baris detail
        judul("5. Transaksi (tanggal) yang tidak punya satu pun baris");
        List<String> transaksiKosong = transaksiTanpaBaris();
        if (transaksiKosong.isEmpty()) {
            System.out.println("Aman: setiap transaksi punya paling tidak satu baris.");
        } else {
            System.out.println("Ditemukan " + transaksiKosong.size() + " transaksi kosong:");
            for (String t : transaksiKosong) {
                System.out.println("  - tanggal " + t);
            }
            System.out.println("Artinya: ada catatan tanggal yang tidak berisi satu baris pun. Ini tidak");
            System.out.println("mengganggu hitungan uang (kosong tidak berarti apa-apa di laporan),");
            System.out.println("tetapi tidak seharusnya ada dan menandakan pernah terjadi simpan");
            System.out.println("yang batal di tengah jalan.");
            jumlahMasalah += transaksiKosong.size();
        }

        // 6. Nama rental yang hanya beda besar-kecil huruf
        judul("6. Nama rental yang hanya beda besar-kecil huruf");
        Map<String, List<String>> perKunci = new LinkedHashMap<>();
        for (Rental r : rentals) {
            String kunci = Rental.matchKey(r.getRentalName());
            if (kunci == null) {
                continue;
            }
            List<String> nama = perKunci.get(kunci);
            if (nama == null) {
                nama = new ArrayList<>();
                perKunci.put(kunci, nama);
            }
            nama.add(r.getRentalName());
        }
        int jumlahKembar = 0;
        for (List<String> nama : perKunci.values()) {
            if (nama.size() > 1) {
                jumlahKembar++;
                StringBuilder gabung = new StringBuilder();
                for (int i = 0; i < nama.size(); i++) {
                    if (i > 0) {
                        gabung.append(i == nama.size() - 1 ? " dan " : ", ");
                    }
                    gabung.append('"').append(nama.get(i)).append('"');
                }
                System.out.println("  - " + gabung);
            }
        }
        if (jumlahKembar == 0) {
            System.out.println("Aman: tidak ada nama rental yang kembar.");
        } else {
            System.out.println("Artinya: nama-nama di atas besar kemungkinan rental yang sama, hanya");
            System.out.println("ditulis dengan besar-kecil huruf berbeda. Akibatnya rekap uang per");
            System.out.println("pemilik terpecah jadi dua. Perbaikannya: samakan tulisannya lewat");
            System.out.println("halaman Data Master supaya uangnya menyatu kembali.");
            jumlahMasalah += jumlahKembar;
        }

        // 7. Ringkasan
        judul("7. Ringkasan isi data");
        BigDecimal totalUang = BigDecimal.ZERO;
        for (ReportRow b : baris) {
            if (b.getTotalAmount() != null) {
                totalUang = totalUang.add(b.getTotalAmount());
            }
        }
        System.out.println("Jumlah rental         : " + rentals.size());
        System.out.println("Jumlah truk           : " + trucks.size());
        System.out.println("Jumlah transaksi      : " + jumlahBaris("SELECT COUNT(*) FROM transaksi"));
        System.out.println("Jumlah baris buku     : " + baris.size());
        System.out.println("Total uang seluruhnya : Rp " + Calculator.formatCurrency(totalUang));

        System.out.println();
        if (jumlahMasalah == 0) {
            System.out.println("=== HASIL: tidak ditemukan tanda kerusakan data ===");
        } else {
            System.out.println("=== HASIL: ditemukan " + jumlahMasalah
                    + " masalah — rincian dan penjelasannya di atas ===");
        }
        return jumlahMasalah;
    }

    private void judul(String teks) {
        System.out.println();
        System.out.println("--- " + teks + " ---");
    }

    /** Id truk yang muncul di paling tidak satu baris transaksi. */
    private Set<Integer> idTrukDipakai() throws SQLException {
        Set<Integer> hasil = new HashSet<>();
        try (Connection c = Db.get(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT DISTINCT id_truk FROM transaksi_detail WHERE id_truk IS NOT NULL")) {
            while (rs.next()) {
                hasil.add(rs.getInt("id_truk"));
            }
        }
        return hasil;
    }

    /** Id rental yang truknya muncul di paling tidak satu baris transaksi. */
    private Set<Integer> idRentalDipakai() throws SQLException {
        Set<Integer> hasil = new HashSet<>();
        try (Connection c = Db.get(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT DISTINCT t.id_rental FROM truk t"
                     + " JOIN transaksi_detail d ON d.id_truk = t.id_truk"
                     + " WHERE t.id_rental IS NOT NULL")) {
            while (rs.next()) {
                hasil.add(rs.getInt("id_rental"));
            }
        }
        return hasil;
    }

    /** Tanggal (dd-MM-yyyy) transaksi yang tidak punya satu pun baris detail. */
    private List<String> transaksiTanpaBaris() throws SQLException {
        List<String> hasil = new ArrayList<>();
        try (Connection c = Db.get(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT t.tanggal FROM transaksi t"
                     + " LEFT JOIN transaksi_detail d ON d.id_transaksi = t.id_transaksi"
                     + " WHERE d.id_detail IS NULL"
                     + " ORDER BY t.tanggal, t.id_transaksi")) {
            while (rs.next()) {
                java.sql.Date d = rs.getDate("tanggal");
                hasil.add(d == null ? "?" : Dates.format(d.toLocalDate()));
            }
        }
        return hasil;
    }

    /** Hasil satu kueri COUNT(*). */
    private int jumlahBaris(String sql) throws SQLException {
        try (Connection c = Db.get(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
