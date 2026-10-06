package kaspe.dao;

import kaspe.Db;
import kaspe.Sandi;
import kaspe.model.Pengguna;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Operasi database untuk akun pengguna: masuk, daftar, tambah, ubah, hapus.
 *
 * <p>Nama pengguna diperiksa sendiri sebelum menulis, bukan menunggu batasan
 * UNIQUE database, dengan alasan yang sama seperti pada penambahan rental di
 * {@link MasterDao}: batasan itu membedakan besar-kecil huruf, jadi "Admin" dan
 * "admin" bisa hidup berdampingan kalau tidak dicek di sini.
 */
public class UserDao {

    /** Jumlah akun yang tercatat. Dipakai layar masuk untuk mengenali pemakaian pertama. */
    public int jumlah() throws SQLException {
        try (Connection c = Db.get(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM pengguna")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Seluruh akun menurut abjad namanya. Sandinya sengaja tidak ikut dibaca. */
    public List<Pengguna> list() throws SQLException {
        List<Pengguna> result = new ArrayList<>();
        try (Connection c = Db.get(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT id_pengguna, nama, peran FROM pengguna ORDER BY nama")) {
            while (rs.next()) {
                Pengguna p = new Pengguna();
                p.setId(rs.getInt("id_pengguna"));
                p.setNama(rs.getString("nama"));
                p.setPeran(rs.getString("peran"));
                result.add(p);
            }
        }
        return result;
    }

    /** Satu akun menurut namanya, atau null kalau tidak ada. */
    public Pengguna cari(String nama) throws SQLException {
        try (Connection c = Db.get()) {
            return cari(c, nama);
        }
    }

    /**
     * Tambah akun baru. Sandinya disandi dengan garam segar, jadi dua akun
     * bersandi sama tidak pernah tersimpan dengan hasil yang sama.
     */
    public void simpan(String nama, String sandi, String peran) throws SQLException {
        periksa(nama, sandi, peran);
        try (Connection c = Db.get()) {
            if (cari(c, nama) != null) {
                // Bukan diam: nama yang dipakai dua kali membuat operator mengira
                // akunnya gagal dibuat tanpa tahu sebabnya.
                throw new IllegalArgumentException(
                        "Nama pengguna \"" + nama.trim() + "\" sudah dipakai.");
            }
        }
        String salt = Sandi.saltBaru();
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO pengguna (nama, sandi_hash, sandi_salt, peran, dibuat)"
                        + " VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, nama.trim());
            ps.setString(2, Sandi.hash(sandi, salt));
            ps.setString(3, salt);
            ps.setString(4, peran);
            ps.setDate(5, java.sql.Date.valueOf(LocalDate.now()));
            ps.executeUpdate();
        }
    }

    /**
     * Ubah nama dan peran akun. {@code sandiBaru} null berarti sandinya tidak
     * disentuh; kalau diisi, sandinya diganti dengan garam baru.
     */
    public void ubah(int id, String nama, String peran, String sandiBaru) throws SQLException {
        periksa(nama, sandiBaru, peran);
        try (Connection c = Db.get()) {
            Pengguna lain = cari(c, nama);
            if (lain != null && lain.getId() != id) {
                throw new IllegalArgumentException(
                        "Nama pengguna \"" + nama.trim() + "\" sudah dipakai.");
            }
        }
        if (sandiBaru == null) {
            try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                    "UPDATE pengguna SET nama=?, peran=? WHERE id_pengguna=?")) {
                ps.setString(1, nama.trim());
                ps.setString(2, peran);
                ps.setInt(3, id);
                ps.executeUpdate();
            }
            return;
        }
        String salt = Sandi.saltBaru();
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "UPDATE pengguna SET nama=?, peran=?, sandi_hash=?, sandi_salt=?"
                        + " WHERE id_pengguna=?")) {
            ps.setString(1, nama.trim());
            ps.setString(2, peran);
            ps.setString(3, Sandi.hash(sandiBaru, salt));
            ps.setString(4, salt);
            ps.setInt(5, id);
            ps.executeUpdate();
        }
    }

    /** Hapus akun. */
    public void hapus(int id) throws SQLException {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM pengguna WHERE id_pengguna=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Coba masuk: akunnya kalau sandinya cocok, null kalau tidak — termasuk kalau
     * namanya tidak dikenal. Dua keadaan itu sengaja tidak dibedakan supaya pesan
     * di layar tidak mengungkapkan nama mana yang benar-benar tercatat.
     */
    public Pengguna masuk(String nama, String sandi) throws SQLException {
        if (nama == null || sandi == null) {
            return null;
        }
        try (Connection c = Db.get()) {
            Pengguna p = cari(c, nama);
            if (p == null) {
                return null;
            }
            return Sandi.cocok(sandi, p.getSandiSalt(), p.getSandiHash()) ? p : null;
        }
    }

    /**
     * Cari akun menurut namanya dengan koneksi milik pemanggil.
     *
     * <p>Pencocokannya di sini, bukan lewat {@code WHERE nama=?}, mengikuti pola
     * {@code cariIdRental} di {@link MasterDao}: ejaan yang berbeda besar-kecil
     * hurufnya tetap satu akun yang sama.
     */
    private Pengguna cari(Connection c, String nama) throws SQLException {
        if (nama == null || nama.trim().isEmpty()) {
            return null;
        }
        String kunci = nama.trim().toLowerCase(Locale.ROOT);
        Pengguna ketemu = null;
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT id_pengguna, nama, peran, sandi_hash, sandi_salt FROM pengguna")) {
            while (rs.next()) {
                String namaBaris = rs.getString("nama");
                if (namaBaris == null || !kunci.equals(namaBaris.trim().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                Pengguna p = baca(rs);
                if (ketemu == null || p.getId() < ketemu.getId()) {
                    ketemu = p;
                }
            }
        }
        return ketemu;
    }

    private static Pengguna baca(ResultSet rs) throws SQLException {
        Pengguna p = new Pengguna();
        p.setId(rs.getInt("id_pengguna"));
        p.setNama(rs.getString("nama"));
        p.setPeran(rs.getString("peran"));
        p.setSandiHash(rs.getString("sandi_hash"));
        p.setSandiSalt(rs.getString("sandi_salt"));
        return p;
    }

    /** Periksa isian sebelum menulis. {@code sandi} null berarti tidak sedang diganti. */
    private static void periksa(String nama, String sandi, String peran) {
        if (nama == null || nama.trim().isEmpty()) {
            throw new IllegalArgumentException("nama pengguna wajib diisi");
        }
        if (sandi != null && sandi.length() < 4) {
            throw new IllegalArgumentException("sandi minimal 4 karakter");
        }
        if (!Pengguna.ADMIN.equals(peran) && !Pengguna.USER.equals(peran)) {
            throw new IllegalArgumentException("peran tidak dikenal");
        }
    }
}
