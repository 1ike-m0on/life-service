# S1 公开数据入库脚本说明

本目录的转换脚本只负责把公开数据集转换成项目内部 CSV。S1 入库脚本用于把已生成的 CSV 加载到本地 MySQL，验证真实数据规模下的读路径。

## 数据规模

当前 S1 资产面向 UCSD Google Local Data 2021 Vermont 子集：

```text
商户：约 11,243
笔记：约 67,004
```

生成文件仍放在忽略目录：

```text
tests/data/importers/generated/
```

不要提交原始数据或生成后的大 CSV。

## 默认文件名

入库脚本默认读取：

```text
tests/data/importers/generated/googlelocal-vt-merchants.csv
tests/data/importers/generated/googlelocal-vt-notes-100k.csv
```

如果你的文件名不同，显式传入 `-MerchantCsv` 和 `-NoteCsv`。

## 预览命令

```powershell
.\tests\data\importers\scripts\Import-PublicDatasetToMysql.ps1 `
  -MerchantCsv .\tests\data\importers\generated\googlelocal-vt-merchants.csv `
  -NoteCsv .\tests\data\importers\generated\googlelocal-vt-notes-100k.csv `
  -DryRun
```

## 入库命令

Docker Compose 默认 MySQL 容器名为 `life-service-mysql`：

```powershell
.\tests\data\importers\scripts\Import-PublicDatasetToMysql.ps1 `
  -MerchantCsv .\tests\data\importers\generated\googlelocal-vt-merchants.csv `
  -NoteCsv .\tests\data\importers\generated\googlelocal-vt-notes-100k.csv `
  -ValidateAfter
```

脚本会在导入前执行 `set global local_infile = 1`，用于允许 MySQL 读取本地 CSV。
这是本地测试导入需要的开关，不会清空数据库或删除 Docker volume。
CSV 行终止符按 Windows CRLF 处理，脚本使用 `X'0D0A'` 指定行尾，避免 MySQL 字符串转义差异。

脚本默认不会清空已有演示数据。它只按 CSV 中的主键做 `replace` 导入。

如果要清理上一次导入的公开数据，再重新导入：

```powershell
.\tests\data\importers\scripts\Import-PublicDatasetToMysql.ps1 `
  -MerchantCsv .\tests\data\importers\generated\googlelocal-vt-merchants.csv `
  -NoteCsv .\tests\data\importers\generated\googlelocal-vt-notes-100k.csv `
  -ClearImportedRange `
  -ValidateAfter
```

`-ClearImportedRange` 只删除导入 ID 段：

```text
ls_merchant.id >= 100000
ls_merchant_note.id >= 200000
```

不会清理普通演示数据。

## 使用宿主机 mysql 客户端

如果不想通过容器内 mysql 客户端执行，可以使用：

```powershell
.\tests\data\importers\scripts\Import-PublicDatasetToMysql.ps1 `
  -UseHostMysql `
  -MysqlHost 127.0.0.1 `
  -MysqlPort 3307 `
  -MysqlDatabase life_service `
  -MysqlUser root `
  -MysqlPassword root `
  -MerchantCsv .\tests\data\importers\generated\googlelocal-vt-merchants.csv `
  -NoteCsv .\tests\data\importers\generated\googlelocal-vt-notes-100k.csv `
  -ValidateAfter
```

宿主机模式要求本机能直接运行 `mysql` 命令。

## 单独执行验收

```powershell
.\tests\data\importers\scripts\Test-PublicDatasetMysql.ps1
```

验收内容：

```text
商户导入数量
笔记导入数量
孤儿笔记
缺失用户引用的笔记
可见笔记数量
笔记分布 Top 10 商户
核心查询 EXPLAIN
```

EXPLAIN 不直接判定失败，需要结合输出判断是否出现全表扫描、filesort、临时表和慢查询。
