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

## Review JSONL Mapping

The review converter expects one JSON object per line. Run the merchant
converter first so `merchant-id-map.csv` exists.

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

Reviews with merchants that are missing from the map are skipped with a
warning.

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
