# 快速上手

本文带你从零完成一次完整的 DataManager 接入：引入依赖 → 定义实体 → 建表 → CRUD → 事务。

## 1. 引入依赖

核心包必须引入；数据库适配器按需选择。

```xml
<dependency>
    <groupId>cn.arkmillion</groupId>
    <artifactId>datamanager-core</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 任选其一或多个 -->
<dependency>
    <groupId>cn.arkmillion</groupId>
    <artifactId>datamanager-sqlite</artifactId>
    <version>1.0.0</version>
</dependency>
<!--
<dependency>
    <groupId>cn.arkmillion</groupId>
    <artifactId>datamanager-mysql</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>cn.arkmillion</groupId>
    <artifactId>datamanager-postgresql</artifactId>
    <version>1.0.0</version>
</dependency>
-->
```

> 驱动（mysql-connector-j / postgresql / sqlite-jdbc）由适配器以 runtime 传递引入，无需单独声明。
> MongoDB / Redis / 迁移引擎分别对应 `datamanager-mongodb`、`datamanager-redis`、`datamanager-migration`。

## 2. 定义实体

```java
import cn.arkmillion.core.annotation.*;
import cn.arkmillion.core.enums.GenerationType;
import cn.arkmillion.core.enums.IndexType;

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

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    // 必须提供公共无参构造器（结果集映射需要）
    public User() { }

    public User(String username, String email) {
        this.username = username;
        this.email = email;
        this.createdAt = java.time.LocalDateTime.now();
    }

    // getters / setters ...
}
```

要点：

- 无参构造器必须存在（查询结果实例化）
- 未标注 `@Column` 的字段也会持久化，列名自动驼峰转下划线（`createdAt` → `created_at`）
- `static` / `transient` 字段被跳过
- 全部注解见 [注解参考](annotations.md)

## 3. 构建配置并创建 DataManager

```java
import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.factory.DataManagerFactory;

DataManagerConfig config = DataManagerConfig.builder()
    .sqlite("data/app.db")     // 目录不存在会自动创建
        .alias("main")         // 可选代号，缺省为 "sqlite"
        .poolSize(1)           // SQLite 默认 1 连接，避免文件锁冲突
        .busyTimeout(5000)
        .build()
    .build();
```

`DataManager` 实现 `AutoCloseable`，推荐 try-with-resources，关闭时依次释放所有连接池：

```java
try (cn.arkmillion.core.db.DataManager dm = DataManagerFactory.create(config)) {
    // 业务逻辑
}
```

## 4. 建表与 CRUD

```java
import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.enums.SyncMode;

import static cn.arkmillion.core.condition.Condition.where;

RelationalDB db = dm.getRelationalDB("main");

db.syncSchema(User.class, SyncMode.CREATE);

User user = new User("alice", "alice@corp.com");
db.insert(user);
System.out.println(user.getId());   // 自增主键已回填

User found = db.selectOne(User.class, where("email").eq("alice@corp.com").build());
found.setUsername("alice2");
db.update(found);

long total = db.count(User.class, where("status").eq(1).build());

var page = db.selectPage(User.class,
    where("email").like("%@corp.com").orderBy("id", true).build(),
    cn.arkmillion.core.condition.PageParam.of(1, 20));
```

完整 API 见 [RelationalDB 指南](relational-db.md)。

## 5. 事务

```java
db.beginTransaction();
try {
    db.insert(order);
    db.update(User.class, where("id").eq(userId).build(),
        java.util.Collections.singletonMap("status", 2));
    db.commit();
} catch (Exception e) {
    db.rollback();
    throw e;
}
```

## 6. 运行官方示例

仓库内置一个零依赖（SQLite）的可运行示例：

```bash
git clone https://github.com/ArkMillion/DataManager.git
cd DataManager
mvn clean install
mvn exec:java -pl datamanager-examples -Dexec.mainClass=cn.arkmillion.examples.QuickStart
```

输出示例：

```
User: id=1, name=alice, status=1
Orders for user: 1 (count=1)
Corp users after seed file: 3
```

源码位置：[`datamanager-examples/src/main/java/cn/arkmillion/examples/QuickStart.java`](../datamanager-examples/src/main/java/cn/arkmillion/examples/QuickStart.java)

## 下一步

- 换用 MySQL / PostgreSQL？阅读 [多数据库与无缝切换](multi-database.md)
- 生产环境建表策略？阅读 [Schema 同步](schema-sync.md)
- SQL 脚本版本化管理？阅读 [迁移引擎 FlywayLite](migration.md)
