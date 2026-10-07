package kaspe.ui;

import kaspe.Calculator;
import kaspe.dao.BackupDao;
import kaspe.dao.MasterDao;
import kaspe.dao.TransactionDao;
import kaspe.model.*;
import kaspe.util.Dates;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Halaman catat pengiriman: satu form untuk satu pengiriman truk, dan daftar
 * pengiriman yang sudah tersimpan.
 *
 * <p>Satu pengiriman = satu catatan sendiri: tidak ada "nota" yang mengumpulkan
 * beberapa baris, dan dua pengiriman truk yang sama pada hari yang sama tetap dua
 * catatan terpisah. Mengisi form lalu menekan Simpan langsung menyimpan pengiriman
 * itu. Berat bersih dan jumlah uang dihitung otomatis.
 */
public class PanelTransaction extends JPanel {

    private final JSpinner spDate = dateSpinner();
    /**
     * Plat truk. Bisa dipilih dari daftar plat yang sudah pernah masuk, bisa juga
     * langsung diketik. Plat yang belum ada tidak perlu didaftarkan dulu di halaman
     * data master — dibuat sendiri saat pengirimannya disimpan.
     */
    private final JComboBox<String> cmbPlate = new JComboBox<>();
    /** Pemilik truk. Terisi sendiri kalau platnya dipilih dari daftar. */
    private final JComboBox<Rental> cmbRental = new JComboBox<>();

    private final JTextField txtFieldWeight = new JTextField();
    private final JTextField txtFactoryWeight = new JTextField();
    private final JTextField txtRefraction = new JTextField("15");
    private final JTextField txtPrice = new JTextField();
    /**
     * Centang "sudah dibayar". Tanpa centang, tanggal lunas dicatat kosong —
     * kolom tanggal_lunas di database memang boleh NULL untuk pengiriman yang
     * belum dibayar.
     */
    private final JCheckBox chkPaid = new JCheckBox("Sudah dibayar", true);
    private final JSpinner spPaid = dateSpinner();
    private final JLabel lblNetWeight = valueLabel();
    private final JLabel lblTotalAmount = valueLabel();
    private final JLabel lblStatus = new JLabel();
    /** Judul kartu form: "Catat Pengiriman" saat menambah, "Ubah Pengiriman" saat mengubah. */
    private final JLabel judulKartu = new JLabel("Catat Pengiriman");
    private final JButton btnSimpan = Theme.primary("Simpan");
    /** Kembali ke keadaan tambah; hanya tampil saat sedang mengubah catatan lama. */
    private final JButton btnBatal = Theme.plain("Batal");
    private final JButton btnUbah = Theme.plain("Ubah");
    private final JButton btnHapus = Theme.plain("Hapus");

    // Saringan daftar pengiriman tersimpan. Berbeda dari isian form di atasnya,
    // isian ini tidak menyimpan apa pun: hanya mempersempit baris mana yang dibaca
    // dari database.
    private final JSpinner spFilterFrom = dateSpinner();
    private final JSpinner spFilterTo = dateSpinner();
    private final JComboBox<Object> cmbFilterRental = new JComboBox<>();
    private final JTextField txtFilterPlat = new JTextField();
    private final JButton btnFilterTampilkan = Theme.primary("Tampilkan");
    private final JButton btnFilterSemua = Theme.plain("Semua");
    /** Keterangan total di bawah; teksnya berubah saat daftar sedang tersaring. */
    /** Pilihan pertama kotak rental saringan: tanpa penyaring rental. */
    private static final String SEMUA_RENTAL = "Semua rental";

    /**
     * Daftar pengiriman tersimpan, satu baris per pengiriman. Kolom 0 menampung
     * id_detail sebagai identitas baris dan disembunyikan (lihat
     * {@link #sembunyikanKolomId()}); sepuluh kolom berikutnya sama dengan tabel
     * laporan.
     */
    private final DefaultTableModel riwayatModel = new DefaultTableModel(
            new Object[]{"Id", "Tanggal", "Plat", "Rental", "Bobot Lapak", "Bobot Pabrik",
                    "Refraksi", "Berat Bersih", "Tgl Lunas", "Harga", "Jumlah Uang"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final Theme.Table riwayatTable = new Theme.Table(riwayatModel,
            "Belum ada pengiriman tersimpan. Pengiriman yang sudah disimpan muncul di sini.");
    /** Gulungan yang memuat form dan daftar; digulir ke atas saat catatan lama diubah. */
    private JScrollPane gulungIsi;
    /** Id catatan pengiriman yang sedang diubah, atau null kalau sedang mencatat yang baru. */
    private Integer detailDiubah;
    /**
     * Penjaga klik ganda pada aksi yang membuka dialog. Dialog modal menjalankan
     * putaran kejadian sendiri, jadi klik kedua yang mengantre bisa terkirim saat
     * dialog masih terbuka — lihat {@link #save()} dan {@link #hapusTerpilih()}.
     */
    private boolean sedangProses;
    /** Daftar truk yang sudah dikenal, dicari berdasarkan platnya. */
    private final Map<String, Truck> trukPerPlat = new LinkedHashMap<>();
    /** Teks plat yang pilihan rentalnya sudah ikut disamakan. Lihat {@link #platDiketik}. */
    private String platTersinkron;
    private final MasterDao masterDao = new MasterDao();
    private final TransactionDao transactionDao = new TransactionDao();

    public PanelTransaction() {
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        add(buildCenter(), BorderLayout.CENTER);
        cmbPlate.setEditable(true);
        // Nama rental juga bisa diketik langsung, sama seperti plat: rental yang belum
        // pernah masuk tidak perlu didaftarkan dulu di halaman data master.
        cmbRental.setEditable(true);
        // Dua-duanya dipasang, dan dua-duanya lewat penyaring yang sama, karena keduanya
        // menangkap kejadian yang berbeda:
        //
        // - ActionListener menyala saat plat DIPILIH dari daftar, saat Enter ditekan, dan
        //   saat fokus meninggalkan kotak plat. Yang terakhir itu yang penting: Swing
        //   menyampaikan tulisan yang selesai sebagai ActionEvent, bukan sebagai perubahan
        //   teks, sehingga jalur ini tidak boleh memanggil penyamaannya secara langsung.
        // - Pendengar perubahan teks menyala saat plat DIKETIK. Ini yang paling penting:
        //   tanpa ini, mengetik plat baru tidak menyentuh pilihan rental sama sekali,
        //   sehingga rental pengiriman sebelumnya ikut terbawa sebagai pemilik truk yang
        //   baru — dan itu langsung salah di catatan uang, tanpa pesan apa pun.
        cmbPlate.addActionListener(e -> platDiketik());
        // Kotak tanggal lunas hanya aktif kalau pengirimannya ditandai sudah dibayar.
        // Tanpa centang, tanggal lunas dicatat kosong (belum dibayar).
        chkPaid.setOpaque(false);
        chkPaid.addItemListener(e -> spPaid.setEnabled(chkPaid.isSelected()));
        komponenEditor(cmbPlate).getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { platDiketik(); }
            public void removeUpdate(DocumentEvent e) { platDiketik(); }
            public void changedUpdate(DocumentEvent e) { platDiketik(); }
        });
        btnSimpan.addActionListener(e -> save());
        btnBatal.addActionListener(e -> kembaliKeTambah());
        btnUbah.addActionListener(e -> ubahPengiriman());
        btnHapus.addActionListener(e -> hapusTerpilih());
        // Saringan daftar: tombol Tampilkan dan Enter pada kotak plat memuat ulang
        // daftarnya; tombol Semua mengembalikan keadaan tanpa saringan dulu, baru
        // memuat ulang.
        txtFilterPlat.addActionListener(e -> muatRiwayat());
        btnFilterTampilkan.addActionListener(e -> muatRiwayat());
        btnFilterSemua.addActionListener(e -> bersihkanSaringan());
        // Kedua tombol mengikuti pilihan di daftar: Ubah hanya kalau tepat satu baris
        // (kalau dua, tidak jelas mana yang mau diubah), Hapus boleh satu atau lebih.
        // Mengubah dan menghapus catatan HANYA lewat tombolnya, tidak lewat klik ganda di
        // barisnya: klik ganda adalah masukan yang mudah tidak disengaja (klik yang
        // terasa lambat terkirim sebagai dua klik), dan di halaman yang memegang angka
        // uang, satu masukan ragu-ragu tidak boleh sampai menjalankan aksi.
        riwayatTable.getSelectionModel().addListSelectionListener(e -> perbaruiTombolRiwayat());
        loadMaster();
        setupAutoCalculate();
        // Form dibuka dalam keadaan menambah: tanggal hari ini, dan dianggap sudah
        // dibayar — keadaan yang paling sering.
        spDate.setValue(new Date());
        chkPaid.setSelected(true);
        spPaid.setValue(new Date());
        // Daftar dibuka dalam keadaan tanpa saringan (lihat bersihkanSaringan).
        muatDaftarRentalSaringan();
        bersihkanSaringan();
    }

    /**
     * Kotak isian di dalam combo yang bisa diketik.
     *
     * <p>Combo yang bisa diketik menyimpan tulisannya di dalam kotak isian miliknya
     * sendiri, bukan di daftar pilihannya. Perubahan tulisan itu hanya bisa dipantau
     * lewat kotak isian tersebut.
     */
    private static JTextField komponenEditor(JComboBox<?> combo) {
        return (JTextField) combo.getEditor().getEditorComponent();
    }

    /**
     * Label hasil hitungan: berat bersih dan jumlah uang.
     *
     * <p>Keduanya memakai ukuran dan ketebalan huruf yang SAMA. Jumlah uang sempat lebih
     * besar (tebal 17) daripada berat bersih (setengah tebal 16), sehingga satu angka di
     * dalam satu kotak yang sama terlihat lebih penting daripada angka di sebelahnya -
     * padahal keduanya sederajat: yang satu beratnya, yang satu uangnya.
     */
    private static JLabel valueLabel() {
        JLabel l = new JLabel(EMPTY);
        l.setFont(Theme.semibold(16f));
        l.setForeground(Theme.INK_SOFT);
        return l;
    }

    /** Isi awal kotak hasil sebelum angkanya ada. */
    private static final String EMPTY = "\u2014";

    /**
     * Lebar kolom kisi isian — lebar <b>minimum</b>, bukan lebar mati.
     *
     * <p>Kolom melebar mengikuti sel terlebarnya, dan {@code fill = HORIZONTAL} membuat sel
     * yang lebih sempit ikut memenuhi kolomnya. Jadi kedua baris selalu berakhir di tepi yang
     * sama, dan tidak ada isian yang bisa terpotong walau hurufnya berganti — beda dengan
     * mematok lebar komposit secara pasti, yang menyisakan nol kelonggaran begitu hurufnya
     * sedikit lebih lebar.
     */
    private static final int KOL0 = 150;
    private static final int KOL1 = 150;
    private static final int KOL2 = 170;
    private static final int KOL3 = 150;

    /** Tulisi hasil hitungan dan warnai sesuai keadaannya. */
    private static void setValue(JLabel label, String text) {
        boolean kosong = text == null || EMPTY.equals(text);
        label.setText(kosong ? EMPTY : text);
        label.setForeground(kosong ? Theme.INK_SOFT : Theme.MONEY);
    }

    /** Samakan tinggi semua kotak isian supaya barisnya lurus. */
    private static <T extends JComponent> T sized(T c, int width) {
        c.setPreferredSize(new Dimension(width, Theme.FIELD_HEIGHT));
        return c;
    }

    /**
     * Kotak plat dan tombol "Kelola" di sebelahnya: satu-satunya jalan ke dialog
     * Kelola Data Truk, karena entri menunya di bilah samping sudah dihapus.
     *
     * <p>Labelnya sengaja pendek, jadi keterangan lengkapnya ada di petunjuk tombol;
     * tombolnya juga tetap punya nama akses dan bisa disorot dengan papan ketik.
     */
    private JComponent barisPlat() {
        // Tombol pintu masuk dialog. Berlabel, bukan ikon polos: ini satu-satunya jalan
        // mengelola plat dan pemilik, dan tombol ikon tanpa teks hanya terbaca lewat
        // tooltip - perlu di-hover. Halaman ini sudah pernah menolak pola "ada di layar
        // tapi tidak kelihatan" untuk menu klik-kanan.
        //
        // Kotak platnya dikecilkan 200 -> 150px supaya labelnya muat: baris form ini
        // punya sisa sangat sedikit pada MainFrame.LEBAR_MINIMUM, dan tanpa mengecilkan
        // kotak plat, tombol berlabel akan meluap dan menggagalkan penjaga "tombol tidak
        // terpotong". 150px masih cukup untuk plat terpanjang ("BE 0000 ZZ") plus panahnya.
        JButton btnMaster = Theme.plain("Kelola");
        btnMaster.setIcon(Icons.of(Icons.BUILDING, Theme.INK_SOFT, 16));
        btnMaster.setToolTipText("Kelola Data Truk: tambah, ubah, atau hapus plat dan pemiliknya");
        btnMaster.getAccessibleContext().setAccessibleName("Kelola Data Truk");
        btnMaster.addActionListener(e -> {
            // JDialog melempar HeadlessException tanpa layar, sedangkan uji berjalan
            // tanpa layar — dialognya dilewati saja supaya jalur kliknya tetap teruji.
            if (!GraphicsEnvironment.isHeadless()) {
                DialogDataMaster.buka(SwingUtilities.getWindowAncestor(this));
            }
            // Plat atau pemilik yang baru ditambah lewat dialog harus langsung terlihat
            // di kotak pilihan ini. Aman dipanggil di luar kawalan headless: menyegarkan
            // daftar tidak butuh layar, dan tanpa dialog yang terbuka isinya memang
            // tidak berubah.
            refreshMaster();
        });

        return Theme.row(6, sized(cmbPlate, 150), btnMaster);
    }


    private JPanel buildCenter() {
        JPanel outer = new JPanel(new BorderLayout(0, 12));
        outer.setOpaque(false);
        outer.add(buildInputCard(), BorderLayout.NORTH);
        outer.add(buildRiwayatCard(), BorderLayout.CENTER);

        // Form dan daftar digulir bersama. Tinggi yang diminta keduanya bisa melebihi
        // jendela bawaan, dan BorderLayout membagi ruang sisa dengan mengorbankan
        // bagian CENTER — sampai tingginya nol dan daftarnya tidak tergambar sama
        // sekali. Di dalam gulungan, tiap bagian memakai tinggi yang dimintanya,
        // jadi tidak ada yang terhimpit jadi nol. Halaman yang cukup tinggi tidak
        // menampakkan gulungan ini sama sekali.
        gulungIsi = new JScrollPane(outer);
        gulungIsi.setBorder(BorderFactory.createEmptyBorder());
        gulungIsi.setOpaque(false);
        gulungIsi.getViewport().setOpaque(false);
        gulungIsi.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        gulungIsi.getVerticalScrollBar().setUnitIncrement(16);

        JPanel wadah = new JPanel(new BorderLayout());
        wadah.setOpaque(false);
        wadah.add(gulungIsi, BorderLayout.CENTER);
        return wadah;
    }

    /**
     * Form satu pengiriman.
     *
     * <p>Keterangan tiap kotak ditaruh di atas kotaknya, bukan di sampingnya. Susunan
     * mendatar "label – kotak – label – kotak" membuat mata harus melompati celah yang
     * lebarnya berbeda-beda di tiap baris; susunan bertumpuk membaca satu arah saja.
     *
     * <p>Isiannya tersusun dua baris empat kolom, dan tepi kiri setiap kotak lurus dari
     * atas ke bawah, sehingga mata tidak perlu mencari ulang kolomnya di tiap baris.
     *
     * <p>Kotak hasil hitungan dan tombol Simpan tidak ikut di dalam kisi isian: keduanya
     * ditaruh di kolom kanan tersendiri, dengan hasil di atas tombolnya. Angka yang dibaca
     * sebelum menyimpan jadi berdampingan dengan tombol yang ditekan sesudahnya.
     */
    private JPanel buildInputCard() {
        // Judul kartu dibuat sendiri (bukan lewat Theme.card(String)) supaya bisa
        // diganti teksnya saat berpindah antara menambah dan mengubah.
        judulKartu.setFont(Theme.semibold(Theme.FONT_SIZE + 1f));
        judulKartu.setForeground(Theme.INK);
        judulKartu.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        JPanel card = Theme.card();
        card.add(judulKartu, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        // Kedua baris memakai empat kolom yang sama, jadi tepi kiri DAN kanan tiap isian
        // lurus dengan pasangannya di baris sebelah. fill HORIZONTAL yang membuatnya: tiap
        // sel dipaksa memenuhi lebar kolomnya.
        //
        // Dulu di sini fill NONE, supaya tiap isian tetap seukuran yang ditetapkan. Akibatnya
        // tepi kanan kedua baris berbeda 139px dan di tengah baris kedua menganga lubang
        // 151px antara "Refraksi" dan "Harga" - isian yang lebih sempit daripada kolomnya
        // memang menyisakan ruang kosong di sebelahnya. Kerapian tepi dianggap lebih penting
        // daripada isian yang tetap sempit, jadi isian baris kedua ikut selebar kolomnya.
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;

        Theme.placeholder(txtFieldWeight, "kg");
        Theme.placeholder(txtFactoryWeight, "kg");
        Theme.placeholder(txtRefraction, "%");
        Theme.placeholder(txtPrice, "Rp/kg");

        // Dua baris, empat kolom. Dulu baris pertama berisi lima isian dan baris kedua
        // tiga, sehingga kolomnya tidak lurus: "Refraksi" jatuh di bawah "Tanggal Nota"
        // dan "Harga" di bawah "Plat / Truk", bukan di bawah isian yang sejenis. Dengan
        // empat kolom di kedua baris, tepi kiri setiap isian lurus dari atas ke bawah.
        //
        // Urutan isian tetap mengikuti urutan pengisian, jadi jalur Tab tidak berubah:
        // tanggal, plat (rental ikut sendiri), dua timbangan, potongan, harga, lunas.
        g.gridy = 0;
        g.insets = new Insets(0, 0, 8, 14);
        g.gridx = 0;
        grid.add(Theme.field("Tanggal Nota", sized(spDate, KOL0)), g);
        g.gridx = 1;
        grid.add(Theme.field("Plat / Truk", barisPlat()), g);
        g.gridx = 2;
        grid.add(Theme.field("Rental", sized(cmbRental, KOL2)), g);
        g.gridx = 3;
        g.insets = new Insets(0, 0, 8, 0);
        grid.add(Theme.field("Bobot Lapak (kg)", sized(txtFieldWeight, KOL3)), g);
        addSpacer(grid, g, 4);

        // baris 2 — sisa timbangan, potongan, harga, dan pembayarannya.
        g.gridy = 1;
        g.insets = new Insets(0, 0, 0, 14);
        g.gridx = 0;
        grid.add(Theme.field("Bobot Pabrik (kg)", sized(txtFactoryWeight, KOL0)), g);
        g.gridx = 1;
        grid.add(Theme.field("Refraksi (%)", sized(txtRefraction, KOL1)), g);
        g.gridx = 2;
        grid.add(Theme.field("Harga (Rp/kg)", sized(txtPrice, KOL2)), g);
        g.gridx = 3;
        g.insets = new Insets(0, 0, 0, 0);
        // Centang "Sudah dibayar" + tanggal lunasnya dalam satu kotak yang sama:
        // keadaan "belum dibayar" adalah bagian dari isian tanggal lunas, bukan isian lain.
        // Dulu kotak ini duduk di bawah kolom "Rental", sehingga pembayaran terlihat
        // seperti bagian dari pemilik truk; sekarang ia menutup barisnya sendiri.
        JPanel kotakLunas = Theme.row(6, chkPaid, sized(spPaid, 130));
        grid.add(Theme.field("Tanggal Lunas", kotakLunas), g);
        addSpacer(grid, g, 4);

        // Isian di kiri, hasil hitungan dan tombol simpan di kanan.
        JPanel isi = new JPanel(new BorderLayout(28, 0));
        isi.setOpaque(false);
        isi.add(grid, BorderLayout.CENTER);
        isi.add(buildRightColumn(), BorderLayout.EAST);

        card.add(isi, BorderLayout.CENTER);

        // Keterangan ditaruh di dalam kartu ini, selebar kartu. Sebelumnya ia di dasar
        // halaman, sekitar 460px di bawah tombol Simpan - pesan validasi seperti
        // "bobot pabrik dan refraksi wajib diisi" jadi muncul jauh dari tombol yang
        // menyebabkannya. Selebar kartu supaya pesan panjang tidak terpotong.
        lblStatus.setForeground(Theme.DANGER);
        card.add(lblStatus, BorderLayout.SOUTH);
        setStatus("");
        return card;
    }

    /**
     * Kolom kanan kartu: hasil hitungan berdiri di atas tombol simpannya.
     *
     * <p>Ditaruh di sini supaya angka yang dibaca sebelum menyimpan dan tombol yang
     * ditekan sesudahnya berdampingan, dan supaya sisi kanan kartu yang tadinya kosong
     * terpakai. Lebar kolom ini ditentukan isinya sendiri, jadi tulisan tombol yang
     * berubah panjang ("Simpan" menjadi "Simpan Perubahan") tidak pernah terpotong.
     */
    private JPanel buildRightColumn() {
        JPanel kanan = new JPanel(new BorderLayout(0, 12));
        kanan.setOpaque(false);
        // Jarak ke tepi kanan kartu. Kolom ini dipaku ke tepi kanan oleh BorderLayout,
        // jadi satu-satunya cara menggesernya ke kiri adalah memberi jarak di sini -
        // memperkecil hurufnya tidak memindahkannya, hanya mempersempit kotak hasilnya.
        // Tepi kartu sendiri hanya memberi 16px, dan dengan tambahan itu tombolnya
        // terlihat menempel di tepi.
        kanan.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 48));
        kanan.add(buildResult(), BorderLayout.NORTH);

        JPanel tombol = new JPanel(new BorderLayout());
        tombol.setOpaque(false);
        // Tombol Simpan TIDAK diberi jarak tepi: lebarnya harus persis selebar kotak
        // hasil di atasnya, supaya angka yang dibaca dan tombol yang ditekan berdiri
        // pada satu kolom dengan tepi kiri dan kanan yang lurus. Sebelumnya tombolnya
        // diberi jarak masuk sebesar STRIP_INSET supaya tepi kirinya sejajar dengan
        // TULISAN di dalam kotak - tepinya jadi 15px masuk dari tepi kotaknya, dan
        // kolom kanan itu terlihat miring walaupun tulisannya lurus.
        tombol.add(buildActions(), BorderLayout.NORTH);
        kanan.add(tombol, BorderLayout.CENTER);
        return kanan;
    }

    /** Kolom kosong penyerap sisa lebar, supaya isian tetap menempel ke kiri. */
    private static void addSpacer(JPanel grid, GridBagConstraints g, int x) {
        g.gridx = x;
        g.gridwidth = 1;
        g.weightx = 1;
        g.insets = new Insets(0, 0, 0, 0);
        grid.add(new JLabel(), g);
        g.weightx = 0;
    }

    /** Kotak sorot: hasil hitungan pengiriman yang sedang diisi. */
    private JPanel buildResult() {
        JPanel p = Theme.strip();
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;

        // Bertumpuk tegak, bukan berdampingan: kolom kanan ini sempit, dan
        // "Jumlah Uang" adalah angka yang paling sering dibaca sebelum menyimpan.
        g.gridy = 0;
        g.insets = new Insets(0, 0, 2, 0);
        p.add(Theme.caption("Berat Bersih"), g);
        g.gridy = 1;
        g.insets = new Insets(0, 0, 10, 0);
        p.add(lblNetWeight, g);

        g.gridy = 2;
        g.insets = new Insets(0, 0, 2, 0);
        p.add(Theme.caption("Jumlah Uang"), g);
        g.gridy = 3;
        g.insets = new Insets(0, 0, 0, 0);
        p.add(lblTotalAmount, g);

        // Penyerap sisa lebar. Kotak ini melebar mengikuti kolomnya, dan GridBagLayout
        // MENENGAHKAN isinya di kelebihan lebar itu - jadi tulisan "Berat Bersih" bergeser
        // mengikuti lebar kotak, bukan menempel di jarak tepinya. Dengan penyerap ini,
        // tulisannya terpaku di jarak tepi yang tetap, berapa pun lebar kotaknya.
        g.gridy = 0;
        g.gridx = 1;
        g.weightx = 1;
        p.add(new JLabel(), g);
        return p;
    }

    /**
     * Tombol simpan, bertumpuk tegak supaya lebarnya mengikuti kolom kanannya.
     *
     * <p>Batal hanya ada saat mengubah catatan lama; di keadaan menambah tidak ada
     * artinya (mengosongkan form saja sudah mengembalikan keadaan tambah). GridLayout
     * hanya menghitung komponen yang tampak, jadi barisnya tidak menyisakan lubang
     * saat Batal disembunyikan.
     */
    private JPanel buildActions() {
        btnBatal.setVisible(false);
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 8));
        p.setOpaque(false);
        p.add(btnSimpan);
        p.add(btnBatal);
        return p;
    }

    /**
     * Sembunyikan kolom 0 (id_detail). Identitas baris disimpan di model tabel,
     * bukan di daftar yang sejajar dengannya: begitu ada saringan atau pengurutan,
     * nomor baris di layar tidak lagi sama dengan nomor di daftar asal, dan aksi
     * pada "baris ke-n" bisa mengenai catatan yang salah tanpa pesan apa pun.
     */
    private void sembunyikanKolomId() {
        TableColumn c = riwayatTable.getColumnModel().getColumn(0);
        c.setMinWidth(0);
        c.setMaxWidth(0);
        c.setPreferredWidth(0);
        c.setResizable(false);
    }

    /**
     * Kartu "Transaksi Tersimpan": pengiriman yang sudah masuk database, satu baris
     * per pengiriman, dengan tombol ubah dan hapusnya. Kartu ini mengisi sisa ruang
     * halaman; daftarnya menggulir di dalam kartunya sendiri.
     */
    private JPanel buildRiwayatCard() {
        Theme.styleTable(riwayatTable);
        // Lebar kolom sama dengan tabel laporan — sudah teruji muat utuh di jendela
        // bawaan — dengan satu angka 0 di depan untuk kolom id yang disembunyikan.
        // Angka yang lebih besar dari tabel laporan (Tanggal, Bobot Lapak, Bobot Pabrik,
        // Refraksi, Berat Bersih) memberi ruang ikon panah urut yang muncul di judul
        // kolomnya; tanpa itu judulnya terpotong begitu kolomnya diklik untuk mengurutkan.
        Theme.widths(riwayatTable, 0, 105, 97, 127, 108, 110, 83, 106, 105, 89, 120);
        sembunyikanKolomId();
        // Semua nomor kolom bergeser satu karena kolom id tersembunyi di depannya.
        // Keseleo satu angka di sini tidak ditangkap uji apa pun — hati-hati.
        Theme.alignRight(riwayatTable, 4, 5, 6, 7, 9, 10);
        Theme.emphasis(riwayatTable, 10);
        // Kolom bisa diurut dengan mengklik judulnya, sama seperti tabel laporan. Ini
        // daftar yang dipakai mencari satu pengiriman yang salah catat, dan mencarinya
        // menurut plat atau jumlah uang jauh lebih cepat daripada menggulir. Urutan
        // bawaan tetap yang terbaru di atas (dari query) sampai ada judul yang diklik.
        //
        // Semua bacaan baris di halaman ini sudah lewat convertRowIndexToModel, jadi
        // nomor baris yang dipakai selalu nomor catatannya, bukan nomor tampilannya -
        // itu syarat mutlak sebelum pengurutan dinyalakan di sini: tanpa itu, mengurut
        // lalu menghapus akan menghapus catatan yang salah.
        //
        // Pengurutnya dinyalakan di sini. Theme.sortTanggal/sortAngka/sortTeks juga
        // membuatnya sendiri kalau belum ada, jadi urutan pemanggilan tidak berpengaruh -
        // dulu urutan yang salah membuat pembandingnya dipasang ke pengurut yang belum ada
        // dan hilang tanpa suara, sehingga tabelnya diam-diam mengurut menurut tulisan lagi.
        riwayatTable.setAutoCreateRowSorter(true);
        // Angka dan tanggal dibandingkan menurut nilainya, bukan menurut tulisannya -
        // sama seperti tabel laporan. Nomor kolomnya bergeser satu karena kolom id
        // tersembunyi di depannya.
        Theme.sortTanggal(riwayatTable, 1, 8);
        Theme.sortAngka(riwayatTable, 4, 5, 6, 7, 9, 10);
        // Nama plat dan rental dibandingkan tanpa bergantung setelan bahasa komputer,
        // sama seperti tabel laporan.
        Theme.sortTeks(riwayatTable, 2, 3);
        // Theme.styleTable memasang pilihan tunggal untuk semua tabel; daftar ini
        // justru harus bisa memilih beberapa baris sekaligus untuk hapus sekali jalan.
        riwayatTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JPanel card = Theme.card("Transaksi Tersimpan");
        JPanel isi = new JPanel(new BorderLayout(0, 10));
        isi.setOpaque(false);
        // Baris saringan ditaruh DI DALAM kartu, di antara judulnya dan tabelnya:
        // saringan ini milik daftar ini saja, bukan saringan halaman.
        isi.add(buildSaringanRiwayat(), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(riwayatTable);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        isi.add(scroll, BorderLayout.CENTER);

        // Kedua tombol mati sampai ada baris yang dipilih; pembaruan keadaannya
        // dipasang di konstruktor lewat pendengar pilihan.
        btnUbah.setEnabled(false);
        btnHapus.setEnabled(false);
        JPanel ubahHapus = Theme.row(8, btnUbah, btnHapus);

        // Cadangan database ditaruh di ujung kanan baris tombol yang sama, bukan di
        // baris baru: tinggi halaman ini dipatok (patokan kartu di bawah), dan baris
        // tambahan memakan ruang daftar pengiriman di atasnya.
        JButton btnCadangkan = Theme.plain("Cadangkan Database");
        btnCadangkan.addActionListener(e -> cadangkanDatabase());
        JPanel cadangan = Theme.rowRight(8, btnCadangkan, Theme.caption(
                "Menyimpan satu berkas cadangan bertanggal berisi seluruh isi buku catatan."));

        JPanel tombol = new JPanel(new BorderLayout());
        tombol.setOpaque(false);
        tombol.add(ubahHapus, BorderLayout.WEST);
        tombol.add(cadangan, BorderLayout.EAST);
        isi.add(tombol, BorderLayout.SOUTH);

        card.add(isi, BorderLayout.CENTER);
        // Tingginya dipatok supaya daftarnya menggulir DI DALAM kartunya sendiri.
        // Tanpa patokan ini kartu ikut setinggi seluruh isinya: dengan puluhan
        // pengiriman, tombol Ubah/Hapus terdorong jauh ke bawah halaman dan harus
        // digulir dulu untuk sampai - padahal tombol itulah gunanya daftar ini.
        // Baris saringan menambah ±40px; patokan 310 menjaga tabel tetap mendapat
        // sekitar 170px area pandang pada jendela bawaan 1320x760, di atas ambang
        // uji 80px.
        card.setPreferredSize(new Dimension(0, 310));
        card.setMinimumSize(new Dimension(0, 200));
        return card;
    }

    /**
     * Baris saringan daftar pengiriman tersimpan: rentang tanggal, rental, dan
     * sebagian plat. Semua isian dijaga seukuran tetap supaya barisnya tetap satu
     * baris pada jendela bawaan; sisa lebarnya diserap kosong di ujung kanan.
     */
    private JPanel buildSaringanRiwayat() {
        return Theme.row(10,
                Theme.label("Dari"), sized(spFilterFrom, 110),
                Theme.label("Sampai"), sized(spFilterTo, 110),
                Theme.label("Rental"), sized(cmbFilterRental, 150),
                Theme.label("Plat"), sized(txtFilterPlat, 120),
                btnFilterTampilkan, btnFilterSemua);
    }

    /**
     * Isi ulang daftar pengiriman tersimpan menurut saringan yang sedang tertulis,
     * satu baris per pengiriman, yang paling baru di atas (urutan dari
     * {@link TransactionDao#listDeliveries}). Total di bawah mengikuti baris yang
     * sedang terdaftar, bukan seluruh data sepanjang masa.
     *
     * <p>Saringannya dijalankan DI DATABASE, bukan disembunyikan di layar. Kalau
     * barisnya hanya disembunyikan di layar, tabel menampilkan tiga baris sementara
     * totalnya tetap menuliskan jumlah seluruh catatan — operator membacanya sebagai
     * total yang salah, bukan daftar yang tersaring. Dengan saringan di database,
     * baris dan total selalu berasal dari hasil query yang sama, jadi keduanya tidak
     * bisa berbeda.
     *
     * <p>Sebab itu juga isian saringan dibaca ulang di sini setiap kali: daftar ini
     * dimuat ulang setelah simpan, ubah, hapus, dan gagal hapus — semuanya harus
     * memakai keadaan saringan yang SEDANG berlaku, bukan keadaan saat terakhir
     * tombol Tampilkan ditekan.
     */
    private void muatRiwayat() {
        LocalDate dari = bacaTanggal(spFilterFrom);
        LocalDate sampai = bacaTanggal(spFilterTo);
        // Batas tanggal yang tidak terbaca dikirim TIDAK terbatas, bukan menolak
        // memuat: tulisan tanggal yang salah tidak boleh mengosongkan daftar diam-diam.
        Object pilihanRental = cmbFilterRental.getSelectedItem();
        String rental = pilihanRental instanceof Rental ? ((Rental) pilihanRental).getRentalName() : null;
        String plat = txtFilterPlat.getText();
        List<ReportRow> baris;
        try {
            baris = transactionDao.listDeliveries(dari, sampai, rental, plat);
        } catch (Exception e) {
            Theme.showError(this, e);
            return;
        }
        riwayatModel.setRowCount(0);
        for (ReportRow b : baris) {
            riwayatModel.addRow(new Object[]{
                    b.getDetailId(),
                    Dates.format(b.getDate()),
                    b.getPlate(), b.getRentalName(),
                    Calculator.formatKg(b.getFieldWeight()), Calculator.formatKg(b.getFactoryWeight()),
                    Calculator.formatPercent(b.getRefractionPercent()), Calculator.formatKg(b.getNetWeight()),
                    Dates.format(b.getPaymentDate()), "Rp " + Calculator.formatNumber(b.getPrice()),
                    "Rp " + Calculator.formatNumber(b.getTotalAmount())});
        }
        boolean tersaring = saringanAktif(dari, sampai, rental, plat);
        // Dua keadaan kosong yang berbeda butuh penjelasan berbeda: belum punya data
        // sama sekali, versus ada data tetapi tidak ada yang lolos saringan. Pesan
        // yang sama untuk keduanya membuat pengguna menduga aplikasinya rusak.
        riwayatTable.setEmptyMessage(baris.isEmpty()
                ? (tersaring
                        ? "Tidak ada pengiriman yang cocok dengan saringan ini."
                        : "Belum ada transaksi tersimpan. Catatan yang sudah disimpan muncul di sini.")
                : "");
    }

    /**
     * Benar kalau saringan yang tertulis lebih sempit daripada keadaan tanpa
     * saringan: rental dipilih, plat diketik, atau salah satu batas tanggal
     * digeser dari bawaannya. Batas yang tidak terbaca dihitung TIDAK menyaring —
     * batasnya memang dikirim tanpa batas.
     */
    private boolean saringanAktif(LocalDate dari, LocalDate sampai, String rental, String plat) {
        // Batas tanggal hanya dihitung menyaring kalau jangkauan datanya benar-benar
        // terbaca. Tanpa penjagaan itu, jangkauan yang gagal dibaca akan membuat
        // batas bawaan tampak "berbeda" dan daftarnya berlaku seperti tersaring
        // padahal tidak ada yang disaring.
        LocalDate awal = tanggalAwalSaringan();
        LocalDate akhir = tanggalAkhirSaringan();
        return rental != null
                || !plat.trim().isEmpty()
                || (awal != null && dari != null && !dari.equals(awal))
                || (akhir != null && sampai != null && !sampai.equals(akhir));
    }

    /**
     * Tanggal transaksi paling awal, untuk batas awal saringan bawaan. Tabel yang
     * masih kosong berarti hari ini.
     *
     * <p>Database yang tidak terbaca mengembalikan null, BUKAN hari ini. Bedanya
     * penting: "hari ini" adalah jawaban yang masuk akal untuk tabel kosong, tetapi
     * salah besar kalau dipakai saat pembacaan gagal - batas awal yang tadinya
     * 06-07-2026 akan dirapikan jadi hari ini dan seluruh catatan lama hilang dari
     * layar tanpa satu pun pesan. Pemanggil yang memutuskan apa yang aman dilakukan
     * saat jangkauannya tidak diketahui.
     *
     * @return tanggal terawal, atau null kalau jangkauannya tidak terbaca
     */
    private LocalDate tanggalAwalSaringan() {
        try {
            LocalDate awal = transactionDao.earliestDate();
            return awal == null ? LocalDate.now() : awal;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Batas akhir saringan bawaan: hari ini, atau tanggal catatan terakhir kalau ada
     * yang lebih jauh. Dulu batasnya selalu hari ini, dan catatan bertanggal setelah
     * hari ini jadi tidak terlihat sama sekali setelah disimpan - terbaca sebagai
     * simpanan yang gagal, padahal catatannya ada.
     */
    private LocalDate tanggalAkhirSaringan() {
        LocalDate akhir = LocalDate.now();
        try {
            LocalDate terakhir = transactionDao.latestDate();
            if (terakhir != null && terakhir.isAfter(akhir)) {
                akhir = terakhir;
            }
        } catch (Exception e) {
            // Sama seperti batas awal: jangkauan yang tidak terbaca dikembalikan
            // sebagai null, bukan sebagai "hari ini".
            return null;
        }
        return akhir;
    }

    /**
     * Apakah pengiriman dengan plat dan rental ini akan terlihat di daftar dengan
     * saringan yang sedang terpasang.
     *
     * <p>Batas tanggalnya tidak diperiksa: {@link #save()} sudah melebarkannya supaya
     * catatan yang baru disimpan pasti masuk. Yang tersisa adalah saringan rental dan
     * plat, dan keduanya sengaja TIDAK direset - operator yang sedang menyaring satu
     * rental tidak boleh kehilangan saringannya hanya karena mencatat pengiriman lain.
     * Karena itu catatan yang tersembunyi diberi tahu, bukan dipaksa tampil.
     */
    private boolean tampilDiSaringan(String plat, String rental) {
        Object pilihan = cmbFilterRental.getSelectedItem();
        if (pilihan instanceof Rental
                && !Rental.matchKey(((Rental) pilihan).getRentalName()).equals(Rental.matchKey(rental))) {
            return false;
        }
        String cariPlat = txtFilterPlat.getText();
        if (cariPlat != null && !cariPlat.trim().isEmpty()) {
            String kunci = Truck.normalizePlate(cariPlat);
            String punya = Truck.normalizePlate(plat);
            if (punya == null || kunci == null || !punya.contains(kunci)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Kembalikan batas tanggal yang sudah berada di luar jangkauan data ke bawaannya.
     *
     * <p>Batas bawaan dihitung dari isi database. Kalau catatan paling awal dihapus,
     * batas "Dari" yang tadinya sama dengan catatan terawal itu tidak lagi sama -
     * sehingga daftarnya berlaku seperti tersaring dan sebagian catatan lama tidak
     * ikut tampil, padahal saringannya tidak menyempitkan apa pun. Karena itu
     * batasnya dirapikan lebih dulu.
     */
    private void rapikanBatasSaringan() {
        LocalDate awal = tanggalAwalSaringan();
        LocalDate akhir = tanggalAkhirSaringan();
        if (awal == null || akhir == null) {
            // Jangkauan datanya tidak terbaca, jadi batas yang "di luar jangkauan" tidak
            // bisa ditentukan. Membiarkannya apa adanya jauh lebih aman daripada
            // menggesernya ke hari ini: penggeseran itu akan menyembunyikan seluruh
            // catatan lama tanpa satu pun pesan, dan itu justru yang paling dihindari.
            return;
        }
        LocalDate dari = bacaTanggal(spFilterFrom);
        if (dari != null && dari.isBefore(awal)) {
            spFilterFrom.setValue(toDate(awal));
        }
        LocalDate sampai = bacaTanggal(spFilterTo);
        if (sampai != null && sampai.isAfter(akhir)) {
            spFilterTo.setValue(toDate(akhir));
        }
    }

    /**
     * Kembalikan saringan ke keadaan tanpa saringan, lalu muat ulang daftarnya:
     * dari tanggal transaksi terawal sampai hari ini, semua rental, plat kosong.
     *
     * <p>Bawaan sengaja menampilkan SEMUA catatan, bukan hanya periode terdekat:
     * saringan yang sejak dibuka menyembunyikan catatan lama terlihat seperti data
     * hilang, padahal catatannya hanya tersaring.
     */
    private void bersihkanSaringan() {
        LocalDate awal = tanggalAwalSaringan();
        LocalDate akhir = tanggalAkhirSaringan();
        // Jangkauan yang tidak terbaca dibiarkan: batasnya tidak disentuh, dan
        // kegagalan membaca datanya sendiri sudah dilaporkan oleh muatRiwayat().
        if (awal != null) {
            spFilterFrom.setValue(toDate(awal));
        }
        if (akhir != null) {
            spFilterTo.setValue(toDate(akhir));
        }
        cmbFilterRental.setSelectedIndex(0);
        txtFilterPlat.setText("");
        muatRiwayat();
    }

    /**
     * Isi ulang daftar rental pada saringan dari data master. Pilihan yang sedang
     * dipakai operator dipulihkan setelahnya: menyegarkan daftarnya tidak boleh
     * menggeser saringan yang sedang berjalan; kalau pilihannya sudah tidak ada di
     * data master, kembali ke "Semua rental".
     */
    private void muatDaftarRentalSaringan() {
        Rental dipilih = cmbFilterRental.getSelectedItem() instanceof Rental
                ? (Rental) cmbFilterRental.getSelectedItem() : null;
        cmbFilterRental.removeAllItems();
        cmbFilterRental.addItem(SEMUA_RENTAL);
        try {
            for (Rental r : masterDao.listRental()) {
                cmbFilterRental.addItem(r);
            }
        } catch (Exception e) {
            Theme.showError(this, e);
        }
        cmbFilterRental.setSelectedIndex(0);
        if (dipilih != null) {
            for (int i = 1; i < cmbFilterRental.getItemCount(); i++) {
                if (((Rental) cmbFilterRental.getItemAt(i)).getRentalId() == dipilih.getRentalId()) {
                    cmbFilterRental.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    /** Id catatan pengiriman dari tiap baris yang dipilih, dibaca dari modelnya. */
    private List<Integer> idTerpilih() {
        List<Integer> ids = new ArrayList<>();
        for (int barisLayar : riwayatTable.getSelectedRows()) {
            ids.add((Integer) riwayatModel.getValueAt(riwayatTable.convertRowIndexToModel(barisLayar), 0));
        }
        return ids;
    }

    /** Samakan keadaan tombol Ubah dan Hapus dengan pilihan yang ada di daftar. */
    private void perbaruiTombolRiwayat() {
        int terpilih = riwayatTable.getSelectedRowCount();
        btnUbah.setEnabled(terpilih == 1);
        btnHapus.setEnabled(terpilih >= 1);
    }

    /**
     * Cari data utuh satu pengiriman menurut id_detailnya. Data mentahnya tidak
     * disimpan di tabel — yang ditampilkan di layar formatnya sudah jadi teks.
     */
    private ReportRow cariPengiriman(int detailId) throws SQLException {
        for (ReportRow b : transactionDao.listDeliveries(null, null)) {
            if (b.getDetailId() == detailId) {
                return b;
            }
        }
        return null;
    }

    /**
     * Muat pengiriman yang dipilih kembali ke form untuk diubah. Klik dua kali pada
     * baris daftar memakai jalan yang sama.
     */
    private void ubahPengiriman() {
        if (riwayatTable.getSelectedRowCount() != 1) {
            setStatus("Pilih satu baris pengiriman yang mau diubah.");
            return;
        }
        int barisModel = riwayatTable.convertRowIndexToModel(riwayatTable.getSelectedRow());
        int id = (Integer) riwayatModel.getValueAt(barisModel, 0);
        ReportRow b;
        try {
            b = cariPengiriman(id);
        } catch (Exception e) {
            Theme.showError(this, e);
            return;
        }
        if (b == null) {
            setStatus("Catatan itu sudah tidak ada. Daftar disegarkan.");
            muatRiwayat();
            return;
        }
        // Perubahan yang belum disimpan pada catatan lain tidak boleh dibuang diam-diam.
        // Dibandingkan dengan detailDiubah (bukan penanda "kotor" yang umum) supaya
        // menekan Ubah pada baris yang SAMA tidak menanyakan apa-apa.
        if (detailDiubah != null && detailDiubah.intValue() != b.getDetailId()
                && !GraphicsEnvironment.isHeadless()) {
            int jwb = JOptionPane.showConfirmDialog(this,
                    "Perubahan yang belum disimpan pada catatan yang sedang diubah "
                            + "akan dibuang.\nLanjut mengubah catatan yang dipilih?",
                    "Perubahan belum disimpan", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (jwb != JOptionPane.YES_OPTION) {
                return;
            }
        }
        detailDiubah = b.getDetailId();
        judulKartu.setText("Ubah Pengiriman");
        btnSimpan.setText("Simpan Perubahan");
        btnBatal.setVisible(true);
        spDate.setValue(Date.from(b.getDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        // Plat ditulis dulu, baru rentalnya: menulis plat menyala penyamaan rental
        // otomatis, dan nama rental catatan ini harus menang yang terakhir.
        cmbPlate.getEditor().setItem(b.getPlate() == null ? "" : b.getPlate());
        cmbRental.getEditor().setItem(b.getRentalName() == null ? "" : b.getRentalName());
        platTersinkron = platText();
        txtFieldWeight.setText(tulisAngka(b.getFieldWeight()));
        txtFactoryWeight.setText(tulisAngka(b.getFactoryWeight()));
        txtRefraction.setText(tulisAngka(b.getRefractionPercent()));
        txtPrice.setText(tulisAngka(b.getPrice()));
        if (b.getPaymentDate() == null) {
            chkPaid.setSelected(false);
        } else {
            chkPaid.setSelected(true);
            spPaid.setValue(Date.from(b.getPaymentDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }
        Theme.clearErrors(txtFieldWeight, txtFactoryWeight, txtRefraction, txtPrice,
                cmbRental, kotakTanggal(spPaid));
        setStatus("");
        // Form di atas harus terlihat operator: gulung ke atas, lalu letakkan fokus
        // di isian pertamanya.
        gulungIsi.getViewport().setViewPosition(new Point(0, 0));
        kotakTanggal(spDate).requestFocusInWindow();
    }

    /**
     * Hapus pengiriman yang dipilih — satu atau sekaligus banyak. Seluruh id difoto
     * SEBELUM dialog konfirmasi dibuka, dan penjaga {@code sedangProses} dipasang
     * sebelum dialognya tampil: dialog modal menjalankan putaran kejadian sendiri,
     * jadi klik kedua yang mengantre bisa terkirim saat dialog masih terbuka —
     * tanpa penjaga itu terbuka dialog kedua, dan kalau daftarnya sempat dimuat
     * ulang di antaranya, dialog kedua itu menghapus catatan yang lain.
     */
    private void hapusTerpilih() {
        if (sedangProses) {
            return;
        }
        List<Integer> ids = idTerpilih();
        if (ids.isEmpty()) {
            setStatus("Pilih dulu pengiriman yang mau dihapus.");
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        try {
            for (ReportRow b : transactionDao.listDeliveries(null, null)) {
                if (ids.contains(b.getDetailId()) && b.getTotalAmount() != null) {
                    total = total.add(b.getTotalAmount());
                }
            }
        } catch (Exception e) {
            Theme.showError(this, e);
            return;
        }
        sedangProses = true;
        try {
            int jwb = JOptionPane.showConfirmDialog(this,
                    "Hapus " + ids.size() + " catatan pengiriman\n"
                            + "(total Rp " + Calculator.formatNumber(total) + ")?",
                    "Hapus Transaksi", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (jwb != JOptionPane.YES_OPTION) {
                return;
            }
            // Hapusnya sekali jalan dan menyeluruh: kalau satu id gagal, tidak ada
            // yang terhapus, jadi daftarnya tidak pernah tinggal setengah terhapus.
            transactionDao.deleteDeliveries(ids);
        } catch (Exception e) {
            Theme.showError(this, e);
            // Daftar disegarkan juga saat gagal. Kalau catatannya sudah tidak ada di
            // database (mis. dihapus dari jendela aplikasi lain), barisnya masih
            // tergambar di sini; tanpa disegarkan, operator memilihnya lagi, menekan
            // Hapus, dan mendapat galat yang sama terus tanpa jalan keluar.
            rapikanBatasSaringan();
            muatRiwayat();
            return;
        } finally {
            sedangProses = false;
        }
        // Menghapus catatan terawal atau terakhir menggeser jangkauan data, sehingga
        // batas saringan yang tadinya bawaan bisa jadi tidak lagi sama dengannya.
        rapikanBatasSaringan();
        muatRiwayat();
        setStatus(ids.size() + " catatan pengiriman dihapus.");
    }

    private void loadMaster() {
        try {
            muatDaftarMaster();
        } catch (Exception e) {
            Theme.showError(this, e);
            return;
        }
        pilihAwal();
    }

    /** Isi ulang daftar plat dan rental dari data master, tanpa menyentuh pilihan apa pun. */
    private void muatDaftarMaster() throws Exception {
        cmbPlate.removeAllItems();
        cmbRental.removeAllItems();
        trukPerPlat.clear();

        for (Rental r : masterDao.listRental()) {
            cmbRental.addItem(r);
        }
        // Truk diurutkan menurut rentalnya, bukan menurut platnya. Dengan begitu plat
        // yang tampil pertama sudah sepasang dengan rental yang tampil pertama, jadi
        // layar terbuka dalam keadaan yang masuk akal - bukan menampilkan plat milik
        // satu rental bersama nama rental yang lain.
        List<Truck> daftarTruk = masterDao.listTrucks();
        java.util.Collections.sort(daftarTruk, new java.util.Comparator<Truck>() {
            @Override
            public int compare(Truck a, Truck b) {
                String na = a.getRentalName() == null ? "" : a.getRentalName();
                String nb = b.getRentalName() == null ? "" : b.getRentalName();
                int urut = na.compareTo(nb);
                return urut != 0 ? urut : a.getPlate().compareTo(b.getPlate());
            }
        });
        for (Truck t : daftarTruk) {
            cmbPlate.addItem(t.getPlate());
            trukPerPlat.put(t.getPlate(), t);
        }
    }

    /** Pilihan bawaan saat halaman dibuka: plat pertama beserta pemiliknya. */
    private void pilihAwal() {
        if (cmbPlate.getItemCount() == 0) {
            setStatus("Belum ada plat tersimpan. Ketik platnya langsung, lalu tekan Simpan.");
        } else {
            // Pilihan plat dan pilihan rental diisi dari dua daftar yang urutannya
            // berbeda, jadi baris pertamanya belum tentu sepasang. Tanpa disamakan di
            // sini, layar terbuka dengan menampilkan plat milik satu rental dan nama
            // rental milik rental lain — sebelum operator menyentuh apa pun.
            cmbPlate.setSelectedIndex(0);
            rentalIkutPlat();
        }
    }

    /**
     * Segarkan daftar plat dan rental dari data master saat halaman ini dibuka kembali.
     *
     * <p>Halaman transaksi dipakai lagi (tidak dibuat baru setiap dibuka) supaya isian
     * yang belum disimpan tidak hilang, tetapi daftar plat dan rentalnya tetap harus
     * mengikuti data master terbaru. Yang sedang tertulis di kotak plat dan rental
     * TIDAK boleh berubah — itu pekerjaan operator yang sedang berjalan, termasuk saat
     * sedang mengubah catatan lama.
     *
     * <p>Penyamaan otomatis tidak dijalankan lagi di sini: menjalankannya akan
     * menghapus rental yang sedang operator tulis untuk plat yang belum dikenal.
     */
    public void refreshMaster() {
        String platSebelumnya = platText();
        String rentalSebelumnya = rentalText();
        boolean belumAdaDaftar = cmbPlate.getItemCount() == 0;
        try {
            muatDaftarMaster();
        } catch (Exception e) {
            Theme.showError(this, e);
            // Tidak berhenti di sini: kegagalan memuat daftar master tidak boleh
            // membuat daftar rental pada saringan diam-diam tidak disegarkan.
        }
        if (belumAdaDaftar && cmbPlate.getItemCount() > 0) {
            pilihAwal();
        } else {
            cmbPlate.getEditor().setItem(platSebelumnya);
            cmbRental.getEditor().setItem(rentalSebelumnya);
            platTersinkron = platSebelumnya;
        }
        // Panel dipakai lagi, bukan dibangun baru — tanggal bawaannya harus diisi ulang
        // dengan hari ini: aplikasi yang dibiarkan terbuka semalaman akan mencatat
        // pengiriman pagi dengan tanggal kemarin. Kalau operator sedang mengerjakan
        // sesuatu, tanggal yang sedang dipakainya tidak disentuh.
        if (!adaKerjaBelumDisimpan()) {
            spDate.setValue(new Date());
            if (chkPaid.isSelected()) {
                spPaid.setValue(new Date());
            }
        }
        // Daftar rental pada saringan ikut disegarkan, tanpa menggeser pilihan yang
        // sedang dipakai — sama seperti plat dan rental di form atas. Disegarkan
        // terpisah dari daftar master supaya kegagalan yang satu tidak membuat
        // yang lain diam-diam tertinggal.
        try {
            muatDaftarRentalSaringan();
        } catch (Exception e) {
            Theme.showError(this, e);
        }
        // Pengiriman yang barusan disimpan langsung terlihat di daftar, menurut
        // saringan yang sedang berlaku.
        muatRiwayat();
    }

    /**
     * Benar kalau ada pekerjaan yang akan terbuang kalau halaman ini ditinggalkan:
     * isian form yang belum disimpan. Dipakai bilah menu sebelum pindah halaman dan
     * jendela utama sebelum ditutup.
     */
    public boolean adaKerjaBelumDisimpan() {
        return formTerisi();
    }

    /** Benar kalau form masih berisi angka yang belum disimpan. */
    private boolean formTerisi() {
        return !txtFieldWeight.getText().trim().isEmpty()
                || !txtFactoryWeight.getText().trim().isEmpty()
                || !txtPrice.getText().trim().isEmpty();
    }

    /**
     * Isi pilihan rental mengikuti plat yang sedang tertulis.
     *
     * <p>Kalau platnya sudah dikenal, rentalnya diisi dari data yang tersimpan — bukan
     * dibiarkan seperti sebelumnya, karena pilihan yang tertinggal dari pengiriman
     * sebelumnya akan terbaca sebagai pemilik plat yang baru.
     *
     * <p>Kalau platnya belum dikenal, pilihan rentalnya dikosongkan, bukan dibiarkan.
     * Membiarkannya berarti plat baru diam-diam mewarisi rental pengiriman sebelumnya
     * — dan aplikasi ini gunanya menghitung uang yang harus dibayar per pemilik truk,
     * jadi kesalahan di sini langsung masuk ke catatan.
     */
    private void rentalIkutPlat() {
        platTersinkron = platText();
        Truck t = trukPerPlat.get(Truck.normalizePlate(platTersinkron));
        tampilkanRental(t == null ? null : t.getRentalId());
    }

    /**
     * Dipanggil saat isi kotak plat berubah karena diketik.
     *
     * <p>Teks yang tidak berubah dilewati. Combo yang bisa diketik menulis ulang isi
     * kotaknya sendiri saat tulisannya diselesaikan (Enter atau pindah fokus ke tombol),
     * dan penulisan ulang itu terlihat persis seperti ketikan baru. Kalau tidak disaring,
     * penyamaan otomatis jalan lagi tepat sebelum pengirimannya disimpan — dan pilihan
     * rental yang baru saja ditentukan operator untuk plat baru ikut terhapus, karena
     * plat itu memang belum dikenal. Operator melihat pilihannya hilang sendiri.
     */
    private void platDiketik() {
        if (platText().equals(platTersinkron)) {
            return;
        }
        rentalIkutPlat();
    }

    /**
     * Pilih rental tertentu di kotak pilihan, atau kosongkan kalau tidak ada.
     *
     * <p>Dikumpulkan di satu tempat supaya yang tampil di layar dan yang tersimpan ke
     * database tidak bisa berbeda — keduanya memakai cara pemilihan yang sama.
     *
     * <p>Kotak isiannya diisi langsung, tidak hanya pilihannya yang dipindah. Combo yang
     * bisa diketik menampilkan isi kotak isiannya sendiri, jadi memindahkan pilihan di
     * daftar saja tidak mengubah apa yang terbaca di layar.
     */
    private void tampilkanRental(Integer rentalId) {
        for (int i = 0; i < cmbRental.getItemCount(); i++) {
            Rental r = cmbRental.getItemAt(i);
            if (rentalId != null && r != null && r.getRentalId() == rentalId) {
                cmbRental.setSelectedIndex(i);
                cmbRental.getEditor().setItem(r);
                return;
            }
        }
        cmbRental.setSelectedIndex(-1);
        cmbRental.getEditor().setItem("");
    }

    /** Plat yang sedang dipilih dari daftar, atau yang baru diketik. */
    private String platText() {
        // Kotak isian yang bisa diketik menyimpan tulisannya di editornya, bukan di
        // daftar pilihannya. Mengambil dari daftar pilihan akan mengembalikan pilihan
        // terakhir, bukan yang baru diketik.
        Object isi = cmbPlate.isEditable() ? cmbPlate.getEditor().getItem() : cmbPlate.getSelectedItem();
        return isi == null ? "" : isi.toString();
    }


    /** Nama rental yang sedang tertulis, baik dipilih dari daftar maupun diketik. */
    private String rentalText() {
        Object isi = cmbRental.isEditable() ? cmbRental.getEditor().getItem() : cmbRental.getSelectedItem();
        return isi == null ? "" : isi.toString();
    }

    private void setupAutoCalculate() {
        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { recalculate(); }
            public void removeUpdate(DocumentEvent e) { recalculate(); }
            public void changedUpdate(DocumentEvent e) { recalculate(); }
        };
        txtFactoryWeight.getDocument().addDocumentListener(dl);
        txtRefraction.getDocument().addDocumentListener(dl);
        txtPrice.getDocument().addDocumentListener(dl);
        // Enter di kotak terakhir = simpan. Ini disengaja: mengetik seluruh form lalu
        // menekan Enter langsung menyimpan pengiriman itu, tanpa memindahkan tangan
        // ke tombol. Tanpa ini, Enter terasa seperti aplikasi macet.
        txtPrice.addActionListener(e -> save());
    }

    private void recalculate() {
        BigDecimal factoryWeight = parseNumber(txtFactoryWeight.getText());
        BigDecimal refraction = parseNumber(txtRefraction.getText());
        BigDecimal price = parseNumber(txtPrice.getText());
        if (factoryWeight == null || refraction == null) {
            setValue(lblNetWeight, EMPTY);
            setValue(lblTotalAmount, EMPTY);
            return;
        }
        BigDecimal netWeight;
        try {
            netWeight = Calculator.netWeight(factoryWeight, refraction);
        } catch (RuntimeException e) {
            lblNetWeight.setText("cek isian");
            lblNetWeight.setForeground(Theme.DANGER);
            setValue(lblTotalAmount, EMPTY);
            return;
        }
        setValue(lblNetWeight, Calculator.formatKg(netWeight));
        setValue(lblTotalAmount, price == null ? EMPTY
                : "Rp " + Calculator.formatNumber(Calculator.totalAmount(netWeight, price)));
    }

    /**
     * Periksa isian yang sedang tertulis di form, tanpa menyimpan apa pun.
     *
     * <p>Dipakai tombol Simpan — satu-satunya jalur penyimpanan sekarang — supaya
     * isian yang buruk ditolak di sini, bukan jadi catatan yang salah di database.
     *
     * @return true kalau isian layak disimpan.
     */
    private boolean isianLayakDisimpan() {
        String plat = Truck.normalizePlate(platText());
        if (plat == null || plat.isEmpty()) {
            setStatus("Plat truk belum diisi.");
            return false;
        }
        String rental = Rental.normalizeName(rentalText());

        BigDecimal fieldWeight = parseNumber(txtFieldWeight.getText());
        BigDecimal factoryWeight = parseNumber(txtFactoryWeight.getText());
        BigDecimal refraction = parseNumber(txtRefraction.getText());
        BigDecimal price = parseNumber(txtPrice.getText());

        // Kotak yang bermasalah ditandai merah di tempatnya, bukan lewat jendela
        // peringatan. Jendela peringatan menutupi layar dan harus ditutup dulu sebelum
        // bisa memperbaiki isian; tanda merah langsung menunjuk kotak yang harus diubah.
        Theme.clearErrors(txtFieldWeight, txtFactoryWeight, txtRefraction, txtPrice,
                cmbRental, kotakTanggal(spPaid));
        JTextField firstBad = null;
        if (fieldWeight == null) {
            Theme.markError(txtFieldWeight, true);
            firstBad = txtFieldWeight;
        }
        if (factoryWeight == null) {
            Theme.markError(txtFactoryWeight, true);
            if (firstBad == null) {
                firstBad = txtFactoryWeight;
            }
        }
        if (refraction == null) {
            Theme.markError(txtRefraction, true);
            if (firstBad == null) {
                firstBad = txtRefraction;
            }
        }
        if (price == null) {
            Theme.markError(txtPrice, true);
            if (firstBad == null) {
                firstBad = txtPrice;
            }
        }
        if (firstBad != null) {
            setStatus("Lengkapi angka pada kotak yang ditandai merah.");
            firstBad.requestFocusInWindow();
            return false;
        }

        // Rental wajib diisi, sama seperti angka-angka di atasnya. Truk yang lahir tanpa
        // pemilik membuat uangnya masuk kelompok "(tanpa rental)" di laporan, dan yang
        // mendiamkannya hanya operator yang membaca laporan itu belakangan.
        if (rental == null || rental.isEmpty()) {
            Theme.markError(cmbRental, true);
            setStatus("Nama rental belum diisi.");
            komponenEditor(cmbRental).requestFocusInWindow();
            return false;
        }

        // Tanggal lunas hanya wajib kalau pengirimannya ditandai sudah dibayar —
        // keadaan "belum dibayar" bukan tanggal yang salah. Yang ditolak hanya
        // tanggal yang diisi tapi salah.
        if (chkPaid.isSelected()) {
            if (bacaTanggal(spPaid) == null) {
                setStatus("Tanggal lunas tidak valid. Tulis seperti 05-10-2026.");
                kotakTanggal(spPaid).requestFocusInWindow();
                return false;
            }
        } else {
            Theme.markError(kotakTanggal(spPaid), false);
        }

        try {
            Calculator.totalAmount(Calculator.netWeight(factoryWeight, refraction), price);
        } catch (RuntimeException e) {
            setStatus(e.getMessage());
            return false;
        }

        // Plat yang sudah dikenal tidak boleh diam-diam berganti pemilik di sini.
        // Memindahkan pemilik mengubah seluruh laporan lama, jadi harus disengaja
        // lewat dialog Kelola Data Truk — bukan lewat ketikan yang kebetulan berbeda.
        Truck dikenal = trukPerPlat.get(plat);
        if (dikenal != null && dikenal.getRentalName() != null
                && !Rental.matchKey(dikenal.getRentalName()).equals(Rental.matchKey(rental))) {
            setStatus("Truk " + plat + " terdaftar milik \"" + dikenal.getRentalName()
                    + "\". Pindahkan pemiliknya lewat Kelola Data Truk > Pindah Pemilik.");
            return false;
        }
        return true;
    }

    private void save() {
        if (sedangProses) {
            return;
        }
        // Form yang kosong ditolak di sini, bukan di isianLayakDisimpan(): kotak yang
        // dikosongkan memang seharusnya kosong setelah simpan sukses, jadi menandainya
        // merah justru menuduh operator salah. Ini juga setengah dari penjaga simpan
        // ganda — lihat catatan panjang di bawah.
        if (!formTerisi()) {
            setStatus("Form masih kosong. Isi dulu satu pengiriman.");
            return;
        }
        if (!isianLayakDisimpan()) {
            return;
        }
        LocalDate tanggal = bacaTanggal(spDate);
        if (tanggal == null) {
            setStatus("Tanggal tidak valid. Tulis seperti 05-10-2026.");
            kotakTanggal(spDate).requestFocusInWindow();
            return;
        }
        sedangProses = true;
        try {
            String plat = Truck.normalizePlate(platText());
            String rental = Rental.normalizeName(rentalText());
            BigDecimal fieldWeight = parseNumber(txtFieldWeight.getText());
            BigDecimal factoryWeight = parseNumber(txtFactoryWeight.getText());
            BigDecimal refraction = parseNumber(txtRefraction.getText());
            BigDecimal price = parseNumber(txtPrice.getText());
            LocalDate paid = chkPaid.isSelected() ? bacaTanggal(spPaid) : null;
            BigDecimal amount = Calculator.totalAmount(
                    Calculator.netWeight(factoryWeight, refraction), price);

            if (detailDiubah != null) {
                transactionDao.updateDelivery(detailDiubah, tanggal, plat, rental,
                        fieldWeight, factoryWeight, refraction, paid, price);
            } else {
                // Truk hanya dicari dari daftar yang dimuat ke layar — TIDAK dari database.
                // Mencarikan (apalagi membuatkan) truk di sini pernah menaburkan rental dan
                // truk hantu di data master setiap kali penyimpanan dicoba lalu gagal, dan
                // rekap uang per pemilik ikut terpecah. Truk yang belum dikenal ditandai
                // dengan nomor 0; TransactionDao yang membuatnya nanti, di dalam
                // transaksi simpannya sendiri.
                Truck dikenal = trukPerPlat.get(plat);

                Transaction t = new Transaction();
                t.setDate(tanggal);
                TransactionDetail d = new TransactionDetail();
                d.setTruckId(dikenal == null ? 0 : dikenal.getTruckId());
                d.setPlate(plat);
                d.setRentalName(rental);
                d.setFieldWeight(fieldWeight);
                d.setFactoryWeight(factoryWeight);
                d.setRefractionPercent(refraction);
                d.setNetWeight(Calculator.netWeight(factoryWeight, refraction));
                d.setPrice(price);
                d.setTotalAmount(amount);
                d.setPaymentDate(paid);
                List<TransactionDetail> daftar = new ArrayList<>();
                daftar.add(d);
                transactionDao.save(t, daftar);
            }

            // Penjaga simpan ganda — klik yang sama dua kali, atau Enter dua kali.
            // Tidak ada utas latar di aplikasi ini: panggilan DAO berjalan serentak di
            // EDT, jadi menonaktifkan tombol selama penulisan TIDAK menahan klik
            // kedua — klik itu terkirim SETELAH pengelola ini selesai, saat tombolnya
            // sudah aktif lagi. Yang benar-benar bekerja adalah dua hal ini: bentuk
            // dikosongkan dan mode ubah ditinggalkan SEBELUM dialog sukses ditampilkan
            // (klik kedua menemukan form kosong dan ditolak di atas), dan bendera
            // sedangProses tetap terpasang sampai dialognya ditutup (klik kedua yang
            // terkirim oleh putaran kejadian dialog ditolak di paling atas).
            // Daftar plat dan rental dimuat ulang SESUDAH menyimpan, bukan hanya saat
            // halaman dibuka. Truk yang baru saja lahir dari penyimpanan ini belum ada
            // di peta truk layar, dan penjaga "plat dikenal tidak boleh berganti pemilik"
            // membaca peta itu: tanpa pemuatan ulang, plat yang baru dibuat masih
            // dianggap belum dikenal, penjaganya tidak menyala, dan pengiriman
            // berikutnya dicatat milik pemilik yang LAMA sementara operator mengetik
            // pemilik yang baru. Uangnya masuk ke rekap pemilik yang salah tanpa satu
            // pun pesan.
            //
            // Urutannya penting: pemuatan ulang LEBIH DULU, pengosongan form SESUDAH.
            // Memuat ulang daftar membuat pilihan pertama terpasang sendiri di kotak
            // plat (item pertama dipilih begitu daftarnya terisi), jadi mengosongkan
            // form sebelum memuat ulang akan menyisakan plat pertama tertulis di kotak -
            // dan pengiriman berikutnya diam-diam mewarisi plat itu.
            muatDaftarMaster();
            kembaliKeTambah();
            // Rental yang baru lahir dari form ini harus langsung bisa dipilih di
            // kotak saringan. Tanpa ini, daftar rental di saringan baru menyusul saat
            // halaman ditinggalkan lalu dibuka lagi - dan operator yang baru mencatat
            // truk pertama sebuah rental akan mengira rentalnya tidak tersimpan.
            muatDaftarRentalSaringan();
            // Catatan yang baru disimpan tidak boleh jatuh di luar saringan yang
            // sedang terpasang: hasilnya terlihat seperti simpanan yang gagal.
            // Batasnya dilebarkan seperlunya saja, saringan lain dibiarkan apa adanya.
            LocalDate awalSaring = bacaTanggal(spFilterFrom);
            if (awalSaring == null || tanggal.isBefore(awalSaring)) {
                spFilterFrom.setValue(toDate(tanggal));
            }
            LocalDate akhirSaring = bacaTanggal(spFilterTo);
            if (akhirSaring == null || tanggal.isAfter(akhirSaring)) {
                spFilterTo.setValue(toDate(tanggal));
            }
            muatRiwayat();

            // Catatan yang tersimpan tetapi tidak muncul di daftar karena saringan
            // rental/plat terbaca sebagai simpanan yang gagal. Dikatakan terus
            // terang di dialognya, karena dialog inilah yang dibaca operator —
            // dan juga di bilah status: dialog tidak bisa tampil tanpa layar, dan
            // pesannya ikut hilang begitu dialog ditutup, sedangkan bilah status
            // bertahan.
            String peringatanSaringan = "Catatan ini tidak tampil di daftar karena "
                    + "saringan rental atau plat sedang aktif. Tekan Semua untuk melihatnya.";
            boolean tampil = tampilDiSaringan(plat, rental);
            if (!tampil) {
                setStatus(peringatanSaringan);
            }
            JOptionPane.showMessageDialog(this,
                    "Pengiriman " + plat + " tanggal " + Dates.format(tanggal)
                            + " tersimpan.\nRp " + Calculator.formatNumber(amount)
                            + (tampil ? "" : "\n\n" + peringatanSaringan),
                    "Tersimpan", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            Theme.showError(this, e);
        } finally {
            sedangProses = false;
        }
    }

    /**
     * Kembali ke keadaan menambah pengiriman baru, sekaligus mengosongkan bekas
     * isian: bobot, harga, plat, dan rental.
     *
     * <p>Tanggal TIDAK direset: tanggal biasanya berulang dalam satu rombongan,
     * sedangkan plat hampir tidak pernah, dan plat yang tertinggal diam-diam
     * mencatat pengiriman di bawah uang pemilik yang salah.
     */
    private void kembaliKeTambah() {
        detailDiubah = null;
        judulKartu.setText("Catat Pengiriman");
        btnSimpan.setText("Simpan");
        btnBatal.setVisible(false);
        cmbPlate.getEditor().setItem("");
        cmbRental.getEditor().setItem("");
        platTersinkron = "";
        txtFieldWeight.setText("");
        txtFactoryWeight.setText("");
        txtPrice.setText("");
        Theme.clearErrors(txtFieldWeight, txtFactoryWeight, txtRefraction, txtPrice,
                cmbRental, kotakTanggal(spPaid));
        setValue(lblNetWeight, EMPTY);
        setValue(lblTotalAmount, EMPTY);
        setStatus("");
        riwayatTable.clearSelection();
    }

    private void setStatus(String message) {
        boolean ada = message != null && !message.isEmpty();
        lblStatus.setText(ada ? message : "");
        // Label kosong tetap memakan tinggi, jadi disembunyikan saat tidak ada pesan.
        // Tanpa ini kartu form selalu menyisakan satu baris kosong di bawahnya.
        lblStatus.setVisible(ada);
    }

    /**
     * Tulis angka BigDecimal kembali ke kotak isian dengan koma sebagai pemisah
     * desimal — kebalikan dari {@link #parseNumber(String)}, supaya isian yang
     * dimuat ulang terbaca sama dengan saat ditulis.
     */
    private static String tulisAngka(BigDecimal v) {
        if (v == null) {
            return "";
        }
        // Nol di ekor dibuang: database menyimpan dua angka desimal, jadi 7050
        // terbaca balik sebagai 7050,00 — operator melihat angka yang tidak
        // pernah ditulisnya.
        return v.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /**
     * Baca angka yang diketik. Titik dipakai sebagai pemisah ribuan dan koma sebagai
     * pemisah desimal, sesuai kebiasaan penulisan angka di Indonesia.
     *
     * @return null kalau kosong atau bukan angka — pemanggil yang memutuskan pesannya.
     */
    private BigDecimal parseNumber(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim().replace(".", "").replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Kotak teks di dalam kotak tanggal; tulisannya di situ, bukan di nilai modelnya. */
    private static JTextField kotakTanggal(JSpinner sp) {
        return ((JSpinner.DefaultEditor) sp.getEditor()).getTextField();
    }

    /**
     * Baca tanggal yang diketik di kotak tanggal, atau null kalau tulisannya tidak valid.
     *
     * <p>Teksnya yang dibaca, bukan nilai modelnya: kotak tanggal hanya memindahkan
     * tulisannya ke model saat tulisannya selesai (Enter atau pindah fokus), dan yang
     * tidak selesai dipakai apa adanya — diam-diam. Tanpa ini, 31-02-2026 diam-diam
     * bergulir menjadi 03-03-2026, 5-10-26 menjadi tahun 26 Masehi, dan bentuk lain
     * (5/10/2026) ditolak sunyi lalu aksinya memakai tanggal LAMA.
     */
    private LocalDate bacaTanggal(JSpinner sp) {
        JTextField kotak = kotakTanggal(sp);
        LocalDate t = Dates.parseInput(kotak.getText());
        if (t == null) {
            Theme.markError(kotak, true);
            return null;
        }
        Theme.markError(kotak, false);
        // Nilai modelnya ikut disamakan supaya tombol panah kotak tanggal melanjutkan
        // dari tanggal yang tertulis, bukan dari tanggal lama yang tertinggal.
        sp.setValue(Date.from(t.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        return t;
    }

    /** Ubah LocalDate ke Date untuk ditulis ke kotak tanggal (modelnya memakai Date). */
    private static Date toDate(LocalDate t) {
        return Date.from(t.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static JSpinner dateSpinner() {
        JSpinner sp = new JSpinner(new SpinnerDateModel());
        sp.setEditor(new JSpinner.DateEditor(sp, "dd-MM-yyyy"));
        return sp;
    }

    /**
     * Cadangkan seluruh database bawaan ke satu file zip berstempel waktu.
     *
     * <p>Database bawaan (H2) tersimpan sebagai satu file di komputer pengguna,
     * dan aplikasi sekarang ikut menulis ke file itu setiap kali dijalankan —
     * jadi file yang rusak atau terhapus berarti seluruh catatan hilang. Satu
     * salinan cadangan yang bisa dibawa pulang adalah satu-satunya jalan pulih.
     *
     * <p>Diminta konfirmasi dulu karena menulis file baru, lalu letak file
     * hasilnya diberitahukan lengkap — cadangan yang tidak ketemu sama saja
     * dengan tidak ada cadangan.
     */
    private void cadangkanDatabase() {
        if (JOptionPane.showConfirmDialog(this,
                "Buat cadangan database sekarang?\nFile cadangannya ditulis ke folder "
                        + "cadangan di samping file databasenya.",
                "Cadangkan Database", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            File hasil = BackupDao.cadangkan();
            JOptionPane.showMessageDialog(this,
                    "Cadangan berhasil dibuat:\n" + hasil.getAbsolutePath(),
                    "Cadangan Database", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }
}
