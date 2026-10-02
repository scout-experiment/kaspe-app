package kaspe.test;

import kaspe.Db;
import kaspe.dao.MasterDao;
import kaspe.model.Rental;
import kaspe.model.Truck;
import kaspe.ui.HeaderBar;
import kaspe.ui.NavBar;
import kaspe.ui.PagePanel;
import kaspe.ui.PanelDashboard;
import kaspe.ui.PanelReport;
import kaspe.ui.PanelMaster;
import kaspe.ui.PanelTransaction;
import kaspe.ui.PrintPreview;
import kaspe.ui.Theme;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Uji tampilan tanpa layar (headless):
 * bangun tiap panel, gambar ke PNG, pastikan tidak error.
 * Hasil gambar bisa dibuka manual untuk cek tampilan.
 *
 * Jalankan: java -Djava.awt.headless=true -cp build:lib/h2-2.1.214.jar kaspe.test.TestUi
 */
public class TestUi {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=== UJI TAMPILAN (headless, hasil berupa PNG) ===\n");

        Theme.install();

        Db.setConfiguration("org.h2.Driver",
                "jdbc:h2:mem:uitest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        createSchema();
        fillData();

        Path out = Paths.get("build/screenshots");
        Files.createDirectories(out);

        render(new PanelDashboard(), "1-dashboard.png", out);
        render(new PanelTransaction(), "2-transaction-form.png", out);
        render(new PanelMaster(), "3-master.png", out);
        render(new PanelReport(), "5-report.png", out);
        render(reportTanpaData(), "6-report-kosong.png", out);
        render(jendelaUtama(), "7-window.png", out);

        // Pemeriksaan yang tidak lewat gambar: bilah atas harus menulis nama HALAMAN,
        // bukan nama aplikasi. Nama aplikasi sudah ada di judul jendela.
        check("bilah atas menulis nama halaman", headerMenulisNamaHalaman());
        // Tanggal panjang dipakai di kepala halaman; nama hari dan bulannya ditulis
        // sendiri, jadi hasilnya tidak boleh berubah mengikuti data bahasa komputer.
        check("tanggal panjang berbahasa Indonesia", tanggalPanjang());
        // Seluruh kolom laporan harus tampil utuh pada jendela bawaan SESUDAH bilah
        // samping dipasang. Tabel laporan ikut dicetak ke kertas, jadi kolom yang
        // terpotong di layar juga terpotong di kertas.
        check("kolom laporan utuh pada jendela bawaan", kolomTabelUtuh(new PanelReport()));
        // Baris menu di bilah samping harus benar-benar punya lebar. Pernah terjadi
        // seluruh barisnya berlebar negatif sehingga tidak tergambar sama sekali, dan
        // pemeriksaan gambar tidak menangkapnya karena bagian lain jendela tetap tergambar.
        check("baris menu bilah samping tergambar", barisMenuTergambar());
        // Memilih baris di halaman data master harus mengisi kotak isiannya tanpa gagal.
        // Pemeriksaan gambar tidak menangkap ini: barisnya tetap tergambar rapi, dan
        // kesalahannya baru muncul saat barisnya benar-benar diklik pengguna.
        check("pilih baris data master tidak gagal", pilihBarisMaster());
        // Truk tidak boleh tersimpan tanpa pemilik. Kesalahan ini pernah terjadi tanpa
        // pesan apa pun, dan uangnya masuk ke pemilik yang salah di laporan.
        check("truk tidak bisa disimpan tanpa pemilik", trukButuhPemilik());
        // Truk harus bisa dipindah ke pemilik lain dari halaman data master, dan nomor
        // platnya tidak boleh ikut berubah. Dua kesalahan pernah terjadi di sini, keduanya
        // tanpa suara: tombolnya ikut hilang waktu halaman master digabung, dan
        // perpindahannya sempat mengambil nomor plat dari kotak isian yang bisa dikosongkan.
        check("pindah pemilik truk tidak merusak plat", pindahPemilikTruk());
        // Rental yang tampil di layar transaksi harus sama dengan yang tercatat. Pernah
        // terjadi sebaliknya: layar membuka dengan plat milik satu rental dan nama rental
        // milik rental lain, dan plat yang baru diketik mewarisi rental baris sebelumnya.
        // Untuk aplikasi yang menghitung uang per pemilik truk, itu menulis data yang salah.
        check("rental di layar transaksi sama dengan yang tersimpan", rentalIkutPlatBenar());
        // Nama rental bisa diketik langsung di layar transaksi, sama seperti plat. Yang
        // diketik harus dipakai apa adanya sebagai pemilik truk baru, dan ejaan yang
        // berbeda besar-kecil hurufnya tidak boleh menjadi rental kedua - kalau menjadi
        // dua, total uang per pemilik ikut terpecah.
        check("nama rental yang diketik dipakai apa adanya", rentalDiketikBenar());
        // Seluruh kolom laporan harus utuh juga pada tabel TRANSAKSI, bukan hanya laporan.
        check("kolom tabel transaksi utuh", kolomTabelUtuh(new PanelTransaction()));
        // Angka bulan berjalan di halaman pembuka harus dihitung dari awal bulan, bukan
        // dari sekian hari ke belakang. Batas itu tidak kelihatan di layar - yang tampak
        // hanya hasilnya - sehingga salah batas berarti angka yang salah tanpa satu pun
        // tanda. Diperiksa dengan membandingkan angka yang benar-benar tertulis di kartu
        // dengan hitungan query yang sama memakai batas awal bulan.
        check("angka bulan berjalan di beranda", angkaBulanBerjalan());
        // Huruf bawaan harus benar-benar terpasang. Kalau berkas huruf gagal dimuat,
        // FlatLaf diam-diam memakai huruf lain dan seluruh uji gambar tetap lolos.
        check("huruf " + Theme.fontFamily() + " terpasang",
                fontTerpasang(Theme.fontFamily()));
        // Huruf bawaan hanya dipakai kalau versi Java-nya memang mendukung.
        check("aturan versi huruf bawaan", aturanVersiHuruf());
        // Pratinjau cetak harus menghasilkan halaman berisi, bukan kertas putih kosong.
        check("pratinjau cetak menghasilkan halaman", pratinjauAdaIsinya());

        System.out.println("\n=== HASIL: " + passed + " lulus, " + failed + " gagal ===");
        System.out.println("Gambar ada di: " + out.toAbsolutePath());
        if (failed > 0) {
            System.exit(1);
        }
    }

    /**
     * Jendela utama apa adanya: bilah samping, bilah nama halaman, dan satu halaman.
     * Dipakai supaya susunan yang dipakai aplikasi sungguhan ikut diperiksa — kalau
     * salah satu bagiannya lupa dipasang, yang tergambar hanya sebagian jendela.
     */
    private static JPanel jendelaUtama() {
        PagePanel halaman = new PagePanel();
        JPanel layar = PagePanel.shell(halaman);
        halaman.showPanel(new PanelReport(), "Laporan", "Rekap penjualan per periode.");
        return layar;
    }

    /** Bilah atas menulis nama halaman yang sedang dibuka, dan bukan nama aplikasi. */
    private static boolean headerMenulisNamaHalaman() {
        HeaderBar bar = new HeaderBar("Beranda", "Ringkasan catatan pengiriman singkong.");
        boolean awalBenar = "Beranda".equals(bar.pageName());
        bar.setPage("Data Master · Truk / Plat", "Kelola plat nomor truk.");
        boolean gantiBenar = "Data Master · Truk / Plat".equals(bar.pageName());
        boolean keteranganBenar = "Kelola plat nomor truk.".equals(bar.subtitle());
        boolean bukanNamaAplikasi = !bar.pageName().contains("Kaspe");
        boolean adaTanggal = bar.dateText() != null && !bar.dateText().isEmpty();
        return awalBenar && gantiBenar && keteranganBenar && bukanNamaAplikasi && adaTanggal;
    }

    /**
     * Tanggal panjang harus berbahasa Indonesia, bukan mengikuti data bahasa komputer.
     *
     * <p>Diuji dengan tanggal yang hari dan bulannya berbeda-beda, karena kesalahan
     * paling mudah terjadi pada pergeseran nama hari: Senin sampai Minggu di Java
     * dimulai dari Senin, sedangkan daftar nama hari dimulai dari Minggu.
     */
    private static boolean tanggalPanjang() {
        return "Jumat, 02 Oktober 2026".equals(
                        kaspe.util.Dates.longFormat(java.time.LocalDate.of(2026, 10, 2)))
                && "Senin, 05 Januari 2026".equals(
                        kaspe.util.Dates.longFormat(java.time.LocalDate.of(2026, 1, 5)))
                && "Minggu, 01 Maret 2026".equals(
                        kaspe.util.Dates.longFormat(java.time.LocalDate.of(2026, 3, 1)))
                && "Kamis, 31 Desember 2026".equals(
                        kaspe.util.Dates.longFormat(java.time.LocalDate.of(2026, 12, 31)))
                && "".equals(kaspe.util.Dates.longFormat(null));
    }

    /**
     * Semua kolom tabel harus tampil selebar yang dibutuhkannya pada jendela bawaan.
     *
     * <p>Yang diukur adalah teks TERLEBAR di tiap kolom — judulnya maupun isinya — bukan
     * hanya judulnya. Isi yang lebih panjang daripada judulnya akan terpotong juga, dan
     * itu sempat lolos dua kali: nama rental "Rental Sinar Jaya" terpotong, dan tanggal
     * "06-07-2026" terpotong walau judul kolomnya muat.
     *
     * <p>Diukur dengan lebar huruf yang sesungguhnya, bukan perkiraan. Sel pernah terpotong
     * hanya karena kurang satu piksel: teksnya selebar 75 px sedangkan ruang yang tersisa
     * di dalam sel 74 px. Karena itu lebarnya diambil dari huruf yang paling lebar di
     * antara yang mungkin dipakai (biasa dan setengah tebal), ditambah jarak kiri-kanan
     * sel beserta garis antar selnya.
     *
     * <p>Karena tabel laporan ikut dicetak ke kertas dan pencetakan hanya memperkecil
     * tabel apa adanya, pemotongan itu ikut ke kertas — jadi ini pemeriksaan hasil cetak,
     * bukan sekadar kerapian layar. Diukur pada ukuran jendela yang benar-benar dipakai
     * aplikasi, yaitu ukuran di {@link kaspe.ui.MainFrame}.
     */
    private static boolean kolomTabelUtuh(JPanel panel) throws Exception {
        PagePanel halaman = new PagePanel();
        JPanel layar = PagePanel.shell(halaman);
        halaman.showPanel(panel, "Laporan", "Rekap penjualan per periode.");

        // Ukuran jendela bawaan, dikurangi tinggi bilah judul jendela sistem.
        layar.setSize(1320, 760);
        for (int i = 0; i < 3; i++) {
            layar.doLayout();
            layoutDeep(layar);
        }

        JTable t = tabel(panel);
        java.awt.FontMetrics biasa = t.getFontMetrics(new java.awt.Font(Theme.fontFamily(),
                java.awt.Font.PLAIN, Theme.FONT_SIZE));
        java.awt.FontMetrics tebal = t.getFontMetrics(Theme.semibold(Theme.FONT_SIZE));
        java.awt.FontMetrics judul = t.getFontMetrics(Theme.semibold(Theme.FONT_SIZE - 1f));

        // Jarak kiri-kanan isi sel (Table.cellMargins) ditambah garis antar sel.
        final int margin = 21;

        boolean utuh = true;
        for (int c = 0; c < t.getColumnCount(); c++) {
            javax.swing.table.TableColumn col = t.getColumnModel().getColumn(c);
            String nama = String.valueOf(col.getHeaderValue());

            int butuh = judul.stringWidth(nama);
            for (int r = 0; r < t.getRowCount(); r++) {
                Object nilai = t.getValueAt(r, c);
                if (nilai != null) {
                    String teks = nilai.toString();
                    butuh = Math.max(butuh, Math.max(biasa.stringWidth(teks), tebal.stringWidth(teks)));
                }
            }
            // Angka terburuk yang mungkin muncul, bukan hanya yang ada di data contoh.
            // Data contoh berhenti di "Rp 6.158.250", jadi kalau diukur dari situ saja
            // kolom uang akan terpotong begitu ada satu nota di atas sepuluh juta -
            // dan itu baru ketahuan setelah dipakai.
            String terburuk = TERBURUK.get(nama);
            if (terburuk != null) {
                butuh = Math.max(butuh, Math.max(biasa.stringWidth(terburuk), tebal.stringWidth(terburuk)));
            }
            butuh += margin;

            if (col.getWidth() < butuh) {
                System.out.println("        kolom " + c + " (" + nama + "): "
                        + col.getWidth() + "px, butuh " + butuh + "px");
                utuh = false;
            }
        }
        return utuh;
    }

    /**
     * Angka "bulan ini" di halaman pembuka harus sama dengan total uang dari awal bulan
     * sampai hari ini.
     *
     * <p>Batasnya tidak kelihatan di layar: yang tampak hanya hasil angkanya. Salah batas —
     * misalnya dipakai "30 hari terakhir" — menghasilkan angka yang tetap masuk akal,
     * sehingga tidak ada yang menyadarinya sampai uangnya dicocokkan per bulan.
     */
    private static boolean angkaBulanBerjalan() throws Exception {
        PanelDashboard panel = new PanelDashboard();
        String tertulis = null;
        for (javax.swing.JLabel label : semuaLabel(panel)) {
            String teks = label.getText();
            if (teks != null && teks.startsWith("bulan ini ")) {
                tertulis = teks.substring("bulan ini ".length()).trim();
            }
        }
        if (tertulis == null) {
            System.out.println("        keterangan 'bulan ini' tidak ada di kartu beranda");
            return false;
        }

        java.time.LocalDate hariIni = java.time.LocalDate.now();
        java.math.BigDecimal bulanIni = new kaspe.dao.TransactionDao()
                .totalAmount(hariIni.withDayOfMonth(1), hariIni);
        String seharusnya = "Rp " + kaspe.Calculator.formatCurrency(bulanIni);
        if (!seharusnya.equals(tertulis)) {
            System.out.println("        tertulis '" + tertulis + "', seharusnya '" + seharusnya + "'");
            return false;
        }
        return true;
    }

    /** Semua label di dalam wadah ini, sampai ke anak terdalam. */
    private static java.util.List<javax.swing.JLabel> semuaLabel(Container c) {
        java.util.List<javax.swing.JLabel> hasil = new java.util.ArrayList<>();
        for (Component anak : c.getComponents()) {
            if (anak instanceof javax.swing.JLabel) {
                hasil.add((javax.swing.JLabel) anak);
            }
            if (anak instanceof Container) {
                hasil.addAll(semuaLabel((Container) anak));
            }
        }
        return hasil;
    }

    /**
     * Angka dan teks terburuk yang harus muat di tiap kolom laporan.
     *
     * <p>Diisi angka nol semua, bukan angka "yang kelihatan besar". Di huruf Inter, angka
     * nol paling lebar (9 px) sedangkan angka satu paling sempit (6 px) — jadi tanggal
     * "31-12-2026" justru lebih sempit daripada "00-00-0000", dan menguji dengan tanggal
     * asli akan meloloskan kolom yang sebenarnya kurang lebar.
     *
     * <p>Batas angkanya jauh di atas pemakaian nyata: bobot sampai 99.999 kg (truk engkel
     * hanya sekitar 8 ton) dan uang sampai Rp 99.999.999.
     */
    private static final java.util.Map<String, String> TERBURUK = new java.util.HashMap<>();

    static {
        TERBURUK.put("Tanggal", "00-00-0000");
        TERBURUK.put("Plat", "BE 0000 ZZ");
        TERBURUK.put("Rental", "Rental Sinar Jaya");
        TERBURUK.put("Bobot Lapak", "00.000");
        TERBURUK.put("Bobot Pabrik", "00.000");
        TERBURUK.put("Refraksi (%)", "000");
        TERBURUK.put("Berat Bersih", "00.000");
        TERBURUK.put("Tgl Lunas", "00-00-0000");
        TERBURUK.put("Harga", "Rp 00.000");
        TERBURUK.put("Jumlah Uang", "Rp 00.000.000");
    }

    /**
     * Memilih baris di halaman data master harus mengisi kotak isiannya, tanpa gagal.
     *
     * <p>Halaman itu membaca isi tabel berdasarkan nomor kolom. Waktu jumlah kolomnya
     * berubah — "No HP" dan "Keterangan" dibuang — pembacaannya ikut harus berubah; kalau
     * tidak, memilih satu baris langsung gagal dan pengguna tidak bisa mengubah data apa
     * pun. Pemeriksaan gambar tidak menangkapnya, karena barisnya tetap tergambar rapi
     * dan kesalahannya baru muncul saat barisnya benar-benar diklik.
     *
     * <p>Diperiksa untuk kedua halaman sekaligus, dan yang diperiksa bukan cuma "tidak
     * gagal": nama yang muncul di kotak isian harus sama dengan nama di barisnya, supaya
     * halaman yang mengisi kotak dengan kolom yang salah ikut ketahuan.
     */
    private static boolean pilihBarisMaster() throws Exception {
        PanelMaster panel = new PanelMaster();
        JTable rental = (JTable) field(panel, "tableRental");
        JTable truk = (JTable) field(panel, "tableTruk");

        if (rental.getRowCount() == 0) {
            System.out.println("        tidak ada rental untuk dipilih");
            return false;
        }
        // Kesalahannya ditangkap di sini supaya dilaporkan sebagai satu pemeriksaan yang
        // gagal, bukan sebagai kesalahan yang menghentikan seluruh berkas uji. Kalau
        // dibiarkan naik, uji setelahnya tidak ikut berjalan dan hasilnya terlihat seperti
        // uji yang belum selesai, bukan uji yang menemukan masalah.
        try {
            rental.setRowSelectionInterval(0, 0);
        } catch (RuntimeException e) {
            System.out.println("        memilih baris rental gagal - " + e);
            return false;
        }

        // Menyorot satu pemilik harus mengisi kotak namanya...
        String diTabel = String.valueOf(rental.getValueAt(0, 1));
        String diKotak = ((javax.swing.text.JTextComponent) field(panel, "fNama")).getText();
        if (!diTabel.equals(diKotak)) {
            System.out.println("        kotak nama berisi '" + diKotak + "', seharusnya '" + diTabel + "'");
            return false;
        }

        // ...dan menampilkan truk miliknya di kanan, bukan truk milik pemilik lain.
        if (truk.getRowCount() == 0) {
            System.out.println("        truk milik '" + diTabel + "' tidak muncul di kanan");
            return false;
        }
        for (int i = 0; i < truk.getRowCount(); i++) {
            String plat = String.valueOf(truk.getValueAt(i, 1));
            if (!milikRental(plat, diTabel)) {
                System.out.println("        truk '" + plat + "' muncul di bawah '" + diTabel
                        + "', padahal bukan pemiliknya");
                return false;
            }
        }

        // Menyorot satu truk harus mengisi kotak platnya.
        try {
            truk.setRowSelectionInterval(0, 0);
        } catch (RuntimeException e) {
            System.out.println("        memilih baris truk gagal - " + e);
            return false;
        }
        String platTabel = String.valueOf(truk.getValueAt(0, 1));
        String platKotak = ((javax.swing.text.JTextComponent) field(panel, "fPlat")).getText();
        if (!platTabel.equals(platKotak)) {
            System.out.println("        kotak plat berisi '" + platKotak + "', seharusnya '" + platTabel + "'");
            return false;
        }
        return true;
    }

    /** Benar kalau plat itu memang milik rental bernama {@code nama}. */
    private static boolean milikRental(String plat, String nama) throws Exception {
        for (Truck t : new MasterDao().listTrucks()) {
            if (t.getPlate().equals(plat)) {
                return t.getRentalName() != null && t.getRentalName().equals(nama);
            }
        }
        return false;
    }

    /**
     * Truk tidak boleh bisa disimpan tanpa pemilik yang dipilih.
     *
     * <p>Ini kesalahan yang pernah benar-benar terjadi dan tidak terlihat: kotak pilihan
     * pemilik selalu sudah terisi begitu halaman dibuka, jadi mengetik plat baru lalu
     * menekan Tambah / Simpan tanpa menyentuh kotak itu mencatat truk sebagai milik
     * pemilik yang kebetulan tampil pertama. Sekarang pemiliknya diturunkan dari baris
     * yang disorot, dan tanpa baris tersorot penolakannya harus jelas.
     */
    private static boolean trukButuhPemilik() throws Exception {
        PanelMaster panel = new PanelMaster();
        JTable rental = (JTable) field(panel, "tableRental");

        // Keadaan awal halaman: belum ada yang disorot, dan tidak ada yang terpilih sendiri.
        rental.clearSelection();
        try {
            klik(panel, "clearRentalForm");
        } catch (Exception e) {
            System.out.println("        membersihkan pilihan gagal: " + e);
            return false;
        }

        int sebelum = new MasterDao().listTrucks().size();
        isi(panel, "fPlat", "ZZ 7777 ZZ");
        // Kegagalan di dalam penyimpanan memunculkan jendela pesan, dan jendela itu tidak
        // bisa dibuat saat pengujian berjalan tanpa layar. Kalau dibiarkan naik, seluruh
        // berkas uji ini mati dan hasilnya tidak pernah tercetak - kegagalan yang tidak
        // terlihat sama berbahayanya dengan kegagalan yang lolos.
        try {
            klik(panel, "saveTruck");
        } catch (Exception e) {
            Throwable sebab = e.getCause() == null ? e : e.getCause();
            System.out.println("        menyimpan truk tanpa pemilik tidak ditolak: " + sebab);
            return false;
        }

        int sesudah = new MasterDao().listTrucks().size();
        if (sesudah != sebelum) {
            System.out.println("        truk tanpa pemilik ikut tersimpan: " + sebelum + " -> " + sesudah);
            return false;
        }
        String status = ((javax.swing.JLabel) field(panel, "lblStatus")).getText();
        if (status == null || status.isEmpty()) {
            System.out.println("        penolakan tidak disertai keterangan apa pun");
            return false;
        }
        return true;
    }

    /**
     * Truk harus bisa dipindahkan ke pemilik lain dari halaman data master, tanpa kehilangan
     * nomor platnya.
     *
     * <p>Dua kesalahan pernah terjadi di sini, keduanya tanpa suara. Pertama, tombolnya ikut
     * hilang waktu halaman master digabung menjadi satu halaman, sehingga satu-satunya jalan
     * memindahkan truk adalah menghapus lalu mencatatnya ulang - dan penghapusan itu
     * mengosongkan id truk di baris transaksi lama, jadi nomor plat hilang dari laporan lama
     * sementara uangnya tetap tercatat. Kedua, perpindahannya sempat mengambil nomor plat dari
     * kotak isian, bukan dari yang tersimpan: kotak yang sudah dikosongkan membuat platnya
     * tersimpan sebagai teks kosong - kolomnya menolak nilai kosong, tetapi teks kosong bukan
     * nilai kosong - dan nomor truk itu hilang tanpa satu pun peringatan.
     */
    private static boolean pindahPemilikTruk() throws Exception {
        PanelMaster panel = new PanelMaster();
        if (!adaTombol(panel, "Pindah Pemilik")) {
            System.out.println("        tombol 'Pindah Pemilik' tidak ada di halaman data master");
            return false;
        }

        MasterDao dao = new MasterDao();
        java.util.List<Truck> truk = dao.listTrucks();
        if (truk.isEmpty()) {
            System.out.println("        tidak ada truk untuk dipindah");
            return false;
        }
        Truck dipindah = truk.get(0);
        int asal = dipindah.getRentalId();
        Integer tujuan = null;
        for (Rental r : dao.listRental()) {
            if (r.getRentalId() != asal) {
                tujuan = r.getRentalId();
            }
        }
        if (tujuan == null) {
            System.out.println("        tidak ada rental lain sebagai tujuan");
            return false;
        }
        int jumlahAwal = truk.size();
        int truckId = dipindah.getTruckId();

        // Kotak isiannya dikosongkan lebih dulu, persis seperti operator yang menyorot truk
        // lalu menghapus isi kotaknya sebelum menekan tombol pindah.
        //
        // Kegagalannya ditangkap di sini supaya dilaporkan sebagai satu pemeriksaan yang
        // gagal. Kalau dibiarkan naik, seluruh berkas uji ini mati sebelum baris hasil
        // tercetak, sisa pemeriksaan tidak ikut berjalan, dan pembacaannya jadi seperti uji
        // yang belum selesai - bukan uji yang menemukan masalah. Itu kelas kesalahan yang
        // sama dengan yang pernah terjadi di berkas uji basis data.
        try {
            isi(panel, "fPlat", "");
            panggil(panel, "pindahTruk", new Class<?>[]{int.class, int.class},
                    new Object[]{truckId, tujuan});
        } catch (Exception e) {
            Throwable sebab = e.getCause() == null ? e : e.getCause();
            System.out.println("        memindahkan truk gagal: " + sebab);
            return false;
        }

        Truck sesudah = null;
        for (Truck t : dao.listTrucks()) {
            if (t.getTruckId() == truckId) {
                sesudah = t;
            }
        }
        if (sesudah == null) {
            System.out.println("        truk hilang setelah dipindah");
            return false;
        }
        if (!dipindah.getPlate().equals(sesudah.getPlate())) {
            System.out.println("        plat berubah: '" + dipindah.getPlate()
                    + "' -> '" + sesudah.getPlate() + "'");
            return false;
        }
        if (sesudah.getRentalId() == null || sesudah.getRentalId() != tujuan) {
            System.out.println("        pemilik tidak berganti: " + asal + " -> " + sesudah.getRentalId());
            return false;
        }
        if (dao.listTrucks().size() != jumlahAwal) {
            System.out.println("        jumlah truk berubah: " + jumlahAwal + " -> " + dao.listTrucks().size());
            return false;
        }

        // Dikembalikan ke pemilik semula supaya pemeriksaan berikutnya melihat data yang sama.
        try {
            panggil(panel, "pindahTruk", new Class<?>[]{int.class, int.class},
                    new Object[]{truckId, asal});
        } catch (Exception e) {
            Throwable sebab = e.getCause() == null ? e : e.getCause();
            System.out.println("        mengembalikan truk ke pemilik semula gagal: " + sebab);
            return false;
        }
        return true;
    }

    /** Benar kalau ada tombol berteks itu di dalam wadah ini. */
    private static boolean adaTombol(Container c, String teks) {
        for (Component anak : c.getComponents()) {
            if (anak instanceof javax.swing.AbstractButton
                    && teks.equals(((javax.swing.AbstractButton) anak).getText())) {
                return true;
            }
            if (anak instanceof Container && adaTombol((Container) anak, teks)) {
                return true;
            }
        }
        return false;
    }

    /** Panggil satu metode beserta argumennya lewat pantulan. */
    private static void panggil(Object target, String method, Class<?>[] jenis, Object[] nilai)
            throws Exception {
        java.lang.reflect.Method m = target.getClass().getDeclaredMethod(method, jenis);
        m.setAccessible(true);
        m.invoke(target, nilai);
    }

    /**
     * Rental yang tampil di layar transaksi harus sama dengan rental yang tercatat.
     *
     * <p>Tiga keadaan diperiksa, dan ketiganya pernah salah:
     *
     * <ol>
     *   <li>Setiap plat di daftar harus tampil bersama pemiliknya sendiri.</li>
     *   <li>Plat yang belum pernah ada harus mengosongkan pilihan rental, bukan
     *       mewarisi rental baris sebelumnya.</li>
     *   <li>Setelah satu baris ditambahkan, yang tampil harus sama dengan yang tersimpan.</li>
     * </ol>
     *
     * <p>Keadaan (1) diperiksa untuk SELURUH plat, bukan hanya plat yang kebetulan tampil
     * pertama. Cara itu perlu: pemeriksaan yang bergantung pada "plat pertama" pernah
     * ikut lolos hanya karena urutan daftarnya kebetulan sudah sepasang - jadi
     * pemeriksaannya hijau walaupun penyamaannya sengaja dimatikan.
     *
     * <p>Keadaan (3) yang paling penting: di situlah tampilan disamakan dengan data yang
     * benar-benar tersimpan, dan di situlah kesalahan pernah lolos ke catatan uang.
     */
    private static boolean rentalIkutPlatBenar() throws Exception {
        PanelTransaction p = new PanelTransaction();
        JComboBox<?> plat = (JComboBox<?>) field(p, "cmbPlate");
        JComboBox<?> rental = (JComboBox<?>) field(p, "cmbRental");

        if (plat.getItemCount() == 0) {
            System.out.println("        tidak ada plat untuk diuji");
            return false;
        }

        // (1) Setiap plat harus tampil bersama pemiliknya sendiri.
        for (int i = 0; i < plat.getItemCount(); i++) {
            plat.setSelectedIndex(i);
            String nama = String.valueOf(plat.getItemAt(i));
            if (!cocok(nama, rental)) {
                System.out.println("        plat '" + nama + "' ditampilkan bersama rental '"
                        + rental.getSelectedItem() + "', padahal bukan pemiliknya");
                return false;
            }
        }

        // (2) Plat yang belum pernah ada, DIKETIK seperti operator mengetik. Pilihan rental
        // harus ikut kosong dengan sendirinya. Di sini metode penyamaannya sengaja TIDAK
        // dipanggil langsung: kalau dipanggil, pengujian ini hanya membuktikan metode itu
        // bekerja, bukan membuktikan layarnya bekerja. Bedanya penting - cara yang dipanggil
        // langsung itulah yang dulu membuat pemeriksaan ini hijau padahal layarnya salah.
        plat.getEditor().setItem("ZZ 9999 ZZ");
        if (rental.getSelectedItem() != null) {
            System.out.println("        plat baru yang diketik mewarisi rental '"
                    + rental.getSelectedItem() + "', seharusnya kosong");
            return false;
        }

        // (3) Operator memilih rental sendiri untuk plat baru, lalu menambahkan barisnya.
        // Pilihan itu harus dipakai, bukan dihapus oleh penyamaan otomatis.
        Rental dipilih = null;
        for (int i = 0; i < rental.getItemCount(); i++) {
            if (rental.getItemAt(i) != null) {
                dipilih = (Rental) rental.getItemAt(i);
                break;
            }
        }
        if (dipilih == null) {
            System.out.println("        tidak ada rental untuk diuji");
            return false;
        }
        rental.setSelectedItem(dipilih);
        // Combo yang bisa diketik menyelesaikan tulisannya saat fokus berpindah ke tombol
        // Tambah Baris, dan Swing menyampaikan itu sebagai ActionEvent — bukan sebagai
        // perubahan teks. Karena itu kejadiannya ditirukan di sini dengan ActionEvent,
        // bukan dengan menuliskan ulang isi kotaknya: penulisan ulang teks yang sama
        // tidak menghasilkan kejadian apa pun, jadi pemeriksaannya tidak pernah bisa gagal.
        plat.actionPerformed(new java.awt.event.ActionEvent(plat, 0, ""));
        if (rental.getEditor().getItem() == null
                || !String.valueOf(rental.getEditor().getItem()).equals(dipilih.getRentalName())) {
            System.out.println("        pilihan rental operator terhapus oleh penyamaan otomatis, "
                    + "sekarang '" + rental.getEditor().getItem() + "', seharusnya '"
                    + dipilih.getRentalName() + "'");
            return false;
        }
        isi(p, "txtFieldWeight", "7200");
        isi(p, "txtFactoryWeight", "7050");
        isi(p, "txtRefraction", "15");
        isi(p, "txtPrice", "1150");
        if (!tambahBaris(p)) {
            return false;
        }

        Truck trukBaru = null;
        for (Truck t : new MasterDao().listTrucks()) {
            if (t.getPlate().equals("ZZ 9999 ZZ")) {
                trukBaru = t;
            }
        }
        if (trukBaru == null) {
            System.out.println("        truk baru tidak dibuat");
            return false;
        }
        if (trukBaru.getRentalId() == null || trukBaru.getRentalId() != dipilih.getRentalId()) {
            System.out.println("        truk baru tercatat milik rental id=" + trukBaru.getRentalId()
                    + ", seharusnya id=" + dipilih.getRentalId());
            return false;
        }

        // (4) Tambah satu baris untuk plat yang sudah dikenal, sementara pilihan rentalnya
        // sengaja disetel ke rental yang BUKAN pemiliknya. Yang tampil sesudah penambahan
        // harus sama dengan yang tercatat, bukan tetap seperti yang disetel tadi.
        plat.setSelectedIndex(0);
        String namaPlat = String.valueOf(plat.getItemAt(0));
        int idPemilik = pemilikPlat(namaPlat);
        pilihRentalLain(rental, idPemilik);

        isi(p, "txtFieldWeight", "7200");
        isi(p, "txtFactoryWeight", "7050");
        isi(p, "txtRefraction", "15");
        isi(p, "txtPrice", "1150");
        if (!tambahBaris(p)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        java.util.List<kaspe.model.TransactionDetail> detail =
                (java.util.List<kaspe.model.TransactionDetail>) field(p, "detailList");
        if (detail.isEmpty()) {
            System.out.println("        baris tidak bertambah, pengujian tidak sampai tujuan");
            return false;
        }
        String tercatat = detail.get(detail.size() - 1).getRentalName();
        String tampil = rental.getSelectedItem() == null ? null
                : ((Rental) rental.getSelectedItem()).getRentalName();
        if (tercatat == null ? tampil != null : !tercatat.equals(tampil)) {
            System.out.println("        tercatat rental '" + tercatat + "', tetapi layar menampilkan '"
                    + tampil + "'");
            return false;
        }
        return true;
    }

    /** Id rental pemilik sebuah plat, atau null kalau platnya tidak dikenal. */
    private static Integer pemilikPlat(String plat) throws Exception {
        for (Truck t : new MasterDao().listTrucks()) {
            if (t.getPlate().equals(Truck.normalizePlate(plat))) {
                return t.getRentalId();
            }
        }
        return null;
    }

    /** Pilih rental yang bukan {@code kecuali}, supaya tampilan dan data berbeda. */
    private static void pilihRentalLain(JComboBox<?> rental, Integer kecuali) {
        for (int i = 0; i < rental.getItemCount(); i++) {
            Rental r = (Rental) rental.getItemAt(i);
            if (r != null && (kecuali == null || r.getRentalId() != kecuali)) {
                rental.setSelectedIndex(i);
                return;
            }
        }
    }

    /** Isi satu kotak teks lewat pantulan. */
    private static void isi(Object target, String field, String nilai) throws Exception {
        ((javax.swing.text.JTextComponent) field(target, field)).setText(nilai);
    }

    /**
     * Nama rental yang diketik langsung harus dipakai, dan ejaan yang berbeda besar-kecil
     * hurufnya harus tetap dianggap rental yang sama.
     *
     * <p>Dua-duanya penting untuk aplikasi yang menghitung uang per pemilik truk. Kalau
     * nama yang diketik diabaikan, truk baru tercatat tanpa pemilik. Kalau ejaan yang
     * berbeda dianggap rental lain, satu pemilik terpecah menjadi beberapa baris di rekap
     * — dan jumlah uangnya ikut terpecah.
     */
    private static boolean rentalDiketikBenar() throws Exception {
        PanelTransaction p = new PanelTransaction();
        JComboBox<?> plat = (JComboBox<?>) field(p, "cmbPlate");
        JComboBox<?> rental = (JComboBox<?>) field(p, "cmbRental");

        if (!rental.isEditable()) {
            System.out.println("        nama rental tidak bisa diketik");
            return false;
        }

        // (1) Rental yang belum pernah ada, diketik untuk plat yang juga belum pernah ada.
        plat.getEditor().setItem("ZZ 1234 ZZ");
        rental.getEditor().setItem("CV Uji Diketik");
        isi(p, "txtFieldWeight", "7000");
        isi(p, "txtFactoryWeight", "6900");
        isi(p, "txtRefraction", "15");
        isi(p, "txtPrice", "1150");
        if (!tambahBaris(p)) {
            return false;
        }

        MasterDao dao = new MasterDao();
        Truck truk = null;
        for (Truck t : dao.listTrucks()) {
            if (t.getPlate().equals("ZZ 1234 ZZ")) {
                truk = t;
            }
        }
        if (truk == null) {
            System.out.println("        truk baru tidak dibuat");
            return false;
        }
        if (!"CV Uji Diketik".equals(truk.getRentalName())) {
            System.out.println("        rental yang diketik tidak dipakai, tercatat '"
                    + truk.getRentalName() + "'");
            return false;
        }

        // (2) Nama yang sama dengan ejaan huruf berbeda harus memakai rental yang sudah
        // ada, bukan membuat rental kedua.
        int sebelum = dao.listRental().size();
        plat.getEditor().setItem("ZZ 5678 ZZ");
        rental.getEditor().setItem("  cv   uji   diketik ");
        isi(p, "txtFieldWeight", "7000");
        isi(p, "txtFactoryWeight", "6900");
        isi(p, "txtRefraction", "15");
        isi(p, "txtPrice", "1150");
        if (!tambahBaris(p)) {
            return false;
        }

        Truck truk2 = null;
        for (Truck t : dao.listTrucks()) {
            if (t.getPlate().equals("ZZ 5678 ZZ")) {
                truk2 = t;
            }
        }
        if (truk2 == null || !"CV Uji Diketik".equals(truk2.getRentalName())) {
            System.out.println("        ejaan berbeda tidak memakai rental yang sudah ada, tercatat '"
                    + (truk2 == null ? "truk tidak dibuat" : truk2.getRentalName()) + "'");
            return false;
        }
        int sesudah = dao.listRental().size();
        if (sesudah != sebelum) {
            System.out.println("        ejaan berbeda membuat rental kedua: " + sebelum + " -> " + sesudah);
            return false;
        }
        return true;
    }

    /**
     * Tekan Tambah Baris, dan laporkan kegagalannya sebagai hasil uji yang gagal.
     *
     * <p>Kegagalan di dalam penambahan baris - mis. data yang tidak lengkap - memunculkan
     * jendela pesan, dan jendela itu tidak bisa dibuat saat pengujian berjalan tanpa layar.
     * Kalau dibiarkan naik, seluruh kelas uji ini mati di tengah jalan, sisa pemeriksaannya
     * tidak pernah dijalankan, dan tidak ada satu pun baris hasil yang tercetak. Kegagalan
     * yang tidak terlihat sama berbahayanya dengan kegagalan yang lolos.
     */
    private static boolean tambahBaris(PanelTransaction p) {
        try {
            klik(p, "addRow");
            return true;
        } catch (Exception e) {
            Throwable sebab = e.getCause() == null ? e : e.getCause();
            System.out.println("        tambah baris gagal: " + sebab);
            return false;
        }
    }

    /** Benar kalau rental yang tampil memang pemilik plat yang tampil. */
    private static boolean cocok(String plat, JComboBox<?> rental) throws Exception {
        if (plat == null || plat.trim().isEmpty()) {
            return rental.getSelectedItem() == null;
        }
        // Dibandingkan lewat daftar truk di database, bukan lewat teks di layar.
        MasterDao dao = new MasterDao();
        for (Truck t : dao.listTrucks()) {
            if (t.getPlate().equals(Truck.normalizePlate(plat))) {
                Object tampil = rental.getSelectedItem();
                Integer idTampil = tampil instanceof Rental ? ((Rental) tampil).getRentalId() : null;
                return idTampil == null ? t.getRentalId() == null : idTampil.equals(t.getRentalId());
            }
        }
        return rental.getSelectedItem() == null;
    }

    /** Panggil satu metode tanpa argumen lewat pantulan. */
    private static void klik(Object target, String method) throws Exception {
        java.lang.reflect.Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(target);
    }

    /** Ambil satu field lewat pantulan. */
    private static Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /** Ambil tabel di dalam panel lewat pantulan. */
    private static JTable tabel(Object panel) throws Exception {
        return (JTable) field(panel, "table");
    }

    /**
     * Semua baris menu di bilah samping harus punya lebar dan tinggi yang masuk akal.
     *
     * <p>Diperiksa lewat ukuran, bukan lewat gambar: baris menu yang berlebar nol atau
     * negatif tidak menggambar apa pun, tetapi bagian jendela yang lain tetap tergambar
     * sehingga pemeriksaan "gambar tidak kosong" tetap lolos. Yang paling mudah salah
     * adalah lebarnya, karena lebar baris dihitung dari lebar bilahnya.
     */
    private static boolean barisMenuTergambar() {
        PagePanel halaman = new PagePanel();
        JPanel layar = PagePanel.shell(halaman);
        layar.setSize(1320, 760);
        for (int i = 0; i < 3; i++) {
            layar.doLayout();
            layoutDeep(layar);
        }

        int menu = 0;
        for (javax.swing.AbstractButton tombol : semuaTombolMenu(layar)) {
            menu++;
            if (tombol.getWidth() <= 0 || tombol.getHeight() <= 0) {
                System.out.println("        baris menu '" + tombol.getText() + "' berukuran "
                        + tombol.getWidth() + "x" + tombol.getHeight());
                return false;
            }
            // Baris menu harus bisa dipakai tanpa tetikus. Tombol yang tidak bisa disorot
            // berarti halaman hanya bisa dibuka dengan tetikus.
            if (!tombol.isFocusable()) {
                System.out.println("        baris menu '" + tombol.getText() + "' tidak bisa disorot");
                return false;
            }
        }
        if (menu != 4) {
            System.out.println("        jumlah baris menu " + menu + ", seharusnya 4");
            return false;
        }
        return true;
    }

    /** Tombol menu di bilah samping: tombol berikon yang tersusun menurun. */
    private static java.util.List<javax.swing.AbstractButton> semuaTombolMenu(Container c) {
        java.util.List<javax.swing.AbstractButton> hasil = new java.util.ArrayList<>();
        for (Component anak : c.getComponents()) {
            if (anak instanceof javax.swing.AbstractButton && anak.getParent() != null
                    && anak.getParent().getLayout() instanceof BoxLayout
                    && ((javax.swing.AbstractButton) anak).getIcon() != null) {
                hasil.add((javax.swing.AbstractButton) anak);
            }
            if (anak instanceof Container) {
                hasil.addAll(semuaTombolMenu((Container) anak));
            }
        }
        return hasil;
    }

    /** Benar kalau keluarga huruf itu benar-benar tersedia untuk digambar. */
    private static boolean fontTerpasang(String keluarga) {
        for (String nama : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (nama.equals(keluarga)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Huruf bawaan tidak boleh dipakai di Java 8 sebelum update 212 maupun di Java 9,
     * karena di versi itu hurufnya digambar kebesaran. Di versi lain harus dipakai.
     */
    private static boolean aturanVersiHuruf() {
        return !Theme.interUsable("1.8.0_171")
                && Theme.interUsable("1.8.0_212")
                && Theme.interUsable("1.8.0_504")
                && !Theme.interUsable("9.0.4")
                && Theme.interUsable("11.0.2");
    }

    /**
     * Pratinjau cetak harus menghasilkan halaman yang benar-benar berisi baris laporan.
     *
     * <p>Tabelnya disusun dulu seperti di aplikasi, karena pencetakan tabel memakai ukuran
     * tabelnya — tabel yang belum pernah ditata akan tercetak kosong. Terukur: laporan
     * berisi menghasilkan satu halaman dengan ribuan titik, sedangkan tabel tanpa baris
     * tidak menghasilkan halaman sama sekali.
     */
    private static boolean pratinjauAdaIsinya() throws Exception {
        PanelReport p = new PanelReport();
        p.setSize(1080, 640);
        layoutDeep(p);

        java.lang.reflect.Method m = PanelReport.class.getDeclaredMethod("printable");
        m.setAccessible(true);
        java.awt.print.Printable pr = (java.awt.print.Printable) m.invoke(p);

        java.util.List<java.awt.image.BufferedImage> halaman = PrintPreview.renderPages(
                pr, PrintPreview.pageFormat(), 0.5, 60);

        return !halaman.isEmpty() && tinta(halaman.get(0)) > 2000;
    }

    /** Banyaknya titik yang bukan kertas putih. */
    private static int tinta(java.awt.image.BufferedImage img) {
        int jumlah = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
                    jumlah++;
                }
            }
        }
        return jumlah;
    }

    /** Satu pemeriksaan tanpa gambar. */
    private static void check(String judul, boolean lulus) {
        System.out.println("   " + (lulus ? "OK  " : "GAGAL") + " " + judul);
        if (lulus) {
            passed++;
        } else {
            failed++;
        }
    }

    /**
     * Laporan dengan rentang tanggal di luar data yang ada. Dipakai untuk memastikan
     * keadaan "tidak ada data pada rentang ini" tampil dengan keterangan, bukan tabel
     * putih kosong yang terlihat seperti aplikasi gagal memuat.
     */
    private static PanelReport reportTanpaData() throws Exception {
        PanelReport p = new PanelReport();
        setSpinner(p, "spFrom", java.time.LocalDate.of(2020, 1, 1));
        setSpinner(p, "spTo", java.time.LocalDate.of(2020, 1, 31));
        p.reload();
        return p;
    }

    private static void setSpinner(Object target, String field, java.time.LocalDate date) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        JSpinner sp = (JSpinner) f.get(target);
        sp.setValue(java.util.Date.from(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
    }

    private static void render(JPanel panel, String name, Path out) {
        try {
            panel.setSize(1080, 640);
            panel.doLayout();
            layoutDeep(panel);

            BufferedImage img = new BufferedImage(1080, 640, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.CANVAS);
            g2.fillRect(0, 0, 1080, 640);
            panel.paint(g2);
            g2.dispose();

            File f = out.resolve(name).toFile();
            ImageIO.write(img, "png", f);

            // Cek anti-blank: kalau kartu isinya tidak pernah tergambar (mis. tinggi 0),
            // hasilnya nyaris seluruhnya latar. Tangkap sebelum lolos diam-diam.
            int drawn = 0;
            int bg = Theme.CANVAS.getRGB() & 0xFFFFFF;
            for (int y = 0; y < img.getHeight(); y += 2) {
                for (int x = 0; x < img.getWidth(); x += 2) {
                    if ((img.getRGB(x, y) & 0xFFFFFF) != bg) {
                        drawn++;
                    }
                }
            }
            if (drawn < 500) {
                System.out.println("   GAGAL " + name + " -> gambar kosong, hanya " + drawn + " titik tergambar");
                failed++;
                return;
            }

            System.out.println("   OK   " + name + " (" + f.length() + " bytes, " + drawn + " titik)");
            passed++;
        } catch (Throwable t) {
            System.out.println("   GAGAL " + name + " -> " + t);
            failed++;
        }
    }

    /** Paksa layout rekursif supaya tabel & tombol punya ukuran. */
    private static void layoutDeep(Container c) {
        c.doLayout();
        if (c instanceof JScrollPane) {
            JScrollPane sp = (JScrollPane) c;

            // Di mode headless addNotify() tidak jalan, jadi header kolom belum terpasang.
            // Dipasang lebih dulu supaya ScrollPaneLayout ikut memposisikannya.
            Component view0 = sp.getViewport() == null ? null : sp.getViewport().getView();
            if (view0 instanceof JTable && sp.getColumnHeader() == null) {
                JTableHeader header = ((JTable) view0).getTableHeader();
                if (header != null) {
                    sp.setColumnHeaderView(header);
                }
            }

            sp.doLayout();
            JViewport vp = sp.getViewport();
            if (vp != null) {
                vp.setExtentSize(vp.getSize());
                vp.doLayout();
                Component view = vp.getView();
                if (view != null) {
                    view.setSize(vp.getExtentSize());
                    if (view instanceof JTable) {
                        JTable t = (JTable) view;
                        if (t.getTableHeader() != null) {
                            t.getTableHeader().setSize(t.getWidth(), t.getTableHeader().getPreferredSize().height);
                            t.getTableHeader().doLayout();
                        }
                    }
                }
            }
            Component head = sp.getColumnHeader();
            if (head != null) {
                head.setSize(head.getWidth(), head.getPreferredSize().height);
                head.doLayout();
            }
        }
        for (Component child : c.getComponents()) {
            if (child instanceof Container) {
                layoutDeep((Container) child);
            }
        }
    }

    /** Id rental berdasarkan namanya. */
    private static int idRental(MasterDao dao, String nama) throws Exception {
        for (Rental r : dao.listRental()) {
            if (nama.equals(r.getRentalName())) {
                return r.getRentalId();
            }
        }
        throw new IllegalStateException("Rental tidak ditemukan: " + nama);
    }

    private static void fillData() throws Exception {
        MasterDao dao = new MasterDao();
        Rental r = new Rental();
        r.setRentalName("Rental Sinar Jaya");
        dao.saveRental(r);

        Rental r2 = new Rental();
        r2.setRentalName("Rental Bumi Ayu");
        dao.saveRental(r2);

        // Rental dipasang lewat namanya, bukan lewat urutan daftar. Urutan daftarnya
        // menurut abjad, jadi "yang pertama" bukan "yang baru dibuat" - dan kalau dipakai
        // begitu, data ujinya berubah arti hanya karena namanya kebetulan berbeda abjad.
        int idSinarJaya = idRental(dao, "Rental Sinar Jaya");
        int idBumiAyu = idRental(dao, "Rental Bumi Ayu");
        for (String[] t : new String[][]{{"KB 8234 HD", String.valueOf(idSinarJaya)},
                {"BE 8009 CF", String.valueOf(idSinarJaya)},
                {"BE 8570 CF", String.valueOf(idBumiAyu)},
                {"BE 8437 CF", String.valueOf(idBumiAyu)},
                // Truk ini sengaja dipasang supaya urutan plat dan urutan rental tidak
                // sepasang: "AA 0001 ZZ" paling awal di daftar plat, sedangkan pemiliknya
                // ("Rental Sinar Jaya") justru paling akhir di daftar rental. Tanpa truk
                // ini, baris pertama kedua daftar kebetulan sepasang, sehingga halaman yang
                // lupa menyamakan pilihan rentalnya tetap terlihat benar.
                {"AA 0001 ZZ", String.valueOf(idSinarJaya)}}) {
            Truck truck = new Truck();
            truck.setPlate(t[0]);
            truck.setRentalId(Integer.parseInt(t[1]));
            dao.saveTruck(truck);
        }

        // contoh transaksi supaya tabel laporan ada isinya saat dirender.
        // Jumlahnya sengaja banyak: tabel laporan yang isinya sedikit tidak memunculkan
        // batang gulir tegak, sehingga lebarnya beberapa piksel lebih lega daripada di
        // aplikasi sungguhan. Dengan 60 baris, batang gulirnya ikut muncul dan lebar
        // tabelnya sama seperti yang benar-benar dipakai.
        kaspe.dao.TransactionDao transactionDao = new kaspe.dao.TransactionDao();
        java.util.List<Truck> trucks = dao.listTrucks();
        for (int i = 0; i < 20; i++) {
            kaspe.model.Transaction t = new kaspe.model.Transaction();
            t.setDate(java.time.LocalDate.of(2026, 9, 15).plusDays(i));
            java.util.List<kaspe.model.TransactionDetail> det = new java.util.ArrayList<>();
            det.add(detail(trucks, "KB 8234 HD", 7200, 7050, 15, 1150));
            det.add(detail(trucks, "BE 8009 CF", 6000, 5970, 15, 1150));
            det.add(detail(trucks, "BE 8570 CF", 6480, 6380, 15, 1150));
            transactionDao.save(t, det);
        }
    }

    private static kaspe.model.TransactionDetail detail(java.util.List<Truck> truck, String plate,
                                                         long fieldWeight, long factoryWeight, int refraction, long price) {
        Truck t = null;
        for (Truck x : truck) {
            if (x.getPlate().equals(plate)) {
                t = x;
            }
        }
        java.math.BigDecimal netWeight = kaspe.Calculator.netWeight(
                new java.math.BigDecimal(factoryWeight), new java.math.BigDecimal(refraction));
        kaspe.model.TransactionDetail d = new kaspe.model.TransactionDetail();
        d.setTruckId(t.getTruckId());
        d.setFieldWeight(new java.math.BigDecimal(fieldWeight));
        d.setFactoryWeight(new java.math.BigDecimal(factoryWeight));
        d.setRefractionPercent(new java.math.BigDecimal(refraction));
        d.setNetWeight(netWeight);
        d.setPrice(new java.math.BigDecimal(price));
        d.setTotalAmount(kaspe.Calculator.totalAmount(netWeight, new java.math.BigDecimal(price)));
        d.setPaymentDate(java.time.LocalDate.of(2026, 9, 15));
        return d;
    }

    private static void createSchema() throws Exception {
        try (Connection c = Db.get(); Statement st = c.createStatement()) {
            for (String sql : kaspe.Schema.readStatements()) {
                st.execute(sql);
            }
        }
    }
}
