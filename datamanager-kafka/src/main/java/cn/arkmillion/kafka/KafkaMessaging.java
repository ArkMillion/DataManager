package cn.arkmillion.kafka;

import cn.arkmillion.core.config.KafkaConfig;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.mq.MessageSubscription;
import cn.arkmillion.core.mq.MessagingListener;
import cn.arkmillion.core.mq.MessagingManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class KafkaMessaging implements MessagingManager {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaMessaging.class);

    private final KafkaConfig config;
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper;
    private final List<KafkaSubscriptionImpl> subscriptions = new CopyOnWriteArrayList<>();

    public KafkaMessaging(KafkaConfig config) {
        this.config = config;
        this.producer = new KafkaProducer<>(producerProperties(config));
        this.mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static Properties producerProperties(KafkaConfig config) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, config.getAcks());
        int requestTimeout = Math.max(100, config.getTimeoutMs());
        long deliveryTimeout = Math.max(config.getSendTimeoutMs(), requestTimeout + 1000L);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeout);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                (int) Math.min(Integer.MAX_VALUE, deliveryTimeout));
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, requestTimeout);
        return props;
    }

    private Properties consumerProperties(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.getAutoOffsetReset());
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, String.valueOf(Math.max(6000, config.getTimeoutMs())));
        props.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, String.valueOf(10_000));
        return props;
    }

    @Override
    public void send(String topic, String message) {
        send(topic, null, message);
    }

    @Override
    public void send(String topic, String key, String message) {
        if (topic == null || topic.isEmpty()) {
            throw new DataManagerException("topic must not be empty");
        }
        try {
            producer.send(new ProducerRecord<>(topic, key, message))
                    .get(config.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataManagerException("Interrupted while sending to Kafka topic '" + topic + "'", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new DataManagerException("Failed to send to Kafka topic '" + topic + "'", e);
        }
    }

    @Override
    public <T> void sendObject(String topic, T obj) {
        sendObject(topic, null, obj);
    }

    @Override
    public <T> void sendObject(String topic, String key, T obj) {
        send(topic, key, serialize(obj));
    }

    private String serialize(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new DataManagerException("Failed to serialize object for Kafka", e);
        }
    }

    public <T> T parseMessage(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new DataManagerException("Failed to deserialize Kafka message", e);
        }
    }

    public <T> T parseMessage(String json, TypeReference<T> typeRef) {
        try {
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            throw new DataManagerException("Failed to deserialize Kafka message", e);
        }
    }

    @Override
    public MessageSubscription subscribe(String topic, String groupId, MessagingListener listener) {
        if (topic == null || topic.isEmpty() || groupId == null || groupId.isEmpty() || listener == null) {
            throw new DataManagerException("topic, groupId and listener are required");
        }
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties(groupId));
        try {
            consumer.subscribe(Collections.singletonList(topic));
        } catch (RuntimeException e) {
            consumer.close();
            throw e;
        }
        KafkaSubscriptionImpl sub = new KafkaSubscriptionImpl(consumer, topic, groupId, listener);
        subscriptions.add(sub);
        return sub;
    }

    private final class KafkaSubscriptionImpl implements MessageSubscription {

        private final KafkaConsumer<String, String> consumer;
        private final String topic;
        private final String groupId;
        private final MessagingListener listener;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final Thread worker;

        private KafkaSubscriptionImpl(KafkaConsumer<String, String> consumer,
                                      String topic, String groupId, MessagingListener listener) {
            this.consumer = consumer;
            this.topic = topic;
            this.groupId = groupId;
            this.listener = listener;
            this.worker = new Thread(this::runLoop,
                    "datamanager-kafka-" + groupId + "-" + topic);
            this.worker.setDaemon(true);
            this.worker.start();
        }

        private void runLoop() {
            int consecutiveFailures = 0;
            while (!closed.get()) {
                try {
                    org.apache.kafka.clients.consumer.ConsumerRecords<String, String> records =
                            consumer.poll(Duration.ofMillis(config.getPollIntervalMs()));
                    consecutiveFailures = 0;
                    for (ConsumerRecord<String, String> record : records) {
                        try {
                            listener.onMessage(record.topic(), record.key(), record.value());
                        } catch (Exception e) {
                            LOG.error("Kafka listener failed for topic '{}' partition {} offset {}",
                                    record.topic(), record.partition(), record.offset(), e);
                        }
                    }
                } catch (WakeupException ignored) {
                    break;
                } catch (RuntimeException e) {
                    if (closed.get()) {
                        break;
                    }
                    consecutiveFailures++;
                    LOG.warn("Kafka poll failed (attempt {}): {}", consecutiveFailures, e.getMessage());
                    if (consecutiveFailures >= 30) {
                        LOG.error("Kafka subscription to topic '{}' (group '{}') giving up "
                                + "after repeated poll failures", topic, groupId);
                        break;
                    }
                    try {
                        Thread.sleep(Math.min(500L * consecutiveFailures, 3000L));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            try {
                consumer.close();
            } catch (RuntimeException e) {
                LOG.debug("Ignored error while closing Kafka consumer", e);
            }
        }

        @Override
        public boolean isActive() {
            return !closed.get() && worker.isAlive();
        }

        @Override
        public void unsubscribe() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            consumer.wakeup();
            try {
                worker.join(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            subscriptions.remove(this);
        }

        @Override
        public void close() {
            unsubscribe();
        }
    }

    public KafkaConfig getConfig() {
        return config;
    }

    @Override
    public void close() {
        for (KafkaSubscriptionImpl sub : subscriptions) {
            try {
                sub.unsubscribe();
            } catch (Exception e) {
                LOG.debug("Ignored error while closing subscription '{}'", sub.topic, e);
            }
        }
        producer.close();
    }
}
