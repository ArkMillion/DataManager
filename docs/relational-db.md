# RelationalDB 使用指南

接口定义于 `cn.arkmillion.core.db.RelationalDB`，MySQL / PostgreSQL / SQLite 共享同一实现基座，
行为完全一致。获取方式：

```java
RelationalDB db = dm.getRelationalDB("main");   // 别名或类型名
```

## CRUD

### 插入

```java
<T> int insert(T entity);
<T> int batchInsert(List<T> entities);
```

- 自增主键（IDENTITY/AUTO）不参与 INSERT 列，执行后通过 generated keys 回填实体
- UUID 主键在值为 null 时自动生成并回填
- `batchInsert`：当前线程无事务时整体包裹在一个事务中，任一行失败全部回滚；
  已处于事务中则加入当前事务
- 返回受影响行数合计

```java
User u = new User("alice", "a@x.com");
db.insert(u);                      // u.getId() 已回填

List<Order> orders = ...;
int affected = db.batchInsert(orders);
```

### 更新

```java
<T> int update(T entity);                                                    // 按主键
<T> int update(Class<T> clazz, Condition condition, Map<String, Object> updates);  // 条件更新
```

- `update(entity)`：只 SET 非 null 且非主键字段；主键缺失抛异常
- 条件更新：**condition 为空直接拒绝**（防全表更新）；updates 为空返回 0

```java
Map<String, Object> patch = new HashMap<>();
patch.put("status", 2);
db.update(User.class, where("id").eq(userId).build(), patch);
```

### 删除

```java
<T> int delete(Class<T> clazz, Condition condition);   // 空 condition 直接拒绝
<T> int deleteById(Class<T> clazz, Object id);
```

### 查询

```java
<T> List<T> select(Class<T> clazz, Condition condition);
<T> T selectOne(Class<T> clazz, Condition condition);   // LIMIT 1，无则 null
<T> T selectById(Class<T> clazz, Object id);
<T> long count(Class<T> clazz, Condition condition);     // null 条件统计全表
<T> PageResult<T> selectPage(Class<T> clazz, Condition condition, PageParam page);
```

排序规则：显式 `orderBy(...)` 优先；未指定时默认按主键升序（保证分页稳定）。

```java
PageResult<User> page = db.selectPage(User.class,
    where("email").like("%@corp.com").orderBy("createdAt", false).build(),
    PageParam.of(2, 20));

page.getRecords();   // 当前页数据
page.getTotal();     // COUNT(*) 结果
page.getPages();     // 总页数（向上取整）
page.hasNext();
```

## 条件 DSL

入口：`Condition.where(field)`，静态导入更简洁：
`import static cn.arkmillion.core.condition.Condition.where;`

| 方法 | SQL 形态 |
|------|----------|
| eq/ne/gt/gte/lt/lte | `col = ?` 等 |
| like(String) | `col LIKE ?`（通配符自行写在 pattern 里，如 `"%@corp.com"`）|
| in(Collection) / notIn(Collection) | `col IN (?,?,...)`；空集合编译为 `(NULL)` 恒假 |
| between(a, b) | `col BETWEEN ? AND ?` |
| isNull() / isNotNull() | `col IS [NOT] NULL` |

连接词与分组：

```java
// 默认 AND 连接；and(field)/or(field) 切换下一个条件的连接方式
where("age").gte(18).and("status").eq(1).or("vip").eq(true).build()

// 整组合并：or(innerCondition) 生成 OR 分组
Condition inner = where("deleted").eq(false).build();
where("tenant").eq("acme").or(inner).build()      // tenant=? OR (deleted=?)

// 排序
where("status").eq(1).orderBy("createdAt", false) // orderBy 返回 Condition，链尾调用
```

字段名解析顺序：属性名 → 列名 → 原样输出（允许写原生列表达式）。

## 分页参数

```java
PageParam.of(pageNum, pageSize)   // pageNum 从 1 开始；offset=(pageNum-1)*pageSize
PageResult.of(records, total, param)
PageResult.empty(param)
```

分页 SQL 由方言生成：MySQL / PostgreSQL / SQLite 均为 `LIMIT n OFFSET m`。

## 原生 SQL

```java
int execute(String sql, Object... params);                       // DML/DDL
<T> List<T> query(String sql, Class<T> clazz, Object... params); // 标量或实体
List<Map<String, Object>> queryMap(String sql, Object... params);// 通用行映射
```

- 全部走 PreparedStatement 参数绑定
- `query` 到简单类型（String/Number/时间等）取第一列做转换；到实体类按列标签匹配列名/属性
- `queryMap` 保持驱动返回的列标签原样

```java
db.execute("UPDATE sys_user SET last_login = ? WHERE id = ?", LocalDateTime.now(), id);
long cnt = db.query("SELECT COUNT(*) FROM sys_user", Long.class).get(0);
```

> ⚠️ 原生 SQL 不做方言翻译，切库时需要自行核对语法（详见[多数据库指南](multi-database.md)的边界说明）。

## 事务管理

```java
void beginTransaction();
void commit();
void rollback();
boolean isInTransaction();
```

- 事务连接绑定 ThreadLocal；同线程后续所有操作（CRUD、原生 SQL、executeSqlFile）自动复用
- 嵌套 `beginTransaction` 抛异常；无事务时 `commit/rollback` 抛异常
- 异常路径务必 rollback（推荐 try/catch 模板，见[快速上手](getting-started.md)）

声明式用法（可选）：

```java
public interface UserService {
    void register(User u, Order firstOrder);
}

UserService service = TransactionProxy.createProxy(new UserServiceImpl(db), db, UserService.class);
// UserServiceImpl 中标注 @Transactional 的方法自动获得事务（外层已有事务则加入）
service.register(user, order);
```

注解为 `cn.arkmillion.core.annotation.Transactional(timeoutSeconds = 30)`。

## SQL 文件执行

```java
void executeSqlFile(String filePath);
void executeSqlFile(String filePath, Map<String, String> placeholders);
```

- 位置协议：`classpath:db/init.sql`（支持前导 `/`）或文件系统路径
- 语句拆分器特性：剥离 `--` 行注释与 `/* */` 块注释；字符串内的分号与 `''` 转义不被误拆
- 占位符 `${KEY}` 文本替换；脚本含占位符但未提供对应值时 fail-fast
- 若当前线程处于事务中，文件内语句并入该事务

## Schema 管理

```java
void syncSchema(Class<?> entityClass, SyncMode mode);
```

四种模式与各库行为详见 [Schema 同步](schema-sync.md)。

## 资源释放

```java
@Override void close();   // 若在事务中先回滚；随后关闭底层连接池（HikariDataSource）
```

通常交由 `DataManager.close()` 统一关闭，不建议单独 close 单个实例后继续使用门面。
