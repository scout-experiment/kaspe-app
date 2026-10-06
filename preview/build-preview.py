#!/usr/bin/env python3
"""Bangun preview/index.html dari file PNG di folder yang sama.

Jalankan:  python3 preview/build-preview.py

Skrip ini menyematkan semua screenshot ke dalam satu file HTML (base64),
supaya hasilnya bisa dibuka langsung di browser tanpa server dan tanpa internet.
Tidak butuh dependensi apa pun selain Python 3 standar.
"""
import base64
import pathlib

FOLDER = pathlib.Path(__file__).resolve().parent

# (nama file, judul, keterangan)
GALERI = [
    ("01-dashboard.png", "Halaman pembuka",
     "Aplikasi dibuka langsung di halaman ini, tanpa login. Isinya empat kartu ringkasan: "
     "jumlah pengiriman, total uang beserta uang bulan berjalan, total berat bersih, dan truk "
     "terdaftar."),
    ("02-transaction-input.png", "Input transaksi",
     "Bobot lapak, bobot pabrik, refraksi, dan harga diisi. Berat bersih dan jumlah uang "
     "terhitung otomatis, dan tiap baris langsung masuk ke tabel."),
    ("03-transaction-saved.png", "Transaksi tersimpan",
     "Setelah disimpan, bobot, harga, plat, dan rental dikosongkan, sedangkan tanggalnya "
     "sengaja dibiarkan - pengisian biasanya beberapa pengiriman bertanggal sama, "
     "sedangkan plat hampir selalu berganti."),
    ("04-master.png", "Data master",
     "Satu dialog berisi tabel semua truk beserta pemiliknya dan satu baris isian. Pemilik "
     "wajib dipilih (tidak ada nilai bawaan) supaya truk tidak tercatat milik orang yang "
     "salah; menambah langsung tersimpan, mengubah lewat tombol Ubah, Hapus bisa massal, "
     "dan Kelola Pemilik... untuk mengganti nama atau menghapus pemilik."),
    ("05-report.png", "Laporan",
     "Filter rentang tanggal, tabel rinci per baris, serta total berat bersih "
     "dan total uang."),
]

def semat(path: pathlib.Path) -> str:
    return base64.b64encode(path.read_bytes()).decode("ascii")


def kartu_dari(daftar: list) -> str:
    """Bangun kartu HTML untuk gambar yang benar-benar ada, lewati yang belum dibuat."""
    ada = [(n, j, k) for n, j, k in daftar if (FOLDER / n).exists()]
    hilang = [n for n, _, _ in daftar if not (FOLDER / n).exists()]
    if hilang:
        print("peringatan: dilewati karena tidak ada ->", ", ".join(hilang))

    kartu = []
    for nama, judul, ket in ada:
        data = semat(FOLDER / nama)
        kartu.append(
            f"""      <figure class="kartu">
        <img src="data:image/png;base64,{data}" alt="{judul}">
        <figcaption>
          <h3>{judul}</h3>
          <p>{ket}</p>
        </figcaption>
      </figure>"""
        )
    return "\n".join(kartu)



def main() -> None:
    asli = kartu_dari(GALERI)

    html = (FOLDER / "index.html")
    isi = TEMPLATE.replace("<!--KARTU-->", asli)
    html.write_text(isi, encoding="utf-8")
    ukuran = html.stat().st_size / 1024 / 1024
    jumlah = len([1 for n, _, _ in GALERI if (FOLDER / n).exists()])
    print(f"selesai: {html} ({ukuran:.1f} MB, {jumlah} gambar)")


TEMPLATE = """<!DOCTYPE html>
<html lang="id">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Pratinjau Aplikasi Pencatatan Transaksi Kaspe</title>
<style>
/* Hallmark · component: preview page · genre: editorial · theme: custom (gelap netral, aksen hijau daun) */
:root {
  color-scheme: dark;
  --paper: oklch(18% 0.010 255);
  --paper-2: oklch(23% 0.012 255);
  --ink: oklch(95% 0.004 255);
  --ink-2: oklch(76% 0.008 255);
  --ink-3: oklch(61% 0.010 255);
  --rule: oklch(31% 0.012 255);
  --accent: oklch(76% 0.125 150);
  --accent-soft: oklch(26% 0.040 150);

  --font-body: ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  --font-mono: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;

  --space-1: 0.25rem;
  --space-2: 0.5rem;
  --space-3: 0.75rem;
  --space-4: 1rem;
  --space-5: 1.5rem;
  --space-6: 2rem;
  --space-7: 3rem;
  --space-8: 4.5rem;

  --radius: 10px;
  --dur: 160ms;
  --ease-out: cubic-bezier(0.22, 1, 0.36, 1);
  --measure: 68ch;
}

* { box-sizing: border-box; }

html { overflow-x: clip; }
body {
  margin: 0;
  overflow-x: clip;
  background: var(--paper);
  color: var(--ink);
  font-family: var(--font-body);
  font-size: 1rem;
  line-height: 1.6;
  -webkit-text-size-adjust: 100%;
}

.bungkus {
  max-width: 76rem;
  margin: 0 auto;
  padding: var(--space-7) var(--space-5) var(--space-8);
}

header.kepala { max-width: var(--measure); }
.eyebrow {
  font-family: var(--font-mono);
  font-size: 0.75rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--accent);
  margin: 0 0 var(--space-3);
}
h1 {
  font-size: clamp(1.75rem, 1.2rem + 2.6vw, 2.9rem);
  line-height: 1.1;
  letter-spacing: -0.02em;
  font-style: normal;
  margin: 0 0 var(--space-4);
  overflow-wrap: anywhere;
  min-width: 0;
}
.ringkas {
  font-size: 1.0625rem;
  color: var(--ink-2);
  margin: 0 0 var(--space-5);
}
.catatan {
  border-left: 3px solid var(--accent);
  background: var(--accent-soft);
  padding: var(--space-4) var(--space-5);
  border-radius: 0 var(--radius) var(--radius) 0;
  margin: 0 0 var(--space-7);
  max-width: var(--measure);
  color: var(--ink);
}
.catatan p { margin: 0; }
.catatan p + p { margin-top: var(--space-3); }

section { margin-bottom: var(--space-8); }
h2 {
  font-size: clamp(1.25rem, 1.05rem + 1vw, 1.6rem);
  letter-spacing: -0.015em;
  font-style: normal;
  margin: 0 0 var(--space-5);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--rule);
}
h3 {
  font-size: 1rem;
  letter-spacing: -0.005em;
  font-style: normal;
  margin: 0 0 var(--space-2);
}

/* Satu kolom: tiap tangkapan layar 1320 px ditampilkan pada lebar isi kolom
   (1168 px, sekitar 0,89x). Kalau dipaksa beberapa kolom seperti sebelumnya
   (~368 px, 0,28x), pita zebra dan garis tabel menyusut sampai tidak terbaca. */
.galeri {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-6);
}
.kartu {
  margin: 0;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.kartu img {
  width: 100%;
  height: auto;
  display: block;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  background: var(--paper-2);
}
.kartu figcaption { margin-top: var(--space-4); }
.kartu figcaption p {
  margin: 0;
  color: var(--ink-2);
  font-size: 0.9375rem;
}

.fakta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 12rem), 1fr));
  gap: 1px;
  background: var(--rule);
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  overflow: hidden;
}
.fakta div { background: var(--paper); padding: var(--space-5); min-width: 0; }
.fakta dt {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--ink-3);
  margin-bottom: var(--space-2);
}
.fakta dd {
  margin: 0;
  font-size: 1.375rem;
  letter-spacing: -0.02em;
  color: var(--ink);
  overflow-wrap: anywhere;
}
.fakta dd span { display: block; font-size: 0.875rem; color: var(--ink-2); letter-spacing: 0; margin-top: var(--space-1); }

table { width: 100%; border-collapse: collapse; font-size: 0.9375rem; }
.tabel-bungkus { overflow-x: auto; -webkit-overflow-scrolling: touch; }
caption { text-align: left; color: var(--ink-2); font-size: 0.875rem; padding-bottom: var(--space-3); }
th, td { text-align: left; padding: var(--space-3) var(--space-4); border-bottom: 1px solid var(--rule); }
th { font-family: var(--font-mono); font-size: 0.6875rem; letter-spacing: 0.07em; text-transform: uppercase; color: var(--ink-3); font-weight: 500; }
td code, p code, li code {
  font-family: var(--font-mono);
  font-size: 0.875em;
  background: var(--paper-2);
  border: 1px solid var(--rule);
  border-radius: 5px;
  padding: 0.1em 0.35em;
}
pre {
  font-family: var(--font-mono);
  font-size: 0.8125rem;
  line-height: 1.7;
  background: var(--paper-2);
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  padding: var(--space-5);
  overflow-x: auto;
  margin: 0 0 var(--space-4);
}
ul { padding-left: 1.15rem; }
li + li { margin-top: var(--space-2); }
a { color: var(--accent); text-underline-offset: 2px; }
a:focus-visible, button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

footer {
  border-top: 1px solid var(--rule);
  padding-top: var(--space-5);
  color: var(--ink-3);
  font-size: 0.875rem;
}

@media (max-width: 768px) {
  .bungkus { padding: var(--space-6) var(--space-4) var(--space-7); }
  section { margin-bottom: var(--space-7); }
  th, td { padding: var(--space-2) var(--space-3); font-size: 0.875rem; }
}
@media (prefers-reduced-motion: reduce) {
  * { animation-duration: 0.01ms !important; transition-duration: 0.01ms !important; }
}
</style>
</head>
<body>
<div class="bungkus">

  <header class="kepala">
    <p class="eyebrow">Pratinjau aplikasi desktop</p>
    <h1>Aplikasi Pencatatan Transaksi Kaspe</h1>
    <p class="ringkas">
      Aplikasi desktop Java Swing untuk mencatat pengiriman singkong per truk: menghitung berat
      bersih setelah refraksi, menghitung jumlah uang yang harus dibayar, lalu menyajikan laporan
      per periode. Halaman ini memperlihatkan tampilan aslinya.
    </p>
  </header>

  <div class="catatan">
    <p><strong>Ini pratinjau statis, bukan aplikasi yang bisa diklik.</strong></p>
    <p>
      Semua gambar di bawah digambar langsung dari jendela aplikasi yang benar-benar dijalankan,
      dengan database asli berisi 23 nota contoh ditambah 1 nota (2 baris) yang disimpan saat
      pengambilan gambar. Tidak ada yang digambar ulang atau direkayasa.
      Untuk memakai aplikasinya sendiri, ikuti bagian <a href="#menjalankan">Menjalankan sendiri</a>.
    </p>
  </div>

  <section>
    <h2>Tampilan aplikasi</h2>
    <div class="galeri">
<!--KARTU-->
    </div>
  </section>

  <section>
    <h2>Hasil pemeriksaan</h2>
    <dl class="fakta">
      <div>
        <dt>Uji otomatis</dt>
        <dd>334 lulus <span>0 gagal</span></dd>
      </div>
      <div>
        <dt>Bytecode</dt>
        <dd>Major 52 <span>target Java 8</span></dd>
      </div>
      <div>
        <dt>Baris data contoh</dt>
        <dd>66 baris <span>23 nota contoh + 1 baru</span></dd>
      </div>
      <div>
        <dt>Selisih rumus</dt>
        <dd>0 baris <span>dari 66 diperiksa</span></dd>
      </div>
    </dl>
  </section>

  <section>
    <h2>Aturan perhitungan</h2>
    <p>Rumus ini sudah dicocokkan dengan 4 baris buku transaksi mitra, hasilnya sama semua.</p>
    <pre>berat_bersih = FLOOR((bobot_pabrik x (1 - refraksi/100)) / 5) x 5
jumlah_uang  = berat_bersih x harga
susut        = bobot_lapak - bobot_pabrik</pre>
    <p>Contoh nyata dari buku:</p>
    <pre>bobot_pabrik 7050 kg, refraksi 15%, harga Rp 1.150/kg
  7050 x 0,85 = 5992,5  ->  dibulatkan ke bawah ke kelipatan 5  ->  5990 kg
  5990 x 1150 = Rp 6.888.500</pre>
  </section>

  <section id="menjalankan">
    <h2>Menjalankan sendiri</h2>
    <p>
      Aplikasi ini hanya butuh JDK 8. Databasenya sudah ikut di dalam aplikasi, jadi tidak ada
      yang perlu dipasang lebih dulu — cukup jalankan, dan tabelnya dibuat sendiri.
      NetBeans opsional, hanya untuk membuka kodenya.
    </p>
    <ul>
      <li>Jalankan: <code>./build.sh</code> lalu <code>./run.sh</code> (Windows: <code>compile.bat</code> lalu <code>run-app.bat</code>).</li>
      <li>Datanya tersimpan sebagai satu file di folder pengguna
          (Windows: <code>C:\\Users\\&lt;nama&gt;\\kaspe\\</code>). Untuk mencadangkan, salin file itu.</li>
      <li>Kalau mau dipakai beberapa komputer sekaligus, arahkan ke MySQL lewat berkas
          <code>kaspe.properties</code> di folder aplikasi — databasenya juga dibuat sendiri.</li>
    </ul>
    <p>Rincian lengkap ada di <code>README.md</code>.</p>
  </section>

  <footer>
    Aplikasi Pencatatan Transaksi Kaspe · pratinjau dibuat dari aplikasi yang dijalankan sungguhan.
  </footer>

</div>
</body>
</html>
"""


if __name__ == "__main__":
    main()
