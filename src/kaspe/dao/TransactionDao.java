package kaspe.dao;

import kaspe.Calculator;
import kaspe.Db;
import kaspe.model.ReportRow;
import kaspe.model.Transaction;
import kaspe.model.TransactionDetail;
import kaspe.model.Truck;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Operasi database untuk transaksi dan laporan. */
public class TransactionDao {

    /**
     * Simpan header + semua baris detail dalam satu transaksi database.
     * Kalau ada satu baris gagal, semuanya dibatalkan (rollback).
     *
     * <p>Truk dan rental yang belum tercatat dibuat di dalam transaksi yang sama,
     * bukan dari koneksi terpisah, supaya kalau simpan gagal keduanya ikut batal
     * dan tidak tertinggal sebagai data hantu.
     */
    public int save(Transaction t, List<TransactionDetail> detail) throws SQLException {
        Connection c = null;
        // Hanya kalau transaksinya sudah ditutup rapi (commit atau rollback berhasil)
        // autocommit boleh dinyalakan lagi. Kalau rollback gagal, transaksinya masih
        // terbuka — setAutoCommit(true) di titik itu justru meng-commit sisa
        // pekerjaannya, dan akan lahir header tanpa detail.
        boolean selesai = false;
        try {
            c = Db.get();
            c.setAutoCommit(false);

            int transactionId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO transaksi (tanggal) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setDate(1, Date.valueOf(t.getDate()));
                ps.executeUpdate();
                try (ResultSet k = ps.getGeneratedKeys()) {
                    k.next();
                    transactionId = k.getInt(1);
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO transaksi_detail (id_transaksi,id_truk,bobot_lapak,bobot_pabrik,refraksi_persen," +
                    "berat_bersih,tanggal_lunas,harga,jumlah_uang) VALUES (?,?,?,?,?,?,?,?,?)")) {
                MasterDao masterDao = new MasterDao();
                for (TransactionDetail d : detail) {
                    ps.setInt(1, transactionId);
                    Integer truckId = d.getTruckId();
                    if (truckId == null || truckId == 0) {
                        // Truk (dan rentalnya) yang belum tercatat dibuat di dalam
                        // transaksi ini, bukan dari koneksi terpisah — supaya kalau
                        // simpan gagal, keduanya ikut batal dan tidak jadi data hantu.
                        Truck truk = masterDao.pastikanTruk(c, d.getPlate(), d.getRentalName());
                        truckId = truk == null ? null : truk.getTruckId();
                    }
                    if (truckId == null) {
                        ps.setNull(2, Types.INTEGER);
                    } else {
                        ps.setInt(2, truckId);
                    }
                    ps.setBigDecimal(3, d.getFieldWeight());
                    ps.setBigDecimal(4, d.getFactoryWeight());
                    ps.setBigDecimal(5, d.getRefractionPercent());
                    ps.setBigDecimal(6, d.getNetWeight());
                    if (d.getPaymentDate() == null) {
                        ps.setNull(7, Types.DATE);
                    } else {
                        ps.setDate(7, Date.valueOf(d.getPaymentDate()));
                    }
                    ps.setBigDecimal(8, d.getPrice());
                    ps.setBigDecimal(9, d.getTotalAmount());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            c.commit();
            selesai = true;
            return transactionId;
        } catch (SQLException | RuntimeException e) {
            if (c != null) {
                try {
                    c.rollback();
                    selesai = true;
                } catch (SQLException rb) {
                    // Kegagalan rollback tidak boleh menenggelamkan galat aslinya.
                    e.addSuppressed(rb);
                }
            }
            throw e;
        } finally {
            if (c != null) {
                try {
                    if (selesai) {
                        c.setAutoCommit(true);
                    }
                } finally {
                    c.close();
                }
            }
        }
    }

    /**
     * Ubah satu catatan pengiriman (detail) beserta tanggal headernya dalam
     * satu transaksi database. Kalau ada langkah yang gagal, semuanya dibatalkan
     * (rollback), termasuk truk/rental yang mungkin baru dibuat.
     *
     * <p>Parameter {@code rental} hanya dipakai kalau platnya belum tercatat,
     * karena rental adalah milik truk, bukan milik catatan pengiriman.
     */
    public void updateDelivery(int detailId, LocalDate tanggal, String plat, String rental,
                               BigDecimal bobotLapak, BigDecimal bobotPabrik, BigDecimal refraksi,
                               LocalDate tanggalLunas, BigDecimal harga) throws SQLException {
        Connection c = null;
        // Hanya kalau transaksinya sudah ditutup rapi (commit atau rollback berhasil)
        // autocommit boleh dinyalakan lagi. Kalau rollback gagal, transaksinya masih
        // terbuka — setAutoCommit(true) di titik itu justru meng-commit sisa
        // pekerjaannya, dan akan lahir header tanpa detail.
        boolean selesai = false;
        try {
            c = Db.get();
            c.setAutoCommit(false);

            // Truk (dan rentalnya) yang belum tercatat dibuat di dalam
            // transaksi ini, bukan dari koneksi terpisah — supaya kalau ubah
            // gagal, keduanya ikut batal dan tidak jadi data hantu.
            Truck truk = new MasterDao().pastikanTruk(c, plat, rental);
            Integer truckId = truk == null ? null : truk.getTruckId();
            BigDecimal netWeight = Calculator.netWeight(bobotPabrik, refraksi);
            BigDecimal amount = Calculator.totalAmount(netWeight, harga);

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE transaksi_detail SET id_truk=?, bobot_lapak=?, bobot_pabrik=?, refraksi_persen=?, " +
                    "berat_bersih=?, tanggal_lunas=?, harga=?, jumlah_uang=? WHERE id_detail=?")) {
                if (truckId == null) {
                    ps.setNull(1, Types.INTEGER);
                } else {
                    ps.setInt(1, truckId);
                }
                ps.setBigDecimal(2, bobotLapak);
                ps.setBigDecimal(3, bobotPabrik);
                ps.setBigDecimal(4, refraksi);
                ps.setBigDecimal(5, netWeight);
                if (tanggalLunas == null) {
                    ps.setNull(6, Types.DATE);
                } else {
                    ps.setDate(6, Date.valueOf(tanggalLunas));
                }
                ps.setBigDecimal(7, harga);
                ps.setBigDecimal(8, amount);
                ps.setInt(9, detailId);
                if (ps.executeUpdate() == 0) {
                    throw new SQLException("Catatan pengiriman dengan id " + detailId + " tidak ditemukan");
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE transaksi SET tanggal=? WHERE id_transaksi=" +
                    "(SELECT id_transaksi FROM transaksi_detail WHERE id_detail=?)")) {
                ps.setDate(1, Date.valueOf(tanggal));
                ps.setInt(2, detailId);
                ps.executeUpdate();
            }

            c.commit();
            selesai = true;
        } catch (SQLException | RuntimeException e) {
            if (c != null) {
                try {
                    c.rollback();
                    selesai = true;
                } catch (SQLException rb) {
                    // Kegagalan rollback tidak boleh menenggelamkan galat aslinya.
                    e.addSuppressed(rb);
                }
            }
            throw e;
        } finally {
            if (c != null) {
                try {
                    if (selesai) {
                        c.setAutoCommit(true);
                    }
                } finally {
                    c.close();
                }
            }
        }
    }

    /**
     * Hapus beberapa catatan pengiriman sekaligus dalam satu transaksi database.
     * Kalau satu id gagal, tidak ada yang terhapus (rollback semua).
     * Header transaksi yang jadi tanpa detail ikut dibersihkan.
     */
    public void deleteDeliveries(List<Integer> detailIds) throws SQLException {
        if (detailIds == null || detailIds.isEmpty()) {
            return;
        }
        Connection c = null;
        boolean selesai = false;
        try {
            c = Db.get();
            c.setAutoCommit(false);

            StringBuilder sql = new StringBuilder("DELETE FROM transaksi_detail WHERE id_detail IN (");
            for (int i = 0; i < detailIds.size(); i++) {
                sql.append(i == 0 ? "?" : ",?");
            }
            sql.append(")");
            int jumlahDiminta = new HashSet<>(detailIds).size();
            int jumlahTerhapus;
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < detailIds.size(); i++) {
                    ps.setInt(i + 1, detailIds.get(i));
                }
                jumlahTerhapus = ps.executeUpdate();
            }
            // DELETE ... IN tidak menjerit kalau ada id yang tidak ada, barisnya
            // cuma dilewati — jumlah baris yang benar-benar terhapus harus
            // cocok dengan jumlah id berbeda yang diminta, kalau tidak
            // penghapusan tidak utuh dan semuanya dibatalkan.
            if (jumlahTerhapus != jumlahDiminta) {
                throw new SQLException("Catatan pengiriman tidak ditemukan: diminta "
                        + jumlahDiminta + ", terhapus " + jumlahTerhapus);
            }

            // Subquery membaca transaksi_detail (bukan transaksi), jadi aman di MySQL.
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM transaksi WHERE id_transaksi NOT IN (SELECT id_transaksi FROM transaksi_detail)")) {
                ps.executeUpdate();
            }

            c.commit();
            selesai = true;
        } catch (SQLException | RuntimeException e) {
            if (c != null) {
                try {
                    c.rollback();
                    selesai = true;
                } catch (SQLException rb) {
                    // Kegagalan rollback tidak boleh menenggelamkan galat aslinya.
                    e.addSuppressed(rb);
                }
            }
            throw e;
        } finally {
            if (c != null) {
                try {
                    if (selesai) {
                        c.setAutoCommit(true);
                    }
                } finally {
                    c.close();
                }
            }
        }
    }

    /** Tanggal transaksi paling awal, atau null kalau belum ada data sama sekali. */
    public LocalDate earliestDate() throws SQLException {
        try (Connection c = Db.get(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT MIN(tanggal) FROM transaksi")) {
            if (rs.next()) {
                Date d = rs.getDate(1);
                return d == null ? null : d.toLocalDate();
            }
        }
        return null;
    }

    /** Daftar baris laporan dengan filter tanggal (null = semua). */
    public List<ReportRow> listReport(LocalDate from, LocalDate to) throws SQLException {
        List<ReportRow> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT t.id_transaksi, t.tanggal, " +
                "d.id_detail, tr.plat, r.nama_rental, d.bobot_lapak, d.bobot_pabrik, d.refraksi_persen, " +
                "d.berat_bersih, d.tanggal_lunas, d.harga, d.jumlah_uang " +
                "FROM transaksi_detail d " +
                "JOIN transaksi t ON t.id_transaksi = d.id_transaksi " +
                "LEFT JOIN truk tr ON tr.id_truk = d.id_truk " +
                "LEFT JOIN rental r ON r.id_rental = tr.id_rental WHERE 1=1 ");
        List<Object> param = new ArrayList<>();
        if (from != null) {
            sql.append("AND t.tanggal >= ? ");
            param.add(Date.valueOf(from));
        }
        if (to != null) {
            sql.append("AND t.tanggal <= ? ");
            param.add(Date.valueOf(to));
        }
        sql.append("ORDER BY t.tanggal, t.id_transaksi, d.id_detail");

        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < param.size(); i++) {
                ps.setObject(i + 1, param.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ReportRow b = new ReportRow();
                    b.setTransactionId(rs.getInt("id_transaksi"));
                    b.setDetailId(rs.getInt("id_detail"));
                    Date trxDate = rs.getDate("tanggal");
                    b.setDate(trxDate == null ? null : trxDate.toLocalDate());
                    b.setPlate(rs.getString("plat"));
                    b.setRentalName(rs.getString("nama_rental"));
                    b.setFieldWeight(rs.getBigDecimal("bobot_lapak"));
                    b.setFactoryWeight(rs.getBigDecimal("bobot_pabrik"));
                    b.setRefractionPercent(rs.getBigDecimal("refraksi_persen"));
                    b.setNetWeight(rs.getBigDecimal("berat_bersih"));
                    Date paidDate = rs.getDate("tanggal_lunas");
                    b.setPaymentDate(paidDate == null ? null : paidDate.toLocalDate());
                    b.setPrice(rs.getBigDecimal("harga"));
                    b.setTotalAmount(rs.getBigDecimal("jumlah_uang"));
                    result.add(b);
                }
            }
        }
        return result;
    }

    /**
     * Daftar semua catatan pengiriman (satu baris = satu pengiriman) untuk
     * riwayat transaksi, diurut dari yang paling baru. Null berarti tanpa batas.
     */
    public List<ReportRow> listDeliveries(LocalDate from, LocalDate to) throws SQLException {
        List<ReportRow> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT t.id_transaksi, t.tanggal, " +
                "d.id_detail, tr.plat, r.nama_rental, d.bobot_lapak, d.bobot_pabrik, d.refraksi_persen, " +
                "d.berat_bersih, d.tanggal_lunas, d.harga, d.jumlah_uang " +
                "FROM transaksi_detail d " +
                "JOIN transaksi t ON t.id_transaksi = d.id_transaksi " +
                "LEFT JOIN truk tr ON tr.id_truk = d.id_truk " +
                "LEFT JOIN rental r ON r.id_rental = tr.id_rental WHERE 1=1 ");
        List<Object> param = new ArrayList<>();
        if (from != null) {
            sql.append("AND t.tanggal >= ? ");
            param.add(Date.valueOf(from));
        }
        if (to != null) {
            sql.append("AND t.tanggal <= ? ");
            param.add(Date.valueOf(to));
        }
        sql.append("ORDER BY t.tanggal DESC, t.id_transaksi DESC, d.id_detail DESC");

        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < param.size(); i++) {
                ps.setObject(i + 1, param.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ReportRow b = new ReportRow();
                    b.setTransactionId(rs.getInt("id_transaksi"));
                    b.setDetailId(rs.getInt("id_detail"));
                    Date trxDate = rs.getDate("tanggal");
                    b.setDate(trxDate == null ? null : trxDate.toLocalDate());
                    b.setPlate(rs.getString("plat"));
                    b.setRentalName(rs.getString("nama_rental"));
                    b.setFieldWeight(rs.getBigDecimal("bobot_lapak"));
                    b.setFactoryWeight(rs.getBigDecimal("bobot_pabrik"));
                    b.setRefractionPercent(rs.getBigDecimal("refraksi_persen"));
                    b.setNetWeight(rs.getBigDecimal("berat_bersih"));
                    Date paidDate = rs.getDate("tanggal_lunas");
                    b.setPaymentDate(paidDate == null ? null : paidDate.toLocalDate());
                    b.setPrice(rs.getBigDecimal("harga"));
                    b.setTotalAmount(rs.getBigDecimal("jumlah_uang"));
                    result.add(b);
                }
            }
        }
        return result;
    }

    public BigDecimal totalAmount(LocalDate from, LocalDate to) throws SQLException {
        List<ReportRow> row = listReport(from, to);
        BigDecimal total = BigDecimal.ZERO;
        for (ReportRow b : row) {
            if (b.getTotalAmount() != null) {
                total = total.add(b.getTotalAmount());
            }
        }
        return total;
    }

    public BigDecimal totalNetWeight(LocalDate from, LocalDate to) throws SQLException {
        List<ReportRow> row = listReport(from, to);
        BigDecimal total = BigDecimal.ZERO;
        for (ReportRow b : row) {
            if (b.getNetWeight() != null) {
                total = total.add(b.getNetWeight());
            }
        }
        return total;
    }

    /** Rekap total uang per rental. */
    public Map<String, BigDecimal> summaryPerRental(LocalDate from, LocalDate to) throws SQLException {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (ReportRow b : listReport(from, to)) {
            String key = b.getRentalName() == null ? "(tanpa rental)" : b.getRentalName();
            BigDecimal old = result.getOrDefault(key, BigDecimal.ZERO);
            result.put(key, old.add(b.getTotalAmount() == null ? BigDecimal.ZERO : b.getTotalAmount()));
        }
        return result;
    }

    public void deleteTransaction(int transactionId) throws SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM transaksi WHERE id_transaksi=?")) {
            ps.setInt(1, transactionId);
            ps.executeUpdate();
        }
    }

}
