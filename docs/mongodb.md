# MongoDB 指南

适配器模块 `datamanager-mongodb`，接口 `cn.arkmillion.core.db.DocumentDB`。

## 依赖与配置

```xml
<dependency>
    <groupId>cn.arkmillion</groupId>
    <artifactId>datamanager-mongodb</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
DataManagerConfig config = DataManagerConfig.builder()
    .mongo("mongodb://localhost:27017/logs")
        .alias("audit-log")
        .database("logs")            // 全局默认库，可被 @Document.database 覆盖
        .applicationName("my-app")
        .build()
    .build();

DocumentDB mongo = dm.getDocumentDB("audit-log");
```

库名解析优先级：`MongoConfig.database` > `@Document.database()` > 抛错。

## 定义文档实体

```java
@Document(collection = "operation_logs", database = "logs")
public class LogEntry {

    @DocumentId                          // 映射到 _id；String 型为 null 时自动生成 ObjectId hex
    private String id;

    private String operationType;

    @Indexed(direction = IndexDirection.DESC, unique = false, sparse = false)
    private Instant timestamp;

    private Map<String, Object> payload; // 任意嵌套结构经 Jackson 序列化
}
```

映射基于 Jackson（已注册 JSR310 时间模块，`FAIL_ON_UNKNOWN_PROPERTIES=false`），
字段与 `_id` 自动双向重命名。需要标准 getter/setter 以获得最佳性能（见[ByteBuddy](bytebuddy-accessors.md)）。

## CRUD

```java
// 插入（单条/批量），String _id 为空自动生成并回填
mongo.insert(logEntry);
mongo.insertMany(entries);

// 查询
List<LogEntry> logs = mongo.find(LogEntry.class,
    Filter.where("operationType").eq("USER_LOGIN"));
LogEntry one = mongo.findOne(LogEntry.class,
    Filter.where("id").eq(hexId).build());

// 更新（Update DSL）
mongo.updateOne(LogEntry.class,
    Filter.where("id").eq(hexId).build(),
    Update.builder()
        .set("payload.status", "done")
        .inc("retry", 1)
        .setOnInsert("createdAt", Instant.now())
        .build());
long modified = mongo.update(LogEntry.class, filter, update);   // updateMany

// 删除
mongo.deleteOne(LogEntry.class, filter);
long deleted = mongo.delete(LogEntry.class, filter);

// 计数与分页
long total = mongo.count(LogEntry.class, Filter.where("operationType").eq("LOGIN").build());
PageResult<LogEntry> page = mongo.findPage(LogEntry.class, filter, PageParam.of(1, 50));
```

### Filter DSL

与关系型 Condition 同构：

```java
Filter.where("timestamp").between(t1, t2)
      .and("level").in(Arrays.asList("WARN", "ERROR"))
      .or("operator").eq("system")
      .build();
```

翻译规则：eq/ne/gt/gte/lt/lte 直译；`like(v)` → 大小写敏感包含匹配
正则 `.*<quoted v>.*`；`isNull` → `field == null`；分组 AND/OR 递归组合。

### Update DSL

| 方法 | Mongo 操作 |
|------|-----------|
| set(k, v) | `$set` |
| unset(k) | `$unset` |
| inc(k, Number) | `$inc`（Integer/Long/Double 保真）|
| push(k, v) | `$push` |
| pull(k, v) | `$pull` |
| setOnInsert(k, v) | `$setOnInsert` |

空 Update 直接拒绝。

## 聚合管道

```java
List<LogEntry> result = mongo.aggregate(LogEntry.class, Arrays.asList(
    AggregationStage.match(Filter.where("level").eq("ERROR")),
    AggregationStage.sort(Collections.singletonList(new Order("timestamp", false))),
    AggregationStage.skip(0),
    AggregationStage.limit(100)
));
```

支持的阶段：

| 阶段 | 说明 |
|------|------|
| `match(Filter)` | 过滤，复用 Filter 翻译器 |
| `sort(List<Order>)` | 排序（Order 来自 condition 包） |
| `skip(n)` / `limit(n)` | 分页 |
| `count(field)` | 计数输出字段 |
| `group(groupExpr, Map<String,String>)` | 分组；accumulator 值为 JSON 片段，如 `{"total":"{$sum:'$amount'}"}` |
| `raw(json)` | 任意阶段原样透传，如 `"{ $unwind: '$tags' }"` |

## Schema 管理

```java
mongo.syncSchema(LogEntry.class, SyncMode.CREATE);
```

| 模式 | 行为 |
|------|------|
| CREATE | 创建全部 @Indexed 索引（命名 `字段_1`/`字段_-1`，携带 unique/sparse）|
| UPDATE | 等价 CREATE（集合无列概念，索引幂等创建）|
| VALIDATE | 校验集合存在，否则抛异常 |
| DROP_CREATE | 删除集合并重建索引（危险操作，受 SchemaSyncPolicy 管控）|

索引创建由驱动保证幂等。

## 连接与关闭

- 客户端基于 `MongoClientSettings` + 连接串构建，URI 支持 `${ENV}` 占位符
- 日志中输出 URI 时自动隐藏凭证（`mongodb://<credentials-hidden>@host`）
- `close()` 由 `DataManager.close()` 统一触发
