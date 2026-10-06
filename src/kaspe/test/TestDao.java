package kaspe.test;

import kaspe.Db;
import kaspe.Schema;
import kaspe.Calculator;
import kaspe.dao.BackupDao;
import kaspe.dao.MasterDao;
import kaspe.dao.TransactionDao;
import kaspe.model.*;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Uji ujung-ke-ujung lapisan DAO (tanpa tampilan):
 *   isi master -&gt; simpan transaksi (4 baris buku) -&gt; baca laporan -&gt; cek total -&gt; cek rollback.
 *
 * Pakai H2 mode MySQL supaya jalan tanpa install MySQL.
 * Jalankan: java -cp build:lib/h2-2.1.214.jar kaspe.test.TestDao
 */
public class TestDao {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=== UJI DAO UJUNG-KE-UJUNG ===\n");

        Db.setConfiguration("org.h2.Driver",
                "jdbc:h2:mem:daotest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        createSchema();

        MasterDao master = new MasterDao();
        TransactionDao transactionDao = new TransactionDao();

        System.out.println("1. Isi data master ...");
        Rental r1 = new Rental();
        r1.setRentalName("Rental Sinar Jaya");
        master.saveRental(r1);

        Truck t1 = new Truck();
        t1.setPlate("KB 8234 HD");
        t1.setRentalId(r1.getRentalId() == 0 ? master.listRental().get(0).getRentalId() : r1.getRentalId());
        master.saveTruck(t1);

        Truck t2 = new Truck();
        t2.setPlate("BE 8009 CF");
        t2.setRentalId(master.listRental().get(0).getRentalId());
        master.saveTruck(t2);

        List<Truck> truckList = master.listTrucks();
        System.out.println("   rental=" + master.listRental().size() + " truk=" + truckList.size());
        record(truckList.size() == 2, "2 truk tersimpan");
        record(truckList.get(0).getRentalName() != null, "join nama rental jalan");
        System.out.println();

        System.out.println("1b. Plat diketik langsung: ejaan berbeda harus jadi satu truk ...");
        int idRental = master.listRental().get(0).getRentalId();
        // Tiga ejaan berbeda untuk truk yang sama. Kalau masing-masing membuat truk baru,
        // laporan akan memecah satu truk menjadi tiga baris berbeda dan tidak ada yang
        // memberi tahu - jadi jumlah truknya yang diperiksa, bukan cuma idnya. Jalurnya
        // kini pastikanTruk, yang dipanggil dengan koneksi milik pemanggil - sama seperti
        // dipanggil TransactionDao.save di dalam transaksinya.
        Truck eja1;
        Truck eja2;
        Truck eja3;
        try (Connection c = Db.get()) {
            eja1 = master.pastikanTruk(c, "  be  9120 xy ", "Rental Sinar Jaya");
            eja2 = master.pastikanTruk(c, "BE 9120 XY", "Rental Sinar Jaya");
            eja3 = master.pastikanTruk(c, "be 9120 xy", null);
        }
        System.out.println("   ejaan 1 -> '" + eja1.getPlate() + "' id=" + eja1.getTruckId()
                + ", ejaan 2 -> id=" + eja2.getTruckId() + ", ejaan 3 -> id=" + eja3.getTruckId());
        record("BE 9120 XY".equals(eja1.getPlate()), "ejaan plat diseragamkan");
        record(eja1.getTruckId() == eja2.getTruckId() && eja2.getTruckId() == eja3.getTruckId(),
                "tiga ejaan menunjuk satu truk");
        record(master.listTrucks().size() == 3, "jumlah truk tetap 3, tidak bertambah");

        // Truk yang sudah ada tidak boleh berpindah pemilik hanya karena pilihan rental
        // di layar transaksi berbeda - daftar truk yang berlaku sebagai acuan.
        Truck d;
        try (Connection c = Db.get()) {
            d = master.pastikanTruk(c, "BE 9120 XY", null);
        }
        record(d.getRentalId() != null && d.getRentalId() == idRental,
                "rental truk lama tidak tertimpa");

        Truck kosong;
        try (Connection c = Db.get()) {
            kosong = master.pastikanTruk(c, "   ", "Rental Sinar Jaya");
        }
        record(kosong == null, "plat kosong tidak membuat truk");
        record(master.listTrucks().size() == 3, "plat kosong tidak menambah baris");
        System.out.println();

        // Truk yang sudah tersimpan dengan ejaan lama tidak boleh dibuat ulang. Penyeragaman
        // ejaan baru berlaku untuk data yang ditulis sejak sekarang, jadi database yang
        // sudah dipakai bisa menyimpan plat dengan ejaan lama. Kalau pencocokannya tidak
        // ikut menyeragamkan, truk yang sama dibuat ulang sebagai baris baru, lalu laporan
        // memecah satu truk menjadi dua pemilik.
        try (Connection c = Db.get(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO truk (plat) VALUES ('be 5555 xx')");
        }
        Truck lama;
        try (Connection c = Db.get()) {
            lama = master.pastikanTruk(c, "BE 5555 XX", null);
        }
        System.out.println("   plat lama 'be 5555 xx' dicari sebagai 'BE 5555 XX' -> id=" + lama.getTruckId());
        record(lama.getPlate().equals("BE 5555 XX"), "ejaan lama ditemukan lewat bentuk seragamnya");
        int jumlah = 0;
        for (Truck t : master.listTrucks()) {
            if ("BE 5555 XX".equals(t.getPlate())) {
                jumlah++;
            }
        }
        record(jumlah == 1, "truk ejaan lama tidak dibuat ulang");
        try (Connection c = Db.get(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM truk WHERE plat = 'be 5555 xx'");
        }
        System.out.println();

        truckList = master.listTrucks();

        // Ganti pemilik sebuah truk. Cabang penggantian ini sebelumnya tidak pernah
        // dijalankan pengujian mana pun - kedua truk di atas disimpan sebagai truk baru,
        // yang berarti cabang penambahannya. Padahal mengganti pemilik adalah satu-satunya
        // jalan memindahkan truk, dan salah di sini berarti truk berpindah ke pemilik yang
        // salah atau gagal tersimpan karena platnya bentrok.
        System.out.println("1c. Ganti pemilik truk ...");
        Rental tujuan = null;
        for (Rental r : master.listRental()) {
            if (!r.getRentalName().equals("Rental Sinar Jaya")) {
                tujuan = r;
            }
        }
        if (tujuan == null) {
            tujuan = new Rental();
            tujuan.setRentalName("Rental Tujuan Uji");
            master.saveRental(tujuan);
            tujuan = master.listRental().get(master.listRental().size() - 1);
        }
        Truck dipindah = findTruck(master.listTrucks(), "BE 8009 CF");
        int idLama = dipindah.getRentalId() == null ? -1 : dipindah.getRentalId();
        Truck ganti = new Truck();
        ganti.setTruckId(dipindah.getTruckId());
        ganti.setPlate(dipindah.getPlate());
        ganti.setRentalId(tujuan.getRentalId());
        // Kegagalannya ditangkap di sini supaya dilaporkan sebagai satu pemeriksaan yang
        // gagal. Kalau dibiarkan naik, seluruh berkas uji ini mati, pemeriksaan berikutnya
        // tidak ikut berjalan, dan hasilnya terlihat seperti uji yang belum selesai -
        // bukan uji yang menemukan masalah.
        boolean gantiBerhasil = true;
        try {
            master.saveTruck(ganti);
        } catch (SQLException e) {
            gantiBerhasil = false;
            System.out.println("   gagal mengganti pemilik: " + e.getMessage().split("\n")[0]);
        }

        Truck sesudah = findTruck(master.listTrucks(), "BE 8009 CF");
        System.out.println("   pemilik '" + sesudah.getPlate() + "': " + idLama + " -> " + sesudah.getRentalId()
                + " (" + sesudah.getRentalName() + ")");
        record(gantiBerhasil && sesudah.getRentalId() != null && sesudah.getRentalId() == tujuan.getRentalId(),
                "pemilik truk berganti");
        // Platnya harus tetap sama: penggantian pemilik tidak boleh mengubah nomor plat,
        // dan tidak boleh menambah baris truk baru.
        record("BE 8009 CF".equals(sesudah.getPlate()), "plat tidak ikut berubah");
        int jumlahBaris = 0;
        for (Truck t : master.listTrucks()) {
            if ("BE 8009 CF".equals(t.getPlate())) {
                jumlahBaris++;
            }
        }
        record(jumlahBaris == 1, "pindah pemilik tidak menambah truk kedua");
        // Dikembalikan ke pemilik semula supaya pemeriksaan berikutnya tidak ikut berubah.
        if (gantiBerhasil) {
            Truck balik = new Truck();
            balik.setTruckId(sesudah.getTruckId());
            balik.setPlate(sesudah.getPlate());
            balik.setRentalId(idLama < 0 ? null : idLama);
            master.saveTruck(balik);
        }
        System.out.println();

        System.out.println("1d. Tambah rental baru tidak menimpa rental lama ...");
        int jumlahRental = master.listRental().size();
        Rental rBaru = new Rental();
        rBaru.setRentalName("Rental Baru Sekali Ini");
        master.saveRental(rBaru);
        int lamaUtuh = 0;
        int baruMuncul = 0;
        for (Rental r : master.listRental()) {
            if ("Rental Sinar Jaya".equals(r.getRentalName())) {
                lamaUtuh++;
            }
            if ("Rental Baru Sekali Ini".equals(r.getRentalName())) {
                baruMuncul++;
            }
        }
        System.out.println("   rental: " + jumlahRental + " -> " + master.listRental().size());
        record(master.listRental().size() == jumlahRental + 1, "rental baru menjadi baris tersendiri");
        record(lamaUtuh == 1 && baruMuncul == 1, "rental lama tidak berubah nama");

        System.out.println("1e. Nama rental dan plat kembar ditolak dengan pesan ...");
        String pesanRental = null;
        try {
            Rental kembar = new Rental();
            kembar.setRentalName("RENTAL BARU SEKALI INI");   // beda besar-kecil huruf
            master.saveRental(kembar);
        } catch (IllegalArgumentException e) {
            pesanRental = e.getMessage();
        }
        System.out.println("   pesan: " + pesanRental);
        record(pesanRental != null && pesanRental.contains("sudah dipakai"),
                "nama rental kembar ditolak dengan pesan");
        record(master.listRental().size() == jumlahRental + 1, "nama kembar tidak menambah baris");

        String pesanPlat = null;
        try {
            Truck kembarPlat = new Truck();
            kembarPlat.setPlate("kb 8234 hd");   // ejaan lain, truk yang sama
            kembarPlat.setRentalId(idRental);
            master.saveTruck(kembarPlat);
        } catch (IllegalArgumentException e) {
            pesanPlat = e.getMessage();
        }
        System.out.println("   pesan: " + pesanPlat);
        record(pesanPlat != null && pesanPlat.contains("sudah terdaftar"),
                "plat kembar ditolak dengan pesan");
        record(master.listTrucks().size() == 3, "plat kembar tidak menambah baris");
        System.out.println();

        truckList = master.listTrucks();

        System.out.println("2. Simpan transaksi 19-01-2026 (3 baris) lewat DAO ...");
        Transaction t = new Transaction();
        t.setDate(LocalDate.of(2026, 1, 19));

        List<TransactionDetail> detail = new ArrayList<>();
        detail.add(makeDetail(findTruck(truckList, "KB 8234 HD"), 7200, 7050, 15, 1150));
        detail.add(makeDetail(findTruck(truckList, "BE 8009 CF"), 6000, 5970, 15, 1150));
        detail.add(makeDetail(findTruck(truckList, "BE 8009 CF"), 6280, 6150, 15, 1150));
        int transactionId = transactionDao.save(t, detail);
        System.out.println("   id_transaksi=" + transactionId + " tanggal=" + t.getDate() + " baris=" + detail.size());
        record(transactionId > 0, "transaksi tersimpan");
        System.out.println();

        System.out.println("3. Baca laporan, cek total ...");
        List<ReportRow> report = transactionDao.listReport(null, null);
        for (ReportRow b : report) {
            System.out.printf("   %s %-12s lapak=%s pabrik=%s ref=%s%% bb=%s harga=%s uang=%s%n",
                    b.getDate(), b.getPlate(), plain(b.getFieldWeight()), plain(b.getFactoryWeight()),
                    plain(b.getRefractionPercent()), plain(b.getNetWeight()), plain(b.getPrice()),
                    Calculator.formatCurrency(b.getTotalAmount()));
        }
        record(report.size() == 3, "laporan berisi 3 baris");

        BigDecimal expectedTotal = new BigDecimal("6888500")
                .add(new BigDecimal("5830500"))
                .add(new BigDecimal("6008750"));
        BigDecimal total = transactionDao.totalAmount(null, null);
        record(total.compareTo(expectedTotal) == 0, "total uang = " + expectedTotal.toPlainString());
        System.out.println("   total uang   = " + Calculator.formatCurrency(total) + " (harap " + Calculator.formatCurrency(expectedTotal) + ")");

        BigDecimal expectedTotalNetWeight = new BigDecimal("5990").add(new BigDecimal("5070")).add(new BigDecimal("5225"));
        BigDecimal totalNetWeight = transactionDao.totalNetWeight(null, null);
        record(totalNetWeight.compareTo(expectedTotalNetWeight) == 0, "total berat bersih");
        System.out.println("   total bb     = " + plain(totalNetWeight) + " kg (harap " + plain(expectedTotalNetWeight) + " kg)");
        System.out.println();

        System.out.println("4. Rekap per rental ...");
        Map<String, BigDecimal> summary = transactionDao.summaryPerRental(null, null);
        for (Map.Entry<String, BigDecimal> e : summary.entrySet()) {
            System.out.println("   " + e.getKey() + " = Rp " + Calculator.formatCurrency(e.getValue()));
        }
        record(summary.size() == 1, "rekap 1 rental");
        System.out.println();

        // Plat yang tersimpan dengan ejaan lama harus tetap tampil seragam di laporan.
        // Laporan membaca plat mentah dari database, sedangkan daftar truk menyeragamkannya
        // saat dibaca - tanpa penyeragaman di jalur laporan, satu truk tampil dengan dua
        // ejaan berbeda di aplikasi yang sama.
        System.out.println("4b. Plat ejaan lama tetap seragam di laporan ...");
        try (Connection c = Db.get(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO truk (plat) VALUES ('be 5555 xx')");
            s.executeUpdate("INSERT INTO transaksi_detail (id_transaksi,id_truk,bobot_lapak,bobot_pabrik,"
                    + "refraksi_persen,berat_bersih,harga,jumlah_uang) "
                    + "SELECT 1, id_truk, 7200, 7050, 15, 5990, 1150, 6888500 FROM truk WHERE plat='be 5555 xx'");
        }
        boolean adaYangSeragam = false;
        for (ReportRow b : transactionDao.listReport(null, null)) {
            if ("BE 5555 XX".equals(b.getPlate())) {
                adaYangSeragam = true;
            }
            if (b.getPlate() != null && !b.getPlate().equals(Truck.normalizePlate(b.getPlate()))) {
                System.out.println("   plat di laporan belum seragam: '" + b.getPlate() + "'");
                adaYangSeragam = false;
                break;
            }
        }
        record(adaYangSeragam, "plat ejaan lama tampil seragam di laporan");

        try (Connection c = Db.get(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM transaksi_detail WHERE id_truk IN (SELECT id_truk FROM truk WHERE plat='be 5555 xx')");
            s.executeUpdate("DELETE FROM truk WHERE plat = 'be 5555 xx'");
        }
        System.out.println();

        System.out.println("5. Uji rollback: satu baris rusak -> semua batal ...");
        Transaction brokenTrx = new Transaction();
        brokenTrx.setDate(LocalDate.of(2026, 1, 20));
        List<TransactionDetail> brokenDetail = new ArrayList<>();
        brokenDetail.add(makeDetail(findTruck(truckList, "KB 8234 HD"), 1000, 1000, 15, 1150));
        TransactionDetail broken = makeDetail(findTruck(truckList, "BE 8009 CF"), 1000, 1000, 15, 1150);
        broken.setFactoryWeight(null);   // memicu error NOT NULL
        brokenDetail.add(broken);
        boolean rejected = false;
        try {
            transactionDao.save(brokenTrx, brokenDetail);
        } catch (SQLException e) {
            rejected = true;
        }
        int countAfter = transactionDao.listReport(null, null).size();
        System.out.println("   ditolak=" + rejected + "  baris laporan sekarang=" + countAfter + " (harap tetap 3)");
        record(rejected, "baris rusak ditolak");
        record(countAfter == 3, "rollback bersih, tidak ada sisa");
        System.out.println();

        System.out.println("5b. Truk & rental baru dibuat di dalam transaksi ...");
        int trukSebelum = master.listTrucks().size();
        int rentalSebelum = master.listRental().size();
        Transaction trxBaru = new Transaction();
        trxBaru.setDate(LocalDate.of(2026, 1, 21));
        List<TransactionDetail> detailBaru = new ArrayList<>();
        TransactionDetail dBaru = makeDetail(new Truck(), 5000, 4900, 15, 1150);
        dBaru.setPlate("BK 1111 AA");
        dBaru.setRentalName("Rental Dari Transaksi");
        detailBaru.add(dBaru);
        int idTrxBaru = transactionDao.save(trxBaru, detailBaru);
        Truck trukBaru = findTruck(master.listTrucks(), "BK 1111 AA");
        System.out.println("   truk=" + (trukBaru == null ? "-" : trukBaru.getPlate())
                + " pemilik=" + (trukBaru == null ? "-" : trukBaru.getRentalName()));
        record(idTrxBaru > 0 && trukBaru != null, "truk baru dibuat di dalam transaksi");
        record(trukBaru != null && "Rental Dari Transaksi".equals(trukBaru.getRentalName()),
                "rental baru ikut dibuat dan tersambung ke truknya");
        record(master.listTrucks().size() == trukSebelum + 1, "truk bertambah tepat satu");
        record(master.listRental().size() == rentalSebelum + 1, "rental bertambah tepat satu");
        transactionDao.deleteTransaction(idTrxBaru);

        System.out.println("5c. Simpan gagal: truk/rental hantu tidak tertinggal ...");
        int trukHantuSebelum = master.listTrucks().size();
        int rentalHantuSebelum = master.listRental().size();
        Transaction trxHantu = new Transaction();
        trxHantu.setDate(LocalDate.of(2026, 1, 22));
        List<TransactionDetail> detailHantu = new ArrayList<>();
        TransactionDetail dHantu = makeDetail(new Truck(), 4000, 3900, 15, 1150);
        dHantu.setPlate("BH 2222 ZZ");
        dHantu.setRentalName("Rental Hantu");
        detailHantu.add(dHantu);
        TransactionDetail dRusak = makeDetail(findTruck(truckList, "KB 8234 HD"), 1000, 1000, 15, 1150);
        dRusak.setFactoryWeight(null);   // memicu error NOT NULL
        detailHantu.add(dRusak);
        boolean hantuGagal = false;
        try {
            transactionDao.save(trxHantu, detailHantu);
        } catch (SQLException e) {
            hantuGagal = true;
        }
        int hantu = 0;
        for (Truck ht : master.listTrucks()) {
            if ("BH 2222 ZZ".equals(Truck.normalizePlate(ht.getPlate()))) {
                hantu++;
            }
        }
        System.out.println("   ditolak=" + hantuGagal
                + "  truk=" + master.listTrucks().size() + " rental=" + master.listRental().size());
        record(hantuGagal, "transaksi rusak ditolak");
        record(master.listTrucks().size() == trukHantuSebelum && hantu == 0,
                "truk hantu tidak tertinggal");
        record(master.listRental().size() == rentalHantuSebelum, "rental hantu tidak tertinggal");
        System.out.println();

        System.out.println("6. Hapus transaksi (cascade ke detail) ...");
        transactionDao.deleteTransaction(transactionId);
        record(transactionDao.listReport(null, null).isEmpty(), "hapus transaksi ikut hapus detail");

        System.out.println("7. Ubah catatan pengiriman lewat updateDelivery ...");
        Truck kbTruk = findTruck(master.listTrucks(), "KB 8234 HD");
        int idUbah = simpanPengiriman(transactionDao, kbTruk, LocalDate.of(2026, 2, 1), 7200, 7000, 15, 1150);

        // Uang dihitung ulang oleh Calculator, bukan disalin begitu saja dari layar.
        // Angkanya dipilih supaya pembulatan ke bawah ke kelipatan 5 benar-benar berlaku:
        // 6150 x 85% = 5227.5 -> 5225, jadi hitungan mentah tanpa pembulatan pasti beda.
        BigDecimal pabrikUbah = new BigDecimal("6150");
        BigDecimal refraksiUbah = new BigDecimal("15");
        BigDecimal hargaUbah = new BigDecimal("1200");
        transactionDao.updateDelivery(idUbah, LocalDate.of(2026, 2, 1), "KB 8234 HD", null,
                new BigDecimal("6500"), pabrikUbah, refraksiUbah, null, hargaUbah);
        ReportRow ubah = cariBaris(transactionDao, idUbah);
        BigDecimal harapBersih = Calculator.netWeight(pabrikUbah, refraksiUbah);
        BigDecimal harapUang = Calculator.totalAmount(harapBersih, hargaUbah);
        System.out.println("   berat bersih=" + (ubah == null ? "-" : plain(ubah.getNetWeight()))
                + ", uang=" + (ubah == null ? "-" : plain(ubah.getTotalAmount()))
                + " (harap " + plain(harapBersih) + " / " + plain(harapUang) + ")");
        record(ubah != null && ubah.getNetWeight().compareTo(harapBersih) == 0,
                "ubah: berat bersih dihitung ulang oleh Calculator");
        record(ubah != null && ubah.getTotalAmount().compareTo(harapUang) == 0,
                "ubah: jumlah uang dihitung ulang dari berat bersih baru");
        record(ubah != null && ubah.getNetWeight().remainder(Calculator.WEIGHT_MULTIPLE).signum() == 0,
                "ubah: berat bersih dibulatkan ke bawah ke kelipatan 5");

        // Tanggal headernya ikut pindah, dan barisnya tetap ditemukan lewat id_detail.
        transactionDao.updateDelivery(idUbah, LocalDate.of(2026, 2, 10), "KB 8234 HD", null,
                new BigDecimal("6500"), pabrikUbah, refraksiUbah, null, hargaUbah);
        ReportRow pindahTanggal = cariBaris(transactionDao, idUbah);
        System.out.println("   tanggal 2026-02-01 -> " + (pindahTanggal == null ? "-" : pindahTanggal.getDate()));
        record(pindahTanggal != null && LocalDate.of(2026, 2, 10).equals(pindahTanggal.getDate()),
                "ubah: tanggal headernya ikut berubah, baris ditemukan lewat id_detail");

        // Plat yang belum tercatat: truk dan rentalnya dibuat, catatan pindah ke sana.
        int trukUbahSebelum = master.listTrucks().size();
        int rentalUbahSebelum = master.listRental().size();
        transactionDao.updateDelivery(idUbah, LocalDate.of(2026, 2, 10), "BL 7777 QQ", "Rental Ubah Baru",
                new BigDecimal("6500"), pabrikUbah, refraksiUbah, null, hargaUbah);
        Truck trukUbahBaru = null;
        for (Truck tk : master.listTrucks()) {
            if ("BL 7777 QQ".equals(tk.getPlate())) {
                trukUbahBaru = tk;
            }
        }
        ReportRow pindahTruk = cariBaris(transactionDao, idUbah);
        System.out.println("   truk=" + (trukUbahBaru == null ? "-" : trukUbahBaru.getPlate())
                + ", pemilik=" + (trukUbahBaru == null ? "-" : trukUbahBaru.getRentalName()));
        record(trukUbahBaru != null && master.listTrucks().size() == trukUbahSebelum + 1,
                "ubah: plat yang belum tercatat membuat truk baru");
        record(trukUbahBaru != null && "Rental Ubah Baru".equals(trukUbahBaru.getRentalName())
                        && master.listRental().size() == rentalUbahSebelum + 1,
                "ubah: rental baru ikut dibuat dan tersambung ke truknya");
        record(pindahTruk != null && "BL 7777 QQ".equals(pindahTruk.getPlate())
                        && "Rental Ubah Baru".equals(pindahTruk.getRentalName()),
                "ubah: catatan terpasang ke truk baru (plat + rental tampil di riwayat)");

        // Rental adalah milik truk: input rental lain untuk plat yang sudah ada tidak boleh
        // memindahkan pemiliknya, dan tidak boleh menambah baris rental baru.
        int rentalUbahLagi = master.listRental().size();
        transactionDao.updateDelivery(idUbah, LocalDate.of(2026, 2, 10), "KB 8234 HD", "Rental Palsu Ubah",
                new BigDecimal("6500"), pabrikUbah, refraksiUbah, null, hargaUbah);
        ReportRow tetapMilik = cariBaris(transactionDao, idUbah);
        record(tetapMilik != null && "KB 8234 HD".equals(tetapMilik.getPlate())
                        && "Rental Sinar Jaya".equals(tetapMilik.getRentalName()),
                "ubah: rental truk yang sudah ada tidak tertimpa input rental berbeda");
        record(master.listRental().size() == rentalUbahLagi,
                "ubah: rental asing tidak dibuat untuk plat yang sudah tercatat");

        // Id detail yang tak dikenal: ditolak, tidak ada yang berubah, truk/rental dari
        // upaya yang gagal pun ikut batal.
        String potretUbah = potretDetail();
        int trukUbahHantuSebelum = master.listTrucks().size();
        boolean ubahDitolak = false;
        try {
            transactionDao.updateDelivery(999999, LocalDate.of(2026, 2, 10), "BG 6666 GG", "Rental Hantu Ubah",
                    new BigDecimal("6500"), pabrikUbah, refraksiUbah, null, hargaUbah);
        } catch (SQLException e) {
            ubahDitolak = true;
        }
        int trukUbahHantu = 0;
        for (Truck tk : master.listTrucks()) {
            if ("BG 6666 GG".equals(tk.getPlate())) {
                trukUbahHantu++;
            }
        }
        System.out.println("   ditolak=" + ubahDitolak + ", detail=" + potretDetail() + " (harap " + potretUbah + ")");
        record(ubahDitolak, "ubah: id detail yang tak dikenal ditolak dengan SQLException");
        record(potretDetail().equals(potretUbah),
                "ubah: id tak dikenal tidak mengubah apa pun (jumlah baris dan total uang tetap)");
        record(master.listTrucks().size() == trukUbahHantuSebelum && trukUbahHantu == 0,
                "ubah: truk dan rental dari upaya yang gagal ikut dibatalkan");

        transactionDao.deleteDeliveries(Arrays.asList(idUbah));
        System.out.println();

        System.out.println("8. Hapus catatan pengiriman lewat deleteDeliveries ...");
        Truck beTruk = findTruck(master.listTrucks(), "BE 8009 CF");
        Truck xyTruk = findTruck(master.listTrucks(), "BE 9120 XY");
        int idHapusA = simpanPengiriman(transactionDao, kbTruk, LocalDate.of(2026, 3, 1), 5000, 4900, 15, 1150);
        int idHapusB = simpanPengiriman(transactionDao, beTruk, LocalDate.of(2026, 3, 2), 5100, 5000, 15, 1150);
        int idHapusC = simpanPengiriman(transactionDao, xyTruk, LocalDate.of(2026, 3, 3), 5200, 5100, 15, 1150);

        // Hanya yang diminta yang terhapus, dan header yang jadi kosong ikut terhapus.
        transactionDao.deleteDeliveries(Arrays.asList(idHapusA));
        boolean aHilang = true, bMasih = false, cMasih = false;
        int sisaHapus = 0;
        for (ReportRow b : transactionDao.listDeliveries(null, null)) {
            sisaHapus++;
            if (b.getDetailId() == idHapusA) aHilang = false;
            if (b.getDetailId() == idHapusB) bMasih = true;
            if (b.getDetailId() == idHapusC) cMasih = true;
        }
        System.out.println("   sisa=" + sisaHapus + ", header tanpa detail=" + hitungHeaderKosong());
        record(aHilang && bMasih && cMasih && sisaHapus == 2,
                "hapus: hanya pengiriman yang diminta yang terhapus, yang lain utuh");
        record(hitungHeaderKosong() == 0, "hapus: header yang jadi tanpa detail ikut terhapus");

        // Salah satu id tidak dikenal: semuanya batal, tidak boleh setengah terhapus.
        boolean hapusGagal = false;
        try {
            transactionDao.deleteDeliveries(Arrays.asList(idHapusB, 999999));
        } catch (SQLException e) {
            hapusGagal = true;
        }
        System.out.println("   ditolak=" + hapusGagal
                + ", B masih ada=" + (cariBaris(transactionDao, idHapusB) != null));
        record(hapusGagal, "hapus: daftar yang memuat id tak dikenal ditolak");
        record(cariBaris(transactionDao, idHapusB) != null,
                "hapus: id tak dikenal membatalkan semuanya, tidak ada yang setengah terhapus");

        // Daftar kosong atau null: tidak melempar galat dan tidak menghapus apa pun.
        String potretHapus = potretDetail();
        transactionDao.deleteDeliveries(new ArrayList<Integer>());
        transactionDao.deleteDeliveries(null);
        record(potretDetail().equals(potretHapus),
                "hapus: daftar kosong atau null tidak melempar galat dan tidak menghapus apa pun");

        // Header kosong warisan (tanpa detail sama sekali) ikut dibersihkan ketika
        // pengiriman lain dihapus, walau bukan miliknya.
        try (Connection c = Db.get(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO transaksi (tanggal) VALUES ('2026-03-05')");
        }
        transactionDao.deleteDeliveries(Arrays.asList(idHapusC));
        record(cariBaris(transactionDao, idHapusC) == null && hitungHeaderKosong() == 0,
                "hapus: header kosong warisan ikut dibersihkan meski bukan milik detail yang dihapus");
        transactionDao.deleteDeliveries(Arrays.asList(idHapusB));
        System.out.println();

        System.out.println("9. Riwayat pengiriman lewat listDeliveries ...");
        int idUrutX = simpanPengiriman(transactionDao, kbTruk, LocalDate.of(2026, 4, 1), 6000, 5900, 15, 1150);
        int idUrutY1 = simpanPengiriman(transactionDao, beTruk, LocalDate.of(2026, 4, 2), 6100, 6000, 15, 1150);
        int idUrutY2 = simpanPengiriman(transactionDao, xyTruk, LocalDate.of(2026, 4, 2), 6200, 6100, 15, 1150);
        int idUrutZ = simpanPengiriman(transactionDao, kbTruk, LocalDate.of(2026, 4, 3), 6300, 6200, 15, 1150);

        List<ReportRow> urutan = transactionDao.listDeliveries(null, null);
        StringBuilder urutanId = new StringBuilder();
        for (ReportRow b : urutan) {
            urutanId.append(b.getDetailId()).append(' ');
        }
        System.out.println("   urutan id: " + urutanId);
        record(urutan.size() == 4
                && urutan.get(0).getDetailId() == idUrutZ
                && urutan.get(1).getDetailId() == idUrutY2
                && urutan.get(2).getDetailId() == idUrutY1
                && urutan.get(3).getDetailId() == idUrutX,
                "riwayat: satu baris per pengiriman, yang paling baru lebih dulu");
        record(urutan.size() == 4
                && urutan.get(1).getDate().equals(urutan.get(2).getDate())
                && urutan.get(1).getDetailId() > urutan.get(2).getDetailId(),
                "riwayat: tanggal sama diurut menurun menurut id_detail");

        // Batas tanggal dipakai apa adanya di kedua ujung; null berarti tanpa batas.
        List<ReportRow> jendela = transactionDao.listDeliveries(LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 2));
        record(jendela.size() == 2
                && jendela.get(0).getDetailId() == idUrutY2
                && jendela.get(1).getDetailId() == idUrutY1,
                "riwayat: batas tanggal inclusif di kedua ujung");
        List<ReportRow> tanpaBatas = transactionDao.listDeliveries(null, null);
        record(tanpaBatas.size() == 4, "riwayat: null berarti tanpa batas, semua baris tampil");

        // Detail tanpa truk (id_truk NULL): plat dan rental null, tanpa melempar galat.
        int idTanpaTruk = simpanPengiriman(transactionDao, new Truck(), LocalDate.of(2026, 4, 5), 4000, 3900, 15, 1150);
        ReportRow tanpaTruk = cariBaris(transactionDao, idTanpaTruk);
        System.out.println("   baris tanpa truk: plat=" + (tanpaTruk == null ? "-" : tanpaTruk.getPlate())
                + ", rental=" + (tanpaTruk == null ? "-" : tanpaTruk.getRentalName()));
        record(tanpaTruk != null && tanpaTruk.getPlate() == null && tanpaTruk.getRentalName() == null,
                "riwayat: truk kosong tampil plat null dan rental null tanpa galat");
        System.out.println();
        System.out.println("10. Saringan riwayat: rental & plat di listDeliveries ...");
        int dasar = transactionDao.listDeliveries(null, null).size();

        // Fixture: tiga rental (satu namanya awalan nama yang lain), truknya, dan
        // pengiriman di beberapa tanggal supaya setiap saringan bisa diasingkan.
        Truck trukMitraA;
        Truck trukMitraB;
        Truck trukMitraC;
        Truck trukTani;
        Truck trukSejahtera;
        try (Connection c = Db.get()) {
            trukMitraA = master.pastikanTruk(c, "BM 1100 AA", "CV Mitra");
            trukMitraB = master.pastikanTruk(c, "BM 2200 BB", "CV Mitra");
            trukMitraC = master.pastikanTruk(c, "BM 3300 CC", "CV Mitra");
            trukTani = master.pastikanTruk(c, "BN 4400 DD", "CV Mitra Tani");
            trukSejahtera = master.pastikanTruk(c, "BM 2500 EE", "Rental Sejahtera");
        }
        int idM0 = simpanPengiriman(transactionDao, trukMitraB, LocalDate.of(2026, 4, 20), 5000, 4900, 15, 1150);
        int idM1 = simpanPengiriman(transactionDao, trukMitraA, LocalDate.of(2026, 5, 1), 5100, 5000, 15, 1150);
        int idM2 = simpanPengiriman(transactionDao, trukMitraB, LocalDate.of(2026, 5, 2), 5200, 5100, 15, 1150);
        int idM3 = simpanPengiriman(transactionDao, trukMitraC, LocalDate.of(2026, 5, 3), 5300, 5200, 15, 1150);
        int idT1 = simpanPengiriman(transactionDao, trukTani, LocalDate.of(2026, 5, 2), 5400, 5300, 15, 1150);
        int idS1 = simpanPengiriman(transactionDao, trukSejahtera, LocalDate.of(2026, 5, 3), 5500, 5400, 15, 1150);
        int idN1 = simpanPengiriman(transactionDao, new Truck(), LocalDate.of(2026, 5, 4), 4000, 3900, 15, 1150);
        List<ReportRow> semua = transactionDao.listDeliveries(null, null);
        System.out.println("   dasar " + dasar + " + fixture 7 = " + semua.size());
        record(semua.size() == dasar + 7, "saringan: fixture 7 pengiriman siap");

        // Rental memilih hanya pengirimannya sendiri; rental lain dan truk kosong tertutup.
        List<ReportRow> mitra = transactionDao.listDeliveries(null, null, "CV Mitra", null);
        List<Integer> idMitra = new ArrayList<>();
        boolean mitraBersih = mitra.size() == 4;
        for (ReportRow b : mitra) {
            idMitra.add(b.getDetailId());
            if (!"CV Mitra".equals(b.getRentalName())) {
                mitraBersih = false;
            }
        }
        System.out.println("   'CV Mitra' -> " + gabungId(mitra));
        record(mitraBersih && !idMitra.contains(idT1) && !idMitra.contains(idS1) && !idMitra.contains(idN1),
                "saringan: rental hanya membawa pengirimannya sendiri, rental lain tertutup");

        // "CV Mitra" adalah awalan "CV Mitra Tani": kalau pencocokannya LIKE, pengiriman
        // Tani ikut terbawa - harusnya tidak.
        List<ReportRow> tani = transactionDao.listDeliveries(null, null, "CV Mitra Tani", null);
        System.out.println("   'CV Mitra Tani' -> " + gabungId(tani));
        record(mitra.size() == 4 && tani.size() == 1 && tani.get(0).getDetailId() == idT1,
                "saringan: nama rental dicocok persis, bukan sebagian");

        record(transactionDao.listDeliveries(null, null, null, null).size() == dasar + 7
                        && transactionDao.listDeliveries(null, null, "   ", null).size() == dasar + 7,
                "saringan: rental null atau kosong berarti tanpa saringan");

        List<ReportRow> platTengah = transactionDao.listDeliveries(null, null, null, "4400");
        List<ReportRow> platAwal = transactionDao.listDeliveries(null, null, null, "BM 22");
        System.out.println("   '4400' -> " + gabungId(platTengah) + ", 'BM 22' -> " + gabungId(platAwal));
        record(platTengah.size() == 1 && platTengah.get(0).getDetailId() == idT1
                        && gabungId(platAwal).equals(idM2 + "," + idM0),
                "saringan: plat dicocok sebagian, dari tengah maupun dari awal");

        record(gabungId(transactionDao.listDeliveries(null, null, null, "bm 22")).equals(idM2 + "," + idM0),
                "saringan: huruf besar-kecil plat pencari diabaikan");

        record(gabungId(transactionDao.listDeliveries(null, null, null, "  bm   2200  bb "))
                        .equals(idM2 + "," + idM0),
                "saringan: ejaan plat pencari diseragamkan dulu (spasi berlebih dirapatkan)");

        record(transactionDao.listDeliveries(null, null, null, "   ").size() == dasar + 7,
                "saringan: plat null atau kosong berarti tanpa saringan");

        // Truk kosong tidak punya nama rental (LEFT JOIN -> NULL), jadi jangan sampai
        // ikut ke hasil saringan rental mana pun.
        List<ReportRow> sejahtera = transactionDao.listDeliveries(null, null, "Rental Sejahtera", null);
        boolean tanpaTrukTampil = false;
        for (ReportRow b : semua) {
            if (b.getDetailId() == idN1) {
                tanpaTrukTampil = true;
            }
        }
        System.out.println("   'Rental Sejahtera' -> " + gabungId(sejahtera)
                + ", tanpa truk tampil tanpa saringan=" + tanpaTrukTampil);
        record(sejahtera.size() == 1 && sejahtera.get(0).getDetailId() == idS1 && tanpaTrukTampil,
                "saringan: pengiriman tanpa truk tertutup saringan rental, tampil kalau tanpa saringan");

        List<ReportRow> jendelaSaring = transactionDao.listDeliveries(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3), "CV Mitra", null);
        System.out.println("   2026-05-01..2026-05-03 'CV Mitra' -> " + gabungId(jendelaSaring));
        record(jendelaSaring.size() == 3
                        && gabungId(jendelaSaring).equals(idM3 + "," + idM2 + "," + idM1),
                "saringan: batas tanggal inclusif di kedua ujung walau ada saringan rental");

        record(transactionDao.listDeliveries(null, LocalDate.of(2026, 4, 19), null, null).size() == dasar
                        && transactionDao.listDeliveries(LocalDate.of(2026, 5, 4), null, null, null).size() == 1,
                "saringan: batas tanggal null berarti tanpa batas di ujung itu");

        List<ReportRow> ketat = transactionDao.listDeliveries(
                LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 3), "CV Mitra", "BM 2");
        System.out.println("   ketat (tanggal+rental+plat) -> " + gabungId(ketat));
        record(ketat.size() == 1 && ketat.get(0).getDetailId() == idM2,
                "saringan: tanggal + rental + plat bekerja bersama (DAN)");
        record(gabungId(transactionDao.listDeliveries(
                        LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 3), "CV Mitra", null))
                        .equals(idM3 + "," + idM2)
                        && gabungId(transactionDao.listDeliveries(
                        LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 3), null, "BM 2"))
                        .equals(idS1 + "," + idM2)
                        && gabungId(transactionDao.listDeliveries(null, null, "CV Mitra", "BM 2"))
                        .equals(idM2 + "," + idM0),
                "saringan: melonggarkan satu saringan menambah baris kembali");

        // Daftar yang diterima pemanggil harus utuh: jumlah baris dan SUM uangnya sama
        // dengan agregat SQL yang memakai saringan persis sama.
        List<ReportRow> ujiSum = transactionDao.listDeliveries(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 3), "CV Mitra", "BM 2");
        BigDecimal uangBaris = BigDecimal.ZERO;
        for (ReportRow b : ujiSum) {
            uangBaris = uangBaris.add(b.getTotalAmount());
        }
        String potretBaris = ujiSum.size() + "/" + plain(uangBaris);
        String potretSql = potretSaringan(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 3), "CV Mitra", "BM 2");
        System.out.println("   baris " + potretBaris + " = SQL " + potretSql);
        record(potretBaris.equals(potretSql),
                "saringan: jumlah baris dan uangnya sama dengan SUM database saringan yang sama");

        record(gabungId(mitra).equals(idM3 + "," + idM2 + "," + idM1 + "," + idM0),
                "saringan: hasil tetap diurut dari yang paling baru");

        // Ejaan lama pada plat harus tetap ditemukan saringan.
        //
        // Database yang sudah lama dipakai bisa menyimpan plat dengan spasi berlebih dan
        // huruf kecil ("be  7777  hd"). Pencocokan teks di dalam SQL tidak menemukannya
        // walaupun barisnya terbaca rapi di layar - dan yang membaca akan mengira
        // catatannya tidak ada, lalu mencatatnya untuk kedua kalinya. Karena itu
        // pencocokan plat dan rental dikerjakan memakai aturan penyeragaman aplikasi.
        Truck trukEjaanLama = new Truck();
        try (Connection c = Db.get(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO truk (plat, id_rental) VALUES ('be  7777  hd', "
                    + "(SELECT id_rental FROM rental WHERE nama_rental = 'CV Mitra'))");
            try (ResultSet rs = s.executeQuery("SELECT id_truk FROM truk WHERE plat = 'be  7777  hd'")) {
                rs.next();
                trukEjaanLama.setTruckId(rs.getInt(1));
            }
        }
        int idEjaanLama = simpanPengiriman(transactionDao, trukEjaanLama,
                LocalDate.of(2026, 5, 6), 6000, 5900, 15, 1150);
        for (String cari : new String[]{"7777", "be 7777", "BE 7777 HD"}) {
            boolean ketemu = false;
            for (ReportRow b : transactionDao.listDeliveries(null, null, null, cari)) {
                if (b.getDetailId() == idEjaanLama) {
                    ketemu = true;
                }
            }
            record(ketemu, "saringan: plat ejaan lama ketemu saat dicari \"" + cari + "\"");
        }

        // Rental dicocokkan dengan aturan penyeragaman aplikasi, jadi huruf besar-kecil
        // tidak menentukan - sama seperti di tempat lain yang membandingkan nama rental.
        record(transactionDao.listDeliveries(null, null, "cv mitra", null).size()
                        == transactionDao.listDeliveries(null, null, "CV Mitra", null).size(),
                "saringan: rental tanpa beda huruf besar-kecil");

        // Penghapusan master yang masih dipakai riwayat harus ditolak sebelum DELETE
        // sempat jalan: tanpa penolakan, plat dan pemiliknya lenyap diam-diam dari
        // catatan lama (kuncinya ON DELETE SET NULL, bukan gagal).
        System.out.println("\n11. Hapus truk/rental yang masih dipakai riwayat ditolak ...");
        ujiHapusMaster(master, transactionDao);

        System.out.println("\n12. Cadangan database non-H2 ditolak dengan jelas ...");
        ujiCadangkan();
        System.out.println("\n13. Cadangan sungguhan tertulis sebagai file zip ...");
        ujiCadangkanFile();
        System.out.println("\n=== HASIL: " + passed + " lulus, " + failed + " gagal ===");
        if (failed > 0) {
            System.exit(1);
        }
    }


    /**
     * Uji penolakan penghapusan data master yang masih dipakai riwayat.
     *
     * <p>Kunci tamu transaksi_detail.id_truk dan truk.id_rental sama-sama
     * ON DELETE SET NULL, jadi tanpa penolakan DELETE tidak gagal — hanya diam-diam
     * mengosongkan id tersebut, dan plat/pemilik hilang dari laporan lama selamanya.
     * Yang diperiksa: penolakannya sendiri, isi database yang tidak berubah sedikit
     * pun, dan penghapusan yang tetap boleh untuk data tanpa riwayat.
     */
    private static void ujiHapusMaster(MasterDao master, TransactionDao transactionDao) throws Exception {
        Truck trukRiwayat;
        Truck trukBebas;
        try (Connection c = Db.get()) {
            trukRiwayat = master.pastikanTruk(c, "BR 1010 AA", "Rental Uji Hapus");
            trukBebas = master.pastikanTruk(c, "BR 2020 BB", "Rental Uji Hapus");
        }
        int idRentalSibuk = trukRiwayat.getRentalId();

        // Rental tanpa satu truk pun: satu-satunya keadaan yang boleh dihapus.
        Rental rentalKosong = new Rental();
        rentalKosong.setRentalName("Rental Uji Hapus Kosong");
        master.saveRental(rentalKosong);
        int idRentalKosong = 0;
        for (Rental r : master.listRental()) {
            if ("Rental Uji Hapus Kosong".equals(r.getRentalName())) {
                idRentalKosong = r.getRentalId();
            }
        }

        int idRiwayat = simpanPengiriman(transactionDao, trukRiwayat,
                LocalDate.of(2026, 6, 1), 5000, 4900, 15, 1150);

        record(master.countDeliveriesForTruck(trukRiwayat.getTruckId()) == 1
                        && master.countDeliveriesForTruck(trukBebas.getTruckId()) == 0,
                "hapus master: jumlah pengiriman per truk terhitung tepat");
        record(master.countTrucksForRental(idRentalSibuk) == 2
                        && master.countTrucksForRental(idRentalKosong) == 0,
                "hapus master: jumlah truk per rental terhitung tepat");

        // Truk berriwayat: ditolak, dan inilah intinya — platnya tidak boleh hilang
        // dari riwayat. Tanpa penolakan, LEFT JOIN membawa NULL dan uangnya pindah
        // ke ember "(tanpa rental)".
        String pesanTruk = null;
        String potretSebelum = potretDetail();
        try {
            master.deleteTruck(trukRiwayat.getTruckId());
        } catch (IllegalStateException e) {
            pesanTruk = e.getMessage();
        }
        System.out.println("   pesan: " + (pesanTruk == null ? "-" : pesanTruk));
        record(pesanTruk != null && !pesanTruk.isEmpty(),
                "hapus master: truk berriwayat ditolak dengan pesan");
        // Pesan penolakan menyuruh operator KE SUATU TEMPAT. Kalau tempatnya sudah tidak
        // ada lagi - seperti saat halaman Data Master diganti dialog - pesannya menyesatkan
        // tanpa ada satu pun uji yang gagal. Yang diperiksa bukan kalimatnya, melainkan
        // bahwa yang disebut memang pintu masuk yang benar-benar ada.
        record(pesanTruk != null && pesanTruk.contains("Kelola Data Truk")
                        && !pesanTruk.contains("menu Data Master"),
                "hapus master: pesan penolakan menunjuk tempat yang benar-benar ada");
        record(findTruck(master.listTrucks(), "BR 1010 AA") != null,
                "hapus master: truk yang ditolak tetap ada");
        ReportRow barisRiwayat = cariBaris(transactionDao, idRiwayat);
        record(barisRiwayat != null && "BR 1010 AA".equals(barisRiwayat.getPlate()),
                "hapus master: plat masih terbaca di riwayat setelah penolakan");
        record(barisRiwayat != null && "Rental Uji Hapus".equals(barisRiwayat.getRentalName()),
                "hapus master: pemilik masih terbaca di riwayat setelah penolakan truk");
        record(potretDetail().equals(potretSebelum),
                "hapus master: penolakan tidak mengubah jumlah baris dan total uang");

        // Truk tanpa riwayat: tetap boleh dihapus (salah catat -> bersihkan truknya).
        master.deleteTruck(trukBebas.getTruckId());
        boolean trukBebasMasih = false;
        for (Truck t : master.listTrucks()) {
            if (t.getTruckId() == trukBebas.getTruckId()) {
                trukBebasMasih = true;
            }
        }
        record(!trukBebasMasih, "hapus master: truk tanpa riwayat terhapus");

        // Rental yang masih memiliki truk: ditolak, truknya tetap miliknya.
        String pesanRental = null;
        try {
            master.deleteRental(idRentalSibuk);
        } catch (IllegalStateException e) {
            pesanRental = e.getMessage();
        }
        System.out.println("   pesan: " + (pesanRental == null ? "-" : pesanRental));
        boolean rentalMasihAda = false;
        for (Rental r : master.listRental()) {
            if (r.getRentalId() == idRentalSibuk) {
                rentalMasihAda = true;
            }
        }
        record(pesanRental != null && !pesanRental.isEmpty() && rentalMasihAda,
                "hapus master: rental yang masih punya truk ditolak dan tetap ada");
        record(pesanRental != null && pesanRental.contains("Kelola Data Truk")
                        && !pesanRental.contains("menu Data Master"),
                "hapus master: pesan penolakan rental menunjuk tempat yang benar-benar ada");
        barisRiwayat = cariBaris(transactionDao, idRiwayat);
        record(barisRiwayat != null && "Rental Uji Hapus".equals(barisRiwayat.getRentalName()),
                "hapus master: truk tetap melaporkan pemiliknya setelah rental ditolak");

        // Rental tanpa truk: tetap boleh dihapus.
        master.deleteRental(idRentalKosong);
        boolean rentalKosongMasih = false;
        for (Rental r : master.listRental()) {
            if (r.getRentalId() == idRentalKosong) {
                rentalKosongMasih = true;
            }
        }
        record(!rentalKosongMasih, "hapus master: rental tanpa truk terhapus");
    }

    /**
     * Uji cadangan database.
     *
     * <p>H2 menolak {@code BACKUP TO} untuk database in-memory ("Database is
     * not persistent", dicek dengan uji coba langsung), jadi cadangan
     * sungguhan tidak bisa diuji dari sini. Yang diuji: konfigurasi non-H2
     * (MySQL/MariaDB) ditolak dengan pesan yang jelas, bukan pura-pura
     * berhasil. Mekanisme {@code BACKUP TO} untuk H2 file sudah diverifikasi
     * dengan uji coba terpisah: file zip tertulis dan isinya tidak kosong.
     */
    private static void ujiCadangkan() throws Exception {
        Db.setConfiguration("org.h2.Driver", "jdbc:mysql://server/db_kaspe", "sa", "");
        try {
            BackupDao.cadangkan();
            record(false, "cadangan: konfigurasi non-H2 ditolak, bukan pura-pura berhasil");
        } catch (SQLException e) {
            System.out.println("   ditolak: " + e.getMessage().split("\n")[0]);
            record(e.getMessage() != null && e.getMessage().contains("MySQL"),
                    "cadangan: konfigurasi non-H2 ditolak dengan pesan yang jelas");
        } finally {
            Db.setConfiguration("org.h2.Driver",
                    "jdbc:h2:mem:daotest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                    "sa", "");
        }
    }

    /**
     * Uji cadangan sungguhan pada database H2 yang tersimpan sebagai file.
     *
     * <p>Seluruh uji lain memakai database in-memory, dan H2 menolak
     * {@code BACKUP TO} di sana, jadi tanpa uji ini tak ada satu pun bukti
     * bahwa cadangan benar-benar tertulis — regresi yang membuat cadangannya
     * kosong akan lolos diam-diam. Database sementara dibuat di folder uji,
     * diisi satu pengiriman, lalu dicadangkan dua kali dalam sedetik yang
     * sama: keduanya harus menghasilkan file zip yang ada, berisi, dan tidak
     * saling menimpa.
     */
    private static void ujiCadangkanFile() throws Exception {
        Path dirUji = Files.createTempDirectory("kaspe-cadangan-uji");
        File dbSementara = dirUji.resolve("db_uji").toFile();
        try {
            Db.setConfiguration("org.h2.Driver",
                    "jdbc:h2:" + dbSementara.getAbsolutePath()
                            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                    "sa", "");
            // Db.get() membuat tabelnya sendiri saat pertama kali tersambung ke URL
            // baru; diisi satu pengiriman supaya cadangannya membawa data sungguhan,
            // bukan struktur kosong.
            Truck truk;
            try (Connection c = Db.get()) {
                truk = new MasterDao().pastikanTruk(c, "BR 3030 CC", "Rental Cadangan");
            }
            simpanPengiriman(new TransactionDao(), truk,
                    LocalDate.of(2026, 6, 2), 4000, 3900, 15, 1100);

            File cadangan1 = BackupDao.cadangkan();
            System.out.println("   ditulis: " + cadangan1.getName() + " (" + cadangan1.length() + " B)");
            record(cadangan1.isFile() && cadangan1.length() > 1024,
                    "cadangan file: zip tertulis dan berisi, bukan kosong");

            File cadangan2 = BackupDao.cadangkan();
            System.out.println("   ditulis: " + cadangan2.getName() + " (" + cadangan2.length() + " B)");
            record(!cadangan2.getName().equals(cadangan1.getName())
                            && cadangan1.isFile() && cadangan2.isFile(),
                    "cadangan file: dua kali dalam sedetik tidak saling menimpa");
        } finally {
            // Kembalikan dulu setelan sebelum bersih-bersih: pemeriksaan lain di kelas
            // ini bergantung pada database in-memory yang tadi dipakai.
            Db.setConfiguration("org.h2.Driver",
                    "jdbc:h2:mem:daotest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                    "sa", "");
            hapusFolder(dirUji.toFile());
        }
    }

    /** Hapus folder beserta seluruh isinya; hanya untuk bersih-bersih berkas uji. */
    private static void hapusFolder(File folder) {
        File[] isi = folder.listFiles();
        if (isi != null) {
            for (File f : isi) {
                if (f.isDirectory()) {
                    hapusFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }

    private static TransactionDetail makeDetail(Truck truck, long fieldWeight, long factoryWeight, int refraction, long price) {
        TransactionDetail d = new TransactionDetail();
        d.setTruckId(truck.getTruckId());
        d.setFieldWeight(new BigDecimal(fieldWeight));
        d.setFactoryWeight(new BigDecimal(factoryWeight));
        d.setRefractionPercent(new BigDecimal(refraction));
        BigDecimal netWeight = Calculator.netWeight(new BigDecimal(factoryWeight), new BigDecimal(refraction));
        d.setNetWeight(netWeight);
        d.setPrice(new BigDecimal(price));
        d.setTotalAmount(Calculator.totalAmount(netWeight, new BigDecimal(price)));
        d.setPaymentDate(LocalDate.of(2026, 1, 19));
        return d;
    }

    private static Truck findTruck(List<Truck> list, String plate) {
        for (Truck t : list) {
            if (t.getPlate().equals(plate)) {
                return t;
            }
        }
        throw new IllegalStateException("truk tidak ditemukan: " + plate);
    }

    private static String plain(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    /** Simpan satu pengiriman lewat DAO, kembalikan id_detail hasil simpannya. */
    private static int simpanPengiriman(TransactionDao dao, Truck truk, LocalDate tanggal,
                                        long lapak, long pabrik, int refraksi, long harga) throws SQLException {
        Transaction t = new Transaction();
        t.setDate(tanggal);
        List<TransactionDetail> ds = new ArrayList<>();
        ds.add(makeDetail(truk, lapak, pabrik, refraksi, harga));
        int idTrx = dao.save(t, ds);
        for (ReportRow b : dao.listDeliveries(null, null)) {
            if (b.getTransactionId() == idTrx) {
                return b.getDetailId();
            }
        }
        throw new IllegalStateException("detail tidak ketemu untuk transaksi " + idTrx);
    }

    /** Baris riwayat dengan id_detail tertentu, atau null kalau tidak ada. */
    private static ReportRow cariBaris(TransactionDao dao, int detailId) throws SQLException {
        for (ReportRow b : dao.listDeliveries(null, null)) {
            if (b.getDetailId() == detailId) {
                return b;
            }
        }
        return null;
    }

    /** Potret isi transaksi_detail (jumlah baris + total uang) untuk membandingkan keadaan
     *  sebelum dan sesudah operasi yang seharusnya tidak mengubah apa pun. */
    private static String potretDetail() throws SQLException {
        try (Connection c = Db.get(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT COUNT(*), COALESCE(SUM(jumlah_uang), 0) FROM transaksi_detail")) {
            rs.next();
            return rs.getInt(1) + "/" + rs.getBigDecimal(2).stripTrailingZeros().toPlainString();
        }
    }

    /** Gabung id_detail baris riwayat menjadi "a,b,c" menurut urutan datanya. */
    private static String gabungId(List<ReportRow> baris) {
        StringBuilder sb = new StringBuilder();
        for (ReportRow b : baris) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(b.getDetailId());
        }
        return sb.toString();
    }

    /** Potret hasil saringan langsung dari SQL (jumlah baris + SUM uang) memakai
     *  saringan yang sama seperti listDeliveries - untuk membuktikan daftar yang
     *  diterima pemanggil utuh dan cocok dengan agregat database. */
    private static String potretSaringan(LocalDate from, LocalDate to, String rental, String plat)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*), COALESCE(SUM(d.jumlah_uang), 0) FROM transaksi_detail d "
                        + "JOIN transaksi t ON t.id_transaksi = d.id_transaksi "
                        + "LEFT JOIN truk tr ON tr.id_truk = d.id_truk "
                        + "LEFT JOIN rental r ON r.id_rental = tr.id_rental WHERE 1=1 ");
        List<Object> param = new ArrayList<>();
        if (from != null) {
            sql.append("AND t.tanggal >= ? ");
            param.add(Date.valueOf(from));
        }
        if (to != null) {
            sql.append("AND t.tanggal <= ? ");
            param.add(Date.valueOf(to));
        }
        if (rental != null && !rental.trim().isEmpty()) {
            sql.append("AND r.nama_rental = ? ");
            param.add(rental.trim());
        }
        if (plat != null && !plat.trim().isEmpty()) {
            sql.append("AND UPPER(tr.plat) LIKE ? ");
            param.add("%" + Truck.normalizePlate(plat).toUpperCase() + "%");
        }
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < param.size(); i++) {
                ps.setObject(i + 1, param.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) + "/" + rs.getBigDecimal(2).stripTrailingZeros().toPlainString();
            }
        }
    }

    /** Jumlah header transaksi yang tidak lagi punya detail. */
    private static int hitungHeaderKosong() throws SQLException {
        try (Connection c = Db.get(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT COUNT(*) FROM transaksi WHERE id_transaksi NOT IN"
                             + " (SELECT id_transaksi FROM transaksi_detail)")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void createSchema() throws Exception {
        try (Connection c = Db.get(); Statement st = c.createStatement()) {
            for (String sql : Schema.readStatements()) {
                st.execute(sql);
            }
        }
    }

    private static void record(boolean ok, String name) {
        if (ok) {
            passed++;
            System.out.println("   OK   " + name);
        } else {
            failed++;
            System.out.println("   GAGAL " + name);
        }
    }
}
