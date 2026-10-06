package kaspe;

import kaspe.model.Pengguna;
import kaspe.ui.DialogLogin;
import kaspe.ui.MainFrame;
import kaspe.ui.Theme;

import java.awt.GraphicsEnvironment;

import javax.swing.*;

/** Titik masuk aplikasi. */
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
        SwingUtilities.invokeLater(() -> {
            // Pintu masuk dulu, jendela utama kemudian. Menutup layar masuk tanpa
            // berhasil masuk berarti keluar dari aplikasi — tidak ada halaman yang
            // bisa dibuka tanpa akun yang masuk.
            Pengguna pengguna = DialogLogin.buka(null);
            if (pengguna == null) {
                System.exit(0);
            }
            new MainFrame(pengguna).setVisible(true);
        });
    }
}
