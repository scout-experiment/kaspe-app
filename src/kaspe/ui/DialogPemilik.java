package kaspe.ui;

import kaspe.dao.MasterDao;
import kaspe.model.Rental;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Kelola pemilik truk: menambah, mengganti nama, dan menghapus rental.
 *
 * <p>Dipisah dari {@link DialogDataMaster} karena dua hal berbeda jangan berbagi satu
 * tempat: di situ yang diurus truknya, di sini pemiliknya. Tombolnya dibuka dari kaki
 * dialog utama, bukan dari baris tombol atasnya - baris itu sudah penuh, dan tombol
 * tambahan di situ akan terlipat lalu terpotong.
 *
 * <p>Tanpa tempat ini, pemilik yang salah ketik dan sudah punya truk tidak bisa diganti
 * namanya dan tidak bisa dihapus (penghapusan ditolak selama masih punya truk), sehingga
 * rekap uang per pemiliknya terpecah permanen. Nama rental dibaca laporan lewat relasi,
 * jadi mengganti namanya otomatis ikut di seluruh riwayat - tidak ada yang perlu
 * disesuaikan.
 *
 * <p>Seperti {@link DialogDataMaster}, isinya kelas ini sendiri (sebuah {@link JPanel})
 * supaya bisa diperiksa tanpa layar; jendelanya dibuat di {@link #buka}.
 */
public class DialogPemilik extends JPanel {

    private final MasterDao dao = new MasterDao();

    private final DefaultTableModel modelRental = new DefaultTableModel(
            new Object[]{"Pemilik truk"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final Theme.Table tableRental = new Theme.Table(modelRental, "Belum ada pemilik.");
    private final JTextField fNama = new JTextField();
    private final JLabel lblStatus = new JLabel();

    private final List<Rental> rental = new ArrayList<>();
    /** Id rental baris yang sedang diubah, 0 kalau tidak ada. */
    private int rentalId = 0;

    public DialogPemilik() {
        setLayout(new BorderLayout(0, 12));
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 14, 16));

        Theme.placeholder(fNama, "mis. Rental Sinar Jaya");
        fNama.setPreferredSize(new Dimension(240, Theme.FIELD_HEIGHT));
        lblStatus.setForeground(Theme.DANGER);

        Theme.styleTable(tableRental);
        Theme.widths(tableRental, 300);
        tableRental.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                pilihBaris();
            }
        });

        JPanel form = new JPanel(new BorderLayout(0, 8));
        form.setOpaque(false);
        JPanel kolom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        kolom.setOpaque(false);
        kolom.add(Theme.field("Nama Pemilik", fNama));
        form.add(kolom, BorderLayout.NORTH);

        JButton btnTambah = Theme.primary("Tambah");
        // "Tambah" dan "Simpan Perubahan" dipisah: satu tombol untuk dua maksud pernah
        // MENIMPA baris yang kebetulan tersorot, beserta seluruh riwayat pemiliknya.
        JButton btnSimpan = Theme.plain("Simpan Perubahan");
        JButton btnHapus = Theme.plain("Hapus");
        btnTambah.addActionListener(e -> tambah());
        btnSimpan.addActionListener(e -> ubah());
        btnHapus.addActionListener(e -> hapus());
        // hgap FlowLayout ikut menjadi padding awal, jadi baris ini dibuat lewat
        // Theme.row agar tepi kirinya rata dengan kolom di atasnya.
        JPanel tombol = Theme.row(8, btnTambah, btnSimpan, btnHapus);
        form.add(tombol, BorderLayout.CENTER);

        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        status.setOpaque(false);
        status.add(lblStatus);
        form.add(status, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(tableRental);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        add(form, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buildKaki(), BorderLayout.SOUTH);
        muat();
    }

    /** Susunan dialog lengkap; {@code setelahBerubah} dijalankan setiap kali data berubah. */
    public static void buka(Window owner, Runnable setelahBerubah) {
        JDialog dialog = new JDialog(owner, "Kelola Pemilik Truk", Dialog.ModalityType.APPLICATION_MODAL);
        DialogPemilik isi = new DialogPemilik();
        isi.setelahBerubah = setelahBerubah;
        dialog.setContentPane(isi);
        dialog.pack();
        Dimension ukuran = ukuranJendela(isi);
        dialog.setSize(ukuran.width, ukuran.height);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    /** Lebar jendela terkecil dialog ini; dipakai bersama pemeriksaan di TestUi agar keduanya tidak berbeda sendiri. */
    public static final int LEBAR_MINIMUM = 460;

    /** Tinggi jendela terkecil dialog ini; dipakai bersama pemeriksaan di TestUi agar keduanya tidak berbeda sendiri. */
    public static final int TINGGI_MINIMUM = 420;

    /**
     * Ukuran jendela dialog: lantai {@link #LEBAR_MINIMUM} x {@link #TINGGI_MINIMUM},
     * diperbesar kalau isinya minta lebih.
     *
     * <p>Dipakai bersama oleh {@link #buka} dan pemeriksaan di TestUi. Kalau angkanya
     * dihitung di dua tempat, keduanya bisa berbeda tanpa ada yang menyadari, dan
     * pemeriksaan itu diam-diam berhenti menguji ukuran yang benar-benar dipakai.
     */
    public static Dimension ukuranJendela(JPanel isi) {
        Dimension butuh = isi.getPreferredSize();
        return new Dimension(Math.max(LEBAR_MINIMUM, butuh.width),
                Math.max(TINGGI_MINIMUM, butuh.height));
    }

    private Runnable setelahBerubah;

    /** Baris tombol penutup, rata kanan tanpa padding tepi dari hgap FlowLayout. */
    private JPanel buildKaki() {
        JButton tutup = Theme.plain("Tutup");
        tutup.addActionListener(e -> {
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w instanceof JDialog) {
                ((JDialog) w).dispose();
            }
        });
        return Theme.rowRight(8, tutup);
    }

    // ---------- data ----------

    /** Muat ulang daftar pemilik, tanpa kehilangan baris yang sedang disorot. */
    final void muat() {
        try {
            int sebelumnya = rentalId;
            modelRental.setRowCount(0);
            rental.clear();
            for (Rental r : dao.listRental()) {
                rental.add(r);
                modelRental.addRow(new Object[]{r.getRentalName()});
            }
            int baris = baris(sebelumnya);
            if (baris < 0 && modelRental.getRowCount() > 0) {
                baris = 0;
            }
            if (baris >= 0) {
                tableRental.setRowSelectionInterval(baris, baris);
            } else {
                fNama.setText("");
            }
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    private int baris(int id) {
        for (int i = 0; i < rental.size(); i++) {
            if (rental.get(i).getRentalId() == id) {
                return i;
            }
        }
        return -1;
    }

    private void pilihBaris() {
        int baris = tableRental.getSelectedRow();
        if (baris < 0) {
            rentalId = 0;
            return;
        }
        Rental r = rental.get(tableRental.convertRowIndexToModel(baris));
        rentalId = r.getRentalId();
        fNama.setText(r.getRentalName());
        setStatus("");
    }

    private void setStatus(String pesan) {
        lblStatus.setText(pesan == null ? "" : pesan);
    }

    // ---------- aksi ----------

    private void tambah() {
        String nama = fNama.getText().trim();
        if (nama.isEmpty()) {
            setStatus("Nama pemilik wajib diisi.");
            return;
        }
        try {
            Rental r = new Rental();
            r.setRentalName(nama);
            dao.saveRental(r);
            setStatus("");
            rentalId = 0;
            fNama.setText("");
            muat();
            beritahu();
        } catch (IllegalArgumentException e) {
            // Penolakan yang disengaja (nama sudah dipakai), bukan kerusakan.
            setStatus(e.getMessage());
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    private void ubah() {
        if (rentalId == 0) {
            setStatus("Pilih dulu pemilik yang mau diganti namanya.");
            return;
        }
        String nama = fNama.getText().trim();
        if (nama.isEmpty()) {
            setStatus("Nama pemilik wajib diisi.");
            return;
        }
        try {
            Rental r = new Rental();
            r.setRentalId(rentalId);
            r.setRentalName(nama);
            dao.saveRental(r);
            setStatus("");
            muat();
            beritahu();
        } catch (IllegalArgumentException e) {
            // Penolakan yang disengaja (nama sudah dipakai), bukan kerusakan.
            setStatus(e.getMessage());
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    private void hapus() {
        if (rentalId == 0) {
            setStatus("Pilih dulu pemilik yang mau dihapus.");
            return;
        }
        try {
            // Penolakan diperiksa SEBELUM konfirmasi: kalau pasti ditolak, meminta "Ya"
            // lebih dulu hanya menjanjikan hal yang tidak bisa ditepati.
            String penolakan = dao.rentalDeleteRefusal(rentalId);
            if (penolakan != null) {
                tolak(penolakan);
                return;
            }
            // Tanpa layar tidak ada operator yang bisa menjawab; penghapusan dianggap
            // boleh saja supaya jalur penolakannya tetap teruji - sama seperti di
            // DialogDataMaster. Yang penting pemeriksaan penolakan di atas tetap jalan.
            if (!GraphicsEnvironment.isHeadless()
                    && JOptionPane.showConfirmDialog(this, "Hapus pemilik ini?", "Konfirmasi",
                            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }
            dao.deleteRental(rentalId);
            setStatus("");
            rentalId = 0;
            fNama.setText("");
            muat();
            beritahu();
        } catch (IllegalStateException e) {
            tolak(e.getMessage());
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    /**
     * Tampilkan alasan penolakan, atau diam saja tanpa layar.
     *
     * <p>Tanpa layar, jendela pesan melempar {@code HeadlessException} dan jalur ini
     * berhenti sebelum hasilnya bisa diperiksa - pemeriksaan yang tidak bisa diamati sama
     * saja tidak ada. Diam di sini bukan menutupi kegagalan: yang menolak adalah
     * pemeriksaan di atasnya, dan itu tetap berjalan.
     */
    private void tolak(String pesan) {
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(this, pesan, "Tidak bisa dihapus",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void beritahu() {
        if (setelahBerubah != null) {
            setelahBerubah.run();
        }
    }
}