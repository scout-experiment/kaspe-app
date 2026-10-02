@echo off
REM Jalankan aplikasi di Windows.
REM JAVA_HOME hanya diisi kalau memang belum ada, supaya tidak menimpa alamat JDK
REM yang sudah dipasang oleh program pemasang JDK.
if not defined JAVA_HOME set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.504.302-hotspot
cd /d "%~dp0"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JAVA_HOME salah atau belum diisi: "%JAVA_HOME%"
    echo Pasang JDK 8 lebih dulu, atau betulkan baris set JAVA_HOME di berkas ini.
    echo Lihat README bagian Memasang di Windows.
    pause
    exit /b 1
)

if not exist build\kaspe\Main.class (
    echo Aplikasi belum dikompilasi. Klik dua kali compile.bat dulu.
    pause
    exit /b 1
)

"%JAVA_HOME%\bin\java" -cp "build;lib\*" kaspe.Main
pause
