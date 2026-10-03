package kaspe.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Bantu format dan baca tanggal (dd-MM-yyyy untuk tampilan). */
public final class Dates {

    public static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private Dates() {
    }

    public static String format(LocalDate t) {
        return t == null ? "" : DISPLAY.format(t);
    }

    /**
     * Nama hari dan bulan ditulis sendiri, tidak diambil dari data bahasa Java.
     *
     * <p>Kalau memakai data bahasa bawaan, di komputer yang datanya tidak lengkap
     * hasilnya diam-diam berubah menjadi bahasa Inggris tanpa pesan kesalahan apa pun.
     * Ditulis sendiri seperti ini hasilnya pasti sama di semua komputer.
     */
    private static final String[] DAYS = {
            "Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"
    };

    private static final String[] MONTHS = {
            "Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    };

    /**
     * Tanggal bentuk panjang untuk kepala halaman, mis. {@code "Kamis, 02 Oktober 2026"}.
     * Mengembalikan teks kosong kalau tanggalnya tidak ada.
     */
    public static String longFormat(LocalDate t) {
        if (t == null) {
            return "";
        }
        // getDayOfWeek(): Senin = 1 sampai Minggu = 7, sedangkan DAYS dimulai dari Minggu.
        int day = t.getDayOfWeek().getValue() % 7;
        return DAYS[day] + ", " + String.format("%02d", t.getDayOfMonth())
                + " " + MONTHS[t.getMonthValue() - 1] + " " + t.getYear();
    }

    /**
     * Baca tanggal dari teks. Mengembalikan null kalau kosong / tidak valid.
     *
     * <p>Pembacaannya ketat: 31-02-2026 ditolak, bukan diam-diam digeser menjadi
     * 28-02. Pola tahunnya memakai huruf u, bukan y, karena tahun bentuk-u tidak
     * butuh era untuk bisa dibaca ketat, sedangkan tahun bentuk-y ditolak ketat
     * tanpa era. Empat huruf juga berarti tahun dua angka (mis. 26) ditolak,
     * bukan diam-diam menjadi tahun 26 Masehi.
     */
    public static LocalDate parse(String text) {
        return baca(text, new String[]{"dd-MM-uuuu", "dd/MM/uuuu", "d-M-uuuu", "d/M/uuuu", "uuuu-MM-dd"});
    }

    /**
     * Baca tanggal dari teks kotak tanggal. Mengembalikan null kalau kosong / tidak valid.
     *
     * <p>Kotak tanggal hanya menerima satu bentuk: hari-bulan-tahun dengan tanda
     * minus. Bentuk lain (5/10/2026, 2026-10-05) ditolak, bukan diterima diam-diam —
     * satu bentuk saja yang membuat tulisan yang salah langsung kelihatan salah.
     */
    public static LocalDate parseInput(String text) {
        return baca(text, new String[]{"dd-MM-uuuu", "d-M-uuuu"});
    }

    private static LocalDate baca(String text, String[] patterns) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String s = text.trim();
        for (String pattern : patterns) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(pattern)
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT));
            } catch (DateTimeParseException ignored) {
                // coba pola berikutnya
            }
        }
        return null;
    }
}
