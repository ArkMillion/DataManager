# 消息队列扩展（Kafka / RabbitMQ）

在缓存与持久化之外，DataManager 通过两个可选模块提供消息能力：

- `datamanager-kafka`（基于 kafka-clients）
- `datamanager-rabbitmq`（基于 amqp-client）

两者实现同一套 `MessagingManager` 接口，通过 ServiceLoader SPI 自动装配——依赖放哪个模块，就能用哪个消息中间件。

## 统一 API

```java
public interface MessagingManager extends AutoCloseable {

    void send(String topic, String message);

    void send(String topic, String key, String message);      // key 用于分区路由

    <T> void sendObject(String topic, T obj);                  // 自动 JSON 序列化

    <T> void sendObject(String topic, String key, T obj);

    MessageSubscription subscribe(String topic,
                                  String groupId,              // Kafka 消费组；Rabbit 忽略
                                  MessagingListener listener);

    void close();                                              // 退订全部 + 关闭连接
}
```

回调接口与订阅句柄：

```java
@FunctionalInterface
public interface MessagingListener {
    void onMessage(String topic, String key, String message);
}

public interface MessageSubscription extends AutoCloseable {
    boolean isActive();
    void unsubscribe();
    @Override void close();
}
```

## 快速开始

```java
DataManagerConfig config = DataManagerConfig.builder()
        .kafka("broker1:9092,broker2:9092")
        .alias("events")
        .autoOffsetReset("earliest")     // 新消费组从最早偏移读，默认 latest
        .end()
        .rabbit("localhost", 5672)
        .alias("bus")
        .credentials("app", "${RABBIT_PASSWORD}")
        .end()
        .build();

try (DataManager dm = DataManagerFactory.create(config)) {
    // ---- Kafka ----
    MessagingManager events = dm.getMessaging("events");
    events.send("order-created", "order-42", "{\"id\":42}");
    events.sendObject("order-created", order);            // JSON 序列化

    MessageSubscription sub = events.subscribe("order-created", "billing-service",
            (topic, key, message) -> System.out.println(key + " -> " + message));
    sub.unsubscribe();                                    // 幂等；close() 等价

    // ---- RabbitMQ ----
    RabbitMessaging bus = (RabbitMessaging) dm.getMessaging("bus");
    bus.declareQueue("sms-tasks");                        // 发布前必须先声明队列
    bus.send("sms-tasks", "{\"phone\":\"...\"}");
    bus.subscribe("sms-tasks", null, (q, k, m) -> { });
}
```

获取实例：`dm.getMessaging()` 返回第一个注册的实例，`dm.getMessaging(alias)` 按别名取，`dm.getMessagingNames()` 列出全部。多个 Kafka/Rabbit 集群可并存，别名互不冲突。

## Kafka 实现说明

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `acks` | `"1"` | 可选 `"0"` / `"1"` / `"all"` |
| `timeout(ms)` | 10000 | 请求超时下限 |
| `sendTimeout(ms)` | 10000 | 发送确认等待；自动钳制 ≥ 请求超时+1s |
| `pollIntervalMs` | 200 | 消费者轮询间隔 |
| `autoOffsetReset` | `latest` | `earliest` / `latest` / `none` |

- **发送**为同步确认语义：`send()` 内部等待 broker ack（超时抛 `DataManagerException`），失败即知。
- **订阅**运行在后台守护线程（每订阅一条线程），`enable.auto.commit=true`，**至少一次**语义；回调异常被捕获记录，不会中断消费。
- poll 连续失败自动重试并指数退避（上限 30 次后放弃），避免瞬时无 broker 导致订阅静默死亡。
- topic 不存在时：发送侧依赖 broker `auto.create.topics.enable`；建议"先发送再订阅"，或提前建 topic。

## RabbitMQ 实现说明

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `credentials(user, pass)` | guest/guest | 生产环境务必覆盖 |
| `virtualHost` | `/` | |
| `timeout(ms)` | 10000 | 连接超时 |
| `automaticRecovery` | `true` | 连接/拓扑自动恢复 |

- **模型映射**：统一 API 的 `topic` 即**队列名**；`send` 走默认交换机（routingKey=队列名）。声明队列用 `declareQueue(queue)`（durable、幂等）或 `declareQueue(queue, durable, autoDelete)`。
- **groupId 被忽略**——AMQP 的竞争消费者天然提供分组语义：同队列多消费者轮流接收。
- 订阅使用 `autoAck=true`，**至多一次**语义；需要可靠处理请在业务层落库去重或改用手动 ack 方案。
- 向未声明的队列发布会被 broker 以 404 关闭通道——发布前先 `declareQueue`。
- `messageCount(queue)` 可查询积压数量。

## 与 Redis Pub/Sub 的取舍

| 场景 | 建议 |
|------|------|
| 单机内轻量事件广播、允许丢消息 | Redis Pub/Sub（零新增组件） |
| 事件回放/顺序/分区并行 | Kafka |
| 任务队列、路由灵活、低吞吐集成 | RabbitMQ |

## 测试

仓库根目录的 docker-compose.yml 已含单节点 KRaft Kafka 与 RabbitMQ（含管理界面 :15672）：

```bash
docker compose up -d
cp online-test.properties.example online-test.properties
mvn test -Ponline-tests "-Dtest=OnlineMqTest" -pl datamanager-examples
```
