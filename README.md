<div align="center">

# DataManager

**零 Spring 依赖 · 多数据库统一访问 · 注解驱动 Schema 管理**

[![Java](https://img.shields.io/badge/JDK-8%2B-blue)](https://github.com/ArkMillion/DataManager)
[![Build](https://img.shields.io/badge/Maven-BUILD%20SUCCESS-brightgreen)](https://github.com/ArkMillion/DataManager/actions)
[![Tests](https://img.shields.io/badge/tests-66%20passing-success)](https://github.com/ArkMillion/DataManager)
[![Release](https://img.shields.io/github/v/release/ArkMillion/DataManager)](https://github.com/ArkMillion/DataManager/releases)
[![JitPack](https://jitpack.io/v/ArkMillion/DataManager.svg)](https://jitpack.io/#ArkMillion/DataManager)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

一套面向纯 Java 环境（桌面应用、CLI 工具、嵌入式系统）的企业级数据持久层框架。

GitHub：**<https://github.com/ArkMillion/DataManager>**

</div>

---

## 目录

- [特性](#特性)
- [模块结构](#模块结构)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [使用指南](#使用指南)
  - [统一门面与工厂](#统一门面与工厂)
  - [注解驱动 Schema 同步](#注解驱动-schema-同步)
  - [条件构建器](#条件构建器)
  - [事务管理](#事务管理)
  - [SQL 文件执行](#sql-文件执行)
  - [SQL 版本化迁移（FlywayLite）](#sql-版本化迁移flywaylite)
  - [MongoDB 文档操作](#mongodb-文档操作)
  - [Redis 独立缓存模块](#redis-独立缓存模块)
- [架构与设计模式](#架构与设计模式)
- [安全特性](#安全特性)
- [构建与测试](#构建与测试)
- [参与贡献](#参与贡献)
- [开源协议](#开源协议)

---

## 特性

- **多数据库统一访问** —— `RelationalDB` / `DocumentDB` / `CacheManager` 三套接口覆盖 MySQL、SQLite、MongoDB、Redis，切换成本趋近于零
- **零 Spring 依赖** —— 纯 Java + JDBC / 官方驱动实现，不锁定任何容器生态
- **注解驱动自动建表** —— `@Table` / `@Column` 声明实体，框架自动生成 DDL、索引并支持增量补列
- **SQL 版本化迁移** —— 内置 FlywayLite 引擎：按版本顺序执行、历史留痕、占位符替换、checksum 防篡改
- **Redis 独立缓存模块** —— 与持久化完全解耦的 `CacheManager`，含对象序列化与轻量分布式锁
- **企业级稳定性** —— HikariCP 连接池、ThreadLocal 事务、SLF4J 日志、慢查询监控、连接状态观察者

## 模块结构

```
datamanager-parent/
├── datamanager-core/           # 核心抽象层：接口、注解、配置、JDBC 基类、工厂
├── datamanager-bytebuddy/      # 可选增强：字节码生成属性访问器，替代反射（高并发优化）
├── datamanager-mysql/          # MySQL 适配器（HikariCP 连接池）
├── datamanager-postgresql/     # PostgreSQL 适配器
├── datamanager-sqlite/         # SQLite 适配器
├── datamanager-mongodb/        # MongoDB 文档适配器
├── datamanager-redis/          # Redis 独立缓存模块（Jedis）
├── datamanager-migration/      # SQL 文件版本化迁移引擎（FlywayLite）
└── datamanager-examples/       # 可运行示例
```

适配器通过 **Java ServiceLoader SPI** 注册——把哪个适配器放进 classpath，`DataManagerFactory` 就自动装配哪个数据库。新增数据库类型只需扩展，无需修改框架代码。

## 环境要求

| 维度 | 要求 |
|------|------|
| JDK | 8+（字节码 target 8） |
| MySQL | 5.7+ |
| PostgreSQL | 10+ |
| SQLite | 3.8+ |
| MongoDB | 4.0+ |
| Redis | 5.0+ |

## 快速开始

### 1. 引入依赖

本项目通过 [JitPack](https://jitpack.io/#ArkMillion/DataManager) 发布：每次合并到 `master` 会自动构建并发布 GitHub Release，JitPack 按 Tag 提供依赖，无需手动上传仓库。

#### Step 1：添加 JitPack 仓库

**Maven**（`pom.xml`）：

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

**Gradle**（`settings.gradle`）：

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

**Kotlin DSL**（`settings.gradle.kts`）：

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

#### Step 2：添加依赖

> 多模块项目经 JitPack 构建后，groupId 为 `com.github.ArkMillion.DataManager`，artifactId 对应各模块名；版本号使用 [Release Tag](https://github.com/ArkMillion/DataManager/releases)（如 `v1.0.0`）。

**Maven**：

```xml
<dependency>
    <groupId>com.github.ArkMillion.DataManager</groupId>
    <artifactId>datamanager-core</artifactId>
    <version>v1.0.0</version>
</dependency>
<!-- 按需引入适配器 -->
<dependency>
    <groupId>com.github.ArkMillion.DataManager</groupId>
    <artifactId>datamanager-sqlite</artifactId>
    <version>v1.0.0</version>
</dependency>
```

**Gradle**：

```groovy
implementation 'com.github.ArkMillion.DataManager:datamanager-core:v1.0.0'
// 按需引入适配器
implementation 'com.github.ArkMillion.DataManager:datamanager-sqlite:v1.0.0'
```

**Kotlin DSL**：

```kotlin
implementation("com.github.ArkMillion.DataManager:datamanager-core:v1.0.0")
// 按需引入适配器
implementation("com.github.ArkMillion.DataManager:datamanager-sqlite:v1.0.0")
```

可用模块与 artifactId 对应关系见[模块结构](#模块结构)。

### 2. 定义实体

```java
@Table(name = "sys_user", comment = "用户表")
public class User {

    @Id(strategy = GenerationType.AUTO)
    @AutoIncrement
    private Long id;

    @Column(name = "user_name", length = 64, nullable = false, comment = "用户名")
    private String username;

    @Column(name = "email", length = 128, nullable = false)
    @Index(type = IndexType.UNIQUE, name = "uk_email")
    private String email;

    // getters / setters / 无参构造器
}
```

### 3. 使用门面

```java
import static cn.arkmillion.core.condition.Condition.where;

DataManagerConfig config = DataManagerConfig.builder()
    .sqlite("data/app.db")
        .poolSize(1)
        .build()
    .build();

try (DataManager dm = DataManagerFactory.create(config)) {
    RelationalDB db = dm.getRelationalDB("sqlite");

    db.syncSchema(User.class, SyncMode.CREATE);          // 自动建表 + 索引

    User user = new User("alice", "alice@corp.com");
    db.insert(user);                                     // 回填自增主键

    User found = db.selectOne(User.class,
        where("email").eq("alice@corp.com").build());

    PageResult<User> page = db.selectPage(User.class,
        where("status").eq(1).orderBy("id", true).build(),
        PageParam.of(1, 20));
}
```

完整可运行示例见 [`datamanager-examples`](datamanager-examples/src/main/java/cn/arkmillion/examples/QuickStart.java)。

## 使用指南

### 统一门面与工厂

```java
DataManagerConfig config = DataManagerConfig.builder()
    .mysql("jdbc:mysql://localhost:3306/enterprise_db", "app_user", "${DB_PASSWORD}")
        .poolSize(20).minIdle(5).maxLifetime(1800000).build()
    .sqlite("data/app.db").poolSize(2).busyTimeout(5000).build()
    .mongo("mongodb://localhost:27017/logs").database("logs").build()
    .redis("localhost", 6379).database(0).password("${REDIS_PASSWORD}")
        .pool(p -> p.setMaxTotal(50)).build()
    .build();

try (DataManager dm = DataManagerFactory.create(config)) {
    RelationalDB mysql  = dm.getRelationalDB("mysql");
    DocumentDB  mongo   = dm.getDocumentDB("mongo");
    CacheManager redis  = dm.getCacheManager();
}
```

MySQL 底层使用 HikariCP 连接池（连接超时、空闲回收、最大存活时间均可配置）；密码等敏感字段支持 `${ENV_VAR}` 占位符注入。

### 多实例与别名（代号）

同一类型的数据库可以配置任意多个实例，每个实例用 `alias` 设置一个代号，之后全程通过代号取用对象：

```java
DataManagerConfig config = DataManagerConfig.builder()
    .mysql("jdbc:mysql://host1:3306/main_db", "app", "${DB_PASSWORD}")
        .alias("main")                      // 代号
        .poolSize(20)
        .build()
    .mysql("jdbc:mysql://host2:3306/report_db", "app", "${DB_PASSWORD}")
        .alias("analytics")                 // 同类型第二个实例
        .poolSize(5)
        .build()
    .sqlite("data/cache.db")
        .alias("local-cache")
        .build()
    .build();

try (DataManager dm = DataManagerFactory.create(config)) {
    RelationalDB main      = dm.getRelationalDB("main");
    RelationalDB analytics = dm.getRelationalDB("analytics");
    RelationalDB cache     = dm.getRelationalDB("local-cache");

    // 各实例拥有独立连接池、独立事务，互不影响
}
```

规则：

- **不设置 alias 时回落到类型名**（`"mysql"` / `"postgres"` / `"sqlite"` / `"mongo"` / `"redis"`），单实例用法完全向后兼容
- 别名在所有实例间全局唯一，重复注册会在启动时立即报错（工厂会自动回滚关闭已创建的连接池）
- 缓存同样支持多实例：`dm.getCacheManager("rate-limit")` 按代号获取；`getCacheManager()` 返回第一个注册的缓存
- 允许先建池、后等数据库就绪：`.initializationFailTimeout(-1)` 关闭 Hikari 启动时的连接校验

### 注解驱动 Schema 同步

关系型注解：`@Table` `@Column` `@Id` `@AutoIncrement` `@Index`
文档型注解：`@Document` `@DocumentId` `@Indexed`

四种同步模式：

| 模式 | 行为 |
|------|------|
| `CREATE` | 表不存在则建表 + 索引（幂等） |
| `UPDATE` | 已存在则增量补列（只加不删），再补索引 |
| `VALIDATE` | 校验表与列齐全，缺失即抛异常（生产推荐） |
| `DROP_CREATE` | 删表重建（危险操作） |

生产环境保护：

```java
SchemaSyncPolicy.setRequireConfirm(true);      // 锁定 UPDATE/DROP_CREATE
SchemaSyncPolicy.confirmDangerousOperation(); // 二次确认后放行一次
```

### 条件构建器

```java
where("age").between(18, 65)
   .and("status").in(Arrays.asList(1, 2))
   .or("email").like("%@corp.com")
   .orderBy("created_at", false)
   .build();
```

支持 `eq/ne/gt/gte/lt/lte/like/in/notIn/between/isNull/isNotNull` 与 AND/OR 组合。所有查询均通过 `PreparedStatement` 参数绑定。

### 事务管理

```java
db.beginTransaction();
try {
    db.insert(order);
    db.update(User.class, where("id").eq(userId).build(), updates);
    db.commit();
} catch (Exception e) {
    db.rollback();
    throw e;
}
```

事务基于 ThreadLocal 绑定连接，同一事务内的所有 CRUD / 原生 SQL 自动复用同一连接。

### SQL 文件执行

```java
db.executeSqlFile("classpath:db/init.sql");
db.executeSqlFile("classpath:db/init.sql", placeholders);   // ${KEY} 占位符替换
```

内置语句拆分器：正确处理行注释 `--`、块注释 `/* */`、字符串内分号与转义引号。

### SQL 版本化迁移（FlywayLite）

将脚本放入 classpath 的 `db/migration/` 目录，命名遵循 `V<版本>__描述.sql`：

```
db/migration/
├── V0001__init_schema.sql
└── V0002__seed_items.sql
```

```java
MigrationEngine flyway = FlywayLite.builder()
    .relational(db)
    .locations("classpath:db/migration")
    .placeholder("SCHEMA_PREFIX", "demo")
    .build();

MigrationSummary summary = flyway.migrate();   // 按版本号顺序执行未应用脚本
flyway.validate();                             // 校验已应用脚本校验和未被篡改
```

- 执行历史记录在 `dm_schema_history` 表（版本、脚本、CRC32 校验和、耗时、成功标记）
- 单个脚本整体事务执行：任一语句失败即回滚且不记入历史
- 已应用脚本的 checksum 变更会在 `validate()` 时被拒绝

### MongoDB 文档操作

```java
@Document(collection = "operation_logs", database = "logs")
public class LogEntry {
    @DocumentId
    private String id;

    @Indexed(direction = IndexDirection.DESC)
    private Instant timestamp;

    private Map<String, Object> payload;
}

mongo.insert(log);                                   // String 型 _id 为空时自动生成 ObjectId
List<LogEntry> logs = mongo.find(LogEntry.class,
    Filter.where("operationType").eq("USER_LOGIN"));
mongo.updateOne(LogEntry.class, Filter.where("id").eq(id).build(),
    Update.builder().set("payload.status", "done").inc("retry", 1).build());
mongo.syncSchema(LogEntry.class, SyncMode.CREATE);   // 创建 @Indexed 索引
```

聚合管道支持 `match/sort/skip/limit/count/group/raw` 阶段。

### Redis 独立缓存模块

```java
CacheManager redis = dm.getCacheManager();

redis.set("k", "v", Duration.ofMinutes(30));
redis.hSet("user:1", "name", "alice");
redis.zAdd("ranking", 98.5, "player1");

redis.setObject("session:1", user, Duration.ofMinutes(30));  // Jackson JSON 序列化
User cached = redis.getObject("session:1", User.class);

// 轻量分布式锁（SET NX EX + Lua 安全释放）
if (redis.tryLock("lock:order", requestId, 10)) {
    try { /* 业务 */ } finally { redis.releaseLock("lock:order", requestId); }
}
```

## 架构与设计模式

```
┌─────────────────────────────────────────────────────────┐
│                    应用层 (业务代码)                       │
├─────────────────────────────────────────────────────────┤
│              统一门面 DataManager (Facade)                │
├──────────────┬──────────────┬──────────────┬────────────┤
│ RelationalDB │  DocumentDB  │ CacheManager │ Migration  │
│  (Strategy)  │  (Strategy)  │  (Strategy)  │            │
├──────────────┼──────────────┼──────────────┼────────────┤
│ MySQLAdapter │ MongoAdapter │ RedisAdapter │ FlywayLite │
│ SQLiteAdapter│              │              │            │
└──────────────┴──────────────┴──────────────┴────────────┘
```

| 设计模式 | 落点 |
|----------|------|
| 门面 Facade | `DataManager` 统一入口 |
| 工厂 Factory | `DataManagerFactory` + ServiceLoader SPI 装配 |
| 建造者 Builder | `DataManagerConfig.Builder` 及各子配置 Builder |
| 适配器 Adapter | 各数据库 Adapter 实现 |
| 策略 Strategy | 数据库接口族；`JdbcDialect` 方言策略 |
| 模板方法 Template Method | `SchemaSynchronizer.sync()` 固化同步流程 |
| 代理 Proxy | `TransactionProxy`（`@Transactional` 动态代理） |
| 观察者 Observer | `ConnectionMonitor` 连接状态监听 |

另含单例 `ConnectionPool`、反射元数据缓存、`MetricsCollector` 慢查询日志与操作计数。

## 安全特性

- **SQL 注入防护**：全部参数化查询走 `PreparedStatement`
- **敏感信息保护**：`${ENV_VAR}` 环境变量占位符；日志中数据库 URI 凭证自动脱敏
- **危险操作管控**：`SchemaSyncPolicy` 二次确认机制；无条件的 DELETE/UPDATE 直接拒绝
- **资源泄漏防护**：连接/语句全部显式释放，事务连接线程级隔离

## 构建与测试

```bash
git clone https://github.com/ArkMillion/DataManager.git
cd DataManager
mvn clean install          # 编译 + 测试 + 安装到本地仓库
mvn test                   # 仅运行测试
```

运行示例：

```bash
mvn exec:java -pl datamanager-examples \
    -Dexec.mainClass=cn.arkmillion.examples.QuickStart
```

当前测试基线：**66 个用例全部通过**（core 32 / sqlite 集成 15 / ByteBuddy 4 / PostgreSQL 4 / 迁移引擎 6 / 示例端到端 5）。MySQL / PostgreSQL / MongoDB / Redis 适配器的在线行为需要对应服务实例，CI 环境默认仅覆盖离线路径。

### 高并发优化：ByteBuddy 访问器（可选）

默认情况下，实体字段读写通过缓存的反射完成。将 `datamanager-bytebuddy` 加入 classpath 后，
框架会自动通过 SPI 切换为 **ByteBuddy 生成的字节码访问器**（直接调用 getter/setter，无安全检查、对 JIT 友好）：

```xml
<dependency>
    <groupId>com.github.ArkMillion.DataManager</groupId>
    <artifactId>datamanager-bytebuddy</artifactId>
    <version>v1.0.0</version>
</dependency>
```

- 实体提供标准 getter/setter 时启用生成访问器；否则自动回退反射
- 按字段缓存生成结果，零重复开销
- 对上层 API 完全透明，无需修改任何业务代码

### PostgreSQL 适配器

```java
DataManagerConfig config = DataManagerConfig.builder()
    .postgres("jdbc:postgresql://localhost:5432/enterprise_db", "app_user", "${DB_PASSWORD}")
        .schema("public")
        .poolSize(20)
        .build()
    .build();

RelationalDB pg = DataManagerFactory.create(config).getRelationalDB("postgres");
pg.syncSchema(User.class, SyncMode.CREATE);
```

PostgreSQL 方言特性：自增主键映射为 `SERIAL` / `BIGSERIAL`，表与列注释通过 `COMMENT ON` 语句写入，
类型映射覆盖 `BOOLEAN` / `BYTEA` / `NUMERIC(p,s)` / `DOUBLE PRECISION` 等，索引通过 `pg_indexes` 幂等创建。

## 文档

完整使用文档位于 [`docs/`](docs/index.md)：

| 文档 | 内容 |
|------|------|
| [快速上手](docs/getting-started.md) | 依赖、实体、第一个程序 |
| [配置参考](docs/configuration.md) | 全部配置项与多实例别名 |
| [RelationalDB 指南](docs/relational-db.md) | CRUD / 条件 DSL / 事务 |
| [Schema 同步](docs/schema-sync.md) | 四种模式与生产保护 |
| [迁移引擎](docs/migration.md) | FlywayLite 版本化管理 |
| [多数据库切换](docs/multi-database.md) | 跨库无缝切换边界 |
| [MongoDB](docs/mongodb.md) · [Redis](docs/redis.md) | 文档库与缓存模块 |
| [架构设计](docs/architecture.md) · [FAQ](docs/faq.md) | 设计模式与常见问题 |

## 参与贡献

欢迎 Issue 与 Pull Request！

1. Fork 本仓库并创建特性分支：`git checkout -b feature/amazing-feature`
2. 提交变更：`git commit -m "feat: add amazing feature"`
3. 推送分支：`git push origin feature/amazing-feature`
4. 发起 Pull Request

提交前请确保 `mvn clean install` 全量通过。

## 开源协议

本项目基于 [Apache License 2.0](LICENSE) 协议开源。

- 允许商业使用、修改与再分发，需保留版权与许可声明
- 修改文件时请按协议第 4(b) 条添加变更声明
- 完整协议文本见 [LICENSE](LICENSE)，亦可查阅
  [Apache 官方原文](https://www.apache.org/licenses/LICENSE-2.0)

---

<div align="center">

如果这个项目对你有帮助，欢迎点一个 Star ⭐

</div>
