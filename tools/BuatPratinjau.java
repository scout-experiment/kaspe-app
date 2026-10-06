import com.formdev.flatlaf.ui.FlatLineBorder;
import kaspe.Db;
import kaspe.dao.UserDao;
import kaspe.model.Pengguna;
import kaspe.ui.DialogDataMaster;
import kaspe.ui.DialogLogin;
import kaspe.ui.PagePanel;
import kaspe.ui.PanelDashboard;
import kaspe.ui.PanelPengguna;
import kaspe.ui.PanelReport;
import kaspe.ui.PanelTransaction;
import kaspe.ui.Theme;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Pembuat gambar pratinjau (preview/*.png).
 *
 * Gambar dibuat dari susunan yang sama dengan jendela aplikasi - menu bar, bilah nama
 * halaman, dan halamannya - lalu digambar ke berkas PNG. Jadi tampilannya sama dengan
 * aplikasi yang dijalankan, tetapi tidak memerlukan layar maupun tetikus. Karena tidak
 * memerlukan layar, pembuatnya bisa dijalankan di server tanpa tampilan. Khusus Kelola
 * Data Truk, yang di aplikasinya berbentuk dialog, gambarnya memakai panelnya saja di
 * susunan jendela yang sama - JDialog tidak bisa dibuka tanpa layar.
 *
 * Cara menjalankan (dari folder proyek, setelah ./build.sh):
 *   javac -d /tmp/tools -cp "build:lib/*" tools/BuatPratinjau.java
 *   java -Djava.awt.headless=true -cp "build:lib/*:/tmp/tools" BuatPratinjau
 *
 * Data contoh diambil dari docs/data-contoh.sql.
 */
public class BuatPratinjau {

    static final int LEBAR = 1320;
    static final int TINGGI = 760;
    static final Path KELUAR = Paths.get("preview");
    static final Path DATA = Paths.get("docs/data-contoh.sql");
    static final Path RUMAH = Paths.get(System.getProperty("java.io.tmpdir"), "kaspe-pratinjau");

    /** Susunan jendela: menu bar di atas, halaman di bawahnya. */
    static JPanel layar;
    static PagePanel halaman;

    public static void main(String[] args) throws Exception {
        Theme.install();
        Files.createDirectories(KELUAR);

        Files.createDirectories(RUMAH.resolve("kaspe"));
        Files.deleteIfExists(RUMAH.resolve("kaspe/db_kaspe.mv.db"));
        Db.setConfiguration("org.h2.Driver",
                "jdbc:h2:" + RUMAH.resolve("kaspe/db_kaspe") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");

        isiDataContoh();

        // Layar masuk digambar paling awal, dan gambar "buat admin pertama" harus
        // diambil SEBELUM akun apa pun tercatat - tabel pengguna yang kosong itulah
        // yang membuat layarnya berganti rupa. Di aplikasinya layar ini muncul
        // sendirian (DialogLogin.buka(null), jendela utama belum ada), karena itu
        // gambarnya tanpa halaman di belakang.
        gambarLogin("06-login-pertama.png", new DialogLogin());

        // Dua akun contoh: admin yang masuk, dan satu pengguna biasa supaya halaman
        // Pengguna memperlihatkan kedua perannya. Gambar "Masuk" mengisi namanya saja -
        // sandi memang tidak pernah terbaca di layar, kotaknya kosong.
        UserDao daoPengguna = new UserDao();
        daoPengguna.simpan("admin", "sandi-admin", Pengguna.ADMIN);
        daoPengguna.simpan("budi", "sandi-budi", Pengguna.USER);
        DialogLogin masuk = new DialogLogin();
        isi(masuk, "fNama", "admin");
        gambarLogin("07-login.png", masuk);

        // Pratinjau memakai mata admin: hanya dia yang melihat halaman Pengguna.
        Pengguna admin = daoPengguna.cari("admin");
        halaman = new PagePanel(admin);
        layar = PagePanel.shell(halaman);

        // Transaksi dikerjakan lebih dulu. Dua pengiriman disimpan lebih dulu supaya
        // daftar "Transaksi Tersimpan" memperlihatkan warna selang-selingnya dan
        // urutannya yang terbaru-di-atas; data contoh mengisi baris-baris di bawahnya.
        // Lalu form diisi lagi: gambar 02 memang memperlihatkan saat operator sedang
        // mengisi (form masih berisi), sedangkan gambar 03 keadaan sesudah Simpan -
        // form sudah dikosongkan lagi dan pengiriman yang baru disimpan ada di paling
        // atas daftar.
        //
        // Urutannya penting: halaman pembuka dan laporan menggambar angka dari basis data
        // yang sama. Kalau halaman pembuka digambar sebelum pengiriman contoh ini disimpan,
        // angkanya berbeda dengan halaman laporan di halaman pratinjau yang sama - padahal
        // keduanya membaca data yang sama, dan yang membacanya akan mengira ada yang salah.
        PanelTransaction pt = new PanelTransaction();
        halaman.showPanel(pt, "Transaksi", "Catat pengiriman per truk.");
        isiForm(pt, "BE 8234 HD", "7200", "7050", "15", "1150");
        simpan(pt);
        isiForm(pt, "BE 8009 CF", "6000", "5970", "15", "1150");
        simpan(pt);
        isiForm(pt, "BE 9120 XY", "7530", "7380", "15", "1150");
        gambar("02-transaction-input.png");
        simpan(pt);
        gambar("03-transaction-saved.png");

        // Halaman pembuka
        halaman.showPanel(new PanelDashboard(), "Beranda",
                "Ringkasan catatan pengiriman singkong.");
        gambar("01-dashboard.png");

        // Data master - sekarang dialog, bukan halaman. Di aplikasinya dibuka lewat tombol
        // ikon di sebelah kotak "Plat / Truk" pada halaman Transaksi. Digambar SEBAGAI
        // dialog: panelnya melayang di atas halaman Transaksi yang diredupkan, dengan
        // bilah judulnya sendiri.
        //
        // Digambar sebagai halaman (lewat showPanel) hasilnya justru berbohong: bilah atas
        // menulis "Data Master" padahal bilah samping sudah tidak punya entrinya, sehingga
        // gambarnya menyebut halaman yang tidak pernah ada. Isinya memang JPanel, jadi
        // cukup digambar melayang tanpa membuka JDialog (yang butuh layar).
        halaman.showPanel(new PanelTransaction(), "Transaksi", "Catat pengiriman per truk.");
        // Ukuran jendelanya diambil dari perhitungan yang sama dengan yang dipakai
        // DialogDataMaster.buka(), bukan angka tetap: pratinjau ini yang dipakai untuk
        // memeriksa tampilan, jadi ia harus menggambar dialog pada ukuran yang benar-benar
        // dibuka aplikasi. Dengan angka tetap 760x560 isinya digambar 510px tinggi, lebih
        // pendek daripada lantai jendelanya sendiri.
        DialogDataMaster isiDialog = new DialogDataMaster();
        java.awt.Dimension ukuranDialog = DialogDataMaster.ukuranJendela(isiDialog);
        gambarDialog("04-master.png", isiDialog, ukuranDialog.width, ukuranDialog.height);

        // Laporan - filter bawaan sudah mencakup seluruh data
        halaman.showPanel(new PanelReport(), "Laporan", "Rekap penjualan per periode.");
        gambar("05-report.png");

        // Halaman Pengguna, khusus admin. Nama dan keterangannya sama persis dengan
        // entrinya di bilah samping (NavBar), supaya gambar menulis halaman yang sama
        // dengan yang dibuka aplikasi.
        halaman.showPanel(new PanelPengguna(admin), "Pengguna", "Kelola akun dan perannya.");
        gambar("08-pengguna.png");

        System.out.println("selesai -> " + KELUAR.toAbsolutePath());
    }

    static void isiKolom(PanelTransaction p, String lapak, String pabrik, String refraksi, String harga)
            throws Exception {
        isi(p, "txtFieldWeight", lapak);
        isi(p, "txtFactoryWeight", pabrik);
        isi(p, "txtRefraction", refraksi);
        isi(p, "txtPrice", harga);
    }

    static void isi(Object target, String field, String nilai) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        ((JTextField) f.get(target)).setText(nilai);
    }

    /** Isi satu pengiriman lengkap: plat plus angka-angka timbangannya. */
    static void isiForm(PanelTransaction p, String plat, String lapak, String pabrik,
            String refraksi, String harga) throws Exception {
        pilih(p, "cmbPlate", plat);
        isiKolom(p, lapak, pabrik, refraksi, harga);
    }

    /** Tulis teks ke kotak isian di dalam combo yang bisa diketik. */
    static void pilih(Object target, String field, String nilai) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        ((JComboBox<?>) f.get(target)).getEditor().setItem(nilai);
    }

    /**
     * Simpan lewat tombol Simpannya sendiri, supaya jalan datanya sama dengan aplikasi:
     * form dikosongkan dan daftar "Transaksi Tersimpan" disegarkan oleh save() itu
     * sendiri.
     *
     * <p>Dialog "Tersimpan" tidak bisa tampil tanpa layar dan melempar
     * HeadlessException - dan menelannya di sini benar, bukan menutup mata atas
     * kegagalan: panel sengaja mengosongkan form dan menyegarkan daftar SEBELUM
     * dialog itu ditampilkan, jadi saat dialognya gagal tampil, penyimpanannya sudah
     * tuntas. Galat lain tetap naik supaya benar-benar terdengar.
     */
    static void simpan(PanelTransaction p) throws Exception {
        Method m = PanelTransaction.class.getDeclaredMethod("save");
        m.setAccessible(true);
        try {
            m.invoke(p);
        } catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof HeadlessException)) {
                throw e;
            }
        }
    }

    static void isiDataContoh() throws Exception {
        String sql = new String(Files.readAllBytes(DATA), StandardCharsets.UTF_8);
        try (Connection c = Db.get(); Statement st = c.createStatement()) {
            StringBuilder cur = new StringBuilder();
            for (String line : sql.split("\n")) {
                String t = line.trim();
                if (t.startsWith("--") || t.isEmpty()) {
                    continue;
                }
                cur.append(line).append('\n');
                if (t.endsWith(";")) {
                    String s = cur.toString();
                    cur.setLength(0);
                    st.execute(s);
                }
            }
        }
    }

    /** Gambar seluruh isi jendela, termasuk menu bar dan bilah nama halaman. */
    static void gambar(String nama) throws Exception {
        layar.setSize(LEBAR, TINGGI);
        layar.doLayout();
        layoutDalam(layar);
        layar.setSize(LEBAR, TINGGI);
        layar.doLayout();
        layoutDalam(layar);

        BufferedImage img = new BufferedImage(LEBAR, TINGGI, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Theme.CANVAS);
        g.fillRect(0, 0, LEBAR, TINGGI);
        layar.paint(g);
        g.dispose();

        File f = KELUAR.resolve(nama).toFile();
        ImageIO.write(img, "png", f);
        System.out.println("   " + nama + " (" + f.length() + " bytes)");
    }

    /**
     * Gambar sebuah dialog di atas halaman yang sedang terbuka, dengan latarnya diredupkan.
     *
     * <p>Jendela sungguhannya tidak dibuat: {@code JDialog} butuh layar dan alat ini
     * berjalan tanpa layar. Yang digambar adalah panel isinya, ditambah bilah judul -
     * supaya gambarnya memperlihatkan dialog, bukan halaman yang kebetulan berisi hal
     * yang sama.
     */
    static void gambarDialog(String nama, JPanel isi, int lebarDialog, int tinggiDialog) throws Exception {
        layar.setSize(LEBAR, TINGGI);
        layar.doLayout();
        layoutDalam(layar);
        layar.setSize(LEBAR, TINGGI);
        layar.doLayout();
        layoutDalam(layar);

        JPanel panel = Theme.card();
        panel.setLayout(new BorderLayout());
        panel.add(bilahJendela("Kelola Data Truk"), BorderLayout.NORTH);
        isi.setOpaque(false);
        isi.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        isi.setPreferredSize(new Dimension(lebarDialog - 2, tinggiDialog - 50));
        panel.add(isi, BorderLayout.CENTER);
        panel.setSize(lebarDialog, tinggiDialog);
        layoutDalam(panel);

        BufferedImage img = new BufferedImage(LEBAR, TINGGI, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Theme.CANVAS);
        g.fillRect(0, 0, LEBAR, TINGGI);
        layar.paint(g);
        // Redupkan halaman di belakang, seperti jendela modal sungguhan.
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRect(0, 0, LEBAR, TINGGI);
        int x = (LEBAR - lebarDialog) / 2;
        int y = (TINGGI - tinggiDialog) / 2;
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(x + 4, y + 6, lebarDialog, tinggiDialog, 16, 16);
        g.translate(x, y);
        panel.paint(g);
        g.translate(-x, -y);
        g.dispose();

        File f = KELUAR.resolve(nama).toFile();
        ImageIO.write(img, "png", f);
        System.out.println("   " + nama + " (" + f.length() + " bytes)");
    }

    /**
     * Bilah judul jendela palsu: judulnya di kiri, tanda tutup di kanan, garis tipis
     * di bawah - meniru bilah judul FlatLaf yang tidak bisa digambar sendiri tanpa
     * layar. Dipakai bersama oleh gambar dialog dan gambar layar masuk.
     */
    static JPanel bilahJendela(String judulJendela) {
        JLabel judul = new JLabel(judulJendela);
        judul.setFont(Theme.semibold(Theme.FONT_SIZE + 1f));
        judul.setForeground(Theme.INK);
        judul.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JPanel bilah = new JPanel(new BorderLayout());
        bilah.setOpaque(false);
        bilah.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));
        bilah.add(judul, BorderLayout.WEST);
        JLabel silang = new JLabel("\u00d7");
        silang.setForeground(Theme.INK_SOFT);
        silang.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));
        bilah.add(silang, BorderLayout.EAST);
        return bilah;
    }

    /**
     * Gambar layar masuk: satu jendela kecil di tengah kanvas, TANPA halaman di
     * belakang dan tanpa peredupan - di aplikasinya layar ini dibuka sebelum jendela
     * utama ada, jadi tidak ada yang perlu diredupkan.
     *
     * <p>Bilah judulnya palsu seperti pada dialog lain, tetapi isinya tidak disentuh
     * sama sekali: jendela sungguhannya menjadikan panelnya sendiri sebagai
     * contentPane lalu pack(), sehingga tepi yang dibawa DialogLogin adalah tepi yang
     * diberikan jendelanya. Mengupasnya seperti isi dialog truk membuat gambarnya
     * berbohong tentang jarak isinya. Ukurannya juga dari hitungan yang sama dengan
     * aplikasi: selebar dan setinggi yang diminta isinya, tanpa angka tetap.
     */
    static void gambarLogin(String nama, DialogLogin isi) throws Exception {
        JPanel panel = new JPanel(new BorderLayout());
        Theme.applyCard(panel);
        panel.setBorder(new FlatLineBorder(new java.awt.Insets(1, 1, 1, 1),
                Theme.LINE, 1f, Theme.CARD_ARC));
        panel.add(bilahJendela(judulJendela(isi)), BorderLayout.NORTH);
        panel.add(isi, BorderLayout.CENTER);

        java.awt.Dimension butuh = panel.getPreferredSize();
        panel.setSize(butuh.width, butuh.height);
        layoutDalam(panel);

        // Tinggi kanvas mengikuti jendelanya: layar masuk jauh lebih tinggi daripada
        // lebar jendelanya (GridLayout-nya menyamakan tinggi keempat barisnya), jadi
        // kanvas tetap 760 seperti gambar lain akan memotong jendela yang sesungguhnya
        // dibuka aplikasi. Lebar tetap sama dengan gambar lain supaya galerinya rapi.
        int tinggi = panel.getHeight() + 120;
        BufferedImage img = new BufferedImage(LEBAR, tinggi, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Theme.CANVAS);
        g.fillRect(0, 0, LEBAR, tinggi);
        int x = (LEBAR - panel.getWidth()) / 2;
        int y = (tinggi - panel.getHeight()) / 2;
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(x + 4, y + 6, panel.getWidth(), panel.getHeight(), 16, 16);
        g.translate(x, y);
        panel.paint(g);
        g.translate(-x, -y);
        g.dispose();

        File f = KELUAR.resolve(nama).toFile();
        ImageIO.write(img, "png", f);
        System.out.println("   " + nama + " (" + f.length() + " bytes)");
    }

    /** Judul jendela layar masuk, dibaca dari metode yang sama yang dipakai aplikasinya. */
    static String judulJendela(DialogLogin isi) throws Exception {
        Method m = DialogLogin.class.getDeclaredMethod("judulJendela");
        m.setAccessible(true);
        return (String) m.invoke(isi);
    }

    /** Paksa layout berulang; di luar layar, ukuran tidak dihitung sendiri. */
    static void layoutDalam(Container c) {
        c.doLayout();
        if (c instanceof JScrollPane) {
            JScrollPane sp = (JScrollPane) c;
            Component v0 = sp.getViewport() == null ? null : sp.getViewport().getView();
            if (v0 instanceof JTable && sp.getColumnHeader() == null) {
                JTableHeader hd = ((JTable) v0).getTableHeader();
                if (hd != null) {
                    sp.setColumnHeaderView(hd);
                }
            }
            sp.doLayout();
            JViewport vp = sp.getViewport();
            if (vp != null) {
                vp.setExtentSize(vp.getSize());
                vp.doLayout();
                Component view = vp.getView();
                if (view != null) {
                    view.setSize(vp.getExtentSize());
                    if (view instanceof JTable) {
                        JTable t = (JTable) view;
                        if (t.getTableHeader() != null) {
                            t.getTableHeader().setSize(t.getWidth(), t.getTableHeader().getPreferredSize().height);
                            t.getTableHeader().doLayout();
                        }
                    }
                }
            }
            Component head = sp.getColumnHeader();
            if (head != null) {
                head.setSize(head.getWidth(), head.getPreferredSize().height);
                head.doLayout();
            }
        }
        for (Component child : c.getComponents()) {
            if (child instanceof Container) {
                layoutDalam((Container) child);
            }
        }
    }
}
