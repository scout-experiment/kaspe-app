package kaspe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
