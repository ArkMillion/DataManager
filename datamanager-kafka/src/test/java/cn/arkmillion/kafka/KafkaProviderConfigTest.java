package cn.arkmillion.kafka;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.config.KafkaConfig;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.factory.InstanceBinding;
import cn.arkmillion.core.mq.MessagingManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaProviderConfigTest {

    @Test
    void builderWiresKafkaConfig() {
        DataManagerConfig config = DataManagerConfig.builder()
                .kafka("broker1:9092,broker2:9092")
                .alias("events")
                .acks("all")
                .timeout(5000)
                .sendTimeout(3000)
                .autoOffsetReset("earliest")
                .end()
                .build();

        List<KafkaConfig> kafkaConfigs = config.getKafkaConfigs();
        assertEquals(1, kafkaConfigs.size());
        KafkaConfig cfg = kafkaConfigs.get(0);
        assertEquals("broker1:9092,broker2:9092", cfg.getServers());
        assertEquals("events", cfg.getAlias());
        assertEquals("all", cfg.getAcks());
        assertEquals(5000, cfg.getTimeoutMs());
        assertEquals(3000, cfg.getSendTimeoutMs());
        assertEquals("earliest", cfg.getAutoOffsetReset());
    }

    @Test
    void autoOffsetResetRejectsInvalidMode() {
        DataManagerConfig config = DataManagerConfig.builder()
                .kafka(null)
                .autoOffsetReset("bogus")
                .end()
                .build();
        assertEquals("latest", config.getKafkaConfigs().get(0).getAutoOffsetReset());
    }

    @Test
    void providerResolvesAliasAndCreatesInstance() {
        DataManagerConfig config = DataManagerConfig.builder()
                .kafka("localhost:9092")
                .alias("k1")
                .sendTimeout(800)
                .end()
                .build();

        KafkaProvider provider = new KafkaProvider();
        assertEquals("kafka", provider.name());

        List<InstanceBinding<MessagingManager>> bindings = provider.createInstances(config);
        assertEquals(1, bindings.size());
        assertEquals("k1", bindings.get(0).getName());
        MessagingManager messaging = bindings.get(0).getInstance();
        assertInstanceOf(KafkaMessaging.class, messaging);

        messaging.close();
    }

    @Test
    void sendFailsFastWhenBrokerUnavailable() {
        try (KafkaMessaging messaging = new KafkaMessaging(
                DataManagerConfig.builder().kafka("localhost:59999")
                        .sendTimeout(600)
                        .timeout(600)
                        .end().build()
                .getKafkaConfigs().get(0))) {
            assertThrows(DataManagerException.class, () -> messaging.send("no-broker-topic", "x"));
            assertTrue(messaging.getConfig().getSendTimeoutMs() <= 1000,
                    "test should fail fast, not wait default 10s");
        }
    }
}
