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
import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Baris laporan yang sedang tampil, apa adanya dari database.
     *
     * <p>Tabelnya menyimpan angka yang sudah diberi satuan ("6.350 kg", "Rp 6.158.250"),
     * dan angka bersatuan tidak bisa dijumlahkan lagi. Ekspor CSV memakai daftar ini
     * supaya angkanya keluar polos. Urutannya sama dengan urutan barisnya di tabel,
     * jadi nomor baris tabel bisa dipakai untuk mencari barisnya di sini.
     */
    private List<ReportRow> barisTabel = new ArrayList<>();

    public PanelReport() {
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        add(buildFilter(), BorderLayout.NORTH);

        Theme.styleTable(table);
        // Kolom bisa diurut dengan mengklik judulnya. Laporan ini bisa memuat puluhan
        // baris, dan yang paling sering dicari justru yang paling besar - bukan yang
        // paling awal. Urutan bawaan tetap menurut tanggal (dari query): sebelum ada
        // judul yang diklik, tidak ada kunci urut, jadi tabel dan kertasnya tetap keluar
        // dalam urutan tanggal seperti sebelumnya.
        table.setAutoCreateRowSorter(true);
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
        // Empat judul kolom (Bobot Lapak, Bobot Pabrik, Refraksi, Berat Bersih) tadinya
        // pas tanpa panah sehingga terpotong begitu kolomnya diurut dan ikon panah
        // penanda urutnya (10 px) muncul; angka kolom-kolom itu di bawah sudah termasuk
        // ruang panah. Plat (97) dan Harga (89) mengikuti isian terlebarnya ("BE 0000 ZZ",
        // "Rp 00.000") - kurang 1 px pun terpotong. Tanggal cukup 105: isinya "00-00-0000"
        // dan judulnya jauh lebih pendek dari panahnya.
        //
        // Jumlah seluruh kolom (1050) harus muat di ruang tabel pada jendela terkecil:
        // 1300 - bilah samping 160 - tepi halaman 36 - tepi kartu 34 - penggeser tegak 10
        // = 1060. Sisa 10 px itu cadangan kalau penggesernya lebih tebal di tema lain.
        // ponytail: lebar ini pas untuk angka sampai Rp 99.999.999 dan bobot sampai
        // 99.999 kg - jauh di atas pemakaian nyata (truk engkel, harga sekitar Rp 1.150/kg).
        // Batasnya: nama rental di atas ~18 huruf akan terpotong, dan angka di atas
        // 100 juta juga. Kalau suatu saat itu terjadi, tambah kolom atau pendekkan judulnya,
        // jangan kecilkan kolom yang lain.
        Theme.widths(table, 105, 97, 127, 108, 110, 83, 106, 105, 89, 120);
        Theme.alignRight(table, 3, 4, 5, 6, 8, 9);
        // Angka dan tanggal dibandingkan menurut nilainya, bukan menurut tulisannya.
        // Tanpa ini, "Rp 10.000.000" terurut sebelum "Rp 6.888.500" hanya karena
        // tulisannya lebih pendek - dan angkanya tetap terbaca benar satu per satu,
        // jadi tidak ada tanda apa pun bahwa urutannya salah.
        Theme.sortTanggal(table, 0, 7);
        Theme.sortAngka(table, 3, 4, 5, 6, 8, 9);
        // Kolom teks (Plat, Rental) juga memakai pembanding tersendiri: pembanding
        // bawaan mengikuti setelan bahasa komputer, sehingga urutan nama bisa berbeda
        // antar komputer padahal laporannya dicetak dan dibaca orang lain.
        Theme.sortTeks(table, 1, 2);
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

    /**
     * Tanggal transaksi paling akhir, untuk dipakai sebagai batas akhir rentang
     * "Semua". Kalau belum ada data sama sekali, dipakai tanggal cadangan.
     */
    private LocalDate tanggalTerakhir(LocalDate cadangan) {
        try {
            LocalDate akhir = transactionDao.latestDate();
            return akhir == null ? cadangan : akhir;
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
     * Kartu saringan dua baris: baris pertama rentang tanggal, rental, dan sepenggal
     * plat — sama seperti saringan riwayat di halaman Transaksi, supaya dua layar ini
     * berkelakuan sama. Semua isian dijaga seukuran tetap supaya barisnya tetap satu
     * baris pada jendela bawaan; kolom tabel tidak boleh dikecilkan demi memuatnya.
     * Baris kedua rentang cepat.
     */
    private JPanel buildFilter() {
        JPanel kartu = new JPanel(new BorderLayout(0, 8));
        Theme.applyCard(kartu);

        JButton btnShow = Theme.primary("Tampilkan");
        JButton btnPreview = Theme.plain("Pratinjau");
        JButton btnPrint = Theme.plain("Cetak");
        btnShow.addActionListener(e -> reload());
        // Enter pada kotak plat memuat ulang laporan, sama seperti Enter pada kotak
        // plat di halaman Transaksi: mengetik lalu Enter langsung diterapkan.
        txtPlat.addActionListener(e -> reload());
        btnPreview.addActionListener(e -> pratinjau());
        btnPrint.addActionListener(e -> print());

        // Pesan tanggal yang tidak valid ditulis di sini, bukan lewat jendela
        // peringatan: jendela menutupi layar dan harus ditutup dulu sebelum kotaknya
        // bisa diperbaiki.
        lblStatus.setForeground(Theme.DANGER);

        // Gap eksplisit lewat Theme.row, bukan hgap FlowLayout: hgap ikut
        // menjorokkan baris dari tepi kiri.
        JPanel p = Theme.row(10,
                Theme.label("Dari"), spFrom,
                Theme.label("Sampai"), spTo,
                Theme.label("Rental"), sized(cmbRental, 150),
                Theme.label("Plat"), sized(txtPlat, 110),
                btnShow, btnPreview, btnPrint, lblStatus);
        kartu.add(p, BorderLayout.NORTH);

        // Rentang cepat ditaruh di baris kedua karena baris pertama sudah selebar
        // jendela minimum — tombol tambahan di situ akan terlipat lalu terpotong.
        JButton btnHariIni = Theme.plain("Hari ini");
        JButton btnBulanIni = Theme.plain("Bulan ini");
        JButton btnSemua = Theme.plain("Semua");
        btnHariIni.addActionListener(e -> rentang(LocalDate.now(), LocalDate.now()));
        btnBulanIni.addActionListener(e -> rentang(LocalDate.now().withDayOfMonth(1), LocalDate.now()));
        btnSemua.addActionListener(e -> rentang(
                tanggalTerawal(LocalDate.now().withDayOfMonth(1)), tanggalTerakhir(LocalDate.now())));
        JPanel cepat = Theme.row(10, Theme.label("Rentang cepat:"), btnHariIni, btnBulanIni, btnSemua);
        kartu.add(cepat, BorderLayout.CENTER);
        return kartu;
    }

    /** Terapkan satu rentang cepat ke kotak tanggal, lalu tampilkan ulang. */
    private void rentang(LocalDate dari, LocalDate sampai) {
        spFrom.setValue(toDate(dari));
        spTo.setValue(toDate(sampai));
        reload();
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

        // Kedua total memakai huruf dan warna yang SAMA, seperti dua angka di kotak hasil
        // halaman Transaksi. Sebelumnya "Total uang" 19pt hijau sementara "Total berat
        // bersih" 17pt hitam, jadi uangnya terlihat lebih penting daripada beratnya -
        // padahal keduanya sederajat. Warnanya hijau untuk keduanya: hijau di sini berarti
        // "ini angka hasil hitungan", bukan "ini uang".
        lblTotalAmount.setFont(Theme.semibold(17f));
        lblTotalAmount.setForeground(Theme.MONEY);
        lblTotalWeight.setFont(Theme.semibold(17f));
        lblTotalWeight.setForeground(Theme.MONEY);
        // Jumlah baris bukan angka hasil hitungan, jadi tetap kecil dan redup. Kalau ikut
        // dibesarkan, ia terbaca sebagai total ketiga yang setara dengan dua di sebelahnya.
        lblRowCount.setForeground(Theme.INK_SOFT);

        JPanel a = Theme.row(8, Theme.label("Total uang"), lblTotalAmount);
        JPanel b = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        b.setOpaque(false);
        b.add(Theme.label("Total berat bersih"));
        b.add(lblTotalWeight);
        // Ekspor ditaruh di sini, bukan di baris saringan di atas: baris itu sudah pas
        // selebar jendela minimum - sisanya hanya 10px - sehingga menambah satu tombol
        // di situ membuat tombolnya terlipat ke baris kedua lalu terpotong. Di sini
        // tempatnya juga berdampingan dengan totalnya, dan total itulah yang mau diolah.
        JButton btnRekap = Theme.plain("Rekap per rental");
        btnRekap.addActionListener(e -> tampilkanRekap());
        JButton btnCsv = Theme.plain("Ekspor CSV");
        btnCsv.addActionListener(e -> eksporCsv());
        JPanel c = Theme.rowRight(8, lblRowCount, btnRekap, btnCsv);

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
            // Daftar ini dipakai juga oleh ekspor CSV, yang butuh angkanya polos.
            barisTabel = row;
            BigDecimal totalAmount = BigDecimal.ZERO;
            BigDecimal totalWeight = BigDecimal.ZERO;
            for (ReportRow b : row) {
                model.addRow(new Object[]{
                        Dates.format(b.getDate()),
                        b.getPlate(), b.getRentalName(),
                        Calculator.formatKg(b.getFieldWeight()), Calculator.formatKg(b.getFactoryWeight()),
                        Calculator.formatPercent(b.getRefractionPercent()), Calculator.formatKg(b.getNetWeight()),
                        Dates.format(b.getPaymentDate()), "Rp " + Calculator.formatNumber(b.getPrice()),
                        "Rp " + Calculator.formatNumber(b.getTotalAmount())});
                totalAmount = totalAmount.add(b.getTotalAmount() == null ? BigDecimal.ZERO : b.getTotalAmount());
                totalWeight = totalWeight.add(b.getNetWeight() == null ? BigDecimal.ZERO : b.getNetWeight());
            }
            lblTotalAmount.setText("Rp " + Calculator.formatNumber(totalAmount));
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
     * Periode dan saringan yang benar-benar diterapkan ke tabel, sebagai teks polos.
     *
     * <p>Dipakai bersama oleh kaki cetak dan kepala berkas CSV supaya kertas dan berkas
     * menyebut cakupannya dengan kalimat yang persis sama. Teksnya sengaja polos:
     * pengamanan ({@link #quote}) urusan pemakainya — pola cetak butuh diamankan,
     * CSV tidak.
     */
    private String cakupanTabel() {
        Date dari = fromTabel == null ? null : toDate(fromTabel);
        Date sampai = toTabel == null ? null : toDate(toTabel);
        StringBuilder teks = new StringBuilder(periodeRingkas(dari, sampai));
        if (rentalTabel != null && !rentalTabel.trim().isEmpty()) {
            teks.append("  ·  Rental: \"").append(rentalTabel.trim()).append('"');
        }
        if (platTabel != null && !platTabel.trim().isEmpty()) {
            teks.append("  ·  Plat: \"").append(Truck.normalizePlate(platTabel)).append('"');
        }
        return teks.toString();
    }

    /**
     * Urutan yang sedang berlaku di tabel, mis. {@code Urut: Jumlah Uang ↓}.
     *
     * <p>Urutan layar ikut ke kertas — pencetakan menggambar tabel apa adanya — jadi
     * kertas yang dipakai mencocokkan uang harus menyebut urutannya, sama seperti ia
     * sudah menyebut periode dan saringannya. Teks kosong berarti tabel belum diurutkan
     * (urutannya tetap urutan bawaan dari query: tanggal menaik).
     */
    private String urutTabel() {
        if (!(table.getRowSorter() instanceof RowSorter)) {
            return "";
        }
        List<? extends RowSorter.SortKey> kunci = table.getRowSorter().getSortKeys();
        if (kunci.isEmpty()) {
            return "";
        }
        RowSorter.SortKey k = kunci.get(0);
        return "Urut: " + model.getColumnName(k.getColumn())
                + (k.getSortOrder() == SortOrder.ASCENDING ? " \u2191" : " \u2193");
    }

    /**
     * Kaki cetak: periode, saringan rental/plat (kalau ada), urutan (kalau ada),
     * total, dan nomor halaman.
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
        StringBuilder teks = new StringBuilder(quote(cakupanTabel()));
        String urut = urutTabel();
        if (!urut.isEmpty()) {
            teks.append("  ·  ").append(quote(urut));
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
        if (!saringanSiapCetak("Pratinjau", "dicetak")) {
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
        if (!saringanSiapCetak("Cetak", "dicetak")) {
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
     * Rekap per rental dari baris yang SEDANG TAMPIL, bukan seluruh isi database.
     *
     * <p>Diambil dari daftar baris yang sudah tersaring, bukan lewat query terpisah: query
     * rekap yang tidak ikut disaring akan diam-diam menjumlah seluruh rental sementara tabel
     * di atasnya hanya menampilkan satu rental - dan total yang lebih besar dari barisnya
     * itulah yang bikin orang salah menyetor uang.
     *
     * <p>Tiap baris: {nama rental, jumlah nota, total berat bersih, total uang}. Baris terakhir
     * berisi jumlah keseluruhan dengan nama "Jumlah".
     */
    List<Object[]> rekapPerRental() {
        Map<String, Object[]> grup = new LinkedHashMap<>();
        for (ReportRow b : barisTabel) {
            String nama = b.getRentalName() == null || b.getRentalName().trim().isEmpty()
                    ? "(tanpa rental)" : b.getRentalName().trim();
            Object[] g = grup.get(nama);
            if (g == null) {
                g = new Object[]{nama, 0, BigDecimal.ZERO, BigDecimal.ZERO};
                grup.put(nama, g);
            }
            g[1] = (Integer) g[1] + 1;
            g[2] = ((BigDecimal) g[2]).add(b.getNetWeight() == null ? BigDecimal.ZERO : b.getNetWeight());
            g[3] = ((BigDecimal) g[3]).add(b.getTotalAmount() == null ? BigDecimal.ZERO : b.getTotalAmount());
        }
        List<Object[]> hasil = new ArrayList<>(grup.values());
        if (!hasil.isEmpty()) {
            int totalNota = 0;
            BigDecimal totalBerat = BigDecimal.ZERO;
            BigDecimal totalUang = BigDecimal.ZERO;
            for (Object[] g : hasil) {
                totalNota += (Integer) g[1];
                totalBerat = totalBerat.add((BigDecimal) g[2]);
                totalUang = totalUang.add((BigDecimal) g[3]);
            }
            hasil.add(new Object[]{"Jumlah", totalNota, totalBerat, totalUang});
        }
        return hasil;
    }

    /** Tampilkan rekap per rental dari baris yang sedang tampil, dalam jendela kecil. */
    private void tampilkanRekap() {
        if (table.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Tidak ada baris untuk direkap dengan saringan ini.",
                    "Rekap per rental", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DefaultTableModel m = new DefaultTableModel(
                new Object[]{"Rental", "Nota", "Berat Bersih", "Jumlah Uang"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        for (Object[] g : rekapPerRental()) {
            m.addRow(new Object[]{g[0], g[1],
                    Calculator.formatKg((BigDecimal) g[2]),
                    "Rp " + Calculator.formatNumber((BigDecimal) g[3])});
        }
        Theme.Table t = new Theme.Table(m, "");
        Theme.styleTable(t);
        Theme.widths(t, 220, 70, 120, 140);
        Theme.alignRight(t, 1, 2, 3);
        Theme.emphasis(t, 3);
        JScrollPane scroll = new JScrollPane(t);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(560, Math.min(400, 46 + t.getRowCount() * Theme.ROW_HEIGHT)));
        JOptionPane.showMessageDialog(this, scroll, "Rekap per rental", JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Tulis baris yang sedang tampil ke berkas CSV, supaya angkanya bisa dijumlah ulang
     * di Excel. Laporan ini dipakai menyetorkan uang, dan dari layar angkanya hanya bisa
     * dibaca - tidak bisa diolah.
     *
     * <p>Isinya diambil dari baris yang SEDANG TAMPIL dan dalam urutan yang sedang
     * tampil, jadi berkasnya selalu sama dengan yang terlihat di layar, bukan seluruh isi
     * database. Urutannya penting: kalau judul kolom baru diklik untuk mengurutkan, yang
     * diekspor harus urutan itu juga.
     *
     * <p>Pemisahnya titik koma, bukan koma. Excel berbahasa Indonesia memakai koma
     * sebagai pemisah desimal, jadi berkas berpemisah koma terbaca sebagai satu kolom
     * panjang. Angkanya ditulis polos tanpa titik pemisah ribuan supaya bisa langsung
     * dijumlahkan - angka bersatuan seperti "6.350 kg" tidak bisa.
     */
    private void eksporCsv() {
        if (!tanggalFilterSah() || !saringanSiapCetak("Ekspor", "diekspor")) {
            return;
        }
        if (table.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "Tidak ada baris untuk diekspor dengan saringan ini.",
                    "Ekspor CSV", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser pilih = new JFileChooser();
        // Nama bawaan memakai periode yang diterapkan, bukan tanggal hari ini:
        // nama "laporan-2026-10-05.csv" menyesatkan kalau yang diekspor periode
        // Juli sampai September.
        pilih.setSelectedFile(new File(fromTabel != null && toTabel != null
                ? "laporan-" + fromTabel + "_" + toTabel + ".csv"
                : "laporan-" + LocalDate.now() + ".csv"));
        if (pilih.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File berkas = pilih.getSelectedFile();
        // Penimpaan ditanyakan di sini karena showSaveDialog TIDAK menanyakannya,
        // sedangkan aplikasi ini sudah berjanji di pencadangan untuk tidak menimpa
        // berkas lama.
        if (berkas.exists()) {
            int timpa = JOptionPane.showConfirmDialog(this,
                    "Berkas " + berkas.getName() + " sudah ada. Timpa berkas itu?",
                    "Ekspor CSV", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (timpa != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            Files.write(berkas.toPath(), isiCsv(), StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(this, "Laporan diekspor ke:\n" + berkas.getAbsolutePath());
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    /**
     * Isi berkas CSV, satu untai per baris - dipisah dari penulisannya supaya isinya bisa
     * diperiksa tanpa membuka jendela "simpan berkas", yang menunggu jawaban orang.
     *
     * <p>Berkasnya menyebut cakupan dan urutannya karena berkas ini dokumen yang sama
     * dengan kertas - laporan sebagian data harus menyebut bagiannya, sebab dipakai
     * menyetorkan uang. Susut ditulis sebagai kolomnya sendiri supaya bisa diperiksa
     * di Excel; nilainya kosong kalau bobot barisnya tidak lengkap.
     */
    List<String> isiCsv() {
        List<String> isi = new ArrayList<>();
        isi.add("Laporan Penjualan Singkong");
        isi.add(cakupanTabel());
        String urut = urutTabel();
        if (!urut.isEmpty()) {
            isi.add(urut);
        }
        isi.add("");
        isi.add("Tanggal;Plat;Rental;Bobot Lapak;Bobot Pabrik;Refraksi;"
                + "Berat Bersih;Susut;Tgl Lunas;Harga;Jumlah Uang");
        for (int baris = 0; baris < table.getRowCount(); baris++) {
            ReportRow b = barisTabel.get(table.convertRowIndexToModel(baris));
            isi.add(kolom(Dates.format(b.getDate()))
                    + ";" + kolom(b.getPlate())
                    + ";" + kolom(b.getRentalName())
                    + ";" + kolom(angka(b.getFieldWeight()))
                    + ";" + kolom(angka(b.getFactoryWeight()))
                    + ";" + kolom(angka(b.getRefractionPercent()))
                    + ";" + kolom(angka(b.getNetWeight()))
                    + ";" + kolom(angka(susut(b)))
                    + ";" + kolom(Dates.format(b.getPaymentDate()))
                    + ";" + kolom(angka(b.getPrice()))
                    + ";" + kolom(angka(b.getTotalAmount())));
        }
        return isi;
    }

    /** Susut satu baris, atau null kalau bobotnya tidak lengkap. */
    private static BigDecimal susut(ReportRow b) {
        return b.getFieldWeight() == null || b.getFactoryWeight() == null
                ? null : Calculator.shrinkage(b.getFieldWeight(), b.getFactoryWeight());
    }

    /** Satu isian CSV: dibungkus tanda petik kalau isinya memuat pemisah, petik, atau baris baru. */
    private static String kolom(String teks) {
        if (teks == null || teks.isEmpty()) {
            return "";
        }
        boolean perluPetik = teks.indexOf(';') >= 0 || teks.indexOf('"') >= 0
                || teks.indexOf('\n') >= 0 || teks.indexOf('\r') >= 0;
        return perluPetik ? '"' + teks.replace("\"", "\"\"") + '"' : teks;
    }

    /** Angka polos tanpa pemisah ribuan, supaya bisa langsung dijumlahkan di Excel. */
    private static String angka(BigDecimal nilai) {
        return nilai == null ? "" : nilai.stripTrailingZeros().toPlainString();
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
    private boolean saringanSiapCetak(String judul, String kataKerja) {
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
                        + "Yang akan " + kataKerja + " masih " + masih + ".\n\n"
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
