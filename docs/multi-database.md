# 多数据库与无缝切换

## 同类型多实例（别名机制）

同一类数据库可以配置任意多个实例，每个实例一个代号（alias），业务全程通过代号取对象：

```java
DataManagerConfig config = DataManagerConfig.builder()
    .mysql("jdbc:mysql://host1:3306/main_db", "app", "${DB_PASSWORD}")
        .alias("main").poolSize(20).build()
    .mysql("jdbc:mysql://host2:3306/report_db", "app", "${DB_PASSWORD}")
        .alias("analytics").poolSize(5).build()
    .sqlite("data/offline.db")
        .alias("local-cache").build()
    .build();

try (DataManager dm = DataManagerFactory.create(config)) {
    RelationalDB main      = dm.getRelationalDB("main");
    RelationalDB analytics = dm.getRelationalDB("analytics");
    RelationalDB local     = dm.getRelationalDB("local-cache");

    main.beginTransaction();          // 每个实例独立事务上下文
    analytics.select(...);            // 互不影响
}
```

### 规则速记

| 规则 | 说明 |
|------|------|
| 别名可选 | 缺省回落类型名 `"mysql"/"postgres"/"sqlite"/"mongo"/"redis"`，旧用法零改动 |
| 全局唯一 | 同类别命名空间内冲突即启动失败；工厂会关闭本次已创建的全部连接池（回滚） |
| 独立资源 | 每别名独立连接池、独立 ThreadLocal 事务 |
| 缓存多实例 | `dm.getCacheManager("rate-limit")`；无参重载返回首个注册者 |

MongoDB 与 Redis 同样支持 `.mongo(uri).alias("audit")` / `.redis(h,p).alias("session")`。

## 跨数据库无缝切换

三种关系型数据库共用 core 的 `AbstractJdbcRelationalDB`，**业务代码层面切换只需改配置**：

```java
// MySQL 版本
DataManagerConfig.builder()
    .mysql("jdbc:mysql://h:3306/db", "u", "p").poolSize(20).build().build();

// PostgreSQL 版本 —— 其余代码一行不动
DataManagerConfig.builder()
    .postgres("jdbc:postgresql://h:5432/db", "u", "p").poolSize(20).build().build();
```

以下能力保证可移植：

| 能力 | 保证方式 |
|------|----------|
| 统一接口签名 | 同一 `RelationalDB` 接口 |
| 实体注解一套通用 | `@Table.engine/charset` 在非 MySQL 库被安全忽略 |
| 逻辑类型系统 | `DataType` 由各 Synchronizer 映射本地类型（见[注解参考](annotations.md)映射表）|
| 条件 DSL 方言化 | 反引号 vs 双引号、LIMIT/OFFSET 由 `JdbcDialect` 内部消化 |
| 自增回填 | AUTO_INCREMENT / SERIAL / AUTOINCREMENT 统一回填 |
| Schema 四模式语义一致 | 模板方法固化流程 |
| 时间类型往返 | TypeBinders 对 Timestamp/字符串多格式对称转换 |

### 需要注意的边界

1. **原生 SQL**（`execute/query/queryMap`）：手写 SQL 天然绑定方言，
   例如 `ON DUPLICATE KEY UPDATE`（MySQL）vs `ON CONFLICT DO UPDATE`（PG）。切库时需人工核对。
2. **SQL 文件与迁移脚本**：脚本内的 DDL 本身是方言相关的，切库时按目标方言准备一套脚本。
3. **SQLite 数值精度**：NUMERIC 亲和性会丢失标度（`100.50` ↔ `100.5`）；
   精确金额场景选择 MySQL/PostgreSQL。
4. **FULLTEXT**：`IndexType.FULLTEXT` 目前在 PG/SQLite 按普通索引处理。

### 跨库事务声明

各数据库事务相互独立，框架**不支持也不伪装支持**分布式事务。
跨多个 `RelationalDB` 的业务一致性需自行设计（如最终一致性 / 补偿）。
