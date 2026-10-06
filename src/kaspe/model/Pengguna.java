package kaspe.model;

/**
 * Akun pengguna aplikasi: nama untuk masuk, perannya, dan sandinya yang sudah
 * disandi (lihat {@code kaspe.Sandi}).
 */
public class Pengguna {

    /** Peran admin: boleh juga mengelola akun pengguna. */
    public static final String ADMIN = "ADMIN";
    /** Peran pengguna biasa. */
    public static final String USER = "USER";

    private int id;
    private String nama;
    private String peran;
    private String sandiHash;
    private String sandiSalt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNama() { return nama; }
    public void setNama(String nama) { this.nama = nama; }

    public String getPeran() { return peran; }
    public void setPeran(String peran) { this.peran = peran; }

    public String getSandiHash() { return sandiHash; }
    public void setSandiHash(String sandiHash) { this.sandiHash = sandiHash; }

    public String getSandiSalt() { return sandiSalt; }
    public void setSandiSalt(String sandiSalt) { this.sandiSalt = sandiSalt; }

    /** Apakah akun ini admin. */
    public boolean admin() {
        return ADMIN.equals(peran);
    }

    @Override
    public String toString() { return nama; }
}
