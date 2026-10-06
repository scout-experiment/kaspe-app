# Repository Guidelines

KaspeApp — desktop Java 8 Swing app for recording cassava truck deliveries: net weight after
refraction cut, amount payable, per-period report. Single operator, single machine, no login.
Replaces a paper ledger. Language split is a hard rule: **identifiers in English, comments /
Javadoc / docs / user-facing text in Indonesian, DB table and column names in Indonesian**
(matches the partner's ledger terms).

## Architecture & Data Flow

Layered, no frameworks, no DI container:

```
Main → kaspe.ui.* → kaspe.dao.* → kaspe.Db → H2 file DB (or MySQL/MariaDB)
                                              ↑ schema auto-created by kaspe.Schema
                                                from bundled /kaspe/schema.sql
```

- `Main.main` → `Theme.install()` (FlatLaf, must run before any component) →
  `SwingUtilities.invokeLater(new MainFrame().setVisible(true))`.
- Navigation is **not** a `CardLayout`: `PagePanel.showPanel(JPanel, String, String)` does
  `content.removeAll() / add / revalidate / repaint`. Each sidebar entry builds a fresh panel
  **except Transaksi**, which `NavBar` keeps and reuses: that page holds rows the operator has
  entered but not yet saved, and rebuilding it threw them away without warning. Reusing it calls
  `refreshMaster()` so the plate/owner lists follow the master data without disturbing those rows.
  **Data Master** is the other exception, in the opposite direction: its sidebar entry swaps no
  page at all — it opens the modal `DialogDataMaster.buka(owner)` ("Kelola Data Truk") over
  whatever page is on screen, so the page below keeps its state.
  The shell is `NavBar` (sidebar, `WEST`) + `PagePanel` (header bar + content). Both are
  standalone builders — `NavBar.build(page)` — so `tools/BuatPratinjau.java` and `TestUi`
  can rebuild the real shell headlessly. Never inline them into `MainFrame`: preview PNGs
  would silently stop representing the app.
- Table column widths in `PanelReport` are calibrated against the default 1320×760 window
  *with* the sidebar; `TestUi` has a guard that measures the widest text per column and fails
  if any column is too narrow. The report table is the printed artifact, so a clipped column
  clips on paper too (`FIT_WIDTH` scales the table as-is).
- Panels construct DAOs inline (`private final MasterDao dao = new MasterDao();`) and call them
  **synchronously on the EDT**. There is no `SwingWorker`, no background thread, no service layer.
- Every DAO method opens its own `DriverManager` connection via `Db.get()` and closes it with
  try-with-resources. No pool, no `DataSource`, autocommit except in `TransactionDao.save`.
- Master delete guards are exposed as PURE accessors: `MasterDao.truckDeleteRefusal(id)` /
  `rentalDeleteRefusal(id)` return the message or null, and the delete methods throw using the
  same string. `DialogDataMaster` and `DialogPemilik` ask the accessor BEFORE showing their
  confirmation dialogs, so they never call a deleting method just to harvest its refusal —
  a read path must not be able to delete.
- Deleting a truck or rental is REFUSED while it still has history
  (`MasterDao.deleteTruck`/`deleteRental` throw `IllegalStateException` naming the affected
  count). Both foreign keys are `ON DELETE SET NULL`, so a delete would not fail — it would
  silently null `transaksi_detail.id_truk` / `truk.id_rental` on historical rows, and
  `listReport` reads plate and owner through LEFT JOINs, so past reports and their printed
  paper lose them permanently. `DialogDataMaster` and `DialogPemilik` show the refusal as an
  information dialog, not through `Theme.showError`.
- Rental and truck rows are created by `MasterDao.pastikanTruk(Connection, plate, name)`
  **inside** the transaction of `TransactionDao.save`/`updateDelivery` — never while a form is
  still being typed. Creating them earlier, from the transaction screen as the operator typed,
  littered Data Master with ghost rentals and trucks from entries that were abandoned or rejected,
  and split the per-owner money summary.
- One submitted delivery is one record: `PanelTransaction` writes on Simpan with no in-memory
  staging, and its saved list is a flat per-delivery list (`TransactionDao.listDeliveries`,
  newest first) whose hidden column 0 holds `id_detail`. Row identity must be read from the
  table model, never from the screen row index — a date/rental filter or a sort would otherwise
  make edit/delete hit a different record.
- `Db` loads config in a static block, precedence: external `kaspe.properties` beside the jar →
  classpath `/kaspe.properties` → hardcoded defaults. It also memoizes "driver loaded" and
  "schema ensured" per JDBC URL.
- Default DB is embedded H2 in file mode:
  `jdbc:h2:${user.home}/kaspe/db_kaspe;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`.
  `MODE=MySQL` is why `src/kaspe/schema.sql` and all inline SQL must stay H2 **and** MySQL
  compatible.

Calculation rules live **only** in `src/kaspe/Calculator.java` (pure static `BigDecimal`):

```
berat_bersih = FLOOR((bobot_pabrik x (1 - refraksi/100)) / 5) x 5   # WEIGHT_MULTIPLE = 5
jumlah_uang  = berat_bersih x harga                                 # scale 0, HALF_UP
susut        = bobot_lapak - bobot_pabrik                           # monitoring only
```

Never re-implement these inline in UI or DAO code; call `Calculator` so UI, reports and
`tools/*.java` cannot drift apart.

## Key Directories

| Path | Purpose |
|------|---------|
| `src/kaspe/` | Core: `Main`, `Db` (connection/config), `Schema` (auto table creation), `Calculator` |
| `src/kaspe/model/` | Plain beans: `Rental`, `Truck`, `Transaction`, `TransactionDetail`, `ReportRow` |
| `src/kaspe/dao/` | `MasterDao` (rental, truck), `TransactionDao` (transactions, reports, totals) |
| `src/kaspe/ui/` | `MainFrame`, `NavBar`, `PagePanel`, `HeaderBar`, `Icons`, `PanelDashboard`, `PanelTransaction`, `DialogDataMaster`, `DialogPemilik`, `PanelReport`, `PrintPreview`, `Theme` |
| `src/kaspe/util/` | `Dates` (display `dd-MM-yyyy`, lenient parse) |
| `src/kaspe/test/` | Hand-rolled test harness (no JUnit) |
| `src/kaspe/schema.sql` | Bundled DDL + `v_transaksi` view; run by the app at first start |
| `src/kaspe.properties` | Bundled DB config (H2 active, MySQL block commented) |
| `docs/` | `specification.md` (system spec), `data-contoh.sql` (sample data, 64 deliveries already in 1:1 shape) |
| `tools/` | Demo helpers, **not** part of the app (see below) |
| `preview/` | Static browser preview page + PNGs, generated artifacts |
| `lib/` | Vendored jars: `flatlaf-3.7.2.jar`, `flatlaf-fonts-inter-3.19.jar`, `h2-2.1.214.jar`. `mysql-connector-j-9.1.0.jar` is GPLv2 and is deliberately NOT published (gitignored, absent from `javac.classpath` and `manifest.mf`); MySQL mode needs the user to drop it into `lib/` themselves. It is runtime-only (`Db` loads it via `Class.forName`), so nothing breaks without it. |
| `nbproject/`, `build.xml` | NetBeans/Ant project files; `build-impl.xml` and `genfiles.properties` are generated — never hand-edit |
| `build/`, `dist/` | Build output, regenerate; never edit |

## Development Commands

JDK 8 is the target (`javac.source=javac.target=1.8` in Ant, `-source 1.8 -target 1.8` in
`build.sh`/`test.sh`/`compile.bat`, bytecode major 52; tested on Temurin 8u504). The scripts pin
the flags so the artifact runs on JDK 8 even when a newer JDK compiles it — verified: JDK 21
compiles and runs the whole suite green, but unpinned it emits major 65, which JDK 8 rejects.
All shell scripts `exit 1` if `JAVA_HOME` is unset. Ant is **not** part of the daily
loop — the scripts call `javac`/`java` directly.

```bash
export JAVA_HOME=/path/to/jdk1.8.0_171

./build.sh     # rm -rf build; javac -source 1.8 -target 1.8 -encoding UTF-8 -d build
               #   -cp "lib/*" @sources.txt;
               # copies src/kaspe.properties → build/ and src/kaspe/schema.sql → build/kaspe/
./run.sh       # java -cp "build:lib/*" kaspe.Main
./test.sh      # recompiles, then runs the 5 test classes in order (TestUi headless)
```

Windows: `compile.bat`, then `run-app.bat`. Both use `JAVA_HOME` if it is already set (the
Temurin MSI sets it) and only then fall back to a hardcoded path; both fail loudly instead of
reporting success when the JDK is wrong or the compile fails.

NetBeans (optional): F6 run, Shift+F11 clean+build, CLI `ant compile | jar | run | clean`. Any
NetBeans version works as long as JDK 8 is registered as a Java Platform; do not tell users to
install NetBeans 8.0.2, it has no official download source anymore. NetBeans 12.5 is the last
release whose binaries run on JDK 8 (12.6 requires JDK 11+). `dist/KaspeApp.jar` needs a
`lib/` folder beside it — the manifest has `Class-Path: lib/...`, so the jar alone dies on
double-click. That `lib/` copy is done by `libs.CopyLibs.classpath`, which is referenced in
`build-impl.xml` but defined nowhere in the repo (no `nbproject/private/`); NetBeans normally
supplies it on project open, but plain CLI `ant jar` will not, so verify `dist/lib/` exists or
copy `lib/` by hand.

Demo/preview helpers (run after `./build.sh`):

```bash
javac -cp "build:lib/*" -d /tmp/tools tools/BuatPratinjau.java
java  -cp "build:lib/*:/tmp/tools" BuatPratinjau          # renders real shell + panels → preview/*.png

python3 preview/build-preview.py                          # rebuilds preview/index.html from those PNGs

javac -cp build -d /tmp/tools tools/BuatDataContoh.java
java  -cp "build:/tmp/tools" BuatDataContoh docs/data-contoh.sql
javac -cp "build:lib/*" -d /tmp/tools tools/PeriksaDataContoh.java
java  -cp "build:lib/*:/tmp/tools" PeriksaDataContoh      # verifies sample data against Calculator

javac -cp "build:lib/*" -d /tmp/tools tools/PeriksaData.java
java  -cp "build:lib/*:/tmp/tools" PeriksaData            # read-only check of the USER's own database
```

`PeriksaData` opens its own connection instead of `Db.get()`, because `Db.get()` runs
`Schema.ensure` and would modify the very database being inspected. It appends `IFEXISTS=TRUE`
on H2 so a missing database is reported rather than silently created — otherwise it would
inspect an empty file and print "no damage". It prints the URL it reads. Sections 1 and 2
(owners/plates with no deliveries yet) are notes, not problems: a newly registered owner is
normal, and telling the operator to delete one would be dangerous advice. Without
`mysql-connector-j` in `lib/` it can only inspect H2.

`preview/index.html` and `preview/*.png` are generated — do not hand-edit them. `docs/data-contoh.sql`
starts with `DELETE`, so it wipes the target database.

## Code Conventions & Common Patterns

- **Java 8 API only.** No `var`, no `List.of`, no records, no `java.nio.file` shortcuts beyond 8.
- **A column header's alignment follows its cells': text columns left, numeric columns right.**
  `Theme.alignRight` sets BOTH the cell and the header renderer, and `Theme.emphasis` re-asserts the
  header for the money column so it cannot drift. Two wrong states were tried and both looked wrong:
  headers left with numeric cells right leaves the header sharing no edge with its values, so it reads
  as a detached line; and everything left makes values of different lengths ragged on the right, which
  is exactly what hides a small amount among large ones. `TestUi` compares header alignment against
  cell alignment on every visible column, because either mistake is otherwise silent.
- **The result strip and the Simpan button are one unit.** Both live in `buildRightColumn`; the strip
  is `Theme.strip()` and the button sits below it indented by `Theme.STRIP_INSET`, so the button's left
  edge lines up with the *text* in the strip rather than with the strip's box. The two numbers in the
  strip (Berat Bersih, Jumlah Uang) use the SAME label helper and therefore the same size and weight —
  one of them being larger made a single figure look more important than its neighbour inside the same
  box, which they are not. `TestUi` checks both the alignment and the font equality.
- **Units live in the cells, not the column headers** (`Calculator.formatKg`, `formatPercent`) — the
  header text is what sets a column's width, so a "(kg)" in the header widens three columns at once,
  while the same unit inside the cell costs nothing because the header stays the longest string. This
  matches the money columns, which already write "Rp" in each cell. `TestUi`'s worst-case map must
  therefore hold the cell text *with* its unit, or it measures a shorter string than what is drawn.
- **Money and weights are always `BigDecimal`**, never `double`; columns are `DECIMAL`.
  Display money with `Calculator.formatCurrency` (`6888500` → `"6.888.500"`).
- **Dates**: `LocalDate` in code, `java.sql.Date` only at the JDBC boundary, `util.Dates.format/parse`
  for UI (`Dates.parse` returns `null` instead of throwing).
- **Error handling**: DAO methods declare `throws SQLException`. Validation throws
  `IllegalArgumentException` with an Indonesian message (`"bobot pabrik dan refraksi wajib diisi"`).
  UI funnels everything through `showError(Exception e)` → `JOptionPane` `ERROR_MESSAGE` with
  `"Gagal: " + e.getMessage()`. `Db` translates H2 lock/permission failures into readable
  Indonesian messages.
- **Schema changes**: `CREATE TABLE IF NOT EXISTS` only applies to brand-new databases, so a
  column removed from `schema.sql` survives forever in an existing database. `Schema.ensure`
  therefore also calls `dropObsoleteColumns` — when you delete a column, add it there, and
  `TestDatabase.checkObsoleteColumnsDropped` covers the migration.
- **Data Master is a dialog, not a page** (`DialogDataMaster`, no `Type` enum). The sidebar
  entry opens it with `buka(Window)` as a modal "Kelola Data Truk" dialog; the class itself is
  the `JPanel` content, so `TestUi` and `tools/BuatPratinjau.java` can build it headlessly —
  the same split `PagePanel` uses against `MainFrame`. Shape: ONE table of all trucks
  (Plat | Rental, multi-select) plus ONE input row. "Tambah Truk" INSERTs immediately — there
  is no idle Simpan button, so nothing can sit half-entered. Editing is selection-driven:
  pick exactly one row, press "Ubah", the row fills the input box and the buttons become
  "Simpan Perubahan"/"Batal"; the owner shows as a **label, never a combo** in that mode, and
  `simpanPerubahan` pins it to the STORED `rentalId` — saving runs
  `UPDATE truk SET plat=?, id_rental=?` as one statement, so an editable owner would turn a
  plate typo fix into an unconfirmed move. The owner combo in add mode is **empty by default**
  and "Tambah Truk" refuses while it is empty — that is deliberate: the old per-type pages had
  a rental combo that `DefaultComboBoxModel.addItem` auto-selected, so typing a plate and
  hitting save without touching the combo silently assigned the alphabetically-first rental.
  `muat()` must keep force-clearing it (`setSelectedIndex(-1)` + empty editor item) whenever
  no choice was pending, because a `JComboBox` selects its first item by itself the moment it
  is filled. A new owner typed there is resolved through `pastikanRental` with
  `Rental.matchKey`, never `WHERE nama=?`, so a different spelling cannot mint a duplicate
  owner that splits the per-owner money summary. Moving a truck to another owner is its own
  confirmed "Pindah Pemilik" button (`pindahPemilik` → `pindahTruk`), never a side effect of
  editing the plate: moving rewrites every past report row's owner, so two intents must not
  share one button. `pindahTruk` MUST re-read the plate from the stored truck, never from
  `fPlat`: the box can be emptied, and an empty `String` satisfies `NOT NULL` — that once
  silently erased a truck's plate. "Hapus" is bulk (multi-select) and consults
  `truckDeleteRefusal` for EVERY selected row BEFORE deleting any — the refusal set comes
  from the package-private `platTerhalang(List<Truck>)`, deliberately returning a list rather
  than showing the message itself, because a `JOptionPane` throws under `HeadlessException`
  and a guard that cannot observe the outcome is no guard at all; each
  `MasterDao.deleteTruck` call opens its own connection and commits on its own, so
  one-by-one deletion is NOT atomic — if row 3 is refused after rows 1–2 are gone, the
  operator cannot reconstruct the half-deleted list. One refusal aborts the whole batch. The
  "×" button beside the plate box deletes whichever truck the typed plate matches (same
  refusal + confirmation path). "Kelola Pemilik..." in the footer opens `DialogPemilik`
  (add / rename / delete owners); renames propagate through the join so history follows, and
  deletes are refused while the owner still owns trucks. Adding a truck goes through
  `MasterDao.simpanTrukBaru(plat, nama)` — ONE transaction that checks the duplicate plate,
  finds-or-creates the owner and inserts the truck, rolling back on any failure. Doing it in
  pieces does not work: `saveRental` commits on its own connection, so creating the owner
  first and then failing on a duplicate plate leaves an owner with no trucks, and checking
  the plate in memory first does not close it either, because the app may be run from
  several machines over MySQL. Note it REFUSES an existing plate rather than reusing the
  truck the way `pastikanTruk` does — on this screen a duplicate is a typo that must be
  heard, not a silent request to reuse the existing row.
- **Dashboard is four stat cards, two rows of two, pinned to the top**: the big number keeps
  its all-time meaning and the month-to-date figure (`totalAmount(withDayOfMonth(1), now)`)
  goes in the caption — do not move the month figure into the headline, since changing a
  number's meaning without changing its title is what causes misreads.
  `TestUi.angkaBulanBerjalan` pins the month boundary. Two things were tried and rejected:
  a per-owner recap (`summaryPerRental`, still used by `TestDao` only) because a two-column
  table stretched across the page left the name and its amount at opposite ends of a
  mostly-empty row; and a monthly bar chart, because the app is ~3 months old, so the chart
  would show three bars and the current month would always look like a collapse. The cards
  must stay pinned NORTH with natural height: filling the leftover space stretches them and
  the 44px icon badge turns into a long stripe.
- **Typed plate / rental**: both combos on the transaction screen are editable, so a plate or
  rental never seen before is created on the spot instead of requiring a trip to Data Master.
  Matching is spelling-aware, never `WHERE x=?`: `Truck.normalizePlate` / `Rental.matchKey` +
  `MasterDao.cariIdTruk` / `cariIdRental` read all rows and compare in Java (portable across H2
  and MySQL). `Truck.setPlate` uppercases because plates are printed uppercase; `Rental` only
  collapses whitespace, because "CV MITRA TANI" reads badly in a report — case-insensitivity
  lives in `matchKey` instead. `ReportRow.setPlate` normalizes too, since report rows are read
  raw from the join. Never resolve these combos with `getSelectedItem()`: the typed text lives in
  `getEditor().getItem()`.
- **SQL**: DDL lives only in `src/kaspe/schema.sql` (idempotent `CREATE TABLE IF NOT EXISTS`,
  no vendor-specific syntax). CRUD SQL is inline in DAOs, `PreparedStatement` for writes,
  `Statement` for reads, one try-with-resources block per call. Multi-row writes use one
  connection with `setAutoCommit(false)` / `commit` / `rollback` in catch / restore in `finally`.
- **Table styling is centralized in `Theme`** — the only per-table decisions allowed are column
  widths and which columns are right-aligned/fixed:

  ```java
  Theme.styleTable(table);                     // row height, gridlines, zebra renderer, header
  Theme.widths(table, 44, 200, 140, 380);
  Theme.fixedWidth(table, 0, 44);              // pin ID / No columns
  Theme.alignRight(table, 4, 5, 6, 7, 9, 10);  // numeric columns
  ```

  Tables sit inside `Theme.card()` + `JScrollPane` with an emptied border. Zebra striping must stay
  visibly contrasting (`Theme.ROW_ALT`) and gridlines on both axes — faint stripes read as invisible
  and the report table is printed in grayscale.
- **All appearance values (colors, `FONT_SIZE`, `ROW_HEIGHT`, `CARD_ARC`) live in `Theme`.** Never
  hardcode a color or font in a panel; use `Theme.primary/plain/label/card/applyCard`.
- **Forms**: `GridBagLayout` with `anchor = WEST`, fields declared as `final` class fields prefixed
  `txt`/`cmb`/`sp`/`lbl`, labels always `Theme.label(text)`. Live recalculation uses a shared
  `DocumentListener` → `recalculate()` (no calculate button).
- **`PanelTransaction` input card layout**: the grid is TWO rows of FOUR columns, and the left edge
  of every field lines up top to bottom — so adding a field means adding a column to both rows, not
  appending to one. Inputs live in the grid; the live result strip (Berat Bersih, Jumlah Uang) and
  the Simpan/Batal buttons sit in a right-hand column (`buildRightColumn`) whose width is set by its
  contents, so a longer button label ("Simpan Perubahan") can never be clipped. Simpan is the only
  wide action button there; Batal is hidden outside edit mode. Do not add a button to a row that can
  overflow — a `FlowLayout` row that runs out of width wraps its last button to a second line that
  the card then clips, and `TestUi`'s "tombol tidak terpotong" check exists to catch exactly that.
- **`MainFrame.LEBAR_MINIMUM` is load-bearing and measured, not guessed.** The floor is set by the
  two fixed-width tables — the saved-deliveries list and the Laporan table — because a `JTable` with
  default auto-resize *squeezes* its columns rather than scrolling, and on Laporan that squeeze
  reaches paper (`FIT_WIDTH` printing shrinks the table as-is). 1290px is the measured smallest width
  that still shows every column whole — including the sort arrow's 10px inside a sorted header cell;
  the constant carries a little slack on top. It also has to
  cover the transaction form's right-hand column, which cannot fold. Anything that widens a fixed
  table, a field, or that column means raising `LEBAR_MINIMUM`, and `TestUi` lays every page out at
  exactly that width so the drift fails the suite instead of shipping. Note the tables are the
  binding constraint — the form is not.
- **`lblStatus` lives in the input card's SOUTH**, full card width, hidden when empty. It carries
  validation messages, so it must stay next to the fields and the button that produced it — moving
  it to the panel bottom detaches a "bobot pabrik dan refraksi wajib diisi" from the Simpan the
  operator just pressed, and no render or test covers that error state.
- **Sortable tables compare VALUES, not text.** Both the Laporan table and the saved-deliveries list
  turn sorting on per table with `setAutoCreateRowSorter(true)`, and must then install
  `Theme.sortAngka` / `Theme.sortTanggal` / `Theme.sortTeks` for their numeric, date and text columns.
  Cells already carry their units ("Rp 6.888.500", "6.350 kg", "05-10-2026"), and with no comparator
  installed `DefaultRowSorter` compares `toString()` with a **locale-dependent `Collator`**, so
  "Rp 10.000.000" sorts *before* "Rp 6.888.500". Every value stays readable, so nothing else catches
  it. `sortTeks` exists because that same locale dependence makes Plat/Rental order differ between
  computers — the reason `Dates` writes its own day/month names. Two further traps: an empty cell is
  real (an unpaid row has no Tgl Lunas) and must not blow up the comparator, and
  `Theme.pengurut` creates the sorter itself, so the comparator calls no longer depend on
  `setAutoCreateRowSorter(true)` having run first — that order used to drop them silently — and any code reading
  `getSelectedRow()` / table row indexes must go through `convertRowIndexToModel`. That is why
  `Theme.styleTable` keeps `setAutoCreateRowSorter(false)` and the two panels opt in individually.
- **Column widths include room for the sort arrow.** The arrow icon is 10px and lives inside the
  header cell, so the four columns whose header text was calibrated to fit exactly (Bobot Lapak,
  Bobot Pabrik, Refraksi, Berat Bersih) were truncated the moment the column was sorted. Their widths
  now carry the arrow's space, which is what moved `LEBAR_MINIMUM` to 1300 (the measured floor is
  1290; the constant carries 10px of slack). `TestUi` measures every header's preferred width against
  its column width with the icon installed, so a width that only fits without the arrow fails.
  The floor is now within 20px of the default window size, which is the real signal: ten columns each
  holding a header, a unit and a worst-case number have run out of room. Adding a column to the
  on-screen table would push `LEBAR_MINIMUM` past the default — treat that as the cue to leave the
  column off the screen (Susut lives only in the CSV for exactly this reason).
- **`Theme.emphasis` / `HeaderRenderer` are the only places that style a header cell.** The sort
  arrow is not inherited: replacing `TableHeader.defaultRenderer` discards the JDK's
  `DefaultTableCellHeaderRenderer`, which is what picks the arrow icon. `HeaderRenderer` sets it
  itself from `RowSorter` + `Table.ascendingSortIcon`/`Table.descendingSortIcon`, so the arrow keeps
  following the theme. Drop that line and a sorted column shows no arrow at all — the order looks
  unchanged, so the click reads as broken.
- **The two figures in a summary row are equals.** In the form's result strip and in the Laporan
  footer, the weight and the money total use the SAME font (and, in the footer, the same
  `Theme.MONEY` colour — green there means "this is a computed figure", not "this is money"). Making
  the money total larger made one figure look more important than its neighbour in the same box.
  The row count is deliberately NOT sized up: it would read as a third total. The Simpan button is
  indented by `Theme.STRIP_INSET` so its left edge lines up with the *text* in the strip above it.
- **Laporan's "Ekspor CSV" and "Rekap per rental" sit in the totals row, not the filter row.** The
  filter row already asks for 1008px of the 1070px the card gives it at `LEBAR_MINIMUM`, leaving ~62px; a
  button added there wraps and
  gets clipped. Quick date ranges therefore live on a *second* row of the filter card, never appended
  to the first. The CSV carries raw numbers (no "Rp", no thousands separator, `;` delimiter for
  Indonesian Excel) taken from the raw `ReportRow` list in current view order — the table cells hold
  unit-suffixed strings that cannot be summed. It also carries a Susut column, which the on-screen
  table has no room for: adding it would push `LEBAR_MINIMUM` past the 1320px default window, and the
  figure is derivable from the two weight columns anyway. A file has no width budget, so it goes there.
  `PanelReport.isiCsv()` and `PanelReport.rekapPerRental()` are package-private precisely so `TestUi`
  can assert their contents against what is on screen.
- **The CSV states its own coverage, and never silently overwrites.** `isiCsv()` starts with the title
  and `cakupanTabel()` — the same period + rental/plat words as the print footer — because the file is
  the same document as the paper and is used to hand over money; a partial export that does not name
  its scope is indistinguishable from a full one. It also records `urutTabel()`, since the row order
  is whatever the operator clicked. The default filename uses the *applied period*, not the export
  date, and an existing file is confirmed before writing: `JFileChooser.showSaveDialog` does NOT ask
  about overwriting (unlike the native dialog), which would break the no-clobber promise the backup
  feature already makes.
- **`rekapPerRental()` groups the rows already on screen, not a fresh query.** `TransactionDao` has a
  `summaryPerRental` that takes only dates, so using it here would recap *every* rental while the table
  above shows one — a total larger than the rows it sits under, which is exactly how money gets handed
  over wrong. Grouping the filtered list in the UI cannot drift that way.
- **The print footer and the CSV state the same coverage, and the row order.** `kakiCetak()` and
  `isiCsv()` both start from `cakupanTabel()` (period + rental/plat) and add `urutTabel()` when the
  table is sorted — the print path draws the table as-is, so the paper's order is whatever the operator
  clicked. Both are used to hand over money, so a partial document that does not name its scope is
  indistinguishable from a full one.
- **A guard must be able to fail, and must fail as a report.** Every `check(...)` added for the above
  was verified by deleting the fix and watching it go red. Two lessons that cost real time:
  a guard comparing the *rows' own order* proves nothing about locale-independence (on the sample
  names, locale order coincides with code-point order) — it has to call the comparator directly with
  a pair that differs, e.g. `("BE 1", "BE1")`, which a `Collator` orders one way and
  `compareToIgnoreCase` the other. And a guard that lets a comparator's exception escape turns into a
  crash that stops the suite before `=== HASIL ===`, which reads like a broken test rather than a
  caught defect; `TestUi.urutkan(tabel, kolom, arah)` wraps every `setSortKeys` for that reason.
- **Models**: plain beans, getters/setters, `toString()` used for combo display. Read-only tables use
  an anonymous `DefaultTableModel` overriding `isCellEditable → false`.
- **UI text and Javadoc in Indonesian**, one-line Javadoc per class.

## Important Files

- `src/kaspe/Main.java` — entry point (`Theme.install()` then `MainFrame`).
- `src/kaspe/Db.java` — config loading, connection factory, H2 error translation, MySQL bootstrap.
- `src/kaspe/Schema.java` — `ensure(Connection)`, `readStatements()`, `tableExists()`,
  `columnExists()`, `dropObsoleteColumns()`, `splitSharedHeaders()`; the same SQL
  parser is used by tests so tested SQL equals runtime SQL. `splitSharedHeaders` runs at every
  start and normalises a legacy database so one `transaksi` header owns at most one detail —
  it is transactional, verifies detail count and `SUM(jumlah_uang)` before committing, and
  rolls back and refuses to start on any mismatch.
- `src/kaspe/Calculator.java` — the only place the business formulas exist.
- `src/kaspe/schema.sql` — DDL + `v_transaksi` (includes `susut = bobot_lapak - bobot_pabrik`).
- `src/kaspe/dao/BackupDao.java` — `cadangkan()`: H2's own `BACKUP TO` (not a file copy, which
  can snapshot a live database inconsistently), timestamped target in a `cadangan` folder, and
  a clear Indonesian refusal on MySQL/MariaDB where backup is the server's job. Note H2 refuses
  `BACKUP TO` on an in-memory database, so the suite can only exercise the real zip by pointing
  `Db.setConfiguration` at a file-based H2 in a temp dir — `TestDao` does exactly that.
- `src/kaspe/Db.java` — `configError()` returns a refusal message when a config file is PRESENT
  but yields no `db.url`; `Main` prints it to stderr (plus a dialog when not headless) and exits
  without touching the database, and `Db.get()` throws the same message as its first statement so
  no other entry point can slip past. A config file that is ABSENT still means the built-in H2
  default — that is the intended default, not an error. `setConfiguration` clears the error, which
  is what keeps the test/tool hook working.
- `src/kaspe/dao/TransactionDao.java` — `save()` (atomic header+detail), `updateDelivery`,
  `deleteDeliveries` (all-or-nothing), `listDeliveries` (flat, newest first, with date/rental/plate
  filters), `listReport`, `totalAmount`, `totalNetWeight`, `summaryPerRental`. In
  `listDeliveries` the DATE bounds are filtered in SQL (indexed) but the rental and plate
  predicates are applied in Java through `Rental.matchKey` / `Truck.normalizePlate` — the same
  reason `MasterDao.cariIdTruk` matches plates in Java: an older database can hold legacy
  spellings such as `be  7777  hd`, and a SQL `LIKE` on the raw column misses them even though
  the row reads normally on screen, so the operator concludes the record does not exist and
  enters it a second time.
- `src/kaspe/ui/Theme.java` — single styling entry point; changing the look means editing here only.
- `nbproject/project.properties` — `main.class=kaspe.Main`, Java level, output dirs.

## Runtime/Tooling Preferences

- JDK 8 (Temurin 8u504 verified) is the supported target and what `JAVA_HOME` should point at.
  Newer JDKs also compile and run it (JDK 21 verified green on all 4 suites) but are untested;
  the scripts pin `-source/-target 1.8` so the output stays runnable on JDK 8 either way.
- Build is plain `javac` + `java` via the shell scripts, or Ant/NetBeans. No Maven, no Gradle,
  no CI config in the repo.
- Dependencies are vendored jars in `lib/` and are on the manifest `Class-Path`. **Do not add new
  dependencies** — the project deliberately uses built-in Java printing instead of JasperReports and
  an embedded DB instead of an installed one.
- `python3` (stdlib only) is used by `preview/build-preview.py`.
- No NetBeans or JDK is needed to view `preview/index.html`; it is a static, self-contained page.

## Testing & QA

Hand-rolled harness, no JUnit/TestNG anywhere (the only JUnit strings are unused NetBeans
boilerplate). Each test class is a plain `public static void main(String[])` with static
`passed`/`failed` counters, a `record(boolean ok, String name)` helper, `System.out.printf`
lines ending in `OK` / `SALAH` / `GAGAL`, a final `=== HASIL: N lulus, M gagal ===`, and
`System.exit(1)` when anything failed. `./test.sh` compiles all of `src/` then runs, in order:

```bash
CP="build:lib/*"
"$JAVA_HOME/bin/java" -cp "$CP" kaspe.test.TestCalculator
"$JAVA_HOME/bin/java" -cp "$CP" kaspe.test.TestDatabase
"$JAVA_HOME/bin/java" -cp "$CP" kaspe.test.TestDao
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "$CP" kaspe.test.TestAlur
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "$CP" kaspe.test.TestUi
```

`set -e` means the first failing class aborts the run. Expected baseline: `TestCalculator` 8,
`TestDatabase` 56, `TestDao` 91, `TestAlur` 115, `TestUi` 61 — **331 lulus, 0 gagal**.

- Tests use in-memory H2 only (`mem:kaspe`, `mem:daotest`, `mem:uitest`) and configure it via the
  test hook `Db.setConfiguration(driver, url, user, pass)`; they never touch the user's real
  database file.
- `TestUi` is a headless render smoke test: it installs `Theme`, builds each panel, paints it into a
  `BufferedImage`, fails if fewer than 500 sampled pixels differ from `Theme.CANVAS`, and writes
  `build/screenshots/*.png`. It does not test `MainFrame`, events, or table content.
  It also carries non-image guards that a blank-image check cannot catch: the report and transaction columns fit
  the default window (`kolomTabelUtuh`), the sidebar menu rows actually have a size
  (`barisMenuTergambar`), the long date is Indonesian and weekday-correct (`tanggalPanjang`),
  and the header bar shows the page name, not the app name (`headerMenulisNamaHalaman`).
  `pilihBarisMaster` catches panels that read a table by column index after the column
  layout changed — the image checks pass while clicking a row throws.
- Add new checks inside the existing class's `main` using `record(...)`; do not introduce a test
  framework. `ant test` is a no-op here (no `test/` source root, no JUnit jar) — always use `./test.sh`.
- UI/visual changes: verify with `./test.sh` plus `tools/BuatPratinjau.java` (renders the real
  shell and panels — the master dialog as its panel, since a `JDialog` needs a screen) rather
  than eyeballing only.
- Sample-data numbers can be re-verified against the app formulas with `tools/PeriksaDataContoh.java`;
  a correct run prints `selisih berat = 0`, `selisih uang = 0`,
  `HASIL: cocok dengan rumus aplikasi`.
