package kaspe.ui;

import kaspe.Calculator;
import kaspe.dao.MasterDao;
import kaspe.dao.TransactionDao;
import kaspe.model.ReportRow;
import kaspe.model.Rental;
import kaspe.model.Truck;
import kaspe.util.Dates;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/** Halaman laporan: filter tanggal, tabel, total, dan cetak. */
public class PanelReport extends JPanel {

    private final JSpinner spFrom = dateSpinner();
    private final JSpinner spTo = dateSpinner();

    private final JComboBox<Object> cmbRental = new JComboBox<>();
    private final JTextField txtPlat = new JTextField();
    /** Pilihan pertama kotak rental saringan: tanpa penyaring rental. */
    private static final String SEMUA_RENTAL = "Semua rental";

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Tanggal", "Plat", "Rental", "Bobot Lapak", "Bobot Pabrik",
                    "Refraksi", "Berat Bersih", "Tgl Lunas", "Harga", "Jumlah Uang"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final Theme.Table table = new Theme.Table(model, "");
    private final JLabel lblTotalAmount = new JLabel("Rp 0");
    private final JLabel lblTotalWeight = new JLabel("0 kg");
    private final JLabel lblRowCount = new JLabel("0 baris");
    /** Pesan kesalahan isian, ditulis di baris filter — dekat kotak yang salah. */
    private final JLabel lblStatus = new JLabel();
    /**
     * Rentang tanggal, rental, dan plat yang benar-benar diterapkan ke tabel oleh
     * {@link #reload()} yang berhasil — dipakai kaki cetakan.
     *
     * <p>Yang tertulis di kotak saringan bisa saja belum diterapkan: operator mengubah
     * isiannya lalu langsung menekan Cetak/Pratinjau tanpa menekan "Tampilkan". Kertas
     * harus menuliskan periode dan saringan yang benar-benar sedang ditampilkan tabel,
     * bukan yang baru diketik, supaya baris dan total di kertas tetap satu keterangan
     * dengan kakinya. Saringan rental/plat terutama: cetakan sebagian data harus
     * menyebut bagiannya, sebab kertas ini dipakai menyetorkan uang.
     */
    private LocalDate fromTabel;
    private LocalDate toTabel;
    private String rentalTabel;
    private String platTabel;

    private final TransactionDao transactionDao = new TransactionDao();

    public PanelReport() {
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        add(buildFilter(), BorderLayout.NORTH);

        Theme.styleTable(table);
        // Lebar kolom laporan dikalibrasi supaya seluruh kolom tampil utuh pada jendela
        // bawaan 1320x760 SESUDAH bilah samping dipasang. Angka-angka ini bukan selera:
        // jumlahnya harus muat di lebar tabel yang tersisa, kalau tidak judul dan isi
        // kolom terpotong - dan karena tabel ini ikut dicetak, pemotongannya ikut ke kertas.
        //
        // Yang dihitung adalah teks TERLEBAR yang mungkin muncul, bukan hanya yang ada di
        // data contoh. Kalau diukur dari data contoh saja, kolom uang akan terpotong begitu
        // ada satu nota di atas sepuluh juta: data contohnya berhenti di "Rp 6.158.250",
        // sedangkan yang harus muat "Rp 99.999.999". Ada uji penjaga yang mengukur ulang
        // dengan angka terburuk itu, jadi angka di sini tidak bisa asal diubah.
        // Kolom Harga lebih lebar dari angka harganya karena ikut memuat awalan "Rp",
        // sama seperti kolom Jumlah Uang - dua kolom uang di satu tabel harus seragam.
        // ponytail: lebar ini pas untuk angka sampai Rp 99.999.999 dan bobot sampai
        // 99.999 kg - jauh di atas pemakaian nyata (truk engkel, harga sekitar Rp 1.150/kg).
        // Batasnya: nama rental di atas ~18 huruf akan terpotong, dan angka di atas
        // 100 juta juga. Kalau suatu saat itu terjadi, tambah kolom atau pendekkan judulnya,
        // jangan kecilkan kolom yang lain.
        Theme.widths(table, 105, 96, 127, 95, 97, 70, 93, 105, 88, 120);
        Theme.alignRight(table, 3, 4, 5, 6, 8, 9);

        JPanel card = Theme.card();
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        card.add(scroll, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        add(buildSummary(), BorderLayout.SOUTH);
        // Bawaan menampilkan SELURUH data, bukan hanya bulan berjalan. Kalau bawaan
        // dibatasi bulan ini, laporan terbuka dalam keadaan kosong dan terlihat seolah
        // tidak berfungsi — apalagi kalau data terakhir diisi bulan-bulan sebelumnya.
        LocalDate today = LocalDate.now();
        spTo.setValue(toDate(today));
        spFrom.setValue(toDate(tanggalTerawal(today.withDayOfMonth(1))));
        reload();
    }

    /**
     * Tanggal transaksi paling awal, untuk dipakai sebagai batas awal bawaan.
     * Kalau belum ada data sama sekali, dipakai tanggal cadangan.
     */
    private LocalDate tanggalTerawal(LocalDate cadangan) {
        try {
            LocalDate awal = transactionDao.earliestDate();
            return awal == null ? cadangan : awal;
        } catch (Exception e) {
            return cadangan;
        }
    }

    /** Benar kalau belum ada satu pun transaksi tersimpan. */
    private boolean belumAdaData() {
        try {
            return transactionDao.earliestDate() == null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Baris saringan laporan: rentang tanggal, rental, dan sepenggal plat — sama
     * seperti saringan riwayat di halaman Transaksi, supaya dua layar ini berkelakuan
     * sama. Semua isian dijaga seukuran tetap supaya barisnya tetap satu baris pada
     * jendela bawaan; kolom tabel tidak boleh dikecilkan demi memuatnya.
     */
    private JPanel buildFilter() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        Theme.applyCard(p);
        p.add(Theme.label("Dari"));
        p.add(spFrom);
        p.add(Theme.label("Sampai"));
        p.add(spTo);
        p.add(Theme.label("Rental"));
        p.add(sized(cmbRental, 150));
        p.add(Theme.label("Plat"));
        p.add(sized(txtPlat, 110));

        JButton btnShow = Theme.primary("Tampilkan");
        JButton btnPreview = Theme.plain("Pratinjau");
        JButton btnPrint = Theme.plain("Cetak");
        btnShow.addActionListener(e -> reload());
        // Enter pada kotak plat memuat ulang laporan, sama seperti Enter pada kotak
        // plat di halaman Transaksi: mengetik lalu Enter langsung diterapkan.
        txtPlat.addActionListener(e -> reload());
        btnPreview.addActionListener(e -> pratinjau());
        btnPrint.addActionListener(e -> print());
        p.add(btnShow);
        p.add(btnPreview);
        p.add(btnPrint);

        // Pesan tanggal yang tidak valid ditulis di sini, bukan lewat jendela
        // peringatan: jendela menutupi layar dan harus ditutup dulu sebelum kotaknya
        // bisa diperbaiki.
        lblStatus.setForeground(Theme.DANGER);
        p.add(lblStatus);
        return p;
    }

    /** Samakan tinggi kotak saringan, sama seperti di halaman Transaksi. */
    private static <T extends JComponent> T sized(T c, int width) {
        c.setPreferredSize(new Dimension(width, Theme.FIELD_HEIGHT));
        return c;
    }

    private JPanel buildSummary() {
        JPanel p = new JPanel(new GridLayout(1, 3, 10, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));

        lblTotalAmount.setFont(Theme.bold(19f));
        lblTotalAmount.setForeground(Theme.MONEY);
        lblTotalWeight.setFont(Theme.bold(17f));
        lblTotalWeight.setForeground(Theme.INK);
        lblRowCount.setForeground(Theme.INK_SOFT);

        JPanel a = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        a.setOpaque(false);
        a.add(Theme.label("Total uang"));
        a.add(lblTotalAmount);
        JPanel b = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        b.setOpaque(false);
        b.add(Theme.label("Total berat bersih"));
        b.add(lblTotalWeight);
        JPanel c = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        c.setOpaque(false);
        c.add(lblRowCount);

        p.add(a);
        p.add(b);
        p.add(c);
        return p;
    }

    public void reload() {
        LocalDate from = bacaTanggal(spFrom, "\"Dari\"");
        if (from == null) {
            return;
        }
        LocalDate to = bacaTanggal(spTo, "\"Sampai\"");
        if (to == null) {
            return;
        }
        lblStatus.setText("");
        // Daftar rental disegarkan tiap kali dimuat ulang: rental bisa bertambah dari
        // halaman Transaksi sewaktu halaman ini terbuka. Pilihan yang sedang dipakai
        // dipulihkan setelahnya supaya menyegarkan tidak menggeser saringan.
        isiRentalSaringan();
        String rental = rentalSaringan();
        String plat = txtPlat.getText();
        try {
            List<ReportRow> row = transactionDao.listReport(from, to, rental, plat);
            model.setRowCount(0);
            BigDecimal totalAmount = BigDecimal.ZERO;
            BigDecimal totalWeight = BigDecimal.ZERO;
            for (ReportRow b : row) {
                model.addRow(new Object[]{
                        Dates.format(b.getDate()),
                        b.getPlate(), b.getRentalName(),
                        Calculator.formatKg(b.getFieldWeight()), Calculator.formatKg(b.getFactoryWeight()),
                        Calculator.formatPercent(b.getRefractionPercent()), Calculator.formatKg(b.getNetWeight()),
                        Dates.format(b.getPaymentDate()), "Rp " + Calculator.formatCurrency(b.getPrice()),
                        "Rp " + Calculator.formatCurrency(b.getTotalAmount())});
                totalAmount = totalAmount.add(b.getTotalAmount() == null ? BigDecimal.ZERO : b.getTotalAmount());
                totalWeight = totalWeight.add(b.getNetWeight() == null ? BigDecimal.ZERO : b.getNetWeight());
            }
            lblTotalAmount.setText("Rp " + Calculator.formatCurrency(totalAmount));
            lblTotalWeight.setText(Calculator.formatKg(totalWeight));
            // Jumlah baris yang sedang tampil. Sempat hilang tanpa ketahuan: barisnya
            // tertimpa blok lain, jadi labelnya terus menulis "0 baris" walaupun
            // tabelnya penuh - dan angka itu ikut dibaca orang yang mencocokkan uang.
            lblRowCount.setText(row.size() + " baris");
            // Saringan yang baru boleh dipakai kaki cetakan setelah seluruh isi tabelnya
            // benar-benar diterapkan — kertas menuliskan apa yang sedang ditampilkan,
            // bukan apa yang baru diketik di kotak.
            fromTabel = from;
            toTabel = to;
            rentalTabel = rental;
            platTabel = plat == null ? null : plat.trim();
            boolean tersaring = rental != null || (platTabel != null && !platTabel.isEmpty());
            // Dua keadaan kosong yang berbeda butuh penjelasan berbeda: belum punya data
            // sama sekali, versus ada data tetapi tidak ada yang lolos saringan. Pesan
            // yang sama untuk keduanya membuat pengguna menduga aplikasinya rusak.
            table.setEmptyMessage(row.isEmpty()
                    ? (belumAdaData()
                            ? "Belum ada nota tersimpan. Catat dulu di halaman Transaksi."
                            : tersaring
                                    ? "Tidak ada nota yang cocok dengan saringan ini."
                                    : "Tidak ada nota pada rentang tanggal ini. Coba lebarkan tanggalnya.")
                    : "");
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    /**
     * Isi ulang daftar rental pada saringan dari data master, kata per kata seperti
     * di halaman Transaksi. Pilihan yang sedang dipakai operator dipulihkan
     * setelahnya; kalau pilihannya sudah tidak ada di data master, kembali ke
     * "Semua rental".
     */
    private void isiRentalSaringan() {
        Rental dipilih = cmbRental.getSelectedItem() instanceof Rental
                ? (Rental) cmbRental.getSelectedItem() : null;
        cmbRental.removeAllItems();
        cmbRental.addItem(SEMUA_RENTAL);
        try {
            for (Rental r : new MasterDao().listRental()) {
                cmbRental.addItem(r);
            }
        } catch (Exception e) {
            Theme.showError(this, e);
        }
        cmbRental.setSelectedIndex(0);
        if (dipilih != null) {
            for (int i = 1; i < cmbRental.getItemCount(); i++) {
                if (((Rental) cmbRental.getItemAt(i)).getRentalId() == dipilih.getRentalId()) {
                    cmbRental.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    /** Nama rental yang dipilih di saringan, atau null kalau "Semua rental". */
    private String rentalSaringan() {
        Object pilihan = cmbRental.getSelectedItem();
        return pilihan instanceof Rental ? ((Rental) pilihan).getRentalName() : null;
    }

    /**
     * Bagian laporan yang dicetak: tabel dengan kepala dan kaki halaman.
     *
     * <p>Dipakai dua-duanya — pratinjau dan pencetakan — supaya yang terlihat di layar
     * benar-benar sama dengan yang keluar di kertas.
     */
    Printable printable() {
        return table.getPrintable(JTable.PrintMode.FIT_WIDTH, kepalaCetak(), kakiCetak());
    }

    /**
     * Kepala cetak. Kepala dan kaki HARUS satu baris dan muat lebar kertas: JTable hanya
     * menggambar teksnya sekali, memakai huruf tebal 18pt untuk kepala dan 12pt untuk kaki,
     * lalu memotong sisanya — baris baru diabaikan. Karena itu judulnya pendek, dan
     * seluruh keterangan dipindah ke kaki.
     */
    private MessageFormat kepalaCetak() {
        return new MessageFormat("Laporan Penjualan Singkong");
    }

    /**
     * Kaki cetak: periode, saringan rental/plat (kalau ada), total, dan nomor halaman.
     *
     * <p>Periodenya diambil dari rentang yang diterapkan ke tabel, bukan dari kotak
     * tanggalnya: operator bisa mengubah tanggal lalu langsung menekan Cetak/Pratinjau,
     * dan kertas harus menuliskan periode yang sama dengan baris dan totalnya. Begitu
     * juga saringan rental/plat: kertas ini dipakai menyetorkan uang, jadi cetakan
     * sebagian data harus menyebut bagiannya — tanpa itu, cetakan satu rental tidak
     * bisa dibedakan dari cetakan seluruh rental. Kalau tidak ada saringan rental/plat,
     * kakinya tertulis sama seperti sebelum ada saringan itu.
     */
    private MessageFormat kakiCetak() {
        Date dari = fromTabel == null ? null : toDate(fromTabel);
        Date sampai = toTabel == null ? null : toDate(toTabel);
        StringBuilder teks = new StringBuilder(periodeRingkas(dari, sampai));
        if (rentalTabel != null && !rentalTabel.trim().isEmpty()) {
            teks.append("  ·  Rental: ").append(quote(rentalTabel.trim()));
        }
        if (platTabel != null && !platTabel.trim().isEmpty()) {
            teks.append("  ·  Plat: ").append(quote(Truck.normalizePlate(platTabel)));
        }
        teks.append("  ·  Total ").append(quote(lblTotalAmount.getText()))
                .append("  ·  ").append(quote(lblTotalWeight.getText()))
                .append("  ·  Hal. {0,number,integer}");
        return new MessageFormat(teks.toString());
    }

    /**
     * Buka jendela pratinjau. Kertas belum dipakai sampai tombol Cetak di jendela itu
     * ditekan, jadi hasil cetak bisa diperiksa dulu.
     */
    private void pratinjau() {
        if (!tanggalFilterSah()) {
            return;
        }
        if (!saringanSiapCetak("Pratinjau")) {
            return;
        }
        if (table.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Tidak ada baris untuk dicetak dengan saringan ini.",
                    "Pratinjau", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        PrintPreview dlg = new PrintPreview(SwingUtilities.getWindowAncestor(this),
                printable(), PrintPreview.pageFormat(), "Pratinjau Laporan");
        dlg.setVisible(true);
    }

    private void print() {
        if (!tanggalFilterSah()) {
            return;
        }
        if (!saringanSiapCetak("Cetak")) {
            return;
        }

        // Tabel tanpa baris tidak menghasilkan satu halaman pun, jadi tanpa pemeriksaan
        // ini menekan Cetak tidak melakukan apa-apa dan terlihat seperti aplikasi macet.
        if (table.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Tidak ada baris untuk dicetak dengan saringan ini.",
                    "Cetak", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            boolean done = table.print(JTable.PrintMode.FIT_WIDTH, kepalaCetak(), kakiCetak(),
                    true, null, true);
            if (done) {
                JOptionPane.showMessageDialog(this, "Laporan dikirim ke printer.");
            }
        } catch (PrinterException e) {
            JOptionPane.showMessageDialog(this, "Gagal mencetak: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Amankan teks yang berasal dari pengguna sebelum masuk ke pola cetak.
     * Kurung kurawal dan tanda petik punya arti khusus di {@link MessageFormat},
     * jadi nama rental seperti "CV { Mitra }" akan membuat pencetakan gagal.
     */
    private static String quote(String text) {
        return text.replace("'", "''").replace("{", "'{'").replace("}", "'}'");
    }

    /**
     * Periode bentuk ringkas untuk kepala cetak, mis. {@code 01/07 – 01/10/2026}.
     * Tahun hanya ditulis sekali kalau rentangnya masih dalam tahun yang sama,
     * supaya keterangannya tetap muat dalam satu baris kertas.
     */
    private static String periodeRingkas(Date dari, Date sampai) {
        if (dari == null || sampai == null) {
            return "-";
        }
        SimpleDateFormat hariBulan = new SimpleDateFormat("dd/MM");
        SimpleDateFormat hariBulanTahun = new SimpleDateFormat("dd/MM/yyyy");
        String awal = hariBulan.format(dari);
        String akhir = hariBulanTahun.format(sampai);
        boolean tahunSama = new SimpleDateFormat("yyyy").format(dari)
                .equals(new SimpleDateFormat("yyyy").format(sampai));
        return tahunSama ? awal + " – " + akhir : hariBulanTahun.format(dari) + " – " + akhir;
    }

    private static Date toDate(LocalDate t) {
        return Date.from(t.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** Kotak teks di dalam kotak tanggal; tulisannya di situ, bukan di nilai modelnya. */
    private static JTextField kotakTanggal(JSpinner sp) {
        return ((JSpinner.DefaultEditor) sp.getEditor()).getTextField();
    }

    /**
     * Baca tanggal yang diketik di kotak filter, atau null kalau tulisannya tidak valid.
     *
     * <p>Teksnya yang dibaca, bukan nilai modelnya: kotak tanggal hanya memindahkan
     * tulisannya ke model saat tulisannya selesai, dan yang tidak selesai dipakai apa
     * adanya — diam-diam. Teks yang tidak valid ditandai merah dan aksinya ditolak,
     * bukan bergulir sendiri (31-02-2026) atau memakai tanggal lama (5/10/2026).
     */
    private LocalDate bacaTanggal(JSpinner sp, String namaKotak) {
        JTextField kotak = kotakTanggal(sp);
        LocalDate t = Dates.parseInput(kotak.getText());
        if (t == null) {
            Theme.markError(kotak, true);
            lblStatus.setText("Tanggal " + namaKotak + " tidak valid. Tulis seperti 05-10-2026.");
            kotak.requestFocusInWindow();
            return null;
        }
        Theme.markError(kotak, false);
        // Nilai modelnya ikut disamakan supaya panah kotak tanggal memakai tanggal
        // yang benar-benar tertulis, bukan tanggal lama yang tertinggal.
        sp.setValue(toDate(t));
        return t;
    }

    /** Benar kalau kedua tanggal filter tertulis dengan benar. */
    private boolean tanggalFilterSah() {
        return bacaTanggal(spFrom, "\"Dari\"") != null && bacaTanggal(spTo, "\"Sampai\"") != null;
    }

    /**
     * Benar kalau saringan di kotak (tanggal, rental, plat) berbeda dari yang sedang
     * ditampilkan tabel.
     *
     * <p>Keadaan ini yang paling mudah terjadi: operator mengubah saringan lalu
     * langsung menekan Cetak tanpa menekan "Tampilkan". Kertasnya sudah benar - ia
     * menuliskan saringan yang sama dengan baris dan totalnya - tetapi saringannya
     * yang LAMA, sehingga maksudnya tetap tidak terlayani dan ia tidak tahu kenapa.
     * Karena itu keadaannya ditanyakan lebih dulu, bukan dibiarkan lewat diam-diam.
     *
     * <p>Rental dibandingkan lewat kunci pencocokan namanya dan plat lewat bentuk
     * seragamnya, jadi ejaan yang berbeda besar-kecil hurufnya tidak dianggap
     * saringan berbeda.
     */
    private boolean saringanBelumDiterapkan() {
        LocalDate dari = bacaTanggal(spFrom, "\"Dari\"");
        LocalDate sampai = bacaTanggal(spTo, "\"Sampai\"");
        if (dari == null || sampai == null) {
            return false;
        }
        if (!dari.equals(fromTabel) || !sampai.equals(toTabel)) {
            return true;
        }
        String rental = rentalSaringan();
        String kunciRental = Rental.matchKey(rental);
        String kunciTabel = Rental.matchKey(rentalTabel);
        if (kunciRental == null ? kunciTabel != null : !kunciRental.equals(kunciTabel)) {
            return true;
        }
        String plat = Truck.normalizePlate(txtPlat.getText());
        String platTabelBaku = Truck.normalizePlate(platTabel);
        return plat == null ? platTabelBaku != null : !plat.equals(platTabelBaku);
    }

    /**
     * Tanyakan dulu kalau saringan di kotak belum diterapkan ke tabel.
     *
     * @return true kalau pencetakan boleh diteruskan, false kalau dibatalkan operator
     */
    private boolean saringanSiapCetak(String judul) {
        if (!saringanBelumDiterapkan()) {
            return true;
        }
        Date dariTabel = fromTabel == null ? null : toDate(fromTabel);
        Date sampaiTabel = toTabel == null ? null : toDate(toTabel);
        StringBuilder masih = new StringBuilder("periode ").append(periodeRingkas(dariTabel, sampaiTabel));
        if (rentalTabel != null && !rentalTabel.trim().isEmpty()) {
            masih.append(", rental ").append(rentalTabel.trim());
        }
        if (platTabel != null && !platTabel.trim().isEmpty()) {
            masih.append(", plat ").append(Truck.normalizePlate(platTabel));
        }
        int pilih = JOptionPane.showConfirmDialog(this,
                "Saringan sudah diubah, tetapi tabelnya belum ditampilkan ulang.\n"
                        + "Yang akan dicetak masih " + masih + ".\n\n"
                        + "Tampilkan dulu dengan saringan yang baru?",
                judul, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (pilih == JOptionPane.YES_OPTION) {
            reload();
            // Setelah dimuat ulang, saringannya sudah sepadan; kalau ternyata tidak ada
            // baris, pemanggil yang memutuskan pesannya lewat pemeriksaan rowCount.
            return true;
        }
        return pilih == JOptionPane.NO_OPTION;
    }

    private static JSpinner dateSpinner() {
        JSpinner sp = new JSpinner(new SpinnerDateModel());
        sp.setEditor(new JSpinner.DateEditor(sp, "dd-MM-yyyy"));
        return sp;
    }
}
