package kaspe.ui;

import kaspe.dao.UserDao;
import kaspe.model.Pengguna;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Halaman kelola akun pengguna: daftarnya, menambah, mengubah, dan menghapus.
 * Hanya admin yang sampai ke sini — entrinya tidak dipasang di bilah samping
 * untuk pengguna biasa (lihat {@link NavBar}).
 *
 * <p>Tiga perubahan ditolak di sini, bukan di database: admin TERAKHIR tidak
 * boleh dihapus (aplikasi bisa terkunci untuk selamanya) maupun diturunkan
 * menjadi pengguna biasa (alasannya sama: tanpa satu pun admin, halaman
 * Pengguna tertutup untuk selamanya), dan akun yang sedang dipakai juga
 * tidak boleh dihapus — penggunanya akan tetap masuk padahal akunnya sudah
 * tiada. Seperti penghapusan massal di halaman transaksi, seluruh baris
 * terpilih diperiksa dulu: selama satu saja terhalang, tidak ada yang terhapus.
 */
public class PanelPengguna extends JPanel {

    private final UserDao dao = new UserDao();
    /** Akun yang sedang masuk; penghapusannya ditolak. */
    private final Pengguna yangMasuk;

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Nama", "Peran"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final Theme.Table tabel = new Theme.Table(model, "Belum ada pengguna tercatat.");
    private final JTextField fNama = new JTextField();
    /** Sandi baru. Kosong saat mengubah berarti tetap memakai yang lama. */
    private final JPasswordField fSandi = new JPasswordField();
    private final JComboBox<String> cmbPeran = new JComboBox<>(new String[]{"Admin", "Pengguna"});
    private final JLabel lblStatus = new JLabel();
    private final JButton btnTambah = Theme.primary("Tambah");
    private final JButton btnUbah = Theme.plain("Ubah");
    private final JButton btnHapus = Theme.plain("Hapus");

    /** Id pengguna tiap baris tabel, sejajar nomor barisnya. */
    private final List<Integer> idPerBaris = new ArrayList<>();

    public PanelPengguna(Pengguna yangMasuk) {
        this.yangMasuk = yangMasuk;
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        fNama.setPreferredSize(new Dimension(200, Theme.FIELD_HEIGHT));
        fSandi.setPreferredSize(new Dimension(160, Theme.FIELD_HEIGHT));
        cmbPeran.setPreferredSize(new Dimension(120, Theme.FIELD_HEIGHT));
        Theme.placeholder(fNama, "mis. budi");
        Theme.placeholder(fSandi, "minimal 4 karakter");
        lblStatus.setForeground(Theme.DANGER);

        btnTambah.addActionListener(e -> tambah());
        btnUbah.addActionListener(e -> ubah());
        btnHapus.addActionListener(e -> hapusTerpilih());

        Theme.styleTable(tabel);
        Theme.widths(tabel, 280, 120);
        // Hapus boleh satu atau banyak baris; Ubah hanya satu — kalau dua, tidak
        // jelas mana yang mau diubah.
        tabel.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tabel.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                barisDipilih();
            }
        });

        JPanel kartu = Theme.card("Pengguna");
        kartu.add(buildIsi(), BorderLayout.CENTER);
        add(kartu, BorderLayout.CENTER);

        muat();
    }

    // ---------- susunan ----------

    private JPanel buildIsi() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);

        JPanel isian = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        isian.setOpaque(false);
        Theme.fillRow(isian, 10,
                Theme.field("Nama", fNama),
                Theme.field("Sandi", fSandi),
                Theme.field("Peran", cmbPeran));
        p.add(isian, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(tabel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        p.add(scroll, BorderLayout.CENTER);

        JPanel kaki = new JPanel(new BorderLayout(0, 8));
        kaki.setOpaque(false);
        JPanel tombol = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tombol.setOpaque(false);
        Theme.fillRow(tombol, 8, btnTambah, btnUbah, btnHapus);
        kaki.add(tombol, BorderLayout.NORTH);
        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        status.setOpaque(false);
        status.add(lblStatus);
        kaki.add(status, BorderLayout.SOUTH);
        p.add(kaki, BorderLayout.SOUTH);
        return p;
    }

    // ---------- data ----------

    /** Muat ulang daftar akun tanpa kehilangan baris yang disorot. */
    private void muat() {
        try {
            int terpilih = barisTerpilih();
            model.setRowCount(0);
            idPerBaris.clear();
            for (Pengguna p : new UserDao().list()) {
                idPerBaris.add(p.getId());
                model.addRow(new Object[]{p.getNama(), namaPeran(p.getPeran())});
            }
            if (terpilih >= 0 && terpilih < tabel.getRowCount()) {
                tabel.setRowSelectionInterval(terpilih, terpilih);
            }
            perbaruiTombol();
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    /** Isi isian mengikuti baris yang disorot; sandinya dibiarkan kosong. */
    private void barisDipilih() {
        if (tabel.getSelectedRowCount() == 1) {
            int baris = tabel.getSelectedRow();
            fNama.setText(String.valueOf(model.getValueAt(baris, 0)));
            pilihPeran(String.valueOf(model.getValueAt(baris, 1)));
            fSandi.setText("");
        }
        perbaruiTombol();
    }

    private void perbaruiTombol() {
        int jumlah = tabel.getSelectedRowCount();
        btnUbah.setEnabled(jumlah == 1);
        btnHapus.setEnabled(jumlah > 0);
    }

    private int barisTerpilih() {
        return tabel.getSelectedRow();
    }

    private void setStatus(String pesan) {
        lblStatus.setText(pesan == null ? "" : pesan);
    }

    /** Tulisan peran di layar: simpanannya ADMIN/USER, yang dibaca orang Indonesia. */
    private static String namaPeran(String peran) {
        return Pengguna.ADMIN.equals(peran) ? "Admin" : "Pengguna";
    }

    private void pilihPeran(String tampilan) {
        cmbPeran.setSelectedIndex("Admin".equals(tampilan) ? 0 : 1);
    }

    private String peranTerpilih() {
        return "Admin".equals(cmbPeran.getSelectedItem()) ? Pengguna.ADMIN : Pengguna.USER;
    }

    // ---------- aksi ----------

    private void tambah() {
        Theme.clearErrors(fNama, fSandi);
        setStatus(null);
        String nama = fNama.getText().trim();
        if (nama.isEmpty()) {
            Theme.markError(fNama, true);
            setStatus("Nama pengguna wajib diisi.");
            return;
        }
        String sandi = new String(fSandi.getPassword());
        if (sandi.length() < 4) {
            Theme.markError(fSandi, true);
            setStatus("Sandi minimal 4 karakter.");
            return;
        }
        try {
            dao.simpan(nama, sandi, peranTerpilih());
            fNama.setText("");
            fSandi.setText("");
            tabel.clearSelection();
            muat();
            setStatus("Pengguna \"" + nama + "\" ditambahkan.");
        } catch (IllegalArgumentException e) {
            setStatus(e.getMessage());
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    private void ubah() {
        if (tabel.getSelectedRowCount() != 1) {
            setStatus("Pilih satu pengguna dulu.");
            return;
        }
        Theme.clearErrors(fNama, fSandi);
        setStatus(null);
        String nama = fNama.getText().trim();
        if (nama.isEmpty()) {
            Theme.markError(fNama, true);
            setStatus("Nama pengguna wajib diisi.");
            return;
        }
        String sandi = new String(fSandi.getPassword());
        // Kotak sandi kosong bukan berarti sandi empat huruf kosong: mengubah tanpa
        // menulis sandi berarti sandi lamanya tetap dipakai.
        if (!sandi.isEmpty() && sandi.length() < 4) {
            Theme.markError(fSandi, true);
            setStatus("Sandi minimal 4 karakter.");
            return;
        }
        int baris = tabel.getSelectedRow();
        try {
            // Penurunan admin terakhir ditolak dengan alasan yang sama seperti
            // penghapusannya di bawah: tanpa satu pun admin, halaman Pengguna
            // tertutup untuk selamanya dan tidak ada yang bisa memperbaikinya
            // dari dalam aplikasi.
            List<Pengguna> semua = dao.list();
            Pengguna target = cariPengguna(semua, idPerBaris.get(baris));
            int adminTersisa = 0;
            for (Pengguna p : semua) {
                if (p.admin()) {
                    adminTersisa++;
                }
            }
            if (target != null && target.admin()
                    && Pengguna.USER.equals(peranTerpilih()) && adminTersisa == 1) {
                setStatus("Admin terakhir tidak bisa diturunkan menjadi pengguna biasa.");
                return;
            }
            dao.ubah(idPerBaris.get(baris), nama, peranTerpilih(), sandi.isEmpty() ? null : sandi);
            muat();
            setStatus("Perubahan pengguna \"" + nama + "\" disimpan.");
        } catch (IllegalArgumentException e) {
            setStatus(e.getMessage());
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    private void hapusTerpilih() {
        setStatus(null);
        int[] baris = tabel.getSelectedRows();
        if (baris.length == 0) {
            setStatus("Pilih dulu pengguna yang mau dihapus.");
            return;
        }
        List<Pengguna> semua;
        try {
            semua = dao.list();
        } catch (Exception e) {
            Theme.showError(this, e);
            return;
        }
        // Seluruh baris terpilih diperiksa dulu, sebelum satu pun konfirmasi
        // atau penghapusan berjalan — setengah terhapus tidak boleh terjadi.
        int adminTersisa = 0;
        for (Pengguna p : semua) {
            if (p.admin()) {
                adminTersisa++;
            }
        }
        for (int b : baris) {
            Pengguna p = cariPengguna(semua, idPerBaris.get(b));
            if (p == null) {
                continue;
            }
            if (p.getId() == yangMasuk.getId()) {
                setStatus("Akun yang sedang dipakai tidak bisa dihapus.");
                return;
            }
            if (p.admin()) {
                adminTersisa--;
                if (adminTersisa == 0) {
                    setStatus("Admin terakhir tidak bisa dihapus.");
                    return;
                }
            }
        }
        try {
            int jwb = JOptionPane.showConfirmDialog(this,
                    "Hapus " + baris.length + " akun pengguna?",
                    "Hapus Pengguna", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (jwb != JOptionPane.YES_OPTION) {
                return;
            }
            for (int b : baris) {
                dao.hapus(idPerBaris.get(b));
            }
        } catch (Exception e) {
            Theme.showError(this, e);
        }
        tabel.clearSelection();
        muat();
        setStatus(baris.length + " akun pengguna dihapus.");
    }

    private static Pengguna cariPengguna(List<Pengguna> semua, int id) {
        for (Pengguna p : semua) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }
}
