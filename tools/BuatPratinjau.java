import kaspe.Db;
import kaspe.ui.PagePanel;
import kaspe.ui.PanelDashboard;
import kaspe.ui.PanelMaster;
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
 * memerlukan layar, pembuatnya bisa dijalankan di server tanpa tampilan.
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

        halaman = new PagePanel();
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

        // Data master - satu halaman berisi pemilik truk dan truknya sekaligus
        halaman.showPanel(new PanelMaster(), "Data Master",
                "Kelola pemilik truk dan plat nomornya.");
        gambar("04-master.png");

        // Laporan - filter bawaan sudah mencakup seluruh data
        halaman.showPanel(new PanelReport(), "Laporan", "Rekap penjualan per periode.");
        gambar("05-report.png");

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
