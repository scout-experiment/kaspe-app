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
 * Kelola data truk: daftar semua plat beserta pemiliknya, dalam satu dialog.
 *
 * <p>Menggantikan halaman Data Master. Bentuknya satu tabel dan satu baris isian, bukan
 * dua kolom kembar - yang dulu membuat halaman itu terbaca seperti dua aplikasi
 * berdampingan.
 *
 * <p>Isinya kelas ini sendiri (sebuah {@link JPanel}), dan jendelanya dibuat di
 * {@link #buka}. Dipisah begitu karena {@link JDialog} melempar {@code HeadlessException}
 * tanpa layar, sedangkan uji tampilan berjalan tanpa layar - susunan isi yang sama harus
 * bisa diperiksa di sana. Pola yang sama dipakai {@link PagePanel} terhadap
 * {@link MainFrame}.
 *
 * <p>Yang dipertahankan dari halaman lama, semuanya disengaja:
 * <ul>
 *   <li>Truk baru selalu INSERT, tidak pernah menimpa baris yang kebetulan tersorot.</li>
 *   <li>Pemilik wajib dipilih; tidak ada nilai bawaan. Kotak yang sudah terisi begitu
 *       layar dibuka pernah membuat truk baru tercatat milik pemilik yang kebetulan
 *       tampil pertama, tanpa pesan apa pun.</li>
 *   <li>Pindah pemilik dipisah dari ganti plat: memindah pemilik MENULIS ULANG laporan
 *       per pemilik, jadi dua maksud berbeda tidak boleh berbagi satu tombol.</li>
 *   <li>Menghapus ditolak kalau truknya sudah dipakai catatan pengiriman, dan
 *       penghapusan massal diperiksa SELURUHNYA lebih dulu - lihat {@link #hapusTruk()}.</li>
 * </ul>
 */
public class DialogDataMaster extends JPanel {

    private final MasterDao dao = new MasterDao();

    private final DefaultTableModel modelTruk = new DefaultTableModel(
            new Object[]{"Plat", "Rental"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final Theme.Table tableTruk = new Theme.Table(modelTruk, "Belum ada truk. Isi platnya di atas.");
    private final JTextField fPlat = new JTextField();
    private final JComboBox<Rental> cmbRental = new JComboBox<>();
    private final JLabel lblStatus = new JLabel();
    /** Pemilik baris yang sedang diubah. Ditampilkan sebagai teks, bukan kotak pilihan. */
    private final JLabel lblPemilik = new JLabel();

    private final JButton btnTambah = Theme.primary("Tambah Truk");
    private final JButton btnUbah = Theme.plain("Ubah");
    private final JButton btnHapus = Theme.plain("Hapus");
    private final JButton btnPindah = Theme.plain("Pindah Pemilik");
    private final JButton btnSimpan = Theme.primary("Simpan Perubahan");
    private final JButton btnBatal = Theme.plain("Batal");
    private final JButton btnHapusPlat = Theme.plain("\u00d7");

    /** Id truk baris yang sedang diubah, 0 kalau tidak ada. */
    private int truckId = 0;
    /** Pemilik truk yang sedang diubah, dipakai supaya menyimpan tidak ikut memindahkannya. */
    private Integer pemilikTruk = null;
    /** true = baris isian sedang dipakai mengubah baris yang dipilih. */
    private boolean ubahMode = false;
    /** Panel tombol yang berganti isi menurut mode. */
    private final JPanel barisTombol = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JPanel barisKotak = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    /** Pemilik yang tersimpan di tiap baris, sejajar nomor barisnya. */
    private final List<Truck> truk = new ArrayList<>();

    public DialogDataMaster() {
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 14, 16));

        Theme.placeholder(fPlat, "mis. BE 8234 HD");
        fPlat.setPreferredSize(new Dimension(150, Theme.FIELD_HEIGHT));
        // Pemilik TIDAK terisi sendiri. Operator harus memilihnya; tombol tambah menolak
        // selama kotaknya kosong.
        cmbRental.setEditable(true);
        cmbRental.setPreferredSize(new Dimension(180, Theme.FIELD_HEIGHT));
        lblPemilik.setFont(Theme.semibold(13f));
        lblPemilik.setForeground(Theme.INK);

        btnHapusPlat.setPreferredSize(new Dimension(38, Theme.FIELD_HEIGHT));
        btnHapusPlat.setToolTipText("Hapus plat yang sedang tertulis di kotak");
        lblStatus.setForeground(Theme.DANGER);

        btnTambah.addActionListener(e -> tambahTruk());
        btnUbah.addActionListener(e -> masukUbah());
        btnSimpan.addActionListener(e -> simpanPerubahan());
        btnBatal.addActionListener(e -> keluarUbah());
        btnHapus.addActionListener(e -> hapusTruk());
        btnPindah.addActionListener(e -> pindahPemilik());
        btnHapusPlat.addActionListener(e -> hapusPlatDiKotak());

        Theme.styleTable(tableTruk);
        Theme.widths(tableTruk, 170, 300);
        // Theme.styleTable memasang pemilihan TUNGGAL untuk semua tabel. "Hapus" di sini
        // harus bisa banyak baris sekaligus, seperti daftar transaksi tersimpan.
        tableTruk.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tableTruk.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                perbaruiTombol();
            }
        });

        add(buildIsi(), BorderLayout.CENTER);
        add(buildKaki(), BorderLayout.SOUTH);
        muat();
        pasangMode(false);
    }

    /** Susunan dialog lengkap dengan isi kelas ini sebagai panelnya. */
    public static void buka(Window owner) {
        JDialog dialog = new JDialog(owner, "Kelola Data Truk", Dialog.ModalityType.APPLICATION_MODAL);
        DialogDataMaster isi = new DialogDataMaster();
        dialog.setContentPane(isi);
        dialog.pack();
        dialog.setSize(Math.max(720, isi.getPreferredSize().width),
                Math.max(520, isi.getPreferredSize().height));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    // ---------- susunan ----------

    private JPanel buildIsi() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        p.add(buildBaris(), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(tableTruk);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildBaris() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        barisKotak.setOpaque(false);
        barisTombol.setOpaque(false);

        p.add(barisKotak, BorderLayout.NORTH);
        p.add(barisTombol, BorderLayout.CENTER);

        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        status.setOpaque(false);
        status.add(lblStatus);
        p.add(status, BorderLayout.SOUTH);
        return p;
    }

    /**
     * Isi baris isian dan baris tombol menurut mode.
     *
     * <p>Mode biasa: kotak pemilik bisa dipilih, tombolnya Tambah/Ubah/Pindah/Hapus.
     * Mode ubah: pemiliknya hanya DITAMPILKAN, dan tombolnya Simpan Perubahan/Batal.
     *
     * <p>Pemilik sengaja tidak bisa diganti dari mode ubah: menyimpan di sini menjalankan
     * {@code UPDATE truk SET plat=?, id_rental=?} sekaligus, jadi mengganti pemiliknya
     * berarti MEMINDAHKAN truk - dan itu menulis ulang laporan per pemilik, tanpa
     * konfirmasi apa pun. Pemindahan hanya lewat "Pindah Pemilik".
     */
    private void pasangMode(boolean ubah) {
        ubahMode = ubah;
        barisKotak.removeAll();
        barisKotak.add(Theme.field("Plat Nomor", fPlat));
        fPlat.setPreferredSize(new Dimension(150, Theme.FIELD_HEIGHT));
        if (ubah) {
            barisKotak.add(btnHapusPlat);
            barisKotak.add(btnSimpan);
            barisKotak.add(btnBatal);
            barisKotak.add(Theme.field("Rental pemiliknya", lblPemilik));
            lblPemilik.setText(pemilikTruk == null ? "-" : namaPemilik(pemilikTruk));
        } else {
            barisKotak.add(btnHapusPlat);
            barisKotak.add(btnTambah);
            barisKotak.add(Theme.field("Rental pemiliknya", cmbRental));
        }

        barisTombol.removeAll();
        if (ubah) {
            barisTombol.add(btnPindah);
        } else {
            barisTombol.add(btnUbah);
            barisTombol.add(btnPindah);
            barisTombol.add(btnHapus);
        }
        barisKotak.revalidate();
        barisKotak.repaint();
        barisTombol.revalidate();
        barisTombol.repaint();
        perbaruiTombol();
    }

    private JPanel buildKaki() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        JPanel kiri = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        kiri.setOpaque(false);
        JButton kelola = Theme.plain("Kelola Pemilik...");
        kelola.addActionListener(e -> DialogPemilik.buka(SwingUtilities.getWindowAncestor(this), this::muat));
        kiri.add(kelola);
        kiri.add(Theme.caption("Pemilik yang masih punya truk tidak bisa dihapus."));
        p.add(kiri, BorderLayout.WEST);
        JPanel kanan = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        kanan.setOpaque(false);
        JButton tutup = Theme.plain("Tutup");
        tutup.addActionListener(e -> tutupJendela());
        kanan.add(tutup);
        p.add(kanan, BorderLayout.EAST);
        return p;
    }

    private void tutupJendela() {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w instanceof JDialog) {
            ((JDialog) w).dispose();
        }
    }

    // ---------- data ----------

    /** Muat ulang daftar truk dan daftar pemilik, tanpa kehilangan baris yang disorot. */
    final void muat() {
        try {
            int sebelumnya = truckId;
            Object pemilikSebelumnya = cmbRental.getSelectedItem();

            modelTruk.setRowCount(0);
            truk.clear();
            for (Truck t : dao.listTrucks()) {
                truk.add(t);
                modelTruk.addRow(new Object[]{t.getPlate(),
                        t.getRentalName() == null ? "(tanpa pemilik)" : t.getRentalName()});
            }

            cmbRental.removeAllItems();
            for (Rental r : dao.listRental()) {
                cmbRental.addItem(r);
            }
            // Pilihan pemilik dipulihkan setelah daftarnya dibangun ulang - kalau tidak,
            // setiap kali data dimuat pilihannya melompat ke pemilik pertama.
            //
            // Kalau memang belum ada pilihan sebelumnya, pilihannya DIKOSONGKAN. JComboBox
            // memilih baris pertama sendiri begitu diisi, dan pemilik yang sudah terisi
            // begitu layar dibuka adalah cacat lama: truk baru langsung tercatat milik
            // pemilik yang kebetulan tampil pertama, tanpa pesan apa pun.
            if (!pulihkanPemilik(pemilikSebelumnya)) {
                cmbRental.setSelectedIndex(-1);
                cmbRental.getEditor().setItem("");
            }

            int baris = barisTruk(sebelumnya);
            if (baris >= 0) {
                tableTruk.setRowSelectionInterval(baris, baris);
            }
            perbaruiTombol();
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    /** Pulihkan pilihan pemilik ke pemilik {@code sebelumnya}; false kalau tidak ketemu. */
    private boolean pulihkanPemilik(Object sebelumnya) {
        if (!(sebelumnya instanceof Rental) || cmbRental.getItemCount() == 0) {
            return false;
        }
        int id = ((Rental) sebelumnya).getRentalId();
        for (int i = 0; i < cmbRental.getItemCount(); i++) {
            if (cmbRental.getItemAt(i).getRentalId() == id) {
                cmbRental.setSelectedIndex(i);
                return true;
            }
        }
        return false;
    }

    private int barisTruk(int id) {
        for (int i = 0; i < truk.size(); i++) {
            if (truk.get(i).getTruckId() == id) {
                return i;
            }
        }
        return -1;
    }

    private String namaPemilik(int rentalId) {
        for (int i = 0; i < cmbRental.getItemCount(); i++) {
            if (cmbRental.getItemAt(i).getRentalId() == rentalId) {
                return cmbRental.getItemAt(i).getRentalName();
            }
        }
        return "-";
    }

    private void perbaruiTombol() {
        // "Ubah" hanya hidup kalau TEPAT satu baris dipilih: kalau dua, tidak jelas mana
        // yang mau diubah. "Hapus" boleh satu atau banyak.
        int jumlah = tableTruk.getSelectedRowCount();
        btnUbah.setEnabled(!ubahMode && jumlah == 1);
        btnHapus.setEnabled(!ubahMode && jumlah > 0);
        btnPindah.setEnabled(jumlah == 1);
        // Ikon x hanya berlaku kalau isi kotaknya memang plat yang sudah terdaftar.
        btnHapusPlat.setEnabled(cariTruk(fPlat.getText().trim()) != null);
    }

    /** Truk tersimpan menurut platnya (ejaan diseragamkan), atau null. */
    private Truck cariTruk(String plat) {
        if (plat == null || plat.trim().isEmpty()) {
            return null;
        }
        String kunci = Truck.normalizePlate(plat);
        for (Truck t : truk) {
            if (kunci.equals(Truck.normalizePlate(t.getPlate()))) {
                return t;
            }
        }
        return null;
    }

    private void setStatus(String pesan) {
        lblStatus.setText(pesan == null ? "" : pesan);
    }

    // ---------- aksi ----------

    /** Tambah truk. Selalu INSERT, tidak peduli ada baris yang tersorot. */
    private void tambahTruk() {
        String plat = fPlat.getText().trim();
        if (plat.isEmpty()) {
            setStatus("Plat nomor wajib diisi.");
            return;
        }
        Object pilihan = cmbRental.isEditable() ? cmbRental.getEditor().getItem() : cmbRental.getSelectedItem();
        String nama = pilihan == null ? "" : pilihan.toString().trim();
        if (nama.isEmpty()) {
            // Tanpa pemilik, truknya tidak muncul di laporan milik siapa pun dan tidak ada
            // yang menyadarinya sampai uangnya ditagih.
            setStatus("Pilih dulu pemiliknya.");
            return;
        }
        try {
            Rental pemilik = pastikanRental(nama);
            Truck t = new Truck();
            t.setPlate(plat);
            t.setRentalId(pemilik.getRentalId());
            dao.saveTruck(t);
            setStatus("");
            fPlat.setText("");
            muat();
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    /**
     * Pemilik menurut namanya, dibuat kalau belum ada.
     *
     * <p>Pencocokannya lewat {@link Rental#matchKey}, bukan {@code WHERE nama=?}: beda
     * besar-kecil huruf atau spasi berlebih tidak boleh melahirkan pemilik kedua yang
     * memecah rekap uangnya.
     */
    private Rental pastikanRental(String nama) throws Exception {
        String kunci = Rental.matchKey(nama);
        for (Rental r : dao.listRental()) {
            if (kunci.equals(Rental.matchKey(r.getRentalName()))) {
                return r;
            }
        }
        Rental baru = new Rental();
        baru.setRentalName(nama);
        dao.saveRental(baru);
        for (Rental r : dao.listRental()) {
            if (kunci.equals(Rental.matchKey(r.getRentalName()))) {
                return r;
            }
        }
        throw new IllegalStateException("Pemilik \"" + nama + "\" gagal disimpan.");
    }

    /** Masuk mode ubah untuk baris yang tersorot. */
    private void masukUbah() {
        if (tableTruk.getSelectedRowCount() != 1) {
            setStatus("Pilih satu baris yang mau diubah.");
            return;
        }
        int model = tableTruk.convertRowIndexToModel(tableTruk.getSelectedRow());
        Truck t = truk.get(model);
        truckId = t.getTruckId();
        pemilikTruk = t.getRentalId();
        fPlat.setText(t.getPlate());
        setStatus("");
        pasangMode(true);
    }

    /** Keluar dari mode ubah tanpa menyimpan apa pun. */
    private void keluarUbah() {
        truckId = 0;
        pemilikTruk = null;
        fPlat.setText("");
        setStatus("");
        pasangMode(false);
    }

    /**
     * Simpan perubahan plat baris yang sedang diubah.
     *
     * <p>Pemiliknya diisi ulang dengan pemilik yang SAMA, bukan diambil dari kotak - kotak
     * pemiliknya memang tidak ada di mode ini. Mengubah plat dan memindahkan pemilik adalah
     * dua maksud berbeda, dan yang ini hanya mengubah plat.
     */
    private void simpanPerubahan() {
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
            t.setRentalId(pemilikTruk);
            dao.saveTruck(t);
            setStatus("");
            fPlat.setText("");
            truckId = 0;
            pemilikTruk = null;
            pasangMode(false);
            muat();
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    /**
     * Hapus plat yang sedang tertulis di kotak.
     *
     * <p>Sama seperti "Hapus", tapi sasarannya kotak isian, bukan baris yang disorot -
     * inilah gunanya ikon x di samping kotak plat.
     */
    private void hapusPlatDiKotak() {
        Truck t = cariTruk(fPlat.getText().trim());
        if (t == null) {
            setStatus("Plat itu tidak ada di daftar.");
            return;
        }
        hapusSatu(t);
    }

    /**
     * Hapus baris yang dipilih, boleh banyak sekaligus.
     *
     * <p>Seluruh baris diperiksa LEBIH DULU, sebelum satu pun dihapus. Tiap panggilan
     * {@link MasterDao#deleteTruck(int)} membuka koneksinya sendiri dan meng-commit
     * sendiri, jadi menghapus satu per satu TIDAK atomik: kalau baris ketiga ditolak, dua
     * yang pertama sudah terhapus dan tidak bisa dibatalkan - menyisakan daftar setengah
     * jadi yang tidak bisa direkonstruksi operator. Karena itu penolakannya diperiksa
     * untuk SEMUA baris dulu, dan seluruh penghapusan dibatalkan begitu ada satu saja yang
     * terhalang.
     */
    private void hapusTruk() {
        List<Truck> dipilih = new ArrayList<>();
        for (int baris : tableTruk.getSelectedRows()) {
            dipilih.add(truk.get(tableTruk.convertRowIndexToModel(baris)));
        }
        if (dipilih.isEmpty()) {
            setStatus("Pilih dulu truk yang mau dihapus.");
            return;
        }
        try {
            List<String> terhalang = new ArrayList<>();
            for (Truck t : dipilih) {
                String penolakan = dao.truckDeleteRefusal(t.getTruckId());
                if (penolakan != null) {
                    terhalang.add(t.getPlate());
                }
            }
            if (!terhalang.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Tidak ada yang dihapus. Plat berikut masih dipakai catatan pengiriman: "
                                + String.join(", ", terhalang)
                                + ".\n\nMenghapusnya akan menghilangkan platnya dari catatan yang sudah "
                                + "ada, termasuk laporan yang sudah dicetak.",
                        "Tidak bisa dihapus", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String pesan = dipilih.size() == 1
                    ? "Hapus truk " + dipilih.get(0).getPlate() + "?"
                    : "Hapus " + dipilih.size() + " truk sekaligus?";
            if (JOptionPane.showConfirmDialog(this, pesan, "Konfirmasi",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }
            for (Truck t : dipilih) {
                dao.deleteTruck(t.getTruckId());
            }
            setStatus("");
            muat();
        } catch (IllegalStateException e) {
            // Penolakan yang disengaja, bukan galat: tidak ada yang berubah, jadi prefiks
            // "Gagal: " dari Theme.showError akan terbaca seperti programnya rusak.
            JOptionPane.showMessageDialog(this, e.getMessage(), "Tidak bisa dihapus",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    /** Hapus satu truk lewat ikon x, dengan penolakan dan konfirmasi yang sama. */
    private void hapusSatu(Truck t) {
        try {
            String penolakan = dao.truckDeleteRefusal(t.getTruckId());
            if (penolakan != null) {
                JOptionPane.showMessageDialog(this, penolakan, "Tidak bisa dihapus",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (JOptionPane.showConfirmDialog(this, "Hapus truk " + t.getPlate() + "?",
                    "Konfirmasi", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }
            dao.deleteTruck(t.getTruckId());
            setStatus("");
            fPlat.setText("");
            muat();
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Tidak bisa dihapus",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    /**
     * Pindah pemilik truk yang tersorot, lalu segarkan layar.
     *
     * <p>Terpisah dari penyimpanan perubahan plat: memindah pemilik MENULIS ULANG laporan
     * per pemilik, jadi harus disengaja dan dikonfirmasi - dua maksud berbeda tidak boleh
     * berbagi satu tombol.
     */
    private void pindahPemilik() {
        if (tableTruk.getSelectedRowCount() != 1) {
            setStatus("Pilih satu truk yang mau dipindah.");
            return;
        }
        if (ubahMode) {
            setStatus("Simpan dulu perubahan platnya, baru pindahkan.");
            return;
        }
        Truck t = truk.get(tableTruk.convertRowIndexToModel(tableTruk.getSelectedRow()));
        try {
            List<Rental> tujuan = new ArrayList<>();
            for (Rental r : dao.listRental()) {
                if (t.getRentalId() == null || r.getRentalId() != t.getRentalId()) {
                    tujuan.add(r);
                }
            }
            if (tujuan.isEmpty()) {
                setStatus("Belum ada pemilik lain. Tambah dulu pemilik barunya lewat Kelola Pemilik.");
                return;
            }
            Rental dipilih = (Rental) JOptionPane.showInputDialog(this,
                    "Pindahkan truk " + t.getPlate() + " ke pemilik mana?",
                    "Pindah Pemilik", JOptionPane.QUESTION_MESSAGE, null,
                    tujuan.toArray(), tujuan.get(0));
            if (dipilih == null) {
                return;
            }
            int jwb = JOptionPane.showConfirmDialog(this,
                    "Truk " + t.getPlate() + " dipindah ke \"" + dipilih.getRentalName() + "\".\n"
                            + "Seluruh laporan lama truk ini akan ikut terbaca sebagai milik "
                            + "\"" + dipilih.getRentalName() + "\". Lanjut?",
                    "Konfirmasi pindah pemilik", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (jwb != JOptionPane.YES_OPTION) {
                return;
            }
            pindahTruk(t.getTruckId(), dipilih.getRentalId());
        } catch (Exception e) {
            Theme.showError(this, e);
        }
    }

    /** Pindahkan satu truk ke pemilik lain. Dipisah supaya bisa diuji tanpa dialog. */
    void pindahTruk(int truckIdPindah, int rentalIdBaru) throws Exception {
        Truck t = null;
        for (Truck k : dao.listTrucks()) {
            if (k.getTruckId() == truckIdPindah) {
                t = k;
            }
        }
        if (t == null) {
            throw new IllegalArgumentException("truk tidak ditemukan");
        }
        // Platnya dibaca ulang dari yang TERSIMPAN, bukan dari kotak isian: kotak bisa saja
        // sudah dikosongkan atau diketik ulang, dan teks kosong bukan nilai kosong - kolomnya
        // menolak null, tetapi "" lolos dan menghapus nomor truknya tanpa peringatan.
        t.setRentalId(rentalIdBaru);
        dao.saveTruck(t);
        setStatus("");
        muat();
    }
}