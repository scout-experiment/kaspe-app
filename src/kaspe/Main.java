package kaspe;

import kaspe.model.Pengguna;
import kaspe.ui.DialogLogin;
import kaspe.ui.MainFrame;
import kaspe.ui.Theme;

import java.awt.GraphicsEnvironment;

import javax.swing.*;

/** Titik masuk aplikasi. Satu sesi = satu layar masuk dan satu jendela utama. */
public class Main {

    public static void main(String[] args) {
        // Berkas setelan yang ada tetapi tidak berkata letak databasenya berarti
        // niat penggunanya tidak diketahui — aplikasi menolak jalan, bukan
        // diam-diam memakai database lain. Dicek di sini, sebelum jendela apa
        // pun dibuat, supaya jalannya lewat terminal pun jelas alasannya.
        String penolakan = Db.configError();
        if (penolakan != null) {
            System.err.println(penolakan);
            if (!GraphicsEnvironment.isHeadless()) {
                try {
                    JOptionPane.showMessageDialog(null, penolakan,
                            "Setelan Database", JOptionPane.ERROR_MESSAGE);
                } catch (Exception e) {
                    // pesannya sudah tercetak di atas; tanpa dialog pun cukup
                }
            }
            System.exit(1);
        }
        Theme.install();
        SwingUtilities.invokeLater(Main::bukaSesi);
    }

    /**
     * Buka satu sesi: layar masuk dulu, jendela utama kalau berhasil masuk.
     *
     * <p>Dipanggil lagi dari aksi keluar akun, sehingga kembali ke layar masuk
     * tidak menumpuk tumpukan panggilan: tiap sesi berjalan lewat
     * invokeLater-nya sendiri dan tumpukan yang lama sudah bubar sebelum
     * sesi yang baru mulai. Menutup layar masuk tanpa berhasil masuk berarti
     * keluar dari aplikasi — tidak ada halaman yang bisa dibuka tanpa akun
     * yang masuk.
     */
    private static void bukaSesi() {
        Pengguna pengguna = DialogLogin.buka(null);
        if (pengguna == null) {
            System.exit(0);
        }
        MainFrame jendela = new MainFrame(pengguna);
        // Lewat invokeLater, bukan Main::bukaSesi langsung: aksi keluar berjalan di
        // dalam pengiriman peristiwa, dan memanggil bukaSesi dari situ menumpuk
        // tumpukan panggilan tiap kali keluar masuk akun. Dengan invokeLater,
        // pengiriman yang lama selesai dan bubar dulu, baru sesi berikutnya mulai.
        jendela.setSesiBerikutnya(() -> SwingUtilities.invokeLater(Main::bukaSesi));
        jendela.setVisible(true);
    }
}
