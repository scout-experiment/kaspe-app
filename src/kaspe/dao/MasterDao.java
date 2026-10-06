package kaspe.dao;

import kaspe.Db;
import kaspe.model.Rental;
import kaspe.model.Truck;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Operasi database untuk data master: rental dan truk. */
public class MasterDao {

    // ================= RENTAL =================

    public List<Rental> listRental() throws SQLException {
        List<Rental> result = new ArrayList<>();
        try (Connection c = Db.get(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     // Kolomnya ditulis satu per satu, tidak memakai SELECT *.
                     // Database lama masih menyimpan kolom no_hp dan keterangan yang sudah
                     // dibuang dari skema, dan SELECT * akan ikut membacanya. Daftar kolom
                     // yang eksplisit membuat perintah ini sama saja di database baru
                     // maupun database lama.
                     "SELECT id_rental, nama_rental FROM rental ORDER BY nama_rental")) {
            while (rs.next()) {
                Rental r = new Rental();
                r.setRentalId(rs.getInt("id_rental"));
                r.setRentalName(rs.getString("nama_rental"));
                result.add(r);
            }
        }
        return result;
    }

    public void saveRental(Rental r) throws SQLException {
        if (r.getRentalId() == 0) {
            // Rental baru diperiksa dulu apakah namanya sudah ada. Nama rental punya
            // batasan unik di database, tetapi batasan itu membedakan besar-kecil huruf —
            // jadi "cv mitra tani" tetap lolos masuk sebagai rental kedua di samping
            // "CV Mitra Tani", dan total uang per pemilik ikut terpecah. Pemeriksaan ini
            // yang menutup celah itu. Yang tersimpan lebih dulu tetap dipakai apa adanya,
            // termasuk ejaan hurufnya.
            try (Connection c = Db.get()) {
                if (cariIdRental(c, Rental.matchKey(r.getRentalName())) != null) {
                    // Bukan return diam: tombol yang "tidak jalan" tanpa pesan membuat
                    // operator mengira aplikasinya rusak, dan rental barunya terasa
                    // hilang begitu saja.
                    throw new IllegalArgumentException(
                            "Nama rental \"" + r.getRentalName() + "\" sudah dipakai.");
                }
            }
            try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO rental (nama_rental) VALUES (?)")) {
                ps.setString(1, r.getRentalName());
                ps.executeUpdate();
            }
        } else {
            // Penggantian nama juga diperiksa. Tanpa ini, mengganti nama satu rental
            // menjadi ejaan lain yang sudah dipakai rental lain tetap lolos — batasan unik
            // di database membedakan besar-kecil huruf, jadi "cv mitra tani" dan
            // "CV Mitra Tani" bisa hidup berdampingan. Akibatnya lebih buruk daripada
            // sekadar dua baris: yang dicari selalu rental dengan id terkecil, sehingga
            // rental hasil penggantian tidak pernah terpakai, sementara rekap memecah
            // satu pemilik menjadi dua.
            try (Connection c = Db.get()) {
                Integer lain = cariIdRental(c, Rental.matchKey(r.getRentalName()));
                if (lain != null && lain != r.getRentalId()) {
                    throw new IllegalArgumentException(
                            "Nama rental \"" + r.getRentalName() + "\" sudah dipakai.");
                }
            }
            try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                    "UPDATE rental SET nama_rental=? WHERE id_rental=?")) {
                ps.setString(1, r.getRentalName());
                ps.setInt(2, r.getRentalId());
                ps.executeUpdate();
            }
        }
    }

    /**
     * Id rental dengan nama tertentu, atau null kalau belum ada.
     *
     * <p>Pencocokannya dilakukan di sini, bukan lewat {@code WHERE nama_rental=?}.
     * Perintah itu hanya cocok kalau ejaannya sama persis, sedangkan nama yang diketik
     * operator bisa berbeda besar-kecil hurufnya. Perbandingan yang tidak membedakan
     * besar-kecil huruf juga tidak dipakai di SQL, karena hanya sebagian merek database
     * yang punya — sedangkan aplikasi ini harus jalan di H2 maupun MySQL.
     */
    private Integer cariIdRental(Connection c, String kunci) throws SQLException {
        Integer ketemu = null;
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id_rental, nama_rental FROM rental")) {
            while (rs.next()) {
                if (!kunci.equals(Rental.matchKey(rs.getString("nama_rental")))) {
                    continue;
                }
                int id = rs.getInt("id_rental");
                if (ketemu == null || id < ketemu) {
                    ketemu = id;
                }
            }
        }
        return ketemu;
    }

    /**
     * Hapus rental.
     *
     * <p>Ditolak kalau masih ada truk miliknya. Kunci tamunya {@code ON DELETE SET NULL},
     * jadi DELETE tidak gagal — diam-diam mengosongkan id_rental di setiap truknya,
     * dan listReport membaca pemiliknya lewat LEFT JOIN, sehingga rekap per pemilik
     * untuk seluruh riwayat truk-truk itu hilang tanpa jalan kembali. Karena
     * pengiriman menempel di truk, rental tanpa truk tidak mungkin punya riwayat,
     * jadi hanya itu yang boleh dihapus.
     */
    public void deleteRental(int id) throws SQLException {
        String penolakan = rentalDeleteRefusal(id);
        if (penolakan != null) {
            throw new IllegalStateException(penolakan);
        }
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement("DELETE FROM rental WHERE id_rental=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Alasan rental ini tidak boleh dihapus, atau null kalau boleh.
     *
     * <p>Terpisah dari {@link #deleteRental} supaya layar bisa MENANYAKAN alasannya
     * tanpa harus memanggil penghapusannya. Memanggil fungsi penghapus hanya untuk
     * memanen pesannya berbahaya: kalau penjaganya suatu saat dilonggarkan atau
     * dipindahkan, cabang "menampilkan alasan" itu berubah menjadi cabang yang
     * benar-benar menghapus - di jalur yang justru dibuat untuk melindungi.
     */
    public String rentalDeleteRefusal(int id) throws SQLException {
        int truk = countTrucksForRental(id);
        if (truk == 0) {
            return null;
        }
        return "Rental ini masih memiliki " + truk
                + " truk. Menghapusnya akan melepaskan semua truknya dari pemiliknya, "
                + "sehingga rekap per pemilik untuk seluruh riwayatnya hilang dan tidak "
                + "bisa dikembalikan. Pindahkan dulu truknya ke pemilik lain lewat menu "
                + "Data Master > Pindah Pemilik, atau hapus truknya satu per satu kalau "
                + "memang salah catat dan tidak punya catatan pengiriman.";
    }

    /** Jumlah truk milik rental ini — dipakai untuk menolak penghapusan rental
     *  yang masih memiliki truk. */
    public int countTrucksForRental(int rentalId) throws SQLException {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM truk WHERE id_rental = ?")) {
            ps.setInt(1, rentalId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ================= TRUK =================

    public List<Truck> listTrucks() throws SQLException {
        List<Truck> result = new ArrayList<>();
        String sql = "SELECT t.*, r.nama_rental FROM truk t LEFT JOIN rental r ON r.id_rental = t.id_rental ORDER BY t.plat";
        try (Connection c = Db.get(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Truck t = new Truck();
                t.setTruckId(rs.getInt("id_truk"));
                t.setPlate(rs.getString("plat"));
                int idR = rs.getInt("id_rental");
                t.setRentalId(rs.wasNull() ? null : idR);
                t.setRentalName(rs.getString("nama_rental"));
                result.add(t);
            }
        }
        return result;
    }

    public void saveTruck(Truck t) throws SQLException {
        // Pemeriksaan plat kembar dikerjakan di Java lewat cariIdTruk, bukan
        // diserahkan ke batasan unik database: batasan itu membedakan ejaan dan
        // berperilaku berbeda antara H2 dan MySQL, sedangkan cariIdTruk juga
        // menemukan plat dengan ejaan lama.
        if (t.getTruckId() == 0) {
            try (Connection c = Db.get()) {
                if (cariIdTruk(c, t.getPlate()) != null) {
                    throw new IllegalArgumentException(
                            "Plat \"" + t.getPlate() + "\" sudah terdaftar.");
                }
            }
            try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO truk (plat,id_rental) VALUES (?,?)")) {
                ps.setString(1, t.getPlate());
                if (t.getRentalId() == null) {
                    ps.setNull(2, Types.INTEGER);
                } else {
                    ps.setInt(2, t.getRentalId());
                }
                ps.executeUpdate();
            }
        } else {
            try (Connection c = Db.get()) {
                Integer lain = cariIdTruk(c, t.getPlate());
                if (lain != null && lain != t.getTruckId()) {
                    throw new IllegalArgumentException(
                            "Plat \"" + t.getPlate() + "\" sudah terdaftar.");
                }
            }
            try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                    "UPDATE truk SET plat=?, id_rental=? WHERE id_truk=?")) {
                ps.setString(1, t.getPlate());
                if (t.getRentalId() == null) {
                    ps.setNull(2, Types.INTEGER);
                } else {
                    ps.setInt(2, t.getRentalId());
                }
                ps.setInt(3, t.getTruckId());
                ps.executeUpdate();
            }
        }
    }

    /**
     * Hapus truk.
     *
     * <p>Ditolak kalau truk ini sudah dipakai catatan pengiriman mana pun. Kunci tamunya
     * {@code ON DELETE SET NULL}, jadi DELETE tidak gagal — diam-diam mengosongkan
     * id_truk di semua baris riwayatnya, dan listReport membaca plat lewat LEFT JOIN,
     * sehingga laporan lama (termasuk kertas yang sudah dicetak) kehilangan platnya
     * tanpa jalan kembali. Alasan sah melepas pemilik — truk berganti tangan — sudah
     * dilayani tombol Pindah Pemilik di dialog Data Master (menu bilah samping).
     * Truk yang salah catat tetap bisa dibersihkan:
     * hapus dulu catatan pengirimannya di layar transaksi, baru truknya.
     */
    public void deleteTruck(int id) throws SQLException {
        String penolakan = truckDeleteRefusal(id);
        if (penolakan != null) {
            throw new IllegalStateException(penolakan);
        }
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement("DELETE FROM truk WHERE id_truk=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Alasan truk ini tidak boleh dihapus, atau null kalau boleh. Terpisah dari
     * {@link #deleteTruck} dengan alasan yang sama seperti pada rental.
     */
    public String truckDeleteRefusal(int id) throws SQLException {
        int dipakai = countDeliveriesForTruck(id);
        if (dipakai == 0) {
            return null;
        }
        return "Truk ini dipakai oleh " + dipakai
                + " catatan pengiriman. Menghapusnya akan menghilangkan platnya dari "
                + "catatan yang sudah ada, termasuk laporan yang sudah dicetak. "
                + "Kalau pemiliknya berganti, pakai menu Data Master > Pindah Pemilik. "
                + "Kalau truknya memang salah catat, hapus dulu catatan pengirimannya.";
    }

    /** Jumlah catatan pengiriman yang memakai truk ini — dipakai untuk menolak
     *  penghapusan truk yang masih punya riwayat. */
    public int countDeliveriesForTruck(int truckId) throws SQLException {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM transaksi_detail WHERE id_truk = ?")) {
            ps.setInt(1, truckId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Pastikan truk dengan plat tertentu sudah tercatat, memakai koneksi pemanggil.
     *
     * <p>Dipakai {@link TransactionDao#save} supaya truk dan rental yang belum ada
     * dibuat di dalam transaksi yang sama dengan detailnya: kalau transaksinya batal,
     * keduanya ikut batal dan tidak tertinggal sebagai data hantu.
     *
     * <p>Truk dicari lewat bentuk seragam platnya, jadi ejaan lama pun ketemu. Kalau
     * tidak ada, rentalnya dicari (dan dibuat kalau perlu) menurut {@code namaRental},
     * lalu truk baru disimpan dengan id_rental itu — atau tanpa pemilik sama sekali
     * kalau {@code namaRental} kosong. Truk yang sudah ada dipakai apa adanya:
     * pilihan rental di layar transaksi tidak boleh memindahkan pemiliknya diam-diam.
     *
     * @param c          koneksi (dan transaksi) milik pemanggil; tidak dibuka sendiri
     * @param plat       plat yang diketik; diseragamkan dulu bentuknya
     * @param namaRental nama rental untuk truk baru; null/kosong berarti tanpa pemilik
     * @return truk yang tersimpan beserta nama rentalnya, atau null kalau platnya kosong
     */
    public Truck pastikanTruk(Connection c, String plat, String namaRental) throws SQLException {
        String normalized = Truck.normalizePlate(plat);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        Truck ada = bacaTruk(c, normalized);
        if (ada != null) {
            return ada;
        }
        Integer idRental = null;
        String rapi = Rental.normalizeName(namaRental);
        if (rapi != null && !rapi.isEmpty()) {
            idRental = cariIdRental(c, Rental.matchKey(rapi));
            if (idRental == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO rental (nama_rental) VALUES (?)")) {
                    ps.setString(1, rapi);
                    ps.executeUpdate();
                }
                idRental = cariIdRental(c, Rental.matchKey(rapi));
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO truk (plat,id_rental) VALUES (?,?)")) {
            ps.setString(1, normalized);
            if (idRental == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, idRental);
            }
            ps.executeUpdate();
        }
        return bacaTruk(c, normalized);
    }

    /** Id truk dengan plat tertentu, atau null kalau belum ada. */
    private Integer cariIdTruk(Connection c, String plate) throws SQLException {
        // Pencocokannya dilakukan di sini, bukan lewat "WHERE plat=?".
        //
        // Alasannya: penyeragaman ejaan plat baru berlaku untuk data yang ditulis sejak
        // sekarang. Database yang sudah dipakai bisa menyimpan plat dengan ejaan lama
        // ("be 8234 hd"), dan pencocokan persis tidak akan menemukannya - akibatnya truk
        // yang sama dibuat ulang sebagai baris baru, lalu laporan memecah satu truk
        // menjadi dua pemilik. Karena itu ejaan yang tersimpan diseragamkan lebih dulu
        // di sini, baru dibandingkan.
        //
        // Seluruh truk dibaca sekaligus, bukan dicocokkan di dalam perintah SQL: cara
        // merapikan spasi berturut-turut berbeda-beda antar merek database, sedangkan
        // penyeragaman di Java pasti sama di H2 maupun MySQL. Jumlah truknya sedikit,
        // jadi membaca semuanya tidak memberatkan.
        Integer ketemu = null;
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id_truk, plat FROM truk")) {
            while (rs.next()) {
                if (plate.equals(Truck.normalizePlate(rs.getString("plat")))) {
                    int id = rs.getInt("id_truk");
                    // Kalau ada lebih dari satu baris yang sama, yang dipakai yang paling
                    // awal - supaya pilihannya tidak berubah-ubah antar pemanggilan.
                    if (ketemu == null || id < ketemu) {
                        ketemu = id;
                    }
                }
            }
        }
        return ketemu;
    }

    /**
     * Simpan TRUK BARU beserta pemiliknya dalam satu transaksi.
     *
     * <p>Kalau platnya sudah terdaftar, atau ada apa pun yang gagal di tengah jalan, seluruh
     * pekerjaan dibatalkan — termasuk pemilik yang mungkin baru dibuat. Itu intinya: membuat
     * pemilik dulu lalu gagal menyimpan truknya meninggalkan PEMILIK TANPA TRUK yang sudah
     * ter-commit, dan ia langsung muncul di setiap kotak pilihan pemilik sementara rekap
     * uangnya terpecah. Pola yang sama dipakai
     * {@link #pastikanTruk(Connection, String, String)} dari jalur transaksi.
     *
     * <p>Bedanya dengan {@code pastikanTruk}: yang ini MENOLAK plat yang sudah ada, bukan
     * memakai ulang truknya. Di dialog, menambah plat yang sudah terdaftar adalah salah
     * ketik yang harus terdengar — bukan permintaan diam-diam untuk memakai truk yang ada.
     *
     * @return truk yang baru tersimpan
     * @throws IllegalArgumentException kalau platnya sudah terdaftar
     */
    public Truck simpanTrukBaru(String plat, String namaRental) throws SQLException {
        String normalized = Truck.normalizePlate(plat);
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException("plat nomor wajib diisi");
        }
        String rapi = Rental.normalizeName(namaRental);
        if (rapi == null || rapi.isEmpty()) {
            throw new IllegalArgumentException("pemilik wajib dipilih");
        }
        Connection c = null;
        boolean selesai = false;
        try {
            c = Db.get();
            c.setAutoCommit(false);

            // Plat kembar diperiksa DI DALAM transaksi. Memeriksanya di memori dulu
            // (seperti versi pertama) tidak menutup apa pun: aplikasi ini boleh dipakai
            // beberapa komputer lewat MySQL, dan komputer lain bisa menambah plat yang
            // sama di antara pemeriksaan dan penyimpanan.
            if (cariIdTruk(c, normalized) != null) {
                throw new IllegalArgumentException(
                        "Plat \"" + normalized + "\" sudah terdaftar.");
            }

            Integer idRental = cariIdRental(c, Rental.matchKey(rapi));
            if (idRental == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO rental (nama_rental) VALUES (?)")) {
                    ps.setString(1, rapi);
                    ps.executeUpdate();
                }
                idRental = cariIdRental(c, Rental.matchKey(rapi));
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO truk (plat,id_rental) VALUES (?,?)")) {
                ps.setString(1, normalized);
                ps.setInt(2, idRental);
                ps.executeUpdate();
            }

            c.commit();
            selesai = true;
            return bacaTruk(c, normalized);
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

    /** Satu truk beserta nama rentalnya. */
    private Truck bacaTruk(Connection c, String plate) throws SQLException {
        // Truknya dibaca lewat cara yang sama dengan pencarian di atas, supaya yang
        // ditemukan dan yang dibaca balik selalu truk yang sama.
        Integer id = cariIdTruk(c, plate);
        if (id == null) {
            return null;
        }
        String sql = "SELECT t.*, r.nama_rental FROM truk t "
                + "LEFT JOIN rental r ON r.id_rental = t.id_rental WHERE t.id_truk=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Truck t = new Truck();
                t.setTruckId(rs.getInt("id_truk"));
                t.setPlate(rs.getString("plat"));
                int idR = rs.getInt("id_rental");
                t.setRentalId(rs.wasNull() ? null : idR);
                t.setRentalName(rs.getString("nama_rental"));
                return t;
            }
        }
    }
}
