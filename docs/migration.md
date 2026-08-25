# 迁移引擎 FlywayLite

`datamanager-migration` 提供轻量 SQL 文件版本化管理，只依赖 `RelationalDB` 抽象，
可用于任何接入的关系型数据库（含多实例：每个库一个引擎实例）。

## 依赖

```xml
<dependency>
    <groupId>cn.arkmillion</groupId>
    <artifactId>datamanager-migration</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 脚本组织

放置在 classpath 目录（推荐 `src/main/resources/db/migration/`），命名：

```
V<版本号>__<描述>.sql
```

- 版本号为数字段，下划线分隔表示多级：`V1`、`V0001`、`V2_1` 均合法
- 执行顺序按版本数值升序，同版本按文件名
- 示例：

```
db/migration/
├── V0001__init_schema.sql      # CREATE TABLE IF NOT EXISTS migration_item ...
└── V0002__seed_items.sql       # INSERT INTO migration_item (name) VALUES ('alpha');
```

## 基本用法

```java
MigrationEngine flyway = FlywayLite.builder()
    .relational(db)                              // 任一 RelationalDB 实例
    .locations("classpath:db/migration")          // 默认值；可传多个位置或文件系统路径
    .build();

MigrationSummary summary = flyway.migrate();
summary.getAppliedVersions();   // 本次执行的版本列表（首次为 ["0001","0002"]）
summary.getCurrentVersion();    // "0002"
summary.getTotalScripts();      // 发现的脚本总数
summary.getAppliedAtMs();       // 每个脚本的耗时

flyway.migrate();               // 幂等：再次执行为 no-op
flyway.validate();              // 校验历史记录与本地脚本 checksum 一致
flyway.info();                  // 已应用清单文本
```

## 历史表

默认表名 `dm_schema_history`（可改）：

| 列 | 类型 | 说明 |
|----|------|------|
| version | VARCHAR(128) PK | 版本号 |
| description | VARCHAR(200) | 文件名描述段 |
| script | VARCHAR(200) | 文件名 |
| checksum | BIGINT | 内容 CRC32 |
| installed_on | TIMESTAMP | 执行时间 |
| execution_ms | BIGINT | 耗时 |
| success | BOOLEAN | 成功标记 |

建表语句使用各库通用类型，由 `db.execute("CREATE TABLE IF NOT EXISTS ...")` 自动创建。

## 核心语义

### 事务性

单个脚本内的全部语句包裹在一个事务中（若调用线程已在事务中则并入）；
任一语句失败 → 回滚整个脚本 → **不写入历史** → 异常抛出。
下次 `migrate()` 会重试该脚本。

### Checksum 防篡改

- 应用脚本时记录 CRC32
- `migrate()` 与 `validate()` 都会比对已应用脚本与本地文件的 checksum，
  不一致立即抛异常（防止修改已执行脚本造成环境漂移）
- 本地缺失已应用脚本仅告警（可用 `ignoreMissingMigrations(true)` 关闭告警）

```java
FlywayLite.builder().relational(db).ignoreMissingMigrations(true).build();
```

### 占位符替换

脚本中的 `${KEY}` 在执行前做文本替换；**要求显式提供全部取值**：

```java
FlywayLite.builder()
    .relational(db)
    .placeholder("SCHEMA_PREFIX", "demo")
    .placeholders(map)                 // 或批量注入
    .build();
```

- 缺失 key → fail-fast，且不产生任何副作用（历史无记录、数据库无变更）
- 脚本不含 `${}` 时无需传值

注意：SQL 迁移占位符与环境变量占位符（`${ENV}`）是两套独立机制，互不影响。

## 多环境 / 多实例

```java
try (DataManager dm = DataManagerFactory.create(config)) {
    MigrationEngine mainFlyway = FlywayLite.builder()
        .relational(dm.getRelationalDB("main"))
        .historyTable("dm_schema_history")
        .build();

    MigrationEngine analyticsFlyway = FlywayLite.builder()
        .relational(dm.getRelationalDB("analytics"))
        .locations("classpath:db/migration-analytics")   // 独立脚本集
        .build();
}
```

## Builder 参数一览

| 方法 | 默认值 | 说明 |
|------|--------|------|
| `relational(RelationalDB)` | 必填 | 目标库 |
| `locations(String...)` | classpath:db/migration | 可混合 classpath: 与文件系统路径；目录自动扫描（支持 jar 内）|
| `placeholder(k,v)` / `placeholders(Map)` | 空 | 替换取值 |
| `historyTable(String)` | dm_schema_history | 历史表名 |
| `ignoreMissingMigrations(boolean)` | false | 本地缺失已应用脚本是否静默 |

## 与 Schema 同步的分工

| 场景 | 工具 |
|------|------|
| 开发期实体加字段快速补列 | `syncSchema(UPDATE)` |
| 生产结构变更（需留痕/审计） | 手写迁移脚本 + FlywayLite |
| 上线前结构漂移检查 | `syncSchema(VALIDATE)` + `flyway.validate()` |

两者可以共存：同步负责「期望态」，迁移负责「变更历史」。
