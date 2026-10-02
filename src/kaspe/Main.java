package kaspe;

import kaspe.ui.MainFrame;
import kaspe.ui.Theme;

import javax.swing.*;

/** Titik masuk aplikasi. */
public class Main {

    public static void main(String[] args) {
        Theme.install();
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
