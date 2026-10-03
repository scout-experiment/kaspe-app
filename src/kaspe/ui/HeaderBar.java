package kaspe.ui;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;

/**
 * Bilah atas jendela: nama halaman, keterangan singkat, dan tanggal hari ini.
 *
 * <p>Yang ditulis di sini adalah nama halaman, bukan nama aplikasi. Nama aplikasi sudah
 * tertulis di judul jendela dan di bilah samping, jadi menuliskannya lagi hanya memakai
 * ruang tanpa menambah keterangan. Nama halaman justru satu-satunya petunjuk "sedang di
 * mana" — jadi itu yang dibuat paling menonjol, ditemani satu baris keterangan tentang
 * isi halamannya.
 */
public class HeaderBar extends JPanel {

    private final JLabel lblPage = new JLabel();
    private final JLabel lblSubtitle = new JLabel();
    private final JLabel lblDate = new JLabel();

    public HeaderBar(String pageName, String subtitle) {
        super(new BorderLayout());
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                BorderFactory.createEmptyBorder(11, 18, 11, 18)));

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 1));
        left.setOpaque(false);
        lblPage.setFont(Theme.semibold(17f));
        lblPage.setForeground(Theme.INK);
        lblSubtitle.setFont(Theme.semibold(11f));
        lblSubtitle.setForeground(Theme.INK_SOFT);
        left.add(lblPage);
        left.add(lblSubtitle);
        add(left, BorderLayout.WEST);

        lblDate.setFont(Theme.semibold(12f));
        lblDate.setForeground(Theme.INK_SOFT);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(lblDate);
        add(right, BorderLayout.EAST);

        setPage(pageName, subtitle);
        refreshDate();
    }

    /** Ganti nama dan keterangan halaman yang ditampilkan. */
    public void setPage(String pageName, String subtitle) {
        lblPage.setText(pageName);
        lblSubtitle.setText(subtitle == null ? "" : subtitle);
    }

    /**
     * Tulis ulang tanggal hari ini.
     *
     * <p>Dipanggil setiap kali halaman dibuka atau dipindah: aplikasi yang dibiarkan
     * terbuka dari sore ke pagi masih menulis tanggal kemarin kalau tanggalnya hanya
     * diisi sekali di konstruktor.
     */
    public void refreshDate() {
        lblDate.setText(kaspe.util.Dates.longFormat(LocalDate.now()));
    }

    /** Nama halaman yang sedang ditampilkan. */
    public String pageName() {
        return lblPage.getText();
    }

    /** Keterangan halaman yang sedang ditampilkan. */
    public String subtitle() {
        return lblSubtitle.getText();
    }

    /** Tanggal hari ini seperti yang tertulis di bilah, mis. "Kamis, 02 Oktober 2026". */
    public String dateText() {
        return lblDate.getText();
    }
}
