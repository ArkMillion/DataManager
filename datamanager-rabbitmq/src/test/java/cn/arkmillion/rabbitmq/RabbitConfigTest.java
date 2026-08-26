package cn.arkmillion.rabbitmq;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.config.RabbitConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RabbitConfigTest {

    @Test
    void builderWiresRabbitConfig() {
        DataManagerConfig config = DataManagerConfig.builder()
                .rabbit("mq.example.com", 5673)
                .alias("bus")
                .credentials("app", "secret")
                .virtualHost("/prod")
                .timeout(8000)
                .automaticRecovery(false)
                .end()
                .build();

        List<RabbitConfig> rabbitConfigs = config.getRabbitConfigs();
        assertEquals(1, rabbitConfigs.size());
        RabbitConfig cfg = rabbitConfigs.get(0);
        assertEquals("mq.example.com", cfg.getHost());
        assertEquals(5673, cfg.getPort());
        assertEquals("bus", cfg.getAlias());
        assertEquals("app", cfg.getUsername());
        assertEquals("secret", cfg.getPassword());
        assertEquals("/prod", cfg.getVirtualHost());
        assertEquals(8000, cfg.getTimeoutMs());
        assertEquals(false, cfg.isAutomaticRecovery());
    }

    @Test
    void defaultsAreSensible() {
        RabbitConfig cfg = new RabbitConfig(null, 5672);
        assertEquals("localhost", cfg.getHost());
        assertEquals(5672, cfg.getPort());
        assertEquals("guest", cfg.getUsername());
        assertEquals("guest", cfg.getPassword());
        assertEquals("/", cfg.getVirtualHost());
        assertEquals(true, cfg.isAutomaticRecovery());

        DataManagerConfig config = DataManagerConfig.builder()
                .rabbit("localhost", 5672)
                .credentials(null, null)
                .virtualHost("")
                .end()
                .build();
        RabbitConfig wired = config.getRabbitConfigs().get(0);
        assertEquals("guest", wired.getUsername());
        assertEquals("/", wired.getVirtualHost());
    }
}
