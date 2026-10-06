package kaspe.test;

import kaspe.Db;
import kaspe.dao.MasterDao;
import kaspe.model.Rental;
import kaspe.model.Truck;
import kaspe.ui.DialogDataMaster;
import kaspe.ui.DialogPemilik;
import kaspe.ui.HeaderBar;
import kaspe.ui.NavBar;
import kaspe.ui.PagePanel;
import kaspe.ui.PanelDashboard;
import kaspe.ui.PanelReport;
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
import java.sql.SQLException;
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
        render(new DialogDataMaster(), "3-master.png", out);
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
        // Memilih satu baris di dialog data master harus mengisi mode ubahnya dengan
        // benar: kotak platnya terisi plat baris itu dan pemilik yang tampil memang
        // pemiliknya. Pemeriksaan gambar tidak menangkap ini: barisnya tetap tergambar
        // rapi, dan kesalahannya baru muncul saat barisnya benar-benar diklik pengguna.
        check("memilih baris data master mengisi kotak plat dan pemiliknya", pilihBarisMaster());
        // Truk tidak boleh tersimpan tanpa pemilik. Kotak pemiliknya memang kosong
        // bawaannya; kesalahan ini pernah terjadi tanpa pesan apa pun, dan uangnya
        // masuk ke pemilik yang salah di laporan.
        check("truk tidak bisa disimpan tanpa pemilik", trukButuhPemilik());
        // Truk harus bisa dipindah ke pemilik lain dari dialog data master, dan nomor
        // platnya tidak boleh ikut berubah. Dua kesalahan pernah terjadi di sini, keduanya
        // tanpa suara: tombolnya ikut hilang waktu halaman master digabung, dan
        // perpindahannya sempat mengambil nomor plat dari kotak isian yang bisa dikosongkan.
        check("pindah pemilik truk tidak merusak plat", pindahPemilikTruk());
        // Pemilik baru lahir dari kotak isian dialog, dan pencocokannya lewat kunci
        // ejaan: nama yang sama tetapi beda besar-kecil huruf atau beda spasinya tidak
        // boleh melahirkan pemilik kedua - kalau menjadi dua, rekap uang per pemilik
        // ikut terpecah tanpa satu pun pesan.
        check("ejaan pemilik berbeda tidak melahirkan pemilik kedua", ejaanPemilikTidakMembelah());
        // Dialog pemilik memegang satu-satunya jalan mengganti nama pemilik yang salah
        // ketik. Jalannya harus MENULIS ULANG barisnya: kalau melahirkan pemilik kedua,
        // rekap uang per pemilik terpecah permanen. Pemilik yang sudah dipakai truk juga
        // tidak boleh bisa dihapus - kunci tamunya ON DELETE SET NULL, jadi penghapusannya
        // tidak gagal, tetapi diam-diam melepaskan seluruh truknya dari pemiliknya
        // beserta rekap riwayatnya.
        check("ganti nama pemilik menulis ulang barisnya tanpa menambah pemilik", gantiNamaPemilik());
        // Penghapusan massal diperiksa seluruhnya lebih dulu: selama satu saja baris
        // terpilih masih dipakai catatan pengiriman, tidak ada yang boleh terhapus.
        // Tiap penghapusan meng-commit sendiri, jadi setengah jalan tidak bisa
        // dibatalkan operator.
        check("hapus massal batal seluruhnya kalau satu baris terhalang", hapusMassalBatalSeluruhnya());
        // Pemeriksaan yang sama lewat API yang bisa dipanggil tanpa layar, jadi buktinya
        // bukan lagi catatan dao tiruan: plat yang terhalang disebut satu per satu,
        // penjagaan lapis keduanya benar-benar menolak, dan penghapusan yang bebas
        // benar-benar menghapus.
        check("hapus massal tidak menghapus apa pun saat satu baris terhalang",
                hapusMassalTidakMenghapusApaPun());
        // Menambah truk dengan plat yang sudah terdaftar harus ditolak tanpa
        // meninggalkan pemilik hampa. Jalur lamanya membuat pemiliknya lebih dulu
        // di koneksi sendiri, jadi penolakan plat kembar datang SETELAH pemiliknya
        // ter-commit - pemilik tanpa satu pun truk itu lalu tampil di setiap kotak
        // pilihan pemilik sementara rekap uangnya terpecah.
        check("plat kembar ditolak tanpa meninggalkan pemilik hampa",
                platKembarTidakMeninggalkanPemilikHampa());
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
        // Sepuluh kolom daftar pengiriman tersimpan juga harus utuh — bukan hanya
        // laporan. Kolom id yang disembunyikan di depannya tidak dihitung, karena
        // memang tidak menampilkan apa pun.
        check("kolom daftar pengiriman tersimpan utuh", kolomTabelUtuh(new PanelTransaction()));
        // Daftar pengiriman tersimpan harus benar-benar punya tinggi di jendela bawaan.
        // Inilah daftar yang dipakai operator untuk mencari, lalu mengubah atau
        // menghapus, pengiriman yang salah dicatat. Pernah terjadi sebaliknya tanpa
        // satu pun tanda: kartu form di atasnya terlalu tinggi dan daftarnya terhimpit
        // sampai 66px — hanya kepala tabelnya yang muat. Total uang di bawahnya tetap
        // benar, jadi layarnya terlihat wajar. Pemeriksaan lebar kolom dan hitungan
        // piksel tidak menangkapnya, karena keduanya tetap lolos.
        check("daftar pengiriman tersimpan punya tinggi di jendela bawaan", daftarRiwayatPunyaTinggi());
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
        check("jumlah baris laporan ikut terisi", jumlahBarisLaporanIkutTerisi());
        // Kaki cetakan juga harus menyebut saringan rental/plat yang sedang diterapkan:
        // kertas itulah yang dipakai mencocokkan uang, jadi cetakan yang tersaring tanpa
        // label saringannya bisa disangka daftar lengkap oleh orang yang memegangnya.
        check("kaki cetak menyebut saringan rental/plat", saringanKakiCetakTertera());
        // Tanggal di bilah atas harus ditulis ulang setiap kali halaman dibuka/dipindah,
        // bukan hanya sekali saat aplikasi dijalankan.
        check("tanggal bilah atas segar saat pindah halaman", tanggalHeaderSegarSaatPindah());
        // Tidak boleh ada tombol yang tergambar keluar dari wadahnya. Pernah terjadi:
        // tombol "Cadangkan Database" ikut ditempel di baris tombol rental, dan di lebar
        // jendela bawaan baris itu kelebihan muatan sehingga tombolnya terlipat ke baris
        // kedua lalu terpotong - ada di kode, tidak terlihat di layar, dan tidak satu pun
        // pemeriksaan lain menangkapnya. Pemeriksaan lebar kolom dan hitungan piksel tetap
        // lolos, karena bagian halaman yang lain tergambar wajar.
        check("tombol tidak terpotong di halaman transaksi", tombolTidakTerpotong(new PanelTransaction(), "Transaksi"));
        check("tombol tidak terpotong di dialog data master", tombolDialogTidakTerpotong());
        check("tombol tidak terpotong di laporan", tombolTidakTerpotong(new PanelReport(), "Laporan"));
        // Pemeriksaan yang sama pada lebar jendela TERKECIL yang diizinkan aplikasi. Form
        // transaksi memakai isian berukuran tetap dan kolom kanannya tidak bisa dilipat, jadi
        // menambah lebar apa pun di situ bisa membuat tombol Simpan keluar layar saat jendela
        // dikecilkan - dan itu hanya ketahuan kalau diuji pada lebar minimum, bukan pada
        // lebar bawaan yang lega.
        // Lebar minimum itu berlaku untuk SELURUH halaman, jadi diuji pada semua halaman -
        // bukan hanya halaman yang membuat angkanya ditetapkan. Kalau hanya diuji di situ,
        // masalah "ada tapi tidak terjangkau" cuma pindah ke halaman lain.
        check("tombol tidak terpotong pada lebar jendela minimum (transaksi)",
                tombolTidakTerpotong(new PanelTransaction(), "Transaksi",
                        kaspe.ui.MainFrame.LEBAR_MINIMUM));
        check("tombol tidak terpotong pada lebar jendela minimum (laporan)",
                tombolTidakTerpotong(new PanelReport(), "Laporan",
                        kaspe.ui.MainFrame.LEBAR_MINIMUM));
        // Halaman laporan punya tabel berkolom lebar tetap, dan tabelnya TIDAK punya
        // penggeser mendatar: saat ruangnya kurang, kolomnya diperas dan isinya terpotong
        // - termasuk di kertas, karena pencetakan memperkecil tabel apa adanya. Karena itu
        // halaman inilah yang menentukan lebar minimum, dan diukur pada lebar itu.
        check("kolom laporan utuh pada lebar jendela minimum",
                kolomTabelUtuh(new PanelReport(), kaspe.ui.MainFrame.LEBAR_MINIMUM));
        check("kolom daftar tersimpan utuh pada lebar jendela minimum",
                kolomTabelUtuh(new PanelTransaction(), kaspe.ui.MainFrame.LEBAR_MINIMUM));
        // Tinggi minimum: yang diperiksa BUKAN "daftarnya cukup tinggi", karena daftarnya
        // ternyata tidak pernah terhimpit - di bawah sekitar 620px halaman luarnya yang
        // menggulir, dan tingginya berhenti di 175px. Pemeriksaan yang berbunyi "daftarnya
        // paling sedikit 80px" karena itu tidak pernah bisa gagal, dan pemeriksaan yang
        // tidak bisa gagal lebih buruk daripada tidak ada: ia terlihat seperti jaminan.
        //
        // Yang benar-benar diperiksa: pada tinggi minimum, seluruh halaman masih muat
        // tanpa perlu digulir. Kalau tidak, tombol dan baris paling bawah hanya bisa
        // dicapai dengan menggulir halaman - dan itu tidak terlihat di gambar pratinjau,
        // yang selalu digambar pada tinggi bawaan.
        check("halaman transaksi muat tanpa digulir pada ukuran jendela minimum",
                muatTanpaGulir(new PanelTransaction(), "Transaksi",
                        kaspe.ui.MainFrame.LEBAR_MINIMUM, kaspe.ui.MainFrame.TINGGI_MINIMUM));
        // Aturan perataan: PERATAAN JUDUL MENGIKUTI PERATAAN ISINYA. Kolom teks rata kiri,
        // kolom angka rata kanan, dan judulnya selalu sepasang dengan isinya.
        //
        // Dua keadaan yang pernah terjadi dan sama-sama tidak menimbulkan pesan apa pun:
        // judul kolom angka diratakan kiri sementara angkanya rata kanan (judul dan angkanya
        // tidak berbagi tepi, terlihat lepas), dan seluruh kolom diratakan kiri termasuk
        // angkanya (angka dengan panjang berbeda jadi bergerigi di kanan, sehingga beda
        // besar-kecil uang tidak lagi melompat). Keduanya dikunci di sini.
        check("perataan judul kolom mengikuti isinya", perataanJudulIkutIsi(new PanelTransaction(), "Transaksi"));
        check("perataan judul kolom laporan mengikuti isinya", perataanJudulIkutIsi(new PanelReport(), "Laporan"));
        check("perataan judul kolom dialog data master mengikuti isinya", perataanJudulIkutIsi(new DialogDataMaster(), "Data Master"));
        // Tombol Simpan dipasang sejajar dengan TULISAN di kotak hasil di atasnya, dan
        // kedua angka di kotak itu memakai huruf yang sama. Keduanya mudah melenceng tanpa
        // pesan apa pun: jarak tepinya dulu ditulis di dua tempat sehingga tombolnya
        // menggantung keluar dari kotaknya, dan jumlah uang dulu lebih besar daripada berat
        // bersih sehingga satu angka terlihat lebih penting padahal keduanya sederajat.
        check("tombol Simpan sejajar dengan angka hasil di atasnya", simpanSejajarAngkaHasil());
        // Berkas CSV dipakai mengolah angkanya di Excel. Dua kesalahan yang sama-sama
        // tidak berbunyi: angka yang ikut membawa satuannya ("6.350 kg") tidak bisa
        // dijumlahkan sehingga berkasnya tidak berguna, dan jumlah yang berbeda dari
        // total di layar menyesatkan orang yang sedang mencocokkan uang.
        check("berkas CSV siap dijumlahkan di Excel", csvSiapDiolah());
        // Isian yang memuat pemisah atau tanda petik harus dibungkus, kalau tidak satu
        // nama rental bisa memecah barisnya jadi dua dan angka di sebelahnya bergeser.
        check("isian CSV yang memuat pemisah dibungkus", isianCsvDibungkus());
        // Kolom yang bisa diurut harus memperlihatkan kolom mana yang sedang diurut -
        // panahnya tidak lagi diwarisi begitu judul kolomnya digambar sendiri, dan yang
        // hilang bukan cuma gambarnya: urutannya terlihat sama saja dengan tidak diurut.
        check("panah penanda urut tergambar di kolom laporan", panahUrutTergambar(new PanelReport(), 9));
        check("panah penanda urut tergambar di kolom daftar tersimpan",
                panahUrutTergambar(new PanelTransaction(), 10));
        // Diuji pada KEDUA tabel: keduanya mengurut kolom uangnya sendiri-sendiri, jadi
        // memasang pembanding di yang satu tidak memperbaiki yang lain - dan pemeriksaan
        // yang hanya melihat laporan tidak akan menangkap daftar tersimpan yang masih
        // mengurut "Rp 10.000.000" sebelum "Rp 6.888.500". Kolom tanggalnya ikut
        // diperiksa dengan alasan yang sama: urutan tanggal yang salah tetap terlihat
        // wajar karena tanggalnya terbaca benar satu per satu, jadi tidak ada tanda
        // apa pun bahwa kolomnya mengurut menurut tulisannya.
        check("kolom uang laporan terurut menurut nilainya",
                urutAngkaMenurutNilai(new PanelReport(), 9, 7));
        check("kolom uang daftar tersimpan terurut menurut nilainya",
                urutAngkaMenurutNilai(new PanelTransaction(), 10, 1));
        check("mengurutkan tabel tidak menukar isi barisnya", urutTidakMenukarBaris());
        // Baris "belum lunas" tidak memuat tanggal pembayaran. Pengurut yang
        // membandingkan tanggal mentah-mentah mati begitu bertemu sel kosong - dan
        // data uji yang semua barisnya lunas membuat cacat itu tidak pernah
        // kelihatan. Satu baris belum lunas sengaja ditanam di fillData().
        check("pengurutan kolom Tgl Lunas tahan baris belum lunas", urutTglLunasTahanBelumLunas());
        // Panah penanda urut menambah sekitar 10px di judul kolom yang sedang diurut.
        // Judul yang lebarnya pas-pasan tanpa panah terpotong begitu panahnya muncul,
        // dan tabel laporan tidak punya penggeser mendatar sehingga yang terpotong
        // memang tidak terlihat. Semua kolom diukur bersama panahnya, karena kolom
        // mana pun bisa menjadi kolom yang diurut.
        check("judul kolom tidak terpotong saat panah urut tampil", judulMuatBersamaPanahUrut());
        // Rekap per rental dihitung dari baris yang SEDANG TAMPIL. Baris "Jumlah"
        // yang tidak sepadan dengan total di layar berarti rekap dan layar saling
        // bertentangan - dan dua-duanya dipakai orang yang mencocokkan uang yang sama.
        check("rekap per rental berjumlah sama dengan total di layar", rekapSamaDenganTotalLayar());
        // Rekap yang menghitung seluruh data (bukan hasil saringan) tetap terlihat
        // benar: angkanya memang jumlah yang sah, hanya bukan jumlah yang sedang
        // disaring. Uang yang disetorkan dari rekap macam itu pasti salah tanpa tanda.
        check("rekap per rental hanya memuat rental yang disaring", rekapMenghormatiSaringan());
        // Tombol "Bulan ini" mempersempit rentang dengan satu klik. Kesalahan yang
        // senyap: kotak tanggalnya berubah tetapi tabelnya tidak dimuat ulang, jadi
        // baris dan totalnya masih periode lama padahal kotaknya menulis bulan ini.
        check("tombol 'Bulan ini' memasang rentang bulan berjalan", rentangCepatBulanIni());
        // Dua tombol rentang cepat lainnya. Ketiganya memasang periode yang dibaca orang
        // untuk mencocokkan uang, dan salah rentang di situ tidak berbunyi: kotaknya tetap
        // berisi tanggal yang masuk akal, hanya bukan rentang yang dimaksud. Menguji satu
        // tombol saja membuat dua jalur lainnya bebas melenceng tanpa ada yang tahu.
        check("tombol 'Hari ini' memasang rentang hari ini", rentangCepatHariIni());
        check("tombol 'Semua' memasang rentang seluruh data", rentangCepatSemua());
        // Pembanding teks yang mengikuti aturan abjad komputer mengurut nama yang
        // sama dengan hasil berbeda di komputer yang berbeda, sehingga laporan dari
        // dua komputer tidak bisa dicocokkan. Diurutkan menaik lalu diperiksa
        // menurut perbandingan yang hasilnya sama di mana-mana.
        check("urutan nama rental tidak bergantung bahasa komputer", urutTeksTegar());
        // Berkas CSV adalah dokumen yang sama dengan kertas, dan namanya memakai tanggal
        // ekspor - bukan periode laporannya. Karena itu periode, saringan, dan urutannya
        // harus tertulis di dalam berkasnya, sama seperti kaki cetak menuliskannya.
        check("berkas CSV menyebut cakupan dan urutannya", csvMenyebutCakupannya());
        // Urutan layar ikut ke kertas, jadi kertas yang dipakai mencocokkan uang harus
        // menyebut urutannya - bagian dari keterangan yang sama dengan periode dan saringan.
        check("kaki cetak menyebut urutan yang sedang berlaku", kakiCetakMenyebutUrutan());
        // Memasang pembanding sebelum tabelnya punya pengurut pernah membuat pembandingnya
        // hilang tanpa suara. Diperiksa pada tabel kosong, bukan lewat halaman, karena
        // halaman yang urutan pemanggilannya sudah benar tetap lulus tanpa penjagaan ini.
        check("memasang pembanding menyalakan pengurutnya sendiri", sortAngkaMenyalakanPengurut());

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
        // Data master bukan halaman lagi - kliknya di bilah samping membuka dialog -
        // jadi pindahan kedua diuji ke halaman Transaksi. Sambungan showPanel ke bilah
        // atas tetap jalur yang sama apa pun halamannya.
        halaman.showPanel(new PanelTransaction(), "Transaksi", "Catat pengiriman per truk.");
        if (!"Transaksi".equals(bar.pageName())) {
            System.out.println("        setelah pindah kedua, bilah atas menulis \""
                    + bar.pageName() + "\", seharusnya \"Transaksi\"");
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
     * Daftar pengiriman tersimpan di halaman transaksi harus punya tinggi yang
     * benar-benar terpakai pada ukuran jendela bawaan.
     *
     * <p>Daftar itulah satu-satunya jalan operator untuk mencari, lalu mengubah atau
     * menghapus, pengiriman yang salah dicatat — kalau daftarnya terhimpit, catatan
     * yang salah tidak bisa diperbaiki. Pernah terjadi: kartu form di atasnya
     * terlalu tinggi dan daftarnya terhimpit sampai 66px, cukup untuk kepala
     * tabelnya saja, tanpa satu pun tanda — total uang di bawahnya tetap benar,
     * jadi layarnya terlihat wajar.
     *
     * <p>Bukan sekadar "tidak nol": tinggi di bawah satu baris berarti daftarnya tidak
     * berguna walaupun ada. Diperiksa pada susunan jendela sungguhan, bukan panel
     * sendirian, karena yang menjepitnya adalah pembagian ruang antar bagian halaman.
     */
    private static boolean daftarRiwayatPunyaTinggi() throws Exception {
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
        return cukupTinggi((JTable) field(p, "riwayatTable"), "daftar pengiriman tersimpan");
    }

    /**
     * Benar kalau setiap judul kolom sejajar dengan isi kolomnya.
     *
     * <p>Penggambar per kolom bisa kosong, dan yang dipakai tabel adalah penggambar
     * bawaannya. Karena itu yang dibaca di sini adalah penggambar yang benar-benar dipakai
     * JTable saat menggambar - bukan hanya yang dipasang per kolom.
     */
    private static boolean perataanJudulIkutIsi(JPanel panel, String nama) {
        java.util.List<String> salah = new java.util.ArrayList<String>();
        for (JTable t : semuaTabel(panel)) {
            for (int c = 0; c < t.getColumnCount(); c++) {
                javax.swing.table.TableColumn col = t.getColumnModel().getColumn(c);
                if (col.getMaxWidth() == 0) {
                    continue;
                }
                javax.swing.table.TableCellRenderer hr = col.getHeaderRenderer() != null
                        ? col.getHeaderRenderer() : t.getTableHeader().getDefaultRenderer();
                javax.swing.table.TableCellRenderer cr = col.getCellRenderer() != null
                        ? col.getCellRenderer() : t.getDefaultRenderer(Object.class);
                int rataJudul = hr instanceof JLabel
                        ? ((JLabel) hr).getHorizontalAlignment() : SwingConstants.LEFT;
                int rataIsi = cr instanceof JLabel
                        ? ((JLabel) cr).getHorizontalAlignment() : SwingConstants.LEFT;
                if (rataJudul != rataIsi) {
                    salah.add("kolom \"" + col.getHeaderValue() + "\": judul "
                            + sebut(rataJudul) + " tetapi isinya " + sebut(rataIsi));
                }
            }
        }
        for (String p : salah) {
            System.out.println("        " + nama + ": " + p);
        }
        return salah.isEmpty();
    }

    private static String sebut(int rata) {
        if (rata == SwingConstants.RIGHT) {
            return "rata kanan";
        }
        if (rata == SwingConstants.LEFT) {
            return "rata kiri";
        }
        if (rata == SwingConstants.CENTER) {
            return "rata tengah";
        }
        return "perataan lain (" + rata + ")";
    }

    /**
     * Benar kalau tombol Simpan mulai dari tepi kiri yang sama dengan tulisan di kotak hasil
     * di atasnya, dan kedua angka di kotak itu berhuruf sama.
     */
    private static boolean simpanSejajarAngkaHasil() throws Exception {
        PagePanel halaman = new PagePanel();
        JPanel layar = PagePanel.shell(halaman);
        PanelTransaction p = new PanelTransaction();
        halaman.showPanel(p, "Transaksi", "Catat pengiriman per truk.");
        layar.setSize(1320, 760);
        for (int i = 0; i < 3; i++) {
            layar.doLayout();
            layoutDeep(layar);
        }

        JButton simpan = (JButton) field(p, "btnSimpan");
        JLabel netto = (JLabel) field(p, "lblNetWeight");
        JLabel uang = (JLabel) field(p, "lblTotalAmount");

        boolean ok = true;
        int kiriSimpan = kiriAbsolut(simpan);
        int kiriNetto = kiriAbsolut(netto);
        if (kiriSimpan != kiriNetto) {
            System.out.println("        tombol Simpan mulai di x=" + kiriSimpan
                    + ", tetapi angka hasil di atasnya mulai di x=" + kiriNetto
                    + " - tombolnya menggantung keluar dari kotaknya");
            ok = false;
        }
        if (!netto.getFont().equals(uang.getFont())) {
            System.out.println("        berat bersih berhuruf " + netto.getFont().getSize()
                    + " " + netto.getFont().getStyle() + ", jumlah uang "
                    + uang.getFont().getSize() + " " + uang.getFont().getStyle()
                    + " - dua angka sederajat di satu kotak harus sama");
            ok = false;
        }
        return ok;
    }

    /** Jarak tepi kiri sebuah komponen dari akar susunannya. */
    private static int kiriAbsolut(Component c) {
        int x = 0;
        for (Component k = c; k != null; k = k.getParent()) {
            x += k.getX();
        }
        return x;
    }

    /** Semua tabel di dalam wadah ini, sedalam apa pun. */
    private static java.util.List<JTable> semuaTabel(java.awt.Container c) {
        java.util.List<JTable> hasil = new java.util.ArrayList<JTable>();
        for (Component anak : c.getComponents()) {
            if (anak instanceof JTable) {
                hasil.add((JTable) anak);
            }
            if (anak instanceof java.awt.Container) {
                hasil.addAll(semuaTabel((java.awt.Container) anak));
            }
        }
        return hasil;
    }

    /**
     * Benar kalau seluruh isi halaman muat pada ukuran jendela ini tanpa perlu digulir.
     *
     * <p>Halaman transaksi menaruh form dan daftarnya di dalam satu gulungan. Kalau tingginya
     * kurang, gulungan itu yang bekerja - isinya tidak terpotong, tetapi bagian bawah halaman
     * hanya bisa dicapai dengan menggulir, dan tombol yang paling bawah jadi tidak terlihat
     * begitu halaman dibuka.
     */
    private static boolean muatTanpaGulir(JPanel panel, String nama, int lebar, int tinggi) {
        PagePanel halaman = new PagePanel();
        JPanel layar = PagePanel.shell(halaman);
        halaman.showPanel(panel, nama, "keterangan");
        layar.setSize(lebar, tinggi);
        for (int i = 0; i < 3; i++) {
            layar.doLayout();
            layoutDeep(layar);
        }

        java.awt.Component gulung = cariDi(layar, javax.swing.JScrollPane.class);
        if (!(gulung instanceof javax.swing.JScrollPane)) {
            System.out.println("        tidak menemukan gulungan halaman di " + nama);
            return false;
        }
        javax.swing.JScrollPane sp = (javax.swing.JScrollPane) gulung;
        if (sp.getVerticalScrollBar().isVisible()) {
            System.out.println("        pada " + lebar + "x" + tinggi + " halaman " + nama
                    + " perlu digulir: isinya " + sp.getViewport().getView().getPreferredSize().height
                    + "px, ruangnya " + sp.getViewport().getExtentSize().height + "px");
            return false;
        }
        return true;
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
        return kolomTabelUtuh(panel, 1320);
    }

    private static boolean kolomTabelUtuh(JPanel panel, int lebar) throws Exception {
        PagePanel halaman = new PagePanel();
        JPanel layar = PagePanel.shell(halaman);
        halaman.showPanel(panel, "Laporan", "Rekap penjualan per periode.");

        // Ukuran jendela bawaan, dikurangi tinggi bilah judul jendela sistem.
        layar.setSize(lebar, 760);
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
            // Kolom yang disembunyikan (id catatan, lebarnya 0) memang tidak menampilkan
            // apa pun, jadi syarat "muat utuh" tidak berlaku untuknya — sepuluh kolom
            // yang benar-benar tampil tetap diperiksa semuanya.
            if (col.getMaxWidth() == 0) {
                continue;
            }
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
            //
            // Peta ini berkunci JUDUL kolom. Jadi mengganti judul - seperti "Refraksi (%)"
            // menjadi "Refraksi" - membuat kuncinya tidak ketemu, dan kolom itu diam-diam
            // berhenti diuji terhadap angka terburuknya: pemeriksaannya tetap hijau karena
            // yang diukur tinggal data contoh. Karena itu kuncinya diperiksa lebih dulu.
            if (!TERBURUK.containsKey(nama)) {
                System.out.println("        kolom \"" + nama + "\" tidak ada di peta angka"
                        + " terburuk, jadi tidak diuji terhadap angka terburuknya");
                utuh = false;
                continue;
            }
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
        // Angka terburuk ditulis PERSIS seperti yang muncul di sel, termasuk satuannya:
        // yang diukur adalah teks yang benar-benar tampil, jadi satuan yang tertinggal
        // dari daftar ini akan membuat pemeriksaan mengukur teks yang lebih pendek
        // daripada kenyataannya - dan pemotongan yang sungguhan lolos.
        TERBURUK.put("Bobot Lapak", "00.000 kg");
        TERBURUK.put("Bobot Pabrik", "00.000 kg");
        TERBURUK.put("Refraksi", "000%");
        TERBURUK.put("Berat Bersih", "00.000 kg");
        TERBURUK.put("Tgl Lunas", "00-00-0000");
        TERBURUK.put("Harga", "Rp 00.000");
        TERBURUK.put("Jumlah Uang", "Rp 00.000.000");
    }

    /**
     * Memilih satu baris di dialog data master harus mengisi mode ubahnya dengan benar.
     *
     * <p>Setelah "Ubah" ditekan, kotak platnya harus berisi plat baris yang dipilih, dan
     * pemilik yang ditampilkan harus pemilik yang benar-benar tersimpan untuk truk itu.
     * Yang diperiksa bukan cuma "tidak gagal": pengisian yang salah kolom atau salah
     * baris tetap berjalan tanpa kesalahan, dan pengguna baru melihatnya waktu truk
     * yang salah ikut terubah. Pemeriksaan gambar tidak menangkap ini - barisnya
     * tetap tergambar rapi dan kesalahannya baru muncul saat barisnya benar-benar
     * diklik.
     *
     * <p>Barisnya dicari lewat isi tabelnya, bukan nomornya, supaya urutan daftar
     * tidak mengubah arti pemeriksaan ini.
     */
    private static boolean pilihBarisMaster() throws Exception {
        DialogDataMaster panel = new DialogDataMaster();
        JTable truk = (JTable) field(panel, "tableTruk");

        int baris = -1;
        for (int i = 0; i < truk.getRowCount(); i++) {
            if ("KB 8234 HD".equals(String.valueOf(truk.getValueAt(i, 0)))) {
                baris = i;
            }
        }
        if (baris < 0) {
            System.out.println("        truk contoh 'KB 8234 HD' tidak ada di daftar");
            return false;
        }
        String pemilikTersimpan = null;
        for (Truck t : new MasterDao().listTrucks()) {
            if ("KB 8234 HD".equals(t.getPlate())) {
                pemilikTersimpan = t.getRentalName();
            }
        }
        if (pemilikTersimpan == null) {
            System.out.println("        pemilik 'KB 8234 HD' tidak terbaca dari database");
            return false;
        }

        // Kegagalan ditangkap di sini supaya dilaporkan sebagai satu pemeriksaan yang
        // gagal, bukan sebagai kesalahan yang menghentikan seluruh berkas uji. Kalau
        // dibiarkan naik, uji setelahnya tidak ikut berjalan dan hasilnya terlihat seperti
        // uji yang belum selesai, bukan uji yang menemukan masalah.
        try {
            truk.setRowSelectionInterval(baris, baris);
            klik(panel, "masukUbah");
        } catch (Exception e) {
            Throwable sebab = e.getCause() == null ? e : e.getCause();
            System.out.println("        masuk mode ubah gagal - " + sebab);
            return false;
        }
        String platKotak = ((javax.swing.text.JTextComponent) field(panel, "fPlat")).getText();
        if (!"KB 8234 HD".equals(platKotak)) {
            System.out.println("        kotak plat berisi '" + platKotak + "', seharusnya 'KB 8234 HD'");
            return false;
        }
        String pemilikTampil = ((javax.swing.JLabel) field(panel, "lblPemilik")).getText();
        if (!pemilikTersimpan.equals(pemilikTampil)) {
            System.out.println("        pemilik yang tampil '" + pemilikTampil
                    + "', seharusnya '" + pemilikTersimpan + "'");
            return false;
        }
        return true;
    }

    /**
     * Truk tidak boleh bisa disimpan tanpa pemilik.
     *
     * <p>Kotak pemilik di dialog sengaja KOSONG saat dibuka, dan tombol tambah menolak
     * selama kotaknya belum diisi. Kotak pemilik yang terisi sendiri begitu layar
     * dibuka pernah membuat truk baru tercatat milik pemilik yang kebetulan tampil
     * pertama, tanpa pesan apa pun - dan uangnya lalu masuk ke pemilik yang salah di
     * laporan.
     *
     * <p>Tiga hal diperiksa di database terpisah: menambah tanpa pemilik ditolak dengan
     * keterangan, truk yang ditambahkan setelah pemiliknya diketik tercatat milik
     * pemilik itu, dan menambah tetap INSERT walau ada baris yang kebetulan tersorot.
     *
     * <p>Memakai database terpisah dalam memori supaya data uji lain tidak ikut
     * dibongkar; konfigurasi dikembalikan setelah selesai.
     */
    private static boolean trukButuhPemilik() throws Exception {
        Db.setConfiguration("org.h2.Driver",
                "jdbc:h2:mem:uitest-kosong;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try {
            DialogDataMaster panel = new DialogDataMaster();
            JTable tabel = (JTable) field(panel, "tableTruk");
            MasterDao dao = new MasterDao();

            // (1) Kotak pemilik masih kosong bawaannya: menambah truk harus ditolak.
            isi(panel, "fPlat", "ZZ 7777 ZZ");
            // Kegagalan di dalam penyimpanan memunculkan jendela pesan, dan jendela itu
            // tidak bisa dibuat saat pengujian berjalan tanpa layar. Kalau dibiarkan naik,
            // seluruh berkas uji ini mati dan hasilnya tidak pernah tercetak.
            try {
                klik(panel, "tambahTruk");
            } catch (Exception e) {
                Throwable sebab = e.getCause() == null ? e : e.getCause();
                System.out.println("        menambah truk tanpa pemilik gagal: " + sebab);
                return false;
            }
            if (tabel.getRowCount() != 0) {
                System.out.println("        truk tanpa pemilik ikut tersimpan");
                return false;
            }
            String status = String.valueOf(((javax.swing.JLabel) field(panel, "lblStatus")).getText());
            if (status.isEmpty()) {
                System.out.println("        penolakan tidak disertai keterangan apa pun");
                return false;
            }

            // (2) Pemilik diketik di kotaknya: truk berikutnya tercatat milik pemilik itu,
            // bukan pemilik lain.
            isiPemilik(panel, "Rental Uji Tunggal");
            isi(panel, "fPlat", "ZZ 8888 ZZ");
            try {
                klik(panel, "tambahTruk");
            } catch (Exception e) {
                Throwable sebab = e.getCause() == null ? e : e.getCause();
                System.out.println("        menambah truk dengan pemilik gagal: " + sebab);
                return false;
            }
            if (tabel.getRowCount() != 1) {
                System.out.println("        truk dengan pemilik tidak masuk daftar");
                return false;
            }
            Rental pemilik = null;
            for (Rental r : dao.listRental()) {
                if ("Rental Uji Tunggal".equals(r.getRentalName())) {
                    pemilik = r;
                }
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
            if (pemilik == null || truk.getRentalId() == null || truk.getRentalId() != pemilik.getRentalId()) {
                System.out.println("        truk tercatat milik rental id=" + truk.getRentalId()
                        + ", seharusnya milik pemilik yang diketik (id=" + pemilik + ")");
                return false;
            }

            // (3) Ada baris yang tersorot: menambah harus tetap INSERT, bukan menimpanya.
            tabel.setRowSelectionInterval(0, 0);
            isiPemilik(panel, "Rental Uji Tunggal");
            isi(panel, "fPlat", "ZZ 9999 YY");
            try {
                klik(panel, "tambahTruk");
            } catch (Exception e) {
                Throwable sebab = e.getCause() == null ? e : e.getCause();
                System.out.println("        menambah truk saat ada baris tersorot gagal: " + sebab);
                return false;
            }
            if (tabel.getRowCount() != 2) {
                System.out.println("        menambah menimpa baris yang tersorot: jumlah baris "
                        + tabel.getRowCount() + ", seharusnya 2");
                return false;
            }
            boolean trukLamaUtuh = false;
            for (Truck t : dao.listTrucks()) {
                if ("ZZ 8888 ZZ".equals(t.getPlate())
                        && t.getRentalId() != null && t.getRentalId() == pemilik.getRentalId()) {
                    trukLamaUtuh = true;
                }
            }
            if (!trukLamaUtuh) {
                System.out.println("        truk lama berubah atau hilang saat truk lain ditambah");
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
     * Truk harus bisa dipindahkan ke pemilik lain dari dialog data master, tanpa kehilangan
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
        DialogDataMaster panel = new DialogDataMaster();
        if (!adaTombol(panel, "Pindah Pemilik")) {
            System.out.println("        tombol 'Pindah Pemilik' tidak ada di dialog data master");
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

    /**
     * Ejaan nama pemilik yang berbeda tidak boleh melahirkan pemilik kedua.
     *
     * <p>Pemilik baru lahir dari kotak isian dialog, dan pencocokannya lewat kunci
     * {@link Rental#matchKey}, bukan perbandingan tulisan apa adanya: nama yang sama
     * tetapi beda besar-kecil huruf atau beda spasinya harus menunjuk pemilik yang
     * sudah ada. Kalau tidak, rekap uang per pemilik terpecah diam-diam - satu
     * perusahaan terbaca dua, dan tidak ada satu pun pesan yang menyebutnya.
     *
     * <p>Memakai database terpisah dalam memori supaya data uji lain tidak ikut
     * dibongkar; konfigurasi dikembalikan setelah selesai.
     */
    private static boolean ejaanPemilikTidakMembelah() throws Exception {
        Db.setConfiguration("org.h2.Driver",
                "jdbc:h2:mem:uitest-ejaan;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try {
            DialogDataMaster panel = new DialogDataMaster();
            JTable tabel = (JTable) field(panel, "tableTruk");

            // (1) Truk pertama sekaligus melahirkan pemiliknya.
            isiPemilik(panel, "Rental Sinar Jaya");
            isi(panel, "fPlat", "ZZ 1111 AA");
            try {
                klik(panel, "tambahTruk");
            } catch (Exception e) {
                Throwable sebab = e.getCause() == null ? e : e.getCause();
                System.out.println("        menambah truk pertama gagal: " + sebab);
                return false;
            }

            // (2) Ejaan kedua untuk pemilik yang sama: hurufnya beda besar-kecil,
            // spasinya berlebih di tengah sekaligus berlebih di ujungnya. Harus
            // menunjuk pemilik itu juga, bukan melahirkan pemilik baru.
            isiPemilik(panel, "  RENTAL    sinar   jaya ");
            isi(panel, "fPlat", "ZZ 2222 BB");
            try {
                klik(panel, "tambahTruk");
            } catch (Exception e) {
                Throwable sebab = e.getCause() == null ? e : e.getCause();
                System.out.println("        menambah truk dengan ejaan kedua gagal: " + sebab);
                return false;
            }

            MasterDao dao = new MasterDao();
            if (dao.listRental().size() != 1) {
                System.out.println("        ejaan berbeda melahirkan " + dao.listRental().size()
                        + " pemilik, seharusnya 1");
                return false;
            }
            Integer pemilik1 = null;
            Integer pemilik2 = null;
            for (Truck t : dao.listTrucks()) {
                if ("ZZ 1111 AA".equals(t.getPlate())) {
                    pemilik1 = t.getRentalId();
                }
                if ("ZZ 2222 BB".equals(t.getPlate())) {
                    pemilik2 = t.getRentalId();
                }
            }
            if (pemilik1 == null || pemilik2 == null) {
                System.out.println("        truk ujinya tidak tersimpan");
                return false;
            }
            if (!pemilik1.equals(pemilik2)) {
                System.out.println("        satu pemilik terbaca dua: truk pertama milik id="
                        + pemilik1 + ", truk kedua milik id=" + pemilik2);
                return false;
            }
            // Daftar di dialognya juga harus menulis nama pemilik yang sama di kedua
            // barisnya - bukan dua nama untuk satu perusahaan.
            String nama0 = String.valueOf(tabel.getValueAt(0, 1));
            String nama1 = String.valueOf(tabel.getValueAt(1, 1));
            if (!nama0.equals(nama1)) {
                System.out.println("        daftar truk menulis dua nama pemilik: '"
                        + nama0 + "' dan '" + nama1 + "'");
                return false;
            }

            // (3) Plat KEMBAR lewat jalur yang dipakai operator, disertai nama pemilik
            // baru. Langkah ini yang menjaga urutannya: kalau tambahTruk kembali membuat
            // pemiliknya lebih dulu lalu menyimpan truknya, penolakan plat kembar datang
            // SETELAH pemilik barunya ter-commit - dan pemilik tanpa truk itu langsung
            // muncul di setiap kotak pilihan pemilik sementara rekap uangnya terpecah.
            // Penjaga yang memanggil simpanTrukBaru langsung tidak menangkap itu, karena
            // yang rusak justru urutan di dalam dialognya.
            isiPemilik(panel, "Rental Barokah");
            isi(panel, "fPlat", "ZZ 1111 AA");
            klik(panel, "tambahTruk");
            if (dao.listRental().size() != 1) {
                System.out.println("        plat kembar meninggalkan " + dao.listRental().size()
                        + " pemilik, seharusnya tetap 1 - pemilik hampa lahir dari jalur tambah");
                return false;
            }
            if (dao.listTrucks().size() != 2) {
                System.out.println("        plat kembar mengubah jumlah truk menjadi "
                        + dao.listTrucks().size() + ", seharusnya tetap 2");
                return false;
            }
            String status = ((JLabel) field(panel, "lblStatus")).getText();
            if (!status.contains("sudah terdaftar")) {
                System.out.println("        penolakan plat kembar tidak terbaca di baris status: \""
                        + status + "\"");
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
     * Penghapusan massal harus diperiksa SELURUHNYA lebih dulu sebelum satu pun dihapus.
     *
     * <p>Tiap penghapusan truk membuka koneksinya sendiri dan meng-commit sendiri, jadi
     * menghapus satu per satu tidak bisa dibatalkan: kalau baris kedua ditolak, baris
     * pertama sudah telanjur hilang dan daftarnya tinggal setengah jadi. Karena itu,
     * selama SATU SAJA baris terpilih masih dipakai catatan pengiriman, tidak ada yang
     * boleh terhapus.
     *
     * <p>Penolakan dan konfirmasinya memunculkan jendela pesan, dan jendela itu tidak
     * bisa dibuat tanpa layar - pemanggilannya ditangkap dan diabaikan saja, karena
     * keputusannya sudah terbaca dari catatan dao yang dipasang menggantikan dao
     * panelnya: truk mana saja yang penolakannya diperiksa, dan truk mana saja yang
     * benar-benar dihapus.
     */
    private static boolean hapusMassalBatalSeluruhnya() throws Exception {
        DialogDataMaster panel = new DialogDataMaster();
        JTable tabel = (JTable) field(panel, "tableTruk");

        // Dua baris dipilih: 'KB 8234 HD' masih dipakai catatan pengiriman (fillData
        // membuat transaksinya), 'BE 8437 CF' bebas. Yang bebas sengaja berada lebih
        // awal di daftar: kalau penghapusan berjalan satu per satu tanpa diperiksa
        // lebih dulu, dialah yang terhapus duluan sehingga kekeliruan itu langsung
        // kelihatan dari catatannya.
        int barisTerpakai = -1;
        int barisBebas = -1;
        for (int i = 0; i < tabel.getRowCount(); i++) {
            String plat = String.valueOf(tabel.getValueAt(i, 0));
            if ("KB 8234 HD".equals(plat)) {
                barisTerpakai = i;
            }
            if ("BE 8437 CF".equals(plat)) {
                barisBebas = i;
            }
        }
        if (barisTerpakai < 0 || barisBebas < 0) {
            System.out.println("        truk uji untuk penghapusan massal tidak lengkap");
            return false;
        }
        MasterDao asli = new MasterDao();
        int idTerpakai = idTruckPlat(asli, "KB 8234 HD");
        int idBebas = idTruckPlat(asli, "BE 8437 CF");
        if (idTerpakai == 0 || idBebas == 0) {
            System.out.println("        id truk uji tidak terbaca dari database");
            return false;
        }

        DaoPencatat dao = new DaoPencatat();
        java.lang.reflect.Field f = panel.getClass().getDeclaredField("dao");
        f.setAccessible(true);
        f.set(panel, dao);

        tabel.setRowSelectionInterval(barisBebas, barisBebas);
        tabel.addRowSelectionInterval(barisTerpakai, barisTerpakai);
        try {
            klik(panel, "hapusTruk");
        } catch (Exception e) {
            // Jendela pesan memang tidak bisa dibuat tanpa layar; keputusan hapus/tidak
            // dibaca dari catatan dao di bawah, bukan dari jendelanya.
        }

        if (!dao.dihapus.isEmpty()) {
            System.out.println("        ada truk yang terhapus padahal satu baris terhalang: id "
                    + dao.dihapus);
            return false;
        }
        if (!dao.penolakanDicek.contains(idTerpakai) || !dao.penolakanDicek.contains(idBebas)) {
            System.out.println("        penolakan hanya diperiksa untuk " + dao.penolakanDicek.size()
                    + " baris, seharusnya keduanya sebelum menghapus apa pun");
            return false;
        }
        int tersisa = 0;
        for (Truck t : asli.listTrucks()) {
            if (t.getTruckId() == idTerpakai || t.getTruckId() == idBebas) {
                tersisa++;
            }
        }
        if (tersisa != 2) {
            System.out.println("        baris truk hilang dari database: tersisa " + tersisa + " dari 2");
            return false;
        }
        return true;
    }

    /**
     * Penghapusan massal tidak boleh menghapus satu pun baris selama satu saja baris
     * terpilih masih dipakai catatan pengiriman.
     *
     * <p>Penjaga {@link #hapusMassalBatalSeluruhnya()} membaca keputusannya dari dao
     * tiruan, karena jalur tombolnya berhenti di jendela pesan yang tidak bisa dibuat
     * tanpa layar. Sekarang pemeriksaan dan penghapusannya bisa dipanggil langsung,
     * jadi yang diuji jalur yang benar-benar dipakai tombol itu: {@code platTerhalang}
     * menunjuk TEPAT plat yang terpakai, {@code hapusSekaligus} pada truk terpakai
     * ditolak penjagaan lapis keduanya di dao, dan penghapusan yang bebas benar-benar
     * menghapus barisnya.
     */
    private static boolean hapusMassalTidakMenghapusApaPun() throws Exception {
        Db.setConfiguration("org.h2.Driver",
                "jdbc:h2:mem:uitest-hapus-massal;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try {
            // Data uji sendiri supaya penghapusan di sini tidak membongkar data uji
            // pemeriksaan lain: satu pemilik, tiga truk, dan satu catatan pengiriman
            // yang dibuat lewat jalan yang sama dengan aplikasi.
            MasterDao dao = new MasterDao();
            Rental pemilik = new Rental();
            pemilik.setRentalName("Rental Uji Hapus");
            dao.saveRental(pemilik);
            int idPemilik = idRental(dao, "Rental Uji Hapus");
            for (String plat : new String[]{"ZZ 1001 AA", "ZZ 1002 BB", "ZZ 1003 CC"}) {
                Truck t = new Truck();
                t.setPlate(plat);
                t.setRentalId(idPemilik);
                dao.saveTruck(t);
            }
            kaspe.model.Transaction kirim = new kaspe.model.Transaction();
            kirim.setDate(java.time.LocalDate.of(2026, 10, 1));
            new kaspe.dao.TransactionDao().save(kirim, java.util.Collections.singletonList(
                    detail(dao.listTrucks(), "ZZ 1003 CC", 6000, 5920, 15, 1150)));

            DialogDataMaster panel = new DialogDataMaster();
            JTable tabel = (JTable) field(panel, "tableTruk");
            if (tabel.getRowCount() != 3) {
                System.out.println("        data uji tidak termuat: " + tabel.getRowCount() + " baris");
                return false;
            }
            int barisBebas1 = -1;
            int barisBebas2 = -1;
            int barisTerpakai = -1;
            for (int i = 0; i < tabel.getRowCount(); i++) {
                String plat = String.valueOf(tabel.getValueAt(i, 0));
                if ("ZZ 1001 AA".equals(plat)) {
                    barisBebas1 = i;
                }
                if ("ZZ 1002 BB".equals(plat)) {
                    barisBebas2 = i;
                }
                if ("ZZ 1003 CC".equals(plat)) {
                    barisTerpakai = i;
                }
            }
            if (barisBebas1 < 0 || barisBebas2 < 0 || barisTerpakai < 0) {
                System.out.println("        truk uji tidak lengkap di daftar");
                return false;
            }
            Truck terpakai = null;
            for (Truck t : dao.listTrucks()) {
                if ("ZZ 1003 CC".equals(t.getPlate())) {
                    terpakai = t;
                }
            }
            if (terpakai == null) {
                System.out.println("        truk terpakai tidak terbaca dari database");
                return false;
            }

            // Dua baris terpilih: satu bebas, satu terpakai. Daftar yang terhalang
            // memang tidak diteruskan ke hapusSekaligus - yang bebas bisa terhapus
            // duluan, dan justru itulah keadaan setengah jadi yang mau dicegah.
            tabel.setRowSelectionInterval(barisBebas1, barisBebas1);
            tabel.addRowSelectionInterval(barisTerpakai, barisTerpakai);
            @SuppressWarnings("unchecked")
            java.util.List<Truck> dipilih = (java.util.List<Truck>) ambil(panel, "trukTerpilih");
            if (dipilih.size() != 2) {
                System.out.println("        trukTerpilih mengembalikan " + dipilih.size()
                        + " truk, seharusnya 2");
                return false;
            }
            java.lang.reflect.Method cekPlat = panel.getClass()
                    .getDeclaredMethod("platTerhalang", java.util.List.class);
            cekPlat.setAccessible(true);
            Object jawaban = cekPlat.invoke(panel, dipilih);
            @SuppressWarnings("unchecked")
            java.util.List<String> terhalang = (java.util.List<String>) jawaban;
            if (terhalang.size() != 1 || !terhalang.get(0).equals(terpakai.getPlate())) {
                System.out.println("        plat terhalang: " + terhalang + ", seharusnya hanya ["
                        + terpakai.getPlate() + "]");
                return false;
            }

            // Lapis kedua di dao: hapusSekaligus pada truk terpakai harus ditolak, dan
            // penolakannya terjadi sebelum satu pun baris hilang.
            java.lang.reflect.Method hapus = panel.getClass()
                    .getDeclaredMethod("hapusSekaligus", java.util.List.class);
            hapus.setAccessible(true);
            try {
                hapus.invoke(panel, java.util.Collections.singletonList(terpakai));
                System.out.println("        penghapusan truk terpakai lolos dari penjagaan dao");
                return false;
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (!(e.getCause() instanceof IllegalStateException)) {
                    System.out.println("        penolakan dao melempar " + e.getCause()
                            + ", seharusnya IllegalStateException");
                    return false;
                }
            }
            if (tabel.getRowCount() != 3 || dao.listTrucks().size() != 3) {
                System.out.println("        ada baris yang hilang: tabel " + tabel.getRowCount()
                        + " baris, database " + dao.listTrucks().size() + " truk");
                return false;
            }

            // Dua-duanya bebas: seluruhnya terhapus, jumlah baris berkurang dua.
            tabel.setRowSelectionInterval(barisBebas1, barisBebas1);
            tabel.addRowSelectionInterval(barisBebas2, barisBebas2);
            @SuppressWarnings("unchecked")
            java.util.List<Truck> duaBebas = (java.util.List<Truck>) ambil(panel, "trukTerpilih");
            if (duaBebas.size() != 2) {
                System.out.println("        dua truk bebas terbaca " + duaBebas.size() + " truk");
                return false;
            }
            Object jawabanBebas = cekPlat.invoke(panel, duaBebas);
            @SuppressWarnings("unchecked")
            java.util.List<String> bebas = (java.util.List<String>) jawabanBebas;
            if (!bebas.isEmpty()) {
                System.out.println("        dua truk bebas dianggap terhalang: " + bebas);
                return false;
            }
            try {
                hapus.invoke(panel, duaBebas);
            } catch (Exception e) {
                Throwable sebab = e.getCause() == null ? e : e.getCause();
                System.out.println("        menghapus dua truk bebas gagal: " + sebab);
                return false;
            }
            klik(panel, "muat");
            if (tabel.getRowCount() != 1 || dao.listTrucks().size() != 1) {
                System.out.println("        setelah hapus dua truk bebas tersisa " + tabel.getRowCount()
                        + " baris di tabel dan " + dao.listTrucks().size() + " truk di database");
                return false;
            }
            if (!"ZZ 1003 CC".equals(String.valueOf(tabel.getValueAt(0, 0)))) {
                System.out.println("        yang tersisa bukan truk terpakai: " + tabel.getValueAt(0, 0));
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
     * Menambah truk dengan plat yang sudah terdaftar tidak boleh meninggalkan
     * pemilik hampa.
     *
     * <p>Jalur lamanya membuat pemiliknya lebih dulu di koneksi sendiri, jadi saat
     * plat kembar ditolak, yang batal hanya truknya - pemiliknya sudah ter-commit
     * dan diam-diam lahir tanpa satu pun truk, lalu tampil di setiap kotak pilihan
     * pemilik sementara rekap uangnya terpecah. {@code simpanTrukBaru} mengerjakan
     * semuanya dalam satu transaksi, dan penjaga ini memastikan penolakannya
     * benar-benar membatalkan seluruhnya: pemeriksaannya bernilai (jumlah pemilik
     * dan truk yang tersisa, nama pemiliknya), bukan sekadar tidak melempar.
     */
    private static boolean platKembarTidakMeninggalkanPemilikHampa() throws Exception {
        Db.setConfiguration("org.h2.Driver",
                "jdbc:h2:mem:uitest-plat-kembar;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try {
            MasterDao dao = new MasterDao();

            // (1) Truk pertama sekaligus melahirkan pemiliknya.
            dao.simpanTrukBaru("BE 1111 AA", "Rental Satu");

            // (2) Plat yang sama dengan ejaan huruf berbeda, disertai nama pemilik
            // BARU: harus ditolak. Penolakan inilah yang dulu datang terlambat,
            // setelah pemilik "Rental Dua" ter-commit.
            try {
                dao.simpanTrukBaru("be 1111 aa", "Rental Dua");
                System.out.println("        plat kembar diterima begitu saja");
                return false;
            } catch (IllegalArgumentException e) {
                // penolakan yang diharapkan
            } catch (SQLException e) {
                System.out.println("        penolakan plat kembar melempar "
                        + e.getClass().getSimpleName() + ", seharusnya IllegalArgumentException");
                return false;
            }

            // (3) Penolakan itu tidak boleh meninggalkan jejak: pemilik hampa tidak
            // lahir, truknya juga tidak lahir, dan pemilik yang tersisa tetap yang asli.
            if (dao.listRental().size() != 1) {
                System.out.println("        penolakan meninggalkan " + dao.listRental().size()
                        + " pemilik, seharusnya tetap 1");
                return false;
            }
            if (dao.listTrucks().size() != 1) {
                System.out.println("        penolakan meninggalkan " + dao.listTrucks().size()
                        + " truk, seharusnya tetap 1");
                return false;
            }
            if (!"Rental Satu".equals(dao.listRental().get(0).getRentalName())) {
                System.out.println("        pemilik yang tersisa berubah menjadi '"
                        + dao.listRental().get(0).getRentalName() + "'");
                return false;
            }

            // (4) Jalur biasanya tetap jalan: pemilik kedua lahir bersama truknya.
            dao.simpanTrukBaru("BE 2222 BB", "Rental Dua");
            if (dao.listRental().size() != 2 || dao.listTrucks().size() != 2) {
                System.out.println("        setelah truk kedua: " + dao.listRental().size()
                        + " pemilik dan " + dao.listTrucks().size() + " truk, seharusnya 2 dan 2");
                return false;
            }

            // (5) Ejaan pemilik yang beda besar-kecil huruf dan beda spasinya menunjuk
            // pemilik yang sama, jadi jumlah pemilik tetap 2.
            dao.simpanTrukBaru("BE 3333 CC", "rental  dua");
            if (dao.listRental().size() != 2) {
                System.out.println("        ejaan kedua melahirkan pemilik ketiga: "
                        + dao.listRental().size() + " pemilik, seharusnya tetap 2");
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
     * Dialog pemilik memegang satu-satunya jalan mengganti nama pemilik yang salah ketik.
     *
     * <p>Nama rental dibaca laporan lewat relasi, jadi mengganti nama harus MENULIS ULANG
     * barisnya, bukan melahirkan pemilik kedua: kalau menjadi dua, rekap uang per pemilik
     * terpecah permanen tanpa satu pun pesan. Pemilik yang sudah dipakai truk juga tidak
     * boleh bisa dihapus - kunci tamunya ON DELETE SET NULL, jadi DELETE-nya sendiri
     * tidak gagal, diam-diam melepaskan seluruh truknya dari pemiliknya.
     */
    private static boolean gantiNamaPemilik() throws Exception {
        Db.setConfiguration("org.h2.Driver",
                "jdbc:h2:mem:uitest-pemilik;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try {
            // Satu pemilik dengan satu truk: namanya mau diganti, dan karena sudah
            // dipakai truk, penghapusannya harus ditolak.
            MasterDao dao = new MasterDao();
            Rental pemilik = new Rental();
            pemilik.setRentalName("Rental Salah Ketik");
            dao.saveRental(pemilik);
            int idPemilik = idRental(dao, "Rental Salah Ketik");
            Truck t = new Truck();
            t.setPlate("ZZ 3131 XX");
            t.setRentalId(idPemilik);
            dao.saveTruck(t);

            DialogPemilik panel = new DialogPemilik();
            JTable tabel = (JTable) field(panel, "tableRental");
            if (tabel.getRowCount() != 1) {
                System.out.println("        pemilik uji tidak termuat: " + tabel.getRowCount()
                        + " baris, seharusnya 1");
                return false;
            }

            // Persis seperti operator: barisnya dipilih dulu (menyorotinya mengisi kotak
            // namanya), lalu nama barunya diketik, lalu Simpan Perubahan ditekan.
            tabel.setRowSelectionInterval(0, 0);
            isi(panel, "fNama", "Rental Benar Ketik");
            try {
                klik(panel, "ubah");
            } catch (Exception e) {
                Throwable sebab = e.getCause() == null ? e : e.getCause();
                System.out.println("        mengganti nama pemilik gagal: " + sebab);
                return false;
            }

            // Nama di barisnya berubah, tetapi jumlah pemiliknya tidak: ganti nama,
            // bukan pemilik baru.
            if (tabel.getRowCount() != 1) {
                System.out.println("        ganti nama menambah baris: " + tabel.getRowCount()
                        + ", seharusnya 1");
                return false;
            }
            String tampil = String.valueOf(tabel.getValueAt(0, 0));
            if (!"Rental Benar Ketik".equals(tampil)) {
                System.out.println("        nama di barisnya masih \"" + tampil + "\"");
                return false;
            }
            java.util.List<Rental> sesudah = dao.listRental();
            if (sesudah.size() != 1) {
                System.out.println("        pemilik menjadi " + sesudah.size()
                        + " di database, seharusnya 1");
                return false;
            }
            if (sesudah.get(0).getRentalId() != idPemilik
                    || !"Rental Benar Ketik".equals(sesudah.get(0).getRentalName())) {
                System.out.println("        nama pemilik tidak tertulis ulang: id="
                        + sesudah.get(0).getRentalId() + ", nama=\""
                        + sesudah.get(0).getRentalName() + "\"");
                return false;
            }

            // Pemilik yang sudah dipakai truk tidak boleh bisa dihapus.
            String penolakan = dao.rentalDeleteRefusal(idPemilik);
            if (penolakan == null) {
                System.out.println("        pemilik yang masih punya truk boleh dihapus");
                return false;
            }

            // Mengganti nama menjadi nama yang SUDAH dipakai harus ditolak lewat jalur
            // yang dipakai operator, dan penolakannya harus terbaca sebagai penolakan -
            // bukan jendela "Gagal:" yang berbunyi seperti programnya rusak. Nama di
            // barisnya tidak boleh ikut berubah, dan pemiliknya tidak boleh bertambah.
            Rental lain = new Rental();
            lain.setRentalName("Rental Lain");
            dao.saveRental(lain);

            tabel.setRowSelectionInterval(0, 0);
            isi(panel, "fNama", "Rental Lain");
            try {
                klik(panel, "ubah");
            } catch (Exception e) {
                // Jalur yang salah menuliskan penolakannya lewat jendela "Gagal:" -
                // dan tanpa layar jendela itu melempar HeadlessException. Ditangkap di
                // sini supaya berakhir sebagai pemeriksaan yang GAGAL dengan nama, bukan
                // sebagai tumpukan galat yang menghentikan seluruh berkas uji sebelum
                // mencetak hasilnya - keluaran macam itu terbaca seperti uji yang rusak,
                // bukan seperti cacat yang ketahuan.
                Throwable sebab = e.getCause() == null ? e : e.getCause();
                System.out.println("        penggantian nama ke nama kembar melempar "
                        + sebab.getClass().getSimpleName()
                        + " - seharusnya ditolak dengan pesan di baris status");
                return false;
            }
            String statusKembar = ((JLabel) field(panel, "lblStatus")).getText();
            if (!statusKembar.contains("sudah dipakai")) {
                System.out.println("        penggantian nama menjadi nama kembar tidak ditolak: \""
                        + statusKembar + "\"");
                return false;
            }
            if (!"Rental Benar Ketik".equals(
                    String.valueOf(dao.listRental().get(0).getRentalName()))
                    && !"Rental Benar Ketik".equals(
                            String.valueOf(dao.listRental().get(1).getRentalName()))) {
                System.out.println("        nama pemilik ikut berubah jadi nama yang sudah dipakai");
                return false;
            }
            if (dao.listRental().size() != 2) {
                System.out.println("        percobaan nama kembar mengubah jumlah pemilik menjadi "
                        + dao.listRental().size() + ", seharusnya tetap 2");
                return false;
            }
            return true;
        } finally {
            Db.setConfiguration("org.h2.Driver",
                    "jdbc:h2:mem:uitest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                    "sa", "");
        }
    }

    /** Id truk menurut platnya, atau 0 kalau tidak ada. */
    private static int idTruckPlat(MasterDao dao, String plat) throws Exception {
        for (Truck t : dao.listTrucks()) {
            if (plat.equals(t.getPlate())) {
                return t.getTruckId();
            }
        }
        return 0;
    }

    /**
     * MasterDao yang mencatat pemanggilan pemeriksaan penolakan dan penghapusan.
     * Semua pekerjaannya tetap dikerjakan induknya; catatannya hanya untuk membaca
     * urutan keputusan panel, yang tidak kelihatan dari database karena memang tidak
     * ada yang berubah.
     */
    private static final class DaoPencatat extends MasterDao {
        final java.util.List<Integer> penolakanDicek = new java.util.ArrayList<>();
        final java.util.List<Integer> dihapus = new java.util.ArrayList<>();

        @Override
        public String truckDeleteRefusal(int id) throws SQLException {
            penolakanDicek.add(id);
            return super.truckDeleteRefusal(id);
        }

        @Override
        public void deleteTruck(int id) throws SQLException {
            dihapus.add(id);
            super.deleteTruck(id);
        }
    }

    /**
     * Benar kalau tidak ada tombol yang tergambar keluar dari batas wadahnya.
     *
     * <p>Yang diperiksa batas wadah, bukan lebar teksnya: cara tombol menghilang di sini
     * adalah terlipat ke baris berikutnya oleh susunan yang kelebihan muatan, sehingga
     * posisinya jatuh di luar tinggi wadahnya. Karena itu tombol yang benar-benar tidak
     * ada tidak dianggap masalah - menghapus sebuah tombol dengan sengaja bukan kesalahan -
     * sedangkan tombol yang ada tetapi tidak terjangkau selalu ketahuan.
     */
    private static boolean tombolTidakTerpotong(JPanel panel, String nama) {
        return tombolTidakTerpotong(panel, nama, 1320);
    }

    private static boolean tombolTidakTerpotong(JPanel panel, String nama, int lebar) {
        PagePanel halaman = new PagePanel();
        JPanel layar = PagePanel.shell(halaman);
        halaman.showPanel(panel, nama, "keterangan");
        layar.setSize(lebar, 760);
        for (int i = 0; i < 3; i++) {
            layar.doLayout();
            layoutDeep(layar);
        }

        java.util.List<String> terpotong = new java.util.ArrayList<String>();
        cariTombolTerpotong(layar, layar, terpotong);
        for (String t : terpotong) {
            System.out.println("        " + t);
        }
        return terpotong.isEmpty();
    }

    /**
     * Benar kalau tidak ada tombol dialog data master yang tergambar keluar dari wadahnya.
     *
     * <p>Dialog itu bukan halaman dan tidak pernah masuk susunan {@link PagePanel};
     * ukurannya ditetapkan sendiri oleh jendela pembukanya: sekurang-kurangnya
     * 720x520, atau lebih besar kalau isinya minta. Karena itu diperiksa pada ukuran
     * yang benar-benar dipakai dialognya - memeriksa lebar halaman berarti menguji
     * lebar yang tidak pernah memuatnya. Ukurannya juga cuma satu: tidak ada
     * pemeriksaan kedua pada "lebar jendela minimum" seperti halaman, karena dialognya
     * memang tidak pernah lebih sempit dari itu.
     */
    private static boolean tombolDialogTidakTerpotong() {
        DialogDataMaster panel = new DialogDataMaster();
        panel.setSize(Math.max(720, panel.getPreferredSize().width),
                Math.max(520, panel.getPreferredSize().height));
        for (int i = 0; i < 3; i++) {
            panel.doLayout();
            layoutDeep(panel);
        }

        java.util.List<String> terpotong = new java.util.ArrayList<String>();
        cariTombolTerpotong(panel, panel, terpotong);
        for (String t : terpotong) {
            System.out.println("        " + t);
        }
        return terpotong.isEmpty();
    }

    /**
     * Cari tombol yang tidak terjangkau pengguna, lewat DUA jalan yang berbeda.
     *
     * <p>Yang pertama: tombolnya keluar dari wadahnya sendiri. Itu yang terjadi waktu tombol
     * "Cadangkan Database" terlipat ke baris kedua oleh susunan yang kelebihan muatan, lalu
     * terpotong oleh tinggi wadahnya.
     *
     * <p>Yang kedua: tombolnya masih rapi di dalam wadahnya, tetapi wadah itu sendiri melebar
     * keluar jendela. Diperiksa karena pemeriksaan pertama TIDAK menangkapnya - waktu kolom
     * kanan form melebihi lebar jendela, tombol Simpan tetap berada di dalam kolomnya, jadi
     * hanya pemeriksaan terhadap lebar jendela yang menemukannya.
     */
    private static void cariTombolTerpotong(Container c, Container akar, java.util.List<String> hasil) {
        for (Component anak : c.getComponents()) {
            if (anak instanceof javax.swing.AbstractButton && anak.isVisible()) {
                javax.swing.AbstractButton b = (javax.swing.AbstractButton) anak;
                Container induk = anak.getParent();
                if (induk != null && induk.getWidth() > 0
                        && (anak.getX() + anak.getWidth() > induk.getWidth()
                            || anak.getY() + anak.getHeight() > induk.getHeight())) {
                    hasil.add("tombol \"" + b.getText() + "\" di "
                            + induk.getClass().getSimpleName()
                            + " pos=(" + anak.getX() + "," + anak.getY() + ") ukuran="
                            + anak.getWidth() + "x" + anak.getHeight()
                            + " keluar dari wadah " + induk.getWidth() + "x" + induk.getHeight());
                } else {
                    int kiri = 0;
                    for (Component k = anak; k != null && k != akar; k = k.getParent()) {
                        kiri += k.getX();
                    }
                    if (kiri + anak.getWidth() > akar.getWidth()) {
                        hasil.add("tombol \"" + b.getText() + "\" melewati tepi jendela:"
                                + " ujung kanannya di " + (kiri + anak.getWidth())
                                + ", lebar jendela " + akar.getWidth());
                    }
                }
            }
            if (anak instanceof Container) {
                cariTombolTerpotong((Container) anak, akar, hasil);
            }
        }
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
     * <p>Lima keadaan diperiksa:
     *
     * <ol>
     *   <li>Setiap plat di daftar harus tampil bersama pemiliknya sendiri.</li>
     *   <li>Plat yang belum pernah ada harus mengosongkan pilihan rental, bukan
     *       mewarisi rental baris sebelumnya.</li>
     *   <li>Rental yang dipilih operator untuk plat baru harus tercatat pada
     *       pengiriman yang tersimpan, dan truknya lahir milik rental pilihan itu.</li>
     *   <li>Plat yang sudah dikenal + rental yang BERBEDA dari pemilik tersimpan harus
     *       DITOLAK dengan pesan, bukan diam-diam memakai pemilik lama.</li>
     *   <li>Rental yang sama (huruf beda) untuk plat yang dikenal tetap diterima,
     *       dan catatannya tetap milik pemilik yang tersimpan.</li>
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

        // (3) Operator memilih rental sendiri untuk plat baru, lalu menyimpan
        // pengirimannya. Pilihan itu harus tercatat pada pengiriman yang tersimpan,
        // dan truknya lahir milik rental pilihan itu — bukan milik rental lain.
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
        // Simpan, dan Swing menyampaikan itu sebagai ActionEvent — bukan sebagai
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
        int riwayatSebelum = jumlahRiwayat(p);
        isi(p, "txtFieldWeight", "7200");
        isi(p, "txtFactoryWeight", "7050");
        isi(p, "txtRefraction", "15");
        isi(p, "txtPrice", "1150");
        if (!simpanPanel(p)) {
            return false;
        }

        // Pengiriman itu tersimpan sebagai satu catatan dengan pasangan plat + rental
        // pilihan operator, dan truknya lahir milik rental pilihan itu.
        if (jumlahRiwayat(p) != riwayatSebelum + 1) {
            System.out.println("        pengiriman plat baru tidak tercatat tepat satu kali");
            return false;
        }
        kaspe.model.ReportRow tersimpan = cariPengiriman("ZZ 9999 ZZ");
        if (tersimpan == null || !dipilih.getRentalName().equals(tersimpan.getRentalName())) {
            System.out.println("        pengiriman plat baru tercatat rental '"
                    + (tersimpan == null ? "-" : tersimpan.getRentalName())
                    + "', seharusnya '" + dipilih.getRentalName() + "'");
            return false;
        }
        Truck lahir = null;
        for (Truck t : dao.listTrucks()) {
            if ("ZZ 9999 ZZ".equals(t.getPlate())) {
                lahir = t;
            }
        }
        if (lahir == null || lahir.getRentalId() == null
                || lahir.getRentalId() != dipilih.getRentalId()) {
            System.out.println("        truk plat baru lahir tanpa pemilik yang dipilih operator");
            return false;
        }

        // (4) Plat yang sudah dikenal + rental yang sengaja disetel ke rental LAIN:
        // harus DITOLAK dengan pesan yang menunjuk pemilik tersimpan, tidak ada catatan
        // baru, dan pemilik yang tersimpan tidak boleh berubah diam-diam.
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
        int jumlahRiwayatSebelum = jumlahRiwayat(p);

        isi(p, "txtFieldWeight", "7200");
        isi(p, "txtFactoryWeight", "7050");
        isi(p, "txtRefraction", "15");
        isi(p, "txtPrice", "1150");
        if (!simpanPanel(p)) {
            return false;
        }

        if (jumlahRiwayat(p) != jumlahRiwayatSebelum) {
            System.out.println("        plat dikenal + rental berbeda tidak ditolak, catatan ikut tersimpan");
            return false;
        }
        String status = String.valueOf(((javax.swing.JLabel) field(p, "lblStatus")).getText());
        if (!status.contains(String.valueOf(namaPemilik)) || !status.contains("Pindah Pemilik")) {
            System.out.println("        penolakan tidak menunjuk pemilik tersimpan dan jalannya: \""
                    + status + "\"");
            return false;
        }
        if (!idPemilik.equals(pemilikPlat(namaPlat))) {
            System.out.println("        pemilik tersimpan berubah diam-diam oleh penyimpanan");
            return false;
        }

        // (5) Rental yang sama (huruf beda) untuk plat yang dikenal tetap diterima,
        // dan catatannya tetap milik pemilik yang tersimpan — tidak terpecah.
        for (int i = 0; i < rental.getItemCount(); i++) {
            Rental r = (Rental) rental.getItemAt(i);
            if (r != null && idPemilik != null && r.getRentalId() == idPemilik) {
                rental.setSelectedIndex(i);
                rental.getEditor().setItem(r.getRentalName().toLowerCase());
                break;
            }
        }
        if (!simpanPanel(p)) {
            return false;
        }
        if (jumlahRiwayat(p) != jumlahRiwayatSebelum + 1) {
            System.out.println("        rental yang sama (huruf beda) malah ditolak");
            return false;
        }
        for (kaspe.model.ReportRow b : new kaspe.dao.TransactionDao().listDeliveries(null, null)) {
            if (namaPlat.equals(b.getPlate())
                    && !String.valueOf(namaPemilik).equals(b.getRentalName())) {
                System.out.println("        catatan plat '" + namaPlat + "' tercatat milik '"
                        + b.getRentalName() + "', padahal pemiliknya '" + namaPemilik + "'");
                return false;
            }
        }
        return true;
    }

    /** Banyaknya pengiriman yang terdaftar di daftar "Transaksi Tersimpan". */
    private static int jumlahRiwayat(PanelTransaction p) throws Exception {
        return ((JTable) field(p, "riwayatTable")).getRowCount();
    }

    /** Pengiriman tersimpan untuk sebuah plat, atau null kalau belum ada. */
    private static kaspe.model.ReportRow cariPengiriman(String plat) throws Exception {
        for (kaspe.model.ReportRow b : new kaspe.dao.TransactionDao().listDeliveries(null, null)) {
            if (plat.equals(b.getPlate())) {
                return b;
            }
        }
        return null;
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

    /** Isi kotak pemilik yang bisa diketik di dialog data master lewat pantulan. */
    private static void isiPemilik(Object target, String nama) throws Exception {
        ((JComboBox<?>) field(target, "cmbRental")).getEditor().setItem(nama);
    }

    /**
     * Nama rental yang diketik langsung harus tercatat pada pengiriman yang disimpan,
     * dan truk serta rentalnya lahir saat pengiriman itu disimpan.
     *
     * <p>Tiga keadaan diperiksa:
     *
     * <ol>
     *   <li>Rental yang belum pernah ada, diketik dengan spasi berantakan untuk plat
     *       yang juga belum pernah ada: pengiriman tersimpan memuat bentuk yang sudah
     *       dirapikan, dan truknya lahir milik rental itu.</li>
     *   <li>Pengiriman kedua memakai nama yang sama dengan ejaan huruf berbeda, untuk
     *       plat lain yang juga baru: tetap diterima.</li>
     *   <li>Kedua truk itu milik SATU rental yang sama — ejaan yang berbeda tidak
     *       boleh memecahnya menjadi dua pemilik, karena jumlah uang per pemilik
     *       ikut terpecah.</li>
     * </ol>
     *
     * <p>Simpan dijalankan lewat tombol Simpannya sendiri; dialog "Tersimpan" yang
     * tidak bisa tampil tanpa layar ditangani di {@link #simpanPanel(PanelTransaction)}.
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
        int riwayatSebelum = jumlahRiwayat(p);

        // (1) Rental yang belum pernah ada, diketik untuk plat yang juga belum pernah ada.
        // Spasinya sengaja berantakan: yang tercatat harus bentuk yang sudah dirapikan.
        plat.getEditor().setItem("zz  1234  zz");
        rental.getEditor().setItem("  CV   Uji Diketik ");
        isi(p, "txtFieldWeight", "7000");
        isi(p, "txtFactoryWeight", "6900");
        isi(p, "txtRefraction", "15");
        isi(p, "txtPrice", "1150");
        if (!simpanPanel(p)) {
            return false;
        }
        if (jumlahRiwayat(p) != riwayatSebelum + 1) {
            System.out.println("        pengiriman dengan rental yang diketik tidak tersimpan");
            return false;
        }
        if (dao.listRental().size() != rentalSebelum + 1) {
            System.out.println("        simpan membuat " + (dao.listRental().size() - rentalSebelum)
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
        kaspe.model.ReportRow baris1 = cariPengiriman("ZZ 1234 ZZ");
        if (baris1 == null || !"CV Uji Diketik".equals(baris1.getRentalName())) {
            System.out.println("        pengiriman tercatat plat '"
                    + (baris1 == null ? "-" : baris1.getPlate()) + "' rental '"
                    + (baris1 == null ? "-" : baris1.getRentalName())
                    + "', seharusnya 'ZZ 1234 ZZ' / 'CV Uji Diketik'");
            return false;
        }

        // (2) Nama yang sama dengan ejaan huruf berbeda, untuk plat lain yang juga baru.
        plat.getEditor().setItem("ZZ 5678 ZZ");
        rental.getEditor().setItem("  cv   uji   diketik ");
        isi(p, "txtFieldWeight", "7000");
        isi(p, "txtFactoryWeight", "6900");
        isi(p, "txtRefraction", "15");
        isi(p, "txtPrice", "1150");
        if (!simpanPanel(p)) {
            return false;
        }
        if (jumlahRiwayat(p) != riwayatSebelum + 2) {
            System.out.println("        ejaan berbeda dari nama yang sama malah ditolak");
            return false;
        }

        // (3) Kedua truk lahir, keduanya milik SATU rental.
        int rentalSesudah = dao.listRental().size();
        if (rentalSesudah != rentalSebelum + 1) {
            System.out.println("        ejaan berbeda memecah rental: "
                    + (rentalSesudah - rentalSebelum) + " rental lahir, seharusnya 1");
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
     * Tekan Simpan dari luar layar, dan laporkan kegagalannya sebagai hasil uji yang gagal.
     *
     * <p>Dialog "Tersimpan" tidak bisa tampil tanpa layar, dan HeadlessException yang
     * dilemparkannya BUKAN kegagalan simpan: panel sengaja mengosongkan form dan
     * menyegarkan daftar SEBELUM dialog itu ditampilkan, jadi pekerjaan sudah tuntas
     * saat dialognya gagal tampil. Karena itu hanya HeadlessException yang diterima.
     * Kegagalan lain tetap dilaporkan — kalau dibiarkan naik, seluruh berkas uji ini
     * mati di tengah jalan, sisa pemeriksaannya tidak pernah dijalankan, dan tidak ada
     * satu pun baris hasil yang tercetak. Kegagalan yang tidak terlihat sama
     * bahayanya dengan kegagalan yang lolos.
     */
    private static boolean simpanPanel(PanelTransaction p) {
        try {
            klik(p, "save");
            return true;
        } catch (Exception e) {
            Throwable sebab = e.getCause() == null ? e : e.getCause();
            if (sebab instanceof HeadlessException) {
                return true;
            }
            System.out.println("        simpan gagal: " + sebab);
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

    /** Panggil satu metode tanpa argumen lewat pantulan, dan ambil hasilnya. */
    private static Object ambil(Object target, String method) throws Exception {
        java.lang.reflect.Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        return m.invoke(target);
    }

    /**
     * Berkas CSV harus bisa langsung dijumlahkan di Excel, dan jumlahnya harus sama dengan
     * total yang tertulis di layar.
     *
     * <p>Dua kesalahan yang sama-sama tidak menimbulkan pesan apa pun: angka yang ikut
     * membawa satuannya ("6.350 kg", "Rp 6.158.250") tidak bisa dijumlahkan di Excel
     * sehingga berkasnya jadi tidak berguna, dan berkas yang jumlahnya berbeda dari layar
     * justru menyesatkan orang yang sedang mencocokkan uang - laporan ini dipakai
     * menyetorkan uang, jadi angkanya harus satu keterangan dengan layarnya.
     */
    private static boolean csvSiapDiolah() throws Exception {
        PanelReport p = new PanelReport();
        Object[] isi = ((java.util.List<?>) ambil(p, "isiCsv")).toArray();
        boolean ok = true;

        // Baris judul dicari sebagai baris PERTAMA yang diawali "Tanggal;", bukan
        // baris pertama berkas: pembuka berkas (judul, cakupan, keterangan urutan)
        // bisa bertambah atau berkurang, dan menghitung nomornya membuat pemeriksaan
        // ini ikut rusak setiap kali pembukanya disunting.
        int judul = -1;
        for (int i = 0; i < isi.length; i++) {
            if (isi[i].toString().startsWith("Tanggal;")) {
                judul = i;
                break;
            }
        }
        String judulHarap = "Tanggal;Plat;Rental;Bobot Lapak;Bobot Pabrik;Refraksi;"
                + "Berat Bersih;Susut;Tgl Lunas;Harga;Jumlah Uang";
        if (judul < 0 || !judulHarap.equals(isi[judul])) {
            System.out.println("        baris judul CSV tidak sesuai: "
                    + (judul < 0 ? "(tidak ada)" : isi[judul]));
            ok = false;
        }
        if (judul < 0) {
            // Tanpa baris judul, kolom-kolom data tidak bisa diperiksa satu per satu.
            return false;
        }

        java.math.BigDecimal jumlah = java.math.BigDecimal.ZERO;
        for (int i = judul + 1; i < isi.length; i++) {
            String[] kolom = isi[i].toString().split(";", -1);
            if (kolom.length != 11) {
                System.out.println("        baris " + i + " berisi " + kolom.length
                        + " kolom, seharusnya 11");
                return false;
            }
            // Kolom 8 (Tgl Lunas) adalah tanggal, bukan angka yang dijumlahkan.
            for (int k : new int[]{3, 4, 5, 6, 7, 9, 10}) {
                if (!kolom[k].matches("\\d+")) {
                    System.out.println("        kolom ke-" + k + " baris " + i
                            + " bukan angka polos: \"" + kolom[k]
                            + "\" - angka bersatuan tidak bisa dijumlahkan di Excel");
                    return false;
                }
            }
            // Kolom 7 (Susut) harus bobot lapak dikurangi bobot pabrik. Pembandingnya
            // adalah bobot yang TERTULIS DI LAYAR, bukan kolom 3 dan 4 berkas ini sendiri:
            // menurunkan susut dari dua kolom yang sama-sama ada di berkas membuat
            // pemeriksaannya ikut berpindah kalau kolomnya tertukar - menukar isi kolom 4
            // dan 7 tetap lolos, karena susut yang salah dibandingkan dengan bobot yang
            // sudah tertukar juga. Layar dan berkas harus menulis angka yang sama.
            JTable layarTabel = tabel(p);
            int barisLayar = i - judul - 1;
            java.math.BigDecimal lapak = angkaSel(layarTabel, barisLayar, 3);
            java.math.BigDecimal pabrik = angkaSel(layarTabel, barisLayar, 4);
            java.math.BigDecimal susut = new java.math.BigDecimal(kolom[7]);
            if (lapak != null && pabrik != null
                    && susut.compareTo(lapak.subtract(pabrik)) != 0) {
                System.out.println("        susut baris " + i + " = " + kolom[7]
                        + ", seharusnya bobot lapak - bobot pabrik di layar = "
                        + lapak.subtract(pabrik));
                return false;
            }
            jumlah = jumlah.add(new java.math.BigDecimal(kolom[10]));
        }

        String diLayar = ((JLabel) field(p, "lblTotalAmount")).getText()
                .replace("Rp ", "").replace(".", "");
        if (!jumlah.toPlainString().equals(diLayar)) {
            System.out.println("        jumlah kolom uang di berkas " + jumlah.toPlainString()
                    + " berbeda dari total di layar " + diLayar);
            ok = false;
        }
        return ok;
    }

    /** Angka sebuah sel tampilan ("7.200 kg" -> 7200), atau null kalau selnya kosong. */
    private static java.math.BigDecimal angkaSel(JTable t, int baris, int kolom) {
        if (t == null || baris < 0 || baris >= t.getRowCount() || kolom >= t.getColumnCount()) {
            return null;
        }
        Object nilai = t.getValueAt(baris, kolom);
        if (nilai == null) {
            return null;
        }
        String teks = nilai.toString().replaceAll("[^0-9]", "");
        return teks.isEmpty() ? null : new java.math.BigDecimal(teks);
    }

    /** Isian yang memuat pemisah, tanda petik, atau baris baru harus dibungkus tanda petik. */
    private static boolean isianCsvDibungkus() throws Exception {
        java.lang.reflect.Method kolom = PanelReport.class.getDeclaredMethod("kolom", String.class);
        kolom.setAccessible(true);
        boolean ok = true;
        ok &= periksaKolom(kolom, "CV Mitra; Tani", "\"CV Mitra; Tani\"");
        ok &= periksaKolom(kolom, "CV \"Mitra\" Tani", "\"CV \"\"Mitra\"\" Tani\"");
        ok &= periksaKolom(kolom, "Rental Sinar Jaya", "Rental Sinar Jaya");
        return ok;
    }

    private static boolean periksaKolom(java.lang.reflect.Method kolom, String masuk, String harap)
            throws Exception {
        Object hasil = kolom.invoke(null, masuk);
        if (harap.equals(hasil)) {
            return true;
        }
        System.out.println("        isian \"" + masuk + "\" menjadi " + hasil
                + ", seharusnya " + harap);
        return false;
    }

    /**
     * Judul kolom yang sedang diurut harus memperlihatkan panahnya.
     *
     * <p>Panah itu tidak lagi diwarisi begitu judul kolomnya digambar sendiri oleh
     * aplikasi. Yang hilang kalau panahnya tidak dipasang bukan cuma gambarnya: kolom
     * yang diurut terlihat sama saja dengan yang tidak diurut, jadi kliknya disangka
     * tidak bekerja sama sekali.
     */
    private static boolean panahUrutTergambar(Container panel, int kolomUang) throws Exception {
        JTable t = tabel(panel);
        if (t.getRowSorter() == null) {
            System.out.println("        tabelnya tidak bisa diurut sama sekali");
            return false;
        }
        if (!urutkan(t, kolomUang, SortOrder.DESCENDING)) {
            return false;
        }

        Icon panah = ikonJudul(t, kolomUang);
        if (panah == null) {
            System.out.println("        kolom yang diurut tidak memperlihatkan panah apa pun");
            return false;
        }
        if (panah != UIManager.getIcon("Table.descendingSortIcon")) {
            System.out.println("        panahnya bukan panah bawaan tema, jadi tidak ikut temanya");
            return false;
        }
        if (ikonJudul(t, kolomUang - 7) != null) {
            System.out.println("        kolom yang tidak diurut ikut memperlihatkan panah");
            return false;
        }
        // Arah panahnya harus ikut arah urutannya, bukan selalu panah naik.
        if (!urutkan(t, kolomUang, SortOrder.ASCENDING)) {
            return false;
        }
        if (ikonJudul(t, kolomUang) != UIManager.getIcon("Table.ascendingSortIcon")) {
            System.out.println("        urutan menaik tetap memakai panah menurun");
            return false;
        }
        return true;
    }

    /**
     * Kolom uang harus terurut menurut NILAI angkanya, dan kolom tanggal di tabel yang
     * sama harus terurut menurut nilainya juga.
     *
     * <p>Isi kolomnya sudah diberi awalan "Rp" dan pemisah ribuan, dan pembanding bawaan
     * tabel membandingkan tulisan itu huruf per huruf. Akibatnya "Rp 10.000.000" terurut
     * sebelum "Rp 6.888.500" - angka yang lebih besar dianggap lebih kecil hanya karena
     * tulisannya lebih pendek. Angkanya tetap terbaca benar satu per satu, jadi tidak ada
     * tanda apa pun bahwa urutannya salah.
     *
     * <p>Diperiksa pada tabelnya langsung, bukan lewat berkas CSV, supaya pemeriksaan yang
     * sama bisa dipakai untuk kedua tabel - daftar tersimpan tidak punya ekspor CSV.
     *
     * <p>Kolom tanggal ikut diperiksa dengan alasan yang sama seperti tabelnya yang diuji
     * dua-duanya: memasang pembanding di satu tabel tidak memperbaiki tabel lain, dan
     * urutan tanggal yang salah tetap terlihat wajar karena tanggalnya terbaca benar satu
     * per satu - tidak ada satu pun tanda bahwa kolomnya mengurut menurut tulisannya.
     * Sel kosong dilewati: nilainya memang tidak ada untuk dibandingkan, dan cacat khusus
     * baris belum lunas diperiksa sendiri oleh {@link #urutTglLunasTahanBelumLunas()}.
     */
    private static boolean urutAngkaMenurutNilai(Container panel, int kolomUang, int kolomTanggal)
            throws Exception {
        JTable t = tabel(panel);
        if (t.getRowSorter() == null) {
            System.out.println("        tabelnya tidak bisa diurut sama sekali");
            return false;
        }
        if (!urutkan(t, kolomUang, SortOrder.DESCENDING)) {
            return false;
        }

        java.math.BigDecimal sebelumnya = null;
        for (int i = 0; i < t.getRowCount(); i++) {
            String teks = String.valueOf(t.getValueAt(i, kolomUang)).replaceAll("[^0-9]", "");
            java.math.BigDecimal nilai = new java.math.BigDecimal(teks);
            if (sebelumnya != null && sebelumnya.compareTo(nilai) < 0) {
                System.out.println("        urutan menurunnya tidak berlaku: " + sebelumnya
                        + " lalu " + nilai + " - yang dibandingkan tulisannya, bukan angkanya");
                return false;
            }
            sebelumnya = nilai;
        }

        if (!urutkan(t, kolomTanggal, SortOrder.DESCENDING)) {
            return false;
        }
        java.time.LocalDate tanggalSebelumnya = null;
        for (int i = 0; i < t.getRowCount(); i++) {
            java.time.LocalDate tanggal = kaspe.util.Dates.parse(
                    String.valueOf(t.getValueAt(i, kolomTanggal)));
            if (tanggal == null) {
                continue;
            }
            if (tanggalSebelumnya != null && tanggalSebelumnya.isBefore(tanggal)) {
                System.out.println("        urutan tanggal menurunnya tidak berlaku: "
                        + tanggalSebelumnya + " lalu " + tanggal
                        + " - yang dibandingkan tulisannya, bukan tanggalnya");
                return false;
            }
            tanggalSebelumnya = tanggal;
        }
        return true;
    }

    /** Ikon di judul satu kolom, apa adanya dari penggambar judul kolomnya. */
    private static Icon ikonJudul(JTable t, int kolom) {
        javax.swing.table.TableCellRenderer r =
                t.getColumnModel().getColumn(kolom).getHeaderRenderer();
        if (r == null) {
            r = t.getTableHeader().getDefaultRenderer();
        }
        JLabel l = (JLabel) r.getTableCellRendererComponent(t, "judul", false, false, -1, kolom);
        return l.getIcon();
    }

    /**
     * Mengurutkan tabel tidak boleh menukar isi barisnya.
     *
     * <p>Tabel menyimpan angka yang sudah diberi satuan, jadi ekspor CSV membaca daftar
     * baris yang lain dan mencocokkannya lewat nomor baris tabel. Begitu pengurutan
     * dinyalakan, nomor baris tampilan tidak lagi sama dengan nomor baris data - dan
     * kalau pencocokannya tidak ikut menyesuaikan, setiap baris di berkasnya berisi
     * angka milik catatan LAIN. Angkanya tetap masuk akal satu per satu, jadi tidak ada
     * satu pun pemeriksaan lain yang menangkapnya.
     *
     * <p>Yang dibandingkan SELURUH baris, bukan hanya yang pertama: pemeriksaan yang
     * hanya melihat baris pertama bisa lolos hanya karena kebetulan baris pertama data
     * ujinya memang yang terbesar - dan pemeriksaan yang bisa lolos karena kebetulan
     * lebih buruk daripada tidak ada, karena ia terlihat seperti jaminan.
     */
    private static boolean urutTidakMenukarBaris() throws Exception {
        PanelReport p = new PanelReport();
        JTable t = tabel(p);
        if (t.getRowSorter() == null) {
            System.out.println("        tabel laporan tidak bisa diurut sama sekali");
            return false;
        }
        if (!urutkan(t, 9, SortOrder.DESCENDING)) {
            return false;
        }

        String[] baris = ((java.util.List<?>) ambil(p, "isiCsv")).toArray(new String[0]);
        // Baris data dimulai SETELAH baris judul, bukan di baris pertama: berkasnya
        // kini berjudul, memuat cakupan, dan bisa memuat keterangan urutan di atas
        // tabelnya. Membaca dari baris pertama membuat pembuka berkas ikut dibaca
        // sebagai baris data.
        int judul = -1;
        for (int i = 0; i < baris.length; i++) {
            if (baris[i].startsWith("Tanggal;")) {
                judul = i;
                break;
            }
        }
        if (judul < 0 || baris.length < judul + 2) {
            System.out.println("        tidak ada baris untuk diperiksa");
            return false;
        }
        java.math.BigDecimal sebelumnya = null;
        for (int i = judul + 1; i < baris.length; i++) {
            // Angkanya dibandingkan sebagai deretan digit saja, bukan sebagai angka
            // bersatuan. Kalau berkasnya memuat satuan, itu cacat tersendiri yang sudah
            // diperiksa pemeriksaan CSV di atas - di sini yang diperiksa hanya apakah
            // baris berkas berpasangan dengan baris layar yang sama.
            String diBerkas = baris[i].split(";", -1)[10].replaceAll("[^0-9]", "");
            // Yang tertulis di layar pada baris yang sama, angkanya saja. Kolom uang
            // di berkas menjadi 10 karena Susut menyisip di kolom 7; di tabel layar
            // kolomnya tetap 9 - Susut hanya ditulis ke berkas.
            String diLayar = String.valueOf(t.getValueAt(i - judul - 1, 9))
                    .replaceAll("[^0-9]", "");
            if (!diBerkas.equals(diLayar)) {
                System.out.println("        baris ke-" + i + " berkas berisi " + diBerkas
                        + " padahal di layar baris itu berisi " + diLayar
                        + " - isinya tertukar dengan catatan lain");
                return false;
            }
            if (diBerkas.isEmpty()) {
                System.out.println("        baris ke-" + i + " berkas tidak memuat jumlah uang");
                return false;
            }
            java.math.BigDecimal nilai = new java.math.BigDecimal(diBerkas);
            if (sebelumnya != null && sebelumnya.compareTo(nilai) < 0) {
                System.out.println("        urutan menurunnya tidak berlaku: " + sebelumnya
                        + " lalu " + nilai);
                return false;
            }
            sebelumnya = nilai;
        }

        // Kolom tanggal diuji juga, bukan hanya kolom uang: keduanya memakai pembanding
        // sendiri-sendiri, jadi memasang yang satu tidak memperbaiki yang lain - dan
        // pemeriksaan yang hanya melihat kolom uang tidak akan menangkap tanggal yang
        // urutannya salah.
        if (!urutkan(t, 0, SortOrder.DESCENDING)) {
            return false;
        }
        // Berkasnya dibaca ULANG setelah urutannya diganti: isinya mengikuti urutan tabel
        // yang sedang berlaku, jadi daftar yang dibaca sebelum pengurutan sudah basi.
        baris = ((java.util.List<?>) ambil(p, "isiCsv")).toArray(new String[0]);
        java.time.LocalDate tanggalSebelumnya = null;
        for (int i = judul + 1; i < baris.length; i++) {
            String diBerkas = baris[i].split(";", -1)[0];
            String diLayar = String.valueOf(t.getValueAt(i - judul - 1, 0));
            if (!diBerkas.equals(diLayar)) {
                System.out.println("        baris ke-" + i + " berkas bertanggal " + diBerkas
                        + " padahal di layar baris itu bertanggal " + diLayar
                        + " - isinya tertukar dengan catatan lain");
                return false;
            }
            java.time.LocalDate tanggal = kaspe.util.Dates.parse(diBerkas);
            if (tanggal == null) {
                System.out.println("        baris ke-" + i + " berkas bertuliskan \"" + diBerkas
                        + "\", bukan tanggal");
                return false;
            }
            if (tanggalSebelumnya != null && tanggalSebelumnya.isBefore(tanggal)) {
                System.out.println("        urutan tanggal menurunnya tidak berlaku: "
                        + tanggalSebelumnya + " lalu " + tanggal);
                return false;
            }
            tanggalSebelumnya = tanggal;
        }
        return true;
    }

    /**
     * Pengurutan kolom Tgl Lunas harus tahan terhadap baris yang belum lunas.
     *
     * <p>Baris belum lunas tidak memuat tanggal pembayaran sama sekali. Pengurut yang
     * membandingkan tanggalnya tanpa memikirkan sel kosong melempar kesalahan pada
     * saat tabelnya diurut - dan di Java 8, pengurut yang melempar membuat seluruh
     * pengurutannya macet, bukan cuma satu klik yang gagal. Data uji yang selalu lunas
     * membuat cacat itu tidak pernah kelihatan, karena itu satu baris belum lunas
     * sengaja ditanam di {@link #fillData()}.
     *
     * <p>Diperiksa dua arah, menaik dan menurun. Sel kosong harus berbaris di SATU
     * ujung (yang tercampur berarti sel kosong dibandingkan dengan hasil tak menentu),
     * dan tanggal yang ada harus benar-benar terurut menurut arahnya.
     */
    private static boolean urutTglLunasTahanBelumLunas() throws Exception {
        PanelReport p = new PanelReport();
        JTable t = tabel(p);
        if (t.getRowSorter() == null) {
            System.out.println("        tabel laporan tidak bisa diurut sama sekali");
            return false;
        }
        // Data yang sedang tampil harus memuat baris belum lunas: tanpa itu,
        // pemeriksaan ini memeriksa keadaan yang tidak pernah terjadi.
        boolean adaKosong = false;
        for (int i = 0; i < t.getRowCount(); i++) {
            if (String.valueOf(t.getValueAt(i, 7)).trim().isEmpty()) {
                adaKosong = true;
                break;
            }
        }
        if (!adaKosong) {
            System.out.println("        data uji tidak memuat baris belum lunas,"
                    + " tidak ada sel kosong untuk diperiksa");
            return false;
        }

        for (SortOrder arah : new SortOrder[]{SortOrder.ASCENDING, SortOrder.DESCENDING}) {
            try {
                t.getRowSorter().setSortKeys(java.util.Collections.singletonList(
                        new RowSorter.SortKey(7, arah)));
            } catch (RuntimeException e) {
                System.out.println("        mengurut kolom Tgl Lunas " + sebutArah(arah)
                        + " melempar: " + e);
                return false;
            }

            java.util.List<java.time.LocalDate> tanggal = new java.util.ArrayList<>();
            int perubahanKosong = 0;
            boolean kosongSebelumnya = String.valueOf(t.getValueAt(0, 7)).trim().isEmpty();
            if (kosongSebelumnya) {
                tanggal.add(null);
            } else {
                tanggal.add(kaspe.util.Dates.parse(String.valueOf(t.getValueAt(0, 7))));
            }
            for (int i = 1; i < t.getRowCount(); i++) {
                String teks = String.valueOf(t.getValueAt(i, 7)).trim();
                boolean kosong = teks.isEmpty();
                if (kosong != kosongSebelumnya) {
                    perubahanKosong++;
                }
                kosongSebelumnya = kosong;
                tanggal.add(kosong ? null : kaspe.util.Dates.parse(teks));
            }

            // Sel kosong berbaris di satu ujung: perpindahan kosong-terisi lebih dari
            // satu berarti sel kosong tercampur di tengah baris yang berisi.
            if (perubahanKosong > 1) {
                System.out.println("        sel kosong tercampur saat diurut " + sebutArah(arah)
                        + ", bukan berbaris di satu ujung");
                return false;
            }
            for (int i = 1; i < tanggal.size(); i++) {
                java.time.LocalDate sebelum = tanggal.get(i - 1);
                java.time.LocalDate kini = tanggal.get(i);
                if (sebelum == null || kini == null) {
                    continue;
                }
                boolean menaikLagi = arah == SortOrder.ASCENDING
                        ? sebelum.isAfter(kini) : sebelum.isBefore(kini);
                if (menaikLagi) {
                    System.out.println("        urutan tanggal " + sebutArah(arah)
                            + "nya tidak berlaku: " + sebelum + " lalu " + kini);
                    return false;
                }
            }
        }

        // Urutan barisnya sendiri tidak cukup: pengurut yang membandingkan tanggal
        // mentah-mentah bisa saja tidak melempar saat diurutkan di sini, lalu barulah
        // gagal di tangan pengguna. Pembandingnya karena itu dipanggil langsung dengan
        // sel kosong - persis nilai yang muncul di kolom ini untuk catatan belum lunas.
        if (!(t.getRowSorter() instanceof javax.swing.table.TableRowSorter)) {
            System.out.println("        pengurutnya bukan TableRowSorter, pembandingnya tidak bisa diperiksa");
            return false;
        }
        javax.swing.table.TableRowSorter<?> pengurut =
                (javax.swing.table.TableRowSorter<?>) t.getRowSorter();
        @SuppressWarnings("unchecked")
        java.util.Comparator<Object> banding =
                (java.util.Comparator<Object>) pengurut.getComparator(7);
        if (banding == null) {
            System.out.println("        kolom Tgl Lunas tidak punya pembanding sendiri,"
                    + " jadi sel kosong dibandingkan sebagai tulisan");
            return false;
        }
        for (Object[] pasangan : new Object[][]{{"", "16-07-2026"}, {"16-07-2026", ""},
                {"", ""}, {"05-10-2026", "12-09-2026"}}) {
            try {
                banding.compare(pasangan[0], pasangan[1]);
            } catch (RuntimeException e) {
                System.out.println("        pembanding Tgl Lunas melempar untuk sel \""
                        + pasangan[0] + "\" dan \"" + pasangan[1] + "\": " + e);
                return false;
            }
        }
        if (banding.compare("05-10-2026", "12-09-2026") <= 0) {
            System.out.println("        pembanding Tgl Lunas menaruh 05-10-2026 sebelum"
                    + " 12-09-2026 - yang dibandingkan tulisannya, bukan tanggalnya");
            return false;
        }
        return true;
    }

    /** Sebutan arah pengurutan untuk pesan kegagalan. */
    private static String sebutArah(SortOrder arah) {
        return arah == SortOrder.ASCENDING ? "menaik" : "menurun";
    }

    /**
     * Judul kolom harus tetap utuh saat panah penanda urutnya tampil.
     *
     * <p>Panah penanda urut menambah sekitar 10px di sisi judul kolom yang sedang
     * diurut. Judul yang lebarnya pas-pasan tanpa panah terpotong begitu panahnya
     * muncul - dan tabel laporan tidak punya penggeser mendatar, jadi yang terpotong
     * tidak bisa dilihat dengan menggeser. Diukur pada lebar jendela MINIMUM karena
     * di sanalah ruangnya paling sempit, dengan cara penyusunan yang sama seperti
     * pemeriksaan lebar kolom lainnya.
     *
     * <p>Setiap kolom diukur bersama panahnya, bukan hanya kolom yang kebetulan
     * disorot pemeriksaan: pengguna bisa mengklik kolom mana pun untuk mengurutnya,
     * jadi judul yang pas-pasan di kolom mana pun adalah cacat yang sama.
     */
    private static boolean judulMuatBersamaPanahUrut() throws Exception {
        PagePanel halaman = new PagePanel();
        JPanel layar = PagePanel.shell(halaman);
        PanelReport panel = new PanelReport();
        halaman.showPanel(panel, "Laporan", "Rekap penjualan per periode.");
        layar.setSize(kaspe.ui.MainFrame.LEBAR_MINIMUM, 760);
        for (int i = 0; i < 3; i++) {
            layar.doLayout();
            layoutDeep(layar);
        }

        JTable t = tabel(panel);
        if (t.getRowSorter() == null) {
            System.out.println("        tabel laporan tidak bisa diurut sama sekali");
            return false;
        }
        if (!urutkan(t, 0, SortOrder.DESCENDING)) {
            return false;
        }

        Icon panah = UIManager.getIcon("Table.descendingSortIcon");
        if (panah == null) {
            System.out.println("        tema tidak menyediakan panah urut, tidak ada yang bisa diukur");
            return false;
        }
        boolean utuh = true;
        for (int kolom = 0; kolom < t.getColumnCount(); kolom++) {
            javax.swing.table.TableColumn col = t.getColumnModel().getColumn(kolom);
            if (col.getMaxWidth() == 0) {
                continue;
            }
            javax.swing.table.TableCellRenderer r = col.getHeaderRenderer() != null
                    ? col.getHeaderRenderer() : t.getTableHeader().getDefaultRenderer();
            java.awt.Component k = r.getTableCellRendererComponent(
                    t, col.getHeaderValue(), false, false, -1, kolom);
            ((JLabel) k).setIcon(panah);
            int butuh = k.getPreferredSize().width;
            if (butuh > col.getWidth()) {
                System.out.println("        judul kolom \"" + col.getHeaderValue()
                        + "\" butuh " + butuh + "px bersama panah urut,"
                        + " kolomnya hanya " + col.getWidth() + "px");
                utuh = false;
            }
        }
        return utuh;
    }

    /**
     * Rekap per rental harus berjumlah sama dengan total yang tertulis di layar.
     *
     * <p>Rekap inilah yang dipakai menyetorkan uang per pemilik. Baris "Jumlah" yang
     * tidak sepadan dengan total di layar berarti salah satu di antaranya salah - dan
     * dua-duanya dipakai orang yang berbeda untuk uang yang sama, jadi selisihnya baru
     * ketahuan saat uangnya sudah berpindah tangan.
     */
    private static boolean rekapSamaDenganTotalLayar() throws Exception {
        PanelReport p = new PanelReport();
        java.util.List<Object[]> rekap = rekap(p);
        if (rekap.isEmpty()) {
            System.out.println("        rekap per rental kosong");
            return false;
        }
        Object[] total = rekap.get(rekap.size() - 1);
        if (!"Jumlah".equals(total[0])) {
            System.out.println("        baris terakhir rekap bernama \"" + total[0]
                    + "\", seharusnya \"Jumlah\"");
            return false;
        }
        boolean ok = true;
        java.math.BigDecimal uang = angkaLayar(((JLabel) field(p, "lblTotalAmount")).getText());
        java.math.BigDecimal berat = angkaLayar(((JLabel) field(p, "lblTotalWeight")).getText());
        if (((java.math.BigDecimal) total[3]).compareTo(uang) != 0) {
            System.out.println("        total uang rekap " + total[3]
                    + " berbeda dari total di layar " + uang.toPlainString());
            ok = false;
        }
        if (((java.math.BigDecimal) total[2]).compareTo(berat) != 0) {
            System.out.println("        total berat rekap " + total[2]
                    + " berbeda dari total di layar " + berat.toPlainString());
            ok = false;
        }
        JTable t = tabel(p);
        if (((Integer) total[1]).intValue() != t.getRowCount()) {
            System.out.println("        jumlah nota rekap " + total[1]
                    + " berbeda dari baris yang tampil " + t.getRowCount());
            ok = false;
        }
        return ok;
    }

    /**
     * Rekap per rental harus dihitung dari baris yang SEDANG TAMPIL, jadi setelah
     * satu rental disaring, rekapnya tidak boleh memuat rental lain.
     *
     * <p>Rekap yang menghitung seluruh data - bukan hasil saringan - tetap terlihat
     * benar: angkanya memang jumlah yang sah, hanya bukan jumlah yang sedang
     * disaring. Uang yang disetorkan dari rekap macam itu pasti salah tanpa satu pun
     * tanda di layar.
     */
    private static boolean rekapMenghormatiSaringan() throws Exception {
        PanelReport p = new PanelReport();
        JComboBox<?> cmbRental = (JComboBox<?>) field(p, "cmbRental");
        String disaring = "Rental Bumi Ayu";
        Rental pilihan = null;
        for (int i = 0; i < cmbRental.getItemCount(); i++) {
            if (cmbRental.getItemAt(i) instanceof Rental
                    && disaring.equals(((Rental) cmbRental.getItemAt(i)).getRentalName())) {
                pilihan = (Rental) cmbRental.getItemAt(i);
                break;
            }
        }
        if (pilihan == null) {
            System.out.println("        rental \"" + disaring + "\" tidak ada di saringan");
            return false;
        }
        cmbRental.setSelectedItem(pilihan);
        klik(p, "reload");

        boolean ok = true;
        for (Object[] baris : rekap(p)) {
            if ("Jumlah".equals(baris[0])) {
                continue;
            }
            if (!disaring.equals(baris[0])) {
                System.out.println("        rekap tersaring \"" + disaring
                        + "\" memuat rental lain: \"" + baris[0] + "\"");
                ok = false;
            }
        }
        return ok;
    }

    /** Rekap per rental halaman laporan, lewat pantulan. */
    private static java.util.List<Object[]> rekap(PanelReport p) throws Exception {
        return (java.util.List<Object[]>) ambil(p, "rekapPerRental");
    }

    /** Angka dari tulisan di layar: buang "Rp", titik ribuan, dan satuannya. */
    private static java.math.BigDecimal angkaLayar(String teks) {
        return new java.math.BigDecimal(teks.replaceAll("[^0-9]", ""));
    }

    /**
     * Tombol "Bulan ini" harus memasang rentang bulan berjalan DAN memuat ulang
     * tabelnya.
     *
     * <p>Kesalahan yang senyap: kotak tanggalnya berubah tetapi tabelnya tidak dimuat
     * ulang, sehingga baris dan totalnya masih periode lama padahal kotaknya menulis
     * bulan ini - orang yang membacanya mencocokkan uangnya dengan periode yang salah.
     * Data uji sengaja menjangkau bulan sebelum hari ini, jadi jumlah barisnya
     * benar-benar berubah saat rentangnya dipersempit.
     */
    private static boolean rentangCepatBulanIni() throws Exception {
        PanelReport p = new PanelReport();
        JButton tombol = tombolBerteks(p, "Bulan ini");
        if (tombol == null) {
            System.out.println("        tombol 'Bulan ini' tidak ada di halaman laporan");
            return false;
        }
        String labelSebelum = ((JLabel) field(p, "lblRowCount")).getText();
        tombol.doClick();

        boolean ok = true;
        java.time.LocalDate hariIni = java.time.LocalDate.now();
        java.time.LocalDate dari = tanggalSpinner(p, "spFrom");
        java.time.LocalDate sampai = tanggalSpinner(p, "spTo");
        if (!hariIni.withDayOfMonth(1).equals(dari)) {
            System.out.println("        'Bulan ini' memasang Dari " + dari
                    + ", seharusnya " + hariIni.withDayOfMonth(1));
            ok = false;
        }
        if (!hariIni.equals(sampai)) {
            System.out.println("        'Bulan ini' memasang Sampai " + sampai
                    + ", seharusnya " + hariIni);
            ok = false;
        }
        javax.swing.table.DefaultTableModel model =
                (javax.swing.table.DefaultTableModel) field(p, "model");
        String label = ((JLabel) field(p, "lblRowCount")).getText();
        if (label.equals(labelSebelum)) {
            System.out.println("        tabel tidak dimuat ulang: jumlah baris masih \""
                    + label + "\"");
            ok = false;
        }
        if (!label.equals(model.getRowCount() + " baris")) {
            System.out.println("        label jumlah baris menulis \"" + label
                    + "\", tabelnya " + model.getRowCount() + " baris");
            ok = false;
        }
        return ok;
    }

    /**
     * Tombol rentang cepat memasang rentangnya ke kedua kotak tanggal.
     *
     * <p>Diperiksa untuk tiap tombol, bukan hanya salah satu: ketiganya memasang periode
     * yang dipakai orang mencocokkan uang, dan salah rentang di situ tidak berbunyi -
     * kotaknya tetap berisi tanggal yang masuk akal, hanya bukan rentang yang dimaksud.
     */
    private static boolean rentangCepat(String nama, java.time.LocalDate harapDari,
            java.time.LocalDate harapSampai) throws Exception {
        PanelReport p = new PanelReport();
        JButton tombol = tombolBerteks(p, nama);
        if (tombol == null) {
            System.out.println("        tombol '" + nama + "' tidak ada di halaman laporan");
            return false;
        }
        tombol.doClick();
        java.time.LocalDate dari = tanggalSpinner(p, "spFrom");
        java.time.LocalDate sampai = tanggalSpinner(p, "spTo");
        if (!harapDari.equals(dari)) {
            System.out.println("        '" + nama + "' memasang Dari " + dari
                    + ", seharusnya " + harapDari);
            return false;
        }
        if (!harapSampai.equals(sampai)) {
            System.out.println("        '" + nama + "' memasang Sampai " + sampai
                    + ", seharusnya " + harapSampai);
            return false;
        }
        return true;
    }

    /** "Hari ini" harus memasang kedua kotak tanggal ke tanggal hari ini. */
    private static boolean rentangCepatHariIni() throws Exception {
        java.time.LocalDate hariIni = java.time.LocalDate.now();
        return rentangCepat("Hari ini", hariIni, hariIni);
    }

    /** "Semua" harus memasang rentang dari transaksi terawal sampai yang terakhir. */
    private static boolean rentangCepatSemua() throws Exception {
        kaspe.dao.TransactionDao dao = new kaspe.dao.TransactionDao();
        java.time.LocalDate terawal = dao.earliestDate();
        java.time.LocalDate terakhir = dao.latestDate();
        if (terawal == null || terakhir == null) {
            System.out.println("        data uji tidak punya transaksi, pemeriksaan ini tidak berlaku");
            return false;
        }
        return rentangCepat("Semua", terawal, terakhir);
    }

    /** Tanggal yang tertulis di nilai model sebuah kotak tanggal. */
    private static java.time.LocalDate tanggalSpinner(Object target, String nama) throws Exception {
        java.util.Date d = (java.util.Date) ((JSpinner) field(target, nama)).getValue();
        return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * Urutan nama rental harus mengikuti perbandingan yang tidak bergantung bahasa
     * komputer.
     *
     * <p>Pembanding yang memakai aturan abjad komputer bisa mengurut nama yang sama
     * dengan hasil berbeda di komputer yang berbeda, sehingga rekap yang dicetak dari
     * dua komputer tidak bisa dicocokkan. Diurutkan menaik lalu diperiksa bahwa
     * urutannya tidak pernah turun menurut {@code String.compareToIgnoreCase},
     * yang hasilnya sama di komputer mana pun.
     */
    private static boolean urutTeksTegar() throws Exception {
        PanelReport p = new PanelReport();
        JTable t = tabel(p);
        if (t.getRowSorter() == null) {
            System.out.println("        tabel laporan tidak bisa diurut sama sekali");
            return false;
        }
        if (!urutkan(t, 2, SortOrder.ASCENDING)) {
            return false;
        }
        String sebelumnya = null;
        for (int i = 0; i < t.getRowCount(); i++) {
            String nama = String.valueOf(t.getValueAt(i, 2));
            if (sebelumnya != null && sebelumnya.compareToIgnoreCase(nama) > 0) {
                System.out.println("        urutan nama rental turun: \"" + sebelumnya
                        + "\" lalu \"" + nama + "\"");
                return false;
            }
            sebelumnya = nama;
        }

        // Urutan barisnya sendiri TIDAK cukup membuktikan pembandingnya tidak bergantung
        // bahasa komputer: pada nama rental yang ada di data uji, urutan menurut aturan
        // bahasa komputer kebetulan sama dengan urutan menurut abjad tetap - jadi
        // pemeriksaan di atas tetap hijau walaupun pembandingnya tidak terpasang sama
        // sekali. Yang membedakan keduanya adalah huruf besar-kecil yang letaknya
        // berjauhan di abjad: aturan bahasa komputer mengabaikan besar-kecil huruf
        // sehingga "a" mendahului "Z", sedangkan pembanding tetap menaruh "Z" lebih dulu
        // (huruf besarnya bernilai lebih kecil). Pembandingnya dipanggil langsung dengan
        // dua nilai itu.
        if (!(t.getRowSorter() instanceof javax.swing.table.TableRowSorter)) {
            System.out.println("        pengurutnya bukan TableRowSorter, pembandingnya tidak bisa diperiksa");
            return false;
        }
        javax.swing.table.TableRowSorter<?> pengurut =
                (javax.swing.table.TableRowSorter<?>) t.getRowSorter();
        @SuppressWarnings("unchecked")
        java.util.Comparator<Object> banding =
                (java.util.Comparator<Object>) pengurut.getComparator(2);
        if (banding == null) {
            System.out.println("        kolom nama rental tidak punya pembanding sendiri,"
                    + " jadi urutannya ikut aturan bahasa komputer");
            return false;
        }
        if (banding.compare("BE 1", "BE1") >= 0) {
            System.out.println("        pembanding nama mengabaikan spasi seperti aturan bahasa"
                    + " komputer (\"BE 1\" tidak mendahului \"BE1\") - hasilnya berbeda antar komputer");
            return false;
        }
        return true;
    }

    /**
     * Urutkan satu kolom, dan laporkan kalau pengurutnya melempar.
     *
     * <p>Pengurutan bisa gagal karena datanya, bukan karena kodenya: pembanding yang
     * membandingkan tanggal atau angka mentah-mentah mati begitu bertemu sel kosong, dan
     * sel kosong itu keadaan yang nyata (catatan belum lunas tidak punya tanggal lunas).
     * Tanpa dibungkus, kegagalan itu melempar keluar dari uji sehingga seluruh uji
     * berhenti sebelum mencetak hasilnya - pemeriksaannya jadi terlihat seperti uji yang
     * rusak, bukan seperti cacat yang ketahuan.
     */
    private static boolean urutkan(JTable t, int kolom, SortOrder arah) {
        try {
            t.getRowSorter().setSortKeys(java.util.Collections.singletonList(
                    new RowSorter.SortKey(kolom, arah)));
            return true;
        } catch (RuntimeException e) {
            System.out.println("        mengurut kolom " + kolom + " " + sebutArah(arah)
                    + " melempar: " + e);
            return false;
        }
    }

    /** Tombol pertama berteks itu di dalam wadah, dicari sampai anak terdalam, atau null. */
    private static JButton tombolBerteks(Container c, String teks) {
        for (Component anak : c.getComponents()) {
            if (anak instanceof JButton && teks.equals(((JButton) anak).getText())) {
                return (JButton) anak;
            }
            if (anak instanceof Container) {
                JButton hasil = tombolBerteks((Container) anak, teks);
                if (hasil != null) {
                    return hasil;
                }
            }
        }
        return null;
    }

    /** Ambil satu field lewat pantulan. */
    private static Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /** Tabel utama halaman: satu-satunya tabel di dalam susunannya, dicari lewat susunannya. */
    private static JTable tabel(Container panel) {
        return (JTable) cariDi(panel, JTable.class);
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
    /**
     * Label jumlah baris di laporan harus benar-benar terisi.
     *
     * <p>Labelnya pernah berhenti diisi tanpa ada yang menyadari: baris pengisiannya
     * tertimpa blok lain saat berkas ini disunting, sehingga laporannya terus menulis
     * "0 baris" walaupun tabelnya penuh. Angka itu dibaca orang yang mencocokkan uang,
     * jadi salahnya tidak boleh lolos lagi.
     */
    private static boolean jumlahBarisLaporanIkutTerisi() throws Exception {
        PanelReport panel = new PanelReport();
        klik(panel, "reload");
        javax.swing.table.DefaultTableModel model =
                (javax.swing.table.DefaultTableModel) field(panel, "model");
        String teks = String.valueOf(((javax.swing.JLabel) field(panel, "lblRowCount")).getText());
        int tampil = model.getRowCount();
        boolean cocok = teks.equals(tampil + " baris");
        if (!cocok) {
            System.out.println("        label menulis \"" + teks + "\", seharusnya \""
                    + tampil + " baris\"");
        }
        return cocok && tampil > 0;
    }

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
     * Berkas CSV harus menyebut cakupannya sendiri dan urutan yang sedang berlaku.
     *
     * <p>Berkas ini dokumen yang sama dengan kertas: dipakai menyetorkan uang. Nama
     * berkasnya bisa diganti orang dan memakai tanggal EKSPOR, bukan periode laporannya -
     * laporan Januari yang diekspor bulan Oktober akan bernama Oktober. Karena itu periode,
     * saringan, dan urutannya harus tertulis DI DALAM berkasnya. Cetakan sebagian data yang
     * tidak menyebut bagiannya tidak bisa dibedakan dari daftar lengkap, dan itu yang bikin
     * uangnya salah setor.
     */
    private static boolean csvMenyebutCakupannya() throws Exception {
        PanelReport p = new PanelReport();
        klik(p, "reload");
        java.util.List<?> isi = (java.util.List<?>) ambil(p, "isiCsv");
        if (isi.size() < 3) {
            System.out.println("        berkas CSV hanya " + isi.size() + " baris");
            return false;
        }
        // Tanpa saringan dan tanpa pengurutan: baris kedua harus sama dengan cakupan
        // yang tertulis di kaki cetak, dan belum boleh menyebut urutan.
        String kaki = kakiCetak(p);
        String cakupanKertas = kaki.split("  ·  ")[0];
        String cakupanBerkas = String.valueOf(isi.get(1));
        if (!cakupanBerkas.equals(cakupanKertas)) {
            System.out.println("        baris cakupan berkas \"" + cakupanBerkas
                    + "\" berbeda dari kaki cetak \"" + cakupanKertas + "\"");
            return false;
        }
        for (Object baris : isi) {
            if (String.valueOf(baris).startsWith("Urut: ")) {
                System.out.println("        berkas menyebut urutan padahal tabelnya belum diurut");
                return false;
            }
        }

        // Dengan saringan rental: cakupannya ikut tertulis di berkas.
        JComboBox<?> cmb = (JComboBox<?>) field(p, "cmbRental");
        for (int i = 1; i < cmb.getItemCount(); i++) {
            Object item = cmb.getItemAt(i);
            if (item instanceof Rental) {
                cmb.setSelectedIndex(i);
                klik(p, "reload");
                String nama = ((Rental) item).getRentalName();
                String cakupan = String.valueOf(((java.util.List<?>) ambil(p, "isiCsv")).get(1));
                if (!cakupan.contains("Rental: \"" + nama + "\"")) {
                    System.out.println("        saringan rental \"" + nama
                            + "\" tidak tertulis di cakupan berkas: \"" + cakupan + "\"");
                    return false;
                }
                break;
            }
        }

        // Dengan pengurutan: berkas menyebut kolom dan arahnya.
        JTable t = tabel(p);
        if (!urutkan(t, 9, SortOrder.DESCENDING)) {
            return false;
        }
        boolean adaUrut = false;
        for (Object baris : (java.util.List<?>) ambil(p, "isiCsv")) {
            String teks = String.valueOf(baris);
            if (teks.startsWith("Urut: ") && teks.contains("Jumlah Uang")) {
                adaUrut = true;
            }
        }
        if (!adaUrut) {
            System.out.println("        berkas tidak menyebut urutan yang sedang berlaku");
            return false;
        }
        return true;
    }

    /**
     * Kaki cetak harus menyebut urutan yang sedang berlaku, dan tidak menyebut apa-apa
     * kalau tabelnya belum diurut.
     *
     * <p>Urutan layar ikut ke kertas: pencetakan menggambar tabel apa adanya. Kertas ini
     * dipakai mencocokkan uang, dan urutan adalah bagian dari keterangan itu - sama seperti
     * periode dan saringan yang sudah lebih dulu ditulis di kaki.
     */
    private static boolean kakiCetakMenyebutUrutan() throws Exception {
        PanelReport p = new PanelReport();
        klik(p, "reload");
        if (kakiCetak(p).contains("Urut:")) {
            System.out.println("        kaki cetak menyebut urutan padahal tabelnya belum diurut");
            return false;
        }
        JTable t = tabel(p);
        if (!urutkan(t, 9, SortOrder.DESCENDING)) {
            return false;
        }
        String kaki = kakiCetak(p);
        if (!kaki.contains("Urut: Jumlah Uang")) {
            System.out.println("        kaki cetak tidak menyebut kolom yang diurut: \"" + kaki + "\"");
            return false;
        }
        if (!kaki.contains("\u2193")) {
            System.out.println("        kaki cetak tidak menyebut arah urutannya: \"" + kaki + "\"");
            return false;
        }
        return true;
    }

    /**
     * Memasang pembanding pada tabel yang belum punya pengurut harus menyalakan
     * pengurutnya sendiri, bukan diam-diam tidak memasang apa pun.
     *
     * <p>Kejadian itu sudah pernah lolos: pembanding dipasang sebelum
     * {@code setAutoCreateRowSorter(true)}, sehingga pemasangannya tidak terjadi dan
     * tabelnya mengurut menurut tulisan tanpa satu pun tanda. Memeriksanya di sini
     * memakai tabel kosong tanpa pengurut - bukan lewat halaman, karena halaman yang
     * urutan pemanggilannya sudah benar tetap lulus walaupun penjagaan ini hilang.
     */
    private static boolean sortAngkaMenyalakanPengurut() throws Exception {
        JTable t = new JTable(new javax.swing.table.DefaultTableModel(
                new Object[]{"Jumlah Uang"}, 1));
        Theme.styleTable(t);
        if (t.getRowSorter() != null) {
            System.out.println("        tabel baru sudah punya pengurut, pemeriksaan ini tidak berlaku");
            return false;
        }
        Theme.sortAngka(t, 0);
        if (!(t.getRowSorter() instanceof javax.swing.table.TableRowSorter)) {
            System.out.println("        memasang pembanding tidak menyalakan pengurutnya,"
                    + " jadi pembandingnya hilang tanpa suara");
            return false;
        }
        @SuppressWarnings("unchecked")
        java.util.Comparator<Object> banding = (java.util.Comparator<Object>)
                ((javax.swing.table.TableRowSorter<?>) t.getRowSorter()).getComparator(0);
        if (banding == null) {
            System.out.println("        pengurutnya menyala tetapi pembandingnya tidak terpasang");
            return false;
        }
        if (banding.compare("Rp 10.000.000", "Rp 6.888.500") <= 0) {
            System.out.println("        pembandingnya tidak membandingkan nilai angkanya");
            return false;
        }
        return true;
    }

    /**
     * Kaki cetakan harus menuliskan saringan rental/plat yang benar-benar diterapkan
     * ke tabel, dan tidak menuliskan apa-apa kalau tidak ada saringan.
     *
     * <p>Kertas laporan dipakai untuk mencocokkan uang dengan pemilik rental, jadi
     * cetakan yang tersaring rental tetapi kakinya hanya menulis periode bisa
     * disangka daftar lengkap — totalnya memang benar, tapi hanya untuk sebagian
     * data. Plat ditulis dalam bentuk yang sudah diseragamkan, sama seperti yang
     * dipakai mencocokkan barisnya, supaya yang tercetak bisa dicari kembali.
     */
    private static boolean saringanKakiCetakTertera() throws Exception {
        PanelReport panel = new PanelReport();
        klik(panel, "reload");

        // (a) Tanpa saringan: tidak boleh ada segmen "Rental:" maupun "Plat:"
        // sama sekali, bukan segmen kosong seperti "Rental: Semua".
        String polos = kakiCetak(panel);
        boolean tanpaSegmen = !polos.contains("Rental:") && !polos.contains("Plat:");
        if (!tanpaSegmen) {
            System.out.println("        kaki cetak tanpa saringan tertulis '" + polos
                    + "', seharusnya tanpa segmen Rental:/Plat:");
        }

        // (b) Rental dipilih di saringan: kaki menuliskan namanya.
        JComboBox<?> cmbRental = (JComboBox<?>) field(panel, "cmbRental");
        Rental dipilih = (Rental) cmbRental.getItemAt(1);
        cmbRental.setSelectedIndex(1);
        klik(panel, "reload");
        String kakiRental = kakiCetak(panel);
        // Nama saringan dibungkus tanda petik ganda di kaki: nama rental bisa memuat
        // titik tengah pemisah segmen, jadi tanpa petik ujung namanya tidak terbaca.
        boolean rentalTertera = kakiRental.contains(
                "Rental: \"" + dipilih.getRentalName() + "\"");
        if (!rentalTertera) {
            System.out.println("        kaki cetak tersaring rental tertulis '" + kakiRental
                    + "', seharusnya memuat 'Rental: \"" + dipilih.getRentalName() + "\"'");
        }

        // (c) Kotak rental dikembalikan ke "Semua rental", sepotong plat diketik
        // seadanya: kaki menuliskan istilah carinya yang sudah diseragamkan,
        // bukan mentahnya — "be 8009" tidak bisa dicocokkan orang dengan
        // "BE 8009 CF" yang tercetak di tabel.
        cmbRental.setSelectedIndex(0);
        JTextField txtPlat = (JTextField) field(panel, "txtPlat");
        txtPlat.setText("  be 8009  cf ");
        klik(panel, "reload");
        String kakiPlat = kakiCetak(panel);
        // Plat juga dibungkus tanda petik ganda, sama seperti nama rental di atas.
        boolean platTertera = kakiPlat.contains("Plat: \"BE 8009 CF\"")
                && !kakiPlat.contains("Rental:");
        if (!platTertera) {
            System.out.println("        kaki cetak tersaring plat tertulis '" + kakiPlat
                    + "', seharusnya memuat 'Plat: \"BE 8009 CF\"'");
        }

        // (d) Saringan dikosongkan lagi: segmennya hilang kembali, bukan menempel.
        txtPlat.setText("");
        cmbRental.setSelectedIndex(0);
        klik(panel, "reload");
        String bersihLagi = kakiCetak(panel);
        boolean polosLagi = !bersihLagi.contains("Rental:") && !bersihLagi.contains("Plat:");
        if (!polosLagi) {
            System.out.println("        kaki cetak setelah saringan dihapus tertulis '" + bersihLagi
                    + "', seharusnya kembali tanpa segmen Rental:/Plat:");
        }
        return tanpaSegmen && rentalTertera && platTertera && polosLagi;
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

        // Satu baris dengan angka BERDIGIT BEDA dari baris lainnya. Baris-baris di atas
        // berjumlah tujuh digit (sekitar Rp 6-7 juta), baris ini delapan digit (di atas
        // sepuluh juta). Baris ini ada justru supaya urutan tabel bisa diuji: selama semua
        // angkanya berdigit sama, urutan menurut tulisan kebetulan sama dengan urutan
        // menurut angka - sehingga tabel yang membandingkan tulisan tetap terlihat benar,
        // dan pemeriksaan urutan yang memakai data itu tidak akan pernah bisa gagal.
        kaspe.model.Transaction besar = new kaspe.model.Transaction();
        besar.setDate(java.time.LocalDate.of(2026, 9, 20));
        transactionDao.save(besar, java.util.Collections.singletonList(
                detail(trucks, "KB 8234 HD", 12000, 11500, 5, 1150)));

        // Satu catatan BELUM LUNAS: tanggal pembayarannya kosong. Baris macam ini nyata
        // di pemakaian (uangnya menyusul setelah notanya jalan), tetapi data uji yang
        // selalu lunas membuat pengurutan kolom Tgl Lunas tidak pernah bertemu sel
        // kosong - dan pengurut yang rapuh terhadapnya tidak pernah kelihatan salah.
        kaspe.model.Transaction belumLunas = new kaspe.model.Transaction();
        belumLunas.setDate(java.time.LocalDate.of(2026, 9, 25));
        transactionDao.save(belumLunas, java.util.Collections.singletonList(
                detail(trucks, "KB 8234 HD", 6000, 5950, 15, 1150, null)));
    }

    /** Rincian siap simpan dengan tanggal lunas bawaan data uji. */
    private static kaspe.model.TransactionDetail detail(java.util.List<Truck> truck, String plate,
                                                         long fieldWeight, long factoryWeight, int refraction, long price) {
        return detail(truck, plate, fieldWeight, factoryWeight, refraction, price,
                java.time.LocalDate.of(2026, 9, 15));
    }

    /** Rincian siap simpan, termasuk tanggal lunasnya: null berarti belum lunas. */
    private static kaspe.model.TransactionDetail detail(java.util.List<Truck> truck, String plate,
                                                         long fieldWeight, long factoryWeight, int refraction, long price,
                                                         java.time.LocalDate paymentDate) {
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
        d.setPaymentDate(paymentDate);
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
