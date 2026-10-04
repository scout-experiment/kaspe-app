package kaspe.dao;

import kaspe.Db;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Cadangkan seluruh database bawaan ke satu file zip.
 *
 * <p>Memakai perintah H2 {@code BACKUP TO}, bukan menyalin file {@code .mv.db}
 * dengan java.nio: file itu sedang dipakai dan terus ditulis aplikasi, jadi
 * salinan yang diambil diam-diam bisa setengah jadi. {@code BACKUP TO}
 * dikerjakan H2 sendiri lewat koneksi yang sudah ada, dan hasilnya konsisten
 * walau database sedang terbuka.
 *
 * <p>Hanya berlaku untuk database H2 yang tersimpan sebagai file. Kalau
 * aplikasi diarahkan ke MySQL/MariaDB, cadangan adalah urusan pengelola server
 * database-nya; tombol aplikasi ini tidak berpura-pura bisa melakukannya.
 */
public class BackupDao {

    /**
     * Buat cadangan database sekarang juga.
     *
     * @return file cadangan yang barusan ditulis; namanya berstempel waktu
     *         supaya cadangan lama tidak tertimpa
     * @throws SQLException kalau database yang dipakai bukan H2 file
     *         (mis. MySQL atau in-memory), atau H2 gagal menulis cadangannya
     */
    public static File cadangkan() throws SQLException {
        if (!Db.isH2()) {
            throw new SQLException("Cadangan otomatis hanya untuk database bawaan aplikasi ini "
                    + "(H2) yang tersimpan sebagai file di komputer Anda.\n\n"
                    + "Saat ini aplikasi memakai MySQL/MariaDB, jadi cadangannya urusan "
                    + "pengelola server database-nya, bukan aplikasi ini.");
        }
        if (Db.infoUrl().contains(":mem:")) {
            // H2 sendiri menolaknya ("Database is not persistent"); diterjemahkan
            // di sini supaya pesannya bisa dibaca, bukan galat mentah dari H2.
            throw new SQLException("Database in-memory tidak punya file yang bisa dicadangkan.");
        }
        File tujuan = fileTujuan();
        String sql = "BACKUP TO '" + tujuan.getAbsolutePath().replace("'", "''") + "'";
        try (Connection c = Db.get(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
        return tujuan;
    }

    /**
     * File cadangan yang akan ditulis: folder {@code cadangan} di samping file
     * databasenya sendiri (bawaannya {@code ${user.home}/kaspe/cadangan}),
     * dibuat dulu kalau belum ada.
     */
    private static File fileTujuan() {
        String path = Db.infoUrl().substring("jdbc:h2:".length());
        int end = path.indexOf(';');
        if (end >= 0) {
            path = path.substring(0, end);
        }
        if (path.startsWith("file:")) {
            path = path.substring("file:".length());
        }
        File db = new File(path).getAbsoluteFile();
        File folder = new File(db.getParentFile() == null ? new File(".") : db.getParentFile(),
                "cadangan");
        // H2 juga bisa membuat foldernya sendiri, tapi dibuat di sini supaya
        // letaknya jelas tanpa bergantung pada perilaku H2.
        String stempel = new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(new Date());
        File tujuan = new File(folder, db.getName() + "-" + stempel + ".zip");
        // Cadangan tidak boleh menimpa cadangan sebelumnya diam-diam. Stempel
        // waktu sudah membuatnya nyaris mustahil, tapi kalau operator menjalankan
        // dua kali dalam detik yang sama, nama berikutnya diberi nomor.
        for (int i = 2; tujuan.exists(); i++) {
            tujuan = new File(folder, db.getName() + "-" + stempel + "-" + i + ".zip");
        }
        return tujuan;
    }
}
