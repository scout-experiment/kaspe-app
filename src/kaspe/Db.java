package kaspe;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Pengelola koneksi database.
 *
 * <p>Bawaannya memakai H2 dalam mode file: database ikut aplikasi, tidak perlu dipasang
 * lebih dulu, dan datanya tersimpan sebagai satu file di folder pengguna. Tabel dibuat
 * sendiri saat pertama kali dijalankan.
 *
 * <p>Kalau ingin dipakai beberapa komputer sekaligus, arahkan ke MySQL/MariaDB lewat file
 * {@code kaspe.properties} (lihat komentar di file itu). Databasenya pun dibuat sendiri
 * oleh aplikasi, jadi tidak ada langkah persiapan.
 */
public final class Db {

    /** Nama file pengaturan yang boleh ditaruh di sebelah jar untuk menimpa bawaan. */
    private static final String CONFIG_FILE = "kaspe.properties";

    private static String driver = "org.h2.Driver";
    // DB_CLOSE_DELAY=-1 menahan database tetap terbuka selama aplikasi hidup, sehingga
    // aplikasi kedua yang dibuka bersamaan langsung gagal dengan pesan yang jelas —
    // bukan diam-diam ikut menulis ke file yang sama.
    private static String url = "jdbc:h2:${user.home}/kaspe/db_kaspe;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static String user = "sa";
    private static String pass = "";
    private static boolean driverLoaded = false;

    /** Database yang tabelnya sudah dipastikan ada, supaya tidak dicek berulang. */
    private static String preparedUrl;
    /** Pesan penolakan setelan; null berarti setelan boleh dipakai. Diisi oleh {@link #load()}. */
    private static String configError;

    static {
        load();
    }

    private Db() {
    }

    private static void load() {
        Properties p = new Properties();
        // Didahulukan file di sebelah aplikasi, supaya pengguna hasil distribusi
        // bisa mengubah pengaturan tanpa membongkar jar. Kalau tidak ada,
        // dipakai pengaturan bawaan yang ikut di dalam aplikasi.
        File external = findExternalConfig();
        String sumber = null;
        boolean terbaca = false;
        if (external != null) {
            sumber = external.getAbsolutePath();
            try (InputStream in = new FileInputStream(external)) {
                p.load(in);
                terbaca = true;
            } catch (Exception e) {
                // berkasnya ada tetapi isinya tidak terbaca — ditolak di bawah
            }
        } else {
            try (InputStream in = Db.class.getResourceAsStream("/" + CONFIG_FILE)) {
                if (in != null) {
                    sumber = CONFIG_FILE + " yang terpasang di dalam aplikasi";
                    p.load(in);
                    terbaca = true;
                }
            } catch (Exception e) {
                // berkasnya ada tetapi isinya tidak terbaca — ditolak di bawah
            }
        }
        String dbUrl = p.getProperty("db.url");
        if (sumber != null && (dbUrl == null || dbUrl.trim().isEmpty())) {
            // Berkas yang ada tetapi tidak berkata letak databasenya berarti niat
            // penggunanya tidak diketahui. Menebak "H2 lokal" persis pemindahan
            // database diam-diam yang dilarang, jadi aplikasi menolak jalan.
            // Tidak melempar dari sini — pemuat kelas statis hanya akan
            // menerjemahkannya jadi ExceptionInInitializerError yang buruk
            // rupanya; penolakannya dibaca Main dan get().
            configError = "Setelan database tidak bisa dipakai:\n  " + sumber + "\n"
                    + (terbaca ? "Berkasnya ada tetapi tidak memuat db.url."
                               : "Berkasnya ada tetapi tidak bisa dibaca.")
                    + "\n\nPerbaiki berkas itu"
                    + (external != null
                            ? ", atau ganti namanya (mis. menjadi " + CONFIG_FILE
                                    + ".rusak) supaya aplikasi memakai database bawaan."
                            : " lalu pasang ulang aplikasinya, atau taruh " + CONFIG_FILE
                                    + " yang benar di folder sebelah aplikasi — berkas di "
                                    + "sebelah aplikasi selalu menimpa yang di dalam.");
            return;
        }
        driver = p.getProperty("db.driver", driver);
        url = expand(p.getProperty("db.url", url));
        user = p.getProperty("db.user", user);
        pass = p.getProperty("db.password", pass);
    }

    /**
     * Pesan penolakan setelan, atau null kalau setelan boleh dipakai.
     *
     * <p>Terisi kalau berkas {@code kaspe.properties} ada tetapi tidak menyebut
     * {@code db.url} — termasuk yang tidak terbaca, kosong, atau isinya hanya
     * komentar. Berkas yang tidak ada bukan masalah: pengaturan bawaan memang
     * dipakai untuk itu. {@code Main} memeriksanya sebelum jendela dibuat;
     * {@link #get()} juga menolak dengan pesan yang sama supaya jalan lain
     * (alat, uji) tidak bisa lolos.
     */
    public static String configError() {
        return configError;
    }

    /**
     * Cari {@code kaspe.properties} di luar aplikasi: pertama di folder tempat aplikasi
     * berada (supaya tetap ketemu walau dijalankan dari folder lain atau lewat pintasan),
     * lalu di folder kerja.
     *
     * @return file yang ada, atau null kalau tidak ketemu
     */
    private static File findExternalConfig() {
        File beside = new File(jarFolder(), CONFIG_FILE);
        if (beside.isFile()) {
            return beside;
        }
        File working = new File(CONFIG_FILE);
        return working.isFile() ? working : null;
    }

    /** Folder tempat aplikasi dijalankan; folder kerja kalau letaknya tidak bisa dipastikan. */
    private static File jarFolder() {
        try {
            File loc = new File(Db.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File folder = loc.isDirectory() ? loc : loc.getParentFile();
            if (folder != null) {
                return folder;
            }
        } catch (Exception e) {
            // jatuh ke folder kerja
        }
        return new File(System.getProperty("user.dir", "."));
    }

    /** Ganti nama folder bawaan seperti {@code ${user.home}} dengan folder pengguna sebenarnya. */
    private static String expand(String value) {
        return value.replace("${user.home}", System.getProperty("user.home"));
    }

    /** Ganti konfigurasi saat program jalan (dipakai uji otomatis). */
    public static void setConfiguration(String newDriver, String newUrl, String newUser, String newPass) {
        driver = newDriver;
        url = expand(newUrl);
        user = newUser;
        pass = newPass;
        driverLoaded = false;
        preparedUrl = null;
        // Setelan sudah ditunjuk langsung oleh pemanggil: penolakan berkas
        // kaspe.properties tidak berlaku lagi.
        configError = null;
    }

    private static void ensureDriver() {
        if (!driverLoaded) {
            try {
                Class.forName(driver);
                driverLoaded = true;
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(
                        "Driver JDBC tidak ditemukan: " + driver
                        + ".\nPastikan file jar-nya ada di folder lib dan masuk ke classpath.", e);
            }
        }
    }

    /**
     * Buka koneksi ke database. Saat pertama kali dipakai, database dan tabelnya dibuat
     * otomatis kalau memang belum ada.
     */
    public static Connection get() throws SQLException {
        if (configError != null) {
            // Menolak sebelum menyentuh apa pun: selama letak database diragukan,
            // jangan buat file H2, jangan buat tabel, jangan buka koneksi.
            throw new SQLException(configError);
        }
        ensureDriver();
        createDatabaseFolder();
        Connection c;
        try {
            c = DriverManager.getConnection(url, user, pass);
        } catch (SQLException e) {
            if (isH2()) {
                throw new SQLException(h2Help(e), e);
            }
            // Server MySQL bisa jadi belum punya databasenya. Coba buat dulu, lalu ulangi.
            if (createServerDatabase()) {
                c = DriverManager.getConnection(url, user, pass);
            } else {
                throw e;
            }
        }
        if (!url.equals(preparedUrl)) {
            Schema.ensure(c);
            preparedUrl = url;
        }
        return c;
    }

    /**
     * Buat database di server MySQL/MariaDB kalau memang belum ada.
     *
     * <p>Caranya: sambung ke server tanpa menyebut nama database, jalankan
     * {@code CREATE DATABASE IF NOT EXISTS}, lalu tutup. Tabelnya sendiri dibuat
     * oleh {@link Schema} setelah koneksi berhasil.
     *
     * @return true kalau berhasil, supaya koneksi bisa dicoba ulang
     */
    private static boolean createServerDatabase() {
        String name = databaseName();
        if (name == null) {
            return false;
        }
        int slash = url.indexOf('/', url.indexOf("//") + 2);
        int query = url.indexOf('?', slash);
        String base = url.substring(0, slash + 1) + (query >= 0 ? url.substring(query) : "");
        try (Connection c = DriverManager.getConnection(base, user, pass);
             Statement s = c.createStatement()) {
            s.executeUpdate("CREATE DATABASE IF NOT EXISTS " + name
                    + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /** Nama database pada URL server, mis. {@code db_kaspe}. Null kalau tidak ada. */
    private static String databaseName() {
        int slash = url.indexOf('/', url.indexOf("//") + 2);
        if (slash < 0) {
            return null;
        }
        int end = url.indexOf('?', slash);
        String name = end < 0 ? url.substring(slash + 1) : url.substring(slash + 1, end);
        return name.isEmpty() ? null : name;
    }

    /**
     * H2 menyimpan database sebagai file, jadi kegagalan yang paling sering terjadi bukan
     * soal tabel melainkan soal file: aplikasi sudah terbuka di jendela lain, atau
     * foldernya tidak bisa ditulis. Pesannya diterjemahkan supaya bisa ditindaklanjuti.
     */
    private static String h2Help(SQLException e) {
        String pesan = e.getMessage() == null ? "" : e.getMessage();
        if (pesan.contains("already in use") || pesan.contains("Locked")
                || pesan.contains("lock") || pesan.contains("in use")) {
            return "Aplikasi sepertinya sudah terbuka di jendela lain.\n\n"
                    + "Tutup dulu jendela yang itu, lalu jalankan lagi.";
        }
        if (pesan.contains("Permission denied") || pesan.contains("Access is denied")
                || pesan.contains("Read-only")) {
            return "Aplikasi tidak bisa menulis file datanya di:\n" + databaseFilePath() + "\n\n"
                    + "Pastikan folder itu bisa ditulis, atau ubah letaknya lewat file "
                    + CONFIG_FILE + " (db.url).";
        }
        return "Tidak bisa membuka file database di:\n" + databaseFilePath() + "\n\n" + pesan;
    }

    /** Letak file database H2 yang sedang dipakai. */
    private static String databaseFilePath() {
        if (!isH2()) {
            return url;
        }
        String path = url.substring("jdbc:h2:".length());
        int end = path.indexOf(';');
        if (end >= 0) {
            path = path.substring(0, end);
        }
        return path + ".mv.db";
    }

    /**
     * H2 menyimpan database sebagai file, dan foldernya harus sudah ada sebelum koneksi.
     * Database di server (MySQL) tidak butuh ini.
     */
    private static void createDatabaseFolder() {
        if (!isH2() || url.contains(":mem:")) {
            return;
        }
        String path = url.substring("jdbc:h2:".length());
        int end = path.indexOf(';');
        if (end >= 0) {
            path = path.substring(0, end);
        }
        File parent = new File(path).getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
    }

    /** Apakah aplikasi sedang memakai database H2 (bawaan), bukan server MySQL. */
    public static boolean isH2() {
        return url.startsWith("jdbc:h2:");
    }

    /** Keterangan database yang sedang dipakai, untuk ditampilkan saat ada masalah. */
    public static String infoUrl() {
        return url;
    }

    /**
     * Nama pengguna database yang sedang dipakai.
     *
     * <p>Disediakan supaya alat di luar aplikasi (mis. {@code tools/PeriksaData}) bisa
     * membuka koneksinya sendiri TANPA lewat {@link #get()}. Lewat {@code get()},
     * pemeriksaan yang seharusnya hanya membaca akan ikut menjalankan pembuatan tabel -
     * dan itu mengubah database yang sedang diperiksa.
     */
    public static String infoUser() {
        return user;
    }

    /** Kata sandi database yang sedang dipakai. Lihat {@link #infoUser()}. */
    public static String infoPass() {
        return pass;
    }
}
