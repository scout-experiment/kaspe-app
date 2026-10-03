package kaspe.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Isi jendela: bilah nama halaman di atas, halaman yang sedang dibuka di bawahnya.
 *
 * <p>Dipisah dari {@link MainFrame} supaya susunan halaman yang sama bisa dipakai juga
 * saat membuat gambar pratinjau, yang berjalan tanpa jendela dan tanpa layar.
 */
public class PagePanel extends JPanel {

    private final HeaderBar header = new HeaderBar("Beranda", "");
    private final JPanel content = new JPanel(new BorderLayout());

    /**
     * Bilah samping yang harus ikut menandai halaman yang sedang dibuka.
     *
     * <p>Halaman bisa dibuka dari dua tempat: dari bilah samping itu sendiri, dan dari
     * kode lain (mis. halaman pembuka saat aplikasi baru dijalankan). Karena itu
     * penandanya diatur di sini, bukan di dalam bilah samping, supaya keduanya ikut
     * tersorot.
     */
    private NavBar nav;

    public PagePanel() {
        super(new BorderLayout());
        setBackground(Theme.CANVAS);

        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(0, 18, 18, 18));

        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    /**
     * Susunan jendela yang lengkap: bilah samping di kiri, halaman di kanan.
     *
     * <p>Dipakai bersama oleh jendela aplikasi, pembuat gambar pratinjau, dan uji tampilan.
     * Kalau susunan ini ditulis ulang di masing-masing tempat, ketiganya bisa berbeda
     * tanpa ada yang menyadari — dan gambar pratinjau akan menampilkan susunan yang
     * tidak lagi sama dengan aplikasi sungguhan.
     */
    public static JPanel shell(PagePanel page) {
        JPanel layar = new JPanel(new BorderLayout());
        layar.setBackground(Theme.CANVAS);
        layar.add(NavBar.build(page), BorderLayout.WEST);
        layar.add(page, BorderLayout.CENTER);
        return layar;
    }

    /** Daftarkan bilah samping yang ikut menandai halaman yang sedang dibuka. */
    void setNav(NavBar nav) {
        this.nav = nav;
    }

    /**
     * Tampilkan satu halaman, sekaligus menuliskan namanya dan tanggal hari ini di
     * bilah atas.
     *
     * <p>Yang ditulis di bilah atas adalah nama halaman, bukan nama aplikasi. Nama
     * aplikasi sudah tertulis di judul jendela dan di bilah samping, jadi menuliskannya
     * lagi hanya memakai ruang tanpa menambah keterangan.
     *
     * @param pageName   nama halaman, sekaligus penanda baris menu yang ikut tersorot
     * @param keterangan satu baris penjelasan isi halaman
     */
    public void showPanel(JPanel panel, String pageName, String keterangan) {
        header.refreshDate();
        if (nav != null) {
            nav.setActive(pageName);
        }
        content.removeAll();
        content.add(panel, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }
}
