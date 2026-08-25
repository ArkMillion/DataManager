# 架构与设计模式

## 总体分层

```
┌─────────────────────────────────────────────────────────────────────┐
│                          应用层（业务代码）                            │
├─────────────────────────────────────────────────────────────────────┤
│                统一门面 DataManager（Facade / AutoCloseable）          │
├───────────────┬───────────────┬─────────────────┬───────────────────┤
│ RelationalDB  │  DocumentDB   │  CacheManager   │ MigrationEngine   │
│  （策略接口）   │  （策略接口）   │  （策略接口）     │                  │
├───────────────┼───────────────┼─────────────────┼───────────────────┤
│ MySQLAdapter  │ MongoAdapter  │ RedisCacheMgr   │ FlywayLite        │
│ PgSQLAdapter  │               │                 │ （基于RelationalDB）│
│ SQLiteAdapter │               │                 │                   │
├───────────────┴───────────────┴─────────────────┴───────────────────┤
│ 驱动层：mysql-connector-j / postgresql / sqlite-jdbc / mongodb-driver │
│         / jedis（均由适配器传递引入，业务无需直接依赖）                    │
└─────────────────────────────────────────────────────────────────────┘
```

核心原则：

1. **单一职责**：每个适配器只负责一类数据库
2. **开闭原则**：新增数据库 = 新增一个适配器模块 + SPI 注册文件，框架代码零修改
3. **依赖倒置**：业务只依赖 `core` 的接口；`FlywayLite` 也只依赖 `RelationalDB` 抽象，
   因此迁移引擎天然可用于任何关系型数据库
4. **接口隔离**：Redis 缓存是独立的 `CacheManager` 接口，不与持久化接口耦合

## 关键组件

### core 包结构

| 包 | 内容 |
|----|------|
| `cn.arkmillion.core.annotation` | 全部注解 |
| `cn.arkmillion.core.enums` | DataType / GenerationType / SyncMode / Operator 等 |
| `cn.arkmillion.core.config` | DataManagerConfig 及子配置（Builder 模式）、占位符解析 |
| `cn.arkmillion.core.condition` | Condition 条件 DSL、PageParam / PageResult / Order |
| `cn.arkmillion.core.filter` | Filter / Update / AggregationStage（MongoDB DSL） |
| `cn.arkmillion.core.metadata` | EntityParser、SchemaDefinition、ColumnMetadata（反射元数据缓存）|
| `cn.arkmillion.core.schema` | SchemaSynchronizer 模板、SchemaSyncPolicy 安全策略 |
| `cn.arkmillion.core.jdbc` | AbstractJdbcRelationalDB、JdbcDialect、TypeBinders、ConnectionPool、TransactionProxy |
| `cn.arkmillion.core.accessor` | PropertyAccessor 抽象层（反射默认实现 + ByteBuddy SPI） |
| `cn.arkmillion.core.factory` | DataManagerFactory / DataManagerImpl、三个 Provider SPI、InstanceBinding |
| `cn.arkmillion.core.db` | DataManager / RelationalDB / DocumentDB / CacheManager 接口 |
| `cn.arkmillion.core.monitor` / `metrics` | 连接状态观察者、慢查询与计数器 |

### 关系型适配器的复用架构

三种关系型数据库的 CRUD / 条件翻译 / 分页 / 事务 **只在 core 写了一次**
（`AbstractJdbcRelationalDB`），每个适配器仅提供两块方言钩子：

```java
public class PostgreSQLAdapter extends AbstractJdbcRelationalDB {

    public PostgreSQLAdapter(PostgresConfig config) {
        super("postgres",
              ConnectionPool.getOrCreate("postgres", resolved(config)),
              PgDialect.INSTANCE);          // 钩子 1：标识符引用、分页语法
    }

    @Override
    protected SchemaSynchronizer createSynchronizer() {   // 钩子 2：DDL 方言
        return new PostgreSQLSchemaSynchronizer(dataSource);
    }
}
```

这保证了「同一套业务代码跨库无缝切换」在架构上不可能出现行为分叉。

## SPI 装配机制

适配器通过 Java ServiceLoader 注册，文件位于各适配器 jar 内：
`META-INF/services/cn.arkmillion.core.factory.RelationalDBProvider`

```java
public interface RelationalDBProvider {
    String name();
    List<InstanceBinding<RelationalDB>> createInstances(DataManagerConfig config);
}
```

`DataManagerFactory.create(config)` 的装配流程：

1. 遍历 classpath 上所有 `RelationalDBProvider` / `DocumentDBProvider` / `CacheProvider`
2. 每个 Provider 根据配置生成若干 `InstanceBinding`（别名 → 实例）
3. 别名全局唯一，冲突立即抛异常；**失败时自动关闭本次已创建的全部实例**（连接池回滚）
4. 第一个注册的 CacheManager 成为默认缓存（`getCacheManager()` 无参重载的目标）

自定义扩展示例——接入一种新关系库只需三步：

```java
public class MyDbProvider implements RelationalDBProvider {
    public String name() { return "mydb"; }
    public List<InstanceBinding<RelationalDB>> createInstances(DataManagerConfig c) {
        return c.getMyDbConfigs().stream()
            .map(cfg -> InstanceBinding.of(
                InstanceBinding.resolveAlias(cfg.getAlias(), name()),
                new MyDbAdapter(cfg)))
            .collect(Collectors.toList());
    }
}
// + META-INF/services 文件一行：com.xxx.MyDbProvider
```

## 设计模式落点

| 设计模式 | 实现位置 | 说明 |
|----------|----------|------|
| 门面 Facade | `DataManager` / `DataManagerImpl` | 统一入口，聚合全部实例 |
| 工厂 Factory | `DataManagerFactory` | 配置驱动装配，SPI 解耦实现类 |
| 建造者 Builder | `DataManagerConfig.Builder` 及五个子 Builder | 链式构建，支持同类多段配置 |
| 适配器 Adapter | 五个 Adapter | 各数据库原生 SDK → 统一接口 |
| 策略 Strategy | `RelationalDB`/`DocumentDB`/`CacheManager`；`JdbcDialect` | 运行时可替换的访问策略与 SQL 方言 |
| 模板方法 Template Method | `SchemaSynchronizer.sync()` | 固化校验→解析→分支处理流程，方言只填空 |
| 代理 Proxy | `TransactionProxy` | 对 `@Transactional` 方法做事务织入 |
| 观察者 Observer | `ConnectionMonitor` + `ConnectionStateListener` | 连接建立/断开/出错广播 |
| 单例 Singleton | `ConnectionPool` 注册表、`MetricsCollector`、`ConnectionMonitor` | 进程级共享资源 |
| 组合模式 | `Condition` / `Filter` 的 Criterion 树 | 叶子与分组节点统一递归翻译为 SQL/BSON |

## 数据流

### CRUD 流程

```
业务代码 → DataManager(门面) → Adapter
                                ├─ 注解元数据缓存（EntityParser.parse，进程级）
                                ├─ SqlBuilder（条件 DSL → 参数化 SQL，方言感知）
                                ├─ PreparedStatement 执行（防注入）
                                └─ 结果映射（TypeBinders 类型转换 + 属性访问器写入）
                                     └─ 属性访问器：ByteBuddy 生成字节码 或 反射回退
```

### Schema 同步流程

```
syncSchema(entity, mode)
  → validateEntity（必须有 @Table/@Document）
  → SchemaSyncPolicy.checkAllowed（危险操作确认）
  → EntityParser.parse → SchemaDefinition
  → 分支：CREATE / UPDATE / VALIDATE / DROP_CREATE
       └─ tableExists / createTable / alterTable / createIndexes / validateSchema（方言实现）
```

### 事务流程

```
beginTransaction → 从池取连接 → setAutoCommit(false) → 绑定 ThreadLocal
之后同线程所有操作复用该连接（CRUD/原生SQL/executeSqlFile 均在事务内）
commit / rollback → 提交或回滚 → 恢复 autoCommit → 归还连接 → 清除 ThreadLocal
```
