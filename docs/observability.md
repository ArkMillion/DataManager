# 可观测性

内置两类可观测组件，均位于 core：`MetricsCollector`（指标）与 `ConnectionMonitor`（连接事件观察者）。
框架自身零外部监控依赖，输出走 SLF4J，可对接任意日志/指标后端。

## MetricsCollector

进程级单例：`MetricsCollector.getInstance()`。

### 操作计数器

关系库基座在每个 CRUD 入口自动累加：

| 操作 | 计数 key |
|------|----------|
| insert | `insert:<dialect>` |
| batchInsert | `batchInsert:<dialect>` |
| update（两种重载共用） | `update:<dialect>` |
| delete / deleteById | `delete:<dialect>` |

`<dialect>` 为 `mysql` / `postgres` / `generic`（SQLite 走默认方言）。

```java
MetricsCollector m = MetricsCollector.getInstance();

m.incrementCounter("cache.hit", "redis");     // 业务自定义计数同样支持
long inserts = m.counterValue("insert", "mysql");
Map<String, Long> snapshot = m.snapshotCounters();
```

### 慢查询日志

- 阈值默认 1000ms，可调：

```java
MetricsCollector.setSlowQueryThresholdMs(500);
```

- 关系库的插入/批量插入/更新入口计时，超过阈值记录：
  `WARN  Slow query detected (1234 ms): INSERT INTO sys_user ...`（SQL 压缩空白并截断至 120 字符）
- 同语句累计次数保留在内存 Map，可自行导出

> 查询类操作（select/query）当前不计时，避免热路径开销；如需覆盖可在业务侧用 AOP 包装。

## ConnectionMonitor

连接池生命周期事件的观察者模式实现，全局单例：

```java
public interface ConnectionStateListener {
    void onConnected(String poolName);
    void onDisconnected(String poolName, Throwable cause);
    void onConnectionError(String poolName, SQLException ex);
}
```

注册与移除：

```java
ConnectionMonitor.getInstance().addListener(new ConnectionStateListener() {
    @Override public void onConnected(String poolName) {
        log.info("pool {} ready", poolName);
    }
    @Override public void onDisconnected(String poolName, Throwable cause) { alert(); }
    @Override public void onConnectionError(String poolName, SQLException ex) { metrics.mark(ex); }
});
```

触发点：

| 事件 | 来源 |
|------|------|
| onConnected | `ConnectionPool.getOrCreate` 成功建池（poolName 为适配器标签：mysql/postgres/sqlite）|
| onConnectionError | 从池获取连接抛出 SQLException 时 |

监听器异常被捕获并以 WARN 记录，绝不影响主流程；监听器列表为 CopyOnWriteArrayList，线程安全。

## 连接池状态

HikariCP 原生暴露 MXBean。`recordPoolStats(poolName, active, idle, waiting)` 已预留挂载点
（DEBUG 级日志），如需周期采集可结合定时任务：

```java
// 示例：从 HikariDataSource 读取实时状态
HikariPoolMXBean pool = ds.unwrap(HikariDataSource.class).getHikariPoolMXBean();
MetricsCollector.getInstance().recordPoolStats("main",
        pool.getActiveConnections(), pool.getIdleConnections(),
        pool.getThreadsAwaitingConnection());
```

## 日志约定

| Logger | 级别 | 内容 |
|--------|------|------|
| `AbstractJdbcRelationalDB` | DEBUG | 连接关闭失败等底层细节 |
| `ConnectionMonitor` | DEBUG/WARN | 连接事件与监听器异常 |
| `MetricsCollector` | WARN | 慢查询命中 |
| `FlywayLite` | INFO | 迁移执行明细、耗时 |
| `RedisCacheManager` | WARN | flushDb / flushAll 操作审计 |
| HikariCP | INFO | 池启动/关闭（slf4j 桥接）|

生产建议至少开启 INFO；排障时临时打开 `cn.arkmillion=DEBUG`。

## 与外部监控集成

框架不绑定 Micrometer/Prometheus。推荐做法：应用侧定时拉取快照并上报——

```java
ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
ses.scheduleAtFixedRate(() -> registry.report(MetricsCollector.getInstance().snapshotCounters()),
        1, 1, TimeUnit.MINUTES);
```
