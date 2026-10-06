package kaspe.ui;

import kaspe.dao.UserDao;
import kaspe.model.Pengguna;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

/**
 * Pintu masuk aplikasi: nama pengguna dan sandinya.
 *
 * <p>Kelas ini sendiri sebuah {@link JPanel}, dan jendelanya dibuat di {@link #buka} —
 * pola yang sama dengan {@link DialogDataMaster}, supaya susunan isinya bisa
 * diperiksa dan digambar tanpa layar.
 *
 * <p>Saat tabel pengguna masih kosong, layar yang sama berubah sendiri menjadi
 * pembuat admin pertama (nama, sandi, ulangi sandi); tidak ada layar pasang
 * tersendiri. Gagal masuk selalu memunculkan SATU pesan yang sama, baik namanya
 * tidak dikenal maupun sandinya salah — pesan yang berbeda akan mengungkapkan
 * nama mana yang benar-benar tercatat — dan nama yang sudah diketik dibiarkan
 * tertulis supaya tingkat sandinya saja yang diperbaiki.
 */
public class DialogLogin extends JPanel {

    private final JTextField fNama = new JTextField();
    private final JPasswordField fSandi = new JPasswordField();
    /** Kotak ulangi sandi; hanya dipasang saat membuat admin pertama. */
    private final JPasswordField fUlangi = new JPasswordField();
    private final JLabel lblStatus = new JLabel();
    private final JButton btnMasuk = Theme.primary("Masuk");

    /**
     * Lebar satu-satunya kolom isian. Tombol masuk dibentangkan selebar ini juga,
     * supaya tepi kiri layar menjadi satu garis lurus dari judul sampai tombol.
     */
    private static final int LEBAR_KOLOM = 280;

    /** true = tabel pengguna masih kosong, layar ini membuat admin pertama. */
    private final boolean pertama;
    /** Akun yang berhasil masuk; null selama belum. */
    private Pengguna hasil;

    public DialogLogin() throws SQLException {
        pertama = new UserDao().jumlah() == 0;

        setLayout(new BorderLayout());
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createEmptyBorder(24, 26, 20, 26));

        // Judulnya sebesar judul halaman di bilah atas (HeaderBar): layar ini adalah
        // halaman pertama yang dilihat operator, jadi gayanya mengikuti halaman lain.
        JLabel judul = new JLabel(pertama ? "Buat Admin Pertama" : "Masuk");
        judul.setFont(Theme.semibold(17f));
        judul.setForeground(Theme.INK);

        // Keterangan kecil di bawah judul: apa yang harus dilakukan di layar ini.
        JLabel subjudul = new JLabel(pertama
                ? "Belum ada akun. Buat akun admin pertama."
                : "Masuk untuk mulai mencatat pengiriman.");
        subjudul.setFont(Theme.semibold(12f));
        subjudul.setForeground(Theme.INK_SOFT);

        fNama.setPreferredSize(new Dimension(LEBAR_KOLOM, Theme.FIELD_HEIGHT));
        fSandi.setPreferredSize(new Dimension(LEBAR_KOLOM, Theme.FIELD_HEIGHT));
        fUlangi.setPreferredSize(new Dimension(LEBAR_KOLOM, Theme.FIELD_HEIGHT));
        Theme.placeholder(fNama, "mis. admin");
        lblStatus.setForeground(Theme.DANGER);
        // Tinggi baris status dipatok setinggi satu baris teks walau sedang kosong:
        // kalau tingginya mengikuti isi, tombol dan kotak isian ikut bergeser
        // setiap kali pesan kesalahan muncul atau hilang.
        lblStatus.setPreferredSize(new Dimension(LEBAR_KOLOM,
                lblStatus.getFontMetrics(lblStatus.getFont()).getHeight()));
        if (pertama) {
            btnMasuk.setText("Buat dan Masuk");
        }

        // Enter di kotak mana pun langsung mencoba masuk — layar ini singkat, dan
        // tangan operator tidak perlu pindah ke tetikus hanya untuk menekan tombolnya.
        fNama.addActionListener(e -> masuk());
        fSandi.addActionListener(e -> masuk());
        fUlangi.addActionListener(e -> masuk());
        btnMasuk.addActionListener(e -> masuk());

        add(buildIsi(judul, subjudul), BorderLayout.NORTH);
    }

    /**
     * Buka layar masuk sebagai jendela modal. Mengembalikan akun yang berhasil
     * masuk, atau null kalau jendelanya ditutup tanpa berhasil.
     */
    public static Pengguna buka(Window owner) {
        DialogLogin isi;
        try {
            isi = new DialogLogin();
        } catch (Exception e) {
            Theme.showError(owner, e);
            return null;
        }
        JDialog dialog = new JDialog(owner, isi.judulJendela(),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(isi);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return isi.hasil;
    }

    /**
     * Judul bilah jendela.
     *
     * <p>Isinya nama aplikasi, BUKAN kata yang sama dengan judul di dalam panel: bilah
     * jendela dan baris pertama panel duduk berdekatan, jadi menuliskan "Masuk" dua kali
     * berturut-turut hanya mengulang hal yang sama. Nama aplikasi di bilah jendela justru
     * yang memberi tahu jendela mana ini saat dilihat dari daftar jendela.
     */
    private static String judulJendela() {
        return "Aplikasi Pencatatan Kaspe";
    }

    // ---------- susunan ----------

    private JPanel buildIsi(JLabel judul, JLabel subjudul) {
        // Bertumpuk tegak dengan jarak tetap, dan TIDAK memakai GridLayout: GridLayout
        // menyamakan tinggi semua barisnya dengan baris TERTINGGI, sehingga judul, tombol,
        // dan baris status masing-masing ikut setinggi blok tiga kotak isian. Jendelanya
        // jadi 740px tinggi dengan celah menganga di antara baris-barisnya, padahal isinya
        // cuma tiga kotak dan satu tombol.
        JPanel kolom = new JPanel();
        kolom.setLayout(new BoxLayout(kolom, BoxLayout.Y_AXIS));
        kolom.setOpaque(false);

        // Blok identitas: gema kecil merek di bilah samping (lambang + nama), supaya
        // layar pertama yang dilihat operator jelas milik aplikasi yang sama. Judul
        // dan keterangannya dibungkus baris rata kiri seperti baris status di bawah:
        // BoxLayout menengahkan label yang lebarnya pas teksnya, sehingga tanpa
        // pembungkus mereka berdiri di tengah sementara kotak isiannya rata kiri.
        JLabel merek = new JLabel("Kaspe");
        merek.setFont(Theme.bold(15f));
        merek.setForeground(Theme.INK);
        kolom.add(Theme.row(8, new JLabel(Icons.of(Icons.BRAND, Theme.ACCENT, 22)), merek));
        kolom.add(Box.createVerticalStrut(14));

        kolom.add(Theme.row(0, judul));
        kolom.add(Box.createVerticalStrut(6));
        kolom.add(Theme.row(0, subjudul));
        kolom.add(Box.createVerticalStrut(16));

        JPanel baris = new JPanel(new GridLayout(pertama ? 3 : 2, 1, 0, 10));
        baris.setOpaque(false);
        baris.add(Theme.field("Nama pengguna", fNama));
        baris.add(Theme.field("Sandi", fSandi));
        if (pertama) {
            baris.add(Theme.field("Ulangi sandi", fUlangi));
        }
        kolom.add(baris);
        kolom.add(Box.createVerticalStrut(14));

        // Tombolnya dibentangkan selebar kolom, bukan dibiarkan kecil di kiri: aksi
        // utama layar ini memang yang paling menonjol, dan tepi kirinya tetap satu
        // garis lurus dengan kotak isian di atasnya. Dua halangan harus disingkirkan:
        // bawaan JButton membatasi ukuran maksimumnya selebar teksnya, dan BoxLayout
        // menafsirkan alignmentX 0 sebagai "selebar sisa kanan saja" — tombolnya malah
        // terdorong ke kanan. alignmentX 0.5 (seperti baris-baris lainnya) plus
        // maksimum yang dilonggarkan membuatnya terentang selebar kolom.
        btnMasuk.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                btnMasuk.getPreferredSize().height));
        btnMasuk.setAlignmentX(CENTER_ALIGNMENT);
        kolom.add(btnMasuk);
        kolom.add(Box.createVerticalStrut(8));

        kolom.add(Theme.row(0, lblStatus));
        return kolom;
    }

    // ---------- aksi ----------

    /** Coba masuk (atau buat admin pertama); berhasil berarti jendelanya ditutup. */
    private void masuk() {
        Theme.clearErrors(fNama, fSandi, fUlangi);
        setStatus(null);
        String nama = fNama.getText().trim();
        String sandi = new String(fSandi.getPassword());
        if (nama.isEmpty()) {
            Theme.markError(fNama, true);
            setStatus("Nama pengguna wajib diisi.");
            return;
        }
        try {
            UserDao dao = new UserDao();
            if (pertama) {
                String ulangi = new String(fUlangi.getPassword());
                if (sandi.length() < 4) {
                    Theme.markError(fSandi, true);
                    setStatus("Sandi minimal 4 karakter.");
                    return;
                }
                if (!sandi.equals(ulangi)) {
                    Theme.markError(fSandi, true);
                    Theme.markError(fUlangi, true);
                    setStatus("Sandi kedua kalinya tidak sama.");
                    return;
                }
                dao.simpan(nama, sandi, Pengguna.ADMIN);
                hasil = dao.masuk(nama, sandi);
            } else {
                hasil = dao.masuk(nama, sandi);
                if (hasil == null) {
                    Theme.markError(fNama, true);
                    Theme.markError(fSandi, true);
                    setStatus("Nama atau sandi salah.");
                    return;
                }
            }
            tutup();
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    private void tutup() {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w != null) {
            w.dispose();
        }
    }

    private void setStatus(String pesan) {
        lblStatus.setText(pesan == null ? "" : pesan);
    }
}
