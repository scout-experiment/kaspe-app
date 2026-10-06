package kaspe;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Penyandi sandi: garam acak per pengguna dan PBKDF2, semuanya dari pustaka bawaan
 * Java (tanpa kebergantungan baru).
 *
 * <p>Garamnya dibuat baru untuk tiap akun, jadi dua akun bersandi sama tetap
 * tersimpan sebagai hasil yang berbeda, dan hasil yang tersimpan tidak bisa
 * dibalik menjadi sandinya.
 */
public final class Sandi {

    /** Banyaknya putaran PBKDF2. */
    private static final int ITERATIONS = 100_000;
    /** Panjang kunci hasil penyandian, dalam bit. */
    private static final int KEY_BITS = 256;
    /** Panjang garam, dalam bita. */
    private static final int SALT_BYTES = 16;

    private Sandi() {
    }

    /** Garam baru, acak untuk tiap pemanggilan. */
    public static String saltBaru() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** Hasil penyandian sandi bersama garamnya, keduanya ditulis terbaca (Base64). */
    public static String hash(String sandi, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(sandi.toCharArray(),
                    Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return Base64.getEncoder().encodeToString(factory.generateSecret(spec).getEncoded());
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            // Tidak mungkin pada Java 8: PBKDF2WithHmacSHA256 dibawa JDK sendiri.
            throw new IllegalStateException("Penyandi sandi tidak tersedia di Java ini", e);
        }
    }

    /**
     * Apakah sandi ini cocok dengan hasil penyandiian tersimpan.
     *
     * <p>Dibandingkan dengan perbandingan yang lamanya tetap
     * ({@link MessageDigest#isEqual}), bukan membandingkan tulisannya: perbandingan
     * tulisan yang berhenti di huruf pertama yang beda membocorkan seberapa dekat
     * tebakannya.
     */
    public static boolean cocok(String sandi, String salt, String hash) {
        if (sandi == null || salt == null || hash == null) {
            return false;
        }
        try {
            byte[] tersimpan = Base64.getDecoder().decode(hash);
            byte[] hitung = Base64.getDecoder().decode(hash(sandi, salt));
            return MessageDigest.isEqual(tersimpan, hitung);
        } catch (IllegalArgumentException e) {
            // Hasil tersimpan bukan Base64 yang sah: tidak mungkin cocok.
            return false;
        }
    }
}
