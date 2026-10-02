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

    /** Baca tanggal dari teks. Mengembalikan null kalau kosong / tidak valid. */
    public static LocalDate parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String s = text.trim();
        for (String pattern : new String[]{"dd-MM-yyyy", "dd/MM/yyyy", "d-M-yyyy", "d/M/yyyy", "yyyy-MM-dd"}) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // coba pola berikutnya
            }
        }
        return null;
    }
}
