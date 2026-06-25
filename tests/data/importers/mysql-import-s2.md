# S2 Public Dataset Import Plan

S2 extends the S1 public-data import path with a repeatable large local sample.
It does not download public data. By default it reads the local raw metadata
file:

```text
tests/data/raw/googlelocal/meta-Vermont.json.gz
```

The generator expands that metadata into deterministic merchants, notes, image
references, and note favorites for read-path validation.

## Default S2 Scale

```text
merchants:       50,000
notes:          300,000
favorites:      120,000
merchant images: 2 local asset references per merchant
note images:     about 80% of notes have 2 local asset references
```

ID ranges are aligned with S1 unless explicitly overridden:

```text
ls_merchant.id       >= 100000
ls_merchant_note.id  >= 200000
ls_note_favorite.id  >= 300000
seed users: 2001..2020
```

The default distribution is intentionally skewed: about 20% of merchants receive
about 80% of notes. This gives merchant detail and merchant-note read paths a
hot-merchant shape instead of a perfectly flat synthetic distribution.

## Generate CSVs

Fast syntax and input smoke:

```powershell
.\tests\data\importers\scripts\New-PublicDatasetS2.ps1 `
  -ValidateOnly
```

`-ValidateOnly` reads at most 1,000 usable source merchant rows by default. To
smoke a different amount without writing CSVs, pass `-SourceMaxRows`.

Default S2 generation:

```powershell
.\tests\data\importers\scripts\New-PublicDatasetS2.ps1
```

Generated files stay under the ignored output directory:

```text
tests/data/importers/generated/googlelocal-vt-s2-merchants.csv
tests/data/importers/generated/googlelocal-vt-s2-merchant-id-map.csv
tests/data/importers/generated/googlelocal-vt-s2-notes.csv
tests/data/importers/generated/googlelocal-vt-s2-favorites.csv
tests/data/importers/generated/googlelocal-vt-s2-summary.json
```

Custom local raw input or smaller dry-run-style sample:

```powershell
.\tests\data\importers\scripts\New-PublicDatasetS2.ps1 `
  -InputPath .\tests\data\raw\googlelocal\meta-Vermont.json.gz `
  -SourceMaxRows 1000 `
  -MerchantCount 1000 `
  -NoteCount 6000 `
  -FavoriteCount 2400
```

The script rejects HTTP(S) input paths on purpose. Download or stage raw data
outside this repository workflow, keep it under `tests/data/raw/`, and pass a
local path.

## Validate Generated CSV Shape

For a small generated sample, run:

```powershell
.\tests\data\importers\scripts\Test-ImportCsv.ps1 `
  -MerchantCsv .\tests\data\importers\generated\googlelocal-vt-s2-merchants.csv `
  -NoteCsv .\tests\data\importers\generated\googlelocal-vt-s2-notes.csv `
  -FavoriteCsv .\tests\data\importers\generated\googlelocal-vt-s2-favorites.csv
```

This checks headers, duplicate ids, note-to-merchant references,
favorite-to-note references, duplicate user-note favorites, and note
`favorite_count` consistency with active favorite rows.

## Import Into MySQL

Preview the full S2 import SQL:

```powershell
.\tests\data\importers\scripts\Import-PublicDatasetToMysql.ps1 `
  -MerchantCsv .\tests\data\importers\generated\googlelocal-vt-s2-merchants.csv `
  -NoteCsv .\tests\data\importers\generated\googlelocal-vt-s2-notes.csv `
  -FavoriteCsv .\tests\data\importers\generated\googlelocal-vt-s2-favorites.csv `
  -ValidationSqlPath .\tests\load\sql\validate-public-dataset-s2.sql `
  -ExpectedMerchantCount 50000 `
  -ExpectedNoteCount 300000 `
  -ExpectedFavoriteCount 120000 `
  -DryRun
```

Clean rerun and validate:

```powershell
.\tests\data\importers\scripts\Import-PublicDatasetToMysql.ps1 `
  -MerchantCsv .\tests\data\importers\generated\googlelocal-vt-s2-merchants.csv `
  -NoteCsv .\tests\data\importers\generated\googlelocal-vt-s2-notes.csv `
  -FavoriteCsv .\tests\data\importers\generated\googlelocal-vt-s2-favorites.csv `
  -ValidationSqlPath .\tests\load\sql\validate-public-dataset-s2.sql `
  -ExpectedMerchantCount 50000 `
  -ExpectedNoteCount 300000 `
  -ExpectedFavoriteCount 120000 `
  -ClearImportedRange `
  -ValidateAfter
```

`-ClearImportedRange` removes imported favorites, comments, notes, and merchants
by the imported note/merchant ID ranges. It does not reset Docker volumes and it
does not clear demo rows.

## Run Validation SQL Separately

```powershell
.\tests\data\importers\scripts\Test-PublicDatasetMysql.ps1 `
  -SqlPath .\tests\load\sql\validate-public-dataset-s2.sql `
  -ExpectedMerchantCount 50000 `
  -ExpectedNoteCount 300000 `
  -ExpectedFavoriteCount 120000
```

The S2 SQL reports:

```text
imported merchant/note/favorite counts
orphan notes and orphan favorites
missing seed-user references
merchant and note image coverage
note favorite_count consistency
merchant comment_count consistency with generated notes
top note-heavy merchants
top favorite-heavy users
EXPLAIN for merchant list, category list, public note feed, deep note page,
merchant notes, and current-user favorite notes
```

EXPLAIN output is evidence for review. The SQL does not fail automatically on
`filesort` or temporary-table markers because the acceptable threshold depends
on the machine and the matching load-test run.

## Suggested Validation Ladder

1. Parser/input smoke:

```powershell
.\tests\data\importers\scripts\New-PublicDatasetS2.ps1 -ValidateOnly
```

2. Tiny generated sample and CSV checks:

```powershell
.\tests\data\importers\scripts\New-PublicDatasetS2.ps1 `
  -OutputDirectory .\tests\data\importers\generated\s2-smoke `
  -SourceMaxRows 12 `
  -MerchantCount 12 `
  -NoteCount 30 `
  -FavoriteCount 15

.\tests\data\importers\scripts\Test-ImportCsv.ps1 `
  -MerchantCsv .\tests\data\importers\generated\s2-smoke\googlelocal-vt-s2-merchants.csv `
  -NoteCsv .\tests\data\importers\generated\s2-smoke\googlelocal-vt-s2-notes.csv `
  -FavoriteCsv .\tests\data\importers\generated\s2-smoke\googlelocal-vt-s2-favorites.csv
```

3. Small MySQL proof, such as 1,000 / 6,000 / 2,400 rows, with
`-Expected*Count` matching the generated parameters.

4. Default S2 MySQL proof: 50,000 / 300,000 / 120,000 rows.

5. Run the existing public read-path JMeter scenario against the S2-loaded DB.
Do not change the JMX for this step; adjust only script parameters and evidence
notes outside generated data.
