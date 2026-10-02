package kaspe.ui;

import kaspe.dao.MasterDao;
import kaspe.model.Rental;
import kaspe.model.Truck;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Halaman data master: pemilik truk dan truk miliknya dalam satu layar.
 *
 * <p>Susunannya kiri-kanan. Kiri daftar pemilik truk, kanan truk milik pemilik yang
 * sedang disorot. Sebelumnya keduanya halaman terpisah, dan di halaman truk pemiliknya
 * harus dipilih sendiri dari kotak pilihan.
 *
 * <p>Perubahan itu bukan sekadar merapikan tampilan. Cara lama menyimpan satu kesalahan
 * yang tidak terlihat: kotak pilihan rental selalu sudah terisi begitu halaman dibuka,
 * sehingga mengetik plat baru lalu menekan Tambah / Simpan tanpa menyentuh kotak itu
 * membuat truk tercatat milik pemilik yang kebetulan tampil pertama — tanpa pesan apa
 * pun, dan uangnya masuk ke pemilik yang salah di laporan. Sekarang pemiliknya
 * diturunkan dari baris yang disorot, jadi tidak ada yang bisa salah pilih.
 */
public class PanelMaster extends JPanel {

    private final MasterDao dao = new MasterDao();

    // ---------- kiri: pemilik truk ----------
    private final DefaultTableModel modelRental = new DefaultTableModel(
            new Object[]{"ID", "Nama Rental"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final Theme.Table tableRental = new Theme.Table(modelRental,
            "Belum ada rental. Isi namanya di atas, lalu tekan Tambah / Simpan.");
    private final JTextField fNama = new JTextField();

    // ---------- kanan: truk milik pemilik yang disorot ----------
    private final DefaultTableModel modelTruk = new DefaultTableModel(
            new Object[]{"ID", "Plat"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final Theme.Table tableTruk = new Theme.Table(modelTruk, "");
    private final JTextField fPlat = new JTextField();
    private final JLabel lblPemilik = new JLabel();
    private final JLabel lblStatus = new JLabel();

    /** Pemilik yang sedang disorot di kiri. 0 artinya belum ada yang dipilih. */
    private int rentalId = 0;
    /** Truk yang sedang disorot di kanan. 0 artinya belum ada yang dipilih. */
    private int truckId = 0;

    public PanelMaster() {
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        JPanel dua = new JPanel(new GridLayout(1, 2, 16, 0));
        dua.setOpaque(false);
        dua.add(buildRentalColumn());
        dua.add(buildTruckColumn());
        add(dua, BorderLayout.CENTER);

        tableRental.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectRental();
            }
        });
        tableTruk.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectTruck();
            }
        });

        load();
    }

    // ================= bagian kiri: rental =================

    private JPanel buildRentalColumn() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);

        JPanel form = Theme.card();
        JPanel isi = new JPanel(new GridBagLayout());
        isi.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.WEST;

        Theme.placeholder(fNama, "mis. Rental Sinar Jaya");
        fNama.setPreferredSize(new Dimension(240, Theme.FIELD_HEIGHT));
        g.gridx = 0;
        g.gridy = 0;
        g.insets = new Insets(0, 0, 12, 0);
        isi.add(Theme.field("Nama Rental", fNama), g);

        JPanel tombol = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tombol.setOpaque(false);
        JButton btnSimpan = Theme.primary("Tambah / Simpan");
        JButton btnHapus = Theme.plain("Hapus");
        JButton btnBersih = Theme.plain("Bersihkan");
        btnSimpan.addActionListener(e -> saveRental());
        btnHapus.addActionListener(e -> deleteRental());
        btnBersih.addActionListener(e -> clearRentalForm());
        tombol.add(btnSimpan);
        tombol.add(btnHapus);
        tombol.add(btnBersih);

        g.gridy = 1;
        g.insets = new Insets(0, 0, 0, 0);
        isi.add(tombol, g);

        // Pengisi di kanan supaya isian menempel ke kiri, bukan melayang di tengah.
        g.gridx = 1;
        g.gridy = 0;
        g.weightx = 1;
        isi.add(new JLabel(), g);

        form.add(isi, BorderLayout.CENTER);

        Theme.styleTable(tableRental);
        Theme.widths(tableRental, 44, 230);
        Theme.fixedWidth(tableRental, 0, 44);
        JPanel card = Theme.card("Pemilik truk");
        JScrollPane scroll = new JScrollPane(tableRental);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        card.add(scroll, BorderLayout.CENTER);

        p.add(form, BorderLayout.NORTH);
        p.add(card, BorderLayout.CENTER);
        return p;
    }

    // ================= bagian kanan: truk =================

    private JPanel buildTruckColumn() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);

        JPanel form = Theme.card();
        JPanel isi = new JPanel(new GridBagLayout());
        isi.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.WEST;

        Theme.placeholder(fPlat, "mis. BE 8234 HD");
        fPlat.setPreferredSize(new Dimension(200, Theme.FIELD_HEIGHT));
        g.gridx = 0;
        g.gridy = 0;
        g.insets = new Insets(0, 0, 12, 0);
        isi.add(Theme.field("Plat Nomor", fPlat), g);

        // Pemiliknya ditampilkan sebagai keterangan, bukan kotak pilihan. Inilah yang
        // menghapus kesalahan salah pilih: yang tertulis di sini selalu pemilik baris
        // yang sedang disorot di kiri.
        lblPemilik.setFont(Theme.semibold(13f));
        g.gridx = 1;
        g.insets = new Insets(0, 24, 12, 0);
        isi.add(Theme.field("Rental pemiliknya", lblPemilik), g);

        JPanel tombol = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tombol.setOpaque(false);
        JButton btnSimpan = Theme.primary("Tambah / Simpan");
        JButton btnHapus = Theme.plain("Hapus");
        JButton btnBersih = Theme.plain("Bersihkan");
        btnSimpan.addActionListener(e -> saveTruck());
        btnHapus.addActionListener(e -> deleteTruck());
        btnBersih.addActionListener(e -> clearTruckForm());
        JButton btnPindah = Theme.plain("Pindah Pemilik");
        btnPindah.addActionListener(e -> moveTruck());
        tombol.add(btnSimpan);
        tombol.add(btnHapus);
        tombol.add(btnBersih);
        tombol.add(btnPindah);

        lblStatus.setForeground(Theme.DANGER);

        g.gridx = 0;
        g.gridy = 1;
        g.gridwidth = 2;
        g.insets = new Insets(0, 0, 0, 0);
        isi.add(tombol, g);

        // Keterangan status ditaruh di barisnya sendiri, bukan menempel di baris tombol.
        // Di baris tombol, ruang sisanya dipakai bersama-sama, dan begitu ada empat tombol
        // sisanya tinggal sekitar 40 piksel - cukup untuk memotong pesannya di tengah, jadi
        // petunjuk yang justru dibutuhkan operator ("Belum ada pemilik lain...") hanya
        // terbaca separuh. Di barisnya sendiri, seluruh lebar kartu tersedia.
        g.gridx = 0;
        g.gridy = 2;
        g.gridwidth = 3;
        g.insets = new Insets(8, 0, 0, 0);
        isi.add(lblStatus, g);

        g.gridx = 2;
        g.gridy = 0;
        g.gridwidth = 1;
        g.weightx = 1;
        isi.add(new JLabel(), g);

        form.add(isi, BorderLayout.CENTER);

        Theme.styleTable(tableTruk);
        Theme.widths(tableTruk, 44, 170);
        Theme.fixedWidth(tableTruk, 0, 44);
        JPanel card = Theme.card("Truk miliknya");
        JScrollPane scroll = new JScrollPane(tableTruk);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        card.add(scroll, BorderLayout.CENTER);

        p.add(form, BorderLayout.NORTH);
        p.add(card, BorderLayout.CENTER);
        return p;
    }

    // ================= isi data =================

    private void load() {
        try {
            // Nama rental yang sedang disorot diingat dulu, supaya daftar bisa disegarkan
            // tanpa kehilangan baris yang sedang dibuka. Kalau tidak, setiap kali data
            // disimpan pilihannya melompat kembali ke baris pertama.
            int sebelumnya = rentalId;
            int baris = barisRental(sebelumnya);

            modelRental.setRowCount(0);
            for (Rental r : dao.listRental()) {
                modelRental.addRow(new Object[]{r.getRentalId(), r.getRentalName()});
            }

            if (modelRental.getRowCount() == 0) {
                rentalId = 0;
                clearRentalForm();
                loadTruk();
                return;
            }
            if (baris < 0) {
                baris = 0;
            }
            tableRental.setRowSelectionInterval(baris, baris);
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    /** Baris tabel rental yang punya id tertentu, atau -1 kalau tidak ada. */
    private int barisRental(int id) {
        for (int i = 0; i < modelRental.getRowCount(); i++) {
            if (id == Integer.parseInt(String.valueOf(modelRental.getValueAt(i, 0)))) {
                return i;
            }
        }
        return -1;
    }

    /** Isi daftar truk dengan truk milik rental yang sedang disorot. */
    private void loadTruk() {
        modelTruk.setRowCount(0);
        truckId = 0;
        clearTruckForm();
        if (rentalId == 0) {
            lblPemilik.setText("Belum ada yang dipilih");
            lblPemilik.setForeground(Theme.INK_SOFT);
            tableTruk.setEmptyMessage("Pilih dulu pemiliknya di kiri.");
            return;
        }
        try {
            String nama = "";
            for (Rental r : dao.listRental()) {
                if (r.getRentalId() == rentalId) {
                    nama = r.getRentalName();
                }
            }
            lblPemilik.setText(nama);
            lblPemilik.setForeground(Theme.INK);
            for (Truck t : dao.listTrucks()) {
                if (t.getRentalId() != null && t.getRentalId() == rentalId) {
                    modelTruk.addRow(new Object[]{t.getTruckId(), t.getPlate()});
                }
            }
            tableTruk.setEmptyMessage("Belum ada truk untuk " + nama
                    + ". Isi platnya di atas, lalu tekan Tambah / Simpan.");
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    private void selectRental() {
        int i = tableRental.getSelectedRow();
        if (i < 0) {
            return;
        }
        rentalId = Integer.parseInt(String.valueOf(modelRental.getValueAt(i, 0)));
        fNama.setText(str(modelRental.getValueAt(i, 1)));
        setStatus("");
        loadTruk();
    }

    private void selectTruck() {
        int i = tableTruk.getSelectedRow();
        if (i < 0) {
            return;
        }
        truckId = Integer.parseInt(String.valueOf(modelTruk.getValueAt(i, 0)));
        fPlat.setText(str(modelTruk.getValueAt(i, 1)));
        setStatus("");
    }

    // ================= simpan dan hapus =================

    private void saveRental() {
        try {
            Rental r = new Rental();
            r.setRentalId(rentalId);
            r.setRentalName(require(fNama.getText(), "Nama rental"));
            dao.saveRental(r);
            // Setelah disimpan, baris yang sedang dibuka dicari ulang lewat namanya.
            // Rental yang baru dibuat belum punya id di sini, jadi patokannya nama.
            if (rentalId == 0) {
                rentalId = cariIdRental(r.getRentalName());
            }
            setStatus("");
            load();
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    private int cariIdRental(String nama) throws Exception {
        for (Rental r : dao.listRental()) {
            if (r.getRentalName() != null && r.getRentalName().equals(nama)) {
                return r.getRentalId();
            }
        }
        return 0;
    }

    private void deleteRental() {
        if (rentalId == 0) {
            JOptionPane.showMessageDialog(this, "Pilih dulu rental yang mau dihapus.");
            return;
        }
        try {
            // Jumlah truknya disebut lebih dulu. Truk tidak ikut terhapus, hanya jadi
            // tanpa pemilik — dan itu perlu diketahui sebelum menekan Ya, bukan sesudah.
            int truk = 0;
            for (Truck t : dao.listTrucks()) {
                if (t.getRentalId() != null && t.getRentalId() == rentalId) {
                    truk++;
                }
            }
            String pesan = truk == 0
                    ? "Hapus rental ini?"
                    : "Hapus rental ini?\n" + truk + " truknya tidak ikut terhapus, "
                            + "hanya menjadi tanpa pemilik.";
            if (JOptionPane.showConfirmDialog(this, pesan, "Konfirmasi",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }
            dao.deleteRental(rentalId);
            rentalId = 0;
            load();
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    private void clearRentalForm() {
        rentalId = 0;
        fNama.setText("");
        tableRental.clearSelection();
        setStatus("");
        loadTruk();
    }

    private void saveTruck() {
        // Tanpa pemilik yang dipilih, truknya tidak boleh disimpan. Kalau dibiarkan,
        // truk itu tidak akan muncul di laporan milik siapa pun, dan tidak ada yang
        // menyadarinya sampai uangnya ditagih.
        if (rentalId == 0) {
            setStatus("Pilih dulu pemiliknya di kiri.");
            return;
        }
        try {
            Truck t = new Truck();
            t.setTruckId(truckId);
            t.setPlate(require(fPlat.getText(), "Plat nomor"));
            t.setRentalId(rentalId);
            dao.saveTruck(t);
            setStatus("");
            load();
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    private void deleteTruck() {
        if (truckId == 0) {
            JOptionPane.showMessageDialog(this, "Pilih dulu truk yang mau dihapus.");
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Hapus truk ini?", "Konfirmasi",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            dao.deleteTruck(truckId);
            load();
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    private void clearTruckForm() {
        truckId = 0;
        fPlat.setText("");
        tableTruk.clearSelection();
        setStatus("");
    }

    /**
     * Pindahkan truk yang sedang disorot ke pemilik lain.
     *
     * <p>Perpindahan pemilik butuh tombol tersendiri karena bentuk halaman ini memang
     * menghalangi cara lama: dulu pemiliknya dipilih dari kotak pilihan, jadi satu truk
     * bisa dipindah dengan mengubah isi kotak itu. Sekarang pemiliknya diturunkan dari
     * baris yang disorot, dan daftar di kanan hanya memuat truk milik pemilik itu — jadi
     * truknya tidak akan pernah muncul di bawah pemilik tujuannya, dan tanpa tombol ini
     * satu-satunya jalan adalah menghapus lalu menambah ulang.
     *
     * <p>Menghapus lalu menambah ulang bukan pengganti yang setara: baris transaksi lama
     * menyimpan id truknya, dan penghapusan itu mengosongkan id tersebut, sehingga nomor
     * plat hilang dari laporan-laporan lama — uangnya tetap tercatat, tetapi tidak lagi
     * diketahui truk mana yang mengangkutnya.
     *
     * <p>Pemilik tujuannya ditanyakan lebih dulu, tidak ditentukan diam-diam. Memindahkan
     * truk mengubah pemilik di semua laporan yang memuatnya, jadi harus disengaja.
     */
    private void moveTruck() {
        if (truckId == 0) {
            setStatus("Pilih dulu truknya di kanan.");
            return;
        }
        try {
            Truck truk = trukDari(truckId);
            if (truk == null) {
                setStatus("Truknya sudah tidak ada. Tekan Bersihkan lalu pilih lagi.");
                return;
            }
            // Perpindahan tidak boleh sekalian mengganti plat. Kotak isiannya bisa saja
            // sudah diketik ulang untuk mengganti nomor plat, dan mengabaikannya diam-diam
            // berarti ketikan itu hilang tanpa pemberitahuan. Dua maksud berbeda jangan
            // berbagi satu tombol: yang ini memindahkan, bukan mengganti nomor.
            if (!truk.getPlate().equals(fPlat.getText().trim())) {
                setStatus("Simpan dulu perubahan platnya, baru pindahkan.");
                return;
            }
            List<Rental> tujuan = new ArrayList<>();
            for (Rental r : dao.listRental()) {
                if (r.getRentalId() != rentalId) {
                    tujuan.add(r);
                }
            }
            if (tujuan.isEmpty()) {
                setStatus("Belum ada pemilik lain. Tambah dulu pemilik barunya di kiri.");
                return;
            }
            Rental dipilih = (Rental) JOptionPane.showInputDialog(this,
                    "Pindahkan truk " + truk.getPlate() + " ke pemilik mana?",
                    "Pindah Pemilik", JOptionPane.QUESTION_MESSAGE, null,
                    tujuan.toArray(), tujuan.get(0));
            if (dipilih == null) {
                return;
            }
            pindahTruk(truckId, dipilih.getRentalId());
            setStatus("");
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    /**
     * Ganti pemilik sebuah truk, lalu segarkan layar.
     *
     * <p>Nomor platnya dibaca ulang dari yang tersimpan, bukan diambil dari kotak isian
     * atau dari argumen. Kotak isian bisa saja sudah dikosongkan atau diketik ulang, dan
     * nilai kosong itu lolos ke basis data: kolomnya menolak nilai kosong, tetapi teks
     * kosong bukan nilai kosong, jadi platnya tersimpan sebagai teks hampa dan nomor truk
     * itu hilang tanpa satu pun peringatan. Yang diuji jadi bagian yang benar-benar
     * mengubah data, dan tidak ada jalan bagi pengujian untuk menyetor plat yang tidak
     * mungkin berasal dari layar.
     */
    void pindahTruk(int truckIdPindah, int rentalIdBaru) throws Exception {
        Truck t = trukDari(truckIdPindah);
        if (t == null) {
            throw new IllegalArgumentException("truk tidak ditemukan");
        }
        t.setRentalId(rentalIdBaru);
        dao.saveTruck(t);
        load();
    }

    /** Truk yang tersimpan menurut idnya, atau {@code null} kalau tidak ada. */
    private Truck trukDari(int id) throws Exception {
        for (Truck t : dao.listTrucks()) {
            if (t.getTruckId() == id) {
                return t;
            }
        }
        return null;
    }

    private void setStatus(String message) {
        lblStatus.setText(message == null ? "" : message);
    }

    private String require(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " wajib diisi.");
        }
        return value.trim();
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
