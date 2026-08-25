# DataManager 文档中心

**零 Spring 依赖 · 多数据库统一访问 · 注解驱动 Schema 管理**

DataManager 是一套面向纯 Java 环境（桌面应用、CLI 工具、嵌入式系统）的企业级数据持久层框架，
统一访问 MySQL / PostgreSQL / SQLite / MongoDB / Redis，并提供注解驱动建表与 SQL 版本化迁移能力。

- 当前版本：`1.0.0`
- JDK 要求：`8+`（已在 JDK 21 上验证）
- 构建工具：Maven 3.6+

## 文档导航

| 文档 | 内容 |
|------|------|
| [快速上手](getting-started.md) | 依赖引入、定义实体、第一个可运行程序 |
| [架构与设计模式](architecture.md) | 分层结构、SPI 装配机制、八种设计模式落点 |
| [配置参考](configuration.md) | 全部配置项、默认值、多实例与别名、环境变量占位符 |
| [注解参考](annotations.md) | 关系型 / 文档型全部注解、逻辑类型到方言类型映射表 |
| [RelationalDB 指南](relational-db.md) | CRUD、条件 DSL、分页、事务、原生 SQL、SQL 文件执行 |
| [Schema 同步](schema-sync.md) | 四种同步模式、三种数据库的 DDL 行为差异、生产保护策略 |
| [多数据库与无缝切换](multi-database.md) | 同类型多实例、按代号取用、跨库切换边界说明 |
| [迁移引擎 FlywayLite](migration.md) | 版本化脚本、历史表、校验和、占位符 |
| [MongoDB 指南](mongodb.md) | 实体映射、Filter/Update DSL、聚合管道、索引管理 |
| [Redis 缓存模块](redis.md) | 键值/Hash/List/Set/ZSet、对象序列化、分布式锁、多实例 |
| [ByteBuddy 性能增强](bytebuddy-accessors.md) | 字节码访问器原理、启用方式、回退策略 |
| [可观测性](observability.md) | 慢查询日志、操作计数器、连接池状态监听 |
| [安全特性](security.md) | SQL 注入防护、敏感信息保护、危险操作管控 |
| [常见问题 FAQ](faq.md) | 切库、主键回填、事务边界等高频问题 |

## 五分钟速览

```java
// 1. 配置（唯一需要关心数据库差异的地方）
DataManagerConfig config = DataManagerConfig.builder()
    .sqlite("data/app.db")
        .alias("main")
        .build()
    .build();

// 2. 创建门面（try-with-resources 自动关闭全部资源）
try (DataManager dm = DataManagerFactory.create(config)) {

    // 3. 按代号获取统一接口
    RelationalDB db = dm.getRelationalDB("main");

    // 4. 注解驱动建表
    db.syncSchema(User.class, SyncMode.CREATE);

    // 5. 条件化 CRUD
    User user = new User("alice", "alice@corp.com");
    db.insert(user);
    User found = db.selectOne(User.class,
        Condition.where("email").eq("alice@corp.com").build());
}
```

## 模块一览

| 模块 | 说明 | 必选 |
|------|------|------|
| `datamanager-core` | 统一接口、注解、配置、JDBC 基类、工厂 | 是 |
| `datamanager-mysql` | MySQL 适配器（HikariCP） | 按需 |
| `datamanager-postgresql` | PostgreSQL 适配器 | 按需 |
| `datamanager-sqlite` | SQLite 适配器 | 按需 |
| `datamanager-mongodb` | MongoDB 文档适配器 | 按需 |
| `datamanager-redis` | Redis 独立缓存模块 | 按需 |
| `datamanager-migration` | SQL 版本化迁移引擎 | 按需 |
| `datamanager-bytebuddy` | 字节码属性访问器（性能增强，可选） | 按需 |
| `datamanager-examples` | 可运行示例 | 参考 |

## 兼容性

| 维度 | 要求 |
|------|------|
| JDK | 8+ |
| MySQL | 5.7+ |
| PostgreSQL | 10+ |
| SQLite | 3.8+ |
| MongoDB | 4.0+ |
| Redis | 5.0+ |

## 支持与反馈

- 项目主页：<https://github.com/ArkMillion/DataManager>
- 问题反馈：<https://github.com/ArkMillion/DataManager/issues>
- 欢迎提交 Pull Request（见仓库根目录 README「参与贡献」）
- 本项目基于 [Apache License 2.0](https://github.com/ArkMillion/DataManager/blob/master/LICENSE) 协议开源
