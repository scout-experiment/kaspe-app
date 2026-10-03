package kaspe.test;

import com.formdev.flatlaf.FlatClientProperties;
import kaspe.Calculator;
import kaspe.Db;
import kaspe.Schema;
import kaspe.dao.MasterDao;
import kaspe.dao.TransactionDao;
import kaspe.model.Rental;
import kaspe.model.Transaction;
import kaspe.model.TransactionDetail;
import kaspe.model.Truck;
import kaspe.ui.PagePanel;
import kaspe.ui.PanelTransaction;
import kaspe.ui.Theme;
import kaspe.util.Dates;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Uji alur layar transaksi: menambah baris tidak boleh menulis data master,
 * rental kosong harus ditolak, dan tanggal yang tidak valid harus ditolak.
 *
 * <p>Yang diuji tingkah laku yang terlihat dari luar (baris tabel, pesan status,
 * jumlah baris di database), bukan isi dalam metodenya — supaya kegagalan layar
 * yang tetap tergambar rapi pun ikut tertangkap.
 *
 * <p>Memakai H2 dalam memori, sama seperti TestDatabase, lewat
 * {@code Db.setConfiguration(...)}.
 *
 * Jalankan: java -Djava.awt.headless=true -cp build:lib/h2-2.1.214.jar kaspe.test.TestAlur
 */
public class TestAlur {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        // Dipaksa tanpa layar sebelum AWT dipakai. Uji ini menyentuh tombol Simpan, dan
        // jalur suksesnya menampilkan jendela pesan yang MODAL. Di komputer berlayar,
        // jendela itu menunggu ditekan selamanya - uji yang dijalankan langsung tanpa
        // tanda -Djava.awt.headless=true akan MENGGANTUNG, bukan gagal. Dipasang di sini,
        // bukan hanya di test.sh, supaya menjalankannya dengan tangan pun tetap aman.
        System.setProperty("java.awt.headless", "true");

        System.out.println("=== UJI ALUR LAYAR TRANSAKSI ===\n");

        Theme.install();
        Db.setConfiguration("org.h2.Driver",
                "jdbc:h2:mem:alur;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        createSchema();
        isiMaster();

        tanggalDibacaKetat();
        tambahBarisTanpaMenulisMaster();
        rentalKosongDitolak();
        pemilikBedaDitolak();
        tanggalLunasDitolakKalauSalah();
        tanggalNotaDitolakKalauSalah();
        pindahMenuTidakMembuangPekerjaan();
        kerjaBelumDisimpanTerdeteksi();
        belumLunasTersimpan();
        hapusTransaksiLewatDao();
        simpanSuksesTidakDobel();

        System.out.println("\n=== HASIL: " + passed + " lulus, " + failed + " gagal ===");
        if (failed > 0) {
            System.exit(1);
        }
    }


    /** Dates.parse ketat: tanggal mustahil ditolak, bukan digeser atau diam-diam sah. */
    private static void tanggalDibacaKetat() {
        System.out.println("1. Pembacaan tanggal ketat (Dates) ...");
        record(Dates.parse("31-02-2026") == null,
                "parse menolak 31-02-2026 (tidak dinormalkan jadi 28-02)");
        record(Dates.parse("5-10-26") == null,
                "parse menolak tahun dua angka (5-10-26)");
        record(LocalDate.of(2026, 10, 5).equals(Dates.parse("05-10-2026")),
                "parse menerima 05-10-2026");
        record(LocalDate.of(2026, 10, 5).equals(Dates.parse("5-10-2026")),
                "parse menerima 5-10-2026");
        record(LocalDate.of(2026, 10, 5).equals(Dates.parse("5/10/2026")),
                "parse tetap menerima bentuk yang sudah didukung (5/10/2026)");
        record(LocalDate.of(2026, 10, 5).equals(Dates.parseInput("05-10-2026")),
                "parseInput (kotak tanggal) menerima 05-10-2026");
        record(Dates.parseInput("31-02-2026") == null,
                "parseInput menolak 31-02-2026");
        record(Dates.parseInput("5-10-26") == null,
                "parseInput menolak 5-10-26");
        record(Dates.parseInput("5/10/2026") == null,
                "parseInput menolak bentuk lain (5/10/2026)");
        record(Dates.parseInput("2026-10-05") == null,
                "parseInput menolak bentuk lain (2026-10-05)");
        System.out.println();
    }

    /**
     * Menekan Tambah Baris tidak boleh menambah apa pun ke tabel rental dan truk.
     *
     * <p>Dulu addRow mencari/membuat rental dan truk di database begitu baris
     * dicoba — walaupun transaksinya tidak pernah disimpan, barisnya dihapus,
     * atau halamannya ditinggalkan. Rental dan truk hantu menumpuk di data master
     * dan rekap uang per pemilik terpecah. Plat yang belum dikenal sekarang
     * ditandai truckId 0; TransactionDao yang membuat truknya saat disimpan.
     */
    private static void tambahBarisTanpaMenulisMaster() throws Exception {
        System.out.println("2. Tambah Baris tidak menulis data master ...");
        PanelTransaction p = new PanelTransaction();
        int rentalSebelum = jumlahBaris("SELECT COUNT(*) FROM rental");
        int trukSebelum = jumlahBaris("SELECT COUNT(*) FROM truk");

        ketikPlat(p, "zz  1111  zz");
        ketikRental(p, "Rental  Belum  Pernah Ada");
        isiAngka(p);
        setTanggal(p, "spPaid", "05-10-2026");
        tambahBaris(p);

        int rentalSesudah = jumlahBaris("SELECT COUNT(*) FROM rental");
        int trukSesudah = jumlahBaris("SELECT COUNT(*) FROM truk");
        record(rentalSesudah == rentalSebelum,
                "tabel rental tidak bertambah (" + rentalSebelum + " -> " + rentalSesudah + ")");
        record(trukSesudah == trukSebelum,
                "tabel truk tidak bertambah (" + trukSebelum + " -> " + trukSesudah + ")");

        TransactionDetail d = satuSatuBaris(p);
        record(d != null && d.getTruckId() != null && d.getTruckId() == 0,
                "baris baru ditandai truckId 0 (belum dikenal)");
        record(d != null && "ZZ 1111 ZZ".equals(d.getPlate()),
                "plat ternormalisasi (zz  1111  zz -> ZZ 1111 ZZ)");
        record(d != null && "Rental Belum Pernah Ada".equals(d.getRentalName()),
                "nama rental ternormalisasi sesuai ketikan");
        System.out.println();
    }

    /** Rental kosong harus ditolak dengan tanda merah, seperti kotak angka. */
    private static void rentalKosongDitolak() throws Exception {
        System.out.println("3. Rental kosong ditolak ...");
        PanelTransaction p = new PanelTransaction();

        // Plat baru otomatis mengosongkan pilihan rental, persis keadaan operator
        // yang mengetik plat lalu langsung menekan Tambah Baris.
        ketikPlat(p, "ZZ 2222 ZZ");
        isiAngka(p);
        setTanggal(p, "spPaid", "05-10-2026");
        tambahBaris(p);

        record(satuSatuBaris(p) == null, "baris tidak ditambahkan");
        record(!pesan(p).isEmpty(), "penolakan disertai pesan status: \"" + pesan(p) + "\"");
        record("error".equals(outline(field(p, "cmbRental"))),
                "kotak rental ditandai merah");

        // Nama berisi spasi saja juga ditolak, bukan dianggap terisi.
        ketikRental(p, "   ");
        tambahBaris(p);
        record(satuSatuBaris(p) == null, "rental berisi spasi saja juga ditolak");
        System.out.println();
    }

    /**
     * Plat yang sudah dikenal + rental yang berbeda dari pemilik tersimpan harus
     * ditolak dengan pesan jelas — bukan diam-diam memakai pemilik lama.
     *
     * <p>Sebaliknya, rental yang sama meski ejaan hurufnya berbeda harus diterima.
     */
    private static void pemilikBedaDitolak() throws Exception {
        System.out.println("4. Plat dikenal + rental berbeda ...");
        PanelTransaction p = new PanelTransaction();

        ketikPlat(p, "kb 8234 hd");
        ketikRental(p, "Rental Lain Sekali");
        isiAngka(p);
        setTanggal(p, "spPaid", "05-10-2026");
        tambahBaris(p);

        record(satuSatuBaris(p) == null, "baris tidak ditambahkan");
        String pesan = pesan(p);
        record(pesan.contains("Rental Sinar Jaya") && pesan.contains("Pindah Pemilik"),
                "pesan menunjuk pemilik tersimpan dan jalannya: \"" + pesan + "\"");

        // Ejaan yang sama walau hurufnya berbeda tetap diterima, dan truknya
        // memakai nomor yang sudah tersimpan — bukan 0.
        ketikRental(p, "rental  sinar  jaya");
        tambahBaris(p);
        TransactionDetail d = satuSatuBaris(p);
        record(d != null, "rental yang sama (huruf beda) tetap diterima");
        record(d != null && d.getTruckId() != null && d.getTruckId() != 0,
                "plat yang dikenal memakai nomor truk tersimpan (bukan 0)");
        System.out.println();
    }

    /** Tanggal lunas yang tidak valid harus ditolak, tanggal yang valid diterima. */
    private static void tanggalLunasDitolakKalauSalah() throws Exception {
        System.out.println("5. Tanggal lunas pada Tambah Baris ...");
        PanelTransaction p = new PanelTransaction();
        ketikPlat(p, "ZZ 3333 ZZ");
        ketikRental(p, "Rental Uji Tanggal");
        isiAngka(p);

        String[] salah = {"31-02-2026", "5-10-26", "5/10/2026", "2026-10-05"};
        for (String teks : salah) {
            setTanggal(p, "spPaid", teks);
            tambahBaris(p);
            record(satuSatuBaris(p) == null, "ditolak: " + teks);
        }
        record(!pesan(p).isEmpty(), "penolakan disertai pesan: \"" + pesan(p) + "\"");
        record("error".equals(outline(kotakTanggal(p, "spPaid"))),
                "kotak tanggal lunas ditandai merah");

        setTanggal(p, "spPaid", "05-10-2026");
        tambahBaris(p);
        TransactionDetail d = satuSatuBaris(p);
        record(d != null, "diterima: 05-10-2026");
        record(d != null && LocalDate.of(2026, 10, 5).equals(d.getPaymentDate()),
                "tanggal yang diterima benar-benar dipakai (05-10-2026)");
        System.out.println();
    }

    /**
     * Simpan Transaksi dengan tanggal nota yang tidak valid harus ditolak juga —
     * bukan diam-diam memakai tanggal lama yang tertinggal di model.
     */
    private static void tanggalNotaDitolakKalauSalah() throws Exception {
        System.out.println("6. Tanggal nota pada Simpan Transaksi ...");
        PanelTransaction p = new PanelTransaction();
        ketikPlat(p, "ZZ 4444 ZZ");
        ketikRental(p, "Rental Uji Simpan");
        isiAngka(p);
        setTanggal(p, "spPaid", "05-10-2026");
        tambahBaris(p);
        if (satuSatuBaris(p) == null) {
            record(false, "baris prasyarat masuk daftar");
            System.out.println();
            return;
        }

        setTanggal(p, "spDate", "31-02-2026");
        try {
            klik(p, "save");
        } catch (Exception e) {
            Throwable sebab = e.getCause() == null ? e : e.getCause();
            record(false, "simpan dengan tanggal salah malah menggagalkan uji: " + sebab);
            System.out.println();
            return;
        }
        record(!pesan(p).isEmpty(), "penolakan disertai pesan: \"" + pesan(p) + "\"");
        record(satuSatuBaris(p) != null, "baris tidak dibuang oleh penolakan");
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == 0,
                "tidak ada transaksi yang tersimpan");
        System.out.println();
    }

    /**
     * Pindah menu lalu kembali: baris yang belum disimpan harus masih ada.
     *
     * <p>Dulu setiap klik menu membuat PanelTransaction baru, sehingga baris yang
     * belum disimpan hilang tanpa peringatan. Sekarang halaman transaksi dipakai
     * lagi, dan daftar plat/rentalnya disegarkan tanpa mengubah yang sedang
     * tertulis.
     */
    private static void pindahMenuTidakMembuangPekerjaan() throws Exception {
        System.out.println("7. Pindah menu lalu kembali ...");
        PagePanel halaman = new PagePanel();
        JPanel layar = PagePanel.shell(halaman);
        klikMenu(layar, "Transaksi");
        PanelTransaction p = cariPanelTransaksi(halaman);
        record(p != null, "halaman transaksi terbuka");

        ketikPlat(p, "ZZ 5555 ZZ");
        ketikRental(p, "Rental Uji Navigasi");
        isiAngka(p);
        setTanggal(p, "spPaid", "05-10-2026");
        tambahBaris(p);
        record(satuSatuBaris(p) != null, "satu baris masuk daftar");

        // Tulisan yang sedang diketik (belum jadi baris) juga tidak boleh hilang.
        ketikPlat(p, "QQ 8888 QQ");
        ketikRental(p, "Rental Sedang Diketik");

        klikMenu(layar, "Beranda");
        klikMenu(layar, "Transaksi");
        PanelTransaction pLagi = cariPanelTransaksi(halaman);

        record(pLagi == p, "panel transaksi dipakai lagi, bukan dibuat baru");
        record(pLagi != null && satuSatuBaris(pLagi) != null,
                "baris yang belum disimpan masih ada");
        record(pLagi != null && "QQ 8888 QQ".equals(textPlat(pLagi)),
                "plat yang sedang diketik tidak berubah");
        record(pLagi != null && "Rental Sedang Diketik".equals(textRental(pLagi)),
                "rental yang sedang diketik tidak berubah");

        // Daftar plat tetap mengikuti data master terbaru, tanpa mengubah pekerjaan.
        Rental rBaru = new Rental();
        rBaru.setRentalName("Rental Baru Menyusul");
        new MasterDao().saveRental(rBaru);
        int idRentalBaru = 0;
        for (Rental x : new MasterDao().listRental()) {
            if ("Rental Baru Menyusul".equals(x.getRentalName())) {
                idRentalBaru = x.getRentalId();
            }
        }
        Truck tBaru = new Truck();
        tBaru.setPlate("NB 9001 NB");
        tBaru.setRentalId(idRentalBaru);
        new MasterDao().saveTruck(tBaru);

        klikMenu(layar, "Beranda");
        klikMenu(layar, "Transaksi");
        PanelTransaction pLagi2 = cariPanelTransaksi(halaman);
        record(pLagi2 == p, "panel tetap dipakai lagi setelah data master bertambah");
        JComboBox<?> daftarPlat = (JComboBox<?>) field(p, "cmbPlate");
        boolean platBaruMuncul = false;
        for (int i = 0; i < daftarPlat.getItemCount(); i++) {
            if ("NB 9001 NB".equals(daftarPlat.getItemAt(i))) {
                platBaruMuncul = true;
            }
        }
        record(platBaruMuncul, "plat baru dari data master muncul di daftar");
        record(satuSatuBaris(p) != null, "baris daftar tetap utuh setelah penyegaran");
        record("QQ 8888 QQ".equals(textPlat(p)),
                "plat yang sedang diketik tetap tidak berubah setelah penyegaran");
        System.out.println();
    }

    /**
     * adaKerjaBelumDisimpan: dipakai bilah menu sebelum pindah halaman dan jendela
     * utama sebelum ditutup. Harus benar saat ada baris belum disimpan ATAU form
     * masih terisi, dan salah saat bersih.
     */
    private static void kerjaBelumDisimpanTerdeteksi() throws Exception {
        System.out.println("8. Pekerjaan belum disimpan terdeteksi ...");
        PanelTransaction p = new PanelTransaction();
        record(!p.adaKerjaBelumDisimpan(), "panel baru: tidak ada kerja belum disimpan");

        ketikPlat(p, "ZZ 6100 ZZ");
        ketikRental(p, "Rental Uji Kerja");
        isiAngka(p);
        setTanggal(p, "spPaid", "05-10-2026");
        tambahBaris(p);
        record(p.adaKerjaBelumDisimpan(), "ada baris: kerja belum disimpan terdeteksi");

        klik(p, "newTransaction");
        record(!p.adaKerjaBelumDisimpan(), "setelah transaksi baru: bersih kembali");

        isi(p, "txtFieldWeight", "5000");
        record(p.adaKerjaBelumDisimpan(), "form terisi (belum jadi baris) juga terdeteksi");
        System.out.println();
    }

    /**
     * Baris "belum dibayar" (centang tidak aktif) harus bisa dicatat dengan tanggal
     * lunas kosong, dan benar-benar tersimpan kosong di database.
     */
    private static void belumLunasTersimpan() throws Exception {
        System.out.println("9. Baris belum dibayar ...");
        PanelTransaction p = new PanelTransaction();
        ketikPlat(p, "ZZ 7200 ZZ");
        ketikRental(p, "Rental Belum Lunas");
        isiAngka(p);
        ((JCheckBox) field(p, "chkPaid")).setSelected(false);
        tambahBaris(p);
        TransactionDetail d = satuSatuBaris(p);
        record(d != null, "baris belum dibayar bisa ditambahkan");
        record(d != null && d.getPaymentDate() == null, "tanggal lunasnya null di baris");
        record(d != null && !((JSpinner) field(p, "spPaid")).isEnabled(),
                "kotak tanggal lunas mati saat belum dibayar");

        simpanTanpaLayar(p);
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi_detail d JOIN truk t ON t.id_truk = d.id_truk "
                        + "WHERE t.plat = 'ZZ 7200 ZZ' AND d.tanggal_lunas IS NULL") == 1,
                "tersimpan ke database dengan tanggal_lunas NULL");
        System.out.println();
    }

    /** Hapus transaksi lewat DAO: headernya terhapus dan baris detailnya ikut (cascade). */
    private static void hapusTransaksiLewatDao() throws Exception {
        System.out.println("10. Hapus transaksi lewat DAO ...");
        TransactionDetail d = barisUntukDao("ZZ 7300 ZZ", "Rental Uji Hapus");
        Transaction nota = new Transaction();
        nota.setDate(LocalDate.of(2026, 10, 5));
        int id = new TransactionDao().save(nota, Collections.singletonList(d));
        record(id > 0, "nota tersimpan dengan id " + id);
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi_detail WHERE id_transaksi = " + id) == 1,
                "notanya punya satu baris detail");

        new TransactionDao().deleteTransaction(id);
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi WHERE id_transaksi = " + id) == 0,
                "header nota terhapus");
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi_detail WHERE id_transaksi = " + id) == 0,
                "baris detail ikut terhapus (cascade)");
        System.out.println();
    }

    /**
     * Simpan sukses: daftar baris kosong, riwayat memuat notanya, dan menekan Simpan
     * lagi tidak bisa membuat nota yang sama tercatat dua kali.
     */
    private static void simpanSuksesTidakDobel() throws Exception {
        System.out.println("11. Simpan sukses: daftar kosong, riwayat terisi, tak dobel ...");
        PanelTransaction p = new PanelTransaction();
        ketikPlat(p, "ZZ 7400 ZZ");
        ketikRental(p, "Rental Uji Simpan Sukses");
        isiAngka(p);
        setTanggal(p, "spPaid", "05-10-2026");
        tambahBaris(p);
        if (satuSatuBaris(p) == null) {
            record(false, "baris prasyarat masuk daftar");
            System.out.println();
            return;
        }
        int notaSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi");

        simpanTanpaLayar(p);
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == notaSebelum + 1,
                "nota tercatat tepat satu kali");
        record(((List<?>) field(p, "detailList")).isEmpty(),
                "daftar baris kosong setelah simpan");
        DefaultTableModel riwayat = (DefaultTableModel) field(p, "riwayatModel");
        String hariIni = Dates.format(LocalDate.now());
        boolean ada = false;
        for (int i = 0; i < riwayat.getRowCount(); i++) {
            if (hariIni.equals(String.valueOf(riwayat.getValueAt(i, 0)))) {
                ada = true;
            }
        }
        record(ada, "riwayat memuat nota hari ini (" + hariIni + ")");
        record(!p.adaKerjaBelumDisimpan(), "tidak ada lagi kerja belum disimpan");

        // Menekan Simpan lagi (mis. karena dialog tadi gagal tampil) tidak boleh
        // membuat notanya tercatat dua kali.
        klik(p, "save");
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == notaSebelum + 1,
                "menekan Simpan lagi tidak menambah nota");
        System.out.println();
    }

    /**
     * Tekan Simpan dari luar layar. Dialog "Tersimpan" tidak bisa tampil tanpa layar —
     * itu bukan kegagalan simpan: urutan simpan sudah diatur supaya kegagalan dialog
     * itu tidak meninggalkan pekerjaan setengah jadi.
     */
    private static void simpanTanpaLayar(PanelTransaction p) throws Exception {
        try {
            klik(p, "save");
        } catch (Exception e) {
            Throwable sebab = e.getCause() == null ? e : e.getCause();
            record(sebab instanceof HeadlessException,
                    "satu-satunya kegagalan yang boleh terjadi adalah dialog tanpa layar: " + sebab);
        }
    }

    /** Satu baris detail siap simpan lewat DAO, dihitung dengan Calculator. */
    private static TransactionDetail barisUntukDao(String plat, String rental) {
        BigDecimal beratBersih = Calculator.netWeight(new BigDecimal("7050"), new BigDecimal("15"));
        TransactionDetail d = new TransactionDetail();
        d.setPlate(plat);
        d.setRentalName(rental);
        d.setFieldWeight(new BigDecimal("7200"));
        d.setFactoryWeight(new BigDecimal("7050"));
        d.setRefractionPercent(new BigDecimal("15"));
        d.setNetWeight(beratBersih);
        d.setPrice(new BigDecimal("1150"));
        d.setTotalAmount(Calculator.totalAmount(beratBersih, new BigDecimal("1150")));
        d.setPaymentDate(null);
        return d;
    }

    // ================= alat uji =================

    private static void createSchema() throws Exception {
        try (Connection c = Db.get(); Statement st = c.createStatement()) {
            for (String sql : Schema.readStatements()) {
                st.execute(sql);
            }
        }
    }

    private static void isiMaster() throws Exception {
        MasterDao dao = new MasterDao();
        Rental r = new Rental();
        r.setRentalName("Rental Sinar Jaya");
        dao.saveRental(r);
        int idRental = 0;
        for (Rental x : dao.listRental()) {
            if ("Rental Sinar Jaya".equals(x.getRentalName())) {
                idRental = x.getRentalId();
            }
        }
        Truck t = new Truck();
        t.setPlate("KB 8234 HD");
        t.setRentalId(idRental);
        dao.saveTruck(t);
    }

    private static int jumlahBaris(String sql) throws Exception {
        try (Connection c = Db.get(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Satu-satunya baris di daftar, atau null kalau daftarnya kosong. */
    private static TransactionDetail satuSatuBaris(PanelTransaction p) throws Exception {
        List<?> semua = baris(p);
        return semua.isEmpty() ? null : (TransactionDetail) semua.get(semua.size() - 1);
    }

    @SuppressWarnings("unchecked")
    private static List<?> baris(PanelTransaction p) throws Exception {
        return (List<?>) field(p, "detailList");
    }

    private static String pesan(PanelTransaction p) throws Exception {
        return String.valueOf(((JLabel) field(p, "lblStatus")).getText());
    }

    private static void isiAngka(PanelTransaction p) throws Exception {
        isi(p, "txtFieldWeight", "7200");
        isi(p, "txtFactoryWeight", "7050");
        isi(p, "txtRefraction", "15");
        isi(p, "txtPrice", "1150");
    }

    private static void ketikPlat(PanelTransaction p, String teks) throws Exception {
        ((JComboBox<?>) field(p, "cmbPlate")).getEditor().setItem(teks);
    }

    private static void ketikRental(PanelTransaction p, String teks) throws Exception {
        ((JComboBox<?>) field(p, "cmbRental")).getEditor().setItem(teks);
    }

    private static String textPlat(PanelTransaction p) throws Exception {
        Object isi = ((JComboBox<?>) field(p, "cmbPlate")).getEditor().getItem();
        return isi == null ? "" : isi.toString();
    }

    private static String textRental(PanelTransaction p) throws Exception {
        Object isi = ((JComboBox<?>) field(p, "cmbRental")).getEditor().getItem();
        return isi == null ? "" : isi.toString();
    }

    /** Tulis teks ke kotak tanggal, seperti operator mengetiknya. */
    private static void setTanggal(PanelTransaction p, String nama, String teks) throws Exception {
        kotakTanggal(p, nama).setText(teks);
    }

    private static JTextField kotakTanggal(PanelTransaction p, String nama) throws Exception {
        JSpinner sp = (JSpinner) field(p, nama);
        return ((JSpinner.DefaultEditor) sp.getEditor()).getTextField();
    }

    /** Tanda merah (outline) sebuah komponen, untuk diperiksa. */
    private static Object outline(Object komponen) {
        return ((JComponent) komponen).getClientProperty(FlatClientProperties.OUTLINE);
    }

    private static void tambahBaris(PanelTransaction p) throws Exception {
        klik(p, "addRow");
    }

    private static void klikMenu(Container c, String teks) {
        for (Component anak : c.getComponents()) {
            if (anak instanceof AbstractButton && teks.equals(((AbstractButton) anak).getText())) {
                ((AbstractButton) anak).doClick();
                return;
            }
            if (anak instanceof Container) {
                klikMenu((Container) anak, teks);
            }
        }
    }

    /** Cari panel transaksi yang sedang ditampilkan di dalam PagePanel. */
    private static PanelTransaction cariPanelTransaksi(Container c) {
        for (Component anak : c.getComponents()) {
            if (anak instanceof PanelTransaction) {
                return (PanelTransaction) anak;
            }
            if (anak instanceof Container) {
                PanelTransaction hasil = cariPanelTransaksi((Container) anak);
                if (hasil != null) {
                    return hasil;
                }
            }
        }
        return null;
    }

    private static void isi(Object target, String field, String nilai) throws Exception {
        ((javax.swing.text.JTextComponent) field(target, field)).setText(nilai);
    }

    private static void klik(Object target, String method) throws Exception {
        java.lang.reflect.Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(target);
    }

    private static Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void record(boolean ok, String name) {
        System.out.printf("   %-62s %s%n", name, ok ? "OK" : "SALAH");
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }
}
