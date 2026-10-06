package kaspe.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.List;

/**
 * Pratinjau cetak: memperlihatkan hasil cetak di layar sebelum kertas dipakai.
 *
 * <p>Laporan dicetak dengan mengecilkan tabel agar muat lebar kertas, jadi yang tampak di
 * layar belum tentu sama dengan yang keluar di kertas. Pratinjau ini menggambar halaman
 * cetak yang sebenarnya — termasuk di mana halaman terpotong — supaya kertas tidak terbuang
 * hanya untuk memeriksa hasilnya.
 *
 * <p>Halamannya digambar dari {@link Printable} yang sama dengan yang dipakai mencetak,
 * jadi yang terlihat di sini memang yang akan tercetak.
 */
public class PrintPreview extends JDialog {

    /** Perkecilan gambar di layar. Kertasnya tetap A4; hanya gambarnya yang dikecilkan. */
    private static final double SKALA = 0.8;

    /**
     * Batas jumlah halaman yang digambar. Bukan pembatasan kebutuhan, hanya pengaman:
     * tiap halaman disimpan sebagai gambar di memori, jadi laporan yang tak terhingga
     * akan menghabiskan memori. Buku mitra tidak pernah sedekat ini.
     */
    private static final int MAKS_HALAMAN = 60;

    private final Printable printable;
    private final PageFormat pageFormat;
    private final List<BufferedImage> halamanGambar;
    private final JLabel lblGambar = new JLabel("", SwingConstants.CENTER);
    private final JLabel lblHalaman = new JLabel();
    private final JButton btnSebelum = Theme.plain("\u2039 Sebelumnya");
    private final JButton btnSesudah = Theme.plain("Berikutnya \u203a");
    private int nomor = 0;

    public PrintPreview(Window owner, Printable printable, PageFormat pageFormat, String judul) {
        super(owner, judul, ModalityType.APPLICATION_MODAL);
        this.printable = printable;
        this.pageFormat = pageFormat;
        this.halamanGambar = renderPages(printable, pageFormat, SKALA, MAKS_HALAMAN);

        setLayout(new BorderLayout());
        add(buildHalaman(), BorderLayout.CENTER);
        add(buildTombol(), BorderLayout.SOUTH);

        lblHalaman.setForeground(Theme.INK_SOFT);
        btnSebelum.addActionListener(e -> tampilkan(nomor - 1));
        btnSesudah.addActionListener(e -> tampilkan(nomor + 1));

        tampilkan(0);

        int lebar = (int) Math.round(pageFormat.getWidth() * SKALA) + 60;
        int tinggi = Math.min(720, (int) Math.round(pageFormat.getHeight() * SKALA) + 120);
        setSize(Math.max(520, lebar), tinggi);
        setLocationRelativeTo(owner);
    }

    /** Lembar halaman yang sedang diperlihatkan, bisa digulung kalau lebih tinggi dari layar. */
    private JScrollPane buildHalaman() {
        JPanel kertas = new JPanel(new BorderLayout());
        kertas.setBackground(Theme.CANVAS);
        kertas.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        // Garis tepi tipis supaya batas kertas terlihat, bukan gambar yang melayang.
        lblGambar.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        lblGambar.setVerticalAlignment(SwingConstants.TOP);
        kertas.add(lblGambar, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(kertas);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        return scroll;
    }

    /** Baris tombol: pindah halaman, cetak, tutup. */
    private JPanel buildTombol() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBorder(BorderFactory.createEmptyBorder(10, 14, 12, 14));

        JPanel kiri = Theme.row(8, btnSebelum, btnSesudah, lblHalaman);

        JButton btnCetak = Theme.primary("Cetak");
        JButton btnTutup = Theme.plain("Tutup");
        btnCetak.addActionListener(e -> cetak());
        btnTutup.addActionListener(e -> dispose());
        JPanel kanan = Theme.rowRight(8, btnCetak, btnTutup);

        p.add(kiri, BorderLayout.WEST);
        p.add(kanan, BorderLayout.EAST);
        return p;
    }

    /** Tampilkan halaman ke-{@code i}, kalau nomornya memang ada. */
    private void tampilkan(int i) {
        if (halamanGambar.isEmpty()) {
            lblHalaman.setText("Tidak ada halaman");
            btnSebelum.setEnabled(false);
            btnSesudah.setEnabled(false);
            return;
        }
        nomor = Math.max(0, Math.min(halamanGambar.size() - 1, i));
        lblGambar.setIcon(new ImageIcon(halamanGambar.get(nomor)));
        lblHalaman.setText("Halaman " + (nomor + 1) + " dari " + halamanGambar.size());
        btnSebelum.setEnabled(nomor > 0);
        btnSesudah.setEnabled(nomor < halamanGambar.size() - 1);
    }

    /** Cetak dari jendela ini, memakai halaman yang sedang diperlihatkan. */
    private void cetak() {
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintable(printable, pageFormat);
            if (job.printDialog()) {
                job.print();
                JOptionPane.showMessageDialog(this, "Laporan dikirim ke printer.");
            }
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(this, "Gagal mencetak: " + t.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Gambar seluruh halaman cetak menjadi gambar di layar.
     *
     * <p>Dipanggil terus sampai {@link Printable} menyatakan halamannya sudah habis, jadi
     * jumlah halaman yang tampil selalu sama dengan jumlah halaman yang akan tercetak.
     *
     * @param skala     perkecilan gambar, 1.0 berarti seukuran kertas pada 72 titik per inci
     * @param maksHalaman batas aman, supaya tidak berputar tanpa henti
     */
    public static List<BufferedImage> renderPages(Printable printable, PageFormat pageFormat,
                                                  double skala, int maksHalaman) {
        List<BufferedImage> halaman = new ArrayList<>();
        int lebar = (int) Math.round(pageFormat.getWidth() * skala);
        int tinggi = (int) Math.round(pageFormat.getHeight() * skala);
        if (lebar < 1 || tinggi < 1) {
            return halaman;
        }
        for (int i = 0; i < maksHalaman; i++) {
            BufferedImage img = new BufferedImage(lebar, tinggi, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, lebar, tinggi);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.scale(skala, skala);
            int hasil;
            try {
                hasil = printable.print(g, pageFormat, i);
            } catch (Throwable t) {
                g.dispose();
                break;
            }
            g.dispose();
            if (hasil != Printable.PAGE_EXISTS) {
                break;
            }
            halaman.add(img);
        }
        return halaman;
    }

    /**
     * Ukuran kertas yang dipakai: mengikuti bawaan printer kalau ada, kalau tidak A4.
     *
     * <p>Komputer tanpa printer tetap bisa membuka pratinjau — kertasnya dianggap A4,
     * yang memang ukuran standar di sini.
     */
    public static PageFormat pageFormat() {
        try {
            PageFormat pf = PrinterJob.getPrinterJob().defaultPage();
            if (pf != null) {
                return pf;
            }
        } catch (Throwable t) {
            // Tidak ada printer terpasang — pakai A4.
        }
        double lebar = 595.0;
        double tinggi = 842.0;
        double tepi = 36.0;
        Paper kertas = new Paper();
        kertas.setSize(lebar, tinggi);
        kertas.setImageableArea(tepi, tepi, lebar - 2 * tepi, tinggi - 2 * tepi);
        PageFormat pf = new PageFormat();
        pf.setPaper(kertas);
        return pf;
    }
}
