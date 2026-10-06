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

    /** true = tabel pengguna masih kosong, layar ini membuat admin pertama. */
    private final boolean pertama;
    /** Akun yang berhasil masuk; null selama belum. */
    private Pengguna hasil;

    public DialogLogin() throws SQLException {
        pertama = new UserDao().jumlah() == 0;

        setLayout(new BorderLayout());
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 16, 20));

        JLabel judul = new JLabel(pertama ? "Buat Admin Pertama" : "Masuk");
        judul.setFont(Theme.semibold(16f));
        judul.setForeground(Theme.INK);

        fNama.setPreferredSize(new Dimension(240, Theme.FIELD_HEIGHT));
        fSandi.setPreferredSize(new Dimension(240, Theme.FIELD_HEIGHT));
        fUlangi.setPreferredSize(new Dimension(240, Theme.FIELD_HEIGHT));
        Theme.placeholder(fNama, "mis. admin");
        lblStatus.setForeground(Theme.DANGER);
        if (pertama) {
            btnMasuk.setText("Buat dan Masuk");
        }

        // Enter di kotak mana pun langsung mencoba masuk — layar ini singkat, dan
        // tangan operator tidak perlu pindah ke tetikus hanya untuk menekan tombolnya.
        fNama.addActionListener(e -> masuk());
        fSandi.addActionListener(e -> masuk());
        fUlangi.addActionListener(e -> masuk());
        btnMasuk.addActionListener(e -> masuk());

        add(buildIsi(judul), BorderLayout.NORTH);
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

    private JPanel buildIsi(JLabel judul) {
        // Bertumpuk tegak dengan jarak tetap, dan TIDAK memakai GridLayout: GridLayout
        // menyamakan tinggi semua barisnya dengan baris TERTINGGI, sehingga judul, tombol,
        // dan baris status masing-masing ikut setinggi blok tiga kotak isian. Jendelanya
        // jadi 740px tinggi dengan celah menganga di antara baris-barisnya, padahal isinya
        // cuma tiga kotak dan satu tombol.
        JPanel kolom = new JPanel();
        kolom.setLayout(new BoxLayout(kolom, BoxLayout.Y_AXIS));
        kolom.setOpaque(false);
        // Judulnya dibungkus baris rata kiri seperti tombol dan status di bawahnya:
        // BoxLayout menengahkan label yang lebarnya pas teksnya, sehingga judulnya
        // berdiri di tengah sementara kotak isiannya rata kiri.
        kolom.add(Theme.row(0, judul));
        kolom.add(Box.createVerticalStrut(12));

        JPanel baris = new JPanel(new GridLayout(pertama ? 3 : 2, 1, 0, 10));
        baris.setOpaque(false);
        baris.add(Theme.field("Nama pengguna", fNama));
        baris.add(Theme.field("Sandi", fSandi));
        if (pertama) {
            baris.add(Theme.field("Ulangi sandi", fUlangi));
        }
        kolom.add(baris);
        kolom.add(Box.createVerticalStrut(12));

        JPanel tombol = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tombol.setOpaque(false);
        tombol.add(btnMasuk);
        kolom.add(tombol);
        kolom.add(Box.createVerticalStrut(12));

        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        status.setOpaque(false);
        status.add(lblStatus);
        kolom.add(status);
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
