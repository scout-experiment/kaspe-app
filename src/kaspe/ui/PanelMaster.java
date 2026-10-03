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
 * sehingga mengetik plat baru lalu menekan tombol simpan tanpa menyentuh kotak itu
 * membuat truk tercatat milik pemilik yang kebetulan tampil pertama — tanpa pesan apa
 * pun, dan uangnya masuk ke pemilik yang salah di laporan. Sekarang pemiliknya
 * diturunkan dari baris yang disorot, jadi tidak ada yang bisa salah pilih.
 *
 * <p>Tombol tambah dan ubah sengaja dipisah. Satu tombol untuk dua maksud
 * membuat tombol tambah menimpa baris yang kebetulan tersorot; sekarang
 * "Tambah ..." selalu membuat baris baru, dan "Simpan Perubahan" hanya
 * mengubah baris yang sedang disorot.
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
            "Belum ada rental. Isi namanya di atas, lalu tekan Tambah Rental.");
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

    // Tombol yang hanya berlaku untuk baris tersorot. Tanpa baris tersorot
    // keduanya mati, supaya maksud "ubah yang ini" tidak bisa tertukar lagi.
    private final JButton btnUbahRental = Theme.plain("Simpan Perubahan");
    private final JButton btnHapusRental = Theme.plain("Hapus");
    private final JButton btnUbahTruk = Theme.plain("Simpan Perubahan");
    private final JButton btnHapusTruk = Theme.plain("Hapus");

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
        JButton btnTambah = Theme.primary("Tambah Rental");
        btnTambah.addActionListener(e -> tambahRental());
        btnUbahRental.addActionListener(e -> ubahRental());
        btnHapusRental.addActionListener(e -> deleteRental());
        tombol.add(btnTambah);
        tombol.add(btnUbahRental);
        tombol.add(btnHapusRental);

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
        JButton btnTambah = Theme.primary("Tambah Truk");
        btnTambah.addActionListener(e -> tambahTruk());
        btnUbahTruk.addActionListener(e -> ubahTruck());
        btnHapusTruk.addActionListener(e -> deleteTruck());
        JButton btnPindah = Theme.plain("Pindah Pemilik");
        btnPindah.addActionListener(e -> moveTruck());
        tombol.add(btnTambah);
        tombol.add(btnUbahTruk);
        tombol.add(btnHapusTruk);
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
                fNama.setText("");
                updateTombolRental();
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

    /** Baris tabel truk yang punya id tertentu, atau -1 kalau tidak ada. */
    private int barisTruk(int id) {
        for (int i = 0; i < modelTruk.getRowCount(); i++) {
            if (id == Integer.parseInt(String.valueOf(modelTruk.getValueAt(i, 0)))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Isi daftar truk dengan truk milik rental yang sedang disorot.
     *
     * <p>Truk yang sedang disorot diingat dulu, seperti daftar rental di {@link #load()}:
     * supaya menambah truk tidak memindahkan pilihan operator.
     */
    private void loadTruk() {
        int sebelumnya = truckId;
        modelTruk.setRowCount(0);
        truckId = 0;
        fPlat.setText("");
        tableTruk.clearSelection();
        setStatus("");
        updateTombolTruk();
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
                    + ". Isi platnya di atas, lalu tekan Tambah Truk.");
            int baris = barisTruk(sebelumnya);
            if (baris >= 0) {
                tableTruk.setRowSelectionInterval(baris, baris);
            }
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    private void selectRental() {
        int i = tableRental.getSelectedRow();
        if (i < 0) {
            rentalId = 0;
            updateTombolRental();
            return;
        }
        rentalId = Integer.parseInt(String.valueOf(modelRental.getValueAt(i, 0)));
        fNama.setText(str(modelRental.getValueAt(i, 1)));
        setStatus("");
        updateTombolRental();
        loadTruk();
    }

    private void selectTruck() {
        int i = tableTruk.getSelectedRow();
        if (i < 0) {
            truckId = 0;
            updateTombolTruk();
            return;
        }
        truckId = Integer.parseInt(String.valueOf(modelTruk.getValueAt(i, 0)));
        fPlat.setText(str(modelTruk.getValueAt(i, 1)));
        setStatus("");
        updateTombolTruk();
    }

    // ================= simpan dan hapus =================

    private void tambahRental() {
        // Selalu INSERT, tidak peduli ada baris yang tersorot. Sebelumnya tambah dan
        // ubah berbagi satu tombol, dan karena load() selalu menyorot baris pertama,
        // mengetik nama baru lalu menekan tombol itu malah MENIMPA rental lama -
        // beserta seluruh riwayat transaksi pemiliknya, yang ikut berganti nama mundur.
        String nama = fNama.getText().trim();
        if (nama.isEmpty()) {
            setStatus("Nama rental wajib diisi.");
            return;
        }
        try {
            Rental r = new Rental();
            r.setRentalName(nama);
            dao.saveRental(r);
            setStatus("");
            // Rental baru belum punya id di sini, jadi barunya dicari lewat namanya.
            rentalId = cariIdRental(nama);
            load();
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    private void ubahRental() {
        if (rentalId == 0) {
            return;
        }
        String nama = fNama.getText().trim();
        if (nama.isEmpty()) {
            setStatus("Nama rental wajib diisi.");
            return;
        }
        try {
            Rental r = new Rental();
            r.setRentalId(rentalId);
            r.setRentalName(nama);
            dao.saveRental(r);
            setStatus("");
            load();
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    /** Id rental menurut nama (tanpa membedakan besar-kecil huruf), atau 0. */
    private int cariIdRental(String nama) throws Exception {
        String kunci = Rental.matchKey(nama);
        for (Rental r : dao.listRental()) {
            if (kunci.equals(Rental.matchKey(r.getRentalName()))) {
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


    private void tambahTruk() {
        // Tanpa pemilik yang dipilih, truknya tidak boleh disimpan. Kalau dibiarkan,
        // truk itu tidak akan muncul di laporan milik siapa pun, dan tidak ada yang
        // menyadarinya sampai uangnya ditagih.
        if (rentalId == 0) {
            setStatus("Pilih dulu pemiliknya di kiri.");
            return;
        }
        String plat = fPlat.getText().trim();
        if (plat.isEmpty()) {
            setStatus("Plat nomor wajib diisi.");
            return;
        }
        try {
            Truck t = new Truck();
            t.setPlate(plat);
            t.setRentalId(rentalId);
            dao.saveTruck(t);
            setStatus("");
            // Truk baru belum punya id di sini, jadi dicari lewat platnya.
            truckId = cariIdTruk(plat);
            load();
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    private void ubahTruck() {
        if (truckId == 0) {
            return;
        }
        String plat = fPlat.getText().trim();
        if (plat.isEmpty()) {
            setStatus("Plat nomor wajib diisi.");
            return;
        }
        try {
            Truck t = new Truck();
            t.setTruckId(truckId);
            t.setPlate(plat);
            t.setRentalId(rentalId);
            dao.saveTruck(t);
            setStatus("");
            load();
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    /** Id truk menurut platnya (bentuk seragam), atau 0. */
    private int cariIdTruk(String plat) throws Exception {
        String kunci = Truck.normalizePlate(plat);
        for (Truck t : dao.listTrucks()) {
            if (kunci.equals(Truck.normalizePlate(t.getPlate()))) {
                return t.getTruckId();
            }
        }
        return 0;
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
                setStatus("Truknya sudah tidak ada. Pilih lagi truknya di daftar.");
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

    /** Hidup-matikan tombol per-baris mengikuti ada/tidaknya baris tersorot. */
    private void updateTombolRental() {
        btnUbahRental.setEnabled(rentalId != 0);
        btnHapusRental.setEnabled(rentalId != 0);
    }

    private void updateTombolTruk() {
        btnUbahTruk.setEnabled(truckId != 0);
        btnHapusTruk.setEnabled(truckId != 0);
    }

    private void setStatus(String message) {
        lblStatus.setText(message == null ? "" : message);
    }


    private String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
