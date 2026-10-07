# Spesifikasi Sistem

Dokumen ini bisa dipakai sebagai bahan BAB III (analisis dan perancangan) dan BAB IV (hasil dan pengujian).

## 1. Deskripsi

Sistem informasi pencatatan transaksi singkong berbasis desktop. Mencatat setiap pengiriman
singkong per truk, menghitung berat bersih setelah potongan (refraksi), menghitung jumlah uang
yang harus dibayar, dan menyajikan laporan per periode.

Sistem menggantikan pencatatan manual pada buku tulis. Aplikasi dipakai lewat akun bernama
yang dilindungi sandi: setiap pemakaian dimulai dari layar masuk, dengan dua peran — Admin
(boleh mengelola akun pengguna) dan Pengguna (mencatat transaksi dan melihat laporan).

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
| F-10 | Sistem meminta nama pengguna dan sandi sebelum halaman apa pun terbuka. Pada pemakaian pertama (tabel pengguna masih kosong), layar yang sama berubah menjadi pembuat admin pertama: nama, sandi, dan ulangi sandi (sandi minimal 4 karakter, keduanya harus sama). Gagal masuk selalu memunculkan satu pesan yang sama ("Nama atau sandi salah."), baik namanya tidak tercatat maupun sandinya salah, supaya tidak ketahuan nama mana yang tercatat |
| F-10a | Sistem menyediakan tombol Keluar di kaki bilah samping untuk semua peran. Kalau ada isian transaksi yang belum disimpan, keluar ditanya dulu, lalu sesi berakhir dan layar masuk dibuka kembali. Menutup jendela aplikasi mengakhiri aplikasi seluruhnya |
| F-11 | Sistem mengelola akun pengguna pada halaman Pengguna, yang hanya ditambahkan ke bilah samping untuk Admin: tambah akun, ubah nama, peran, atau sandi, dan hapus. Penghapusan ditolak kalau akunnya admin terakhir atau akun yang sedang dipakai masuk. Perubahan peran juga ditolak kalau akunnya admin terakhir yang tersisa: tanpa admin, halaman Pengguna tidak bisa dibuka lagi dari dalam aplikasi, jadi buku catatan terkunci secara permanen |

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
| N-08 | Sandi tidak pernah tersimpan apa adanya: yang tercatat adalah hasil PBKDF2WithHmacSHA256 (100.000 putaran, kunci 256 bit) dengan garam acak segar 16 bita per akun, dan pemeriksaannya memakai perbandingan yang lamanya tetap. Semuanya dari pustaka bawaan Java, tanpa kebergantungan baru |

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

Lima tabel dan satu view:

| Tabel | Isi | Kunci utama | Relasi |
|-------|-----|-------------|--------|
| rental | pemilik truk (nama saja) | id_rental | - |
| truk | plat nomor | id_truk | id_rental ke rental |
| transaksi | header nota (tanggal) | id_transaksi | - |
| transaksi_detail | baris per plat | id_detail | id_transaksi ke transaksi, id_truk ke truk |
| pengguna | akun masuk: nama, peran, sandi tersandi beserta garamnya | id_pengguna | - (berdiri sendiri) |
| v_transaksi | view laporan | - | gabungan transaksi + detail + truk + rental |

Relasi:

```
rental 1 ---- n truk 1 ---- n transaksi_detail 1 ---- 1 transaksi

pengguna (berdiri sendiri, tidak berelasi)
```

Tabel `pengguna` tidak berhubungan dengan tabel transaksi: akun tidak terikat pada rental
atau truk tertentu.

Satu catatan pengiriman menempati satu baris `transaksi_detail` beserta satu baris
`transaksi` sebagai tanggalnya. Database lama yang satu tanggalnya memuat beberapa
pengiriman dirapikan otomatis saat aplikasi pertama kali dijalankan, sehingga bentuk
1 : 1 di atas berlaku untuk seluruh isi buku catatan.

## 6. Use case ringkas

- Lihat ringkasan di halaman pembuka: jumlah pengiriman, total uang beserta uang bulan
  berjalan, total berat bersih, dan truk terdaftar
- Masuk dengan nama pengguna dan sandi; pemakaian pertama sekalian membuat admin pertama
- Keluar dari sesi lewat tombol Keluar di kaki bilah samping, lalu masuk lagi dengan akun lain
- Kelola akun pengguna beserta perannya (khusus Admin)
- Kelola Truk beserta pemiliknya (Rental) lewat dialog Kelola Data Truk, yang dibuka dari
  tombol ikon di sebelah kotak Plat / Truk pada halaman Transaksi — termasuk mengganti
  nama pemilik atau menghapusnya lewat tombol Kelola Pemilik di dialog itu
- Input Transaksi
- Cari catatan pengiriman lewat saringan tanggal, rental, dan plat, lalu ubah atau hapus
- Urutkan daftar pengiriman dan laporan dengan mengklik judul kolomnya
- Persempit rentang laporan dengan satu klik (Hari ini / Bulan ini / Semua)
- Lihat rekap total per rental dari baris yang sedang tampil
- Lihat, Cetak, dan ekspor Laporan ke berkas CSV

## 7. Rencana pengujian black box

| No | Skenario | Input | Hasil yang diharapkan |
|----|----------|-------|------------------------|
| 1 | Tambah rental | nama rental baru | data muncul di tabel |
| 2 | Tambah truk | plat, lalu pilih atau ketik pemiliknya | truk langsung tersimpan dan muncul di tabel; pemilik yang belum tercatat dibuat dulu |
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
| 6a | Urutkan laporan | klik judul kolom Jumlah Uang | angka terbesar di atas; Rp 10.000.000 mendahului Rp 6.888.500, dan kolom yang diurut memperlihatkan panahnya |
| 6b | Urutkan daftar pengiriman | klik judul kolom Jumlah Uang | sama seperti laporan, dan baris yang dipilih tetap menunjuk catatan yang sama |
| 6c | Ekspor laporan | klik Ekspor CSV, pilih berkas | berkas berisi baris yang sedang tampil dalam urutan yang tampil; angkanya polos tanpa satuan dan jumlah kolom uangnya sama dengan total di layar |
| 6d | Ekspor laporan ke berkas yang sudah ada | nama berkas yang sudah dipakai | ditanya dulu sebelum ditimpa; menjawab tidak membatalkan ekspornya |
| 6e | Berkas ekspor menyebut cakupannya | ekspor setelah menyaring satu rental dan mengurut kolom | berkas memuat periode, rental yang disaring, dan urutan yang berlaku |
| 6f | Rentang cepat | klik Bulan ini | kotak tanggal berisi awal bulan sampai hari ini, dan tabelnya ikut dimuat ulang |
| 6g | Rekap per rental | klik Rekap per rental | rekap menjumlah baris yang sedang tampil saja; baris Jumlah-nya sama dengan total di layar |
| 6h | Urutkan daftar pengiriman | klik judul kolom Tgl Lunas pada data yang memuat nota belum lunas | urutan berjalan tanpa gagal, sel kosong berbaris di satu ujung |
| 7 | Cetak laporan | klik tombol cetak | dialog cetak muncul |
| 8 | Cadangkan database | klik Cadangkan Database | berkas cadangan bertanggal terbentuk, jalurnya diberitahukan |
| 9 | Berkas setelan rusak | `kaspe.properties` tanpa `db.url` | aplikasi menolak dibuka, menjelaskan berkas dan jalan keluarnya; database tidak tersentuh |
| 10 | Masuk | nama dan sandi yang benar | halaman pembuka terbuka sesuai peran akunnya |
| 10a | Gagal masuk karena nama tidak tercatat | nama yang tidak ada di tabel pengguna | ditolak dengan pesan "Nama atau sandi salah." — sama seperti kalau sandinya salah, tidak mengungkapkan ada tidaknya nama itu |
| 10b | Buat admin pertama | pemakaian pertama: nama, sandi, ulangi sandi | admin pertama tercatat dan langsung masuk; sandi di bawah 4 karakter atau ulangannya berbeda ditolak |
| 10c | Hapus admin terakhir | akun admin yang tersisa satu-satunya | ditolak dengan pesan berbahasa Indonesia |
| 10d | Hapus akun yang sedang dipakai | akun yang sedang masuk | ditolak dengan pesan berbahasa Indonesia |
| 10e | Halaman Pengguna untuk peran Pengguna | masuk dengan peran Pengguna | entri Pengguna tidak tampil di bilah samping |
| 10f | Turunkan admin terakhir menjadi pengguna biasa | satu-satunya admin, ubah perannya lewat Ubah | ditolak dengan pesan berbahasa Indonesia; tanpa penolakan ini tidak ada admin tersisa dan halaman Pengguna tak terjangkau dari dalam aplikasi |
| 10g | Keluar | tombol Keluar di kaki bilah samping | sesi berakhir dan layar masuk terbuka kembali; isian transaksi yang belum disimpan ditanya dulu |

## 8. Hasil pengujian otomatis

Seluruh uji dijalankan lewat `./test.sh` dan lulus tanpa kegagalan:

| Berkas uji | Cakupan | Hasil |
|------------|---------|-------|
| TestCalculator | rumus berat bersih, jumlah uang, susut, satuan bobot/refraksi, angka berdesimal, validasi | 12 lulus |
| TestDatabase | pembuatan tabel otomatis (termasuk tabel pengguna), skema, view, foreign key, pembersihan kolom lama (nomor nota, view lama ikut diuji), perapian database lama menjadi satu catatan per pengiriman (jumlah dan total uang tidak berubah, waktu pencatatan asli ikut pindah, aman dijalankan berulang) | 57 lulus |
| TestDao | master, plat diketik langsung (termasuk ejaan lama), ganti pemilik truk, tambah rental tidak menimpa rental lama, nama/plat kembar ditolak, simpan transaksi, rollback, laporan, rekap, hapus, ubah pengiriman (hitungan diulang, tanggal ikut pindah, rental tidak tertimpa), hapus sekaligus yang tuntas, daftar pengiriman terbaru dulu, saringan tanggal/rental/plat (termasuk plat ejaan lama dan rental tanpa beda huruf besar-kecil), penolakan hapus truk/rental yang beriwayat, penolakan cadangan di luar H2, cadangan sungguhan pada H2 berbasis berkas, akun pengguna (penyandian sandi dengan garam berbeda, masuk benar/salah, ubah nama/peran/sandi, hapus) | 111 lulus |
| TestUi | panel tampilan tergambar, bilah halaman, huruf, pratinjau cetak, lebar kolom tabel, tinggi daftar pengiriman tersimpan, tombol tidak terpotong wadahnya, kolom tabel utuh dan halaman muat tanpa digulir pada ukuran jendela minimum, perataan judul kolom mengikuti isinya, tombol Simpan selebar kotak hasil, berkas CSV siap dijumlahkan, berkas CSV menyebut cakupan dan urutannya, panah penanda urut tergambar, judul kolom tidak terpotong saat panah urut tampil, kolom uang dan tanggal terurut menurut nilainya, pengurutan tahan baris belum lunas, kaki cetak menyebut urutan, tombol rentang cepat memasang rentangnya, nilai susut di berkas CSV, memasang pembanding menyalakan pengurutnya sendiri, rekap per rental menghormati saringan, urutan nama tidak bergantung bahasa komputer, judul bilah atas ikut pindah halaman, baris menu bilah samping, pemilihan baris data master, truk tanpa pemilik ditolak, pindah pemilik truk, angka bulan berjalan di beranda, kesesuaian rental dengan plat, dan nama rental yang diketik, layar masuk (pembuatan admin pertama, pesan gagal masuk yang selalu sama), menu Pengguna tampil untuk admin dan tidak tampil untuk pengguna biasa, penolakan hapus admin terakhir, tabel akun yang tidak melar dan tombolnya yang duduk di bawah isiannya | 86 lulus |
| TestAlur | satu Simpan jadi satu catatan, truk dan tanggal sama tetap dua catatan, form dikosongkan setelah simpan (tanggal tetap), simpan kedua tidak menggandakan, ubah menulis tanpa menambah, Batal tidak mengubah apa pun, hapus yang dipilih, pilihan menentukan tombol, id baris dibaca dari model, saringan daftar (rental baru langsung muncul, batas dirapikan), rental wajib diisi, pemilik berbeda ditolak, tanggal tidak valid ditolak, belum lunas tersimpan, isian tidak hilang saat pindah halaman | 115 lulus |
| **Total** | | **381 lulus, 0 gagal** |
