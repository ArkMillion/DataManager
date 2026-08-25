# Schema 同步

`syncSchema(Class<?> entity, SyncMode mode)` 依据注解元数据自动管理表结构。
实现采用模板方法：core 固化流程，各数据库只实现方言 DDL。

## 四种模式

| 模式 | 行为 | 幂等性 | 适用环境 |
|------|------|--------|----------|
| `CREATE` | 表不存在 → 建表 + 建索引；已存在 → 什么都不做 | ✅ | 开发/测试首次初始化 |
| `UPDATE` | 不存在→建表；存在→**增量补列**（只加不改不删）+ 补齐缺失索引 | ✅ | 开发迭代期 |
| `VALIDATE` | 表不存在或缺少列 → 抛 `DataManagerException`（含缺失列清单） | ✅ | **生产推荐** |
| `DROP_CREATE` | DROP TABLE IF EXISTS → CREATE → 索引 | ❌ 危险 | 测试重置 |

## 生产保护策略

```java
// 启用后 UPDATE / DROP_CREATE 一律拒绝，直到显式确认
SchemaSyncPolicy.setRequireConfirm(true);

SchemaSyncPolicy.confirmDangerousOperation();   // 放行一次（线程级）
db.syncSchema(Order.class, SyncMode.UPDATE);
SchemaSyncPolicy.clearConfirmation();
```

- `CREATE` / `VALIDATE` 不受影响，可常开
- 确认标记为 ThreadLocal 一次性消费，防止误连续放行

## 各库 DDL 行为对照

### MySQL

- 标识符使用反引号
- 建表携带 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` 与表注释（来自 @Table）
- 列注释内联在列定义后；自增 `AUTO_INCREMENT`
- 默认值规则：`CURRENT_TIMESTAMP` / `NULL` / 数字字面量原样输出，其余加单引号转义
- 存在性检查：`information_schema.TABLES` / `COLUMNS` / `STATISTICS`

### PostgreSQL

- 自增整型主键映射 `SERIAL` / `BIGSERIAL`（ALTER 补列时不使用 SERIAL，仅普通类型）
- 注解 comment 通过建表后的 `COMMENT ON TABLE/COLUMN` 语句写入
- 类型映射见[注解参考](annotations.md)（BOOLEAN/BYTEA/NUMERIC/DOUBLE PRECISION 等）
- 存在性检查：`current_schema()` + `information_schema.*` + `pg_indexes`
- 主键统一以表级 `PRIMARY KEY (col)` 子句声明

### SQLite

- 类型亲和映射：整型族→INTEGER、浮点→REAL、DECIMAL→NUMERIC、时间与文本→TEXT、二进制→BLOB
- 整型自增主键生成 `INTEGER PRIMARY KEY AUTOINCREMENT`
- 补列走 `PRAGMA table_info(...)` 比对 + `ALTER TABLE ... ADD COLUMN`
- 索引使用 `CREATE [UNIQUE] INDEX IF NOT EXISTS`，并先查 sqlite_master 预检
- ⚠️ NUMERIC 亲和性下 DECIMAL 标度可能丢失（`100.50` 读回 `100.5`），精确小数场景请选 MySQL/PG

## UPDATE 模式的边界（有意设计）

增量补列为 **additive-only**：

- ✅ 新增缺失的列、新增缺失的索引
- ❌ 不删除列、不改列类型、不重命名——这类破坏性变更请通过迁移脚本管理（[FlywayLite](migration.md)）

理由：自动删改列在生产环境风险极高，框架宁可保守。

## 推荐实践

```java
// 应用启动时（开发环境）
if (env.isDev()) {
    db.syncSchema(User.class, SyncMode.UPDATE);     // 迭代补列
} else {
    db.syncSchema(User.class, SyncMode.VALIDATE);   // 生产强校验，结构漂移立即失败
}
```

配合迁移引擎的分工建议：

| 场景 | 工具 |
|------|------|
| 全新空库快速起步 | `syncSchema(CREATE)` |
| 实体加字段（开发期） | `syncSchema(UPDATE)` |
| 生产结构变更留痕/可回溯 | FlywayLite 脚本（手写 DDL） |
| 上线前防漂移校验 | `syncSchema(VALIDATE)` |
