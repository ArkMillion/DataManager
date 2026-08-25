# 常见问题 FAQ

## 通用

### Q: 如何从 MySQL 切换到 PostgreSQL / SQLite？要改多少代码？
业务代码零改动，只改配置段与获取时的代号（或统一用 `getRelationalDBNames()` 遍历）。
边界限制：原生 SQL、迁移脚本内容、SQLite 小数标度。详见[多数据库指南](multi-database.md)。

### Q: 支持分布式事务吗？
不支持。各数据库事务相互独立且框架不伪装支持；跨库一致性请用最终一致性/补偿设计。

### Q: 实体必须有哪些结构？
公共无参构造器 + 标准 getter/setter（后者在使用 ByteBuddy 模块时是启用生成访问器的条件；
纯反射路径下无 getter/setter 也能工作）。

### Q: Spring 项目能用吗？
可以——它是普通 jar，不与 Spring 冲突，只是不走 Spring 事务/数据源体系。
官方 Spring Boot Starter 在 Roadmap 中（`datamanager-spring`）。

## CRUD

### Q: insert 之后为什么拿不到自增 id？
确认三点：主键标注 `@Id(strategy = GenerationType.AUTO)` 或 IDENTITY 且类型为 Long/Integer；
字段带 `@AutoIncrement`（或依赖 AUTO 默认语义）；实体不是通过 `batchInsert` 插入的
（批量路径不做逐行回填）。

### Q: update(entity) 会把 null 字段写进库吗？
不会。按主键更新时只 SET 非 null 非 主键字段；需要显式置空请使用
条件更新 `update(clazz, condition, updates)` 并放入 null 值。

### Q: 为什么 delete/update 传空 Condition 会抛异常？
防全表误操作的刻意设计。确需全表操作请构造恒真条件（如 `where("id").gt(0)`）
并自行评估风险。

### Q: 分页没写排序会乱序吗？
不会。未指定 orderBy 时自动按主键升序保证分页稳定；显式 orderBy 优先。

### Q: in() 传了空集合会怎样？
编译为 `(NULL)` 恒假条件，安全返回空结果而不是全表。

## Schema

### Q: UPDATE 模式会删我的列吗？
永远不会。UPDATE 只增量补列补索引（additive-only）；删列/改类型请走迁移脚本。

### Q: 生产环境怎么防止误执行 DROP_CREATE？
```java
SchemaSyncPolicy.setRequireConfirm(true);
```
开启后 UPDATE/DROP_CREATE 一律拒绝，直到线程内调用 `confirmDangerousOperation()`。

### Q: SQLite 里 DECIMAL(18,2) 的 100.50 读回变成 100.5？
SQLite NUMERIC 亲和性所致。精确小数场景请选 MySQL/PostgreSQL，
或在实体层改用以「分」为单位的整型存储。

## 迁移

### Q: 迁移脚本执行到一半失败了怎么办？
该脚本整体回滚且**不记入历史**，修复脚本后重新 `migrate()` 即可重试。
已成功的脚本不会重复执行。

### Q: 我改了一个已执行的迁移脚本，报 checksum mismatch？
这是防篡改保护。正确做法：新增一个更高版本的脚本做修正，禁止修改历史脚本。
若确属开发期废弃库可手工清空 `dm_schema_history` 对应记录后重建。

### Q: 脚本里想用 `${TABLE}` 占位符但启动就报缺 placeholder？
FlywayLite 要求显式提供全部取值：`.placeholder("TABLE", "sys_user")`。
这与环境变量占位符是两套机制。

## Redis / MongoDB

### Q: getCacheManager() 抛 CacheManager not configured？
配置里没有任何 `.redis(...)` 段。缓存是可选模块，只有配置并引入
`datamanager-redis` 后才可用；多实例用 `getCacheManager(alias)` 取指定实例。

### Q: tryLock 的锁过期了业务还没跑完怎么办？
轻量锁不含续期看门狗。预估好 TTL 并留裕量，或在临界区内自行实现续期；
更严格的诉求建议引入 RedLock 类方案。

### Q: MongoDB 的 like 是忽略大小写的正则吗？
不是。`like(v)` 翻译为包含匹配 `.*<Pattern.quote(v)>.*`，大小写敏感；
需要模糊/大小写规则请用 `AggregationStage.raw` 或驱动原生 Regex。

## 构建与集成

### Q: 离线环境能跑测试吗？
能。全部测试基于 SQLite 文件库与逻辑层，不需要任何外部服务；
MySQL/PG/Mongo/Redis 的在线行为需自备实例（CI 默认只覆盖离线路径）。

### Q: 如何新增一种数据库支持？
1. 新建 Maven 模块依赖 core
2. 继承 `AbstractJdbcRelationalDB`，实现 `JdbcDialect` 与 `SchemaSynchronizer`
3. 实现 `RelationalDBProvider` 并在 `META-INF/services` 注册
4. （可选）在 core 配置类中增加对应子配置与 Builder 段

参考最小样例：`datamanager-postgresql`（仅 4 个主类）。

### Q: 多个实例连同一个 MySQL 会重复建池吗？
不会。连接池键 = `标签|url|username`，同端点同账号自动共享同一个 HikariCP 池。

### Q: ByteBuddy 模块必须引吗？
可选增强。不引入时走缓存反射，功能完全一致；高并发服务建议引入。
见[ByteBuddy 指南](bytebuddy-accessors.md)。
