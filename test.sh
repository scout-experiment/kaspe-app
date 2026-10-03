#!/usr/bin/env bash
# Jalankan semua uji otomatis (pakai H2 di memori, tidak menyentuh data asli).
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
cp src/kaspe.properties build/
cp src/kaspe/schema.sql build/kaspe/

CP="build:lib/*"
echo
"$JAVA_HOME/bin/java" -cp "$CP" kaspe.test.TestCalculator
echo
"$JAVA_HOME/bin/java" -cp "$CP" kaspe.test.TestDatabase
echo
"$JAVA_HOME/bin/java" -cp "$CP" kaspe.test.TestDao
echo
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "$CP" kaspe.test.TestAlur
echo
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "$CP" kaspe.test.TestUi
