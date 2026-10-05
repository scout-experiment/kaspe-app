package kaspe.ui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Bilah samping: merek aplikasi, daftar halaman, dan nomor versi.
 *
 * <p>Menggantikan menu teks di tepi atas jendela. Deretan kata tanpa ikon dan tanpa
 * penanda halaman aktif tidak memberi tahu pengguna sedang berada di mana — bentuk
 * itulah yang membuat aplikasi terlihat kuno, bukan warnanya.
 *
 * <p>Dipisah dari {@link MainFrame} supaya pembuat gambar pratinjau
 * (tools/BuatPratinjau.java) bisa memakai bilah yang sama persis tanpa membuat jendela.
 * Kalau bilah ini ditanam langsung ke dalam jendela, gambar pratinjau akan diam-diam
 * berhenti mewakili aplikasi.
 */
public class NavBar extends JPanel {

    /**
     * Lebar bilah, dalam piksel.
     *
     * <p>Angka ini menentukan lebar yang tersisa untuk isi halaman. Tabel laporan punya
     * sebelas kolom dan paling lebar, jadi lebarnya diikat oleh kebutuhan tabel itu:
     * pada jendela bawaan 1320 px, bilah selebar ini masih menyisakan ruang yang cukup
     * untuk seluruh kolom laporan tampil utuh. Menaikkannya akan memotong kolom laporan,
     * dan pemotongan itu ikut tercetak ke kertas.
     */
    public static final int WIDTH = 160;

    private final List<Item> items = new ArrayList<>();
    /** Memastikan hanya satu halaman yang bertanda sedang dibuka. */
    private final ButtonGroup group = new ButtonGroup();
    /**
     * Panel transaksi yang disimpan, supaya dipakai lagi saat halamannya dibuka kembali.
     *
     * <p>Halaman lain dibuat baru setiap dibuka karena datanya harus segar, tetapi
     * halaman transaksi adalah pekerjaan yang sedang berjalan: isian yang sudah
     * diketik tetapi belum disimpan akan hilang tanpa peringatan kalau halamannya
     * dibuang setiap kali operator sempat melihat halaman lain.
     */
    private PanelTransaction panelTransaksi;
    /** Halaman yang sedang terbuka, untuk tahu kapan operator MENINGGALKAN transaksi. */
    private String halamanAktif;

    private NavBar(PagePanel page) {
        super(new BorderLayout());
        setBackground(Theme.CARD);
        setPreferredSize(new Dimension(WIDTH, 100));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.LINE));

        add(brand(), BorderLayout.NORTH);

        JPanel menu = new JPanel();
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setOpaque(false);
        menu.setBorder(BorderFactory.createEmptyBorder(4, 10, 0, 10));

        menu.add(entry(page, "Beranda", "Ringkasan catatan pengiriman singkong.", Icons.HOME));
        menu.add(gap(3));
        menu.add(entry(page, "Transaksi", "Catat pengiriman per truk.", Icons.NOTE));
        menu.add(gap(3));
        // Data master ditaruh tepat di bawah Transaksi, bukan di bawah Laporan. Ia bukan
        // halaman lagi — kliknya membuka dialog — tapi urutannya tetap mengikuti alur
        // pemakaian: catat pengiriman, lengkapi datanya, baru lihat laporan.
        menu.add(entry(page, "Data Master", "Kelola pemilik truk dan plat nomornya.", Icons.BUILDING));
        menu.add(gap(3));
        menu.add(entry(page, "Laporan", "Rekap penjualan per periode.", Icons.CHART));
        menu.add(Box.createVerticalGlue());
        add(menu, BorderLayout.CENTER);

        add(version(), BorderLayout.SOUTH);
        page.setNav(this);
        setActive("Beranda");
    }

    /** Bangun bilah samping yang mengarahkan halaman ke {@code page}. */
    public static NavBar build(PagePanel page) {
        return new NavBar(page);
    }

    /**
     * Buat halaman berdasarkan namanya.
     *
     * <p>Halaman dibuat baru setiap kali dibuka, sama seperti menu yang digantikannya —
     * jadi datanya selalu yang terbaru. SATU pengecualian: halaman transaksi disimpan
     * dan dipakai lagi, supaya isian yang sudah diketik tetapi belum disimpan tidak
     * hilang begitu operator membuka halaman lain. Daftar plat dan rentalnya tetap
     * disegarkan setiap dibuka kembali, jadi tetap mengikuti data master terbaru
     * tanpa menyentuh isian yang sedang dikerjakan.
     */
    private JPanel create(String name) {
        if ("Transaksi".equals(name)) {
            if (panelTransaksi == null) {
                panelTransaksi = new PanelTransaction();
            } else {
                panelTransaksi.refreshMaster();
            }
            return panelTransaksi;
        }
        if ("Laporan".equals(name)) {
            return new PanelReport();
        }
        return new PanelDashboard();
    }

    /**
     * Tandai satu halaman sebagai yang sedang dibuka.
     *
     * <p>Bisa dipanggil dari {@link PagePanel}, karena halaman juga bisa dibuka dari kode
     * lain — bukan hanya dari klik di bilah ini.
     */
    void setActive(String name) {
        halamanAktif = name;
        for (Item item : items) {
            item.setSelected(name.equals(item.name));
        }
    }

    /**
     * Benar kalau ada pekerjaan transaksi yang belum disimpan. Dipakai jendela utama
     * saat ingin ditutup, supaya menekan X tidak membuang pekerjaan itu diam-diam.
     */
    public boolean adaKerjaBelumDisimpan() {
        return panelTransaksi != null && panelTransaksi.adaKerjaBelumDisimpan();
    }

    /**
     * Bolehkah meninggalkan halaman transaksi menuju halaman lain?
     *
     * <p>Isian yang sudah diketik tetapi belum disimpan hanya ada di halaman itu;
     * pindah halaman lalu menyimpannya dari tempat lain mustahil. Karena itu
     * perpindahan ditanya dulu — bukan langsung dibuang seperti dulu.
     *
     * <p>Tanpa layar (uji otomatis) tidak ada operator yang bisa menjawab; perpindahan
     * dianggap boleh saja supaya jalurnya tetap teruji.
     */
    private boolean bolehTinggalkanTransaksi(String tujuan) {
        if (panelTransaksi == null || !"Transaksi".equals(halamanAktif)
                || "Transaksi".equals(tujuan)) {
            return true;
        }
        if (!panelTransaksi.adaKerjaBelumDisimpan()) {
            return true;
        }
        if (GraphicsEnvironment.isHeadless()) {
            return true;
        }
        int jwb = JOptionPane.showConfirmDialog(this,
                "Masih ada isian transaksi yang belum disimpan.\n"
                        + "Pindah ke " + tujuan + " dan tinggalkan isian itu?",
                "Belum disimpan", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return jwb == JOptionPane.YES_OPTION;
    }

    /**
     * Merek aplikasi: lambang dan namanya.
     *
     * <p>Tanpa keterangan tambahan di bawah nama. Bilah samping sengaja dibuat sempit
     * supaya tabel laporan tetap muat seluruhnya, dan keterangan panjang di sini akan
     * memaksa bilahnya melebar. Nama aplikasi yang lengkap sudah tertulis di judul jendela.
     */
    private static JPanel brand() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(18, 16, 16, 16));
        p.add(new JLabel(Icons.of(Icons.BRAND, Theme.ACCENT, 28)));

        JLabel name = new JLabel("Kaspe");
        name.setFont(Theme.bold(16f));
        name.setForeground(Theme.INK);
        p.add(name);
        return p;
    }

    /** Satu baris menu. */
    private Item entry(PagePanel page, String name, String subtitle, int icon) {
        Item item = new Item(name, subtitle, icon);
        group.add(item);
        item.addActionListener(e -> {
            if ("Data Master".equals(item.name)) {
                // Bukan halaman: dialog di atas halaman yang sedang terbuka.
                bukaDialogDataMaster();
                return;
            }
            if (!bolehTinggalkanTransaksi(item.name)) {
                // Batal pindah: sorotan menu dikembalikan ke halaman yang masih terbuka.
                setActive(halamanAktif);
                return;
            }
            setActive(item.name);
            page.showPanel(create(item.name), item.name, item.subtitle);
        });
        items.add(item);
        return item;
    }

    /**
     * Buka dialog data master tanpa berpindah halaman.
     *
     * <p>Data master bukan halaman lagi; dialognya muncul DI ATAS halaman yang sedang
     * terbuka. Karena halamannya tidak berubah, sorotan menu tidak boleh berpindah —
     * tapi klik pada tombol ganti sudah menandai entrinya terpilih, jadi setelah
     * dialognya ditutup sorotan dikembalikan ke halaman yang benar-benar terbuka.
     *
     * <p>Pemeriksaan pekerjaan yang belum disimpan tetap dijalankan: dialog modal dari
     * halaman Transaksi sama-sama menutupi pekerjaan yang sedang dikerjakan, sama
     * seperti kalau pindah halaman.
     */
    private void bukaDialogDataMaster() {
        if (!bolehTinggalkanTransaksi("Data Master")) {
            setActive(halamanAktif);
            return;
        }
        if (!GraphicsEnvironment.isHeadless()) {
            // buka() modal: baris berikutnya jalan setelah dialognya ditutup. Tanpa
            // layar (uji otomatis) dialog tidak bisa dibuat sama sekali, jadi
            // dilewati saja supaya jalur kliknya tetap teruji.
            DialogDataMaster.buka(SwingUtilities.getWindowAncestor(this));
        }
        setActive(halamanAktif);
        // Plat atau pemilik yang baru ditambah lewat dialog harus langsung terlihat di
        // halaman transaksi yang sedang dipakai ulang — operator yang membuka dialog
        // dari halaman itu masih berdiri di halaman yang sama, dan daftar platnya sudah
        // basi tanpa tanda apa pun kalau menunggu halaman dibuka ulang.
        if (panelTransaksi != null) {
            panelTransaksi.refreshMaster();
        }
    }

    /** Nomor versi di kaki bilah. */
    private static JLabel version() {
        JLabel l = new JLabel("Versi 1.0.1");
        l.setFont(Theme.semibold(11f));
        l.setForeground(Theme.INK_SOFT);
        l.setBorder(BorderFactory.createEmptyBorder(10, 22, 16, 16));
        return l;
    }

    private static Component gap(int height) {
        return Box.createRigidArea(new Dimension(1, height));
    }

    /**
     * Satu baris menu: lambang dan tulisan, dengan penanda saat sedang dibuka.
     *
     * <p>Dibuat dari {@link JToggleButton}, bukan label yang diberi pendengar tetikus.
     * Menu teks di tepi atas jendela yang digantikan bilah ini bisa dipakai tanpa tetikus;
     * kalau barisnya berupa label, halaman hanya bisa dibuka dengan tetikus dan itu
     * kemunduran untuk pekerjaan yang seluruhnya diketik. Tombol sudah bisa disorot dan
     * ditekan dengan papan ketik (Tab, lalu Enter atau Spasi), dan sorotan kursor di
     * atasnya didapat tanpa kode tambahan.
     *
     * <p>Tombolnya berjenis tombol ganti karena memang begitu keadaannya: hanya satu
     * halaman yang sedang dibuka, dan tombol ganti sudah mengenal keadaan "sedang dipilih"
     * sehingga warnanya bisa diatur sekali di awal, bukan dihitung ulang tiap kali pindah
     * halaman.
     */
    private static final class Item extends JToggleButton {

        private final String name;
        private final String subtitle;
        private final int shape;

        Item(String name, String subtitle, int shape) {
            super(name);
            this.name = name;
            this.subtitle = subtitle;
            this.shape = shape;

            setFont(Theme.semibold(Theme.FONT_SIZE));
            setIconTextGap(11);
            setHorizontalAlignment(SwingConstants.LEFT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(0, 0, 0, 0));

            // NavBar.WIDTH ditulis lengkap, tidak disingkat. Di dalam kelas turunan
            // komponen, nama WIDTH sudah dipakai konstanta lain dari ImageObserver yang
            // nilainya 1 — kalau ditulis singkat, lebarnya jadi 1 dikurangi 20, yaitu
            // negatif, dan baris menunya tidak tergambar sama sekali tanpa pesan kesalahan.
            Dimension size = new Dimension(NavBar.WIDTH - 20, 36);
            setPreferredSize(size);
            setMaximumSize(size);
            setAlignmentX(Component.LEFT_ALIGNMENT);

            // Warna diatur sekali di sini, termasuk warna saat kursor di atasnya dan saat
            // sedang dipilih. Mengaturnya berulang tiap kali pindah halaman membuat FlatLaf
            // memasang ulang gayanya terus-menerus, dan kunci gaya yang salah hanya
            // tercatat di berkas log tanpa menggagalkan apa pun.
            putClientProperty(FlatClientProperties.STYLE, ""
                    + "background: #FFFFFF;"
                    + "foreground: #1E242B;"
                    + "hoverBackground: #EDF4EF;"
                    + "hoverForeground: #2F7D4F;"
                    + "pressedBackground: #E4EAF0;"
                    + "selectedBackground: #EDF4EF;"
                    + "selectedForeground: #2F7D4F;"
                    + "borderWidth: 0;"
                    + "focusWidth: 1;"
                    + "innerFocusWidth: 0");

            pilih(false);
        }

        @Override
        public void setSelected(boolean aktif) {
            super.setSelected(aktif);
            pilih(aktif);
        }

        private void pilih(boolean aktif) {
            // Baris yang sedang dibuka diberi garis aksen di tepi kiri. Karena garis itu
            // memakan ruang, jarak kirinya dikurangi supaya tulisannya tetap lurus
            // dengan baris yang tidak aktif.
            setBorder(aktif
                    ? BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.ACCENT),
                            BorderFactory.createEmptyBorder(0, 8, 0, 12))
                    : BorderFactory.createEmptyBorder(0, 11, 0, 12));
            setIcon(Icons.menu(shape, aktif ? Theme.ACCENT : Theme.INK_SOFT));
        }
    }
}
