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
        // Judul di bilah atas harus ikut berubah saat halaman dipindah. Pemeriksaan di
        // atas menguji HeaderBar langsung, jadi ia tetap hijau walaupun sambungannya
        // putus: pernah terjadi, showPanel berhenti memanggil setPage dan judulnya macet
        // "Beranda" di semua halaman tanpa satu pun tes gagal.
        check("judul bilah atas ikut pindah halaman", judulIkutPindahHalaman());
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
        // Tabel baris yang sedang diketik harus benar-benar punya tinggi di jendela
        // bawaan. Pernah terjadi sebaliknya tanpa satu pun tanda: tiga bagian halaman
        // bertumpuk melebihi tinggi jendela, BorderLayout mengorbankan bagian tengah,
        // dan tabel itu tinggal bernilai MINUS - tidak tergambar sama sekali. Total uang
        // di bawahnya tetap benar, jadi layarnya terlihat wajar; yang hilang hanya
        // tabelnya, dan barisnya tidak bisa dipilih untuk dihapus. Pemeriksaan lebar
        // kolom dan hitungan piksel tidak menangkapnya, karena keduanya tetap lolos.
        check("tabel baris punya tinggi di jendela bawaan", tabelBarisPunyaTinggi());
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
        // Kaki cetakan harus menuliskan periode yang benar-benar diterapkan ke tabel.
        // Operator bisa mengubah tanggal lalu langsung menekan Cetak/Pratinjau tanpa
        // menekan "Tampilkan" — kertasnya tidak boleh menulis periode baru padahal
        // seluruh baris dan totalnya masih data periode lama.
        check("periode kaki cetak mengikuti tabel", periodeKakiCetakIkutTabel());
        // Tanggal di bilah atas harus ditulis ulang setiap kali halaman dibuka/dipindah,
        // bukan hanya sekali saat aplikasi dijalankan.
        check("tanggal bilah atas segar saat pindah halaman", tanggalHeaderSegarSaatPindah());

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
     * Judul di bilah atas benar-benar berubah saat halaman dipindah.
     *
     * <p>Diperiksa lewat {@code showPanel}, bukan lewat {@code HeaderBar} langsung -
     * justru sambungan itulah yang pernah putus tanpa ada yang menyadari.
     */
    private static boolean judulIkutPindahHalaman() throws Exception {
        PagePanel halaman = new PagePanel();
        PagePanel.shell(halaman);
        HeaderBar bar = (HeaderBar) field(halaman, "header");

        halaman.showPanel(new PanelReport(), "Laporan", "Rekap penjualan per periode.");
        if (!"Laporan".equals(bar.pageName())) {
            System.out.println("        setelah pindah, bilah atas masih menulis \""
                    + bar.pageName() + "\", seharusnya \"Laporan\"");
            return false;
        }
        halaman.showPanel(new PanelMaster(), "Data Master", "Kelola data.");
        if (!"Data Master".equals(bar.pageName())) {
            System.out.println("        setelah pindah kedua, bilah atas menulis \""
                    + bar.pageName() + "\", seharusnya \"Data Master\"");
            return false;
        }
        return true;
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
     * Tabel baris di halaman transaksi harus punya tinggi yang benar-benar terpakai
     * pada ukuran jendela bawaan.
     *
     * <p>Bukan sekadar "tidak nol": tinggi di bawah satu baris berarti tabelnya tidak
     * berguna walaupun ada. Diperiksa pada susunan jendela sungguhan, bukan panel
     * sendirian, karena yang menjepitnya adalah pembagian ruang antar bagian halaman.
     */
    private static boolean tabelBarisPunyaTinggi() throws Exception {
        PagePanel halaman = new PagePanel();
        JPanel layar = PagePanel.shell(halaman);
        halaman.showPanel(new PanelTransaction(), "Transaksi", "Catat pengiriman per truk.");

        layar.setSize(1320, 760);
        for (int i = 0; i < 3; i++) {
            layar.doLayout();
            layoutDeep(layar);
        }

        PanelTransaction p = (PanelTransaction) cariDi(layar, PanelTransaction.class);
        if (p == null) {
            System.out.println("        halaman transaksi tidak tergambar");
            return false;
        }
        // Dua-duanya diperiksa. Daftar riwayat justru yang paling sering terhimpit -
        // ia di bawah - dan itulah yang dipakai mencari lalu menghapus nota yang salah
        // dicatat. Menjaga tabel baris saja membiarkan bentuk regresi yang sama lolos
        // lewat riwayat.
        if (!cukupTinggi((JTable) field(p, "table"), "tabel baris")) {
            return false;
        }
        return cukupTinggi((JTable) field(p, "riwayatTable"), "daftar riwayat");
    }

    /** Benar kalau tabelnya benar-benar dapat tinggi yang terpakai, bukan hanya headernya. */
    private static boolean cukupTinggi(JTable t, String nama) {
        java.awt.Container induk = t.getParent();
        int tinggi = induk instanceof javax.swing.JViewport
                ? ((javax.swing.JViewport) induk).getExtentSize().height
                : (induk == null ? 0 : induk.getHeight());
        if (tinggi < TINGGI_MIN_TABEL_BARIS) {
            System.out.println("        " + nama + " hanya " + tinggi + "px, butuh paling tidak "
                    + TINGGI_MIN_TABEL_BARIS + "px - isinya tidak akan terlihat");
            return false;
        }
        return true;
    }

    /** Tinggi terkecil yang masih memperlihatkan beberapa baris tabel, bukan hanya headernya. */
    private static final int TINGGI_MIN_TABEL_BARIS = 80;

    /** Komponen pertama berjenis tertentu di dalam susunan, atau null. */
    private static java.awt.Component cariDi(java.awt.Container c, Class<?> jenis) {
        for (java.awt.Component k : c.getComponents()) {
            if (jenis.isInstance(k)) {
                return k;
            }
            if (k instanceof java.awt.Container) {
                java.awt.Component hasil = cariDi((java.awt.Container) k, jenis);
                if (hasil != null) {
                    return hasil;
                }
            }
        }
        return null;
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
     * Truk tidak boleh bisa disimpan tanpa pemilik.
     *
     * <p>Halaman data master sekarang menurunkan pemilik truk dari baris rental yang
     * disorot, dan begitu ada rental, selalu ada baris tersorot — jadi keadaan
     * "tanpa pemilik" hanya tercapai kalau daftar rentalnya benar-benar kosong.
     *
     * <p>Dua hal diperiksa di database kosong rental: menambah truk saat belum ada
     * rental ditolak dengan keterangan (truk tidak boleh lahir tanpa pemilik),
     * lalu setelah satu rental ditambahkan dan tersorot, truk yang ditambahkan
     * tercatat milik rental yang sedang tersorot itu.
     *
     * <p>Memakai database terpisah dalam memori supaya data uji lain tidak ikut
     * dibongkar; konfigurasi dikembalikan setelah selesai.
     */
    private static boolean trukButuhPemilik() throws Exception {
        Db.setConfiguration("org.h2.Driver",
                "jdbc:h2:mem:uitest-kosong;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try {
            PanelMaster panel = new PanelMaster();

            // (1) Belum ada rental sama sekali: menambah truk harus ditolak.
            isi(panel, "fPlat", "ZZ 7777 ZZ");
            // Kegagalan di dalam penyimpanan memunculkan jendela pesan, dan jendela itu
            // tidak bisa dibuat saat pengujian berjalan tanpa layar. Kalau dibiarkan naik,
            // seluruh berkas uji ini mati dan hasilnya tidak pernah tercetak.
            try {
                klik(panel, "tambahTruk");
            } catch (Exception e) {
                Throwable sebab = e.getCause() == null ? e : e.getCause();
                System.out.println("        menambah truk tanpa rental tersedia gagal: " + sebab);
                return false;
            }
            MasterDao dao = new MasterDao();
            if (!dao.listTrucks().isEmpty()) {
                System.out.println("        truk tanpa pemilik ikut tersimpan saat rental kosong");
                return false;
            }
            String status = String.valueOf(((javax.swing.JLabel) field(panel, "lblStatus")).getText());
            if (status.isEmpty()) {
                System.out.println("        penolakan tidak disertai keterangan apa pun");
                return false;
            }

            // (2) Satu rental ditambahkan lalu tersorot: truk berikutnya tercatat
            // milik rental yang sedang tersorot, bukan rental lain.
            isi(panel, "fNama", "Rental Uji Tunggal");
            try {
                klik(panel, "tambahRental");
            } catch (Exception e) {
                Throwable sebab = e.getCause() == null ? e : e.getCause();
                System.out.println("        menambah rental gagal: " + sebab);
                return false;
            }
            int idRental = 0;
            for (Rental r : dao.listRental()) {
                if ("Rental Uji Tunggal".equals(r.getRentalName())) {
                    idRental = r.getRentalId();
                }
            }
            if (idRental == 0) {
                System.out.println("        rental baru tidak tersimpan");
                return false;
            }

            isi(panel, "fPlat", "ZZ 8888 ZZ");
            try {
                klik(panel, "tambahTruk");
            } catch (Exception e) {
                Throwable sebab = e.getCause() == null ? e : e.getCause();
                System.out.println("        menambah truk dengan rental tersorot gagal: " + sebab);
                return false;
            }
            Truck truk = null;
            for (Truck t : dao.listTrucks()) {
                if ("ZZ 8888 ZZ".equals(t.getPlate())) {
                    truk = t;
                }
            }
            if (truk == null) {
                System.out.println("        truk dengan pemilik tidak tersimpan");
                return false;
            }
            if (truk.getRentalId() == null || truk.getRentalId() != idRental) {
                System.out.println("        truk tercatat milik rental id=" + truk.getRentalId()
                        + ", seharusnya id=" + idRental + " (yang sedang tersorot)");
                return false;
            }
            return true;
        } finally {
            Db.setConfiguration("org.h2.Driver",
                    "jdbc:h2:mem:uitest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                    "sa", "");
        }
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
     * <p>Empat keadaan diperiksa:
     *
     * <ol>
     *   <li>Setiap plat di daftar harus tampil bersama pemiliknya sendiri.</li>
     *   <li>Plat yang belum pernah ada harus mengosongkan pilihan rental, bukan
     *       mewarisi rental baris sebelumnya.</li>
     *   <li>Rental yang dipilih operator untuk plat baru harus tercatat di baris yang
     *       ditambahkan — dengan nomor truk 0, karena menambah baris tidak boleh
     *       menyentuh database; truknya baru lahir saat transaksinya disimpan.</li>
     *   <li>Plat yang sudah dikenal + rental yang BERBEDA dari pemilik tersimpan harus
     *       DITOLAK dengan pesan, bukan diam-diam memakai pemilik lama.</li>
     * </ol>
     *
     * <p>Keadaan (1) diperiksa untuk SELURUH plat, bukan hanya plat yang kebetulan tampil
     * pertama. Cara itu perlu: pemeriksaan yang bergantung pada "plat pertama" pernah
     * ikut lolos hanya karena urutan daftarnya kebetulan sudah sepasang - jadi
     * pemeriksaannya hijau walaupun penyamaannya sengaja dimatikan.
     *
     * <p>Keadaan (4) yang paling penting: memindahkan pemilik mengubah seluruh laporan
     * lama, jadi kalau layar diam-diam memakai pemilik lama (atau diam-diam menimpanya),
     * uangnya tercatat milik yang salah tanpa satu pun tanda.
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
        // Pilihan itu harus tercatat di barisnya, dan baris itu TIDAK boleh membuat truk
        // di database — menambah baris pernah menaburkan truk hantu setiap kali barisnya
        // dicoba lalu dibuang.
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
        MasterDao dao = new MasterDao();
        int trukSebelum = dao.listTrucks().size();
        isi(p, "txtFieldWeight", "7200");
        isi(p, "txtFactoryWeight", "7050");
        isi(p, "txtRefraction", "15");
        isi(p, "txtPrice", "1150");
        if (!tambahBaris(p)) {
            return false;
        }

        kaspe.model.TransactionDetail detailBaru = barisTerakhir(p);
        if (detailBaru == null) {
            System.out.println("        baris tidak bertambah, pengujian tidak sampai tujuan");
            return false;
        }
        if (detailBaru.getTruckId() == null || detailBaru.getTruckId() != 0) {
            System.out.println("        plat yang belum dikenal harus ditandai truckId 0, tertulis "
                    + detailBaru.getTruckId());
            return false;
        }
        if (!"ZZ 9999 ZZ".equals(detailBaru.getPlate())
                || !dipilih.getRentalName().equals(detailBaru.getRentalName())) {
            System.out.println("        baris tercatat plat '" + detailBaru.getPlate() + "' rental '"
                    + detailBaru.getRentalName() + "', seharusnya 'ZZ 9999 ZZ' / '"
                    + dipilih.getRentalName() + "'");
            return false;
        }
        int trukSesudah = dao.listTrucks().size();
        if (trukSesudah != trukSebelum) {
            System.out.println("        menambah baris ikut membuat truk di database: "
                    + trukSebelum + " -> " + trukSesudah);
            return false;
        }

        // (4) Plat yang sudah dikenal + rental yang sengaja disetel ke rental LAIN:
        // harus DITOLAK dengan pesan yang menunjuk pemilik tersimpan, dan pemilik yang
        // tersimpan tidak boleh berubah diam-diam.
        plat.setSelectedIndex(0);
        String namaPlat = String.valueOf(plat.getItemAt(0));
        Integer idPemilik = pemilikPlat(namaPlat);
        String namaPemilik = null;
        for (Truck t : dao.listTrucks()) {
            if (t.getPlate().equals(Truck.normalizePlate(namaPlat))) {
                namaPemilik = t.getRentalName();
            }
        }
        pilihRentalLain(rental, idPemilik);
        int jumlahBarisSebelum = jumlahBaris(p);

        isi(p, "txtFieldWeight", "7200");
        isi(p, "txtFactoryWeight", "7050");
        isi(p, "txtRefraction", "15");
        isi(p, "txtPrice", "1150");
        if (!tambahBaris(p)) {
            return false;
        }

        if (jumlahBaris(p) != jumlahBarisSebelum) {
            System.out.println("        plat dikenal + rental berbeda tidak ditolak, baris ikut bertambah");
            return false;
        }
        String status = String.valueOf(((javax.swing.JLabel) field(p, "lblStatus")).getText());
        if (!status.contains(String.valueOf(namaPemilik)) || !status.contains("Pindah Pemilik")) {
            System.out.println("        penolakan tidak menunjuk pemilik tersimpan dan jalannya: \""
                    + status + "\"");
            return false;
        }
        if (!idPemilik.equals(pemilikPlat(namaPlat))) {
            System.out.println("        pemilik tersimpan berubah diam-diam oleh penambahan baris");
            return false;
        }

        // (5) Rental yang sama (huruf beda) untuk plat yang dikenal tetap diterima,
        // dan barisnya memakai nomor truk yang sudah tersimpan — bukan 0.
        for (int i = 0; i < rental.getItemCount(); i++) {
            Rental r = (Rental) rental.getItemAt(i);
            if (r != null && idPemilik != null && r.getRentalId() == idPemilik) {
                rental.setSelectedIndex(i);
                rental.getEditor().setItem(r.getRentalName().toLowerCase());
                break;
            }
        }
        if (!tambahBaris(p)) {
            return false;
        }
        kaspe.model.TransactionDetail diterima = barisTerakhir(p);
        if (diterima == null || jumlahBaris(p) != jumlahBarisSebelum + 1) {
            System.out.println("        rental yang sama (huruf beda) malah ditolak");
            return false;
        }
        if (diterima.getTruckId() == null || diterima.getTruckId() == 0) {
            System.out.println("        plat yang dikenal seharusnya memakai nomor truk tersimpan, tertulis "
                    + diterima.getTruckId());
            return false;
        }
        return true;
    }

    /** Baris terakhir yang sudah masuk daftar transaksi, atau null kalau kosong. */
    @SuppressWarnings("unchecked")
    private static kaspe.model.TransactionDetail barisTerakhir(PanelTransaction p) throws Exception {
        java.util.List<kaspe.model.TransactionDetail> detail =
                (java.util.List<kaspe.model.TransactionDetail>) field(p, "detailList");
        return detail.isEmpty() ? null : detail.get(detail.size() - 1);
    }

    /** Banyaknya baris yang sudah masuk daftar transaksi. */
    private static int jumlahBaris(PanelTransaction p) throws Exception {
        return ((javax.swing.table.DefaultTableModel) field(p, "model")).getRowCount();
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
     * Nama rental yang diketik langsung harus tercatat di barisnya, dan truk serta
     * rentalnya baru lahir saat transaksi DISIMPAN — bukan saat barisnya ditambahkan.
     *
     * <p>Tiga keadaan diperiksa:
     *
     * <ol>
     *   <li>Rental yang belum pernah ada, diketik dengan spasi berantakan: baris yang
     *       ditambahkan memuat nama ternormalisasi dan nomor truk 0, dan database
     *       belum tersentuh.</li>
     *   <li>Baris kedua memakai nama yang sama dengan ejaan huruf berbeda: tetap
     *       diterima sebagai baris (nomor truk 0 juga).</li>
     *   <li>Setelah disimpan, kedua truk benar-benar lahir DI SITU, keduanya milik
     *       SATU rental yang sama — ejaan yang berbeda tidak boleh memecahnya menjadi
     *       dua pemilik, karena jumlah uang per pemilik ikut terpecah.</li>
     * </ol>
     *
     * <p>Simpan dipanggil lewat TransactionDao, bukan lewat tombol Simpan Transaksi:
     * tombol itu memunculkan jendela pesan setelah berhasil, dan jendela tidak bisa
     * dibuat saat pengujian berjalan tanpa layar. Jalan data yang ditempuh tetap
     * sama — persis kontrak yang diuji.
     */
    private static boolean rentalDiketikBenar() throws Exception {
        PanelTransaction p = new PanelTransaction();
        JComboBox<?> plat = (JComboBox<?>) field(p, "cmbPlate");
        JComboBox<?> rental = (JComboBox<?>) field(p, "cmbRental");

        if (!rental.isEditable()) {
            System.out.println("        nama rental tidak bisa diketik");
            return false;
        }

        MasterDao dao = new MasterDao();
        int rentalSebelum = dao.listRental().size();
        int trukSebelum = dao.listTrucks().size();

        // (1) Rental yang belum pernah ada, diketik untuk plat yang juga belum pernah ada.
        // Spasinya sengaja berantakan: yang tercatat harus bentuk yang sudah dirapikan.
        plat.getEditor().setItem("zz  1234  zz");
        rental.getEditor().setItem("  CV   Uji Diketik ");
        isi(p, "txtFieldWeight", "7000");
        isi(p, "txtFactoryWeight", "6900");
        isi(p, "txtRefraction", "15");
        isi(p, "txtPrice", "1150");
        if (!tambahBaris(p)) {
            return false;
        }

        kaspe.model.TransactionDetail baris1 = barisTerakhir(p);
        if (baris1 == null) {
            System.out.println("        baris tidak bertambah, pengujian tidak sampai tujuan");
            return false;
        }
        if (baris1.getTruckId() == null || baris1.getTruckId() != 0) {
            System.out.println("        plat yang belum dikenal harus ditandai truckId 0, tertulis "
                    + baris1.getTruckId());
            return false;
        }
        if (!"ZZ 1234 ZZ".equals(baris1.getPlate()) || !"CV Uji Diketik".equals(baris1.getRentalName())) {
            System.out.println("        baris tercatat plat '" + baris1.getPlate() + "' rental '"
                    + baris1.getRentalName() + "', seharusnya 'ZZ 1234 ZZ' / 'CV Uji Diketik'");
            return false;
        }
        if (dao.listRental().size() != rentalSebelum || dao.listTrucks().size() != trukSebelum) {
            System.out.println("        menambah baris ikut menulis database: rental "
                    + rentalSebelum + " -> " + dao.listRental().size() + ", truk "
                    + trukSebelum + " -> " + dao.listTrucks().size());
            return false;
        }

        // (2) Nama yang sama dengan ejaan huruf berbeda: tetap diterima sebagai baris.
        plat.getEditor().setItem("ZZ 5678 ZZ");
        rental.getEditor().setItem("  cv   uji   diketik ");
        isi(p, "txtFieldWeight", "7000");
        isi(p, "txtFactoryWeight", "6900");
        isi(p, "txtRefraction", "15");
        isi(p, "txtPrice", "1150");
        if (!tambahBaris(p)) {
            return false;
        }
        kaspe.model.TransactionDetail baris2 = barisTerakhir(p);
        if (baris2 == null || jumlahBaris(p) != 2) {
            System.out.println("        ejaan berbeda dari nama yang sama malah ditolak");
            return false;
        }
        if (baris2.getTruckId() == null || baris2.getTruckId() != 0) {
            System.out.println("        baris kedua seharusnya juga truckId 0, tertulis "
                    + baris2.getTruckId());
            return false;
        }

        // (3) Simpan: kedua truk lahir di sini, keduanya milik SATU rental.
        kaspe.model.Transaction trx = new kaspe.model.Transaction();
        trx.setDate(java.time.LocalDate.of(2026, 10, 5));
        new kaspe.dao.TransactionDao().save(trx,
                (java.util.List<kaspe.model.TransactionDetail>) field(p, "detailList"));

        int rentalSesudah = dao.listRental().size();
        if (rentalSesudah != rentalSebelum + 1) {
            System.out.println("        simpan membuat " + (rentalSesudah - rentalSebelum)
                    + " rental baru, seharusnya tepat satu");
            return false;
        }
        Rental lahir = null;
        for (Rental r : dao.listRental()) {
            if ("CV Uji Diketik".equals(r.getRentalName())) {
                lahir = r;
            }
        }
        if (lahir == null) {
            System.out.println("        rental yang diketik tidak tersimpan dengan namanya");
            return false;
        }
        for (String platBaru : new String[]{"ZZ 1234 ZZ", "ZZ 5678 ZZ"}) {
            Truck t = null;
            for (Truck x : dao.listTrucks()) {
                if (x.getPlate().equals(platBaru)) {
                    t = x;
                }
            }
            if (t == null) {
                System.out.println("        truk " + platBaru + " tidak lahir saat disimpan");
                return false;
            }
            if (t.getRentalId() == null || t.getRentalId() != lahir.getRentalId()) {
                System.out.println("        truk " + platBaru + " tercatat milik rental id="
                        + t.getRentalId() + ", seharusnya id=" + lahir.getRentalId()
                        + " (ejaan berbeda harus tetap satu rental)");
                return false;
            }
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

    /**
     * Kaki cetakan harus menuliskan periode yang benar-benar diterapkan ke tabel,
     * bukan yang sedang tertulis di kotak tanggal.
     *
     * <p>Operator bisa mengubah tanggal Dari/Sampai lalu langsung menekan Cetak atau
     * Pratinjau tanpa menekan "Tampilkan". Kertas yang diberikan ke pemilik rental
     * untuk mencocokkan uang tidak boleh menuliskan periode baru padahal seluruh
     * baris dan totalnya masih data periode lama.
     */
    private static boolean periodeKakiCetakIkutTabel() throws Exception {
        PanelReport panel = new PanelReport();
        setSpinner(panel, "spFrom", java.time.LocalDate.of(2026, 7, 1));
        setSpinner(panel, "spTo", java.time.LocalDate.of(2026, 9, 30));
        klik(panel, "reload");

        // (a) Setelah reload dengan rentang itu, kaki menuliskan rentang yang sama.
        String setelahReload = kakiCetak(panel);
        boolean sesuaiRentang = setelahReload.startsWith("01/07 – 30/09/2026");
        if (!sesuaiRentang) {
            System.out.println("        kaki cetak tertulis '" + setelahReload
                    + "', seharusnya diawali '01/07 – 30/09/2026'");
        }

        // (b) Kotak tanggal diubah TANPA menekan "Tampilkan": kaki tidak boleh ikut
        // berubah — ia mengikuti tabel, bukan kotak.
        setSpinner(panel, "spFrom", java.time.LocalDate.of(2020, 1, 1));
        setSpinner(panel, "spTo", java.time.LocalDate.of(2020, 1, 31));
        String setelahUbahKotak = kakiCetak(panel);
        boolean tidakIkutKotak = setelahUbahKotak.equals(setelahReload);
        if (!tidakIkutKotak) {
            System.out.println("        kaki cetak ikut berubah menjadi '" + setelahUbahKotak
                    + "' padahal tabel belum dimuat ulang");
        }
        return sesuaiRentang && tidakIkutKotak;
    }

    /** Teks pola kaki cetakan laporan, diambil lewat pantulan. */
    private static String kakiCetak(PanelReport panel) throws Exception {
        java.lang.reflect.Method m = PanelReport.class.getDeclaredMethod("kakiCetak");
        m.setAccessible(true);
        return ((java.text.MessageFormat) m.invoke(panel)).toPattern();
    }

    /**
     * Tanggal di bilah atas harus ditulis ulang setiap kali halaman dibuka atau
     * dipindah. Aplikasi yang dibiarkan terbuka dari sore ke pagi masih menulis
     * tanggal kemarin kalau tanggalnya hanya diisi sekali di konstruktor.
     */
    private static boolean tanggalHeaderSegarSaatPindah() throws Exception {
        PagePanel halaman = new PagePanel();
        HeaderBar bar = (HeaderBar) field(halaman, "header");
        // Tulis tanggal basi, seperti aplikasi yang dibiarkan semalaman, lalu pindah halaman.
        ((JLabel) field(bar, "lblDate")).setText("Senin, 01 Januari 2001");
        halaman.showPanel(new JPanel(), "Laporan", "Rekap penjualan per periode.");
        String seharusnya = kaspe.util.Dates.longFormat(java.time.LocalDate.now());
        if (!seharusnya.equals(bar.dateText())) {
            System.out.println("        tanggal tertulis '" + bar.dateText()
                    + "', seharusnya '" + seharusnya + "'");
            return false;
        }
        return true;
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
