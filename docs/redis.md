# Redis 缓存模块

Redis 在 DataManager 中是**独立的 `CacheManager`**（接口位于 core，实现 `datamanager-redis`），
与持久化数据库完全解耦——专用于缓存、会话、排行榜、分布式锁等临时数据场景。

## 依赖与配置

```xml
<dependency>
    <groupId>cn.arkmillion</groupId>
    <artifactId>datamanager-redis</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
DataManagerConfig config = DataManagerConfig.builder()
    .redis("127.0.0.1", 6379)
        .alias("session")                 // 多实例代号
        .database(0)
        .password("${REDIS_PASSWORD}")    // 支持 ${ENV}
        .timeout(3000)                    // socket 超时 ms
        .ssl(false)                       // true → rediss://
        .pool(p -> p.setMaxTotal(50).setMaxIdle(10).setMinIdle(2))
        .build()
    .build();

CacheManager redis = dm.getCacheManager("session");
CacheManager anyDefault = dm.getCacheManager();   // 第一个注册的缓存
```

连接串组装：`redis://:pwd@host:port/db`；开启 ssl 时为 `rediss://`。
池参数：maxTotal=8 / maxIdle=8 / minIdle=0，testWhileIdle=true。

## 基础键值

```java
redis.set("k", "v");                                  // 永久
redis.set("k", "v", Duration.ofMinutes(30));          // TTL
redis.set("k", "v", 1800);                            // 秒
String v = redis.get("k");

redis.del("k");
boolean exists = redis.exists("k");
redis.expire("k", 600);                               // 返回 true 表示设置成功
long ttl = redis.ttl("k");                            // 秒；-2 不存在，-1 无过期
```

## Hash

```java
redis.hSet("user:1", "name", "alice");
String name = redis.hGet("user:1", "name");
Map<String, String> all = redis.hGetAll("user:1");    // 空 key 返回空 Map
redis.hDel("user:1", "name", "email");
boolean has = redis.hExists("user:1", "name");
```

## List

```java
redis.lPush("queue", "a", "b");      // 左入
redis.rPush("queue", "c");           // 右入
String head = redis.lPop("queue");
String tail = redis.rPop("queue");
List<String> range = redis.lRange("queue", 0, -1);    // 全量
```

## Set

```java
redis.sAdd("tags", "java", "redis");
redis.sRem("tags", "java");
Set<String> members = redis.sMembers("tags");
boolean in = redis.sIsMember("tags", "redis");
```

## Sorted Set

```java
redis.zAdd("ranking", 98.5, "player1");
redis.zAdd("ranking", Map.of("player2", 91.0));       // JDK8 用 HashMap 组装

Set<String> top10 = redis.zRange("ranking", 0, 9);            // 按 score 升序
Set<String> band  = redis.zRangeByScore("ranking", 90, 100);
long removed = redis.zRem("ranking", "player1");
```

## 对象序列化

基于 Jackson + JSR310（LocalDateTime/Instant 原生支持），JSON 文本存储：

```java
redis.setObject("session:" + userId, user, Duration.ofMinutes(30));
User cached = redis.getObject("session:" + userId, User.class);

// 泛型容器
List<User> users = redis.getObject("users",
    new com.fasterxml.jackson.core.type.TypeReference<List<User>>() { });
```

反序列化忽略未知字段；序列化失败抛 `DataManagerException`。

## 批量操作

```java
Map<String, String> kv = new HashMap<>();
kv.put("cfg:a", "1");
kv.put("cfg:b", "2");
redis.mSet(kv);

List<String> values = redis.mGet("cfg:a", "cfg:b", "missing");   // 缺失位为 null
redis.del("cfg:a", "cfg:b");                                     // 可变参删除
```

## 分布式锁（轻量）

加锁 = `SET key requestId NX EX seconds`（原子）；释放 = Lua 脚本比对 requestId 后删除（防误删他人锁）：

```java
String requestId = UUID.randomUUID().toString();
if (redis.tryLock("lock:order:create", requestId, 10)) {     // 至少持锁 1 秒
    try {
        // 业务临界区
    } finally {
        redis.releaseLock("lock:order:create", requestId);   // 仅释放自己的锁
    }
}
```

> 这是单实例 Redis 的轻量方案，不含看门狗续期与 RedLock 共识；长事务请自行评估续期策略。

## 键空间管理

```java
Set<String> keys = redis.keys("session:*");   // 生产大库慎用 KEYS，建议业务侧维护索引集合
long size = redis.dbSize();
redis.flushDb();                              // WARN 级日志记录
redis.flushAll();
```

## 多实例

同一应用可挂多个 Redis（不同别名/库/集群），例如会话与限流分离：

```java
DataManagerConfig.builder()
    .redis(h, p).alias("session").database(0).build()
    .redis(h, p).alias("rate-limit").database(1).pool(p -> p.setMaxTotal(32)).build()
    .build();
// dm.getCacheManager("session") / dm.getCacheManager("rate-limit")
```

`close()` 关闭 JedisPool；由 `DataManager.close()` 统一触发。
