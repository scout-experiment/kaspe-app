package kaspe.model;

import java.util.Locale;

public class Truck {
    private int truckId;
    private String plate;
    private Integer rentalId;
    private String rentalName;   // hasil join, untuk tampilan

    public int getTruckId() { return truckId; }
    public void setTruckId(int truckId) { this.truckId = truckId; }

    public String getPlate() { return plate; }

    /**
     * Simpan plat dalam bentuk yang seragam, bukan apa adanya.
     *
     * <p>Plat diketik langsung oleh operator, dan orang menuliskannya bermacam-macam:
     * "be 8234 hd", "BE  8234  HD", " BE 8234 HD ". Kalau dibiarkan apa adanya, ketiganya
     * menjadi tiga truk berbeda di laporan padahal truknya satu — dan tidak ada yang
     * memberi tahu. Bentuknya diseragamkan di sini, satu tempat, supaya halaman data
     * master dan layar transaksi tidak bisa berbeda aturan.
     */
    public void setPlate(String plate) {
        this.plate = normalizePlate(plate);
    }

    /**
     * Bentuk seragam plat: tanpa spasi di ujung, huruf besar, dan spasi berturut-turut
     * dirapatkan menjadi satu spasi. Mengembalikan null kalau platnya tidak ada.
     */
    public static String normalizePlate(String plate) {
        if (plate == null) {
            return null;
        }
        return plate.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public Integer getRentalId() { return rentalId; }
    public void setRentalId(Integer rentalId) { this.rentalId = rentalId; }

    public String getRentalName() { return rentalName; }
    public void setRentalName(String rentalName) { this.rentalName = rentalName; }

    @Override
    public String toString() { return plate + (rentalName == null ? "" : " - " + rentalName); }
}
