package kaspe.test;

import kaspe.Db;
import kaspe.Schema;
import kaspe.Calculator;
import kaspe.dao.MasterDao;
import kaspe.dao.TransactionDao;
import kaspe.model.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
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
        // memberi tahu - jadi jumlah truknya yang diperiksa, bukan cuma idnya.
        Truck eja1 = master.truckFor("  be  9120 xy ", idRental);
        Truck eja2 = master.truckFor("BE 9120 XY", idRental);
        Truck eja3 = master.truckFor("be 9120 xy", null);
        System.out.println("   ejaan 1 -> '" + eja1.getPlate() + "' id=" + eja1.getTruckId()
                + ", ejaan 2 -> id=" + eja2.getTruckId() + ", ejaan 3 -> id=" + eja3.getTruckId());
        record("BE 9120 XY".equals(eja1.getPlate()), "ejaan plat diseragamkan");
        record(eja1.getTruckId() == eja2.getTruckId() && eja2.getTruckId() == eja3.getTruckId(),
                "tiga ejaan menunjuk satu truk");
        record(master.listTrucks().size() == 3, "jumlah truk tetap 3, tidak bertambah");

        // Truk yang sudah ada tidak boleh berpindah pemilik hanya karena pilihan rental
        // di layar transaksi berbeda - daftar truk yang berlaku sebagai acuan.
        Truck d = master.truckFor("BE 9120 XY", null);
        record(d.getRentalId() != null && d.getRentalId() == idRental,
                "rental truk lama tidak tertimpa");

        record(master.truckFor("   ", idRental) == null, "plat kosong tidak membuat truk");
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
        Truck lama = master.truckFor("BE 5555 XX", null);
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

        System.out.println("6. Hapus transaksi (cascade ke detail) ...");
        transactionDao.deleteTransaction(transactionId);
        record(transactionDao.listReport(null, null).isEmpty(), "hapus transaksi ikut hapus detail");

        System.out.println("\n=== HASIL: " + passed + " lulus, " + failed + " gagal ===");
        if (failed > 0) {
            System.exit(1);
        }
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
