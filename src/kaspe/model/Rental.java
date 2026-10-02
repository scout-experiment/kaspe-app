package kaspe.model;

import java.util.Locale;

/**
 * Pemilik truk / penyedia angkutan.
 *
 * <p>Isinya hanya nama. Nomor HP dan keterangan pernah ada di sini, tetapi dibuang:
 * keduanya tidak pernah muncul di laporan, di hasil cetak, maupun di layar transaksi,
 * jadi orang yang mengisinya tidak pernah melihat hasilnya di mana pun.
 */
public class Rental {
    private int rentalId;
    private String rentalName;

    public int getRentalId() { return rentalId; }
    public void setRentalId(int rentalId) { this.rentalId = rentalId; }

    public String getRentalName() { return rentalName; }

    /**
     * Simpan nama rental dalam bentuk yang seragam, bukan apa adanya.
     *
     * <p>Nama rental diketik langsung oleh operator, dan orang menuliskannya bermacam-macam:
     * " CV  Mitra Tani ", "cv mitra tani", "CV MITRA TANI". Kalau dibiarkan apa adanya,
     * satu rental menjadi beberapa pemilik berbeda di laporan — dan total uang per pemilik,
     * yang justru alasan aplikasi ini ada, ikut terpecah.
     *
     * <p>Besar-kecil huruf sengaja tidak diubah di sini, berbeda dari
     * {@link Truck#setPlate}. Nama rental tampil di laporan dan ikut tercetak, dan
     * "CV MITRA TANI" bukan bentuk yang enak dibaca. Yang disamakan hanya spasinya;
     * pencocokan nama yang tidak membedakan besar-kecil huruf dikerjakan oleh
     * {@link #matchKey}.
     */
    public void setRentalName(String rentalName) {
        this.rentalName = normalizeName(rentalName);
    }

    /**
     * Bentuk seragam nama rental: spasi di ujung dibuang dan spasi berturut-turut
     * dirapatkan menjadi satu. Mengembalikan null kalau namanya tidak ada.
     */
    public static String normalizeName(String rentalName) {
        if (rentalName == null) {
            return null;
        }
        return rentalName.trim().replaceAll("\\s+", " ");
    }

    /**
     * Kunci pencocokan nama: bentuk seragam dengan huruf kecil semua.
     *
     * <p>Dipakai untuk mengenali rental yang sama walaupun ejaan hurufnya berbeda, supaya
     * "cv mitra tani" tidak menjadi rental kedua di samping "CV Mitra Tani".
     */
    public static String matchKey(String rentalName) {
        String rapi = normalizeName(rentalName);
        return rapi == null ? null : rapi.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() { return rentalName; }
}
