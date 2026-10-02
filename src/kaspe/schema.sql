-- =====================================================================
-- SKEMA DATABASE - Aplikasi Pencatatan Transaksi Kaspe
--
-- File ini disimpan DI DALAM aplikasi (src/kaspe/schema.sql) dan dijalankan
-- sendiri oleh aplikasi saat pertama kali dijalankan. Jadi pengguna tidak
-- perlu memasang database lebih dulu.
--
-- Dipakai dua-duanya: H2 (bawaan, tanpa install) dan MySQL/MariaDB (kalau
-- diarahkan lewat kaspe.properties). Karena itu tidak ada perintah yang
-- hanya dimengerti salah satu merek.
--
-- Semua perintah boleh diulang dengan aman.
--
-- Kolom mengikuti buku transaksi mitra:
--   No | Plat | Nama Rental | Bobot Lapak | Bobot Pabrik | Refraksi
--      | Berat Bersih | Tanggal Lunas | Harga | Jumlah Uang
--
-- Rumus (sudah dicocokkan dengan 4 baris buku, hasil sama 4/4):
--   berat_bersih = (bobot_pabrik x (1 - refraksi/100)) dibulatkan ke bawah ke kelipatan 5
--   jumlah_uang  = berat_bersih x harga
-- =====================================================================

-- 1. RENTAL (pemilik truk / penyedia angkutan)
--    Isinya hanya nama. Nomor HP dan keterangan pernah ada di sini, tetapi dibuang
--    karena tidak pernah muncul di laporan, di hasil cetak, maupun di layar transaksi.
--    Database lama yang masih menyimpan dua kolom itu dibersihkan oleh Schema.java.
CREATE TABLE IF NOT EXISTS rental (
  id_rental     INT AUTO_INCREMENT PRIMARY KEY,
  nama_rental   VARCHAR(100)  NOT NULL UNIQUE
);

-- 2. TRUK (plat nomor)
CREATE TABLE IF NOT EXISTS truk (
  id_truk       INT AUTO_INCREMENT PRIMARY KEY,
  plat          VARCHAR(20)   NOT NULL UNIQUE,
  id_rental     INT           NULL,
  CONSTRAINT fk_truk_rental FOREIGN KEY (id_rental)
    REFERENCES rental(id_rental) ON UPDATE CASCADE ON DELETE SET NULL
);

-- 3. TRANSAKSI (header / tanggal)
--    Nomor nota (no_transaksi) pernah ada di sini, tetapi dibuang: operator tidak pernah
--    menyebut nomor nota, dan laporan yang dipakai sehari-hari tidak menampilkannya.
--    Yang menandai satu nota sekarang tanggalnya. Database lama yang masih menyimpan
--    kolom itu dibersihkan oleh Schema.java.
CREATE TABLE IF NOT EXISTS transaksi (
  id_transaksi  INT AUTO_INCREMENT PRIMARY KEY,
  tanggal       DATE          NOT NULL,
  dibuat_pada   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_trx_tanggal (tanggal)
);

-- 4. TRANSAKSI DETAIL (satu baris buku = satu plat)
--    Refraksi dan harga disimpan per baris karena nilainya berbeda-beda tiap transaksi.
CREATE TABLE IF NOT EXISTS transaksi_detail (
  id_detail       INT AUTO_INCREMENT PRIMARY KEY,
  id_transaksi    INT            NOT NULL,
  id_truk         INT            NULL,
  bobot_lapak     DECIMAL(10,2)  NOT NULL,      -- kg, dicatat sebagai pembanding
  bobot_pabrik    DECIMAL(10,2)  NOT NULL,      -- kg, dipakai untuk hitungan
  refraksi_persen DECIMAL(5,2)   NOT NULL,      -- contoh 15.00
  berat_bersih    DECIMAL(10,2)  NOT NULL,      -- hasil hitung, disimpan sebagai arsip
  tanggal_lunas   DATE           NULL,
  harga           DECIMAL(12,2)  NOT NULL,      -- Rp per kg
  jumlah_uang     DECIMAL(15,2)  NOT NULL,      -- hasil hitung
  CONSTRAINT fk_det_trx  FOREIGN KEY (id_transaksi)
    REFERENCES transaksi(id_transaksi) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_det_truk FOREIGN KEY (id_truk)
    REFERENCES truk(id_truk) ON UPDATE CASCADE ON DELETE SET NULL,
  INDEX idx_det_tanggal_lunas (tanggal_lunas),
  INDEX idx_det_trx (id_transaksi)
);

-- View laporan siap pakai (termasuk susut = bobot lapak - bobot pabrik)
CREATE OR REPLACE VIEW v_transaksi AS
SELECT t.id_transaksi, t.tanggal,
       d.id_detail, tr.plat, r.nama_rental,
       d.bobot_lapak, d.bobot_pabrik, d.refraksi_persen,
       d.berat_bersih, d.tanggal_lunas, d.harga, d.jumlah_uang,
       (d.bobot_lapak - d.bobot_pabrik) AS susut
FROM transaksi_detail d
JOIN transaksi t ON t.id_transaksi = d.id_transaksi
LEFT JOIN truk tr  ON tr.id_truk = d.id_truk
LEFT JOIN rental r ON r.id_rental = tr.id_rental;
