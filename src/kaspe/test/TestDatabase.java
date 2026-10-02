package kaspe.test;

import kaspe.Schema;

import java.math.BigDecimal;
import java.sql.*;

/**
 * Uji integrasi database: jalankan skema aplikasi, isi data ASLI dari buku, lalu
 * pastikan hitungan yang tersimpan sama dengan yang tertulis di buku.
 *
 * Skema dibaca dari dalam aplikasi (kaspe/schema.sql) - file yang sama dipakai
 * aplikasi saat membuat tabelnya sendiri. Jadi yang diuji di sini sama persis
 * dengan yang dijalankan di komputer pengguna.
 *
 * Jalankan:
 *   java -cp build:lib/h2-2.1.214.jar kaspe.test.TestDatabase
 */
public class TestDatabase {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=== UJI DATABASE (schema.sql + data buku asli) ===\n");

        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:mem:kaspe;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

        checkFreshDatabase();
        checkObsoleteColumnsDropped();

        try (Connection c = DriverManager.getConnection(url, "sa", "")) {

            System.out.println("2. Jalankan schema.sql ...");
            int n = runSchema(c);
            System.out.println("   " + n + " statement dieksekusi. OK\n");

            System.out.println("3. Isi master data ...");
            try (Statement s = c.createStatement()) {
                s.executeUpdate("INSERT INTO rental (nama_rental) VALUES ('Rental A'), ('Rental B')");
                s.executeUpdate("INSERT INTO truk (plat, id_rental) VALUES ('KB 8234 HD',1),('BE 8009 CF',1),('BE 8570 CF',2),('BE 8437 CF',2)");
                s.executeUpdate("INSERT INTO transaksi (tanggal) VALUES ('2026-01-19')");
            }
            System.out.println("   OK\n");

            System.out.println("   Isi 4 baris transaksi (data buku) ...");
            Object[][] row = {
                // plat, lapak, pabrik, refraksi, harga, bb_buku, ju_buku
                {"KB 8234 HD", 7200, 7050, 15, 1150, 5990, 6888500L},
                {"BE 8009 CF", 6000, 5970, 15, 1150, 5070, 5830500L},
                {"BE 8009 CF", 6280, 6150, 15, 1150, 5225, 6008750L},
                {"BE 8570 CF", 6480, 6380, 15, 1150, 5420, 6233000L},
            };
            for (Object[] b : row) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO transaksi_detail (id_transaksi,id_truk,bobot_lapak,bobot_pabrik," +
                        "refraksi_persen,berat_bersih,tanggal_lunas,harga,jumlah_uang) VALUES (1,?,?,?,?,?,?,?,?)")) {
                    int truckId = truckId(c, (String) b[0]);
                    ps.setInt(1, truckId);
                    ps.setBigDecimal(2, bd((Integer) b[1]));
                    ps.setBigDecimal(3, bd((Integer) b[2]));
                    ps.setBigDecimal(4, bd((Integer) b[3]));
                    ps.setBigDecimal(5, bd((Integer) b[5]));   // berat bersih (sudah dihitung Calculator)
                    ps.setDate(6, Date.valueOf("2026-01-19"));
                    ps.setBigDecimal(7, bd((Integer) b[4]));   // harga
                    ps.setBigDecimal(8, bd(((Long) b[6]).intValue()));
                    ps.executeUpdate();
                }
            }
            System.out.println("   OK\n");

            System.out.println("4. Baca balik lewat view v_transaksi, bandingkan dengan buku ...");
            for (Object[] b : row) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT * FROM v_transaksi WHERE plat = ? AND bobot_pabrik = ?")) {
                    ps.setString(1, (String) b[0]);
                    ps.setBigDecimal(2, bd((Integer) b[2]));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            System.out.println("   SALAH: baris tidak ditemukan " + b[0]);
                            failed++;
                            continue;
                        }
                        BigDecimal netWeight = rs.getBigDecimal("berat_bersih");
                        BigDecimal amount = rs.getBigDecimal("jumlah_uang");
                        boolean ok = netWeight.compareTo(bd((Integer) b[5])) == 0
                                  && amount.compareTo(bd(((Long) b[6]).intValue())) == 0;
                        System.out.printf("   %-12s bb=%s(%d) uang=%s(%d) %s%n",
                                b[0], netWeight.toPlainString(), b[5],
                                amount.toPlainString(), b[6], ok ? "OK" : "SALAH");
                        record(ok, "view " + b[0]);
                    }
                }
            }

            System.out.println("\n5. Cek foreign key ...");
            try (Statement s = c.createStatement()) {
                s.executeUpdate("INSERT INTO transaksi_detail (id_transaksi,id_truk,bobot_lapak,bobot_pabrik," +
                        "refraksi_persen,berat_bersih,harga,jumlah_uang) VALUES (999,1,100,100,15,85,1150,97750)");
                System.out.println("   SALAH: id_transaksi 999 diterima, FK tidak jalan");
                failed++;
            } catch (SQLException e) {
                System.out.println("   OK: transaksi_id 999 ditolak (FK jalan)");
                passed++;
            }
        }

        System.out.println("\n=== HASIL: " + passed + " lulus, " + failed + " gagal ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static int truckId(Connection c, String plate) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id_truk FROM truk WHERE plat = ?")) {
            ps.setString(1, plate);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Janji utama aplikasi: tabel dibuat sendiri saat pertama kali dipakai.
     * Diuji di database kosong yang benar-benar baru, lalu dijalankan ulang
     * untuk memastikan pembuatannya aman diulang.
     */
    private static void checkFreshDatabase() throws Exception {
        System.out.println("1. Database baru: tabel dibuat sendiri ...");
        String url = "jdbc:h2:mem:kaspe-fresh;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            record(!Schema.tableExists(c, "transaksi"), "database baru belum punya tabel");

            Schema.ensure(c);

            record(Schema.tableExists(c, "rental"), "tabel rental terbuat");
            record(Schema.tableExists(c, "truk"), "tabel truk terbuat");
            record(Schema.tableExists(c, "transaksi"), "tabel transaksi terbuat");
            record(Schema.tableExists(c, "transaksi_detail"), "tabel transaksi_detail terbuat");

            // Dijalankan lagi: tidak boleh gagal, dan tabelnya tidak boleh bertambah.
            Schema.ensure(c);
            record(Schema.tableExists(c, "transaksi"), "dijalankan ulang tetap aman");
        }
        System.out.println();
    }

    /**
     * Database lama yang masih menyimpan kolom yang sudah dibuang harus dibersihkan.
     *
     * <p>Tabel dibuat dengan {@code CREATE TABLE IF NOT EXISTS}, jadi database yang sudah
     * ada tidak ikut berubah saat file skema diubah. Kalau tidak ada yang membersihkan,
     * kolom lama beserta isinya yang sudah basi akan tersimpan terus dan tidak ada cara
     * menghapusnya dari dalam aplikasi.
     *
     * <p>Diuji dengan membuat tabel bentuk lama lebih dulu, lalu menjalankan
     * {@link Schema#ensure} seperti yang dilakukan aplikasi saat dibuka.
     */
    private static void checkObsoleteColumnsDropped() throws Exception {
        System.out.println("1b. Database lama: kolom yang sudah dibuang ikut dibersihkan ...");
        String url = "jdbc:h2:mem:kaspe-lama;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            // Bentuk lama tabel rental, persis seperti sebelum kolomnya dibuang.
            try (Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE rental ("
                        + "id_rental INT AUTO_INCREMENT PRIMARY KEY,"
                        + "nama_rental VARCHAR(100) NOT NULL UNIQUE,"
                        + "no_hp VARCHAR(20) NULL,"
                        + "keterangan VARCHAR(255) NULL)");
                s.executeUpdate("INSERT INTO rental (nama_rental,no_hp,keterangan) "
                        + "VALUES ('Rental Lama','0812-0000-1111','keterangan basi')");
                // Bentuk lama tabel transaksi, lengkap dengan nomor nota yang wajib diisi.
                s.executeUpdate("CREATE TABLE transaksi ("
                        + "id_transaksi INT AUTO_INCREMENT PRIMARY KEY,"
                        + "no_transaksi VARCHAR(20) NOT NULL UNIQUE,"
                        + "tanggal DATE NOT NULL)");
                s.executeUpdate("INSERT INTO transaksi (no_transaksi,tanggal) "
                        + "VALUES ('7','2026-01-19')");
                // Dua tabel sisanya ikut dibuat karena view lama membacanya; tanpa itu
                // view-nya tidak bisa dipasang.
                s.executeUpdate("CREATE TABLE truk ("
                        + "id_truk INT AUTO_INCREMENT PRIMARY KEY,"
                        + "plat VARCHAR(20) NOT NULL UNIQUE,"
                        + "id_rental INT NULL)");
                s.executeUpdate("CREATE TABLE transaksi_detail ("
                        + "id_detail INT AUTO_INCREMENT PRIMARY KEY,"
                        + "id_transaksi INT NOT NULL,"
                        + "id_truk INT NULL,"
                        + "bobot_lapak DECIMAL(10,2) NOT NULL,"
                        + "bobot_pabrik DECIMAL(10,2) NOT NULL,"
                        + "refraksi_persen DECIMAL(5,2) NOT NULL,"
                        + "berat_bersih DECIMAL(10,2) NOT NULL,"
                        + "tanggal_lunas DATE NULL,"
                        + "harga DECIMAL(12,2) NOT NULL,"
                        + "jumlah_uang DECIMAL(15,2) NOT NULL)");
                // View lama yang membaca kolom nomor nota. Ikut dibuat di sini karena
                // database yang sudah dipakai memang punya view ini, dan urutan
                // penggantiannya penting: Schema.ensure menjalankan skema dulu (view
                // diganti versi baru yang tidak lagi memilih nomor nota), baru membuang
                // kolomnya. Urutan yang salah bisa membuat aplikasi gagal dibuka.
                s.executeUpdate("CREATE VIEW v_transaksi AS "
                        + "SELECT t.id_transaksi, t.no_transaksi, t.tanggal, d.id_detail, tr.plat, "
                        + "r.nama_rental, d.bobot_lapak, d.bobot_pabrik, d.refraksi_persen, "
                        + "d.berat_bersih, d.tanggal_lunas, d.harga, d.jumlah_uang, "
                        + "(d.bobot_lapak - d.bobot_pabrik) AS susut "
                        + "FROM transaksi_detail d "
                        + "JOIN transaksi t ON t.id_transaksi = d.id_transaksi "
                        + "LEFT JOIN truk tr ON tr.id_truk = d.id_truk "
                        + "LEFT JOIN rental r ON r.id_rental = tr.id_rental");
            }
            record(Schema.columnExists(c, "rental", "no_hp"), "database lama masih punya kolom no_hp");
            record(Schema.columnExists(c, "transaksi", "no_transaksi"),
                    "database lama masih punya kolom no_transaksi");

            Schema.ensure(c);

            record(!Schema.columnExists(c, "rental", "no_hp"), "kolom no_hp dibuang");
            record(!Schema.columnExists(c, "rental", "keterangan"), "kolom keterangan dibuang");
            // Nomor nota punya batasan wajib diisi dan unik. Kalau kolomnya tidak dibuang,
            // setiap transaksi baru yang ditulis tanpa nomor nota akan ditolak database.
            record(!Schema.columnExists(c, "transaksi", "no_transaksi"), "kolom no_transaksi dibuang");

            // Datanya tidak boleh ikut hilang - hanya kolomnya yang dibuang.
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT nama_rental FROM rental")) {
                rs.next();
                record("Rental Lama".equals(rs.getString(1)), "isinya tetap utuh");
            }
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM transaksi")) {
                rs.next();
                record(rs.getInt(1) == 1, "transaksi lama tetap utuh");
            }
            // Transaksi baru tanpa nomor nota harus bisa ditulis setelah pembersihan.
            try (Statement s = c.createStatement()) {
                s.executeUpdate("INSERT INTO transaksi (tanggal) VALUES ('2026-02-01')");
                record(true, "transaksi baru tanpa nomor nota diterima");
            }
            // View-nya harus tetap bisa dibaca setelah kolom nomor nota dibuang.
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM v_transaksi")) {
                rs.next();
                record(rs.getInt(1) == 0, "view v_transaksi tetap bisa dibaca");
            }

            // Dijalankan lagi: tidak boleh gagal karena kolomnya sudah tidak ada.
            Schema.ensure(c);
            record(true, "dijalankan ulang setelah pembersihan tetap aman");
        }
        System.out.println();
    }

    /**
     * Jalankan skema aplikasi. Skema dibaca dari dalam aplikasi (kaspe/schema.sql),
     * bukan dari file terpisah, supaya yang diuji sama persis dengan yang dipakai
     * aplikasi saat membuat tabelnya sendiri.
     */
    private static int runSchema(Connection c) throws SQLException {
        int n = 0;
        for (String sql : Schema.readStatements()) {
            try (Statement st = c.createStatement()) {
                st.execute(sql);
                n++;
            } catch (SQLException e) {
                System.out.println("   GAGAL: " + e.getMessage());
                System.out.println("   pada: " + sql.substring(0, Math.min(80, sql.length())).replace('\n', ' '));
                throw e;
            }
        }
        return n;
    }

    private static void record(boolean ok, String name) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }

    private static BigDecimal bd(int v) {
        return new BigDecimal(v);
    }
}
