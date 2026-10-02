# Spesifikasi Sistem

Dokumen ini bisa dipakai sebagai bahan BAB III (analisis dan perancangan) dan BAB IV (hasil dan pengujian).

## 1. Deskripsi

Sistem informasi pencatatan transaksi singkong berbasis desktop. Mencatat setiap pengiriman
singkong per truk, menghitung berat bersih setelah potongan (refraksi), menghitung jumlah uang
yang harus dibayar, dan menyajikan laporan per periode.

Sistem menggantikan pencatatan manual pada buku tulis. Aplikasi dipakai oleh satu orang
pengelola di satu komputer.

## 2. Kebutuhan fungsional

| Kode | Kebutuhan |
|------|-----------|
| F-01 | Sistem mengelola data rental (tambah, ubah, hapus, lihat) |
| F-02 | Sistem mengelola data truk beserta rental pemiliknya |
| F-02a | Sistem dapat menerima plat truk yang belum pernah tercatat, langsung dari layar transaksi, tanpa mendaftarkannya lebih dulu di data master. Ejaan plat diseragamkan supaya satu truk tidak terpecah menjadi beberapa |
| F-03 | Sistem mencatat transaksi per tanggal, berisi banyak baris plat |
| F-04 | Sistem menghitung berat bersih dan jumlah uang secara otomatis |
| F-05 | Sistem menampilkan total uang dan total berat bersih pada form input |
| F-06 | Sistem menyimpan transaksi secara utuh (semua baris berhasil atau semua dibatalkan) |
| F-07 | Sistem menampilkan laporan dengan filter rentang tanggal |
| F-08 | Sistem mencetak laporan |
| F-09 | Sistem menampilkan susut (selisih bobot lapak dan bobot pabrik) |

## 3. Kebutuhan non-fungsional

| Kode | Kebutuhan |
|------|-----------|
| N-01 | Aplikasi berjalan di desktop Windows/Linux dengan Java 8 (target kompilasi 1.8, diuji pada 8u504) |
| N-02 | Data tersimpan di database H2 yang ikut di dalam aplikasi, sehingga tidak perlu pemasangan terpisah |
| N-03 | Antarmuka berbahasa Indonesia |
| N-04 | Tampilan memakai tema FlatLaf sehingga bentuk jendela seragam di semua sistem operasi |
| N-04a | Huruf Inter ikut dikirim bersama aplikasi (`lib/flatlaf-fonts-inter-3.19.jar`), supaya ukuran huruf dan lebar kolom sama di semua komputer. Huruf itu hanya dipakai di Java 8 update 212 ke atas; di versi lebih tua dan di Java 9 aplikasi memakai huruf sistem |
| N-05 | Tabel database dibuat sendiri oleh aplikasi saat pertama kali dijalankan |
| N-06 | Aplikasi dapat diarahkan ke MySQL/MariaDB lewat berkas pengaturan, untuk pemakaian beberapa komputer |

## 4. Aturan perhitungan

```
berat_bersih = FLOOR((bobot_pabrik x (1 - refraksi/100)) / 5) x 5
jumlah_uang  = berat_bersih x harga
susut        = bobot_lapak - bobot_pabrik
```

- Bobot lapak hanya dicatat sebagai pembanding, tidak dipakai menghitung uang.
- Refraksi dan harga berbeda untuk setiap baris transaksi, jadi disimpan per baris.
- Pembulatan ke bawah ke kelipatan 5 kg sesuai kebiasaan mitra.

## 5. Rancangan basis data

Skema lengkapnya ada di `src/kaspe/schema.sql`, ditulis dalam bentuk yang dimengerti H2
maupun MySQL. Aplikasi menjalankannya sendiri saat pertama kali dipakai, jadi tabel-tabel
di bawah ini dibuat otomatis tanpa langkah pemasangan.

Empat tabel dan satu view:

| Tabel | Isi | Kunci utama | Relasi |
|-------|-----|-------------|--------|
| rental | pemilik truk (nama saja) | id_rental | - |
| truk | plat nomor | id_truk | id_rental ke rental |
| transaksi | header nota (tanggal) | id_transaksi | - |
| transaksi_detail | baris per plat | id_detail | id_transaksi ke transaksi, id_truk ke truk |
| v_transaksi | view laporan | - | gabungan transaksi + detail + truk + rental |

Relasi:

```
rental 1 ---- n truk 1 ---- n transaksi_detail n ---- 1 transaksi
```

## 6. Use case ringkas

- Lihat ringkasan di halaman pembuka: jumlah nota, total uang beserta uang bulan berjalan,
  total berat bersih, dan truk terdaftar
- Kelola Rental (pemilik truk) dan Truk dalam satu halaman
- Input Transaksi
- Lihat dan Cetak Laporan

## 7. Rencana pengujian black box

| No | Skenario | Input | Hasil yang diharapkan |
|----|----------|-------|------------------------|
| 1 | Tambah rental | nama rental baru | data muncul di tabel |
| 2 | Tambah truk | plat, dengan pemilik yang sedang disorot di kiri | data muncul di bawah pemiliknya |
| 2a | Tambah truk tanpa memilih pemilik | plat saja | ditolak, ada keterangan untuk memilih pemilik dulu |
| 2b | Pindah pemilik truk | truk yang disorot, pemilik tujuan | truk berpindah pemilik, plat dan barisnya tetap satu |
| 3 | Input transaksi | bobot pabrik, refraksi, harga | berat bersih dan jumlah uang terhitung otomatis |
| 4 | Simpan transaksi | beberapa baris plat | tersimpan, muncul di laporan |
| 5 | Batal simpan | satu baris data tidak lengkap | tidak ada data tersimpan |
| 6 | Filter laporan | rentang tanggal | hanya data dalam rentang itu yang tampil |
| 7 | Cetak laporan | klik tombol cetak | dialog cetak muncul |

## 8. Hasil pengujian otomatis

Seluruh uji dijalankan lewat `./test.sh` dan lulus tanpa kegagalan:

| Berkas uji | Cakupan | Hasil |
|------------|---------|-------|
| TestCalculator | rumus berat bersih, jumlah uang, susut, validasi | 7 lulus |
| TestDatabase | pembuatan tabel otomatis, skema, view, foreign key, pembersihan kolom lama (nomor nota, view lama ikut diuji) | 21 lulus |
| TestDao | master, plat diketik langsung (termasuk ejaan lama), ganti pemilik truk, simpan transaksi, rollback, laporan, rekap, hapus | 22 lulus |
| TestUi | panel tampilan tergambar, bilah halaman, huruf, pratinjau cetak, lebar kolom tabel, pemilihan baris data master, truk tanpa pemilik ditolak, pindah pemilik truk, angka bulan berjalan di beranda, kesesuaian rental dengan plat, dan nama rental yang diketik | 20 lulus |
