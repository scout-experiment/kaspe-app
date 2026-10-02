@echo off
REM Kompilasi aplikasi di Windows.
REM JAVA_HOME hanya diisi kalau memang belum ada, supaya tidak menimpa alamat JDK
REM yang sudah dipasang oleh program pemasang JDK.
if not defined JAVA_HOME set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.504.302-hotspot
cd /d "%~dp0"

if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo JAVA_HOME salah atau belum diisi: "%JAVA_HOME%"
    echo Folder itu tidak berisi JDK 8. Pasang JDK 8 lebih dulu, atau betulkan
    echo baris set JAVA_HOME di berkas ini. Lihat README bagian Memasang di Windows.
    pause
    exit /b 1
)

if exist build rmdir /s /q build
mkdir build
REM Setiap jalur diberi tanda kutip: javac memecah berkas @argfile di spasi,
REM jadi folder yang namanya mengandung spasi akan gagal kalau tidak dikutip.
(for /r src %%f in (*.java) do @echo "%%f") > sources.txt
REM -source/-target 1.8 dipatok supaya hasil kompilasi selalu bytecode Java 8 (major 52),
REM apa pun JDK yang dipakai mengompilasi.
"%JAVA_HOME%\bin\javac" -source 1.8 -target 1.8 -encoding UTF-8 -d build -cp "lib\*" @sources.txt
set HASIL=%ERRORLEVEL%
del sources.txt

if not "%HASIL%"=="0" (
    echo.
    echo Kompilasi GAGAL. Pesan galatnya ada di baris di atas.
    pause
    exit /b 1
)

REM Berkas pendukung ikut disalin, supaya hasil kompilasi bisa dijalankan sendiri.
copy /y src\kaspe.properties build\ >nul
copy /y src\kaspe\schema.sql build\kaspe\ >nul

echo Selesai. Class ada di build\
pause
