#!/usr/bin/env bash
# Jalankan aplikasi. Database (H2) ikut aplikasi, tidak perlu dipasang dulu.
set -e
cd "$(dirname "$0")"
if [ -z "$JAVA_HOME" ]; then
  echo "JAVA_HOME belum diset. Contoh: export JAVA_HOME=/path/ke/jdk1.8.0_171"
  exit 1
fi
CP="build:lib/*"
"$JAVA_HOME/bin/java" -cp "$CP" kaspe.Main
