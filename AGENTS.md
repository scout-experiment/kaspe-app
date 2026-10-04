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
  `refreshMaster()` so the plate/owner lists follow Data Master without disturbing those rows.
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
| `src/kaspe/ui/` | `MainFrame`, `NavBar`, `PagePanel`, `HeaderBar`, `Icons`, `PanelDashboard`, `PanelTransaction`, `PanelMaster`, `PanelReport`, `PrintPreview`, `Theme` |
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
java  -cp "build:lib/*:/tmp/tools" BuatPratinjau          # renders real MainFrame → preview/*.png

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
- **Data Master is one master-detail page** (`PanelMaster`, no `Type` enum): rentals on the
  left, the selected rental's trucks on the right. The truck form shows the owner as a
  **label, never a combo** — that is deliberate. The old per-type pages had a rental combo
  that `DefaultComboBoxModel.addItem` auto-selected, so typing a plate and hitting save
  without touching the combo silently assigned the alphabetically-first rental. Never
  reintroduce a rental picker there; derive the owner from `rentalId` (the selected row) and
  keep the "pilih dulu pemiliknya di kiri" guard in `saveTruck`. Moving a truck to another
  owner goes through the explicit "Pindah Pemilik" button (`moveTruck` → `pindahTruk`), never
  through editing the plate, and `pindahTruk` MUST re-read the plate from the stored truck
  (`trukDari`) instead of taking it from `fPlat`: the box can be emptied, and an empty
  `String` satisfies `NOT NULL` — that once silently erased a truck's plate. `moveTruck` also
  refuses when `fPlat` differs from the stored plate, so a pending rename is never dropped
  silently. Never edit the plate: `saveTruck` always writes the *selected* rental, so typing a
  plate that exists under another owner would either be a no-op or a UNIQUE violation —
  and delete-then-re-add is not a substitute, because `transaksi_detail.id_truk` is
  `ON DELETE SET NULL` and the report reads the plate from `LEFT JOIN truk`, so old report
  rows would lose their plate.
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
- `src/kaspe/dao/TransactionDao.java` — `save()` (atomic header+detail), `updateDelivery`,
  `deleteDeliveries` (all-or-nothing), `listDeliveries` (flat, newest first), `listReport`,
  `totalAmount`, `totalNetWeight`, `summaryPerRental`.
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

`set -e` means the first failing class aborts the run. Expected baseline: `TestCalculator` 7,
`TestDatabase` 56, `TestDao` 58, `TestAlur` 96, `TestUi` 24 — **241 lulus, 0 gagal**.

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
  `MainFrame`) rather than eyeballing only.
- Sample-data numbers can be re-verified against the app formulas with `tools/PeriksaDataContoh.java`;
  a correct run prints `selisih berat = 0`, `selisih uang = 0`,
  `HASIL: cocok dengan rumus aplikasi`.
