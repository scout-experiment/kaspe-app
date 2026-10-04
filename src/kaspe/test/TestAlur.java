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
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Uji alur layar transaksi: satu Simpan = satu catatan pengiriman (tidak ada
 * lagi nota yang mengumpulkan beberapa baris), dua pengiriman truk yang sama
 * pada hari yang sama tetap dua catatan, ubah menulis tanpa menambah, dan
 * hapus mengikuti pilihan di daftar.
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
        simpanDitolakTidakMenulisApaPun();
        rentalKosongDitolak();
        pemilikBedaDitolak();
        tanggalLunasDitolakKalauSalah();
        tanggalNotaDitolakKalauSalah();
        satuSimpanSatuCatatan();
        duaPengirimanSamaTetapDuaCatatan();
        formKosongSetelahSimpan();
        simpanGandaTidakDobel();
        ubahPengirimanMenulisTanpaMenambah();
        ubahLaluBatalTidakMengubah();
        hapusYangTerpilih();
        tombolMengikutiPilihan();
        identitasBarisDariModel();
        pindahMenuTidakMembuangPekerjaan();
        kerjaBelumDisimpanTerdeteksi();
        belumLunasTersimpan();
        hapusTransaksiLewatDao();

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
     * Simpan yang ditolak tidak boleh menulis apa pun: bukan transaksinya, bukan
     * rental/truk barunya. Dulu setiap upaya penyimpanan yang gagal menaburkan
     * rental dan truk hantu di data master; sekarang truk hanya dibuat
     * TransactionDao di dalam transaksi simpan yang benar-benar berjalan.
     */
    private static void simpanDitolakTidakMenulisApaPun() throws Exception {
        System.out.println("2. Simpan yang ditolak tidak menulis apa pun ...");
        PanelTransaction p = new PanelTransaction();
        int trxSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi");
        int detSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi_detail");
        int rentalSebelum = jumlahBaris("SELECT COUNT(*) FROM rental");
        int trukSebelum = jumlahBaris("SELECT COUNT(*) FROM truk");

        ketikPlat(p, "zz  1111  zz");
        ketikRental(p, "Rental Belum Pernah Ada");
        isiAngka(p);
        // tanggal lunas salah dengan centang "Sudah dibayar" terpasang -> ditolak
        setTanggal(p, "spPaid", "31-02-2026");
        simpanDitolak(p);

        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum
                        && jumlahBaris("SELECT COUNT(*) FROM transaksi_detail") == detSebelum,
                "tidak ada transaksi/detail yang tersimpan (" + trxSebelum + "/" + detSebelum + " tetap)");
        record(jumlahBaris("SELECT COUNT(*) FROM rental") == rentalSebelum
                        && jumlahBaris("SELECT COUNT(*) FROM truk") == trukSebelum,
                "data master tidak tersentuh (rental " + rentalSebelum + ", truk " + trukSebelum + " tetap)");
        record(!pesan(p).isEmpty(), "penolakan disertai pesan status: \"" + pesan(p) + "\"");
        record("error".equals(outline(kotakTanggal(p, "spPaid"))),
                "kotak tanggal lunas ditandai merah");
        System.out.println();
    }

    /** Rental kosong harus ditolak dengan tanda merah, seperti kotak angka. */
    private static void rentalKosongDitolak() throws Exception {
        System.out.println("3. Rental kosong ditolak ...");
        PanelTransaction p = new PanelTransaction();
        int trxSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi");

        // Plat baru otomatis mengosongkan pilihan rental, persis keadaan operator
        // yang mengetik plat lalu langsung menekan Simpan.
        ketikPlat(p, "ZZ 2222 ZZ");
        isiAngka(p);
        simpanDitolak(p);

        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum,
                "tidak ada transaksi yang tersimpan");
        record(!pesan(p).isEmpty() && "error".equals(outline(field(p, "cmbRental"))),
                "penolakan disertai pesan (\"" + pesan(p) + "\") dan kotak rental ditandai merah");

        // Nama berisi spasi saja juga ditolak, bukan dianggap terisi.
        ketikRental(p, "   ");
        simpanDitolak(p);
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum,
                "rental berisi spasi saja juga ditolak");
        System.out.println();
    }

    /**
     * Plat yang sudah dikenal + rental yang berbeda dari pemilik tersimpan harus
     * ditolak dengan pesan jelas — bukan diam-diam memakai pemilik lama.
     *
     * <p>Sebaliknya, rental yang sama meski ejaan hurufnya berbeda harus diterima,
     * dan catatannya memakai nomor truk yang sudah tersimpan.
     */
    private static void pemilikBedaDitolak() throws Exception {
        System.out.println("4. Plat dikenal + rental berbeda ...");
        PanelTransaction p = new PanelTransaction();
        int trxSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi");
        int trukSebelum = jumlahBaris("SELECT COUNT(*) FROM truk");

        ketikPlat(p, "kb 8234 hd");
        ketikRental(p, "Rental Lain Sekali");
        isiAngka(p);
        simpanDitolak(p);

        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum,
                "tidak ada transaksi yang tersimpan");
        String m = pesan(p);
        record(m.contains("Rental Sinar Jaya") && m.contains("Pindah Pemilik"),
                "pesan menunjuk pemilik tersimpan dan jalannya: \"" + m + "\"");

        // Ejaan yang sama walau hurufnya berbeda tetap diterima.
        ketikRental(p, "rental  sinar  jaya");
        simpanTanpaLayar(p);
        String sqlDetail = " FROM transaksi_detail d JOIN truk t ON t.id_truk = d.id_truk WHERE t.plat = 'KB 8234 HD'";
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum + 1
                        && jumlahBaris("SELECT COUNT(*)" + sqlDetail) == 1,
                "rental yang sama (huruf beda) diterima: satu catatan tersimpan");
        int idTrukTersimpan = angka("SELECT id_truk FROM truk WHERE plat = 'KB 8234 HD'");
        record(angka("SELECT d.id_truk" + sqlDetail) == idTrukTersimpan
                        && jumlahBaris("SELECT COUNT(*) FROM truk") == trukSebelum,
                "memakai nomor truk tersimpan (bukan 0), tanpa truk hantu");
        System.out.println();
    }

    /** Tanggal lunas yang tidak valid harus ditolak — tetapi hanya kalau dicatat sudah dibayar. */
    private static void tanggalLunasDitolakKalauSalah() throws Exception {
        System.out.println("5. Tanggal lunas pada Simpan ...");
        PanelTransaction p = new PanelTransaction();
        ketikPlat(p, "ZZ 3333 ZZ");
        ketikRental(p, "Rental Uji Tanggal");
        isiAngka(p);

        int trxSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi");
        String[] salah = {"31-02-2026", "5-10-26", "5/10/2026", "2026-10-05"};
        for (String teks : salah) {
            setTanggal(p, "spPaid", teks);
            simpanDitolak(p);
            record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum, "ditolak: " + teks);
        }
        record(!pesan(p).isEmpty(), "penolakan disertai pesan: \"" + pesan(p) + "\"");
        record("error".equals(outline(kotakTanggal(p, "spPaid"))),
                "kotak tanggal lunas ditandai merah");

        // Centang dilepas: tulisan yang salah tidak lagi dipakai — "belum dibayar"
        // bukan tanggal yang salah.
        ((JCheckBox) field(p, "chkPaid")).setSelected(false);
        simpanTanpaLayar(p);
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi_detail d JOIN truk t ON t.id_truk = d.id_truk "
                        + "WHERE t.plat = 'ZZ 3333 ZZ' AND d.tanggal_lunas IS NULL") == 1,
                "centang dilepas: diterima dengan tanggal_lunas NULL");

        // Tanggal yang valid benar-benar dipakai, bukan tanggal lama yang tertinggal.
        ketikPlat(p, "ZZ 3333 ZZ");
        ketikRental(p, "Rental Uji Tanggal");
        isiAngka(p);
        ((JCheckBox) field(p, "chkPaid")).setSelected(true);
        setTanggal(p, "spPaid", "05-10-2026");
        simpanTanpaLayar(p);
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi_detail d JOIN truk t ON t.id_truk = d.id_truk "
                        + "WHERE t.plat = 'ZZ 3333 ZZ' AND d.tanggal_lunas = '2026-10-05'") == 1,
                "tanggal yang diterima benar-benar dipakai (05-10-2026)");
        System.out.println();
    }

    /**
     * Simpan dengan tanggal nota yang tidak valid harus ditolak juga — bukan
     * diam-diam memakai tanggal lama yang tertinggal di model.
     */
    private static void tanggalNotaDitolakKalauSalah() throws Exception {
        System.out.println("6. Tanggal nota salah ditolak ...");
        PanelTransaction p = new PanelTransaction();
        int trxSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi");
        ketikPlat(p, "ZZ 4444 ZZ");
        ketikRental(p, "Rental Uji Simpan");
        isiAngka(p);
        setTanggal(p, "spDate", "31-02-2026");
        simpanDitolak(p);

        record(!pesan(p).isEmpty() && jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum,
                "ditolak dengan pesan (\"" + pesan(p) + "\"), tidak ada yang tersimpan");
        record("7200".equals(text(p, "txtFieldWeight")),
                "isian tidak dibuang oleh penolakan");
        System.out.println();
    }

    /** Inti perubahan ini: satu form yang diisi + satu Simpan = satu catatan pengiriman. */
    private static void satuSimpanSatuCatatan() throws Exception {
        System.out.println("7. Satu Simpan = satu catatan ...");
        PanelTransaction p = new PanelTransaction();
        int trxSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi");
        int detSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi_detail");

        ketikPlat(p, "zz  7400  zz");
        ketikRental(p, "Rental Belum Pernah Ada");
        isiAngka(p);
        simpanTanpaLayar(p);

        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum + 1
                        && jumlahBaris("SELECT COUNT(*) FROM transaksi_detail") == detSebelum + 1,
                "tepat satu header dan satu baris detail");
        record(jumlahBaris("SELECT COUNT(*) FROM truk WHERE plat = 'ZZ 7400 ZZ'") == 1,
                "plat ternormalisasi (zz  7400  zz -> ZZ 7400 ZZ)");
        BigDecimal bersih = Calculator.netWeight(new BigDecimal("7050"), new BigDecimal("15"));
        BigDecimal uang = Calculator.totalAmount(bersih, new BigDecimal("1150"));
        BigDecimal bersihDb = desimal("SELECT d.berat_bersih FROM transaksi_detail d "
                + "JOIN truk t ON t.id_truk = d.id_truk WHERE t.plat = 'ZZ 7400 ZZ'");
        BigDecimal uangDb = desimal("SELECT d.jumlah_uang FROM transaksi_detail d "
                + "JOIN truk t ON t.id_truk = d.id_truk WHERE t.plat = 'ZZ 7400 ZZ'");
        record(bersihDb != null && bersihDb.compareTo(bersih) == 0
                        && uangDb != null && uangDb.compareTo(uang) == 0,
                "berat bersih dan jumlah uang dihitung Calculator (" + Calculator.formatCurrency(uang) + ")");
        DefaultTableModel riwayat = (DefaultTableModel) field(p, "riwayatModel");
        record(jumlahRiwayat(riwayat, "ZZ 7400 ZZ") == 1,
                "daftar Transaksi Tersimpan menampilkan satu baris untuk plat itu");
        System.out.println();
    }

    /**
     * Inti perubahan ini: dua pengiriman truk yang sama pada hari yang sama adalah
     * DUA catatan sendiri — tidak dikelompokkan menjadi satu nota, tidak digabung.
     */
    private static void duaPengirimanSamaTetapDuaCatatan() throws Exception {
        System.out.println("8. Dua pengiriman sama, hari sama = dua catatan ...");
        PanelTransaction p = new PanelTransaction();
        int trxSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi");
        int detSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi_detail");

        ketikPlat(p, "ZZ 7500 ZZ");
        ketikRental(p, "Rental Dua Kali");
        isiAngka(p);
        simpanTanpaLayar(p);
        // pengiriman kedua: plat, rental, dan tanggal sama persis — tanggal memang
        // sengaja tidak direset setelah simpan
        ketikPlat(p, "ZZ 7500 ZZ");
        ketikRental(p, "Rental Dua Kali");
        isiAngka(p);
        simpanTanpaLayar(p);

        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum + 2
                        && jumlahBaris("SELECT COUNT(*) FROM transaksi_detail") == detSebelum + 2,
                "2 header dan 2 baris detail (bukan satu nota yang digabung)");
        record(jumlahBaris("SELECT COUNT(DISTINCT d.id_transaksi) FROM transaksi_detail d "
                        + "JOIN truk t ON t.id_truk = d.id_truk WHERE t.plat = 'ZZ 7500 ZZ'") == 2,
                "kedua catatan punya header masing-masing");
        DefaultTableModel riwayat = (DefaultTableModel) field(p, "riwayatModel");
        record(jumlahRiwayat(riwayat, "ZZ 7500 ZZ") == 2,
                "daftar Transaksi Tersimpan menampilkan 2 baris");
        System.out.println();
    }

    /**
     * Setelah simpan sukses, form dikosongkan supaya pengiriman berikutnya tidak
     * diam-diam mewarisi plat dan harga pengiriman sebelumnya — tetapi tanggalnya
     * tetap, karena tanggal biasanya berulang dalam satu rombongan.
     */
    private static void formKosongSetelahSimpan() throws Exception {
        System.out.println("9. Form kosong setelah simpan, tanggal tetap ...");
        PanelTransaction p = new PanelTransaction();
        ketikPlat(p, "ZZ 7550 ZZ");
        ketikRental(p, "Rental Uji Kosong");
        isiAngka(p);
        setTanggal(p, "spDate", "07-10-2026");
        String tanggalSebelum = kotakTanggal(p, "spDate").getText();

        simpanTanpaLayar(p);

        record(text(p, "txtFieldWeight").isEmpty() && text(p, "txtFactoryWeight").isEmpty()
                        && text(p, "txtPrice").isEmpty() && textPlat(p).isEmpty() && textRental(p).isEmpty(),
                "bobot lapak, bobot pabrik, harga, plat, dan rental dikosongkan");
        record(kotakTanggal(p, "spDate").getText().equals(tanggalSebelum),
                "tanggal tidak ikut direset (" + tanggalSebelum + ")");
        System.out.println();
    }

    /**
     * Simpan sukses lalu Simpan ditekan lagi (klik ganda, atau Enter dua kali):
     * form sudah kosong dan harus ditolak — bukan menyimpan catatan kosong atau
     * menggandakan catatan yang barusan tersimpan. Panggilan kedua ditembakkan
     * sungguhan, bukan sekadar memeriksa bendera.
     */
    private static void simpanGandaTidakDobel() throws Exception {
        System.out.println("10. Simpan ganda tidak menggandakan ...");
        PanelTransaction p = new PanelTransaction();
        int trxSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi");
        int detSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi_detail");

        ketikPlat(p, "ZZ 7650 ZZ");
        ketikRental(p, "Rental Uji Dobel");
        isiAngka(p);
        simpanTanpaLayar(p);
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum + 1
                        && jumlahBaris("SELECT COUNT(*) FROM transaksi_detail") == detSebelum + 1
                        && !p.adaKerjaBelumDisimpan(),
                "tersimpan tepat sekali, tidak ada kerja tersisa");

        boolean lempar = false;
        try {
            klik(p, "save");
        } catch (Exception e) {
            lempar = true;
        }
        record(!lempar && !pesan(p).isEmpty(),
                "Simpan kedua ditolak dengan pesan: \"" + pesan(p) + "\"");
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum + 1
                        && jumlahBaris("SELECT COUNT(*) FROM transaksi_detail") == detSebelum + 1,
                "tidak ada catatan tambahan");
        System.out.println();
    }

    /**
     * Ubah pengiriman: menulis ke catatan yang sama, tidak menambah catatan baru.
     * Berat bersih dan jumlah uang dihitung ulang oleh Calculator.
     */
    private static void ubahPengirimanMenulisTanpaMenambah() throws Exception {
        System.out.println("11. Ubah pengiriman: menulis, tidak menambah ...");
        PanelTransaction p = new PanelTransaction();
        ketikPlat(p, "ZZ 7600 ZZ");
        ketikRental(p, "Rental Uji Ubah");
        isiAngka(p);
        simpanTanpaLayar(p);

        int trxSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi");
        int detSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi_detail");

        DefaultTableModel riwayat = (DefaultTableModel) field(p, "riwayatModel");
        int baris = barisRiwayat(riwayat, "ZZ 7600 ZZ");
        if (baris < 0) {
            record(false, "prasyarat: pengiriman masuk daftar riwayat");
            System.out.println();
            return;
        }
        ((JTable) field(p, "riwayatTable")).setRowSelectionInterval(baris, baris);
        klik(p, "ubahPengiriman");
        record("Ubah Pengiriman".equals(((JLabel) field(p, "judulKartu")).getText())
                        && "Simpan Perubahan".equals(((AbstractButton) field(p, "btnSimpan")).getText())
                        && ((AbstractButton) field(p, "btnBatal")).isVisible(),
                "pilih satu baris + Ubah: masuk mode ubah");

        // Angka yang membuat pembulatan ke bawah benar-benar berlaku:
        // 6150 x 85% = 5227,5 -> 5225, jadi hitungan mentah pasti beda.
        isiAngka(p, "8000", "6150", "1200");
        setTanggal(p, "spDate", "10-10-2026");
        simpanTanpaLayar(p);

        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum
                        && jumlahBaris("SELECT COUNT(*) FROM transaksi_detail") == detSebelum,
                "jumlah header dan detail tetap (menulis, bukan menambah)");
        BigDecimal bersih = Calculator.netWeight(new BigDecimal("6150"), new BigDecimal("15"));
        BigDecimal uang = Calculator.totalAmount(bersih, new BigDecimal("1200"));
        BigDecimal bersihDb = desimal("SELECT d.berat_bersih FROM transaksi_detail d "
                + "JOIN truk t ON t.id_truk = d.id_truk WHERE t.plat = 'ZZ 7600 ZZ'");
        BigDecimal uangDb = desimal("SELECT d.jumlah_uang FROM transaksi_detail d "
                + "JOIN truk t ON t.id_truk = d.id_truk WHERE t.plat = 'ZZ 7600 ZZ'");
        record(bersihDb != null && bersihDb.compareTo(bersih) == 0,
                "berat bersih dihitung ulang oleh Calculator");
        record(uangDb != null && uangDb.compareTo(uang) == 0,
                "jumlah uang dihitung ulang dari berat bersih baru");
        record("2026-10-10".equals(satuNilai("SELECT h.tanggal FROM transaksi h "
                        + "JOIN transaksi_detail d ON d.id_transaksi = h.id_transaksi "
                        + "JOIN truk t ON t.id_truk = d.id_truk WHERE t.plat = 'ZZ 7600 ZZ'")),
                "tanggal nota berubah di database");

        int barisBaru = barisRiwayat(riwayat, "ZZ 7600 ZZ");
        record(barisBaru >= 0
                        && Dates.format(LocalDate.of(2026, 10, 10)).equals(String.valueOf(riwayat.getValueAt(barisBaru, 1)))
                        && ("Rp " + Calculator.formatCurrency(uang)).equals(String.valueOf(riwayat.getValueAt(barisBaru, 10))),
                "riwayat menampilkan tanggal dan jumlah uang yang baru");
        record(textPlat(p).isEmpty() && "Catat Pengiriman".equals(((JLabel) field(p, "judulKartu")).getText()),
                "kembali ke keadaan menambah setelah simpan perubahan");
        System.out.println();
    }

    /**
     * Ubah lalu Batal: tidak ada yang berubah di database, form kembali ke keadaan
     * menambah, dan Simpan berikutnya mencatat pengiriman baru — bukan menimpa
     * catatan yang tadi hampir diubah.
     */
    private static void ubahLaluBatalTidakMengubah() throws Exception {
        System.out.println("12. Ubah lalu Batal tidak mengubah apa pun ...");
        PanelTransaction p = new PanelTransaction();
        ketikPlat(p, "ZZ 7625 ZZ");
        ketikRental(p, "Rental Uji Batal");
        isiAngka(p);
        simpanTanpaLayar(p);

        int trxSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi");
        String sqlUang = "SELECT d.jumlah_uang FROM transaksi_detail d "
                + "JOIN truk t ON t.id_truk = d.id_truk WHERE t.plat = 'ZZ 7625 ZZ'";
        BigDecimal uangSebelum = desimal(sqlUang);

        DefaultTableModel riwayat = (DefaultTableModel) field(p, "riwayatModel");
        int baris = barisRiwayat(riwayat, "ZZ 7625 ZZ");
        if (baris < 0) {
            record(false, "prasyarat: pengiriman masuk daftar riwayat");
            System.out.println();
            return;
        }
        ((JTable) field(p, "riwayatTable")).setRowSelectionInterval(baris, baris);
        record(klikDuaKali(p) && "Simpan Perubahan".equals(((AbstractButton) field(p, "btnSimpan")).getText()),
                "klik dua kali baris daftar juga masuk mode ubah");

        isiAngka(p, "9000", "8800", "1150");
        klik(p, "kembaliKeTambah");   // tombol Batal

        BigDecimal uangSesudah = desimal(sqlUang);
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum
                        && uangSesudah != null && uangSesudah.compareTo(uangSebelum) == 0,
                "catatan tidak berubah sama sekali");
        record(text(p, "txtFieldWeight").isEmpty() && textPlat(p).isEmpty()
                        && "Catat Pengiriman".equals(((JLabel) field(p, "judulKartu")).getText())
                        && "Simpan".equals(((AbstractButton) field(p, "btnSimpan")).getText()),
                "form dikosongkan dan kembali ke keadaan menambah");
        record(!((AbstractButton) field(p, "btnBatal")).isVisible(),
                "tombol Batal tidak tampil lagi di keadaan menambah");

        ketikPlat(p, "ZZ 7626 ZZ");
        ketikRental(p, "Rental Uji Batal");
        isiAngka(p);
        simpanTanpaLayar(p);
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum + 1,
                "Simpan setelah Batal mencatat pengiriman baru, bukan menimpa");
        System.out.println();
    }

    /**
     * Hapus mengikuti pilihan: satu atau beberapa baris sekaligus, identitasnya
     * dibaca dari model tabel (id_detail pada kolom 0), dan yang lain tetap utuh.
     *
     * <p>Dialog konfirmasi tidak bisa tampil tanpa layar; yang diuji di sini:
     * tanpa konfirmasi tidak ada yang terhapus (dialog mendahului penghapusan),
     * lalu langkah-langkah sesudah konfirmasi — hapus ids yang dipilih dan muat
     * ulang daftar — meninggalkan daftar yang cocok dengan catatan tersisa.
     */
    @SuppressWarnings("unchecked")
    private static void hapusYangTerpilih() throws Exception {
        System.out.println("13. Hapus yang dipilih ...");
        PanelTransaction p = new PanelTransaction();
        for (String plat : new String[]{"ZZ 7701 ZZ", "ZZ 7702 ZZ", "ZZ 7703 ZZ"}) {
            ketikPlat(p, plat);
            ketikRental(p, "Rental Uji Hapus");
            isiAngka(p);
            simpanTanpaLayar(p);
        }

        int trxSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi");
        int detSebelum = jumlahBaris("SELECT COUNT(*) FROM transaksi_detail");
        DefaultTableModel riwayat = (DefaultTableModel) field(p, "riwayatModel");
        int barisSebelum = riwayat.getRowCount();
        JTable tabel = (JTable) field(p, "riwayatTable");

        // pilih ZZ 7701 ZZ dan ZZ 7703 ZZ (tidak bersebelahan di daftar)
        int b1 = barisRiwayat(riwayat, "ZZ 7701 ZZ");
        int b3 = barisRiwayat(riwayat, "ZZ 7703 ZZ");
        if (b1 < 0 || b3 < 0) {
            record(false, "prasyarat: tiga pengiriman masuk daftar riwayat");
            System.out.println();
            return;
        }
        tabel.setRowSelectionInterval(b1, b1);
        tabel.addRowSelectionInterval(b3, b3);

        List<Integer> ids = (List<Integer>) klik(p, "idTerpilih");
        int id1 = angka("SELECT d.id_detail FROM transaksi_detail d "
                + "JOIN truk t ON t.id_truk = d.id_truk WHERE t.plat = 'ZZ 7701 ZZ'");
        int id3 = angka("SELECT d.id_detail FROM transaksi_detail d "
                + "JOIN truk t ON t.id_truk = d.id_truk WHERE t.plat = 'ZZ 7703 ZZ'");
        record(ids.size() == 2 && ids.contains(id1) && ids.contains(id3),
                "pilihan membaca id_detail dari model, bukan nomor baris");

        boolean tanpaKonfirmasi = false;
        try {
            klik(p, "hapusTerpilih");
        } catch (Exception e) {
            Throwable sebab = e.getCause() == null ? e : e.getCause();
            tanpaKonfirmasi = sebab instanceof HeadlessException;
        }
        record(tanpaKonfirmasi && jumlahBaris("SELECT COUNT(*) FROM transaksi_detail") == detSebelum,
                "tanpa konfirmasi (tanpa layar) tidak ada yang terhapus");

        new TransactionDao().deleteDeliveries(ids);
        klik(p, "muatRiwayat");

        record(jumlahBaris("SELECT COUNT(*) FROM transaksi_detail") == detSebelum - 2
                        && jumlahBaris("SELECT COUNT(*) FROM transaksi") == trxSebelum - 2,
                "tepat dua catatan terhapus beserta headernya");
        record(hitungHeaderKosong() == 0, "tidak ada header yang tinggal tanpa detail");
        record(riwayat.getRowCount() == barisSebelum - 2
                        && barisRiwayat(riwayat, "ZZ 7702 ZZ") >= 0
                        && barisRiwayat(riwayat, "ZZ 7701 ZZ") < 0
                        && barisRiwayat(riwayat, "ZZ 7703 ZZ") < 0,
                "daftar riwayat cocok dengan catatan yang tersisa");
        System.out.println();
    }

    /**
     * Tombol Ubah dan Hapus mengikuti pilihan di daftar: Ubah hanya kalau tepat
     * satu baris (kalau dua, tidak jelas mana yang mau diubah), Hapus boleh satu
     * atau lebih.
     */
    private static void tombolMengikutiPilihan() throws Exception {
        System.out.println("14. Tombol Ubah dan Hapus mengikuti pilihan ...");
        PanelTransaction p = new PanelTransaction();
        JTable tabel = (JTable) field(p, "riwayatTable");
        AbstractButton ubah = (AbstractButton) field(p, "btnUbah");
        AbstractButton hapus = (AbstractButton) field(p, "btnHapus");
        if (tabel.getRowCount() < 2) {
            record(false, "prasyarat: minimal dua baris di daftar riwayat");
            System.out.println();
            return;
        }

        tabel.clearSelection();
        record(!ubah.isEnabled() && !hapus.isEnabled(), "tanpa pilihan: dua-duanya mati");

        tabel.setRowSelectionInterval(0, 0);
        record(ubah.isEnabled() && hapus.isEnabled(), "satu baris: dua-duanya hidup");

        tabel.addRowSelectionInterval(1, 1);
        record(!ubah.isEnabled() && hapus.isEnabled(), "dua baris: hanya Hapus yang hidup");
        System.out.println();
    }

    /**
     * Identitas baris daftar dibaca dari model (id_detail pada kolom 0 yang
     * disembunyikan), bukan dari nomor barisnya — begitu ada saringan tanggal
     * atau rental, nomor baris di layar tidak lagi sama dengan nomor di data
     * asal, dan aksi pada "baris ke-n" bisa mengenai catatan yang salah.
     */
    private static void identitasBarisDariModel() throws Exception {
        System.out.println("15. Identitas baris dari model, bukan nomor baris ...");
        PanelTransaction p = new PanelTransaction();
        // dua catatan dengan isi berbeda, yang lebih lama TIDAK di baris pertama
        ketikPlat(p, "ZZ 7801 ZZ");
        ketikRental(p, "Rental Uji Identitas");
        isiAngka(p, "7200", "7050", "1150");
        simpanTanpaLayar(p);
        ketikPlat(p, "ZZ 7802 ZZ");
        ketikRental(p, "Rental Uji Identitas");
        isiAngka(p, "8100", "7900", "1150");
        simpanTanpaLayar(p);

        DefaultTableModel riwayat = (DefaultTableModel) field(p, "riwayatModel");
        int baris = barisRiwayat(riwayat, "ZZ 7801 ZZ");
        int idDb = angka("SELECT d.id_detail FROM transaksi_detail d "
                + "JOIN truk t ON t.id_truk = d.id_truk WHERE t.plat = 'ZZ 7801 ZZ'");
        record(baris > 0 && (Integer) riwayat.getValueAt(baris, 0) == idDb,
                "id pada baris terpilih sama dengan id_detail catatan itu di database");

        ((JTable) field(p, "riwayatTable")).setRowSelectionInterval(baris, baris);
        klik(p, "ubahPengiriman");
        record("7200".equals(text(p, "txtFieldWeight"))
                        && Integer.valueOf(idDb).equals(field(p, "detailDiubah")),
                "Ubah mengisi form dari catatan itu (bukan tetangganya) dan menandainya sebagai yang diubah");
        System.out.println();
    }

    /**
     * Pindah menu lalu kembali: isian yang belum disimpan harus masih ada.
     *
     * <p>Dulu setiap klik menu membuat PanelTransaction baru, sehingga isian yang
     * belum disimpan hilang tanpa peringatan. Sekarang halaman transaksi dipakai
     * lagi, dan daftar plat/rentalnya disegarkan tanpa mengubah yang sedang
     * tertulis.
     */
    private static void pindahMenuTidakMembuangPekerjaan() throws Exception {
        System.out.println("16. Pindah menu lalu kembali ...");
        PagePanel halaman = new PagePanel();
        JPanel layar = PagePanel.shell(halaman);
        klikMenu(layar, "Transaksi");
        PanelTransaction p = cariPanelTransaksi(halaman);
        record(p != null, "halaman transaksi terbuka");
        if (p == null) {
            System.out.println();
            return;
        }

        ketikPlat(p, "ZZ 5555 ZZ");
        ketikRental(p, "Rental Uji Navigasi");
        isiAngka(p);
        setTanggal(p, "spDate", "06-10-2026");

        klikMenu(layar, "Beranda");
        klikMenu(layar, "Transaksi");
        PanelTransaction pLagi = cariPanelTransaksi(halaman);

        record(pLagi == p, "panel transaksi dipakai lagi, bukan dibuat baru");
        record(pLagi != null && "ZZ 5555 ZZ".equals(textPlat(pLagi))
                        && "Rental Uji Navigasi".equals(textRental(pLagi))
                        && "7200".equals(text(pLagi, "txtFieldWeight"))
                        && "06-10-2026".equals(kotakTanggal(pLagi, "spDate").getText()),
                "isian yang belum disimpan bertahan, termasuk tanggalnya");
        record(p.adaKerjaBelumDisimpan(), "pekerjaan terdeteksi sebagai belum disimpan");

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
        JComboBox<?> daftarPlat = (JComboBox<?>) field(p, "cmbPlate");
        boolean platBaruMuncul = false;
        for (int i = 0; i < daftarPlat.getItemCount(); i++) {
            if ("NB 9001 NB".equals(daftarPlat.getItemAt(i))) {
                platBaruMuncul = true;
            }
        }
        record(pLagi2 == p && platBaruMuncul && "ZZ 5555 ZZ".equals(textPlat(p)),
                "plat baru dari data master muncul, pekerjaan tetap utuh");
        System.out.println();
    }

    /**
     * adaKerjaBelumDisimpan: dipakai bilah menu sebelum pindah halaman dan jendela
     * utama sebelum ditutup. Harus benar saat form masih terisi, dan salah saat
     * bersih.
     */
    private static void kerjaBelumDisimpanTerdeteksi() throws Exception {
        System.out.println("17. Pekerjaan belum disimpan terdeteksi ...");
        PanelTransaction p = new PanelTransaction();
        record(!p.adaKerjaBelumDisimpan(), "panel baru: tidak ada kerja belum disimpan");

        isi(p, "txtFieldWeight", "5000");
        record(p.adaKerjaBelumDisimpan(), "form terisi: kerja belum disimpan terdeteksi");

        isi(p, "txtFieldWeight", "");
        record(!p.adaKerjaBelumDisimpan(), "form kosong kembali: tidak terdeteksi");
        System.out.println();
    }

    /**
     * Pengiriman "belum dibayar" (centang tidak aktif) harus bisa dicatat dengan
     * tanggal lunas kosong, dan benar-benar tersimpan kosong di database.
     */
    private static void belumLunasTersimpan() throws Exception {
        System.out.println("18. Belum dibayar tersimpan ...");
        PanelTransaction p = new PanelTransaction();
        ketikPlat(p, "ZZ 7200 ZZ");
        ketikRental(p, "Rental Belum Lunas");
        isiAngka(p);
        ((JCheckBox) field(p, "chkPaid")).setSelected(false);
        record(!((JSpinner) field(p, "spPaid")).isEnabled(),
                "kotak tanggal lunas mati saat belum dibayar");

        simpanTanpaLayar(p);
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi_detail d JOIN truk t ON t.id_truk = d.id_truk "
                        + "WHERE t.plat = 'ZZ 7200 ZZ' AND d.tanggal_lunas IS NULL") == 1,
                "tersimpan ke database dengan tanggal_lunas NULL");
        System.out.println();
    }

    /** Hapus transaksi lewat DAO: headernya terhapus dan baris detailnya ikut (cascade). */
    private static void hapusTransaksiLewatDao() throws Exception {
        System.out.println("19. Hapus transaksi lewat DAO ...");
        TransactionDetail d = barisUntukDao("ZZ 7300 ZZ", "Rental Uji Hapus");
        Transaction nota = new Transaction();
        nota.setDate(LocalDate.of(2026, 10, 5));
        int id = new TransactionDao().save(nota, Collections.singletonList(d));
        record(id > 0 && jumlahBaris("SELECT COUNT(*) FROM transaksi_detail WHERE id_transaksi = " + id) == 1,
                "nota tersimpan dengan satu baris detail (id " + id + ")");

        new TransactionDao().deleteTransaction(id);
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi WHERE id_transaksi = " + id) == 0,
                "header nota terhapus");
        record(jumlahBaris("SELECT COUNT(*) FROM transaksi_detail WHERE id_transaksi = " + id) == 0,
                "baris detail ikut terhapus (cascade)");
        System.out.println();
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

    /** Nilai satu kolom dari query yang menghasilkan satu baris, atau null. */
    private static String satuNilai(String sql) throws Exception {
        try (Connection c = Db.get(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /** Angka bulat dari query satu baris, atau -1 kalau tidak ada. */
    private static int angka(String sql) throws Exception {
        String v = satuNilai(sql);
        return v == null ? -1 : Integer.parseInt(v.trim());
    }

    /** Nilai desimal dari query satu baris, atau null. */
    private static BigDecimal desimal(String sql) throws Exception {
        try (Connection c = Db.get(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getBigDecimal(1) : null;
        }
    }

    /** Jumlah header transaksi yang tidak lagi punya detail. */
    private static int hitungHeaderKosong() throws Exception {
        return jumlahBaris("SELECT COUNT(*) FROM transaksi t WHERE NOT EXISTS "
                + "(SELECT 1 FROM transaksi_detail d WHERE d.id_transaksi = t.id_transaksi)");
    }

    /** Nomor baris pertama di daftar riwayat yang menampilkan plat itu, atau -1. */
    private static int barisRiwayat(DefaultTableModel riwayat, String plat) {
        for (int i = 0; i < riwayat.getRowCount(); i++) {
            if (plat.equals(String.valueOf(riwayat.getValueAt(i, 2)))) {
                return i;
            }
        }
        return -1;
    }

    /** Jumlah baris di daftar riwayat yang menampilkan plat itu. */
    private static int jumlahRiwayat(DefaultTableModel riwayat, String plat) {
        int n = 0;
        for (int i = 0; i < riwayat.getRowCount(); i++) {
            if (plat.equals(String.valueOf(riwayat.getValueAt(i, 2)))) {
                n++;
            }
        }
        return n;
    }

    private static String pesan(PanelTransaction p) throws Exception {
        return String.valueOf(((JLabel) field(p, "lblStatus")).getText());
    }

    private static void isiAngka(PanelTransaction p) throws Exception {
        isiAngka(p, "7200", "7050", "1150");
    }

    private static void isiAngka(PanelTransaction p, String lapak, String pabrik, String harga) throws Exception {
        isi(p, "txtFieldWeight", lapak);
        isi(p, "txtFactoryWeight", pabrik);
        isi(p, "txtRefraction", "15");
        isi(p, "txtPrice", harga);
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

    /**
     * Tekan Simpan yang seharusnya DITOLAK: tidak boleh melempar galat apa pun —
     * penolakan isian bukan kegagalan program.
     */
    private static void simpanDitolak(PanelTransaction p) throws Exception {
        try {
            klik(p, "save");
        } catch (Exception e) {
            Throwable sebab = e.getCause() == null ? e : e.getCause();
            record(false, "simpan yang seharusnya ditolak malah melempar galat: " + sebab);
        }
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

    /** Kirim klik dua kali ke daftar riwayat, seperti operator mengekliknya. */
    private static boolean klikDuaKali(PanelTransaction p) throws Exception {
        JTable tabel = (JTable) field(p, "riwayatTable");
        for (MouseListener l : tabel.getMouseListeners()) {
            if (l.getClass().getName().startsWith("kaspe.ui.PanelTransaction$")) {
                l.mouseClicked(new MouseEvent(tabel, MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(), 0, 5, 5, 2, false));
                return true;
            }
        }
        return false;
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

    private static String text(Object target, String field) throws Exception {
        return ((javax.swing.text.JTextComponent) field(target, field)).getText();
    }

    private static Object klik(Object target, String method) throws Exception {
        java.lang.reflect.Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        return m.invoke(target);
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
