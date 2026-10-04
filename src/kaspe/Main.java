package kaspe;

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
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
