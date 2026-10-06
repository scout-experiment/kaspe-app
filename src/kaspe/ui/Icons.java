package kaspe.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Ikon yang digambar sendiri dengan Java2D.
 *
 * <p>Digambar, bukan dibaca dari berkas gambar: build.sh dan compile.bat hanya menyalin
 * dua berkas pendukung (kaspe.properties dan schema.sql), jadi berkas gambar yang
 * ditaruh di dalam src/ tidak ikut ke folder build/ dan aplikasi hasil distribusi akan
 * kehilangan ikonnya tanpa pesan kesalahan apa pun.
 *
 * <p>Semua bentuk digambar pada kisi 24 satuan lalu diperkecil, supaya bentuknya tetap
 * sama di ukuran menu maupun di ukuran kartu tanpa perlu dihitung ulang.
 */
public final class Icons {

    /** Rumah — halaman beranda. */
    public static final int HOME = 0;
    /** Nota — halaman transaksi. */
    public static final int NOTE = 1;
    /** Diagram batang — halaman laporan. */
    public static final int CHART = 2;
    /** Gedung — data master rental. */
    public static final int BUILDING = 3;
    /** Truk — data master truk. */
    public static final int TRUCK = 4;
    /** Orang — halaman pengguna. */
    public static final int USER = 6;
    /** Lambang aplikasi. */
    public static final int BRAND = 5;

    /** Ukuran ikon di bilah samping. */
    public static final int MENU_SIZE = 17;
    /** Ukuran ikon di dalam kartu dasbor. */
    public static final int CARD_SIZE = 20;

    private Icons() {
    }

    /** Ikon berukuran {@link #MENU_SIZE}. */
    public static Icon menu(int shape, Color color) {
        return new Drawn(shape, color, MENU_SIZE);
    }

    /** Ikon berukuran bebas. */
    public static Icon of(int shape, Color color, int size) {
        return new Drawn(shape, color, size);
    }

    /** Satu bentuk ikon, digambar pada kisi 24 satuan. */
    private static final class Drawn implements Icon {

        private final int shape;
        private final Color color;
        private final int size;

        Drawn(int shape, Color color, int size) {
            this.shape = shape;
            this.color = color;
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);

            double scale = size / 24.0;
            g2.scale(scale, scale);
            g2.setColor(color);
            g2.setStroke(new BasicStroke((float) (1.5 / scale),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            switch (shape) {
                case HOME:
                    g2.drawPolyline(new int[]{3, 12, 21}, new int[]{11, 3, 11}, 3);
                    g2.drawRect(6, 11, 12, 10);
                    g2.drawLine(10, 21, 10, 15);
                    g2.drawLine(14, 21, 14, 15);
                    g2.drawLine(10, 15, 14, 15);
                    break;
                case NOTE:
                    g2.drawRect(5, 3, 14, 18);
                    g2.drawLine(8, 8, 16, 8);
                    g2.drawLine(8, 12, 16, 12);
                    g2.drawLine(8, 16, 13, 16);
                    break;
                case CHART:
                    g2.drawLine(4, 3, 4, 20);
                    g2.drawLine(4, 20, 21, 20);
                    g2.fillRect(7, 13, 3, 7);
                    g2.fillRect(12, 8, 3, 12);
                    g2.fillRect(17, 15, 3, 5);
                    break;
                case BUILDING:
                    g2.drawRect(4, 3, 16, 18);
                    g2.fillRect(7, 7, 4, 4);
                    g2.fillRect(13, 7, 4, 4);
                    g2.fillRect(7, 14, 4, 4);
                    g2.fillRect(13, 14, 4, 4);
                    break;
                case TRUCK:
                    g2.drawRect(2, 6, 11, 9);
                    g2.drawPolyline(new int[]{13, 17, 20, 20, 13},
                            new int[]{9, 9, 12, 15, 15}, 5);
                    g2.fillOval(4, 14, 4, 4);
                    g2.fillOval(15, 14, 4, 4);
                    break;
                case USER:
                    // Orang: kepala dan bahunya.
                    g2.drawOval(8, 3, 8, 8);
                    g2.drawArc(4, 13, 16, 9, 180, 180);
                    break;
                default:
                    // Lambang aplikasi: keping hijau dengan biji putih di tengahnya.
                    g2.fillRoundRect(1, 1, 22, 22, 8, 8);
                    g2.setColor(Color.WHITE);
                    g2.fillOval(7, 7, 10, 10);
                    break;
            }
            g2.dispose();
        }
    }
}
