# ByteBuddy 性能增强（可选）

`datamanager-bytebuddy` 用运行期字节码生成的属性访问器替代反射读写实体字段，
消除高并发场景下反射的安全检查与方法调用开销。

## 原理

实体字段读写的默认路径是缓存的反射（`Field.get/set`）。引入本模块后，
框架通过 SPI 自动切换为 **ByteBuddy 动态生成的访问器类**：

```
ColumnMetadata.getValue/setSetValue
        │
        ▼
PropertyAccessors.forField(field)          ← core 抽象层（SPI 解析 + 双层缓存）
        │ ServiceLoader 发现 Provider
        ▼
ByteBuddyAccessorProvider                  ← 本模块
        │ 实体存在标准 getter/setter？
        ├─ 是：生成 AbstractPropertyReader/Writer 子类
        │     read(bean)  ⇒ invokevirtual bean.getXxx()   ← 直接方法调用，JIT 友好
        │     write(bean,v) ⇒ invokevirtual bean.setXxx((T)v)
        └─ 否：回退 ReflectivePropertyAccessor（仍缓存 setAccessible 结果）
```

生成要点：

- `MethodCall.invoke(getter).onArgument(0).withAssigner(Assigner.DEFAULT, Typing.DYNAMIC)`
  —— 参数与返回值自动完成装箱/拆箱与类型强转（int↔Integer、BigDecimal、时间类型均验证通过）
- 类加载使用 `ClassLoadingStrategy.Default.WRAPPER`，跨 JDK（8~21）安全
- 读写器实例按 Field 全局缓存，生成成本只发生一次

## 启用方式

加入依赖即可，**无需任何代码或配置变更**：

```xml
<dependency>
    <groupId>cn.arkmillion</groupId>
    <artifactId>datamanager-bytebuddy</artifactId>
    <version>1.0.0</version>
</dependency>
```

对上层完全透明：所有关系库 CRUD、MongoDB Jackson 映射之外的属性访问自动受益。

## 行为规则

| 场景 | 行为 |
|------|------|
| 字段有 public getter 且有兼容 setter | 生成字节码访问器 |
| 只有 getter 或只有 setter | 回退反射 |
| static / 无任何访问器 | 回退反射 |
| Provider 生成过程抛异常 | 回退反射（不影响业务）|
| 同一 Field 多次请求 | 命中缓存，返回同一访问器 |

判定标准：

- getter：`getXxx()`（Boolean 型接受 `isXxx()`），无参、public
- setter：`setXxx(param)`，单参且参数类型可接收字段类型，public

## 验证代码

```java
Field f = User.class.getDeclaredField("username");

// 未引入 bytebuddy 模块时：
assertTrue(PropertyAccessors.forField(f) instanceof ReflectivePropertyAccessor);

// 引入后（User 有 getUsername/setUsername）：
assertFalse(PropertyAccessors.forField(f) instanceof ReflectivePropertyAccessor);

// 无访问器实体仍走反射回退：
Field g = Legacy.class.getDeclaredField("value");
assertTrue(PropertyAccessors.forField(g) instanceof ReflectivePropertyAccessor);
```

仓库内测试：

- `ByteBuddyAccessorProviderTest`：Long/String/boolean/int/double/BigDecimal/LocalDateTime 七种类型往返 + 回退 + 缓存断言
- `ByteBuddySQLiteIntegrationTest`：生成访问器贯穿 SQLite 建表→批量插入→条件查询→更新→计数全链路

## 适用建议

| 场景 | 建议 |
|------|------|
| 高并发在线服务（QPS 数千以上） | 推荐启用 |
| 桌面工具 / 低频后台任务 | 反射路径已足够 |
| 实体普遍缺少 getter/setter | 收益有限，可不启用 |

核心 jar 保持零 ByteBuddy 依赖；不引入该模块时 SPI 自然回落，无任何类路径污染。
