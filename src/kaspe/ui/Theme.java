package kaspe.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.ui.FlatLineBorder;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Satu tempat untuk seluruh tampilan aplikasi: tema, warna, huruf, dan gaya komponen.
 *
 * <p>Kalau mau mengganti warna aksen atau ukuran huruf, cukup ubah nilai di kelas ini —
 * tidak perlu menyentuh halaman satu per satu.
 *
 * <p>Pemakaian: panggil {@link #install()} sekali di awal, sebelum komponen apa pun dibuat.
 */
public final class Theme {

    private Theme() {
    }

    // ---------- warna ----------

    /** Warna aksen (hijau daun singkong). Dipakai untuk tombol utama dan sorotan. */
    public static final Color ACCENT = new Color(0x2F7D4F);
    /** Aksen saat kursor di atas tombol. */
    public static final Color ACCENT_HOVER = new Color(0x276841);
    /** Aksen saat tombol ditekan. */
    public static final Color ACCENT_PRESSED = new Color(0x1F5535);

    /** Latar halaman (di belakang kartu-kartu). */
    public static final Color CANVAS = new Color(0xF3F5F7);
    /** Latar kartu. */
    public static final Color CARD = Color.WHITE;
    /** Garis pemisah dan tepi kartu. */
    public static final Color LINE = new Color(0xDFE4E9);
    /**
     * Garis kotak tabel (mendatar dan tegak). Lebih gelap dari {@link #LINE} karena
     * tabel ikut dicetak: pencetakan mengecilkan tabel agar muat lebar kertas, sehingga
     * garis tipis berwarna terlalu terang bisa hilang di kertas.
     */
    public static final Color LINE_PRINT = new Color(0xD2DBE2);
    /** Warna teks utama. */
    public static final Color INK = new Color(0x1E242B);
    /** Warna teks sekunder (label, keterangan). */
    public static final Color INK_SOFT = new Color(0x5B6671);
    /** Warna angka uang dan berat. */
    public static final Color MONEY = new Color(0x1B6B3A);
    /**
     * Latar baris tabel ke-2, ke-4, dan seterusnya. Dipakai bersama garis tipis untuk
     * memisahkan baris. Nadanya sengaja cukup gelap supaya selang-selingnya benar-benar
     * terlihat — kalau terlalu dekat dengan putih, tabel 12 kolom jadi sulit diikuti mata.
     */
    public static final Color ROW_ALT = new Color(0xE4EAF0);
    /** Latar baris yang sedang dipilih. */
    public static final Color SELECTION = new Color(0xD3E9DC);
    /**
     * Latar baris judul kolom tabel.
     *
     * <p>Judul kolom sebelumnya hanya dibedakan oleh huruf dan satu garis tipis, sehingga
     * tampil lebih pucat daripada isinya — padahal judul yang harus paling tegas. Latar
     * tipis ini memisahkannya tanpa mengganggu hasil cetak: tabel laporan ikut dicetak
     * dan warnanya berubah menjadi abu-abu, jadi latarnya sengaja dipilih yang masih
     * terbaca sebagai pemisah di kertas.
     */
    public static final Color HEADER_BG = new Color(0xE7EDF3);
    /** Latar kotak sorot angka hasil hitungan. */
    public static final Color STRIP = new Color(0xEDF4EF);
    /** Tepi kotak sorot angka hasil hitungan. */
    public static final Color STRIP_LINE = new Color(0xD6E6DB);
    /** Warna penanda angka yang perlu diperiksa (mis. susut terlalu besar). */
    public static final Color DANGER = new Color(0xB3261E);
    /** Warna isian yang salah, dipakai lewat {@link #markError}. */
    public static final Color ERROR = new Color(0xC62828);

    /** Tinggi satu baris tabel, dalam piksel. */
    public static final int ROW_HEIGHT = 32;
    /** Tinggi baris judul kolom, dalam piksel. */
    public static final int HEADER_HEIGHT = 34;
    /** Sudut membulat kartu dan tombol, dalam piksel. */
    public static final int CARD_ARC = 12;
    /** Tinggi seragam untuk semua kotak isian, supaya barisnya lurus. */
    public static final int FIELD_HEIGHT = 30;

    // ---------- huruf ----------

    /**
     * Ukuran huruf dasar. Ini satu-satunya tombol pengatur besar-kecil seluruh tampilan —
     * naikkan kalau kurang terbaca, turunkan kalau kolom tabel mulai terpotong.
     */
    public static final int FONT_SIZE = 13;

    /** Keluarga huruf untuk teks biasa. */
    private static String family = "SansSerif";
    /** Keluarga huruf untuk teks setengah tebal (semibold). */
    private static String familySemibold = "SansSerif";
    /**
     * Benar kalau {@link #familySemibold} adalah keluarga huruf tersendiri yang memang
     * sudah setengah tebal (Inter Semi Bold). Kalau salah, keluarga itu huruf biasa, jadi
     * ketebalannya harus ditambahkan sendiri — kalau tidak, judul dan judul kolom tampil
     * biasa saja dan tidak ada lagi yang menonjol.
     */
    private static boolean semiboldIsOwnFamily;

    // ---------- pemasangan tema ----------

    /**
     * Pasang tema. Panggil sekali di awal program, sebelum membuat komponen apa pun.
     *
     * <p>Kalau berkas tema tidak ada di classpath, aplikasi berhenti dengan pesan yang jelas —
     * lebih baik daripada jendela yang gagal terbuka tanpa penjelasan.
     */
    public static void install() {
        try {
            Class.forName("com.formdev.flatlaf.FlatLightLaf");
        } catch (ClassNotFoundException e) {
            System.err.println("Berkas tema tampilan tidak ditemukan: lib/flatlaf-3.7.2.jar");
            System.err.println("Pastikan folder lib ikut disalin bersama aplikasi,");
            System.err.println("atau jalankan lewat run.sh / run-app.bat.");
            System.exit(1);
        }
        pickFonts();
        // FlatLaf menghitung sendiri besar-kecil huruf untuk menu, judul kolom, dan
        // tooltip dari "preferred font". Kalau hanya defaultFont yang diset, bagian
        // itu tetap memakai huruf bawaan FlatLaf dan tidak ikut berubah.
        FlatLaf.setPreferredFontFamily(family);
        FlatLaf.setPreferredSemiboldFontFamily(familySemibold);
        FlatLightLaf.setup();
        UIManager.put("defaultFont", new FontUIResource(family, Font.PLAIN, FONT_SIZE));
        applyDefaults();
    }

    /** Nama keluarga huruf yang sedang dipakai, untuk ditampilkan saat memeriksa tampilan. */
    public static String fontFamily() {
        return family;
    }

    /**
     * Pakai huruf Inter yang ikut dikirim bersama aplikasi, supaya tampilan sama persis
     * di semua komputer. Kalau berkasnya tidak ada, jatuh ke huruf sistem yang paling
     * enak dibaca — aplikasi tetap jalan, hanya hurufnya ikut sistem.
     */
    private static void pickFonts() {
        if (interUsable()) {
            try {
                // Huruf "Inter" dipakai untuk teks biasa, "Inter Semi Bold" untuk judul dan
                // angka penting. Keduanya keluarga terpisah, jadi huruf tebal di layar adalah
                // huruf semibold yang sebenarnya, bukan hasil menebalkan paksa.
                com.formdev.flatlaf.fonts.inter.FlatInterFont.install();
                family = com.formdev.flatlaf.fonts.inter.FlatInterFont.FAMILY;
                familySemibold = com.formdev.flatlaf.fonts.inter.FlatInterFont.FAMILY_SEMIBOLD;
                semiboldIsOwnFamily = true;
                return;
            } catch (Throwable e) {
                // Berkas huruf tidak ada — pakai huruf sistem.
            }
        }
        family = pickSystemFamily();
        familySemibold = family;
        semiboldIsOwnFamily = false;
    }

    /**
     * Huruf Inter hanya tampil benar di Java 8 update 212 ke atas. Di versi yang lebih tua
     * — dan di Java 9 — hurufnya digambar jauh kebesaran, sehingga tampilan justru rusak.
     * Karena itu huruf itu dilewati di versi-versi tersebut, dan aplikasi memakai huruf
     * sistem seperti sebelum huruf ikut dikirim.
     */
    private static boolean interUsable() {
        return interUsable(System.getProperty("java.version", ""));
    }

    /**
     * Aturan versi di atas, dipisah supaya bisa diperiksa tanpa mengganti versi Java yang
     * sedang berjalan.
     *
     * @param versi isi {@code java.version}, contoh {@code "1.8.0_171"}
     */
    public static boolean interUsable(String versi) {
        if (!versi.startsWith("1.8")) {
            // Java 10 ke atas tidak bermasalah lagi; Java 9 masih.
            return !versi.startsWith("9");
        }
        int garisBawah = versi.indexOf('_');
        if (garisBawah < 0) {
            return false;
        }
        String update = versi.substring(garisBawah + 1).replaceAll("[^0-9].*", "");
        try {
            return Integer.parseInt(update) >= 212;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Pilih huruf pertama yang tersedia di sistem, dari yang paling enak dibaca. */
    private static String pickSystemFamily() {
        String[] wanted = {
                "Segoe UI", "Inter", "Roboto", "Noto Sans", "DejaVu Sans",
                "Ubuntu", "Cantarell", "Liberation Sans", "Arial", "SansSerif"
        };
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String name : wanted) {
            if (available.contains(name)) {
                return name;
            }
        }
        return FlatLaf.getPreferredFontFamily();
    }

    private static void applyDefaults() {
        // sudut membulat & garis tepi tipis
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0.5);
        UIManager.put("Component.borderColor", LINE);
        UIManager.put("Component.accentColor", ACCENT);

        // latar
        UIManager.put("Panel.background", CANVAS);

        // isian: tempat mengetik diberi ruang lebih lega supaya tidak terlihat sesak
        UIManager.put("TextComponent.padding", new Insets(4, 8, 4, 8));
        UIManager.put("ComboBox.padding", new Insets(4, 8, 4, 8));
        UIManager.put("Spinner.padding", new Insets(4, 8, 4, 8));

        // tabel — baris dipisahkan warna selang-seling, sel dipisahkan garis tipis
        UIManager.put("Table.rowHeight", ROW_HEIGHT);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", true);
        // Jarak kiri-kanan isi sel diatur lewat bawaan FlatLaf, supaya border sel
        // (termasuk penanda sel terpilih) tidak tertimpa. Nilainya harus berupa
        // Insets, bukan teks — teks membuat FlatLaf gagal membuat bordernya.
        UIManager.put("Table.cellMargins", new Insets(2, 10, 2, 10));
        UIManager.put("TableHeader.cellMargins", new Insets(2, 10, 2, 10));
        UIManager.put("Table.alternateRowColor", ROW_ALT);
        UIManager.put("Table.selectionBackground", SELECTION);
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("TableHeader.height", HEADER_HEIGHT);
        UIManager.put("TableHeader.background", HEADER_BG);
        UIManager.put("TableHeader.foreground", INK);
        // Garis tegak antar judul kolom dibuat sama dengan garis kotak isi tabel, supaya
        // batas kolom lurus dari judul sampai baris paling bawah. Garis pemisah judul
        // dengan isi dibuat sama tegas dengan garis antar baris — justru garis judul yang
        // paling perlu terlihat.
        UIManager.put("TableHeader.separatorColor", LINE_PRINT);
        UIManager.put("TableHeader.bottomSeparatorColor", LINE_PRINT);

        // batang gulir ramping
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.track", CANVAS);
        UIManager.put("ScrollPane.smoothScrolling", Boolean.TRUE);

        // Tombol default (tombol yang aktif saat Enter ditekan, mis. tombol Masuk).
        // FlatLaf punya gayanya sendiri untuk tombol ini, jadi harus diarahkan ke warna aksen.
        UIManager.put("Button.default.background", ACCENT);
        UIManager.put("Button.default.foreground", Color.WHITE);
        UIManager.put("Button.default.hoverBackground", ACCENT_HOVER);
        UIManager.put("Button.default.pressedBackground", ACCENT_PRESSED);
        UIManager.put("Button.default.borderColor", ACCENT);
        UIManager.put("Button.default.focusColor", ACCENT);

        // Sorotan menu, disamakan dengan warna aksen.
        UIManager.put("MenuBar.selectionBackground", ACCENT);
        UIManager.put("MenuBar.selectionForeground", Color.WHITE);
        UIManager.put("Menu.selectionBackground", ACCENT);
        UIManager.put("Menu.selectionForeground", Color.WHITE);
        UIManager.put("MenuItem.selectionBackground", ACCENT);
        UIManager.put("MenuItem.selectionForeground", Color.WHITE);
    }

    // ---------- huruf siap pakai ----------

    /**
     * Huruf setengah tebal untuk judul kartu, judul kolom, dan label isian.
     *
     * <p>Kalau keluarga semibold asli tersedia, gaya {@code PLAIN} sudah benar — hurufnya
     * memang sudah setengah tebal. Kalau yang dipakai huruf sistem biasa, ketebalannya
     * harus ditambahkan sendiri, supaya judul tetap lebih tegas daripada isinya.
     */
    public static Font semibold(float size) {
        return new Font(familySemibold, semiboldIsOwnFamily ? Font.PLAIN : Font.BOLD, Math.round(size));
    }

    /** Huruf tebal penuh. Dipakai hanya untuk judul halaman dan angka total. */
    public static Font bold(float size) {
        return new Font(family, Font.BOLD, Math.round(size));
    }

    /** Label biasa berwarna redup, dipakai untuk keterangan di samping kolom isian. */
    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(INK_SOFT);
        return l;
    }

    /**
     * Label kecil di atas kotak isian. Dipakai oleh {@link #field}.
     * Ukurannya sengaja lebih kecil dari isian supaya yang menonjol tetap isinya.
     */
    public static JLabel caption(String text) {
        JLabel l = new JLabel(text);
        l.setFont(semibold(FONT_SIZE - 2f));
        l.setForeground(INK_SOFT);
        return l;
    }

    /**
     * Satu kolom isian: keterangan kecil di atas, kotak isian di bawah.
     *
     * <p>Keterangan ditaruh di atas, bukan di samping, supaya mata membaca dari atas ke
     * bawah dan tidak perlu melompati celah yang lebarnya berubah-ubah antar baris.
     */
    public static JPanel field(String caption, JComponent input) {
        JPanel p = new JPanel(new BorderLayout(0, 5));
        p.setOpaque(false);
        p.add(caption(caption), BorderLayout.NORTH);
        p.add(input, BorderLayout.CENTER);
        return p;
    }

    // ---------- isian ----------

    /** Tulis petunjuk samar di dalam kotak isian, mis. "kg" atau "Rp/kg". */
    public static void placeholder(JComponent input, String text) {
        input.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, text);
    }

    /**
     * Tandai kotak isian yang salah. Garisnya berubah merah dan tetap merah sampai
     * {@code on} dikembalikan ke false — jadi salah ketik langsung kelihatan di tempat
     * kejadiannya, tanpa jendela peringatan yang harus ditutup dulu.
     */
    public static void markError(JComponent input, boolean on) {
        input.putClientProperty(FlatClientProperties.OUTLINE, on ? "error" : null);
        input.setForeground(on ? ERROR : INK);
    }

    /** Bersihkan tanda salah dari beberapa kotak isian sekaligus. */
    public static void clearErrors(JComponent... inputs) {
        for (JComponent c : inputs) {
            markError(c, false);
        }
    }

    /**
     * Tampilkan kegagalan dalam satu bentuk yang sama di seluruh aplikasi.
     *
     * <p>Dikumpulkan di sini supaya pesannya seragam dan tidak ada halaman yang lupa
     * menanganinya. Yang paling mudah terlewat adalah halaman yang dibuka paling awal:
     * kalau halaman itu gagal membaca database (berkasnya sedang terkunci, atau pengaturan
     * MySQL-nya salah), pengguna melihat jendela kosong tanpa penjelasan apa pun.
     *
     * <p>{@link kaspe.Db} sudah menerjemahkan kegagalan H2 yang tersering menjadi kalimat
     * berbahasa Indonesia, jadi pesannya bisa langsung ditampilkan.
     */
    public static void showError(Component parent, Throwable e) {
        String pesan = e.getMessage();
        if (pesan == null || pesan.trim().isEmpty()) {
            pesan = e.getClass().getSimpleName();
        }
        JOptionPane.showMessageDialog(parent, "Gagal: " + pesan,
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ---------- kartu ----------

    /** Kartu putih bersudut membulat, tanpa judul. Isi ditaruh di tengah (BorderLayout.CENTER). */
    public static JPanel card() {
        JPanel p = new JPanel(new BorderLayout());
        applyCard(p);
        return p;
    }

    /**
     * Kartu putih dengan judul kecil di atasnya.
     * Isi kartu ditaruh di tengah: {@code card.add(isi, BorderLayout.CENTER)}.
     */
    public static JPanel card(String title) {
        JPanel p = card();
        if (title != null && !title.isEmpty()) {
            JLabel label = new JLabel(title);
            label.setFont(semibold(FONT_SIZE + 1f));
            label.setForeground(INK);
            label.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
            p.add(label, BorderLayout.NORTH);
        }
        return p;
    }

    /** Beri gaya kartu pada panel yang sudah ada. */
    public static void applyCard(JComponent c) {
        c.putClientProperty(FlatClientProperties.STYLE, "arc: " + CARD_ARC + "; background: #FFFFFF");
        c.setOpaque(true);
        c.setBorder(BorderFactory.createCompoundBorder(
                new FlatLineBorder(new Insets(1, 1, 1, 1), LINE, 1f, CARD_ARC),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
    }

    /**
     * Kotak sorot untuk angka hasil hitungan (berat bersih, jumlah uang).
     * Latarnya hijau sangat muda supaya angka penting terpisah dari isian di atasnya.
     */
    public static JPanel strip() {
        JPanel p = new JPanel(new GridBagLayout());
        p.putClientProperty(FlatClientProperties.STYLE, "arc: 10; background: #EDF4EF");
        p.setOpaque(true);
        p.setBorder(BorderFactory.createCompoundBorder(
                new FlatLineBorder(new Insets(1, 1, 1, 1), STRIP_LINE, 1f, 10),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        return p;
    }

    // ---------- tombol ----------

    /** Tombol utama: berlatar aksen, dipakai untuk aksi yang paling sering ditekan. */
    public static JButton primary(String text) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatClientProperties.STYLE, ""
                + "background: #2F7D4F;"
                + "foreground: #FFFFFF;"
                + "hoverBackground: #276841;"
                + "hoverForeground: #FFFFFF;"
                + "pressedBackground: #1F5535;"
                + "pressedForeground: #FFFFFF;"
                + "borderWidth: 0;"
                + "focusWidth: 0;"
                + "innerFocusWidth: 0;"
                + "minimumWidth: 90");
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Tombol biasa: untuk aksi pendamping yang tidak perlu menonjol. */
    public static JButton plain(String text) {
        JButton b = new JButton(text);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ---------- tabel ----------

    /**
     * Tabel yang menampilkan pesan di tengah kalau belum ada barisnya.
     *
     * <p>Tabel kosong yang dibiarkan putih kosong terlihat seperti aplikasi yang gagal
     * memuat data. Pesannya sekaligus memberi tahu langkah berikutnya.
     */
    public static class Table extends JTable {

        private String emptyMessage;

        public Table(javax.swing.table.TableModel model, String emptyMessage) {
            super(model);
            this.emptyMessage = emptyMessage;
        }

        /**
         * Ganti pesan yang tampil saat tabel kosong. Dipakai laporan, karena "belum ada
         * data sama sekali" dan "tidak ada data pada rentang tanggal ini" adalah dua
         * keadaan berbeda yang butuh penjelasan berbeda.
         */
        public void setEmptyMessage(String message) {
            this.emptyMessage = message;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (emptyMessage == null || emptyMessage.isEmpty() || getRowCount() > 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(new Font(family, Font.PLAIN, FONT_SIZE));
            g2.setColor(INK_SOFT);
            FontMetrics fm = g2.getFontMetrics();
            int x = Math.max(12, (getWidth() - fm.stringWidth(emptyMessage)) / 2);
            int y = Math.min(getHeight() - 20, Math.max(60, getHeight() / 3));
            g2.drawString(emptyMessage, x, y);
            g2.dispose();
        }
    }

    /**
     * Gaya tabel: baris dipisah warna selang-seling, dan tiap sel dibatasi garis tipis
     * mendatar maupun tegak. Dipakai semua tabel — data master, transaksi, dan laporan —
     * supaya seragam.
     *
     * <p>Warna selang-seling memudahkan mata mengikuti baris panjang, sedangkan garis
     * menjaga batas baris dan kolom tetap terlihat saat dicetak. Baris yang diwarnai
     * adalah baris ke-2, ke-4, dan seterusnya.
     */
    public static void styleTable(JTable t) {
        t.setRowHeight(ROW_HEIGHT);
        t.setShowHorizontalLines(true);
        t.setShowVerticalLines(true);
        t.setGridColor(LINE_PRINT);
        // Jarak antar sel disisakan 1 px; tanpa itu tidak ada tempat untuk menggambar
        // garis tegaknya, sehingga garis mendatar pun tampak putus-putus.
        t.setIntercellSpacing(new Dimension(1, 1));
        t.setFillsViewportHeight(true);
        t.setAutoCreateRowSorter(false);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setDefaultRenderer(Object.class, new CellRenderer(SwingConstants.LEFT));

        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, HEADER_HEIGHT));
        h.setFont(semibold(FONT_SIZE - 1f));
        h.setForeground(INK);
        h.setBackground(HEADER_BG);
        // Judul kolom rata kiri, mengikuti rata teks isinya.
        h.setDefaultRenderer(new HeaderRenderer(SwingConstants.LEFT));
    }

    /**
     * Atur lebar kolom tabel. Angka-angka ini menentukan judul kolom tidak terpotong;
     * ubah kalau ada judul yang masih terpangkas.
     *
     * <p>Kolom tetap ikut menyesuaikan lebar jendela, jadi seluruh kolom selalu terlihat
     * walau jendela dikecilkan — isi yang panjang dipotong dengan tanda titik-titik.
     */
    public static void widths(JTable t, int... preferred) {
        for (int i = 0; i < preferred.length && i < t.getColumnCount(); i++) {
            TableColumn c = t.getColumnModel().getColumn(i);
            c.setPreferredWidth(preferred[i]);
            c.setMinWidth(Math.min(preferred[i], 50));
        }
    }

    /**
     * Kunci lebar satu kolom pada ukuran tetap. Dipakai kolom yang isinya pendek dan
     * panjangnya bisa diduga — seperti nomor urut dan ID yang hanya satu sampai dua
     * angka. Tanpa dikunci, kolom itu ikut melar saat jendela diperlebar dan menyisakan
     * ruang kosong yang lebar.
     */
    public static void fixedWidth(JTable t, int column, int width) {
        if (column < 0 || column >= t.getColumnCount()) {
            return;
        }
        TableColumn c = t.getColumnModel().getColumn(column);
        c.setMinWidth(width);
        c.setMaxWidth(width);
        c.setPreferredWidth(width);
    }

    /**
     * Rata kanan untuk kolom angka, judulnya ikut.
     *
     * <p>Aturannya: PERATAAN JUDUL MENGIKUTI PERATAAN ISINYA. Kolom teks rata kiri
     * (bawaan), kolom angka rata kanan. Jadi judul dan isinya selalu berbagi tepi yang
     * sama, dan judulnya terbaca sebagai satu kepala untuk kolomnya.
     *
     * <p>Pernah dicoba sebaliknya - semua judul rata kiri, hanya angkanya rata kanan.
     * Hasilnya judul dan angkanya tidak berbagi tepi mana pun, sehingga terlihat seperti
     * dua baris yang lepas. Percobaan lain: semua rata kiri termasuk angkanya, tetapi itu
     * membuat angka yang panjangnya berbeda jadi bergerigi di kanan - dan pada tabel yang
     * dipakai mencocokkan uang, angka menyimpang justru yang harus paling cepat ketahuan.
     * Rata kanan membuat perbedaan besar-kecil angka langsung melompat.
     */
    public static void alignRight(JTable t, int... columns) {
        for (int i : columns) {
            if (i < t.getColumnCount()) {
                TableColumn c = t.getColumnModel().getColumn(i);
                c.setCellRenderer(new CellRenderer(SwingConstants.RIGHT));
                c.setHeaderRenderer(new HeaderRenderer(SwingConstants.RIGHT));
            }
        }
    }

    /** Judul kolom tabel: huruf setengah tebal kecil, rata sesuai isi kolomnya. */
    private static class HeaderRenderer extends DefaultTableCellRenderer {

        HeaderRenderer(int alignment) {
            setHorizontalAlignment(alignment);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setFont(semibold(FONT_SIZE - 1f));
            setForeground(INK);
            setBackground(HEADER_BG);
            setOpaque(true);
            return this;
        }
    }

    /**
     * Isi sel tabel. Memberi jarak kiri-kanan lewat border bawaan FlatLaf, dan — kalau
     * tabelnya memakai gaya ringkas — mewarnai baris ganjil sebagai pemisah baris.
     */
    private static class CellRenderer extends DefaultTableCellRenderer {

        CellRenderer(int alignment) {
            setHorizontalAlignment(alignment);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            if (!selected) {
                Color alt = UIManager.getColor("Table.alternateRowColor");
                setBackground(row % 2 == 1 && alt != null ? alt : table.getBackground());
            }
            return this;
        }
    }
}
