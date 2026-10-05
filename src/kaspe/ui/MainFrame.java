package kaspe.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Jendela utama: bilah samping berisi halaman, isinya di sebelah kanan. */
public class MainFrame extends JFrame {

    /**
     * Lebar jendela terkecil yang masih menampilkan seluruh isinya.
     *
     * <p>Dua hal menentukannya, dan keduanya diukur pada lebar ini oleh {@code TestUi}:
     * form transaksi memakai isian berukuran tetap dengan kolom kanan yang tidak bisa
     * dilipat, dan dua tabel - daftar pengiriman tersimpan serta tabel laporan - memakai
     * lebar kolom tetap tanpa penggeser mendatar. Kalau ruangnya kurang, kolomnya diperas
     * dan isinya terpotong; pada tabel laporan pemotongan itu ikut ke kertas, karena
     * pencetakan memakai {@code FIT_WIDTH} yang memperkecil tabel apa adanya.
     *
     * <p>Yang paling menuntut adalah tabel-tabel itu: 1264px adalah lebar terkecil yang
     * masih menampilkan seluruh kolomnya utuh. Diberi kelonggaran sedikit supaya perubahan
     * lebar kolom yang wajar tidak langsung menggagalkan pengujian, tetapi tidak lebih -
     * lebar ini membatasi berapa kecil jendelanya boleh dikecilkan.
     */
    public static final int LEBAR_MINIMUM = 1272;

    /** Tinggi jendela terkecil yang masih menyisakan ruang untuk daftar di bawah form. */
    public static final int TINGGI_MINIMUM = 700;

    private final PagePanel page = new PagePanel();

    public MainFrame() {
        setTitle("Aplikasi Pencatatan Kaspe");
        // Menutup jendela tidak langsung keluar: kalau masih ada isian transaksi yang
        // belum disimpan, operator harus ditanya dulu — sama seperti saat pindah halaman.
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1320, 760);
        setMinimumSize(new Dimension(LEBAR_MINIMUM, TINGGI_MINIMUM));
        setLocationRelativeTo(null);

        // Susunannya dipakai bersama dengan pembuat gambar pratinjau dan uji tampilan,
        // supaya ketiganya tidak bisa berbeda tanpa ada yang menyadari.
        JPanel layar = PagePanel.shell(page);
        setContentPane(layar);
        NavBar nav = cariNavBar(layar);

        page.showPanel(new PanelDashboard(), "Beranda",
                "Ringkasan catatan pengiriman singkong.");

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (nav != null && nav.adaKerjaBelumDisimpan()) {
                    int jwb = JOptionPane.showConfirmDialog(MainFrame.this,
                            "Masih ada isian transaksi yang belum disimpan.\nKeluar tanpa menyimpan?",
                            "Belum disimpan", JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                    if (jwb != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
                System.exit(0);
            }
        });
    }

    /**
     * Bilah samping dicari dari susunan jendela, karena {@link PagePanel#shell}
     * membuatnya sendiri dan tidak menyerahkannya — dan PagePanel tidak boleh berubah
     * hanya untuk kebutuhan ini.
     */
    private static NavBar cariNavBar(Container c) {
        for (Component anak : c.getComponents()) {
            if (anak instanceof NavBar) {
                return (NavBar) anak;
            }
            if (anak instanceof Container) {
                NavBar hasil = cariNavBar((Container) anak);
                if (hasil != null) {
                    return hasil;
                }
            }
        }
        return null;
    }
}
