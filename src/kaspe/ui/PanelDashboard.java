package kaspe.ui;

import kaspe.Calculator;
import kaspe.dao.MasterDao;
import kaspe.dao.TransactionDao;
import kaspe.model.ReportRow;
import kaspe.util.Dates;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Halaman pembuka: ringkasan angka.
 *
 * <p>Sebelumnya halaman ini hanya berisi satu kartu petunjuk, sehingga sebagian besar
 * layar kosong dan tidak memberi keterangan apa pun tentang isi catatan. Sekarang isinya
 * angka yang paling sering dicari saat aplikasi dibuka — berapa pengiriman yang
 * berapa total uangnya.
 *
 * <p>Semua angkanya dihitung dari query yang sudah ada; tidak ada tabel atau kolom baru.
 */
public class PanelDashboard extends JPanel {


    public PanelDashboard() {
        setLayout(new BorderLayout(0, 14));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        load();
    }

    private void load() {
        try {
            TransactionDao dao = new TransactionDao();
            LocalDate from = dao.earliestDate();
            LocalDate to = LocalDate.now();

            List<ReportRow> rows = dao.listReport(from, to);
            BigDecimal amount = dao.totalAmount(from, to);
            BigDecimal weight = dao.totalNetWeight(from, to);

            // Satu pengiriman = satu catatan sendiri, jadi jumlahnya adalah jumlah
            // baris daftar datar (listReport mengembalikan satu baris per pengiriman),
            // bukan jumlah header transaksi.
            int pengiriman = rows.size();

            MasterDao master = new MasterDao();
            int rentals = master.listRental().size();
            int trucks = master.listTrucks().size();

            // Angka bulan berjalan. Keempat kartu lainnya berisi angka sepanjang masa yang
            // hampir tidak berubah dari hari ke hari, sehingga halaman ini terasa beku;
            // angka bulan berjalan itulah yang menjawab "sudah kucatat belum pengiriman
            // hari ini". Batasnya awal bulan, bukan 30 hari terakhir, supaya cocok dengan
            // cara uangnya dicocokkan per bulan.
            BigDecimal monthAmount = dao.totalAmount(to.withDayOfMonth(1), to);

            // Kartu-kartunya ditempel ke atas, tingginya setinggi isinya. Jangan ditaruh
            // di tengah dan jangan dibiarkan mengisi seluruh ruang: yang pertama
            // menyisakan lajur kosong di atas kartu, yang kedua membuat kartunya melar
            // jadi tinggi sekali sehingga lambang di dalamnya ikut tertarik jadi lajur
            // panjang seperti garis.
            add(buildStats(from, pengiriman, amount, weight, rentals, trucks, monthAmount),
                    BorderLayout.NORTH);


        } catch (Exception e) {
            // Halaman ini yang paling awal dibuka, jadi kegagalan membaca database di
            // sini berarti pengguna melihat jendela kosong tanpa penjelasan apa pun.
            // Karena itu dua-duanya dipakai: pesan di dalam halaman supaya jendelanya
            // tidak kosong, dan jendela pesan yang sama seperti di halaman lain.
            //
            // Jendela pesannya ditunda sebentar. Kalau ditampilkan langsung dari sini,
            // jendela utamanya belum sempat muncul, sehingga pesannya menggantung tanpa
            // induk yang jelas.
            JPanel pesan = Theme.card();
            JLabel teks = new JLabel("<html><b>Ringkasan tidak bisa dimuat.</b><br>"
                    + e.getMessage() + "</html>");
            teks.setForeground(Theme.DANGER);
            pesan.add(teks, BorderLayout.NORTH);
            add(pesan, BorderLayout.NORTH);

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    Theme.showError(PanelDashboard.this, e);
                }
            });
        }
    }

    /**
     * Empat kartu angka, tersusun dua baris dua kolom.
     *
     * <p>Dua baris membuat tiap kartu lebih lebar dan angkanya muat dibuat lebih besar.
     * Isinya tetap tersusun menurun - lambang, keterangan, angka, catatan - jadi kartu
     * selebar ini menyisakan ruang kosong di sebelah kanan. Itu disengaja: menaruh
     * catatannya di samping angka akan membuatnya terbaca sebagai angka kedua.
     */
    private JPanel buildStats(LocalDate earliest, int pengiriman, BigDecimal amount,
                              BigDecimal weight, int rentals, int trucks, BigDecimal monthAmount) {
        JPanel p = new JPanel(new GridLayout(2, 2, 14, 14));
        p.setOpaque(false);
        p.add(statCard(Icons.NOTE, "Pengiriman tercatat", String.valueOf(pengiriman),
                earliest == null ? "belum ada data" : "sejak " + Dates.format(earliest), false));
        // Angka besarnya tetap total sepanjang masa — mengubah arti angka besar tanpa
        // mengubah judulnya justru bikin salah baca. Yang bergerak ditaruh di keterangan
        // kecilnya, di baris yang memang sudah ada.
        p.add(statCard(Icons.CHART, "Total uang", "Rp " + Calculator.formatCurrency(amount),
                "bulan ini Rp " + Calculator.formatCurrency(monthAmount), true));
        p.add(statCard(Icons.HOME, "Total berat bersih", Calculator.formatCurrency(weight) + " kg",
                "setelah dipotong refraksi", false));
        p.add(statCard(Icons.TRUCK, "Truk terdaftar", String.valueOf(trucks),
                "dari " + rentals + " rental", false));
        return p;
    }

    /**
     * Satu kartu angka: lambang, keterangan kecil, angka besar, dan catatan tambahan.
     *
     * @param money benar kalau angkanya uang, supaya diwarnai seperti angka uang di tempat lain
     */
    private JPanel statCard(int icon, String title, String value, String note, boolean money) {
        JPanel card = Theme.card();

        JPanel isi = new JPanel(new BorderLayout(14, 0));
        isi.setOpaque(false);

        JLabel badge = new JLabel(Icons.of(icon, Theme.ACCENT, Icons.CARD_SIZE));
        badge.setHorizontalAlignment(SwingConstants.CENTER);
        badge.setOpaque(true);
        badge.setBackground(Theme.STRIP);
        badge.setBorder(BorderFactory.createLineBorder(Theme.STRIP_LINE, 1));
        badge.setPreferredSize(new Dimension(44, 44));
        // Lambangnya ditempel ke atas. Kalau dibiarkan, ia ikut melar mengikuti tinggi
        // kartu dan berubah jadi lajur panjang - terlihat seperti garis, bukan lambang.
        JPanel badgeAtas = new JPanel(new BorderLayout());
        badgeAtas.setOpaque(false);
        badgeAtas.add(badge, BorderLayout.NORTH);
        isi.add(badgeAtas, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);

        JLabel caption = Theme.caption(title);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Angkanya dibuat cukup besar supaya terbaca dari jauh, tetapi tidak sampai
        // terpotong di lebar kartu yang tersisa setelah bilah samping. Ukurannya dipakai
        // bersama lebar kartu: pada susunan dua kolom, kartunya cukup lebar untuk angka
        // yang lebih besar daripada waktu keempatnya berbagi satu baris.
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(Theme.bold(24f));
        valueLabel.setForeground(money ? Theme.MONEY : Theme.INK);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel noteLabel = new JLabel(note);
        noteLabel.setFont(Theme.semibold(11f));
        noteLabel.setForeground(Theme.INK_SOFT);
        noteLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        text.add(caption);
        text.add(Box.createRigidArea(new Dimension(1, 4)));
        text.add(valueLabel);
        text.add(Box.createRigidArea(new Dimension(1, 3)));
        text.add(noteLabel);

        JPanel textAtas = new JPanel(new BorderLayout());
        textAtas.setOpaque(false);
        textAtas.add(text, BorderLayout.NORTH);
        isi.add(textAtas, BorderLayout.CENTER);

        JPanel isiAtas = new JPanel(new BorderLayout());
        isiAtas.setOpaque(false);
        isiAtas.add(isi, BorderLayout.NORTH);
        card.add(isiAtas, BorderLayout.CENTER);
        return card;
    }

}
