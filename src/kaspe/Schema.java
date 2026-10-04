package kaspe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Pembuat tabel otomatis.
 *
 * <p>Skema database disimpan di dalam aplikasi ({@code kaspe/schema.sql}), lalu dijalankan
 * sendiri saat pertama kali aplikasi memakai database yang masih kosong. Jadi pengguna tidak
 * perlu memasang database lebih dulu, cukup jalankan aplikasi.
 *
 * <p>Seluruh isi skema boleh dijalankan berulang dengan aman.
 */
public final class Schema {

    /** Nama file skema di dalam aplikasi. */
    private static final String RESOURCE = "/kaspe/schema.sql";

    private Schema() {
    }

    /**
     * Pastikan seluruh tabel sudah ada.
     * Dipanggil otomatis oleh {@link Db} sekali untuk setiap database yang dipakai.
     */
    public static void ensure(Connection c) throws SQLException {
        run(c);
        dropObsoleteColumns(c);
        splitSharedHeaders(c);
    }

    /**
     * Buang kolom yang sudah tidak dipakai lagi dari database yang sudah ada.
     *
     * <p>Tabel dibuat dengan {@code CREATE TABLE IF NOT EXISTS}, jadi perubahan pada file
     * skema hanya berlaku untuk database yang benar-benar baru. Database yang sudah berisi
     * data akan menyimpan terus kolom yang sudah dibuang dari skema — beserta isinya yang
     * sudah basi — tanpa cara apa pun untuk membersihkannya dari dalam aplikasi.
     *
     * <p>Karena itu kolom yang sudah tidak dipakai dibuang di sini. Caranya sengaja dua
     * langkah: ditanya dulu lewat keterangan database apakah kolomnya memang ada, baru
     * dibuang. Perintah {@code DROP COLUMN IF EXISTS} tidak dipakai karena hanya dimengerti
     * sebagian merek database, sedangkan aplikasi ini harus jalan di H2 maupun MySQL.
     */
    private static void dropObsoleteColumns(Connection c) throws SQLException {
        dropColumnIfExists(c, "rental", "no_hp");
        dropColumnIfExists(c, "rental", "keterangan");
        // Nomor nota dibuang dari skema. Kolomnya punya batasan unik dan wajib diisi,
        // jadi database lama yang masih menyimpannya akan menolak setiap transaksi baru
        // yang ditulis tanpa nomor nota — bukan sekadar menyisakan kolom kosong.
        dropColumnIfExists(c, "transaksi", "no_transaksi");
    }

    /** Buang satu kolom kalau memang ada. Aman dipanggil berulang. */
    private static void dropColumnIfExists(Connection c, String table, String column) throws SQLException {
        if (!tableExists(c, table) || !columnExists(c, table, column)) {
            return;
        }
        try (Statement s = c.createStatement()) {
            s.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
        }
    }

    /**
     * Pecah header lama yang masih dipakai bersama beberapa detail.
     *
     * <p>Pada database lama satu header bisa memiliki banyak detail karena satu pengiriman
     * dulu dicatat sebagai beberapa baris di bawah satu tanggal. Bentuk itu membuat satu
     * catatan tidak berdiri sendiri: mengubah tanggal satu pengiriman diam-diam ikut
     * mengubah tanggal pengiriman lain yang kebetulan sehari (tanggalnya disimpan di header
     * bersama), dan menghapus satu pengiriman bisa menyeret pengiriman lain ikut terhapus
     * lewat {@code ON DELETE CASCADE}. Karena itu setiap header yang memiliki lebih dari
     * satu detail dipecah: detail dengan {@code id_detail} terkecil tetap tinggal di header
     * asli, sisanya dipindah ke header baru yang menyalin {@code tanggal} dan
     * {@code dibuat_pada} dari header asli — bukan membiarkan default
     * {@code CURRENT_TIMESTAMP} — supaya waktu pencatatan aslinya tidak hilang. Semuanya
     * dikerjakan dalam satu transaksi yang diperiksa ulang sebelum di-commit, jadi aplikasi
     * menolak menyala daripada jalan di atas data yang setengah termigrasi.
     */
    private static void splitSharedHeaders(Connection c) throws SQLException {
        // Deteksi dulu tanpa menulis apa pun. Database yang baru atau sudah bersih
        // tidak diganggu sama sekali — tidak ada transaksi, tidak ada tulisan.
        List<Integer> headerBercabang = new ArrayList<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT id_transaksi FROM transaksi_detail"
                     + " GROUP BY id_transaksi HAVING COUNT(*) > 1")) {
            while (rs.next()) {
                headerBercabang.add(rs.getInt(1));
            }
        }
        if (headerBercabang.isEmpty()) {
            return;
        }

        // Patokan sebelum migrasi, untuk membandingkan hasilnya nanti.
        // SUM menghasilkan NULL kalau tabelnya kosong, jadi totalnya boleh null.
        long jumlahDetailSebelum;
        BigDecimal totalUangSebelum;
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT COUNT(*), SUM(jumlah_uang) FROM transaksi_detail")) {
            rs.next();
            jumlahDetailSebelum = rs.getLong(1);
            totalUangSebelum = rs.getBigDecimal(2);
        }

        boolean autoCommitLama = c.getAutoCommit();
        // Hanya kalau transaksinya sudah ditutup rapi (commit atau rollback berhasil)
        // autocommit boleh dikembalikan. Kalau rollback gagal, transaksinya masih
        // terbuka — setAutoCommit di titik itu justru meng-commit sisanya.
        boolean selesai = false;
        try {
            c.setAutoCommit(false);
            try (PreparedStatement ambilHeader = c.prepareStatement(
                         "SELECT tanggal, dibuat_pada FROM transaksi WHERE id_transaksi = ?");
                 PreparedStatement ambilDetail = c.prepareStatement(
                         "SELECT id_detail FROM transaksi_detail WHERE id_transaksi = ?"
                         + " ORDER BY id_detail");
                 PreparedStatement buatHeader = c.prepareStatement(
                         "INSERT INTO transaksi (tanggal, dibuat_pada) VALUES (?, ?)",
                         Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement pindahDetail = c.prepareStatement(
                         "UPDATE transaksi_detail SET id_transaksi = ? WHERE id_detail = ?")) {
                for (int idHeader : headerBercabang) {
                    ambilHeader.setInt(1, idHeader);
                    Date tanggal;
                    Timestamp dibuatPada;
                    try (ResultSet rs = ambilHeader.executeQuery()) {
                        rs.next();
                        tanggal = rs.getDate(1);
                        dibuatPada = rs.getTimestamp(2);
                    }
                    List<Integer> detail = new ArrayList<>();
                    ambilDetail.setInt(1, idHeader);
                    try (ResultSet rs = ambilDetail.executeQuery()) {
                        while (rs.next()) {
                            detail.add(rs.getInt(1));
                        }
                    }
                    // Detail pertama (id_detail terkecil) tetap tinggal di header asli;
                    // sisanya pindah ke header baru masing-masing.
                    for (int i = 1; i < detail.size(); i++) {
                        buatHeader.setDate(1, tanggal);
                        buatHeader.setTimestamp(2, dibuatPada);
                        buatHeader.executeUpdate();
                        int idHeaderBaru;
                        try (ResultSet k = buatHeader.getGeneratedKeys()) {
                            k.next();
                            idHeaderBaru = k.getInt(1);
                        }
                        pindahDetail.setInt(1, idHeaderBaru);
                        pindahDetail.setInt(2, detail.get(i));
                        pindahDetail.executeUpdate();
                    }
                }
            }

            // Periksa ulang hasilnya sebelum di-commit. Ada satu saja yang gagal
            // berarti migrasi cacat: rollback dan aplikasi menolak menyala.
            try (Statement s = c.createStatement()) {
                long jumlahDetailSesudah;
                BigDecimal totalUangSesudah;
                try (ResultSet rs = s.executeQuery(
                        "SELECT COUNT(*), SUM(jumlah_uang) FROM transaksi_detail")) {
                    rs.next();
                    jumlahDetailSesudah = rs.getLong(1);
                    totalUangSesudah = rs.getBigDecimal(2);
                }
                if (jumlahDetailSesudah != jumlahDetailSebelum) {
                    throw new SQLException("Migrasi gagal: jumlah baris transaksi_detail berubah dari "
                            + jumlahDetailSebelum + " menjadi " + jumlahDetailSesudah);
                }
                if (totalUangSebelum == null ? totalUangSesudah != null
                        : totalUangSesudah == null || totalUangSesudah.compareTo(totalUangSebelum) != 0) {
                    throw new SQLException("Migrasi gagal: total jumlah_uang berubah dari "
                            + totalUangSebelum + " menjadi " + totalUangSesudah);
                }
                long cabang;
                try (ResultSet rs = s.executeQuery(
                        "SELECT COUNT(*) FROM (SELECT id_transaksi FROM transaksi_detail"
                        + " GROUP BY id_transaksi HAVING COUNT(*) > 1) x")) {
                    rs.next();
                    cabang = rs.getLong(1);
                }
                if (cabang > 0) {
                    throw new SQLException(
                            "Migrasi gagal: masih ada header dengan lebih dari satu detail");
                }
                long yatim;
                try (ResultSet rs = s.executeQuery(
                        "SELECT COUNT(*) FROM transaksi_detail d"
                        + " LEFT JOIN transaksi t ON t.id_transaksi = d.id_transaksi"
                        + " WHERE t.id_transaksi IS NULL")) {
                    rs.next();
                    yatim = rs.getLong(1);
                }
                if (yatim > 0) {
                    throw new SQLException("Migrasi gagal: masih ada detail tanpa header");
                }
            }

            c.commit();
            selesai = true;
        } catch (SQLException | RuntimeException e) {
            try {
                c.rollback();
                selesai = true;
            } catch (SQLException rb) {
                // Kegagalan rollback tidak boleh menenggelamkan galat aslinya.
                e.addSuppressed(rb);
            }
            throw e;
        } finally {
            if (selesai) {
                c.setAutoCommit(autoCommitLama);
            }
        }
    }

    /** Apakah satu kolom ada di dalam tabel tertentu. */
    public static boolean columnExists(Connection c, String table, String column) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getColumns(c.getCatalog(), null, "%", "%")) {
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))
                        && column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Jalankan seluruh perintah di file skema. Semuanya berbentuk
     * {@code CREATE TABLE IF NOT EXISTS} atau {@code CREATE OR REPLACE VIEW}, jadi aman
     * dijalankan berkali-kali — termasuk kalau aplikasi sempat tertutup di tengah proses.
     */
    private static void run(Connection c) throws SQLException {
        for (String sql : readStatements()) {
            try (Statement s = c.createStatement()) {
                s.execute(sql);
            }
        }
    }

    /**
     * Apakah tabel sudah ada. Ditanya lewat keterangan database, bukan dengan mencoba
     * query — supaya tidak meninggalkan catatan kesalahan di file log.
     */
    public static boolean tableExists(Connection c, String table) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getTables(c.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Pecah file skema menjadi perintah-perintah terpisah, membuang baris komentar.
     * Dipakai juga oleh uji otomatis supaya yang diuji sama persis dengan yang
     * dijalankan aplikasi saat membuat tabelnya sendiri.
     */
    public static List<String> readStatements() throws SQLException {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : read().split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                statements.add(current.toString());
                current.setLength(0);
            }
        }
        if (statements.isEmpty()) {
            throw new SQLException("File skema " + RESOURCE + " tidak berisi perintah apa pun.");
        }
        return statements;
    }

    /** Isi file skema, dibaca dari dalam aplikasi. */
    static String read() throws SQLException {
        try (InputStream in = Schema.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new SQLException("File skema " + RESOURCE + " tidak ada di dalam aplikasi.");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Gagal membaca file skema: " + e.getMessage(), e);
        }
    }
}
