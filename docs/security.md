# 安全特性

## SQL 注入防护

- **全量参数化**：条件 DSL、CRUD、原生 `execute/query` 一律走 `PreparedStatement` 参数绑定，
  框架内部不存在字符串拼接用户输入的路径
- 条件 DSL 的字段名来自代码而非用户输入时才允许透传（解析顺序：属性名→列名→原样）；
  如需把外部输入映射为列名，请先做白名单校验
- `LIKE` 通配符由调用方写在 pattern 中，框架只做参数绑定

## 敏感信息保护

### 环境变量占位符

密码等敏感配置不落明文，运行期解析 `${KEY}`：

```yaml
# 等效 Java 配置中的写法
.mysql(url, "app_user", "${DB_PASSWORD}")
.redis(host, port).password("${REDIS_PASSWORD}")
.mongo("mongodb://user:${MONGO_PWD}@host:27017/db")
```

优先级：手工注册 > 环境变量 > 系统属性；全部未命中 fail-fast 抛异常，
杜绝把字面量 `${...}` 当真实密码使用。

```java
PlaceholderResolver.register("DB_PASSWORD", "local-dev");   // 本地/单测覆写
PlaceholderResolver.clear();                                // 清理
```

### 日志脱敏

MongoDB 连接失败信息中的 URI 自动隐藏凭证：

```
mongodb://<credentials-hidden>@host:27017/db
```

## 危险操作管控

### Schema 危险模式确认

```java
SchemaSyncPolicy.setRequireConfirm(true);      // 全局开关：锁死 UPDATE/DROP_CREATE

SchemaSyncPolicy.confirmDangerousOperation();  // 线程级一次性放行
db.syncSchema(Order.class, SyncMode.UPDATE);
SchemaSyncPolicy.clearConfirmation();
```

- 未确认时直接拒绝并提示补救方法，防止生产误触发 DROP_CREATE
- CREATE / VALIDATE 不受限，可常开保护

### 无条件删除/更新防御

```java
db.delete(User.class, Condition.empty());                       // ❌ 抛异常
db.update(User.class, Condition.empty(), updates);              // ❌ 抛异常
db.delete(User.class, where("status").eq(0).build());           // ✅ 必须显式给条件
```

批量插入在无外层事务时自动整体包裹事务，失败全回滚（见[RelationalDB](relational-db.md)）。

## 资源泄漏防护

- 连接获取统一入口，事务连接 ThreadLocal 绑定并在 commit/rollback 后立即归还
- Statement/ResultSet 全部 try-with-resources；非事务连接 finally 显式 close
- `DataManager.close()` 幂等关闭全部实例；工厂装配失败时回滚关闭本次已创建实例

## Redis 专项

- 分布式锁释放使用 Lua 比对 requestId，只能解自己持有的锁
- `flushDb/flushAll` 执行前输出 WARN 审计日志（含 host:port）

## MongoDB 专项

- URI 凭证在异常消息中脱敏
- 空 Update DSL 直接拒绝，防误发全文档覆盖语义

## 加固清单（部署前自查）

- [ ] 所有密码走 `${ENV}` 注入，配置文件/仓库无明文
- [ ] 生产环境 `SchemaSyncPolicy.setRequireConfirm(true)` 且同步模式为 VALIDATE
- [ ] 数据库账号最小权限（应用账号不授予 DROP DATABASE）
- [ ] MySQL/PG 开启传输加密（MySQL useSSL / PG sslmode），Redis 视场景启用 rediss
- [ ] 监控接入 `MetricsCollector` 慢查询告警
