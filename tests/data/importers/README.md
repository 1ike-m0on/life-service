# Public Dataset Importers

This folder contains small, repository-safe tooling for turning local public
dataset extracts into seed/import CSVs for the local-life prototype. It does
not download datasets and does not store raw public datasets.

## What Goes Here

Commit these files:

- Import scripts.
- Synthetic sample fixtures.
- Field mapping notes and validation examples.

Do not commit these files:

- Raw public datasets.
- Large transformed CSVs.
- Local evidence files.

Generated output belongs under:

```text
tests/data/importers/generated/
```

That folder ignores generated content by default.

## Outputs

The merchant converter writes `merchants.csv` with columns aligned to
`ls_merchant`:

```text
id,category_id,name,images,area,address,longitude,latitude,avg_price_cent,sold_count,comment_count,score,open_hours,status,created_at,updated_at
```

It also writes `merchant-id-map.csv`, which maps source merchant ids to local
merchant ids:

```text
source_merchant_id,merchant_id,name
```

The review converter writes `merchant-notes.csv` with columns aligned to the
current `ls_merchant_note` shape:

```text
id,user_id,merchant_id,order_id,title,content,rating,images,like_count,comment_count,favorite_count,status,created_at,updated_at
```

It also writes `source-user-id-map.csv`. By default, source users are mapped
onto demo user ids `2001` through `2020`, so note CSVs can be used with the
existing local seed users.

## Merchant CSV Mapping

Run a dry validation pass on the synthetic sample:

```powershell
.\tests\data\importers\scripts\Convert-MerchantCsv.ps1 `
  -InputPath .\tests\data\importers\samples\merchants.sample.csv `
  -ValidateOnly
```

If local PowerShell policy blocks direct script execution, use the same
arguments through:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File <script-path> <args>
```

Write normalized merchant outputs:

```powershell
.\tests\data\importers\scripts\Convert-MerchantCsv.ps1 `
  -InputPath .\tests\data\importers\samples\merchants.sample.csv `
  -OutputPath .\tests\data\importers\generated\merchants.csv `
  -MapPath .\tests\data\importers\generated\merchant-id-map.csv
```

Accepted source field names are flexible. Useful merchant fields include:

```text
source_id, business_id, gmap_id, merchant_id, id
name, business_name, merchant_name
categories, category, category_id
area, district, neighborhood, city
address, address1, full_address
longitude, lng, lon
latitude, lat
stars, rating, score, avg_rating
review_count, comment_count
price, price_per_person, avg_price_cent
cover_image, image_url, images
open_hours, hours
status, is_open
```

Known categories are mapped to the demo category ids:

```text
1 Coffee
2 Hotpot / restaurant-like food
3 Bakery
4 Japanese food
5 Fitness
6 Cinema
```

## Merchant JSONL / Gzip Mapping

Some public datasets, such as Google Local Data 2021, publish business metadata
as one JSON object per line and may compress it as `.json.gz`. Use:

```powershell
.\tests\data\importers\scripts\Convert-MerchantJsonl.ps1 `
  -InputPath .\tests\data\raw\googlelocal\meta-Vermont.json.gz `
  -OutputPath .\tests\data\importers\generated\googlelocal-vt-merchants.csv `
  -MapPath .\tests\data\importers\generated\googlelocal-vt-merchant-id-map.csv `
  -MaxRows 10000
```

The converter accepts the Google Local metadata fields `gmap_id`, `name`,
`address`, `latitude`, `longitude`, `category`, `avg_rating`, `num_of_reviews`,
`price`, `hours`, and `state`.

`InputPath` can be either a local path or an HTTP(S) URL. For remote gzip files,
the converter reads the stream progressively and stops once `MaxRows` is
reached.

## Review JSONL Mapping

The review converter expects one JSON object per line. It also accepts `.gz`
inputs. Run the merchant converter first so `merchant-id-map.csv` exists.

Run a dry validation pass:

```powershell
.\tests\data\importers\scripts\Convert-ReviewJsonl.ps1 `
  -InputPath .\tests\data\importers\samples\reviews.sample.jsonl `
  -MerchantMapPath .\tests\data\importers\generated\merchant-id-map.csv `
  -ValidateOnly
```

Write normalized note output:

```powershell
.\tests\data\importers\scripts\Convert-ReviewJsonl.ps1 `
  -InputPath .\tests\data\importers\samples\reviews.sample.jsonl `
  -MerchantMapPath .\tests\data\importers\generated\merchant-id-map.csv `
  -OutputPath .\tests\data\importers\generated\merchant-notes.csv `
  -UserMapPath .\tests\data\importers\generated\source-user-id-map.csv
```

Useful review fields include:

```text
review_id, note_id, id
business_id, gmap_id, merchant_id
user_id, author_id, author
title, summary, headline
content, text, review_text, body, comment
rating, stars, score
images, image_urls, photos, pics
like_count, likes, useful
comment_count, favorite_count
created_at, date, time, timestamp
```

For Google Local review files, `gmap_id`, millisecond `time`, `text`, `rating`,
`user_id`, and nested `pics.url` values are handled directly.

Reviews with merchants that are missing from the map are skipped with a
warning.

`InputPath` can also point at an HTTP(S) `.json.gz` file. This is useful for
large public datasets because 1k/10k/100k validation runs do not need to
download the complete gzip artifact first.

## Real Public Dataset Smoke

The recommended first real dataset is the Vermont slice of Google Local Data
2021 because it is public, local-business oriented, and small enough for local
progressive validation.

Raw downloads, when used, stay under the ignored folder:

```text
tests/data/raw/googlelocal/
```

Suggested validation ladder:

```text
1,000 reviews  -> fast converter smoke
10,000 reviews -> medium mapping check
100,000 reviews or state slice -> larger local evidence run
```

Do not commit raw `.json.gz` files or generated CSV outputs.

One local validation run used:

```text
Merchant source:
https://mcauleylab.ucsd.edu/public_datasets/gdrive/googlelocal/meta-Vermont.json.gz

Review source:
https://mcauleylab.ucsd.edu/public_datasets/gdrive/googlelocal/review-Vermont.json.gz
```

Observed converter results:

```text
merchant metadata: 11,291 source rows -> 11,243 merchants, 48 skipped
1,000 reviews:      1,000 source rows  ->    788 notes, 212 skipped
10,000 reviews:    10,000 source rows  ->  7,150 notes, 2,850 skipped
100,000 reviews:  100,000 source rows  -> 67,004 notes, 32,996 skipped
```

The skipped review rows were mainly empty-text reviews. Generated CSV
validation passed for the 1k, 10k, and 100k note outputs against the 11,243
merchant output.

## Validate Generated CSVs

After writing both outputs, validate headers, basic ranges, duplicate merchant
ids, and note-to-merchant references:

```powershell
.\tests\data\importers\scripts\Test-ImportCsv.ps1 `
  -MerchantCsv .\tests\data\importers\generated\merchants.csv `
  -NoteCsv .\tests\data\importers\generated\merchant-notes.csv
```

Expected sample result:

```text
Validated 3 merchants and 4 notes.
```

## Import Notes

These CSVs are staging artifacts. Keep generated files local, review them, and
load them into a local database only when they match the scenario you are
testing. For repeatable evidence, record the source dataset name, local file
hash, converter parameters, row counts, and validation result outside Git if
the evidence is large.
