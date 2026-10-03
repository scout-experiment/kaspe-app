import kaspe.Calculator;
import kaspe.Db;
import kaspe.model.Rental;
import kaspe.model.Truck;
import kaspe.util.Dates;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pemeriksa kerusakan data KaspeApp (BACA-SAJA).
 *
 * <p>Alat ini dijalankan sendiri oleh operator di komputernya untuk melihat apakah
 * datanya menunjukkan tanda rusak: truk tanpa pemilik, baris lama yang kehilangan plat
 * truknya, transaksi kosong tanpa baris, dan nama rental yang hanya beda besar-kecil
 * huruf (yang membuat rekap uang per pemilik terpecah).
 *
 * <p><b>Alat ini tidak mengubah apa pun.</b> Ia membuka koneksinya sendiri, TIDAK lewat
 * {@link Db#get()} - jalur itu menjalankan pembuatan tabel, dan pemeriksaan yang
 * seharusnya hanya membaca tidak boleh mengubah database yang sedang diperiksa. Kalau
 * database H2-nya belum ada, alat ini berhenti dan mengatakannya, bukan membuat database
 * kosong lalu melaporkan "aman" - jawaban menenangkan yang justru menyesatkan.
 *
 * <p>Nama database yang diperiksa selalu dicetak di awal, supaya jelas mana yang dibaca.
 *
 * <p>Cara mengompilasi dan menjalankan (dari folder proyek, setelah ./build.sh):
 * <pre>
 *   javac -cp "build:lib/*" -d /tmp/tools tools/PeriksaData.java
 *   java -cp "build:lib/*:/tmp/tools" PeriksaData
 * </pre>
 *
 * <p>Tanpa berkas {@code mysql-connector-j} di folder {@code lib/}, alat ini hanya bisa
 * memeriksa mode H2 (bawaan).
 */
public class PeriksaData {

    /** Dianggap "database kosong" kalau rental, truk, dan transaksi semuanya nol. */
    private int jumlahMasalah = 0;

    public static void main(String[] args) throws Exception {
        new PeriksaData().periksa();
    }

    /** Jalankan semua pemeriksaan dan cetak hasilnya. Mengembalikan jumlah masalah. */
    public int periksa() throws SQLException {
        System.out.println("=== Pemeriksa data KaspeApp ===");
        System.out.println("Database yang diperiksa:");
        System.out.println("  " + Db.infoUrl());
        System.out.println();
        System.out.println("Alat ini hanya membaca isi buku catatan. Ia tidak menambah, mengubah,");
        System.out.println("atau menghapus data, dan tidak membuat tabel apa pun.");

        Connection c = bukaBacaSaja();
        if (c == null) {
            return 0;
        }
        try {
            List<Rental> rentals = daftarRental(c);
            List<Truck> trucks = daftarTruk(c);
            Set<Integer> trukDipakai = idTrukDipakai(c);
            Set<Integer> rentalDipakai = idRentalDipakai(c);
            int jumlahTransaksi = hitung(c, "SELECT COUNT(*) FROM transaksi");
            int jumlahBaris = hitung(c, "SELECT COUNT(*) FROM transaksi_detail");

            if (rentals.isEmpty() && trucks.isEmpty() && jumlahTransaksi == 0) {
                System.out.println();
                System.out.println("!!! DATABASE INI KOSONG !!!");
                System.out.println("Tidak ada satu pun rental, truk, atau transaksi di dalamnya.");
                System.out.println("Kalau kamu sudah pernah mencatat pengiriman, berarti yang diperiksa");
                System.out.println("BUKAN database yang biasa kamu pakai - periksa dulu alamat database");
                System.out.println("di baris paling atas, dan berkas kaspe.properties kalau kamu memakai");
                System.out.println("MySQL. Hasil di bawah ini tidak berarti apa-apa.");
            }

            catatanRentalTakDipakai(rentals, rentalDipakai);
            catatanTrukTakDipakai(trucks, trukDipakai);
            periksaTrukTanpaPemilik(trucks);
            periksaBarisTanpaPlat(c);
            periksaTransaksiKosong(c);
            periksaNamaKembar(rentals);
            ringkasan(rentals.size(), trucks.size(), jumlahTransaksi, jumlahBaris, c);
        } finally {
            c.close();
        }

        System.out.println();
        if (jumlahMasalah == 0) {
            System.out.println("=== HASIL: tidak ditemukan tanda kerusakan data ===");
        } else {
            System.out.println("=== HASIL: ditemukan " + jumlahMasalah
                    + " masalah — rincian dan penjelasannya di atas ===");
        }
        return jumlahMasalah;
    }

    // ================= koneksi =================

    /**
     * Buka koneksi baca-saja TANPA menjalankan pembuatan tabel.
     *
     * <p>Mengembalikan {@code null} kalau database-nya tidak ada atau tidak bisa dibuka;
     * alasannya sudah dicetak.
     */
    private Connection bukaBacaSaja() {
        String url = Db.infoUrl();
        boolean h2 = Db.isH2();
        if (h2) {
            // IFEXISTS=TRUE membuat H2 MENOLAK membuka database yang belum ada, alih-alih
            // membuatnya. Tanpa ini, alat pemeriksa akan membuat berkas database kosong
            // lalu melaporkan "tidak ada tanda kerusakan" untuk database yang salah.
            url = url + (url.indexOf(';') >= 0 ? ";" : ";") + "IFEXISTS=TRUE";
        }
        try {
            Class.forName("org.h2.Driver");
            return DriverManager.getConnection(url, Db.infoUser(), Db.infoPass());
        } catch (Exception e) {
            System.out.println();
            if (h2 && !berkasH2Ada(Db.infoUrl())) {
                System.out.println("=== BERHENTI: database belum ada ===");
                System.out.println("Berkas database yang dituju tidak ditemukan:");
                System.out.println("  " + berkasH2(Db.infoUrl()));
                System.out.println("Tidak ada yang diperiksa, dan tidak ada berkas yang dibuat.");
                System.out.println("Jalankan aplikasinya dulu minimal sekali supaya databasenya terbentuk.");
            } else {
                System.out.println("=== BERHENTI: database tidak bisa dibuka ===");
                System.out.println(e.getMessage());
                System.out.println();
                System.out.println("Kalau aplikasinya sedang terbuka, tutup dulu - database H2 hanya");
                System.out.println("boleh dibuka satu program sekaligus. Kalau kamu memakai MySQL,");
                System.out.println("pastikan servernya hidup dan isi kaspe.properties sudah benar.");
            }
            return null;
        }
    }

    /** Jalur berkas H2 (.mv.db) menurut alamatnya, atau null untuk mem:/tcp:. */
    private static String berkasH2(String url) {
        String sisa = url.substring("jdbc:h2:".length());
        int titikKoma = sisa.indexOf(';');
        if (titikKoma >= 0) {
            sisa = sisa.substring(0, titikKoma);
        }
        if (sisa.startsWith("mem:") || sisa.startsWith("tcp:") || sisa.isEmpty()) {
            return null;
        }
        return sisa + ".mv.db";
    }

    private static boolean berkasH2Ada(String url) {
        String jalur = berkasH2(url);
        return jalur != null && new File(jalur).exists();
    }

    // ================= pemeriksaan =================

    /**
     * Rental yang belum dipakai transaksi mana pun — CATATAN, bukan masalah.
     *
     * <p>Pemilik yang baru didaftarkan dan memang belum ada pengirimannya akan masuk ke
     * sini, dan itu wajar. Menghitungnya sebagai masalah lalu menyarankan penghapusan
     * bisa membuat operator menghapus pemilik yang sah.
     */
    private void catatanRentalTakDipakai(List<Rental> rentals, Set<Integer> dipakai) {
        judul("1. Rental yang belum punya pengiriman (catatan, bukan masalah)");
        List<Rental> belum = new ArrayList<>();
        for (Rental r : rentals) {
            if (!dipakai.contains(r.getRentalId())) {
                belum.add(r);
            }
        }
        if (belum.isEmpty()) {
            System.out.println("Setiap rental sudah punya pengiriman.");
        } else {
            System.out.println("Ada " + belum.size() + " rental yang belum punya pengiriman:");
            for (Rental r : belum) {
                System.out.println("  - \"" + r.getRentalName() + "\"");
            }
            System.out.println("Ini WAJAR kalau pemiliknya baru didaftarkan, atau truknya memang");
            System.out.println("belum pernah mengirim. Tidak perlu dihapus. Baru patut dicurigai");
            System.out.println("kalau namanya mirip pemilik lain - bandingkan dengan bagian 6.");
        }
    }

    /** Truk yang belum dipakai transaksi mana pun — CATATAN, bukan masalah. */
    private void catatanTrukTakDipakai(List<Truck> trucks, Set<Integer> dipakai) {
        judul("2. Truk yang belum punya pengiriman (catatan, bukan masalah)");
        List<Truck> belum = new ArrayList<>();
        for (Truck t : trucks) {
            if (!dipakai.contains(t.getTruckId())) {
                belum.add(t);
            }
        }
        if (belum.isEmpty()) {
            System.out.println("Setiap truk sudah punya pengiriman.");
        } else {
            System.out.println("Ada " + belum.size() + " truk yang belum punya pengiriman:");
            for (Truck t : belum) {
                System.out.println("  - plat " + t.getPlate());
            }
            System.out.println("Ini WAJAR kalau truknya baru didaftarkan. Tidak perlu dihapus.");
            System.out.println("Baru patut dicurigai kalau platnya mirip truk lain (salah ketik).");
        }
    }

    private void periksaTrukTanpaPemilik(List<Truck> trucks) {
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
    }

    private void periksaBarisTanpaPlat(Connection c) throws SQLException {
        judul("4. Baris buku yang kehilangan plat truknya");
        List<String> pesan = new ArrayList<>();
        String sql = "SELECT t.tanggal, d.jumlah_uang FROM transaksi_detail d "
                + "JOIN transaksi t ON t.id_transaksi = d.id_transaksi "
                + "WHERE d.id_truk IS NULL ORDER BY t.tanggal, d.id_detail";
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                LocalDate tanggal = rs.getDate("tanggal") == null
                        ? null : rs.getDate("tanggal").toLocalDate();
                BigDecimal uang = rs.getBigDecimal("jumlah_uang");
                pesan.add("  - tanggal " + Dates.format(tanggal) + ", jumlah uang Rp "
                        + Calculator.formatCurrency(uang));
            }
        }
        if (pesan.isEmpty()) {
            System.out.println("Aman: semua baris buku punya plat truk.");
        } else {
            System.out.println("Ditemukan " + pesan.size() + " baris tanpa plat:");
            for (String p : pesan) {
                System.out.println(p);
            }
            System.out.println("Artinya: baris lama ini menampilkan kolom plat kosong, karena truknya");
            System.out.println("sudah dihapus dari daftar truk. Uangnya tetap terhitung di total,");
            System.out.println("tetapi tidak bisa dilacak per pemilik. Ini tidak bisa diperbaiki dari");
            System.out.println("aplikasi; barisnya hanya berguna sebagai arsip.");
            jumlahMasalah += pesan.size();
        }
    }

    private void periksaTransaksiKosong(Connection c) throws SQLException {
        judul("5. Transaksi (tanggal) yang tidak punya satu pun baris");
        List<String> kosong = new ArrayList<>();
        String sql = "SELECT t.tanggal FROM transaksi t "
                + "LEFT JOIN transaksi_detail d ON d.id_transaksi = t.id_transaksi "
                + "WHERE d.id_detail IS NULL ORDER BY t.tanggal";
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                LocalDate tanggal = rs.getDate("tanggal") == null
                        ? null : rs.getDate("tanggal").toLocalDate();
                kosong.add("  - tanggal " + Dates.format(tanggal));
            }
        }
        if (kosong.isEmpty()) {
            System.out.println("Aman: setiap transaksi punya paling tidak satu baris.");
        } else {
            System.out.println("Ditemukan " + kosong.size() + " transaksi kosong:");
            for (String t : kosong) {
                System.out.println(t);
            }
            System.out.println("Artinya: ada catatan tanggal yang tidak berisi satu baris pun. Ini tidak");
            System.out.println("mengganggu hitungan uang (kosong tidak berarti apa-apa di laporan),");
            System.out.println("tetapi tidak seharusnya ada dan menandakan pernah terjadi simpan");
            System.out.println("yang batal di tengah jalan.");
            jumlahMasalah += kosong.size();
        }
    }

    private void periksaNamaKembar(List<Rental> rentals) {
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
    }

    private void ringkasan(int jumlahRental, int jumlahTruk, int jumlahTransaksi,
                           int jumlahBaris, Connection c) throws SQLException {
        judul("7. Ringkasan isi data");
        BigDecimal totalUang = BigDecimal.ZERO;
        String sql = "SELECT jumlah_uang FROM transaksi_detail";
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                BigDecimal uang = rs.getBigDecimal("jumlah_uang");
                if (uang != null) {
                    totalUang = totalUang.add(uang);
                }
            }
        }
        System.out.println("Jumlah rental         : " + jumlahRental);
        System.out.println("Jumlah truk           : " + jumlahTruk);
        System.out.println("Jumlah transaksi      : " + jumlahTransaksi);
        System.out.println("Jumlah baris buku     : " + jumlahBaris);
        System.out.println("Total uang seluruhnya : Rp " + Calculator.formatCurrency(totalUang));
    }

    // ================= pembantu =================

    private void judul(String teks) {
        System.out.println();
        System.out.println("--- " + teks + " ---");
    }

    private static List<Rental> daftarRental(Connection c) throws SQLException {
        List<Rental> hasil = new ArrayList<>();
        String sql = "SELECT id_rental, nama_rental FROM rental ORDER BY nama_rental";
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Rental r = new Rental();
                r.setRentalId(rs.getInt("id_rental"));
                r.setRentalName(rs.getString("nama_rental"));
                hasil.add(r);
            }
        }
        return hasil;
    }

    private static List<Truck> daftarTruk(Connection c) throws SQLException {
        List<Truck> hasil = new ArrayList<>();
        String sql = "SELECT id_truk, plat, id_rental FROM truk ORDER BY plat";
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Truck t = new Truck();
                t.setTruckId(rs.getInt("id_truk"));
                t.setPlate(rs.getString("plat"));
                int idRental = rs.getInt("id_rental");
                t.setRentalId(rs.wasNull() ? null : idRental);
                hasil.add(t);
            }
        }
        return hasil;
    }

    private static Set<Integer> idTrukDipakai(Connection c) throws SQLException {
        Set<Integer> hasil = new HashSet<>();
        String sql = "SELECT DISTINCT id_truk FROM transaksi_detail WHERE id_truk IS NOT NULL";
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                hasil.add(rs.getInt(1));
            }
        }
        return hasil;
    }

    private static Set<Integer> idRentalDipakai(Connection c) throws SQLException {
        Set<Integer> hasil = new HashSet<>();
        String sql = "SELECT DISTINCT tr.id_rental FROM transaksi_detail d "
                + "JOIN truk tr ON tr.id_truk = d.id_truk WHERE tr.id_rental IS NOT NULL";
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                hasil.add(rs.getInt(1));
            }
        }
        return hasil;
    }

    private static int hitung(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
