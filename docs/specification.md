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
| F-01 | Sistem mengelola data rental (tambah, ubah, hapus, lihat). Penghapusan ditolak selama rentalnya masih punya truk |
| F-02 | Sistem mengelola data truk beserta rental pemiliknya. Penghapusan ditolak selama truknya masih dipakai catatan pengiriman, karena penghapusan itu akan menghilangkan platnya dari catatan yang sudah ada |
| F-02a | Sistem dapat menerima plat truk yang belum pernah tercatat, langsung dari layar transaksi, tanpa mendaftarkannya lebih dulu di data master. Ejaan plat diseragamkan supaya satu truk tidak terpecah menjadi beberapa |
| F-03 | Sistem mencatat setiap pengiriman sebagai satu catatan tersendiri, berisi tanggal, plat, rental, bobot lapak, bobot pabrik, refraksi, tanggal lunas, dan harga. Dua pengiriman dengan truk dan tanggal yang sama tetap menjadi dua catatan terpisah |
| F-04 | Sistem menghitung berat bersih dan jumlah uang secara otomatis |
| F-05 | Sistem menampilkan total uang dan total berat bersih pada form input |
| F-06 | Sistem menyimpan satu pengiriman secara utuh (tersimpan seluruhnya atau dibatalkan seluruhnya, termasuk truk dan rental yang baru pertama kali tercatat) |
| F-06a | Sistem dapat mengubah dan menghapus catatan pengiriman yang sudah tersimpan, satu per satu maupun beberapa sekaligus. Penghapusan sekaligus bersifat tuntas: kalau satu catatan gagal dihapus, tidak ada yang terhapus |
| F-06b | Sistem dapat menyaring daftar catatan pengiriman menurut rentang tanggal, nama rental, dan sepenggal plat. Rentang tanggal disaring oleh database, sedangkan nama rental dan plat dicocokkan memakai aturan penyeragaman aplikasi supaya ejaan lama tetap ditemukan. Halaman transaksi tidak menampilkan total uang tersimpan; yang tampil hanya jumlah uang per baris pada tabel, supaya tidak ada angka yang bisa disangka milik seluruh catatan padahal bukan |
| F-07 | Sistem menampilkan laporan dengan filter rentang tanggal, rental, dan sepenggal plat. Saringan yang sedang dipakai ikut tertulis di kaki halaman yang dicetak |
| F-08 | Sistem mencetak laporan |
| F-08a | Sistem dapat membuat cadangan database bawaan (H2) ke berkas bertanggal tanpa menimpa cadangan sebelumnya. Pada MySQL/MariaDB, pencadangan otomatis tidak dilakukan dan hal itu diberitahukan |
| F-08b | Sistem menolak dibuka kalau berkas setelannya ada tetapi tidak memuat letak database, disertai penjelasan berkas mana yang bermasalah dan jalan keluarnya |
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
| N-07 | Kalau berkas pengaturan ada tetapi tidak memuat letak database, aplikasi MENOLAK dibuka dan menjelaskan berkas mana yang bermasalah beserta jalan keluarnya. Aplikasi tidak pernah diam-diam memakai database lain daripada yang dimaksud penggunanya. Penolakan itu tidak membuat atau mengubah apa pun |

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
rental 1 ---- n truk 1 ---- n transaksi_detail 1 ---- 1 transaksi
```

Satu catatan pengiriman menempati satu baris `transaksi_detail` beserta satu baris
`transaksi` sebagai tanggalnya. Database lama yang satu tanggalnya memuat beberapa
pengiriman dirapikan otomatis saat aplikasi pertama kali dijalankan, sehingga bentuk
1 : 1 di atas berlaku untuk seluruh isi buku catatan.

## 6. Use case ringkas

- Lihat ringkasan di halaman pembuka: jumlah pengiriman, total uang beserta uang bulan
  berjalan, total berat bersih, dan truk terdaftar
- Kelola Rental (pemilik truk) dan Truk dalam satu halaman
- Input Transaksi
- Cari catatan pengiriman lewat saringan tanggal, rental, dan plat, lalu ubah atau hapus
- Lihat dan Cetak Laporan

## 7. Rencana pengujian black box

| No | Skenario | Input | Hasil yang diharapkan |
|----|----------|-------|------------------------|
| 1 | Tambah rental | nama rental baru | data muncul di tabel |
| 2 | Tambah truk | plat, dengan pemilik yang sedang disorot di kiri | data muncul di bawah pemiliknya |
| 2a | Tambah truk tanpa memilih pemilik | plat saja | ditolak, ada keterangan untuk memilih pemilik dulu |
| 2b | Pindah pemilik truk | truk yang disorot, pemilik tujuan | truk berpindah pemilik, plat dan barisnya tetap satu |
| 2c | Hapus truk yang sudah dipakai | truk yang punya catatan pengiriman | ditolak, disertai jumlah catatan yang terdampak; riwayatnya tetap utuh |
| 2d | Hapus rental yang masih punya truk | rental yang punya truk | ditolak, disertai jumlah truknya |
| 3 | Input transaksi | bobot pabrik, refraksi, harga | berat bersih dan jumlah uang terhitung otomatis |
| 4 | Simpan pengiriman | bobot, plat, rental, harga | tersimpan sebagai satu catatan, muncul di daftar dan di laporan |
| 4a | Simpan dua pengiriman truk dan tanggal yang sama | form yang sama dua kali | tersimpan sebagai DUA catatan terpisah |
| 4b | Ubah pengiriman | pilih satu baris, tekan Ubah, ubah bobotnya | catatan yang sama berubah, tidak ada catatan baru |
| 4c | Hapus pengiriman | pilih satu baris, tekan Hapus | catatan itu hilang, yang lain tetap |
| 4d | Hapus beberapa sekaligus | pilih beberapa baris, tekan Hapus | semuanya hilang; kalau ada yang gagal, tidak ada yang terhapus |
| 5 | Batal simpan | data tidak lengkap | tidak ada data tersimpan |
| 5a | Saring daftar pengiriman | rentang tanggal, rental, sepenggal plat | hanya catatan yang cocok yang tampil, dan kolom Jumlah Uang tiap baris tetap milik barisnya sendiri |
| 5b | Kembalikan saringan ke semua | tekan Semua | seluruh catatan tampil lagi |
| 6 | Filter laporan | rentang tanggal, rental, sepenggal plat | hanya data yang cocok yang tampil, dan kertas yang dicetak menyebut saringan itu |
| 7 | Cetak laporan | klik tombol cetak | dialog cetak muncul |
| 8 | Cadangkan database | klik Cadangkan Database | berkas cadangan bertanggal terbentuk, jalurnya diberitahukan |
| 9 | Berkas setelan rusak | `kaspe.properties` tanpa `db.url` | aplikasi menolak dibuka, menjelaskan berkas dan jalan keluarnya; database tidak tersentuh |

## 8. Hasil pengujian otomatis

Seluruh uji dijalankan lewat `./test.sh` dan lulus tanpa kegagalan:

| Berkas uji | Cakupan | Hasil |
|------------|---------|-------|
| TestCalculator | rumus berat bersih, jumlah uang, susut, satuan bobot/refraksi, validasi | 8 lulus |
| TestDatabase | pembuatan tabel otomatis, skema, view, foreign key, pembersihan kolom lama (nomor nota, view lama ikut diuji), perapian database lama menjadi satu catatan per pengiriman (jumlah dan total uang tidak berubah, waktu pencatatan asli ikut pindah, aman dijalankan berulang) | 56 lulus |
| TestDao | master, plat diketik langsung (termasuk ejaan lama), ganti pemilik truk, tambah rental tidak menimpa rental lama, nama/plat kembar ditolak, simpan transaksi, rollback, laporan, rekap, hapus, ubah pengiriman (hitungan diulang, tanggal ikut pindah, rental tidak tertimpa), hapus sekaligus yang tuntas, daftar pengiriman terbaru dulu, saringan tanggal/rental/plat (termasuk plat ejaan lama dan rental tanpa beda huruf besar-kecil), penolakan hapus truk/rental yang beriwayat, penolakan cadangan di luar H2, cadangan sungguhan pada H2 berbasis berkas | 91 lulus |
| TestUi | panel tampilan tergambar, bilah halaman, huruf, pratinjau cetak, lebar kolom tabel, tinggi daftar pengiriman tersimpan, tombol tidak terpotong wadahnya, kolom tabel utuh dan halaman muat tanpa digulir pada ukuran jendela minimum, judul kolom rata kiri, judul bilah atas ikut pindah halaman, baris menu bilah samping, pemilihan baris data master, truk tanpa pemilik ditolak, pindah pemilik truk, angka bulan berjalan di beranda, kesesuaian rental dengan plat, dan nama rental yang diketik | 38 lulus |
| TestAlur | satu Simpan jadi satu catatan, truk dan tanggal sama tetap dua catatan, form dikosongkan setelah simpan (tanggal tetap), simpan kedua tidak menggandakan, ubah menulis tanpa menambah, Batal tidak mengubah apa pun, hapus yang dipilih, pilihan menentukan tombol, id baris dibaca dari model, saringan daftar (rental baru langsung muncul, batas dirapikan), rental wajib diisi, pemilik berbeda ditolak, tanggal tidak valid ditolak, belum lunas tersimpan, isian tidak hilang saat pindah halaman | 115 lulus |
| **Total** | | **308 lulus, 0 gagal** |
