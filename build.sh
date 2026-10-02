#!/usr/bin/env bash
# Kompilasi aplikasi. Hasil ada di folder build/.
set -e
cd "$(dirname "$0")"
if [ -z "$JAVA_HOME" ]; then
  echo "JAVA_HOME belum diset. Contoh: export JAVA_HOME=/path/ke/jdk1.8.0_171"
  exit 1
fi
rm -rf build && mkdir -p build
find src -name '*.java' > sources.txt
# -source/-target 1.8 dipatok supaya hasil kompilasi selalu bytecode Java 8 (major 52),
# apa pun JDK yang dipakai mengompilasi. Tanpa ini, JDK yang lebih baru menghasilkan
# bytecode yang tidak bisa dibuka di komputer ber-JDK 8.
"$JAVA_HOME/bin/javac" -source 1.8 -target 1.8 -encoding UTF-8 -d build -cp "lib/*" @sources.txt
rm -f sources.txt

# Berkas pendukung ikut disalin, supaya hasil kompilasi bisa dijalankan sendiri
# tanpa menyertakan folder src/ lagi.
cp src/kaspe.properties build/
cp src/kaspe/schema.sql build/kaspe/

echo "Selesai. Class ada di build/"
