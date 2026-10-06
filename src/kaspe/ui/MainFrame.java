package kaspe.ui;

import kaspe.model.Pengguna;

import javax.swing.*;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Jendela utama: bilah samping berisi halaman, isinya di sebelah kanan. */
public class MainFrame extends JFrame {

    /**
     * Lebar jendela terkecil yang masih menampilkan seluruh isinya.
     *
     * <p>Dua hal menentukannya, dan keduanya diukur pada lebar ini oleh {@code TestUi}:
     * form transaksi memakai isian berukuran tetap dengan kolom kanan yang tidak bisa
     * dilipat, dan dua tabel - daftar pengiriman tersimpan serta tabel laporan - memakai
     * lebar kolom tetap tanpa penggeser mendatar. Kalau ruangnya kurang, kolomnya diperas
     * dan isinya terpotong; pada tabel laporan pemotongan itu ikut ke kertas, karena
     * pencetakan memakai {@code FIT_WIDTH} yang memperkecil tabel apa adanya.
     *
     * <p>Yang paling menuntut adalah tabel-tabel itu: 1290px adalah lebar terkecil yang
     * masih menampilkan seluruh kolomnya utuh, termasuk ruang ikon panah urut yang muncul
     * di judul kolom yang sedang diurutkan. Di bawah itu tabelnya mulai memeras kolom, dan
     * kolom yang paling mepet kehilangan satu piksel lalu terpotong. Angka di bawah ini
     * diberi kelonggaran 10px di atas lantai itu supaya perubahan lebar kolom yang wajar
     * tidak langsung menggagalkan pengujian, tetapi tidak lebih - lebar ini membatasi
     * berapa kecil jendelanya boleh dikecilkan.
     *
     * <p>Angkanya diukur, bukan diperkirakan: batasnya dicari dengan menguji satu per satu
     * sampai pengujiannya berhenti gagal. Mengubah lebar kolom tabel atau lebar isian form
     * menggeser angka ini, dan {@code TestUi} menguji seluruh halaman persis pada lebar ini.
     *
     * <p>Perhatikan bahwa lebar ini hampir menyentuh lebar bawaan jendela (1320). Itu wajar:
     * sepuluh kolom laporan yang masing-masing harus memuat judulnya, satuannya, dan angka
     * terburuknya memang memakai hampir seluruh lebar. Karena itu menambah kolom di layar
     * berarti menaikkan angka ini sampai di atas lebar bawaan - dan itu tanda bahwa kolom
     * barunya sebaiknya tidak ada di layar (lihat kolom Susut, yang karena itu hanya ada di
     * berkas ekspor).
     */
    public static final int LEBAR_MINIMUM = 1300;

    /**
     * Tinggi jendela terkecil yang masih memuat seluruh halaman transaksi tanpa digulir.
     *
     * <p>Batasnya 660px: di bawah itu halaman luarnya mulai menggulir, sehingga tombol dan
     * baris paling bawah hanya bisa dicapai dengan menggulir. Diberi kelonggaran supaya
     * halaman masih terasa lega, bukan pas-pasan.
     *
     * <p>Perhatikan apa yang TIDAK terjadi di bawah batas ini: daftar pengiriman tersimpan
     * tidak pernah terhimpit. Di bawah sekitar 620px tingginya berhenti di 175px dan halaman
     * luarnya yang menggulir. Karena itu "daftarnya cukup tinggi" bukan pemeriksaan yang
     * berarti di sini - ia tidak pernah bisa gagal. Yang diperiksa adalah "muat tanpa digulir".
     */
    public static final int TINGGI_MINIMUM = 700;

    private final PagePanel page;
    /** Aksi sesi berikutnya: dibuka jendela utama lagi lewat Main setelah keluar akun. */
    private Runnable sesiBerikutnya;

    public MainFrame(Pengguna pengguna) {
        page = new PagePanel(pengguna);
        setTitle("Aplikasi Pencatatan Kaspe");
        // Menutup jendela tidak langsung keluar: kalau masih ada isian transaksi yang
        // belum disimpan, operator harus ditanya dulu — sama seperti saat pindah halaman.
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1320, 760);
        setMinimumSize(new Dimension(LEBAR_MINIMUM, TINGGI_MINIMUM));
        setLocationRelativeTo(null);

        // Susunannya dipakai bersama dengan pembuat gambar pratinjau dan uji tampilan,
        // supaya ketiganya tidak bisa berbeda tanpa ada yang menyadari.
        JPanel layar = PagePanel.shell(page);
        setContentPane(layar);
        NavBar nav = cariNavBar(layar);

        page.showPanel(new PanelDashboard(), "Beranda",
                "Ringkasan catatan pengiriman singkong.");

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Menekan X menghentikan aplikasinya sekalian, bukan sekadar
                // keluar akun — tetap dengan pertanyaan yang sama kalau masih
                // ada isian transaksi yang belum disimpan.
                if (nav != null && nav.adaKerjaBelumDisimpan() && !yakinBuangKerja()) {
                    return;
                }
                System.exit(0);
            }
        });

        // Keluar akun: tombolnya di kaki bilah samping, aksinya dipasang di sini
        // karena bilahnya dibuat PagePanel.shell dan tidak diserahkan ke siapa pun.
        if (nav != null) {
            nav.setKeluar(() -> keluar(nav));
        }
    }

    /**
     * Pasang aksi yang berjalan setelah jendela ditutup karena keluar akun —
     * yaitu membuka layar masuk untuk sesi berikutnya.
     */
    public void setSesiBerikutnya(Runnable aksi) {
        this.sesiBerikutnya = aksi;
    }

    /**
     * Keluar akun: jendela ini dibuang lalu layar masuk dibuka lagi. Jalannya
     * berbeda dari menekan X — itu menghentikan aplikasinya sekalian. Isian
     * transaksi yang belum disimpan ditanya dulu, dengan pesan yang sama
     * seperti saat menutup jendela.
     */
    private void keluar(NavBar nav) {
        if (nav.adaKerjaBelumDisimpan() && !yakinBuangKerja()) {
            return;
        }
        dispose();
        if (sesiBerikutnya != null) {
            sesiBerikutnya.run();
        }
    }

    /** Tanya dulu sebelum membuang isian transaksi yang belum disimpan. */
    private boolean yakinBuangKerja() {
        int jwb = JOptionPane.showConfirmDialog(this,
                "Masih ada isian transaksi yang belum disimpan.\nKeluar tanpa menyimpan?",
                "Belum disimpan", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return jwb == JOptionPane.YES_OPTION;
    }

    /**
     * Bilah samping dicari dari susunan jendela, karena {@link PagePanel#shell}
     * membuatnya sendiri dan tidak menyerahkannya — dan PagePanel tidak boleh berubah
     * hanya untuk kebutuhan ini.
     */
    private static NavBar cariNavBar(Container c) {
        for (Component anak : c.getComponents()) {
            if (anak instanceof NavBar) {
                return (NavBar) anak;
            }
            if (anak instanceof Container) {
                NavBar hasil = cariNavBar((Container) anak);
                if (hasil != null) {
                    return hasil;
                }
            }
        }
        return null;
    }
}
