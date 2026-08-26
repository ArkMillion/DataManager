package cn.arkmillion.rabbitmq;

import cn.arkmillion.core.config.RabbitConfig;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.mq.MessageSubscription;
import cn.arkmillion.core.mq.MessagingListener;
import cn.arkmillion.core.mq.MessagingManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class RabbitMessaging implements MessagingManager {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitMessaging.class);

    private final RabbitConfig config;
    private final Connection connection;
    private final ObjectMapper mapper;
    private final List<RabbitSubscriptionImpl> subscriptions = new CopyOnWriteArrayList<>();

    public RabbitMessaging(RabbitConfig config) {
        this.config = config;
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(config.getHost());
        factory.setPort(config.getPort());
        factory.setUsername(config.getUsername());
        factory.setPassword(config.getPassword());
        factory.setVirtualHost(config.getVirtualHost());
        factory.setConnectionTimeout(config.getTimeoutMs());
        factory.setAutomaticRecoveryEnabled(config.isAutomaticRecovery());
        try {
            this.connection = factory.newConnection("datamanager-rabbit-" + config.getHost());
        } catch (IOException | TimeoutException e) {
            throw new DataManagerException(
                    "Failed to connect to RabbitMQ at " + config.getHost() + ":" + config.getPort()
                            + " (check host/port/credentials/virtualHost)", e);
        }
        this.mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void declareQueue(String queue) {
        declareQueue(queue, true, false);
    }

    public void declareQueue(String queue, boolean durable, boolean autoDelete) {
        if (queue == null || queue.isEmpty()) {
            throw new DataManagerException("queue name must not be empty");
        }
        try (Channel channel = connection.createChannel()) {
            channel.queueDeclare(queue, durable, false, autoDelete, null);
        } catch (IOException | TimeoutException e) {
            throw new DataManagerException("Failed to declare RabbitMQ queue '" + queue + "'", e);
        }
    }

    public long messageCount(String queue) {
        try (Channel channel = connection.createChannel()) {
            AMQP.Queue.DeclareOk result = channel.queueDeclarePassive(queue);
            return result.getMessageCount();
        } catch (IOException | TimeoutException e) {
            throw new DataManagerException("Failed to query RabbitMQ queue '" + queue + "'", e);
        }
    }

    @Override
    public void send(String topic, String message) {
        send(topic, null, message);
    }

    @Override
    public void send(String topic, String key, String message) {
        publishInternal(topic, key, message);
    }

    @Override
    public <T> void sendObject(String topic, T obj) {
        sendObject(topic, null, obj);
    }

    @Override
    public <T> void sendObject(String topic, String key, T obj) {
        publishInternal(topic, key, serialize(obj));
    }

    private void publishInternal(String queue, String routingKey, String payload) {
        if (queue == null || queue.isEmpty()) {
            throw new DataManagerException("queue name must not be empty");
        }
        try (Channel channel = connection.createChannel()) {
            channel.basicPublish("", queue,
                    new AMQP.BasicProperties.Builder()
                            .contentType("application/json")
                            .deliveryMode(2)
                            .build(),
                    payload.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | TimeoutException e) {
            throw new DataManagerException("Failed to publish to RabbitMQ queue '" + queue
                    + "' (does the queue exist? call declareQueue first)", e);
        }
    }

    private String serialize(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new DataManagerException("Failed to serialize object for RabbitMQ", e);
        }
    }

    public <T> T parseMessage(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new DataManagerException("Failed to deserialize RabbitMQ message", e);
        }
    }

    public <T> T parseMessage(String json, TypeReference<T> typeRef) {
        try {
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            throw new DataManagerException("Failed to deserialize RabbitMQ message", e);
        }
    }

    @Override
    public MessageSubscription subscribe(String topic, String groupId, MessagingListener listener) {
        if (topic == null || topic.isEmpty() || listener == null) {
            throw new DataManagerException("topic (queue name) and listener are required");
        }
        Channel channel;
        try {
            channel = connection.createChannel();
        } catch (IOException e) {
            throw new DataManagerException("Failed to open RabbitMQ channel for queue '" + topic + "'", e);
        }
        try {
            RabbitSubscriptionImpl sub =
                    new RabbitSubscriptionImpl(channel, topic, groupId, listener);
            subscriptions.add(sub);
            return sub;
        } catch (RuntimeException e) {
            closeChannelQuietly(channel);
            throw e;
        }
    }

    private final class RabbitSubscriptionImpl implements MessageSubscription {

        private final Channel channel;
        private final String queue;
        private final String consumerTag;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private RabbitSubscriptionImpl(Channel channel, String queue, String groupId,
                                       MessagingListener listener) throws DataManagerException {
            this.channel = channel;
            this.queue = queue;
            try {
                this.consumerTag = channel.basicConsume(queue, true, (tag, delivery) -> {
                    String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    try {
                        listener.onMessage(queue, delivery.getEnvelope().getRoutingKey(), message);
                    } catch (Exception e) {
                        LOG.error("RabbitMQ listener failed for queue '{}'", queue, e);
                    }
                }, tag -> { });
            } catch (IOException e) {
                closeChannelQuietly(channel);
                throw new DataManagerException("Failed to subscribe to RabbitMQ queue '" + queue + "'", e);
            }
        }

        @Override
        public boolean isActive() {
            return !closed.get() && channel.isOpen();
        }

        @Override
        public void unsubscribe() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                channel.basicCancel(consumerTag);
            } catch (Exception e) {
                LOG.debug("Ignored error while cancelling consumer '{}' on queue '{}': {}",
                        consumerTag, queue, e.getMessage());
            }
            closeChannelQuietly(channel);
            subscriptions.remove(this);
        }

        @Override
        public void close() {
            unsubscribe();
        }
    }

    private static void closeChannelQuietly(Channel channel) {
        try {
            channel.close();
        } catch (Exception e) {
            LOG.debug("Ignored error while closing RabbitMQ channel", e);
        }
    }

    public RabbitConfig getConfig() {
        return config;
    }

    @Override
    public void close() {
        for (RabbitSubscriptionImpl sub : subscriptions) {
            try {
                sub.unsubscribe();
            } catch (Exception e) {
                LOG.debug("Ignored error while closing subscription '{}'", sub.queue, e);
            }
        }
        try {
            connection.close();
        } catch (IOException e) {
            LOG.debug("Ignored error while closing RabbitMQ connection", e);
        }
    }
}
