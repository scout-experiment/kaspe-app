package kaspe.ui;

import javax.swing.*;

/** Jendela utama: bilah samping berisi halaman, isinya di sebelah kanan. */
public class MainFrame extends JFrame {

    private final PagePanel page = new PagePanel();

    public MainFrame() {
        setTitle("Aplikasi Pencatatan Kaspe");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1320, 760);
        setLocationRelativeTo(null);

        // Susunannya dipakai bersama dengan pembuat gambar pratinjau dan uji tampilan,
        // supaya ketiganya tidak bisa berbeda tanpa ada yang menyadari.
        setContentPane(PagePanel.shell(page));

        page.showPanel(new PanelDashboard(), "Beranda",
                "Ringkasan catatan pengiriman singkong.");
    }
}
