# 配置参考

全部配置通过 `DataManagerConfig.builder()` 链式构建。同一类型可重复调用以创建多实例，
每段 `.build()` / `.end()` 返回外层 Builder 继续追加。

## 总览

```java
DataManagerConfig config = DataManagerConfig.builder()
    .mysql(url, user, password)      .alias(a).build()   // 可重复
    .postgres(url, user, password)   .alias(a).build()   // 可重复
    .sqlite(filePath)                .alias(a).build()   // 可重复
    .mongo(uri)                      .alias(a).build()   // 可重复
    .redis(host, port)               .alias(a).build()   // 可重复
    .build();
```

## 别名（代号）

| 规则 | 说明 |
|------|------|
| 可选 | 不设置时回落到类型名：`"mysql"` / `"postgres"` / `"sqlite"` / `"mongo"` / `"redis"` |
| 全局唯一 | 同类别（关系库命名空间 / 文档库命名空间 / 缓存命名空间）内不得重复，启动即报错 |
| 取用 | `dm.getRelationalDB(alias)`、`dm.getDocumentDB(alias)`、`dm.getCacheManager(alias)` |
| 列举 | `getRelationalDBNames()` / `getDocumentDBNames()` / `getCacheNames()` |
| 默认缓存 | 第一个注册的 CacheManager 为默认（无参 `getCacheManager()` 目标） |

## MySQL（`.mysql(url, user, password)`）

| 方法 | 默认值 | 说明 |
|------|--------|------|
| `alias(String)` | null | 实例代号 |
| `poolSize(int)` | 10 | Hikari maximumPoolSize |
| `minIdle(int)` | 2 | Hikari minimumIdle |
| `connectionTimeout(long)` | 30000 ms | 获取连接超时 |
| `idleTimeout(long)` | 600000 ms | 空闲连接回收 |
| `maxLifetime(long)` | 1800000 ms | 连接最大存活 |
| `initializationFailTimeout(long)` | Hikari 默认（启动即校验连通性）；传 `-1` 改为惰性初始化 | 启动期行为 |
| `charset(String)` | utf8mb4 | 建表 DEFAULT CHARSET |
| `ssl(boolean)` | false | URL 追加 useSSL=true |

底层连接池键 = `mysql|url|username`：多个实例指向同一端点时自动共享同一个池。

## PostgreSQL（`.postgres(url, user, password)`）

MySQL 的池参数方法全部可用，另加：

| 方法 | 默认值 | 说明 |
|------|--------|------|
| `schema(String)` | public | Synchronizer 在 current_schema() 下比对表/列/索引 |

## SQLite（`.sqlite(filePath)`）

| 方法 | 默认值 | 说明 |
|------|--------|------|
| `poolSize(int)` | **1** | 默认单连接规避文件锁写冲突；提高前请评估并发写 |
| `busyTimeout(int)` | 5000 ms | 预留字段 |
| `foreignKeys(boolean)` | true | 预留字段 |
| `initializationFailTimeout(long)` | Hikari 默认 | 同上 |

- `filePath` 支持相对/绝对路径（父目录自动创建）、`:memory:`、`file:` URI
- 目录不存在时自动 mkdirs

## MongoDB（`.mongo(uri)`）

| 方法 | 默认值 | 说明 |
|------|--------|------|
| `alias(String)` | null | 实例代号 |
| `database(String)` | null | 库名；未设置时使用实体 `@Document.database()`；两者皆无则报错 |
| `applicationName(String)` | datamanager | 驱动 applicationName |

## Redis（`.redis(host, port)`）

| 方法 | 默认值 | 说明 |
|------|--------|------|
| `alias(String)` | null | 实例代号 |
| `database(int)` | 0 | 逻辑库编号 |
| `password(String)` | null | 支持 `${ENV}` 占位符 |
| `timeout(int)` | 2000 ms | socket 超时 |
| `ssl(boolean)` | false | true 时使用 rediss:// |
| `pool(PoolCustomizer)` | maxTotal=8, maxIdle=8, minIdle=0 | 定制 JedisPoolConfig |

连接串按 URI 组装：`redis://:pwd@host:port/db` 或 `rediss://...`。

## 环境变量占位符

所有连接串、用户名、密码、Redis host/password、Mongo URI 在**适配器构造时**统一解析 `${KEY}`：

优先级：`PlaceholderResolver.register(key,value)` 手工注册 → 环境变量 → JVM 系统属性。

```java
PlaceholderResolver.register("DB_PASSWORD", "dev-only");  // 单测/本地覆写
```

解析失败的 key 抛出 `DataManagerPlaceholderException`（fail-fast，避免把字面量 `${...}` 送进驱动）。

> 注意：SQL 脚本内的占位符是另一套机制（`executeSqlFile` / FlywayLite 的 placeholders 参数），
> 仅做文本替换且要求显式传值，两者互不影响。

## initializationFailTimeout 使用场景

HikariCP 默认在启动时取一条连接验证可用性（失败即抛）。两种典型调整：

```java
// 场景 A：容器编排中希望应用先起来、等数据库就绪后自愈
.postgres(url, u, p).initializationFailTimeout(-1).build()

// 场景 B：离线单元测试只想拿对象不做真实连接
.postgres(url, u, p).initializationFailTimeout(-1).poolSize(1).minIdle(0).build()
```

## 完整示例

```java
DataManagerConfig config = DataManagerConfig.builder()
    .mysql("jdbc:mysql://${DB_HOST}:3306/main", "app", "${DB_PASSWORD}")
        .alias("main")
        .poolSize(20).minIdle(5)
        .connectionTimeout(10_000).maxLifetime(1_800_000)
        .charset("utf8mb4")
        .build()
    .postgres("jdbc:postgresql://${PG_HOST}:5432/report", "app", "${DB_PASSWORD}")
        .alias("report")
        .schema("analytics")
        .initializationFailTimeout(-1)
        .build()
    .sqlite("data/local.db").alias("offline").build()
    .mongo("mongodb://${MG_HOST}:27017/logs").alias("audit-log").database("logs").build()
    .redis("127.0.0.1", 6379).alias("session-cache").database(0)
        .password("${REDIS_PASSWORD}")
        .timeout(3000)
        .pool(p -> p.setMaxTotal(50).setMinIdle(2))
        .build()
    .build();
```
