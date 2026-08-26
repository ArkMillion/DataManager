package cn.arkmillion.examples;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.db.DataManager;
import cn.arkmillion.core.factory.DataManagerFactory;
import cn.arkmillion.core.mq.MessageSubscription;
import cn.arkmillion.core.mq.MessagingManager;
import cn.arkmillion.rabbitmq.RabbitMessaging;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("online")
class OnlineMqTest {

    private static final Properties PROPS = new Properties();
    private static boolean loaded = false;

    @BeforeAll
    static void loadProperties() throws Exception {
        Path local = Paths.get("online-test.properties");
        Path parent = Paths.get("..", "online-test.properties");
        File file = Files.exists(local) ? local.toFile()
                : (Files.exists(parent) ? parent.toFile() : null);
        if (file == null) {
            return;
        }
        try (InputStream in = new FileInputStream(file)) {
            PROPS.load(in);
        }
        loaded = !PROPS.isEmpty();
    }

    private static void assumeConfigured(String... keys) {
        assumeTrue(loaded, "online-test.properties not provided - online tests skipped");
        for (String key : keys) {
            String value = PROPS.getProperty(key);
            assumeTrue(value != null && !value.trim().isEmpty(), "missing property: " + key);
        }
    }

    private static String require(String key) {
        return PROPS.getProperty(key).trim();
    }

    @Test
    void kafkaRoundTrip() throws Exception {
        assumeConfigured("kafka.servers");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String topic = "dm-it-kafka-" + suffix;
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();

        DataManagerConfig config = DataManagerConfig.builder()
                .kafka(require("kafka.servers"))
                .alias("it-kafka")
                .sendTimeout(15000)
                .autoOffsetReset("earliest")
                .end()
                .build();

        try (DataManager dm = DataManagerFactory.create(config)) {
            MessagingManager messaging = dm.getMessaging("it-kafka");
            assertTrue(dm.getMessagingNames().contains("it-kafka"));

            messaging.send(topic, "k1", "hello");

            MessageSubscription subscription = messaging.subscribe(topic, "it-group-" + suffix,
                    (t, k, m) -> received.offer(k + "|" + m));
            assertTrue(subscription.isActive());

            assertEquals("k1|hello", waitFor(received, "k1|hello"));

            messaging.sendObject(topic, "k2", new OrderPayload("A-1024", 3));
            String json = waitFor(received, "k2|");
            assertTrue(json.contains("\"sku\":\"A-1024\""));
            assertTrue(json.contains("\"quantity\":3"));

            subscription.unsubscribe();
            assertFalse(subscription.isActive());
        }
    }

    @Test
    void rabbitRoundTrip() throws Exception {
        assumeConfigured("rabbit.host", "rabbit.port", "rabbit.username", "rabbit.password");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String queue = "dm-it-rabbit-" + suffix;
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();

        DataManagerConfig config = DataManagerConfig.builder()
                .rabbit(require("rabbit.host"), Integer.parseInt(require("rabbit.port")))
                .alias("it-rabbit")
                .credentials(require("rabbit.username"),
                        PROPS.getProperty("rabbit.password") == null ? null : require("rabbit.password"))
                .timeout(5000)
                .end()
                .build();

        try (DataManager dm = DataManagerFactory.create(config)) {
            MessagingManager messaging = dm.getMessaging("it-rabbit");
            RabbitMessaging rabbit = (RabbitMessaging) messaging;
            rabbit.declareQueue(queue);
            assertEquals(0L, rabbit.messageCount(queue));

            MessageSubscription subscription = rabbit.subscribe(queue, null, (t, k, m) -> received.offer(m));
            assertTrue(subscription.isActive());
            messaging.send(queue, "hello");
            assertEquals("hello", received.poll(10, TimeUnit.SECONDS));

            messaging.sendObject(queue, new OrderPayload("B-2048", 7));
            String json = received.poll(10, TimeUnit.SECONDS);
            assertNotNull(json);
            assertTrue(json.contains("\"sku\":\"B-2048\""));
            assertTrue(json.contains("\"quantity\":7"));
            assertEquals(0L, rabbit.messageCount(queue));

            subscription.unsubscribe();
            assertFalse(subscription.isActive());

            messaging.send(queue, "after-unsub");
            Long leftover = null;
            for (int i = 0; i < 20; i++) {
                leftover = rabbit.messageCount(queue);
                if (leftover > 0) {
                    break;
                }
                Thread.sleep(100);
            }
            assertTrue(leftover != null && leftover >= 1, "message should stay queued after unsubscribe");
        }
    }

    private static String waitFor(LinkedBlockingQueue<String> queue, String prefix)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20000;
        String item = queue.poll(20, TimeUnit.MILLISECONDS);
        while ((item == null || !item.startsWith(prefix))
                && System.currentTimeMillis() < deadline) {
            if (item != null) {
                queue.offer(item);
            }
            Thread.sleep(50);
            item = queue.poll(20, TimeUnit.MILLISECONDS);
        }
        assertNotNull(item, "timed out waiting for message with prefix '" + prefix + "'");
        return item;
    }

    public static class OrderPayload {

        private String sku;
        private int quantity;

        public OrderPayload() {
        }

        public OrderPayload(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
