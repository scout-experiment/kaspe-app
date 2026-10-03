package kaspe.ui;

import kaspe.Calculator;
import kaspe.dao.TransactionDao;
import kaspe.model.ReportRow;
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

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Tanggal", "Plat", "Rental", "Bobot Lapak", "Bobot Pabrik",
                    "Refraksi (%)", "Berat Bersih", "Tgl Lunas", "Harga", "Jumlah Uang"}, 0) {
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
        Theme.widths(table, 105, 96, 127, 95, 97, 93, 93, 105, 88, 120);
        Theme.alignRight(table, 3, 4, 5, 6, 8, 9);
        // Kolom uang ditegaskan. Ketebalan huruf yang menonjolkannya, bukan warnanya -
        // di kertas warnanya menjadi abu-abu dan yang tersisa hanya ketebalannya.
        Theme.emphasis(table, 9);

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

    private JPanel buildFilter() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        Theme.applyCard(p);
        p.add(Theme.label("Dari"));
        p.add(spFrom);
        p.add(Theme.label("Sampai"));
        p.add(spTo);

        JButton btnShow = Theme.primary("Tampilkan");
        JButton btnPreview = Theme.plain("Pratinjau");
        JButton btnPrint = Theme.plain("Cetak");
        btnShow.addActionListener(e -> reload());
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
        try {
            List<ReportRow> row = transactionDao.listReport(from, to);
            model.setRowCount(0);
            BigDecimal totalAmount = BigDecimal.ZERO;
            BigDecimal totalWeight = BigDecimal.ZERO;
            for (ReportRow b : row) {
                model.addRow(new Object[]{
                        Dates.format(b.getDate()),
                        b.getPlate(), b.getRentalName(),
                        Calculator.formatCurrency(b.getFieldWeight()), Calculator.formatCurrency(b.getFactoryWeight()),
                        Calculator.formatCurrency(b.getRefractionPercent()), Calculator.formatCurrency(b.getNetWeight()),
                        Dates.format(b.getPaymentDate()), "Rp " + Calculator.formatCurrency(b.getPrice()),
                        "Rp " + Calculator.formatCurrency(b.getTotalAmount())});
                totalAmount = totalAmount.add(b.getTotalAmount() == null ? BigDecimal.ZERO : b.getTotalAmount());
                totalWeight = totalWeight.add(b.getNetWeight() == null ? BigDecimal.ZERO : b.getNetWeight());
            }
            lblTotalAmount.setText("Rp " + Calculator.formatCurrency(totalAmount));
            lblTotalWeight.setText(Calculator.formatCurrency(totalWeight) + " kg");
            lblRowCount.setText(row.size() + " baris");
            // Dua keadaan kosong yang berbeda butuh penjelasan berbeda: belum punya data
            // sama sekali, versus punya data tapi tidak ada yang masuk rentang tanggal.
            // Pesan yang sama untuk keduanya membuat pengguna menduga aplikasinya rusak.
            table.setEmptyMessage(row.isEmpty()
                    ? (belumAdaData()
                            ? "Belum ada nota tersimpan. Catat dulu di halaman Transaksi."
                            : "Tidak ada nota pada rentang tanggal ini. Coba lebarkan tanggalnya.")
                    : "");
        } catch (Exception e) {
            Theme.showError(this, e);
        }
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

    /** Kaki cetak: periode, total, dan nomor halaman. */
    private MessageFormat kakiCetak() {
        return new MessageFormat(periodeRingkas((Date) spFrom.getValue(), (Date) spTo.getValue())
                + "  ·  Total " + quote(lblTotalAmount.getText())
                + "  ·  " + quote(lblTotalWeight.getText())
                + "  ·  Hal. {0,number,integer}");
    }

    /**
     * Buka jendela pratinjau. Kertas belum dipakai sampai tombol Cetak di jendela itu
     * ditekan, jadi hasil cetak bisa diperiksa dulu.
     */
    private void pratinjau() {
        if (!tanggalFilterSah()) {
            return;
        }
        if (table.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Tidak ada baris untuk dicetak pada rentang tanggal ini.",
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

        // Tabel tanpa baris tidak menghasilkan satu halaman pun, jadi tanpa pemeriksaan
        // ini menekan Cetak tidak melakukan apa-apa dan terlihat seperti aplikasi macet.
        if (table.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Tidak ada baris untuk dicetak pada rentang tanggal ini.",
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
        // Nilai modelnya ikut disamakan supaya kaki cetakan memakai tanggal yang
        // benar-benar tertulis, bukan tanggal lama yang tertinggal.
        sp.setValue(toDate(t));
        return t;
    }

    /** Benar kalau kedua tanggal filter tertulis dengan benar. */
    private boolean tanggalFilterSah() {
        return bacaTanggal(spFrom, "\"Dari\"") != null && bacaTanggal(spTo, "\"Sampai\"") != null;
    }

    private static JSpinner dateSpinner() {
        JSpinner sp = new JSpinner(new SpinnerDateModel());
        sp.setEditor(new JSpinner.DateEditor(sp, "dd-MM-yyyy"));
        return sp;
    }
}
