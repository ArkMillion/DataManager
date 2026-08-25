# 注解参考

所有注解位于 `cn.arkmillion.core.annotation`，运行期保留（`RUNTIME`）。

## 关系型数据库注解

### @Table

标注在实体类上（必须）。

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `name` | 必填 | 表名 |
| `engine` | InnoDB | MySQL 专用；其他库忽略 |
| `charset` | utf8mb4 | MySQL 专用；其他库忽略 |
| `comment` | "" | 表注释；MySQL 内联、PostgreSQL 走 COMMENT ON |

```java
@Table(name = "sys_user", comment = "用户表")
public class User { ... }
```

### @Column

标注在字段上，可省略。省略时列名 = 驼峰转下划线，类型自动推断。

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `name` | ""（转下划线） | 列名 |
| `type` | AUTO | 逻辑类型，AUTO 时按 Java 类型推断（见下表） |
| `length` | 255 | VARCHAR/CHAR 长度 |
| `precision` / `scale` | 0 / 0 | DECIMAL 精度与标度（>0 时生效，否则方言默认） |
| `nullable` | true | NOT NULL |
| `unique` | false | 生成唯一索引（命名 `uk_表名_列名`，除非 @Index 指定名） |
| `defaultValue` | "" | DDL DEFAULT 子句；`CURRENT_TIMESTAMP`/数字/NULL 原样输出，其余加引号 |
| `comment` | "" | 列注释 |

### @Id

主键标记，**一个实体最多一个**。

| strategy | 行为 |
|----------|------|
| `AUTO` | 解析为 IDENTITY（自增），insert 后回填生成键 |
| `IDENTITY` | 同上，DDL 使用 AUTO_INCREMENT / SERIAL / AUTOINCREMENT |
| `UUID` | insert 时若为 null 自动填充 `UUID.randomUUID().toString()` 并作为普通列写入 |
| `SEQUENCE` / `NONE` | 手动赋值，框架不做处理 |

配合 `@AutoIncrement` 显式声明自增（MySQL `AUTO_INCREMENT`、PG `SERIAL/BIGSERIAL`、SQLite `INTEGER PRIMARY KEY AUTOINCREMENT`）。
自增主键类型建议 `Long`/`Integer`（含包装类型）以获得最佳方言兼容。

### @AutoIncrement

独立标记自增；用于非 `@Id` 字段时同样会触发方言自增 DDL（SQLite 仅对整型主键生效）。

### @Index

```java
@Index(type = IndexType.UNIQUE, name = "uk_email")
```

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `type` | NORMAL | NORMAL / UNIQUE / FULLTEXT（FULLTEXT 仅 MySQL 生成普通索引语义占位，按需手工建）|
| `name` | "" | 缺省：`(唯一? uk_ : idx_) + 表名 + "_" + 列名` |

索引创建是幂等的：已存在同名索引则跳过（MySQL 查 STATISTICS、PG 查 pg_indexes、SQLite 用 IF NOT EXISTS + sqlite_master 预检）。

## 文档型（MongoDB）注解

### @Document

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `collection` | 必填 | 集合名 |
| `database` | "" | 库名；MongoConfig.database 可覆盖它 |

```java
@Document(collection = "operation_logs", database = "logs")
public class LogEntry { ... }
```

### @DocumentId

标记文档主键字段，映射到 `_id`（可用 `fieldName` 改名）。未标注但名为 `id` 的字段同样视为主键。
insert 时若 String 型 `_id` 为 null，自动生成 `ObjectId` 十六进制串并回填。

### @Indexed

```java
@Indexed(direction = IndexDirection.DESC, unique = false, sparse = false)
private Instant timestamp;
```

`syncSchema(clazz, CREATE)` 时创建索引，命名 `字段名_1` / `字段名_-1`。

## 逻辑类型映射表（DataType）

`DataType.AUTO` 按 Java 类型推断；显式指定后按各库 Synchronizer 映射：

| DataType | Java 推断来源 | MySQL | PostgreSQL | SQLite |
|----------|--------------|-------|------------|--------|
| TINYINT | Byte | TINYINT | SMALLINT | INTEGER |
| SMALLINT | Short | SMALLINT | SMALLINT | INTEGER |
| INT / INTEGER | Integer/int | INT | INTEGER | INTEGER |
| BIGINT | Long/long | BIGINT | BIGINT | INTEGER |
| FLOAT | Float | FLOAT | REAL | REAL |
| DOUBLE | Double | DOUBLE | DOUBLE PRECISION | REAL |
| DECIMAL | BigDecimal | DECIMAL(p,s)¹ | NUMERIC(p,s)¹ | NUMERIC |
| BOOLEAN | Boolean | TINYINT(1) | BOOLEAN | INTEGER |
| VARCHAR | String/枚举 | VARCHAR(n)² | VARCHAR(n)² | TEXT |
| CHAR | - | CHAR(n) | CHAR(n) | TEXT |
| TEXT | - | TEXT | TEXT | TEXT |
| LONGTEXT | - | LONGTEXT | TEXT | TEXT |
| BLOB | byte[] | BLOB | BYTEA | BLOB |
| DATE | LocalDate | DATE | DATE | TEXT |
| TIME | LocalTime | TIME | TIME | TEXT |
| DATETIME | LocalDateTime | DATETIME | TIMESTAMP | TEXT |
| TIMESTAMP | Instant / Date | TIMESTAMP | TIMESTAMP | TEXT |

¹ precision=0 时使用方言默认（MySQL DECIMAL(18,2)、PG NUMERIC(18,2)）
² length<=0 时取 255

## 参数绑定与读回的类型转换

由 core 的 `TypeBinders` 统一处理，两侧对称：

- **写入**：LocalDateTime→Timestamp、Instant→Timestamp、Enum→String(name)、byte[]→setBytes
- **读回**：数字间任意互转（Long↔Integer↔BigDecimal…）、`0/1`↔Boolean、
  Timestamp/字符串多格式（ISO、`yyyy-MM-dd HH:mm:ss[.SSS]`、毫秒数字串）→ java.time 类型、Blob→byte[]

因此同一实体在三种关系库之间切换无需调整任何类型声明。

## 完整示例

```java
@Table(name = "t_order", comment = "订单表")
public class Order {

    @Id(strategy = GenerationType.AUTO)
    @AutoIncrement
    private Long id;

    @Id 无关字段示例 —— UUID 主键写法：
    // @Id(strategy = GenerationType.UUID)
    // private String uid;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(type = DataType.DECIMAL, precision = 18, scale = 2, nullable = false,
            defaultValue = "0.00", comment = "金额")
    private BigDecimal amount;

    @Column(name = "status", type = DataType.TINYINT, defaultValue = "0")
    private Integer status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```
