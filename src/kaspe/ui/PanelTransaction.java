package kaspe.ui;

import kaspe.Calculator;
import kaspe.dao.MasterDao;
import kaspe.dao.TransactionDao;
import kaspe.model.*;
import kaspe.util.Dates;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Halaman input transaksi.
 * Bagian atas: header (No dan tanggal).
 * Bagian tengah: input satu baris (plat, bobot lapak, bobot pabrik, refraksi, harga, tanggal lunas).
 * Berat bersih dan jumlah uang dihitung otomatis.
 */
public class PanelTransaction extends JPanel {

    private final JSpinner spDate = dateSpinner();
    /**
     * Plat truk. Bisa dipilih dari daftar plat yang sudah pernah masuk, bisa juga
     * langsung diketik. Plat yang belum ada tidak perlu didaftarkan dulu di halaman
     * data master — dibuat sendiri saat barisnya ditambahkan.
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
     * kolom tanggal_lunas di database memang boleh NULL untuk nota yang belum dibayar.
     */
    private final JCheckBox chkPaid = new JCheckBox("Sudah dibayar", true);
    private final JSpinner spPaid = dateSpinner();
    private final JLabel lblNetWeight = valueLabel();
    private final JLabel lblTotalAmount = valueLabel();
    private final JLabel lblTotal = totalLabel();
    private final JLabel lblStatus = new JLabel();

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Plat", "Rental", "Bobot Lapak", "Bobot Pabrik", "Refraksi (%)",
                    "Berat Bersih", "Tgl Lunas", "Harga", "Jumlah Uang"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable table = new Theme.Table(model,
            "Belum ada baris. Isi datanya di atas, lalu tekan Tambah Baris.");
    /** Daftar transaksi yang SUDAH tersimpan, satu baris per nota. */
    private final DefaultTableModel riwayatModel = new DefaultTableModel(
            new Object[]{"Tanggal", "Baris", "Total"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final Theme.Table riwayatTable = new Theme.Table(riwayatModel,
            "Belum ada transaksi tersimpan. Nota yang sudah disimpan muncul di sini.");
    /** Nota di tiap baris riwayat, sejajar dengan baris tabelnya. */
    private final List<Nota> riwayatNota = new ArrayList<>();
    private final List<TransactionDetail> detailList = new ArrayList<>();
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
        add(buildBottom(), BorderLayout.SOUTH);
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
        //   sehingga rental baris sebelumnya ikut terbawa sebagai pemilik truk yang baru —
        //   dan itu langsung salah di catatan uang, tanpa pesan apa pun.
        cmbPlate.addActionListener(e -> platDiketik());
        // Kotak tanggal lunas hanya aktif kalau notanya ditandai sudah dibayar.
        // Tanpa centang, tanggal lunas dicatat kosong (belum dibayar).
        chkPaid.setOpaque(false);
        chkPaid.addItemListener(e -> spPaid.setEnabled(chkPaid.isSelected()));
        komponenEditor(cmbPlate).getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { platDiketik(); }
            public void removeUpdate(DocumentEvent e) { platDiketik(); }
            public void changedUpdate(DocumentEvent e) { platDiketik(); }
        });
        loadMaster();
        setupAutoCalculate();
        newTransaction();
        muatRiwayat();
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

    /** Label hasil hitungan: berat bersih dan jumlah uang. */
    private static JLabel valueLabel() {
        JLabel l = new JLabel(EMPTY);
        l.setFont(Theme.semibold(16f));
        l.setForeground(Theme.INK_SOFT);
        return l;
    }

    /** Isi awal kotak hasil sebelum angkanya ada. */
    private static final String EMPTY = "\u2014";

    /** Tulisi hasil hitungan dan warnai sesuai keadaannya. */
    private static void setValue(JLabel label, String text) {
        boolean kosong = text == null || EMPTY.equals(text);
        label.setText(kosong ? EMPTY : text);
        label.setForeground(kosong ? Theme.INK_SOFT : Theme.MONEY);
    }

    /** Label total di bagian bawah. */
    private static JLabel totalLabel() {
        JLabel l = new JLabel("Rp 0");
        l.setFont(Theme.bold(19f));
        l.setForeground(Theme.MONEY);
        return l;
    }

    /** Samakan tinggi semua kotak isian supaya barisnya lurus. */
    private static <T extends JComponent> T sized(T c, int width) {
        c.setPreferredSize(new Dimension(width, Theme.FIELD_HEIGHT));
        return c;
    }


    private JPanel buildCenter() {
        JPanel outer = new JPanel(new BorderLayout(0, 12));
        outer.setOpaque(false);
        outer.add(buildInputCard(), BorderLayout.NORTH);
        outer.add(buildTableCard(), BorderLayout.CENTER);
        // Daftar nota yang sudah tersimpan ditaruh di bawah: selama ini nota yang
        // tersimpan tidak terlihat di halaman ini, sehingga terlihat "hilang" dan
        // tidak ada jalannya dihapus dari layar.
        outer.add(buildRiwayatCard(), BorderLayout.SOUTH);

        // Tiga bagian bertumpuk (form, tabel baris, riwayat) tingginya melebihi jendela
        // bawaan, dan BorderLayout membagi ruang sisa: bagian CENTER-lah yang dikorbankan,
        // sampai tingginya nol. Akibatnya tabel baris yang sedang diisi tidak tergambar
        // sama sekali - totalnya tetap benar, jadi layarnya terlihat wajar, tetapi
        // barisnya tidak bisa dilihat maupun dipilih untuk dihapus.
        //
        // Digulir, bukan dipadatkan. Di dalam gulungan, setiap bagian memakai tinggi yang
        // dimintanya, jadi tidak ada yang bisa terhimpit jadi nol dan kedua tabel dapat
        // tinggi yang benar-benar terpakai. Halaman yang cukup tinggi tidak menampakkan
        // gulungan ini sama sekali.
        //
        // Pemisah yang bisa digeser sempat dicoba, tetapi ruangnya kurang untuk dua tabel:
        // tabel baris hanya kebagian 74 piksel - cukup untuk headernya, tidak untuk
        // barisnya. Gulungan memberi keduanya tinggi yang utuh.
        JScrollPane gulung = new JScrollPane(outer);
        gulung.setBorder(BorderFactory.createEmptyBorder());
        gulung.setOpaque(false);
        gulung.getViewport().setOpaque(false);
        gulung.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        gulung.getVerticalScrollBar().setUnitIncrement(16);

        JPanel wadah = new JPanel(new BorderLayout());
        wadah.setOpaque(false);
        wadah.add(gulung, BorderLayout.CENTER);
        return wadah;
    }

    /**
     * Form satu baris.
     *
     * <p>Keterangan tiap kotak ditaruh di atas kotaknya, bukan di sampingnya. Susunan
     * mendatar "label – kotak – label – kotak" membuat mata harus melompati celah yang
     * lebarnya berbeda-beda di tiap baris; susunan bertumpuk membaca satu arah saja.
     *
     * <p>Tiga bagian dipisah jelas: identitas truk, angka timbangan, lalu kotak hasil
     * hitungan. Tombol aksi ditaruh di barisnya sendiri, tidak lagi menyempil di antara
     * kotak isian.
     */
    private JPanel buildInputCard() {
        JPanel card = Theme.card("Tambah Baris");

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        // Isian dibiarkan seukuran yang ditentukan (fill NONE). Kalau dibiarkan melar
        // mengikuti lebar kartu, kotak "Bobot Lapak" jadi selebar kotak "Plat / Truk"
        // dan barisnya terlihat renggang tanpa alasan.
        g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.WEST;

        Theme.placeholder(txtFieldWeight, "kg");
        Theme.placeholder(txtFactoryWeight, "kg");
        Theme.placeholder(txtRefraction, "%");
        Theme.placeholder(txtPrice, "Rp/kg");

        // baris 1 — tanggal nota, lalu truk dan dua angka timbangan.
        //
        // Tanggal nota dulu punya kartu sendiri di atas form. Kartu itu tingginya 79
        // piksel hanya untuk satu kotak isian, dan tinggi itu diambil dari jatah dua
        // tabel di bawahnya - sampai kartu "Transaksi Tersimpan" terdorong ke bawah
        // lipatan, padahal justru itu yang dicari operator. Dipindahkan ke sini, tidak
        // ada isian yang hilang dan dua tabelnya sama-sama muat tanpa menggulir.
        g.gridy = 0;
        g.insets = new Insets(0, 0, 8, 14);
        g.gridx = 0;
        grid.add(Theme.field("Tanggal Nota", sized(spDate, 150)), g);
        g.gridx = 1;
        grid.add(Theme.field("Plat / Truk", sized(cmbPlate, 200)), g);
        g.gridx = 2;
        grid.add(Theme.field("Rental", sized(cmbRental, 170)), g);
        g.gridx = 3;
        grid.add(Theme.field("Bobot Lapak (kg)", sized(txtFieldWeight, 120)), g);
        g.gridx = 4;
        g.insets = new Insets(0, 0, 8, 0);
        grid.add(Theme.field("Bobot Pabrik (kg)", sized(txtFactoryWeight, 120)), g);
        addSpacer(grid, g, 5);

        // baris 2 — potongan dan harga
        g.gridy = 1;
        g.insets = new Insets(0, 0, 8, 14);
        g.gridx = 0;
        grid.add(Theme.field("Refraksi (%)", sized(txtRefraction, 120)), g);
        g.gridx = 1;
        grid.add(Theme.field("Harga (Rp/kg)", sized(txtPrice, 120)), g);
        g.gridx = 2;
        g.insets = new Insets(0, 0, 8, 0);
        // Centang "Sudah dibayar" + tanggal lunasnya dalam satu kotak yang sama:
        // keadaan "belum dibayar" adalah bagian dari isian tanggal lunas, bukan isian lain.
        JPanel kotakLunas = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        kotakLunas.setOpaque(false);
        kotakLunas.add(chkPaid);
        kotakLunas.add(sized(spPaid, 130));
        grid.add(Theme.field("Tanggal Lunas", kotakLunas), g);
        addSpacer(grid, g, 3);

        // baris 4 — hasil hitungan dan tombol, sebaris.
        //
        // Sebelumnya keduanya dua baris terpisah, dan tingginya menghabiskan ruang yang
        // justru dibutuhkan dua tabel di bawahnya. Digabung, formulirnya lebih pendek
        // tanpa ada isian yang hilang, dan tabel baris serta daftar nota tersimpan
        // masing-masing tetap dapat ruang.
        g.gridy = 2;
        g.gridx = 0;
        g.gridwidth = 5;
        g.insets = new Insets(0, 0, 0, 0);
        JPanel hasilDanTombol = new JPanel(new BorderLayout(18, 0));
        hasilDanTombol.setOpaque(false);
        hasilDanTombol.add(buildResult(), BorderLayout.WEST);
        hasilDanTombol.add(buildActions(), BorderLayout.CENTER);
        grid.add(hasilDanTombol, g);

        card.add(grid, BorderLayout.CENTER);
        return card;
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

    /** Kotak sorot: hasil hitungan baris yang sedang diisi. */
    private JPanel buildResult() {
        JPanel p = Theme.strip();
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;

        g.gridy = 0;
        g.gridx = 0;
        g.insets = new Insets(0, 0, 2, 0);
        p.add(Theme.caption("Berat Bersih"), g);
        g.gridx = 1;
        g.insets = new Insets(0, 32, 2, 0);
        p.add(Theme.caption("Jumlah Uang"), g);

        g.gridy = 1;
        g.gridx = 0;
        g.insets = new Insets(0, 0, 0, 0);
        p.add(lblNetWeight, g);
        g.gridx = 1;
        g.insets = new Insets(0, 32, 0, 0);
        p.add(lblTotalAmount, g);

        // penyerap sisa lebar
        g.gridx = 2;
        g.weightx = 1;
        p.add(new JLabel(), g);
        return p;
    }

    /** Baris tombol, terpisah dari kotak isian. */
    private JPanel buildActions() {
        JPanel p = new JPanel(new BorderLayout(14, 0));
        p.setOpaque(false);

        JButton btnAdd = Theme.primary("Tambah Baris");
        btnAdd.addActionListener(e -> addRow());
        JButton btnDelete = Theme.plain("Hapus Baris Terpilih");
        btnDelete.addActionListener(e -> removeRow());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(btnAdd);
        buttons.add(btnDelete);

        lblStatus.setForeground(Theme.DANGER);

        p.add(buttons, BorderLayout.WEST);
        p.add(lblStatus, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildTableCard() {
        Theme.styleTable(table);
        Theme.widths(table, 112, 148, 104, 104, 92, 104, 96, 85, 120);
        Theme.alignRight(table, 2, 3, 4, 5, 7, 8);
        Theme.emphasis(table, 8);

        JPanel card = Theme.card();
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        card.add(scroll, BorderLayout.CENTER);
        // Tinggi yang diminta menentukan pembagian awal ruang dengan kartu riwayat di
        // bawahnya; batas bawahnya menjaga tabel ini tidak pernah bisa menyusut sampai
        // tinggal headernya saja.
        card.setPreferredSize(new Dimension(0, 150));
        card.setMinimumSize(new Dimension(0, 120));
        return card;
    }

    /**
     * Kartu "Transaksi Tersimpan": nota yang sudah masuk database, satu baris per
     * nota (tanggal, jumlah baris, total uang), dan tombol hapusnya.
     *
     * <p>Tingginya tidak dipatok, melainkan dibagi bersama tabel baris lewat pemisah di
     * {@link #buildCenter()}; daftar riwayat yang panjang digulir di dalam kartunya sendiri.
     */
    private JPanel buildRiwayatCard() {
        Theme.styleTable(riwayatTable);
        Theme.widths(riwayatTable, 120, 70, 130);
        Theme.alignRight(riwayatTable, 1, 2);
        Theme.emphasis(riwayatTable, 2);
        riwayatTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel card = Theme.card("Transaksi Tersimpan");
        JPanel isi = new JPanel(new BorderLayout(0, 10));
        isi.setOpaque(false);

        JScrollPane scroll = new JScrollPane(riwayatTable);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        isi.add(scroll, BorderLayout.CENTER);

        JPanel tombol = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tombol.setOpaque(false);
        JButton btnHapus = Theme.plain("Hapus Transaksi Terpilih");
        btnHapus.addActionListener(e -> hapusTransaksiTerpilih());
        tombol.add(btnHapus);
        isi.add(tombol, BorderLayout.SOUTH);

        card.add(isi, BorderLayout.CENTER);
        card.setPreferredSize(new Dimension(0, 215));
        card.setMinimumSize(new Dimension(0, 110));
        return card;
    }

    /**
     * Isi ulang daftar transaksi tersimpan: satu baris per nota, diurutkan menurut
     * tanggalnya seperti di laporan.
     */
    private void muatRiwayat() {
        List<ReportRow> baris;
        try {
            baris = transactionDao.listReport(null, null);
        } catch (Exception e) {
            Theme.showError(this, e);
            return;
        }
        Map<Integer, Nota> perNota = new LinkedHashMap<>();
        for (ReportRow b : baris) {
            Nota nota = perNota.get(b.getTransactionId());
            if (nota == null) {
                nota = new Nota(b.getTransactionId(), b.getDate());
                perNota.put(b.getTransactionId(), nota);
            }
            nota.baris++;
            if (b.getTotalAmount() != null) {
                nota.total = nota.total.add(b.getTotalAmount());
            }
        }
        riwayatModel.setRowCount(0);
        riwayatNota.clear();
        // Terbaru di atas. Daftar ini dipakai untuk menghapus nota yang barusan salah
        // dicatat, dan itu nota yang paling akhir tersimpan - kalau yang lama di atas,
        // yang dicari justru ada di baris paling bawah dan harus digulir dulu.
        List<Nota> urut = new ArrayList<>(perNota.values());
        java.util.Collections.reverse(urut);
        for (Nota nota : urut) {
            riwayatNota.add(nota);
            riwayatModel.addRow(new Object[]{
                    Dates.format(nota.tanggal), nota.baris,
                    "Rp " + Calculator.formatCurrency(nota.total)});
        }
    }

    /**
     * Hapus nota yang dipilih di daftar transaksi tersimpan, lewat konfirmasi yang
     * menyebut tanggal dan total uangnya. Baris-baris notanya ikut terhapus sendiri
     * oleh database (FK ON DELETE CASCADE), jadi cukup headernya saja.
     */
    private void hapusTransaksiTerpilih() {
        int i = riwayatTable.getSelectedRow();
        if (i < 0) {
            setStatus("Pilih dulu nota yang mau dihapus pada daftar Transaksi Tersimpan.");
            return;
        }
        Nota nota = riwayatNota.get(i);
        int jwb = JOptionPane.showConfirmDialog(this,
                "Hapus nota tanggal " + Dates.format(nota.tanggal) + "\n("
                        + nota.baris + " baris, total Rp " + Calculator.formatCurrency(nota.total) + ")?",
                "Hapus Transaksi", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (jwb != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            transactionDao.deleteTransaction(nota.id);
        } catch (Exception e) {
            Theme.showError(this, e);
            return;
        }
        muatRiwayat();
        setStatus("Nota tanggal " + Dates.format(nota.tanggal) + " dihapus.");
    }

    /** Satu nota tersimpan di daftar riwayat. */
    private static final class Nota {
        final int id;
        final LocalDate tanggal;
        int baris;
        BigDecimal total = BigDecimal.ZERO;

        Nota(int id, LocalDate tanggal) {
            this.id = id;
            this.tanggal = tanggal;
        }
    }

    private JPanel buildBottom() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        left.add(Theme.label("Total"));
        left.add(lblTotal);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        JButton btnNew = Theme.plain("Transaksi Baru");
        JButton btnSave = Theme.primary("Simpan Transaksi");
        btnSave.addActionListener(e -> save());
        btnNew.addActionListener(e -> newTransaction());
        right.add(btnNew);
        right.add(btnSave);

        p.add(left, BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
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
            setStatus("Belum ada plat tersimpan. Ketik platnya langsung, lalu tekan Tambah Baris.");
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
     * <p>Halaman transaksi dipakai lagi (tidak dibuat baru setiap dibuka) supaya baris
     * yang belum disimpan tidak hilang, tetapi daftar plat dan rentalnya tetap harus
     * mengikuti data master terbaru. Yang sedang tertulis di kotak plat dan rental
     * TIDAK boleh berubah, dan baris yang sudah masuk daftar tidak boleh tersentuh —
     * keduanya pekerjaan operator yang sedang berjalan.
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
            return;
        }
        if (belumAdaDaftar && cmbPlate.getItemCount() > 0) {
            pilihAwal();
        } else {
            cmbPlate.getEditor().setItem(platSebelumnya);
            cmbRental.getEditor().setItem(rentalSebelumnya);
            platTersinkron = platSebelumnya;
        }
        // Panel dipakai lagi, bukan dibangun baru — tanggal bawaannya harus diisi ulang
        // dengan hari ini: aplikasi yang dibiarkan terbuka semalaman akan mencatat baris
        // pagi dengan tanggal kemarin. Kalau operator sedang mengerjakan sesuatu,
        // tanggal yang sedang dipakainya tidak disentuh.
        if (!adaKerjaBelumDisimpan()) {
            spDate.setValue(new Date());
            if (chkPaid.isSelected()) {
                spPaid.setValue(new Date());
            }
        }
        // Nota yang barusan disimpan langsung terlihat di daftar Transaksi Tersimpan.
        muatRiwayat();
    }

    /**
     * Benar kalau ada pekerjaan yang akan terbuang kalau halaman ini ditinggalkan:
     * baris yang sudah masuk daftar tapi belum disimpan, atau isian yang belum
     * ditambahkan sebagai baris. Dipakai bilah menu sebelum pindah halaman dan
     * jendela utama sebelum ditutup.
     */
    public boolean adaKerjaBelumDisimpan() {
        return !detailList.isEmpty() || formTerisi();
    }

    /** Benar kalau form baris masih berisi angka yang belum ditambahkan sebagai baris. */
    private boolean formTerisi() {
        return !txtFieldWeight.getText().trim().isEmpty()
                || !txtFactoryWeight.getText().trim().isEmpty()
                || !txtPrice.getText().trim().isEmpty();
    }

    /**
     * Isi pilihan rental mengikuti plat yang sedang tertulis.
     *
     * <p>Kalau platnya sudah dikenal, rentalnya diisi dari data yang tersimpan — bukan
     * dibiarkan seperti sebelumnya, karena pilihan yang tertinggal dari baris sebelumnya
     * akan terbaca sebagai pemilik plat yang baru.
     *
     * <p>Kalau platnya belum dikenal, pilihan rentalnya dikosongkan, bukan dibiarkan.
     * Membiarkannya berarti plat baru diam-diam mewarisi rental baris sebelumnya — dan
     * aplikasi ini gunanya menghitung uang yang harus dibayar per pemilik truk, jadi
     * kesalahan di sini langsung masuk ke catatan.
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
     * penyamaan otomatis jalan lagi tepat sebelum barisnya ditambahkan — dan pilihan
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
        // Enter di kotak terakhir = tambah baris. Tanpa ini, mengetik seluruh baris
        // lalu menekan Enter tidak melakukan apa-apa dan terasa seperti aplikasi macet.
        txtPrice.addActionListener(e -> addRow());
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
        setValue(lblNetWeight, Calculator.formatCurrency(netWeight) + " kg");
        setValue(lblTotalAmount, price == null ? EMPTY
                : "Rp " + Calculator.formatCurrency(Calculator.totalAmount(netWeight, price)));
    }

    /**
     * Periksa isian yang sedang tertulis di form baris, tanpa menambahkan barisnya.
     *
     * <p>Dipakai dua jalur: tombol Tambah Baris, dan tombol Simpan Transaksi saat form
     * masih berisi isian yang belum ditambahkan sebagai baris — supaya keduanya menolak
     * isian yang sama dengan alasan yang sama.
     *
     * @return true kalau isian layak ditambahkan sebagai baris.
     */
    private boolean isianLayakJadiBaris() {
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

        // Tanggal lunas hanya wajib kalau notanya ditandai sudah dibayar — keadaan
        // "belum dibayar" bukan tanggal yang salah. Yang ditolak hanya tanggal yang
        // diisi tapi salah.
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
        // lewat halaman Data Master — bukan lewat ketikan yang kebetulan berbeda.
        Truck dikenal = trukPerPlat.get(plat);
        if (dikenal != null && dikenal.getRentalName() != null
                && !Rental.matchKey(dikenal.getRentalName()).equals(Rental.matchKey(rental))) {
            setStatus("Truk " + plat + " terdaftar milik \"" + dikenal.getRentalName()
                    + "\". Pindahkan pemiliknya lewat Data Master > Pindah Pemilik.");
            return false;
        }
        return true;
    }

    private void addRow() {
        if (!isianLayakJadiBaris()) {
            return;
        }
        String plat = Truck.normalizePlate(platText());
        String rental = Rental.normalizeName(rentalText());
        BigDecimal fieldWeight = parseNumber(txtFieldWeight.getText());
        BigDecimal factoryWeight = parseNumber(txtFactoryWeight.getText());
        BigDecimal refraction = parseNumber(txtRefraction.getText());
        BigDecimal price = parseNumber(txtPrice.getText());
        LocalDate paid = chkPaid.isSelected() ? bacaTanggal(spPaid) : null;

        // Truk hanya dicari dari daftar yang dimuat ke layar — TIDAK dari database.
        // Mencarikan (apalagi membuatkan) truk di sini pernah menaburkan rental dan
        // truk hantu di data master setiap kali baris dicoba lalu dibuang, dan rekap
        // uang per pemilik ikut terpecah. Truk yang belum dikenal ditandai dengan
        // nomor 0; TransactionDao yang membuatnya nanti, saat transaksinya
        // benar-benar disimpan.
        Truck dikenal = trukPerPlat.get(plat);

        BigDecimal netWeight = Calculator.netWeight(factoryWeight, refraction);
        BigDecimal amount = Calculator.totalAmount(netWeight, price);

        TransactionDetail d = new TransactionDetail();
        d.setTruckId(dikenal == null ? 0 : dikenal.getTruckId());
        d.setPlate(plat);
        d.setRentalName(rental);
        d.setFieldWeight(fieldWeight);
        d.setFactoryWeight(factoryWeight);
        d.setRefractionPercent(refraction);
        d.setNetWeight(netWeight);
        d.setPrice(price);
        d.setTotalAmount(amount);
        d.setPaymentDate(paid);

        detailList.add(d);
        model.addRow(new Object[]{
                plat, rental,
                Calculator.formatCurrency(fieldWeight), Calculator.formatCurrency(factoryWeight), Calculator.formatCurrency(refraction),
                Calculator.formatCurrency(netWeight), Dates.format(paid),
                "Rp " + Calculator.formatCurrency(price), "Rp " + Calculator.formatCurrency(amount)});
        setStatus("");
        clearInput();
        updateTotal();
    }

    private void removeRow() {
        int i = table.getSelectedRow();
        if (i < 0) {
            setStatus("Pilih dulu baris yang mau dihapus.");
            return;
        }
        detailList.remove(i);
        model.removeRow(i);
        setStatus("");
        updateTotal();
    }

    /** Jumlah uang seluruh baris yang sudah masuk daftar. */
    private static BigDecimal totalOf(List<TransactionDetail> details) {
        BigDecimal total = BigDecimal.ZERO;
        for (TransactionDetail d : details) {
            total = total.add(d.getTotalAmount());
        }
        return total;
    }

    private void updateTotal() {
        lblTotal.setText("Rp " + Calculator.formatCurrency(totalOf(detailList)));
    }

    private void setStatus(String message) {
        lblStatus.setText(message == null ? "" : message);
    }

    private void clearInput() {
        txtFieldWeight.setText("");
        txtFactoryWeight.setText("");
        txtPrice.setText("");
        Theme.clearErrors(txtFieldWeight, txtFactoryWeight, txtRefraction, txtPrice);
        setValue(lblNetWeight, EMPTY);
        setValue(lblTotalAmount, EMPTY);
    }

    private void newTransaction() {
        detailList.clear();
        model.setRowCount(0);
        updateTotal();
        setStatus("");
        spDate.setValue(new Date());
        // Nota baru dimulai dari keadaan "sudah dibayar" — keadaan yang paling sering.
        chkPaid.setSelected(true);
        spPaid.setEnabled(true);
        spPaid.setValue(new Date());
        clearInput();
        Theme.clearErrors(cmbRental, kotakTanggal(spDate), kotakTanggal(spPaid));
        riwayatTable.clearSelection();
        recalculate();
    }

    private void save() {
        // Isian yang belum ditambahkan sebagai baris jangan dibuang diam-diam:
        // operator menekan Simpan dan mengira semuanya tersimpan, padahal isian itu
        // tidak ikut. Tawarkan menyimpannya, atau beri tahu apa yang kurang.
        if (formTerisi()) {
            if (!isianLayakJadiBaris()) {
                // Pesan kekurangannya sudah tampil di baris status dan kotaknya
                // ditandai merah — sama seperti saat menekan Tambah Baris.
                return;
            }
            int jwb = JOptionPane.showConfirmDialog(this,
                    "Ada isian yang belum ditambahkan sebagai baris. Simpan juga?",
                    "Isian belum ditambahkan", JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (jwb == JOptionPane.CANCEL_OPTION) {
                return;
            }
            if (jwb == JOptionPane.YES_OPTION) {
                addRow();
            }
        }
        if (detailList.isEmpty()) {
            setStatus("Belum ada baris. Isi dulu satu baris truk.");
            return;
        }
        LocalDate tanggal = bacaTanggal(spDate);
        if (tanggal == null) {
            setStatus("Tanggal tidak valid. Tulis seperti 05-10-2026.");
            kotakTanggal(spDate).requestFocusInWindow();
            return;
        }
        try {
            Transaction t = new Transaction();
            t.setDate(tanggal);
            // Total dihitung sebelum daftar dikosongkan oleh newTransaction().
            BigDecimal total = totalOf(detailList);
            int rows = detailList.size();

            transactionDao.save(t, detailList);

            // Daftar dikosongkan dan riwayat disegarkan SEBELUM dialog sukses: kalau
            // dialognya gagal (mis. galat grafis), daftar sudah bersih, jadi operator
            // yang menekan Simpan lagi tidak bisa membuat notanya tercatat dua kali.
            newTransaction();
            muatRiwayat();

            JOptionPane.showMessageDialog(this,
                    "Transaksi tersimpan.\n"
                            + rows + " baris, total Rp " + Calculator.formatCurrency(total),
                    "Tersimpan", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            Theme.showError(this, e);
        }
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

    private static JSpinner dateSpinner() {
        JSpinner sp = new JSpinner(new SpinnerDateModel());
        sp.setEditor(new JSpinner.DateEditor(sp, "dd-MM-yyyy"));
        return sp;
    }
}
